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

    private String staffId;

    private String staffName;

    private String deviceId;

    private String note;
}
