package com.cruise.activity.enums;

import java.util.Collections;
import java.util.Set;

/**
 * Trạng thái của Tour bờ (Shore Excursion Status)
 * Theo đặc tả 3.4: Đang diễn ra, Đã hoàn thành, Bị hủy hoặc Trì hoãn.
 */
public enum ExcursionStatus {
    /**
     * Tour bờ đã lên lịch
     */
    SCHEDULED,

    /**
     * Đang mở đăng ký cho hành khách
     */
    OPEN_FOR_BOOKING,

    /**
     * Tour bờ đang diễn ra
     */
    IN_PROGRESS,

    /**
     * Tour bờ đã hoàn thành
     */
    COMPLETED,

    /**
     * Tour bờ bị hoãn/trì hoãn
     */
    DELAYED,

    /**
     * Tour bờ đã bị hủy
     */
    CANCELLED;

    public boolean canTransitionTo(ExcursionStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case SCHEDULED -> target == OPEN_FOR_BOOKING || target == DELAYED || target == CANCELLED;
            case OPEN_FOR_BOOKING -> target == IN_PROGRESS || target == DELAYED || target == CANCELLED;
            case DELAYED -> target == OPEN_FOR_BOOKING || target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> false;
            case CANCELLED -> target == SCHEDULED;
        };
    }

    public Set<ExcursionStatus> getAllowedTransitions() {
        return switch (this) {
            case SCHEDULED -> Set.of(OPEN_FOR_BOOKING, DELAYED, CANCELLED);
            case OPEN_FOR_BOOKING -> Set.of(IN_PROGRESS, DELAYED, CANCELLED);
            case DELAYED -> Set.of(OPEN_FOR_BOOKING, IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED -> Collections.emptySet();
            case CANCELLED -> Set.of(SCHEDULED);
        };
    }
}
