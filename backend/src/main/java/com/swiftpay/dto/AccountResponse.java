package com.swiftpay.dto;

import com.swiftpay.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountResponse {
    private UUID id;
    private String iban;
    private BigDecimal balance;
    private String currency;

    public static AccountResponse from(Account a) {
        AccountResponse r = new AccountResponse();
        r.id = a.getId();
        r.iban = a.getIban();
        r.balance = a.getBalance();
        r.currency = a.getCurrency();
        return r;
    }

    public UUID getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }
}
