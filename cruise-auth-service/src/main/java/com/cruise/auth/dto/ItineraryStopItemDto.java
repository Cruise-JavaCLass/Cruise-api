package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho một cảng dừng trong lịch trình hành trình (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryStopItemDto {
    private Integer stopOrder;
    private String portName;
    private String country;
    private LocalDateTime arrivalTime;
    private LocalDateTime departureTime;
    private String status;
    private String excursionHighlights;
}
