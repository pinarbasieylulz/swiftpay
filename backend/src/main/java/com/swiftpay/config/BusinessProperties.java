package com.swiftpay.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "swiftpay")
public class BusinessProperties {

    private final Business business = new Business();
    private final Limits limits = new Limits();

    public Business getBusiness() {
        return business;
    }

    public Limits getLimits() {
        return limits;
    }

    @PostConstruct
    public void validate() {
        ZoneId.of(business.getZoneId());

        BigDecimal perTransfer = limits.getPerTransfer();
        BigDecimal dailyTotal = limits.getDailyTotal();
        BigDecimal monthlyTotal = limits.getMonthlyTotal();

        if (perTransfer.signum() <= 0 || dailyTotal.signum() <= 0 || monthlyTotal.signum() <= 0) {
            throw new IllegalStateException(
                    "swiftpay.limits values must all be positive (per-transfer=" + perTransfer
                            + ", daily-total=" + dailyTotal + ", monthly-total=" + monthlyTotal + ")");
        }
        if (dailyTotal.compareTo(perTransfer) < 0) {
            throw new IllegalStateException("swiftpay.limits.daily-total must be >= per-transfer limit");
        }
        if (monthlyTotal.compareTo(dailyTotal) < 0) {
            throw new IllegalStateException("swiftpay.limits.monthly-total must be >= daily-total limit");
        }
    }

    public static class Business {
        private String zoneId = "Europe/Istanbul";

        public String getZoneId() {
            return zoneId;
        }

        public void setZoneId(String zoneId) {
            this.zoneId = zoneId;
        }
    }

    public static class Limits {
        private BigDecimal perTransfer;
        private BigDecimal dailyTotal;
        private BigDecimal monthlyTotal;

        public BigDecimal getPerTransfer() {
            return perTransfer;
        }

        public void setPerTransfer(BigDecimal perTransfer) {
            this.perTransfer = perTransfer;
        }

        public BigDecimal getDailyTotal() {
            return dailyTotal;
        }

        public void setDailyTotal(BigDecimal dailyTotal) {
            this.dailyTotal = dailyTotal;
        }

        public BigDecimal getMonthlyTotal() {
            return monthlyTotal;
        }

        public void setMonthlyTotal(BigDecimal monthlyTotal) {
            this.monthlyTotal = monthlyTotal;
        }
    }
}
