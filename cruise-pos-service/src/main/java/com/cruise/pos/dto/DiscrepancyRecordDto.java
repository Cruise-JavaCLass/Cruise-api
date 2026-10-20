package com.cruise.pos.dto;

import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DiscrepancyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyRecordDto {
    private Long id;
    private Long batchId;
    private String transactionId;
    private String folioId;
    private String cabinNumber;
    private String passengerName;
    private String serviceArea;
    private DiscrepancyType discrepancyType;
    private BigDecimal posAmount;
    private BigDecimal folioAmount;
    private BigDecimal variance;
    private DiscrepancyStatus status;
    private String resolutionNotes;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant transactionTimestamp;
}
