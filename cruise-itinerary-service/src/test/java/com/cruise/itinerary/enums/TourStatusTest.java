package com.cruise.itinerary.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TourStatus Transition Unit Tests")
class TourStatusTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT, PUBLISHED, true",
            "DRAFT, CANCELLED, true",
            "DRAFT, IN_PROGRESS, false",
            "DRAFT, COMPLETED, false",
            "PUBLISHED, IN_PROGRESS, true",
            "PUBLISHED, DRAFT, true",
            "PUBLISHED, CANCELLED, true",
            "PUBLISHED, COMPLETED, false",
            "IN_PROGRESS, COMPLETED, true",
            "IN_PROGRESS, CANCELLED, true",
            "IN_PROGRESS, DRAFT, false",
            "IN_PROGRESS, PUBLISHED, false",
            "COMPLETED, DRAFT, false",
            "COMPLETED, CANCELLED, false",
            "COMPLETED, IN_PROGRESS, false",
            "CANCELLED, DRAFT, true",
            "CANCELLED, PUBLISHED, false",
            "CANCELLED, IN_PROGRESS, false",
            "CANCELLED, COMPLETED, false"
    })
    void shouldValidateStatusTransitions(TourStatus from, TourStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    void shouldAllowTransitionToSameOrNull() {
        assertThat(TourStatus.DRAFT.canTransitionTo(null)).isTrue();
        assertThat(TourStatus.DRAFT.canTransitionTo(TourStatus.DRAFT)).isTrue();
    }

    @Test
    void shouldReturnCorrectAllowedTransitions() {
        assertThat(TourStatus.DRAFT.getAllowedTransitions()).containsExactlyInAnyOrder(TourStatus.PUBLISHED, TourStatus.CANCELLED);
        assertThat(TourStatus.PUBLISHED.getAllowedTransitions()).containsExactlyInAnyOrder(TourStatus.IN_PROGRESS, TourStatus.DRAFT, TourStatus.CANCELLED);
        assertThat(TourStatus.IN_PROGRESS.getAllowedTransitions()).containsExactlyInAnyOrder(TourStatus.COMPLETED, TourStatus.CANCELLED);
        assertThat(TourStatus.COMPLETED.getAllowedTransitions()).isEmpty();
        assertThat(TourStatus.CANCELLED.getAllowedTransitions()).containsExactly(TourStatus.DRAFT);
    }
}
