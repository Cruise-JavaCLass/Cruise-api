package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private String transactionUuid;
    private String idempotencyKey;
    private Long shiftId;
    private String stationId;
    private String cashierId;
    private Long passengerId;
    private String cabinNumber;
    private String guestName;
    private PaymentMethod paymentMethod;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal gratuity;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal refundedAmount;
    private BigDecimal remainingRefundableAmount;
    private String currency;
    private Boolean settled;
    private Long originalTransactionId;
    private String voidReason;
    private String notes;
    private Instant createdAt;
    private List<TransactionItemResponse> items;
}
