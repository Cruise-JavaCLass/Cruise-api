package com.cruise.activity.dining.controller;

import com.cruise.activity.dining.dto.DietaryAlertResponse;
import com.cruise.activity.dining.service.DiningReservationService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dining/guests")
@RequiredArgsConstructor
public class DietaryAlertController {

    private final DiningReservationService reservationService;

    @GetMapping("/{passengerId}/dietary-alerts")
    public ApiResponse<List<DietaryAlertResponse>> getDietaryAlerts(
            @PathVariable Long passengerId) {

        return ApiResponse.ok(
                reservationService.checkGuestAllergens(passengerId)
        );
    }
}

