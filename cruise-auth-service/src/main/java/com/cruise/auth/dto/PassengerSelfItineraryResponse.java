package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO phản hồi thông tin lịch trình hành trình tự phục vụ của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerSelfItineraryResponse {
    private Long passengerId;
    private String bookingReference;
    private String cabinNumber;
    private String currentStatus;
    private List<ItineraryStopItemDto> portsOfCall;
}
