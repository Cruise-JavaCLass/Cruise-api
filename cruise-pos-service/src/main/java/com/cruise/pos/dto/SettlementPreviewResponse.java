package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả xem trước tất toán của một folio cabin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPreviewResponse {

    private String cabinNumber;

    private String primaryGuestName;

    private String currency;

    private BigDecimal subtotal;

    private BigDecimal reversalsTotal;

    private BigDecimal discountTotal;

    private BigDecimal taxTotal;

    private BigDecimal gratuityTotal;

    private BigDecimal netAmountDue;

    private BigDecimal heldDisputeAmount;

    private int openDisputeCount;

    private boolean eligibleForSettlement;

    @Builder.Default
    private List<String> blockingReasons = new ArrayList<>();

    @Builder.Default
    private List<SettlementLineItemDto> lineItems = new ArrayList<>();
}
