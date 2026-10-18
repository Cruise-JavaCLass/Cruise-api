package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu đăng ký và check-in tức thì cho khách vãng lai (KAN-320)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalkInCheckInRequest {

    private Long passengerId;

    private String fullName;

    private String cabinNumber;

    private String rfidTag;

    private String staffId;

    private String staffName;

    private String deviceId;

    private String note;
}
