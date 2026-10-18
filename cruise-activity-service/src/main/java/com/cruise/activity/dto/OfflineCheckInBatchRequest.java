package com.cruise.activity.dto;

import com.cruise.activity.enums.ActivityCheckInMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Yêu cầu đồng bộ hàng loạt các lượt quét điểm danh ngoại tuyến (Offline Batch Sync - KAN-321)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineCheckInBatchRequest {

    @Builder.Default
    private List<OfflineCheckInItem> checkIns = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfflineCheckInItem {
        private Long passengerId;
        private String rfidTag;
        private String qrToken;
        private String cabinNumber;
        private Instant scannedAt;
        private String staffId;
        private String deviceId;
        private ActivityCheckInMethod method;
        private String offlineQueueId;
    }
}
