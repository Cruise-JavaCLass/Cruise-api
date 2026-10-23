package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.PaymentGatewayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_payment_orders", indexes = {
        @Index(name = "idx_pay_order_payment_ref", columnList = "payment_reference", unique = true),
        @Index(name = "idx_pay_order_idem_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_pay_order_cabin", columnList = "cabin_number"),
        @Index(name = "idx_pay_order_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseEntity {

    @Column(name = "payment_reference", nullable = false, unique = true, length = 64)
    private String paymentReference;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "bank_code", length = 32)
    @Builder.Default
    private String bankCode = "970436"; // Vietcombank default

    @Column(name = "account_number", length = 64)
    @Builder.Default
    private String accountNumber = "1028391829";

    @Column(name = "account_name", length = 128)
    @Builder.Default
    private String accountName = "CRUISE LINE OPERATIONS CORP";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "qr_data_url", columnDefinition = "TEXT")
    private String qrDataUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PaymentGatewayStatus status = PaymentGatewayStatus.PENDING;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "bank_transaction_id", length = 128)
    private String bankTransactionId;

    @Column(name = "confirmed_amount", precision = 12, scale = 2)
    private BigDecimal confirmedAmount;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
