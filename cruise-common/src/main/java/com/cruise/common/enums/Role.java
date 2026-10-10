package com.cruise.common.enums;

/**
 * Định nghĩa vai trò của người dùng theo đặc tả yêu cầu hệ thống
 */
public enum Role {
    PASSENGER,                  // Hành khách
    ITINERARY_COORDINATOR,      // Bộ phận điều phối hải trình
    ACTIVITY_MANAGER,           // Bộ phận quản lý hoạt động trên tàu
    SHORE_EXCURSION_MANAGER,    // Bộ phận quản lý tour trên bờ
    POS_STAFF,                  // Nhân viên bán hàng & dịch vụ trên tàu
    FINANCE_FRONT_DESK,         // Bộ phận tài chính & lễ tân
    OPERATIONS_MANAGER,         // Quản lý vận hành
    SYSTEM_ADMIN                // Quản trị viên hệ thống
}
