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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private TransferService transferService;

    private UUID customerId;
    private UUID sourceId;
    private UUID destId;
    private Customer customer;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        destId = UUID.randomUUID();
        customer = new Customer(customerId, "alice", "hash", "Alice",
                new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00"));
        source = new Account(sourceId, customerId, "TR110006200000000011110001", new BigDecimal("1000.00"), "TRY");
        destination = new Account(destId, UUID.randomUUID(), "TR110006200000000022220002", new BigDecimal("0.00"), "TRY");
    }

    private TransferRequest request(BigDecimal amount, String iban, String idemKey) {
        TransferRequest r = new TransferRequest();
        r.setSourceAccountId(sourceId);
        r.setDestinationIban(iban);
        r.setAmount(amount);
        r.setDescription("desc");
        r.setIdempotencyKey(idemKey);
        return r;
    }

    @Test
    void createTransfer_success_movesFundsAndMarksCompleted() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));
        when(transferRepository.sumCompletedAmount(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1"));

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(source.getBalance()).isEqualTo(new BigDecimal("900.00"));
        assertThat(destination.getBalance()).isEqualTo(new BigDecimal("100.00"));
        verify(accountRepository).save(source);
        verify(accountRepository).save(destination);
    }

    @Test
    void createTransfer_duplicateIdempotencyKey_returnsPreviousResultWithoutMovingFundsAgain() {
        Transfer previous = new Transfer(UUID.randomUUID(), "k1", customerId, sourceId,
                "TR110006200000000022220002", destId, new BigDecimal("100.00"), "TRY", "desc");
        previous.markCompleted();
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.of(previous));

        TransferResponse response = transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1"));

        assertThat(response.getId()).isEqualTo(previous.getId());
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createTransfer_idempotencyKeyBelongsToAnotherCustomer_throwsAccessDenied() {
        Transfer previous = new Transfer(UUID.randomUUID(), "k1", UUID.randomUUID(), sourceId,
                "TR110006200000000022220002", destId, new BigDecimal("100.00"), "TRY", "desc");
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void createTransfer_sourceAccountNotOwnedByCustomer_throwsAccessDenied() {
        Account otherOwnerAccount = new Account(sourceId, UUID.randomUUID(), "TR1", BigDecimal.TEN, "TRY");
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(otherOwnerAccount));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("10.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void createTransfer_destinationIbanUnknown_throwsRejected() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("10.00"), "TR_UNKNOWN", "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("Destination IBAN");
    }

    @Test
    void createTransfer_selfTransfer_throwsRejected() {
        destination = new Account(sourceId, customerId, source.getIban(), new BigDecimal("1000.00"), "TRY");
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban(source.getIban())).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("10.00"), source.getIban(), "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void createTransfer_amountExceedsPerTransferLimit_throwsRejected() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("50001.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("per-transfer limit");
    }

    @Test
    void createTransfer_dailyLimitExceeded_throwsRejected() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));
        source.credit(new BigDecimal("200000.00"));
        when(transferRepository.sumCompletedAmount(eq(sourceId), eq(TransferStatus.COMPLETED), any(), any()))
                .thenReturn(new BigDecimal("99950.00"));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("daily limit");
    }

    @Test
    void createTransfer_customerNotFound_throwsResourceNotFound() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("10.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTransfer_monthlyLimitExceeded_throwsRejected() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));
        source.credit(new BigDecimal("600000.00"));
        when(transferRepository.sumCompletedAmount(eq(sourceId), eq(TransferStatus.COMPLETED), any(), any()))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(new BigDecimal("499950.00"));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("monthly limit");
    }

    @Test
    void createTransfer_concurrentAccountUpdate_throwsConflict() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));
        when(transferRepository.sumCompletedAmount(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(accountRepository.save(source)).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Account.class, sourceId));

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("100.00"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(TransferConflictException.class)
                .hasMessageContaining("concurrently");
    }

    @Test
    void listTransfers_withExplicitDateRange_passesThroughDates() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        when(transferRepository.findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(customerId, start, end))
                .thenReturn(java.util.List.of());

        var result = transferService.listTransfers(customerId, start, end);
        assertThat(result).isEmpty();
        verify(transferRepository).findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(customerId, start, end);
    }

    @Test
    void createTransfer_insufficientBalance_throwsRejected() {
        when(transferRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByIban("TR110006200000000022220002")).thenReturn(Optional.of(destination));
        when(transferRepository.sumCompletedAmount(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> transferService.createTransfer(customerId, request(new BigDecimal("1000.01"), "TR110006200000000022220002", "k1")))
                .isInstanceOf(TransferRejectedException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void getTransfer_notOwnedByCustomer_throwsAccessDenied() {
        Transfer transfer = new Transfer(UUID.randomUUID(), "k1", UUID.randomUUID(), sourceId,
                "iban", destId, BigDecimal.TEN, "TRY", "d");
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transferService.getTransfer(transfer.getId(), customerId))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void getTransfer_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(transferRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransfer(id, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTransfer_found_returnsResponse() {
        Transfer transfer = new Transfer(UUID.randomUUID(), "k1", customerId, sourceId,
                "iban", destId, BigDecimal.TEN, "TRY", "d");
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        TransferResponse response = transferService.getTransfer(transfer.getId(), customerId);
        assertThat(response.getId()).isEqualTo(transfer.getId());
    }

    @Test
    void listTransfers_delegatesToRepositoryWithDefaults() {
        when(transferRepository.findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(customerId), any(Instant.class), any(Instant.class)))
                .thenReturn(java.util.List.of());

        var result = transferService.listTransfers(customerId, null, null);
        assertThat(result).isEmpty();
    }
}
