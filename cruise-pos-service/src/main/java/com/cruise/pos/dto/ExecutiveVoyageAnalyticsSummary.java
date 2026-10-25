package com.cruise.pos.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExecutiveVoyageAnalyticsSummary(
        VoyageFinancialClosureDto latestVoyage,
        BigDecimal fleetAverageRevPCD,
        BigDecimal fleetAverageOccupancy,
        BigDecimal fleetTotalGrossRevenue,
        List<VoyageFinancialClosureDto> historicalTrend
) {}
