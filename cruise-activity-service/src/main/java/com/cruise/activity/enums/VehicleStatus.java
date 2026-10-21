package com.cruise.activity.enums;

/**
 * Trạng thái hoạt động của Phương tiện vận chuyển tour bờ (KAN-177, KAN-374)
 */
public enum VehicleStatus {
    /**
     * Sẵn sàng đón khách / khởi hành
     */
    READY,

    /**
     * Đang hoạt động tiếp nhận / chuyên chở khách (AC2 alias cho xe vận hành)
     */
    ACTIVE,

    /**
     * Đang thực hiện chuyến đi / vận chuyển trên hành trình
     */
    ON_TRIP,

    /**
     * Đang bảo dưỡng hoặc kiểm tra kỹ thuật
     */
    MAINTENANCE
}
