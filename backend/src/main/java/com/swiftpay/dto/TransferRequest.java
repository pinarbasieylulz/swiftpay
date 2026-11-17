package com.swiftpay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {

    @NotNull
    private UUID sourceAccountId;

    @ValidIban
    private String destinationIban;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "amount must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @Size(max = 255)
    // Rejects raw control characters / CR / LF so a description can never forge an
    // extra log line (log injection) or corrupt a plain-text log file.
    @Pattern(regexp = "^[\\P{Cc}]*$", message = "description must not contain control characters")
    private String description;

    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "idempotencyKey must be alphanumeric (., _, :, - allowed)")
    private String idempotencyKey;

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(UUID sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public String getDestinationIban() {
        return destinationIban;
    }

    public void setDestinationIban(String destinationIban) {
        this.destinationIban = destinationIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
