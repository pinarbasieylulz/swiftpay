package com.swiftpay.service;

import com.swiftpay.dto.AccountResponse;
import com.swiftpay.dto.LimitResponse;
import com.swiftpay.entity.Account;
import com.swiftpay.entity.Customer;
import com.swiftpay.entity.TransferStatus;
import com.swiftpay.exception.AccessDeniedCustomException;
import com.swiftpay.exception.ResourceNotFoundException;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.repository.TransferRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransferRepository transferRepository;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                           TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transferRepository = transferRepository;
    }

    public List<AccountResponse> listAccounts(UUID customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    public Account getOwnedAccount(UUID accountId, UUID customerId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getCustomerId().equals(customerId)) {
            throw new AccessDeniedCustomException("Account does not belong to the authenticated customer");
        }
        return account;
    }

    public LimitResponse getLimits(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        List<Account> accounts = accountRepository.findByCustomerId(customerId);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plusSeconds(86400);
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal dailySpent = BigDecimal.ZERO;
        BigDecimal monthlySpent = BigDecimal.ZERO;
        for (Account account : accounts) {
            dailySpent = dailySpent.add(transferRepository.sumCompletedAmount(
                    account.getId(), TransferStatus.COMPLETED, startOfDay, startOfNextDay));
            monthlySpent = monthlySpent.add(transferRepository.sumCompletedAmount(
                    account.getId(), TransferStatus.COMPLETED, startOfMonth, startOfNextMonth));
        }

        BigDecimal dailyRemaining = customer.getDailyLimit().subtract(dailySpent).max(BigDecimal.ZERO);
        BigDecimal monthlyRemaining = customer.getMonthlyLimit().subtract(monthlySpent).max(BigDecimal.ZERO);

        return new LimitResponse(customer.getPerTransferLimit(), customer.getDailyLimit(), dailyRemaining,
                customer.getMonthlyLimit(), monthlyRemaining);
    }
}
