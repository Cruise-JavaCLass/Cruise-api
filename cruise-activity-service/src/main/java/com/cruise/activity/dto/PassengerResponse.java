package com.cruise.activity.dto;

import com.cruise.activity.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO trả về thông tin hành khách đã đăng ký
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerResponse {

    private Long id;
    private Long tourId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String cabinNumber;
    private String rfidTag;
    private String qrCode;
    private String tourPackage;
    private String bleZone;
    private com.cruise.activity.enums.RegistrationStatus registrationStatus;
    private CheckInStatus checkInStatus;
    private Instant boardedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
