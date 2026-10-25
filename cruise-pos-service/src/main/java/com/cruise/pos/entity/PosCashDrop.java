package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity tracking mid-shift cash drops from terminal cash drawer to the ship's safe.
 * Maintains tamper-evident audit records and dual signature authorizations.
 */
@Entity
@Table(name = "pos_cash_drops", indexes = {
        @Index(name = "idx_cash_drop_shift", columnList = "shift_id"),
        @Index(name = "idx_cash_drop_receipt", columnList = "receipt_number", unique = true),
        @Index(name = "idx_cash_drop_station", columnList = "station_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCashDrop extends BaseEntity {

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "cashier_id", nullable = false, length = 64)
    private String cashierId;

    @Column(name = "witness_id", nullable = false, length = 64)
    private String witnessId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "safe_balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal safeBalanceAfter;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 64)
    private String receiptNumber;

    @Column(name = "currency_breakdown", length = 1024)
    private String currencyBreakdown;

    @Column(name = "dropped_at", nullable = false)
    @Builder.Default
    private Instant droppedAt = Instant.now();

    @Column(name = "dual_signature_verified", nullable = false)
    @Builder.Default
    private Boolean dualSignatureVerified = true;

    @Column(name = "notes", length = 512)
    private String notes;
}
