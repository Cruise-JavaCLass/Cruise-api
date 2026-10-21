package com.cruise.activity.dto;

import com.cruise.activity.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbarkationCheckInResponse {

    private Long passengerId;
    private CheckInStatus checkInStatus;
    private Instant boardedAt;
    private String rfidTag;
    private String cabinNumber;
    private String folioStatus;
    private String message;
}
