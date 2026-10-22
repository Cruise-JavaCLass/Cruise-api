package com.cruise.activity.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO tổng hợp báo cáo quyết toán theo nhà cung cấp (KAN-332, AC4, KAN-335)
 */
public record ProviderSettlementSummaryResponse(
        String providerId,
        String providerName,
        Long voyageId,
        int totalExcursions,
        int totalRegisteredPax,
        int totalActualPax,
        int totalNoShowPax,
        int totalBillablePax,
        BigDecimal totalBaseAmount,
        BigDecimal totalAdjustedAmount,
        BigDecimal grandTotalAmount,
        String currency,
        List<SettlementDetailDto> settlements
) {}
