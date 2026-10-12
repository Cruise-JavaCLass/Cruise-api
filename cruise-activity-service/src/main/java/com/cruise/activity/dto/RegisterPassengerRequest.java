package com.cruise.activity.dto;

import com.cruise.activity.enums.CheckInStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPassengerRequest {

    @NotNull(message = "Tour ID không được để trống")
    private Long tourId;

    @NotBlank(message = "Họ và tên hành khách không được để trống")
    private String fullName;

    private String email;

    private String phoneNumber;

    @NotBlank(message = "Số cabin không được để trống")
    private String cabinNumber;

    private String rfidTag;

    private String qrCode;

    private String tourPackage;

    private String bleZone;

    @Builder.Default
    private CheckInStatus checkInStatus = CheckInStatus.NOT_CHECKED_IN;
}
