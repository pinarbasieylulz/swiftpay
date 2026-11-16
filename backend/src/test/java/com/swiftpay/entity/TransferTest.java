package com.swiftpay.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferTest {

    private Transfer newTransfer() {
        return new Transfer(UUID.randomUUID(), "idem-1", UUID.randomUUID(), UUID.randomUUID(),
                "TR110006200000000011110001", UUID.randomUUID(), new BigDecimal("100.00"), "TRY", "desc");
    }

    @Test
    void constructor_setsPendingStatusAndAllFields() {
        Transfer transfer = newTransfer();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(transfer.getCurrency()).isEqualTo("TRY");
        assertThat(transfer.getDescription()).isEqualTo("desc");
        assertThat(transfer.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(transfer.getDestinationIban()).isEqualTo("TR110006200000000011110001");
        assertThat(transfer.getDestinationAccountId()).isNotNull();
        assertThat(transfer.getCreatedAt()).isNotNull();
        assertThat(transfer.getCompletedAt()).isNull();
        assertThat(transfer.getFailureReason()).isNull();
    }

    @Test
    void markCompleted_setsCompletedStatusAndTimestamp() {
        Transfer transfer = newTransfer();

        transfer.markCompleted();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCompletedAt()).isNotNull();
    }

    @Test
    void markFailed_setsFailedStatusReasonAndTimestamp() {
        Transfer transfer = newTransfer();

        transfer.markFailed("insufficient funds");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("insufficient funds");
        assertThat(transfer.getCompletedAt()).isNotNull();
    }
}
