package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bản ghi tất toán cuối cùng của một folio cabin (bill settlement).
 * Mỗi folio chỉ có tối đa một settlement ở trạng thái COMPLETED.
 */
@Entity
@Table(name = "pos_folio_settlements", indexes = {
        @Index(name = "idx_settlement_uuid", columnList = "settlement_uuid", unique = true),
        @Index(name = "idx_settlement_cabin", columnList = "cabin_number"),
        @Index(name = "idx_settlement_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_settlement_invoice", columnList = "invoice_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolioSettlement extends BaseEntity {

    @Column(name = "settlement_uuid", nullable = false, unique = true, length = 64)
    private String settlementUuid;

    @Column(name = "folio_id", nullable = false)
    private Long folioId;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "primary_guest_name", length = 128)
    private String primaryGuestName;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "reversals_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal reversalsTotal = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "gratuity", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal gratuity = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "amount_tendered", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountTendered;

    @Column(name = "change_given", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal changeGiven = BigDecimal.ZERO;

    @Column(name = "held_dispute_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal heldDisputeAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Column(name = "cashier_id", nullable = false, length = 64)
    private String cashierId;

    @Column(name = "force_with_hold", nullable = false)
    @Builder.Default
    private Boolean forceWithHold = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.COMPLETED;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    /**
     * Ảnh chụp bảng kê tại thời điểm tất toán (JSON), dùng lại cho hoá đơn PDF
     * để hoá đơn không phụ thuộc dữ liệu thay đổi về sau.
     */
    @Column(name = "line_items_json", columnDefinition = "TEXT")
    private String lineItemsJson;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;
}
