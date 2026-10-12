package com.cruise.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyItineraryResponse {

    private Long id;
    private Long tourId;
    private Integer dayNumber;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate itineraryDate;

    private String title;
    private String description;
    private Long portId;
    private String portName;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime departureTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime allAboardTime;

    private Boolean isSeaDay;
    private String weatherForecast;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
