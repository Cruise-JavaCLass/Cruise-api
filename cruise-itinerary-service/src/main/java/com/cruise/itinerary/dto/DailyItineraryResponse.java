package com.cruise.itinerary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DailyItineraryResponse extends BaseDailyItineraryDto {

    private Long id;
    private Long tourId;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
