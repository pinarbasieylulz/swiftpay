package com.swiftpay.service;

import com.swiftpay.dto.LoginRequest;
import com.swiftpay.dto.LoginResponse;
import com.swiftpay.entity.Customer;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // Valid BCrypt hash of a random value, with no corresponding account. Used to keep the
    // password comparison on the same code path (and therefore the same rough timing) whether
    // the username exists or not, so response time cannot be used to enumerate usernames.
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6jPeTnHdU7RwmUAdCpuhDLTR8bByq";

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByUsername(request.getUsername()).orElse(null);
        String hashToCheck = customer != null ? customer.getPasswordHash() : DUMMY_HASH;
        boolean matches = passwordEncoder.matches(request.getPassword(), hashToCheck);
        if (customer == null || !matches) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String token = jwtUtil.generateToken(customer.getId(), customer.getUsername());
        return new LoginResponse(token, jwtUtil.getExpirationMs());
    }
}
