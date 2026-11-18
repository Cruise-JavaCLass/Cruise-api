package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một hoạt động đã đăng ký của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookedActivityItemDto {
    private Long activityId;
    private String activityName;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private BigDecimal price;
}
