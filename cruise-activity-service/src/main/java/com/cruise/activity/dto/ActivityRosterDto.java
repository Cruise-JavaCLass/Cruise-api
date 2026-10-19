package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRosterDto {

    private Long registrationId;
    private Long activityId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String tourPackage;
    private String status;
    private BigDecimal chargedAmount;
    private String transactionId;
    private Instant registeredAt;
}
