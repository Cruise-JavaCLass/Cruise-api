package com.cruise.activity.dto;

import com.cruise.activity.enums.NotificationCategory;

/**
 * Projection thống kê số lượng thông báo chưa đọc theo Category (KAN-363)
 */
public interface CategoryUnreadCountProjection {
    NotificationCategory getCategory();
    long getUnreadCount();
}
