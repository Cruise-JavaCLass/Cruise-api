package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pos_transactions", indexes = {
        @Index(name = "idx_tx_uuid", columnList = "transaction_uuid", unique = true),
        @Index(name = "idx_tx_idempotency", columnList = "idempotency_key"),
        @Index(name = "idx_tx_shift", columnList = "shift_id"),
        @Index(name = "idx_tx_cabin", columnList = "cabin_number"),
        @Index(name = "idx_tx_cabin_created", columnList = "cabin_number, created_at"),
        @Index(name = "idx_tx_status", columnList = "status"),
        @Index(name = "idx_tx_station", columnList = "station_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosTransaction extends BaseEntity {

    @Column(name = "transaction_uuid", nullable = false, unique = true, length = 64)
    private String transactionUuid;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "cashier_id", nullable = false, length = 64)
    private String cashierId;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(name = "cabin_number", length = 32)
    private String cabinNumber;

    @Column(name = "guest_name", length = 128)
    private String guestName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    @Builder.Default
    private TransactionType type = TransactionType.CHARGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "gratuity", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal gratuity = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "settled", nullable = false)
    @Builder.Default
    private Boolean settled = false;

    @Column(name = "original_transaction_id")
    private Long originalTransactionId;

    @Column(name = "void_reason", length = 512)
    private String voidReason;

    @Column(name = "notes", length = 512)
    private String notes;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<PosTransactionItem> items = new ArrayList<>();

    public void addItem(PosTransactionItem item) {
        items.add(item);
        item.setTransaction(this);
    }

    public BigDecimal getRemainingRefundableAmount() {
        return totalAmount.subtract(refundedAmount);
    }
}
