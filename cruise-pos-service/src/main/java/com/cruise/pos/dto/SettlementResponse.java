package com.cruise.pos.dto;

import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả tất toán folio. Trả về nguyên văn khi replay idempotency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettlementResponse {

    private String settlementUuid;

    private String invoiceNumber;

    private String cabinNumber;

    private BigDecimal netAmount;

    private BigDecimal amountTendered;

    private BigDecimal changeGiven;

    private PaymentMethod paymentMethod;

    private String currency;

    private Instant settledAt;

    private FolioStatus folioStatus;
}
