package com.cruise.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity maintaining the persisted ship safe balance ledger state under pessimistic lock.
 */
@Entity
@Table(name = "pos_safe_ledgers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSafeLedger {

    @Id
    @Column(name = "safe_id", nullable = false, length = 64)
    private String safeId;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;
}
