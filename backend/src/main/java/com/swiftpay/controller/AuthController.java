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

    /**
     * The real TCP peer address. We deliberately do NOT trust X-Forwarded-For here: this
     * service is not deployed behind a reverse proxy in this case study, and a client-supplied
     * forwarded-for header can be set to an arbitrary value on every request, which would let
     * an attacker forge a new "IP" per attempt and bypass the rate limiter entirely. If this
     * is later deployed behind a trusted reverse proxy, that proxy's hostname/IP should be
     * allow-listed (e.g. via Spring's ForwardedHeaderFilter with a trusted-proxy check) before
     * X-Forwarded-For is honored again.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
