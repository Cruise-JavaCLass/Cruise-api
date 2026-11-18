package com.cruise.auth.dto;

import com.cruise.auth.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi thông tin hồ sơ tự phục vụ của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerSelfProfileResponse {
    private Long userId;
    private Long passengerId;
    private String username;
    private String fullName;
    private String email;
    private String bookingReference;
    private String cabinNumber;
    private String musterStation;
    private VerificationStatus verificationStatus;
    private String rfidTag;
}
