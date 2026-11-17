package com.swiftpay.service;

import com.swiftpay.entity.Account;
import com.swiftpay.entity.Customer;
import com.swiftpay.exception.AccessDeniedCustomException;
import com.swiftpay.exception.ResourceNotFoundException;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.repository.TransferRepository;
import com.swiftpay.config.BusinessProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TransferRepository transferRepository;

    private AccountService accountService;

    private UUID customerId;
    private Account account;
    private Customer customer;

    @BeforeEach
    void setUp() {
        BusinessProperties businessProperties = new BusinessProperties();
        businessProperties.getBusiness().setZoneId("Europe/Istanbul");
        accountService = new AccountService(accountRepository, customerRepository, transferRepository, businessProperties);
        customerId = UUID.randomUUID();
        account = new Account(UUID.randomUUID(), customerId, "TR1", new BigDecimal("500.00"), "TRY");
        customer = new Customer(customerId, "alice", "hash", "Alice",
                new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00"));
    }

    @Test
    void listAccounts_returnsMappedAccounts() {
        when(accountRepository.findByCustomerId(customerId)).thenReturn(List.of(account));
        var result = accountService.listAccounts(customerId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIban()).isEqualTo("TR1");
    }

    @Test
    void getOwnedAccount_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getOwnedAccount(id, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOwnedAccount_notOwned_throwsAccessDenied() {
        Account other = new Account(UUID.randomUUID(), UUID.randomUUID(), "TR2", BigDecimal.ZERO, "TRY");
        when(accountRepository.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> accountService.getOwnedAccount(other.getId(), customerId))
                .isInstanceOf(AccessDeniedCustomException.class);
    }

    @Test
    void getLimits_computesRemainingFromSpentAmounts() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomerId(customerId)).thenReturn(List.of(account));
        when(transferRepository.sumCompletedAmount(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));

        var result = accountService.getLimits(customerId);

        assertThat(result.getDailyRemaining()).isEqualTo(new BigDecimal("99000.00"));
        assertThat(result.getMonthlyRemaining()).isEqualTo(new BigDecimal("499000.00"));
    }

    @Test
    void getLimits_spentExceedsLimit_clampsRemainingToZero() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomerId(customerId)).thenReturn(List.of(account));
        when(transferRepository.sumCompletedAmount(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1000000.00"));

        var result = accountService.getLimits(customerId);

        assertThat(result.getDailyRemaining()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getMonthlyRemaining()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void getLimits_customerNotFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getLimits(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
