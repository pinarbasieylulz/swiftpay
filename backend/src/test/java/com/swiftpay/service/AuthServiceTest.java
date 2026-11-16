package com.swiftpay.service;

import com.swiftpay.dto.LoginRequest;
import com.swiftpay.entity.Customer;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer(UUID.randomUUID(), "alice", "hash", "Alice",
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
    }

    private LoginRequest req(String u, String p) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u);
        r.setPassword(p);
        return r;
    }

    @Test
    void login_validCredentials_returnsToken() {
        when(customerRepository.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwtUtil.generateToken(customer.getId(), "alice")).thenReturn("tok");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        var response = authService.login(req("alice", "pw"));

        assertThat(response.getToken()).isEqualTo("tok");
    }

    @Test
    void login_unknownUser_throwsBadCredentials() {
        when(customerRepository.findByUsername("bob")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(req("bob", "pw")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownUser_stillInvokesPasswordEncoder_toAvoidTimingLeak() {
        when(customerRepository.findByUsername("bob")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req("bob", "pw")))
                .isInstanceOf(BadCredentialsException.class);

        // The encoder must be invoked even when the user does not exist, so that the
        // response time does not reveal whether the username is valid.
        org.mockito.Mockito.verify(passwordEncoder)
                .matches(org.mockito.ArgumentMatchers.eq("pw"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(customerRepository.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> authService.login(req("alice", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
