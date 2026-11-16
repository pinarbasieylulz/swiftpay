package com.swiftpay.dto;

import java.math.BigDecimal;

public class MonthlyStatisticResponse {
    private String month;
    private long totalTransferCount;
    private BigDecimal totalTransferredAmount;

    public MonthlyStatisticResponse(String month, long totalTransferCount, BigDecimal totalTransferredAmount) {
        this.month = month;
        this.totalTransferCount = totalTransferCount;
        this.totalTransferredAmount = totalTransferredAmount;
    }

    public String getMonth() {
        return month;
    }

    public long getTotalTransferCount() {
        return totalTransferCount;
    }

    public BigDecimal getTotalTransferredAmount() {
        return totalTransferredAmount;
    }
}
