package com.swiftpay.dto;

import java.time.Instant;

public class ApiErrorResponse {
    private String errorCode;
    private String message;
    private String traceId;
    private Instant timestamp;

    public ApiErrorResponse(String errorCode, String message, String traceId) {
        this.errorCode = errorCode;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
