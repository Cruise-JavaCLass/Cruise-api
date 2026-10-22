package com.cruise.activity.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionWaitlistEntryDto {

    private Integer position;
    private Long bookingId;
    private Long excursionId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String emergencyPhone;
    private String specialRequirements;
    private Instant bookedAt;
}
