package com.swiftpay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.dto.LoginRequest;
import com.swiftpay.dto.LoginResponse;
import com.swiftpay.exception.GlobalExceptionHandler;
import com.swiftpay.security.LoginRateLimiter;
import com.swiftpay.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvcWith(AuthService authService, LoginRateLimiter rateLimiter) {
        return MockMvcBuilders.standaloneSetup(new AuthController(authService, rateLimiter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String username, String password) throws Exception {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return objectMapper.writeValueAsString(r);
    }

    @Test
    void login_success_recordsSuccessAndReturnsToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginResponse("tok", 3600000L));
        LoginRateLimiter rateLimiter = new LoginRateLimiter();

        mockMvcWith(authService, rateLimiter).perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "9.9.9.9, 1.1.1.1")
                        .content(body("alice", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"));
    }

    @Test
    void login_badCredentials_recordsFailureAndReturns401() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));
        LoginRateLimiter rateLimiter = new LoginRateLimiter();
        MockMvc mockMvc = mockMvcWith(authService, rateLimiter);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blockedByRateLimiter_returns429() throws Exception {
        AuthService authService = mock(AuthService.class);
        LoginRateLimiter rateLimiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure("127.0.0.1", "alice");
        }

        mockMvcWith(authService, rateLimiter).perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice", "wrong")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
