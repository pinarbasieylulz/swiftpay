package com.swiftpay.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void constructorAndGetters_exposeAllFields() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "alice", "hash", "Alice Doe",
                new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00"));

        assertThat(customer.getId()).isEqualTo(id);
        assertThat(customer.getUsername()).isEqualTo("alice");
        assertThat(customer.getPasswordHash()).isEqualTo("hash");
        assertThat(customer.getFullName()).isEqualTo("Alice Doe");
        assertThat(customer.getPerTransferLimit()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(customer.getDailyLimit()).isEqualTo(new BigDecimal("100000.00"));
        assertThat(customer.getMonthlyLimit()).isEqualTo(new BigDecimal("500000.00"));
        assertThat(customer.getCreatedAt()).isNotNull();
    }
}
