package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_journal_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosJournalEntry extends BaseEntity {

    @Column(name = "pos_transaction_id", nullable = false, unique = true, length = 64)
    private String posTransactionId;

    @Column(name = "terminal_id", nullable = false, length = 64)
    private String terminalId;

    @Column(name = "service_area", nullable = false, length = 64)
    private String serviceArea;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "passenger_name", length = 128)
    private String passengerName;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Column(name = "is_synced", nullable = false)
    @Builder.Default
    private Boolean isSynced = false;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "items_summary", length = 512)
    private String itemsSummary;
}
