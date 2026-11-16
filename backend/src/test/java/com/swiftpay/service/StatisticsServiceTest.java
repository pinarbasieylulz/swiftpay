package com.swiftpay.service;

import com.swiftpay.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getMonthlyStatistics_returnsSixMonthsNewestFirstWithZeroFill() {
        UUID customerId = UUID.randomUUID();
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant currentMonthStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Object[] row = new Object[]{
                java.sql.Timestamp.from(currentMonthStart), 2L, new BigDecimal("300.00")
        };
        when(transferRepository.monthlyCompletedStats(any(), any())).thenReturn(List.<Object[]>of(row));

        var result = statisticsService.getMonthlyStatistics(customerId);

        assertThat(result).hasSize(6);
        assertThat(result.get(0).getMonth()).isEqualTo(currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        assertThat(result.get(0).getTotalTransferCount()).isEqualTo(2L);
        assertThat(result.get(0).getTotalTransferredAmount()).isEqualTo(new BigDecimal("300.00"));
        assertThat(result.get(1).getTotalTransferCount()).isEqualTo(0L);
        assertThat(result.get(1).getTotalTransferredAmount()).isEqualTo(BigDecimal.ZERO);
        // newest-to-oldest ordering
        for (int i = 0; i < 5; i++) {
            YearMonth a = YearMonth.parse(result.get(i).getMonth());
            YearMonth b = YearMonth.parse(result.get(i + 1).getMonth());
            assertThat(a.isAfter(b)).isTrue();
        }
    }

    @Test
    void getMonthlyStatistics_rowWithLocalDateTime_isParsedCorrectly() {
        UUID customerId = UUID.randomUUID();
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        java.time.LocalDateTime ldt = currentMonth.atDay(1).atStartOfDay();

        Object[] row = new Object[]{ldt, 1L, new BigDecimal("50.00")};
        when(transferRepository.monthlyCompletedStats(any(), any())).thenReturn(List.<Object[]>of(row));

        var result = statisticsService.getMonthlyStatistics(customerId);

        assertThat(result.get(0).getTotalTransferCount()).isEqualTo(1L);
        assertThat(result.get(0).getTotalTransferredAmount()).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void getMonthlyStatistics_rowWithInstant_isParsedCorrectly() {
        UUID customerId = UUID.randomUUID();
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant instant = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Object[] row = new Object[]{instant, 3L, new BigDecimal("75.00")};
        when(transferRepository.monthlyCompletedStats(any(), any())).thenReturn(List.<Object[]>of(row));

        var result = statisticsService.getMonthlyStatistics(customerId);

        assertThat(result.get(0).getTotalTransferCount()).isEqualTo(3L);
        assertThat(result.get(0).getTotalTransferredAmount()).isEqualTo(new BigDecimal("75.00"));
    }

    @Test
    void getMonthlyStatistics_rowWithUnsupportedDateType_throwsIllegalState() {
        UUID customerId = UUID.randomUUID();
        Object[] row = new Object[]{"not-a-date", 1L, new BigDecimal("10.00")};
        when(transferRepository.monthlyCompletedStats(any(), any())).thenReturn(List.<Object[]>of(row));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> statisticsService.getMonthlyStatistics(customerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported date type");
    }

    @Test
    void getMonthlyStatistics_noData_returnsAllZero() {
        when(transferRepository.monthlyCompletedStats(any(), any())).thenReturn(List.of());
        var result = statisticsService.getMonthlyStatistics(UUID.randomUUID());
        assertThat(result).hasSize(6);
        result.forEach(r -> {
            assertThat(r.getTotalTransferCount()).isZero();
            assertThat(r.getTotalTransferredAmount()).isEqualTo(BigDecimal.ZERO);
        });
    }
}
