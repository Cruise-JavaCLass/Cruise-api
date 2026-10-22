package com.cruise.activity.dto;

import com.cruise.activity.enums.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO phản hồi chi tiết quyết toán nhà cung cấp tour (KAN-332, KAN-333)
 */
public record SettlementDetailDto(
        Long id,
        String providerId,
        String providerName,
        Long excursionId,
        String excursionTitle,
        Long voyageId,
        Long portId,
        String portName,
        BigDecimal agreedUnitRate,
        Integer minGuaranteePax,
        Integer registeredPax,
        Integer actualPax,
        Integer noShowPax,
        Integer billablePax,
        BigDecimal baseAmount,
        BigDecimal adjustedAmount,
        BigDecimal totalAmount,
        String currency,
        SettlementStatus status,
        String approvedBy,
        Instant approvedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<SettlementAdjustmentDto> adjustments
) {}
