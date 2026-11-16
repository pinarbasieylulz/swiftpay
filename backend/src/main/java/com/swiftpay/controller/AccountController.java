package com.swiftpay.controller;

import com.swiftpay.dto.AccountResponse;
import com.swiftpay.dto.LimitResponse;
import com.swiftpay.service.AccountService;
import com.swiftpay.util.CurrentCustomer;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public List<AccountResponse> myAccounts() {
        return accountService.listAccounts(CurrentCustomer.id());
    }

    @GetMapping("/me/limits")
    public LimitResponse myLimits() {
        return accountService.getLimits(CurrentCustomer.id());
    }
}
