package com.cruise.activity.dining.service;

import com.cruise.activity.dining.dto.DietaryAlertResponse;
import com.cruise.activity.dining.enums.SeverityLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiningAllergenService {

    public static final String ALLERGEN_SAFETY_INTERLOCK =
            "ALLERGEN_SAFETY_INTERLOCK";

    public boolean canProcessPayment(
            List<String> menuIngredients,
            List<DietaryAlertResponse> dietaryAlerts
    ) {
        for (DietaryAlertResponse alert : dietaryAlerts) {
            boolean containsAllergen = menuIngredients.stream()
                    .filter(ingredient -> ingredient != null)
                    .anyMatch(ingredient -> ingredient
                            .toUpperCase()
                            .contains(alert.getAllergenType().name()));

            if (containsAllergen
                    && alert.getSeverityLevel() == SeverityLevel.CRITICAL) {
                return false;
            }
        }

        return true;
    }

    public String getPaymentStatus(
            List<String> menuIngredients,
            List<DietaryAlertResponse> dietaryAlerts
    ) {
        if (!canProcessPayment(menuIngredients, dietaryAlerts)) {
            return ALLERGEN_SAFETY_INTERLOCK;
        }

        return "PAYMENT_ALLOWED";
    }

    public boolean validateSupervisorOverride(
            boolean supervisorPinVerified
    ) {
        return supervisorPinVerified;
    }
}
