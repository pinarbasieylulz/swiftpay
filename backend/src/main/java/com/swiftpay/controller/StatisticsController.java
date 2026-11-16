package com.swiftpay.controller;

import com.swiftpay.dto.MonthlyStatisticResponse;
import com.swiftpay.service.StatisticsService;
import com.swiftpay.util.CurrentCustomer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/monthly")
    public List<MonthlyStatisticResponse> monthly() {
        return statisticsService.getMonthlyStatistics(CurrentCustomer.id());
    }
}
