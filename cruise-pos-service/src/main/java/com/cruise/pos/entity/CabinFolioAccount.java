package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.FolioStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_cabin_folios", indexes = {
        @Index(name = "idx_cabin_number", columnList = "cabin_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabinFolioAccount extends BaseEntity {

    @Column(name = "cabin_number", nullable = false, unique = true, length = 32)
    private String cabinNumber;

    @Column(name = "primary_guest_name", nullable = false, length = 128)
    private String primaryGuestName;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.valueOf(1500.00);

    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private FolioStatus status = FolioStatus.ACTIVE;

    public BigDecimal getAvailableLimit() {
        return creditLimit.subtract(currentBalance);
    }

    public boolean canDebit(BigDecimal amount) {
        if (status != FolioStatus.ACTIVE) {
            return false;
        }
        return getAvailableLimit().compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        this.currentBalance = this.currentBalance.add(amount);
    }

    public void credit(BigDecimal amount) {
        this.currentBalance = this.currentBalance.subtract(amount);
        if (this.currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            this.currentBalance = BigDecimal.ZERO;
        }
    }
}
