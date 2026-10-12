package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ChangeType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryChangeHistoryResponse {

    private Long id;
    private Long tourId;
    private Long dailyItineraryId;
    private ChangeType changeType;
    private String reason;
    private String changedBy;
    private String previousState;
    private String newState;
    private Instant createdAt;
}
