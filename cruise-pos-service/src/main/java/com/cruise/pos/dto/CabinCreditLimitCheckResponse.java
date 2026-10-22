package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinCreditLimitCheckResponse {
    private String cabinNumber;
    private Long passengerId;
    private boolean isAllowed;
    private boolean isLocked;
    private boolean isOverLimit;
    private String lockReason;
    private BigDecimal requestedAmount;
    private BigDecimal cabinCreditLimit;
    private BigDecimal cabinCurrentBalance;
    private BigDecimal cabinAvailableLimit;
    private BigDecimal memberCreditLimit;
    private BigDecimal memberCurrentBalance;
    private BigDecimal memberAvailableLimit;
}
