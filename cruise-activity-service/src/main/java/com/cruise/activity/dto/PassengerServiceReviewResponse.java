package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerServiceReviewResponse {

    private Long id;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private FeedbackCategory category;
    private Integer rating;
    private String sentiment;
    private String comments;
    private String serviceOutlet;
    private FeedbackStatus status;
    private Boolean isLowRatingAlert;
    private String escalationReason;
    private String adminNotes;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
