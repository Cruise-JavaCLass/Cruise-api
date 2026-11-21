package com.cruise.activity.dining.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateReservationRequest {

    @NotNull
    private Long restaurantId;

    @NotBlank
    private String cabinNumber;

    @NotNull
    @Future
    private LocalDateTime reservationTime;

    @NotNull
    @Min(1)
    private Integer guestCount;
}