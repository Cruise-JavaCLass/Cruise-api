package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu điểm danh sự kiện hỗ trợ đa kênh (QR, RFID, ID thủ công - KAN-222)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCheckInRequest {

    private String qrToken;

    private String rfidTag;

    private Long passengerId;

    private String cabinNumber;

    @Builder.Default
    private String staffId = "STAFF-01";

    @Builder.Default
    private String staffName = "Nguyễn Văn Điều Phối";

    @Builder.Default
    private String deviceId = "GATE-POS-01";

    private String note;
}
