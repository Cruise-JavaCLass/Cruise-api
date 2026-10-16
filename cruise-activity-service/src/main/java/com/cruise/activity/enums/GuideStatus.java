package com.cruise.activity.enums;

/**
 * Trạng thái của Hướng dẫn viên trong Tour bờ (KAN-177)
 */
public enum GuideStatus {
    /**
     * Sẵn sàng nhận nhiệm vụ
     */
    ACTIVE,

    /**
     * Đã được phân công vào đoàn tour
     */
    ASSIGNED,

    /**
     * Ở trạng thái trực dự phòng (Standby)
     */
    STANDBY
}
