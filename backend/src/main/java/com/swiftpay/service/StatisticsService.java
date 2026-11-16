package com.swiftpay.service;

import com.swiftpay.dto.MonthlyStatisticResponse;
import com.swiftpay.repository.TransferRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatisticsService {

    private static final int MONTHS_WINDOW = 6;
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransferRepository transferRepository;

    public StatisticsService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    public List<MonthlyStatisticResponse> getMonthlyStatistics(UUID customerId) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        YearMonth earliestMonth = currentMonth.minusMonths(MONTHS_WINDOW - 1);
        var from = earliestMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, long[]> counts = new HashMap<>();
        Map<String, BigDecimal> sums = new HashMap<>();

        for (Object[] row : transferRepository.monthlyCompletedStats(customerId, from)) {
            LocalDate monthStart = toLocalDate(row[0]);
            String key = monthStart.format(KEY_FORMAT);
            long count = ((Number) row[1]).longValue();
            BigDecimal total = (BigDecimal) row[2];
            counts.put(key, new long[]{count});
            sums.put(key, total);
        }

        List<MonthlyStatisticResponse> result = new ArrayList<>();
        for (int i = 0; i < MONTHS_WINDOW; i++) {
            YearMonth month = currentMonth.minusMonths(i);
            String key = month.format(KEY_FORMAT);
            long count = counts.containsKey(key) ? counts.get(key)[0] : 0L;
            BigDecimal total = sums.getOrDefault(key, BigDecimal.ZERO);
            result.add(new MonthlyStatisticResponse(key, count, total));
        }
        return result;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
        throw new IllegalStateException("Unsupported date type: " + value.getClass());
    }
}
