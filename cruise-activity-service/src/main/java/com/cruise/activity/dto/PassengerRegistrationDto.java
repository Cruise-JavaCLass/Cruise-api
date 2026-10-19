package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerRegistrationDto {

    private Long registrationId;
    private Long activityId;
    private String activityTitle;
    private String category;
    private String location;
    private LocalDate activityDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private BigDecimal chargedAmount;
    private BigDecimal refundAmount;
    private String transactionId;
    private Instant registeredAt;
    private Integer waitlistPosition;
    private Integer cancellationCutOffMinutes;
}
