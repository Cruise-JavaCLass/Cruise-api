package com.cruise.itinerary.event;

import com.cruise.itinerary.enums.ChangeType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Event phát ra khi có sự thay đổi lịch trình tour hoặc lịch trình ngày
 */
@Getter
@Builder
public class ItineraryChangedEvent {

    private final Long tourId;
    private final String tourCode;
    private final String tourName;
    private final Long dailyItineraryId;
    private final Integer dayNumber;
    private final ChangeType changeType;
    private final String title;
    private final String reason;
    private final String changedBy;
    private final String previousState;
    private final String newState;
    @Builder.Default
    private final Instant timestamp = Instant.now();
}
