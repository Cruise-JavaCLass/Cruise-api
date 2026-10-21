package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Thống kê số lượng thông báo chưa đọc của hành khách (KAN-363)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryDto {
    private long totalUnread;
    private long criticalUnread;
    private Map<String, Long> unreadByCategory;
}
