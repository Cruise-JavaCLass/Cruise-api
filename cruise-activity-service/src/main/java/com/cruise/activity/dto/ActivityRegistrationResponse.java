package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRegistrationResponse {

    private Long registrationId;
    private Long activityId;
    private String activityTitle;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String status;
    private BigDecimal chargedAmount;
    private String transactionId;
    private Instant registeredAt;
    private Integer waitlistPosition;
}
