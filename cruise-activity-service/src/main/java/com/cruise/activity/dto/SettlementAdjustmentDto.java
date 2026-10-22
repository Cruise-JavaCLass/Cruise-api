package com.cruise.activity.dto;

import com.cruise.activity.enums.AdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO phản hồi chi tiết điều chỉnh / tranh chấp (KAN-332, KAN-334)
 */
public record SettlementAdjustmentDto(
        Long id,
        AdjustmentType adjustmentType,
        BigDecimal amount,
        String invoiceReference,
        String explanation,
        String recordedBy,
        Instant createdAt
) {}
