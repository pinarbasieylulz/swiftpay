package com.swiftpay.service;

import com.swiftpay.dto.TransferRequest;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.entity.Account;
import com.swiftpay.entity.Customer;
import com.swiftpay.entity.Transfer;
import com.swiftpay.entity.TransferStatus;
import com.swiftpay.exception.AccessDeniedCustomException;
import com.swiftpay.exception.ResourceNotFoundException;
import com.swiftpay.exception.TransferConflictException;
import com.swiftpay.exception.TransferRejectedException;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.repository.TransferRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public TransferService(TransferRepository transferRepository, AccountRepository accountRepository,
                            CustomerRepository customerRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public TransferResponse createTransfer(UUID customerId, TransferRequest request) {
        var existing = transferRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            Transfer previous = existing.get();
            if (!previous.getCustomerId().equals(customerId)) {
                throw new AccessDeniedCustomException("Idempotency key belongs to another customer");
            }
            return TransferResponse.from(previous);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Account source = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        if (!source.getCustomerId().equals(customerId)) {
            throw new AccessDeniedCustomException("Source account does not belong to the authenticated customer");
        }

        Account destination = accountRepository.findByIban(request.getDestinationIban())
                .orElseThrow(() -> new TransferRejectedException("DESTINATION_NOT_FOUND",
                        "Destination IBAN does not belong to any account in this system"));

        if (destination.getId().equals(source.getId())) {
            throw new TransferRejectedException("SELF_TRANSFER_NOT_ALLOWED", "Cannot transfer to the same account");
        }

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(customer.getPerTransferLimit()) > 0) {
            throw new TransferRejectedException("PER_TRANSFER_LIMIT_EXCEEDED",
                    "Amount exceeds the per-transfer limit of " + customer.getPerTransferLimit());
        }

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plusSeconds(86400);
        BigDecimal dailySpent = transferRepository.sumCompletedAmount(
                source.getId(), TransferStatus.COMPLETED, startOfDay, startOfNextDay);
        if (dailySpent.add(amount).compareTo(customer.getDailyLimit()) > 0) {
            throw new TransferRejectedException("DAILY_LIMIT_EXCEEDED",
                    "Transfer would exceed the daily limit of " + customer.getDailyLimit());
        }

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal monthlySpent = transferRepository.sumCompletedAmount(
                source.getId(), TransferStatus.COMPLETED, startOfMonth, startOfNextMonth);
        if (monthlySpent.add(amount).compareTo(customer.getMonthlyLimit()) > 0) {
            throw new TransferRejectedException("MONTHLY_LIMIT_EXCEEDED",
                    "Transfer would exceed the monthly limit of " + customer.getMonthlyLimit());
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw new TransferRejectedException("INSUFFICIENT_BALANCE", "Source account balance is insufficient");
        }

        Transfer transfer = new Transfer(UUID.randomUUID(), request.getIdempotencyKey(), customerId,
                source.getId(), request.getDestinationIban(), destination.getId(), amount,
                source.getCurrency(), request.getDescription());

        try {
            source.debit(amount);
            destination.credit(amount);
            accountRepository.save(source);
            accountRepository.save(destination);
            transfer.markCompleted();
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new TransferConflictException("Account was modified concurrently, please retry");
        }

        transfer = transferRepository.save(transfer);
        return TransferResponse.from(transfer);
    }

    public TransferResponse getTransfer(UUID transferId, UUID customerId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
        if (!transfer.getCustomerId().equals(customerId)) {
            throw new AccessDeniedCustomException("Transfer does not belong to the authenticated customer");
        }
        return TransferResponse.from(transfer);
    }

    public List<TransferResponse> listTransfers(UUID customerId, Instant startDate, Instant endDate) {
        Instant start = startDate != null ? startDate : Instant.EPOCH;
        Instant end = endDate != null ? endDate : Instant.now().plusSeconds(1);
        return transferRepository.findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(customerId, start, end)
                .stream().map(TransferResponse::from).collect(Collectors.toList());
    }
}
