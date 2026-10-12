package com.cruise.activity.enums;

import java.util.Collections;
import java.util.Set;

/**
 * Trạng thái của Hoạt động / Dịch vụ (Onboard Activity Status)
 */
public enum ActivityStatus {
    /**
     * Hoạt động đã lên lịch dự kiến
     */
    SCHEDULED,

    /**
     * Hoạt động đang diễn ra
     */
    IN_PROGRESS,

    /**
     * Hoạt động đã hoàn thành
     */
    COMPLETED,

    /**
     * Hoạt động đã bị hủy (thời tiết, điều kiện vận hành...)
     */
    CANCELLED;

    /**
     * Kiểm tra trạng thái hiện tại có thể chuyển sang trạng thái mục tiêu hay không
     */
    public boolean canTransitionTo(ActivityStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case SCHEDULED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> false;
            case CANCELLED -> target == SCHEDULED;
        };
    }

    /**
     * Danh sách các trạng thái được phép chuyển đến từ trạng thái hiện tại
     */
    public Set<ActivityStatus> getAllowedTransitions() {
        return switch (this) {
            case SCHEDULED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED -> Collections.emptySet();
            case CANCELLED -> Set.of(SCHEDULED);
        };
    }
}
