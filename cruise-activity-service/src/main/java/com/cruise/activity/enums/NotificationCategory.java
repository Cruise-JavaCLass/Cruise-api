package com.cruise.activity.enums;

/**
 * Mức độ ưu tiên / phân loại của thông báo hành khách (KAN-363)
 */
public enum NotificationCategory {
    CRITICAL,      // Cảnh báo khẩn cấp, bắt buộc đọc (All Aboard, bão, khẩn cấp)
    OPERATIONAL,   // Thay đổi lịch trình, đổi điểm đón tour
    REMINDER,      // Nhắc nhở giờ tập trung hoạt động
    PROMOTIONAL    // Ưu đãi dịch vụ, khuyến mãi trên tàu
}
