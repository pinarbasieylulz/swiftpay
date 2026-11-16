package com.swiftpay.security;

import com.swiftpay.entity.Customer;
import com.swiftpay.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_found_returnsPrincipal() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, "alice", "hash", "Alice",
                new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00"));
        when(customerRepository.findByUsername("alice")).thenReturn(Optional.of(customer));

        UserDetails result = service.loadUserByUsername("alice");

        assertThat(result).isInstanceOf(CustomerPrincipal.class);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getPassword()).isEqualTo("hash");
        CustomerPrincipal principal = (CustomerPrincipal) result;
        assertThat(principal.getCustomerId()).isEqualTo(id);
        assertThat(principal.getAuthorities()).isEmpty();
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFound() {
        when(customerRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
