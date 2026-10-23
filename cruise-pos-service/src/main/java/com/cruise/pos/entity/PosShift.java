package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.ShiftStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_shifts", indexes = {
        @Index(name = "idx_shift_station", columnList = "station_id"),
        @Index(name = "idx_shift_cashier", columnList = "cashier_id"),
        @Index(name = "idx_shift_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosShift extends BaseEntity {

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "cashier_id", nullable = false, length = 64)
    private String cashierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    @Builder.Default
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "starting_cash", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal startingCash = BigDecimal.ZERO;

    @Column(name = "ending_cash", precision = 12, scale = 2)
    private BigDecimal endingCash;

    @Column(name = "total_sales", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_refunds", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Column(name = "net_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal netRevenue = BigDecimal.ZERO;

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Integer transactionCount = 0;

    @Column(name = "total_cash_drops", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCashDrops = BigDecimal.ZERO;

    @Column(name = "closed_by_manager_id", length = 64)
    private String closedByManagerId;

    public void addSale(BigDecimal amount) {
        this.totalSales = this.totalSales.add(amount);
        this.netRevenue = this.totalSales.subtract(this.totalRefunds);
        this.transactionCount++;
    }

    public void addRefund(BigDecimal amount) {
        this.totalRefunds = this.totalRefunds.add(amount);
        this.netRevenue = this.totalSales.subtract(this.totalRefunds);
    }

    public void addCashDrop(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalCashDrops = this.totalCashDrops.add(amount);
        }
    }
}
