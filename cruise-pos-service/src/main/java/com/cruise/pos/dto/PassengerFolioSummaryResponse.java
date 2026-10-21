package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerFolioSummaryResponse {

    private Long passengerId;
    private String cabinNumber;
    private String passengerName;
    private BigDecimal currentBalance;
    private BigDecimal creditLimit;
    private BigDecimal availableLimit;
    private BigDecimal spendingPercentage;
    private boolean warningThresholdReached; // >= 80%
    private boolean limitExceeded; // >= 100%
    private String status;
    private Map<String, BigDecimal> spendingByCategory;
}
