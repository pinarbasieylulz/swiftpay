package com.swiftpay.util;

import com.swiftpay.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    public static UUID id() {
        CustomerPrincipal principal = (CustomerPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getCustomerId();
    }
}
