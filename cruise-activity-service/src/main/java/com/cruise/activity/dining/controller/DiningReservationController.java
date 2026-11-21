package com.cruise.activity.dining.controller;

import com.cruise.activity.dining.dto.CreateReservationRequest;
import com.cruise.activity.dining.dto.DiningReservationResponse;
import com.cruise.activity.dining.service.DiningReservationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dining/reservations")
@RequiredArgsConstructor
public class DiningReservationController {

    private final DiningReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiningReservationResponse> makeReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader("X-Passenger-Id") Long passengerId) {

        return ApiResponse.ok(
                "Reservation created",
                reservationService.makeReservation(request, passengerId)
        );
    }

    @GetMapping("/my-bookings")
    public ApiResponse<List<DiningReservationResponse>> getMyBookings(
            @RequestHeader("X-Passenger-Id") Long passengerId) {

        return ApiResponse.ok(
                reservationService.getMyBookings(passengerId)
        );
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancelReservation(
            @PathVariable Long id,
            @RequestHeader("X-Passenger-Id") Long passengerId) {

        reservationService.cancelReservation(id, passengerId);
        return ApiResponse.ok("Reservation cancelled", null);
    }
}
