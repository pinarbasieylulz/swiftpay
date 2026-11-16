package com.swiftpay.repository;

import com.swiftpay.entity.Transfer;
import com.swiftpay.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    Page<Transfer> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<Transfer> findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID customerId, Instant start, Instant end);

    @Query("select coalesce(sum(t.amount), 0) from Transfer t " +
            "where t.sourceAccountId = :accountId and t.status = :status " +
            "and t.createdAt >= :from and t.createdAt < :to")
    BigDecimal sumCompletedAmount(@Param("accountId") UUID accountId,
                                   @Param("status") TransferStatus status,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    @Query(value = "select date_trunc('month', created_at) as month, count(*) as cnt, coalesce(sum(amount), 0) as total " +
            "from transfers " +
            "where customer_id = :customerId and status = 'COMPLETED' " +
            "and created_at >= :from " +
            "group by month", nativeQuery = true)
    List<Object[]> monthlyCompletedStats(@Param("customerId") UUID customerId, @Param("from") Instant from);
}
