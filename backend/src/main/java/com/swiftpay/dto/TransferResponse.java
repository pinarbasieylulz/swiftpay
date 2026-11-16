package com.swiftpay.dto;

import com.swiftpay.entity.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransferResponse {
    private UUID id;
    private UUID sourceAccountId;
    private String destinationIban;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;

    public static TransferResponse from(Transfer t) {
        TransferResponse r = new TransferResponse();
        r.id = t.getId();
        r.sourceAccountId = t.getSourceAccountId();
        r.destinationIban = t.getDestinationIban();
        r.amount = t.getAmount();
        r.currency = t.getCurrency();
        r.description = t.getDescription();
        r.status = t.getStatus().name();
        r.failureReason = t.getFailureReason();
        r.createdAt = t.getCreatedAt();
        r.completedAt = t.getCompletedAt();
        return r;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationIban() {
        return destinationIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
