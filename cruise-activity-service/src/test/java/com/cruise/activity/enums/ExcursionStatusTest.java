package com.cruise.activity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcursionStatus Enum & State Machine Unit Tests (KAN-168)")
class ExcursionStatusTest {

    @Test
    @DisplayName("SCHEDULED transitions: can transition to OPEN_FOR_BOOKING, DELAYED, CANCELLED")
    void scheduledTransitions() {
        ExcursionStatus status = ExcursionStatus.SCHEDULED;
        assertThat(status.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.DELAYED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isFalse();
        assertThat(status.canTransitionTo(ExcursionStatus.COMPLETED)).isFalse();
        assertThat(status.getAllowedTransitions()).containsExactlyInAnyOrder(
                ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.DELAYED, ExcursionStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("OPEN_FOR_BOOKING transitions: can transition to IN_PROGRESS, DELAYED, CANCELLED")
    void openForBookingTransitions() {
        ExcursionStatus status = ExcursionStatus.OPEN_FOR_BOOKING;
        assertThat(status.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.DELAYED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
        assertThat(status.canTransitionTo(ExcursionStatus.COMPLETED)).isFalse();
        assertThat(status.getAllowedTransitions()).containsExactlyInAnyOrder(
                ExcursionStatus.IN_PROGRESS, ExcursionStatus.DELAYED, ExcursionStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("DELAYED transitions: can transition to OPEN_FOR_BOOKING, IN_PROGRESS, CANCELLED")
    void delayedTransitions() {
        ExcursionStatus status = ExcursionStatus.DELAYED;
        assertThat(status.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
        assertThat(status.getAllowedTransitions()).containsExactlyInAnyOrder(
                ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.IN_PROGRESS, ExcursionStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("IN_PROGRESS transitions: can transition to COMPLETED, CANCELLED")
    void inProgressTransitions() {
        ExcursionStatus status = ExcursionStatus.IN_PROGRESS;
        assertThat(status.canTransitionTo(ExcursionStatus.COMPLETED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
        assertThat(status.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isFalse();
        assertThat(status.getAllowedTransitions()).containsExactlyInAnyOrder(
                ExcursionStatus.COMPLETED, ExcursionStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("COMPLETED is terminal state: cannot transition to any other status")
    void completedIsTerminal() {
        ExcursionStatus status = ExcursionStatus.COMPLETED;
        for (ExcursionStatus other : ExcursionStatus.values()) {
            if (other != ExcursionStatus.COMPLETED) {
                assertThat(status.canTransitionTo(other)).isFalse();
            }
        }
        assertThat(status.getAllowedTransitions()).isEmpty();
    }

    @Test
    @DisplayName("CANCELLED can transition back to SCHEDULED")
    void cancelledTransitions() {
        ExcursionStatus status = ExcursionStatus.CANCELLED;
        assertThat(status.canTransitionTo(ExcursionStatus.SCHEDULED)).isTrue();
        assertThat(status.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isFalse();
        assertThat(status.getAllowedTransitions()).containsExactly(ExcursionStatus.SCHEDULED);
    }

    @ParameterizedTest
    @EnumSource(ExcursionStatus.class)
    @DisplayName("Every status can transition to itself or null")
    void selfAndNullTransitions(ExcursionStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
        assertThat(status.canTransitionTo(null)).isTrue();
    }
}
