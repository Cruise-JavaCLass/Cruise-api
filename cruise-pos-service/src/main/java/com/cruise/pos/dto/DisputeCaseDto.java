package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCaseDto {
    private Long id;
    private String disputeNumber;
    private String transactionId;
    private String folioId;
    private String cabinNumber;
    private String passengerId;
    private String passengerName;
    private DisputeReason reason;
    private BigDecimal disputeAmount;
    private BigDecimal refundAmount;
    private String passengerNote;
    private String evidenceUrl;
    private DisputeStatus status;
    private String reviewerId;
    private String reviewerNote;
    private String adjustmentTransactionId;
    private Instant createdAt;
    private Instant resolvedAt;
    private List<DisputeHistoryDto> histories;
}
