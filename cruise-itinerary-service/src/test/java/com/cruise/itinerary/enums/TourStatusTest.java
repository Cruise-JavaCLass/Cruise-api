package com.cruise.itinerary.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TourStatus Transition Unit Tests")
class TourStatusTest {

    // ------------------------------------------------------------------ //
    //  canTransitionTo — ma trận chuyển đổi đầy đủ (KAN-98)              //
    // ------------------------------------------------------------------ //

    @ParameterizedTest(name = "{0} → {1} = {2}")
    @CsvSource({
            // DRAFT
            "DRAFT, PUBLISHED,    true",
            "DRAFT, CANCELLED,    true",
            "DRAFT, IN_PROGRESS,  false",
            "DRAFT, COMPLETED,    false",
            // PUBLISHED
            "PUBLISHED, IN_PROGRESS, true",
            "PUBLISHED, DRAFT,        true",
            "PUBLISHED, CANCELLED,    true",
            "PUBLISHED, COMPLETED,    false",
            // IN_PROGRESS
            "IN_PROGRESS, COMPLETED, true",
            "IN_PROGRESS, CANCELLED, true",
            "IN_PROGRESS, DRAFT,     false",
            "IN_PROGRESS, PUBLISHED, false",
            // COMPLETED (terminal)
            "COMPLETED, DRAFT,       false",
            "COMPLETED, PUBLISHED,   false",
            "COMPLETED, IN_PROGRESS, false",
            "COMPLETED, CANCELLED,   false",
            // CANCELLED (reopen → DRAFT only)
            "CANCELLED, DRAFT,       true",
            "CANCELLED, PUBLISHED,   false",
            "CANCELLED, IN_PROGRESS, false",
            "CANCELLED, COMPLETED,   false"
    })
    @DisplayName("Ma trận chuyển đổi trạng thái tour")
    void shouldValidateStatusTransitions(TourStatus from, TourStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    // ------------------------------------------------------------------ //
    //  Self / null — luôn hợp lệ                                          //
    // ------------------------------------------------------------------ //

    @ParameterizedTest(name = "{0} → self = true")
    @EnumSource(TourStatus.class)
    @DisplayName("Chuyển về chính trạng thái hiện tại luôn hợp lệ")
    void canTransitionToSelf_alwaysTrue(TourStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
    }

    @ParameterizedTest(name = "{0} → null = true")
    @EnumSource(TourStatus.class)
    @DisplayName("canTransitionTo(null) luôn trả về true cho mọi trạng thái")
    void canTransitionToNull_alwaysTrue(TourStatus status) {
        assertThat(status.canTransitionTo(null)).isTrue();
    }

    // ------------------------------------------------------------------ //
    //  getAllowedTransitions                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("getAllowedTransitions trả về đúng tập hợp cho mọi trạng thái")
    void shouldReturnCorrectAllowedTransitions() {
        assertThat(TourStatus.DRAFT.getAllowedTransitions())
                .containsExactlyInAnyOrder(TourStatus.PUBLISHED, TourStatus.CANCELLED);
        assertThat(TourStatus.PUBLISHED.getAllowedTransitions())
                .containsExactlyInAnyOrder(TourStatus.IN_PROGRESS, TourStatus.DRAFT, TourStatus.CANCELLED);
        assertThat(TourStatus.IN_PROGRESS.getAllowedTransitions())
                .containsExactlyInAnyOrder(TourStatus.COMPLETED, TourStatus.CANCELLED);
        assertThat(TourStatus.COMPLETED.getAllowedTransitions()).isEmpty();
        assertThat(TourStatus.CANCELLED.getAllowedTransitions())
                .containsExactly(TourStatus.DRAFT);
    }

    @Test
    @DisplayName("Kích thước danh sách allowedTransitions đúng với từng trạng thái")
    void allowedTransitions_correctSize() {
        assertThat(TourStatus.DRAFT.getAllowedTransitions()).hasSize(2);
        assertThat(TourStatus.PUBLISHED.getAllowedTransitions()).hasSize(3);
        assertThat(TourStatus.IN_PROGRESS.getAllowedTransitions()).hasSize(2);
        assertThat(TourStatus.COMPLETED.getAllowedTransitions()).isEmpty();
        assertThat(TourStatus.CANCELLED.getAllowedTransitions()).hasSize(1);
    }

    // ------------------------------------------------------------------ //
    //  Business-rule edge cases                                           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("COMPLETED là trạng thái terminal — không cho phép bất kỳ chuyển đổi nào")
    void completed_isTerminal() {
        for (TourStatus target : TourStatus.values()) {
            if (target != TourStatus.COMPLETED) {
                assertThat(TourStatus.COMPLETED.canTransitionTo(target))
                        .as("COMPLETED → %s phải bị từ chối", target)
                        .isFalse();
            }
        }
        assertThat(TourStatus.COMPLETED.getAllowedTransitions()).isEmpty();
    }

    @Test
    @DisplayName("CANCELLED chỉ cho phép mở lại về DRAFT (reopen flow)")
    void cancelled_canOnlyReopenToDraft() {
        assertThat(TourStatus.CANCELLED.canTransitionTo(TourStatus.DRAFT)).isTrue();
        assertThat(TourStatus.CANCELLED.getAllowedTransitions())
                .hasSize(1)
                .containsExactly(TourStatus.DRAFT);
    }

    @Test
    @DisplayName("PUBLISHED hỗ trợ rollback về DRAFT")
    void published_canRollbackToDraft() {
        assertThat(TourStatus.PUBLISHED.canTransitionTo(TourStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("IN_PROGRESS không thể quay về DRAFT hay PUBLISHED")
    void inProgress_cannotGoBackward() {
        assertThat(TourStatus.IN_PROGRESS.canTransitionTo(TourStatus.DRAFT)).isFalse();
        assertThat(TourStatus.IN_PROGRESS.canTransitionTo(TourStatus.PUBLISHED)).isFalse();
    }
}
