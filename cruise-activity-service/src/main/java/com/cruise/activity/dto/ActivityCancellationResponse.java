package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityCancellationResponse {

    private Long registrationId;
    private String status;
    private BigDecimal refundAmount;
    private Long waitlistPromotedPassengerId;
    private String cancellationReason;
    private Instant cancelledAt;
}
