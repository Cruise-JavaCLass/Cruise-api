package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phản hồi kết quả xử lý đồng bộ điểm danh ngoại tuyến (KAN-321)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineCheckInBatchResponse {

    private Long activityId;
    private int processedCount;
    private int duplicateCount;
    private int failedCount;
    private String message;
}
