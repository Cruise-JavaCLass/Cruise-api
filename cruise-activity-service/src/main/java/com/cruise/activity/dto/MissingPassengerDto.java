package com.cruise.activity.dto;

import com.cruise.activity.enums.GangwayMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingPassengerDto {

    private Long passengerId;
    private String fullName;
    private String cabinNumber;
    private String rfidTag;
    private String phoneNumber;
    private String emergencyContact;
    private GangwayMovementType currentStatus; // DISEMBARKED_SHORE
    private Instant disembarkedAt;
    private String lastKnownLocation;
    private boolean alertSent;
    private Instant lastAlertSentAt;
}
