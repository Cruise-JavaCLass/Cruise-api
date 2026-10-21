package com.cruise.activity.dto;

import com.cruise.activity.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerEmbarkationProfileDto {

    private Long id;
    private Long tourId;
    private String bookingCode;
    private String fullName;
    private String passportNumber;
    private String nationality;
    private String emergencyContact;
    private String email;
    private String phoneNumber;
    private String cabinNumber;
    private String cabinCategory;
    private Boolean isVip;
    private Boolean healthDeclarationCompleted;
    private String rfidTag;
    private String qrCode;
    private String tourPackage;
    private String bleZone;
    private String folioStatus;
    private CheckInStatus checkInStatus;
    private Instant boardedAt;
}
