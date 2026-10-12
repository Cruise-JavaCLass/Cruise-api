package com.cruise.activity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ActivityStatus Enum Unit Tests (KAN-136)")
class ActivityStatusTest {

    @ParameterizedTest
    @EnumSource(ActivityStatus.class)
    @DisplayName("canTransitionTo: should return true when target is null or same state")
    void canTransitionTo_NullOrSameState(ActivityStatus status) {
        assertThat(status.canTransitionTo(null)).isTrue();
        assertThat(status.canTransitionTo(status)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: SCHEDULED transitions")
    void canTransitionTo_FromScheduled() {
        assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isTrue();
        assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.CANCELLED)).isTrue();
        assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: IN_PROGRESS transitions")
    void canTransitionTo_FromInProgress() {
        assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.COMPLETED)).isTrue();
        assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.CANCELLED)).isTrue();
        assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.SCHEDULED)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: COMPLETED is terminal state")
    void canTransitionTo_FromCompleted() {
        assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.SCHEDULED)).isFalse();
        assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isFalse();
        assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: CANCELLED transitions back to SCHEDULED")
    void canTransitionTo_FromCancelled() {
        assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.SCHEDULED)).isTrue();
        assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isFalse();
        assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("getAllowedTransitions: returns exact transition sets for all statuses")
    void getAllowedTransitions_AllCases() {
        assertThat(ActivityStatus.SCHEDULED.getAllowedTransitions())
                .containsExactlyInAnyOrder(ActivityStatus.IN_PROGRESS, ActivityStatus.CANCELLED);

        assertThat(ActivityStatus.IN_PROGRESS.getAllowedTransitions())
                .containsExactlyInAnyOrder(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED);

        assertThat(ActivityStatus.COMPLETED.getAllowedTransitions())
                .isEmpty();

        assertThat(ActivityStatus.CANCELLED.getAllowedTransitions())
                .containsExactly(ActivityStatus.SCHEDULED);
    }
}
