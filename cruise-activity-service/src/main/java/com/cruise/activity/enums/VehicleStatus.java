package com.cruise.activity.enums;

/**
 * Trạng thái hoạt động của Phương tiện vận chuyển tour bờ (KAN-177)
 */
public enum VehicleStatus {
    /**
     * Sẵn sàng đón khách / khởi hành
     */
    READY,

    /**
     * Đang thực hiện chuyến đi / vận chuyển trên hành trình
     */
    ON_TRIP,

    /**
     * Đang bảo dưỡng hoặc kiểm tra kỹ thuật
     */
    MAINTENANCE
}
