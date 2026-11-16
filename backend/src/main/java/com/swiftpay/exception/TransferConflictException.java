package com.swiftpay.exception;

import org.springframework.http.HttpStatus;

public class TransferConflictException extends ApiException {
    public TransferConflictException(String message) {
        super("TRANSFER_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
