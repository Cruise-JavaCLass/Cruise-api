package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "guest_folio_ledgers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestFolioLedger extends BaseEntity {

    @Column(name = "folio_id", nullable = false, length = 64)
    private String folioId;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "passenger_name", length = 128)
    private String passengerName;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(name = "description", nullable = false, length = 256)
    private String description;

    @Column(name = "service_area", nullable = false, length = 64)
    private String serviceArea;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_reversal", nullable = false)
    @Builder.Default
    private Boolean isReversal = false;

    @Column(name = "linked_dispute_id")
    private Long linkedDisputeId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;
}
