package com.swiftpay.dto;

import java.math.BigDecimal;

public class LimitResponse {
    private BigDecimal perTransferLimit;
    private BigDecimal dailyLimit;
    private BigDecimal dailyRemaining;
    private BigDecimal monthlyLimit;
    private BigDecimal monthlyRemaining;

    public LimitResponse(BigDecimal perTransferLimit, BigDecimal dailyLimit, BigDecimal dailyRemaining,
                          BigDecimal monthlyLimit, BigDecimal monthlyRemaining) {
        this.perTransferLimit = perTransferLimit;
        this.dailyLimit = dailyLimit;
        this.dailyRemaining = dailyRemaining;
        this.monthlyLimit = monthlyLimit;
        this.monthlyRemaining = monthlyRemaining;
    }

    public BigDecimal getPerTransferLimit() {
        return perTransferLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public BigDecimal getDailyRemaining() {
        return dailyRemaining;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public BigDecimal getMonthlyRemaining() {
        return monthlyRemaining;
    }
}
