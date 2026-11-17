package com.swiftpay.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    @Test
    void constructorAndGetters_exposeAllFields() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Account account = new Account(id, customerId, "TR180006200000000011110001", new BigDecimal("100.00"), "TRY");

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getIban()).isEqualTo("TR180006200000000011110001");
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("100.00"));
        assertThat(account.getCurrency()).isEqualTo("TRY");
        assertThat(account.getVersion()).isZero();
        assertThat(account.getCreatedAt()).isNotNull();
    }

    @Test
    void debitAndCredit_adjustBalance() {
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID(), "TR1", new BigDecimal("100.00"), "TRY");

        account.debit(new BigDecimal("40.00"));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("60.00"));

        account.credit(new BigDecimal("15.00"));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("75.00"));
    }
}
