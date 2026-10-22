package com.cruise.activity.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionScheduleAlertResponse {

    private Long excursionId;
    private int recipientsCount;
    private int failedCount;
    private String title;
    private String message;
    private Instant broadcastedAt;
    private String status;
}
