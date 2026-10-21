package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng chi tiết trên bảng kê tất toán / hoá đơn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementLineItemDto {

    private String description;

    private String serviceArea;

    private Instant postedAt;

    private BigDecimal amount;

    private boolean isReversal;
}
