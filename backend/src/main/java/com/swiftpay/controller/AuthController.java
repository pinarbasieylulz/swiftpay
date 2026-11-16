package com.swiftpay.controller;

import com.swiftpay.dto.LoginRequest;
import com.swiftpay.dto.LoginResponse;
import com.swiftpay.exception.ApiException;
import com.swiftpay.security.LoginRateLimiter;
import com.swiftpay.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String username = request.getUsername();

        if (rateLimiter.isBlocked(ip, username)) {
            throw new ApiException("TOO_MANY_ATTEMPTS",
                    "Too many failed login attempts. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            LoginResponse response = authService.login(request);
            rateLimiter.recordSuccess(ip, username);
            return response;
        } catch (BadCredentialsException ex) {
            rateLimiter.recordFailure(ip, username);
            throw ex;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
