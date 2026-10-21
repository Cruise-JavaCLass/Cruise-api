package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
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
public class DisputeResponse {

    private Long disputeId;
    private String disputeNumber;
    private String transactionId;
    private Long passengerId;
    private String cabinNumber;
    private DisputeReason reason;
    private BigDecimal disputeAmount;
    private DisputeStatus status;
    private String passengerNote;
    private Instant createdAt;
}
