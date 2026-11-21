package com.cruise.activity.dining.service;
import com.cruise.activity.dining.dto.DietaryAlertResponse;
import com.cruise.activity.dining.enums.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DiningAllergenServiceTest {
    private final DiningAllergenService service=new DiningAllergenService();
    @ParameterizedTest @EnumSource(AllergenType.class)
    void matchingCriticalAllergenBlocksPaymentRegardlessOfCase(AllergenType type) {
        var alert=DietaryAlertResponse.builder().passengerId(10L).allergenType(type).severityLevel(SeverityLevel.CRITICAL).build();
        assertThat(service.canProcessPayment(List.of(type.name().toLowerCase(Locale.ROOT)),List.of(alert))).isFalse();
        assertThat(service.getPaymentStatus(List.of(type.name()),List.of(alert))).isEqualTo(DiningAllergenService.ALLERGEN_SAFETY_INTERLOCK);
    }
    @Test void mildOrUnrelatedIngredientsDoNotTriggerCriticalPaymentInterlock() {
        var mild=DietaryAlertResponse.builder().allergenType(AllergenType.PEANUT).severityLevel(SeverityLevel.MILD).build();
        var critical=DietaryAlertResponse.builder().allergenType(AllergenType.SEAFOOD).severityLevel(SeverityLevel.CRITICAL).build();
        assertThat(service.getPaymentStatus(Arrays.asList(null,"rice","peanut oil"),List.of(mild,critical))).isEqualTo("PAYMENT_ALLOWED");
        assertThat(service.canProcessPayment(List.of(),List.of(critical))).isTrue();
        assertThat(service.canProcessPayment(List.of("peanut"),List.of())).isTrue();
    }
    @Test void supervisorOverrideRequiresVerifiedPin() {
        assertThat(service.validateSupervisorOverride(false)).isFalse();assertThat(service.validateSupervisorOverride(true)).isTrue();
    }
}
