package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO phản hồi danh sách hoạt động tự phục vụ của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerSelfActivitiesResponse {
    private Long passengerId;
    private String bookingReference;
    private String cabinNumber;
    private Integer totalBooked;
    private List<BookedActivityItemDto> activities;
}
