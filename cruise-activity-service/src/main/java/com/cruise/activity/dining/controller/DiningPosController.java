package com.cruise.activity.dining.controller;

import com.cruise.activity.dining.dto.DietaryAlertResponse;
import com.cruise.activity.dining.service.DiningAllergenService;
import com.cruise.activity.dining.service.DiningReservationService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dining/pos")
@RequiredArgsConstructor
public class DiningPosController {

    private final DiningReservationService reservationService;
    private final DiningAllergenService allergenService;

    @PostMapping("/check-payment")
    public ApiResponse<String> checkPayment(
            @RequestParam Long passengerId,
            @RequestBody List<String> menuIngredients,
            @RequestParam(defaultValue = "false") boolean supervisorPinVerified
    ) {
        List<DietaryAlertResponse> alerts =
                reservationService.checkGuestAllergens(passengerId);

        boolean allowed = allergenService.canProcessPayment(
                menuIngredients, alerts
        );

        if (!allowed && !supervisorPinVerified) {
            return ApiResponse.error(
                    DiningAllergenService.ALLERGEN_SAFETY_INTERLOCK
            );
        }

        if (!allowed) {
            return ApiResponse.ok(
                    "Payment allowed after supervisor override",
                    "PAYMENT_ALLOWED_WITH_OVERRIDE"
            );
        }

        return ApiResponse.ok("PAYMENT_ALLOWED");
    }
}
