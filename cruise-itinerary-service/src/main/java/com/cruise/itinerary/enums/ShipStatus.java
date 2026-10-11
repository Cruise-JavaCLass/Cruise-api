package com.cruise.itinerary.enums;

/**
 * Trạng thái hoạt động của tàu du lịch
 */
public enum ShipStatus {
    /**
     * Sẵn sàng hoạt động, phục vụ hành khách
     */
    ACTIVE,

    /**
     * Đang neo đậu tại cảng
     */
    DOCKED,

    /**
     * Đang trong hành trình trên biển (hải trình)
     */
    ON_VOYAGE,

    /**
     * Đang bảo dưỡng hoặc sửa chữa kỹ thuật
     */
    UNDER_MAINTENANCE,

    /**
     * Tạm dừng khai thác hoặc ngừng hoạt động
     */
    INACTIVE
}
