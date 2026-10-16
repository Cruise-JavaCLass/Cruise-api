package com.cruise.activity.enums;

/**
 * Trạng thái của hành khách trong Tour tham quan trên bờ (KAN-176)
 */
public enum ExcursionPassengerStatus {
    /**
     * Đã đăng ký tour bờ
     */
    REGISTERED,

    /**
     * Đã tập trung và check-in tại điểm hẹn / cầu tàu
     */
    CHECKED_IN,

    /**
     * Đang trong hành trình tham quan trên bờ
     */
    ON_TOUR,

    /**
     * Đã quay trở lại tàu an toàn trước giờ All-Aboard
     */
    RETURNED_SAFE
}
