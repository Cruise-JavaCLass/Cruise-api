package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.ObcVoucherStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "service_recovery_obc_vouchers", indexes = {
        @Index(name = "idx_obc_voucher_code", columnList = "voucher_code", unique = true),
        @Index(name = "idx_obc_case_id", columnList = "case_id"),
        @Index(name = "idx_obc_cabin", columnList = "cabin_number"),
        @Index(name = "idx_obc_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecoveryObcVoucher extends BaseEntity {

    @Column(name = "voucher_code", nullable = false, unique = true, length = 64)
    private String voucherCode; // e.g. "OBC-REC-89211"

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "case_number", nullable = false, length = 64)
    private String caseNumber;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", nullable = false, length = 128)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "credit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ObcVoucherStatus status = ObcVoucherStatus.ISSUED;

    @Column(name = "apology_title", nullable = false, length = 128)
    private String apologyTitle;

    @Column(name = "apology_letter_body", columnDefinition = "TEXT")
    private String apologyLetterBody;

    @Column(name = "authorized_by", nullable = false, length = 64)
    private String authorizedBy; // e.g. "DIRECTOR_MARCUS"

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "folio_transaction_id", length = 64)
    private String folioTransactionId;
}
