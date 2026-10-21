package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionCancellationResponse {

    private Long bookingId;

    private Long excursionId;

    private Long passengerId;

    private String status;

    private BigDecimal refundAmount;

    private BigDecimal penaltyFee;

    private String cancellationReason;

    private Instant cancelledAt;
}
