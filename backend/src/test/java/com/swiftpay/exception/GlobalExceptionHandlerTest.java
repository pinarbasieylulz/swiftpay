package com.swiftpay.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiException_mapsStatusAndErrorCode() {
        ApiException ex = new TransferConflictException("conflict happened");

        ResponseEntity<com.swiftpay.dto.ApiErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("TRANSFER_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("conflict happened");
        assertThat(response.getBody().getTraceId()).isNotBlank();
    }

    @Test
    void handleValidation_joinsFieldErrorsAndReturns400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("request", "amount", "must be positive")));

        ResponseEntity<com.swiftpay.dto.ApiErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("amount: must be positive");
    }

    @Test
    void handleAuth_badCredentials_returns401() {
        ResponseEntity<com.swiftpay.dto.ApiErrorResponse> response = handler.handleAuth(new BadCredentialsException("bad creds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTH_ERROR");
    }

    @Test
    void handleAuth_accessDenied_returns401() {
        ResponseEntity<com.swiftpay.dto.ApiErrorResponse> response = handler.handleAuth(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTH_ERROR");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage() {
        ResponseEntity<com.swiftpay.dto.ApiErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Unexpected error occurred");
    }
}
