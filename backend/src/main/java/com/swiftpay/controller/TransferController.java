package com.swiftpay.controller;

import com.swiftpay.dto.TransferRequest;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.service.TransferService;
import com.swiftpay.util.CurrentCustomer;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.createTransfer(CurrentCustomer.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return transferService.getTransfer(id, CurrentCustomer.id());
    }

    @GetMapping
    public List<TransferResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return transferService.listTransfers(CurrentCustomer.id(), startDate, endDate);
    }
}
