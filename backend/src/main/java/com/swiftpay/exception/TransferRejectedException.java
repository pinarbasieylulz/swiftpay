package com.swiftpay.exception;

import org.springframework.http.HttpStatus;

public class TransferRejectedException extends ApiException {
    public TransferRejectedException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
