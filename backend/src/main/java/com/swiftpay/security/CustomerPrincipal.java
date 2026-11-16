package com.swiftpay.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomerPrincipal implements UserDetails {

    private final UUID customerId;
    private final String username;
    private final String passwordHash;

    public CustomerPrincipal(UUID customerId, String username, String passwordHash) {
        this.customerId = customerId;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
