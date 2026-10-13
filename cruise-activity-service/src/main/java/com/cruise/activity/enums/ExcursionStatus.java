package com.cruise.activity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái của Tour bờ (Shore Excursion Status)
 * Theo đặc tả 3.4: Đang diễn ra, Đã hoàn thành, Bị hủy hoặc Trì hoãn.
 */
public enum ExcursionStatus {
    SCHEDULED,
    OPEN_FOR_BOOKING,
    IN_PROGRESS,
    COMPLETED,
    DELAYED,
    CANCELLED;

    public boolean canTransitionTo(ExcursionStatus next) {
        if (next == null || next == this) {
            return true;
        }
        return getAllowedTransitions().contains(next);
    }

    public Set<ExcursionStatus> getAllowedTransitions() {
        return switch (this) {
            case SCHEDULED -> EnumSet.of(OPEN_FOR_BOOKING, DELAYED, CANCELLED);
            case OPEN_FOR_BOOKING -> EnumSet.of(IN_PROGRESS, DELAYED, CANCELLED);
            case DELAYED -> EnumSet.of(OPEN_FOR_BOOKING, IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED -> Collections.emptySet();
            case CANCELLED -> EnumSet.of(SCHEDULED);
        };
    }
}
