package com.cruise.activity.dining.dto;

import com.cruise.activity.dining.enums.ReservationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DiningReservationResponse {
    private Long id;
    private String reservationCode;
    private Long restaurantId;
    private String restaurantName;
    private String tableNumber;
    private LocalDateTime reservationTime;
    private Integer guestCount;
    private ReservationStatus status;
}