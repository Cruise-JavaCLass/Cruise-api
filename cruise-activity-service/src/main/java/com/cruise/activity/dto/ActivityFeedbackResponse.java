package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Phản hồi chi tiết sau khi gửi feedback (KAN-364)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFeedbackResponse {
    private Long id;
    private Long activityId;
    private Long passengerId;
    private Integer rating;
    private Integer organizationRating;
    private Integer guideRating;
    private Integer valueRating;
    private String comments;
    private Boolean escalated;
    private String escalationReason;
    private Instant createdAt;
}
