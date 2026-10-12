package com.cruise.activity.dto;

import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Response DTO trả về thông tin hoạt động
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardActivityResponse {

    private Long id;
    private Long tourId;
    private Integer dayNumber;
    private String title;
    private String description;
    private String category;
    private String location;
    private LocalDate activityDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxCapacity;
    private Integer currentParticipants;
    private BigDecimal price;
    private Boolean isIncluded;
    private ActivityStatus status;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static OnboardActivityResponse fromEntity(OnboardActivity activity) {
        if (activity == null) {
            return null;
        }
        return OnboardActivityResponse.builder()
                .id(activity.getId())
                .tourId(activity.getTourId())
                .dayNumber(activity.getDayNumber())
                .title(activity.getTitle())
                .description(activity.getDescription())
                .category(activity.getCategory())
                .location(activity.getLocation())
                .activityDate(activity.getActivityDate())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .maxCapacity(activity.getMaxCapacity())
                .currentParticipants(activity.getCurrentParticipants())
                .price(activity.getPrice())
                .isIncluded(activity.getIsIncluded())
                .status(activity.getStatus())
                .active(activity.getActive())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }
}
