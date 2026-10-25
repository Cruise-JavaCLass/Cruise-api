package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllAboardStatusDto {

    private Long tourId;
    private Long portCallId;
    private String portName;
    private Instant allAboardTime;
    private Instant departureTime;
    private long secondsRemaining;
    private boolean countdownExpired;

    private int totalEmbarkedPassengers;
    private int passengersOnBoard;
    private int missingAshoreCount;
    private double aboardPercentage;

    private List<MissingPassengerDto> missingPassengers;
}
