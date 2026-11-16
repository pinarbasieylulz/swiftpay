package com.swiftpay.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedCustomException extends ApiException {
    public AccessDeniedCustomException(String message) {
        super("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}
