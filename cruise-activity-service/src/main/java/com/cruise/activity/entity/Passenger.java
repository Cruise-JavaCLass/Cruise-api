package com.cruise.activity.entity;

import com.cruise.activity.enums.CheckInStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Thực thể Hành khách trên du thuyền (Cruise Passenger)
 */
@Entity
@Table(name = "passengers", indexes = {
        @Index(name = "idx_passenger_tour_id", columnList = "tour_id"),
        @Index(name = "idx_passenger_rfid_tag", columnList = "rfid_tag"),
        @Index(name = "idx_passenger_cabin", columnList = "cabin_number"),
        @Index(name = "idx_passenger_passport", columnList = "passport_number"),
        @Index(name = "idx_passenger_booking_code", columnList = "booking_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Passenger extends BaseEntity {

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "booking_code", length = 50)
    private String bookingCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "emergency_contact", length = 150)
    private String emergencyContact;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "cabin_category", length = 50)
    private String cabinCategory;

    @Column(name = "is_vip", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean isVip = false;

    @Column(name = "health_declaration_completed", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean healthDeclarationCompleted = false;

    @Column(name = "rfid_tag", unique = true, length = 50)
    private String rfidTag;

    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    @Column(name = "tour_package", length = 100)
    private String tourPackage;

    @Column(name = "ble_zone", length = 100)
    private String bleZone;

    @Column(name = "folio_status", length = 30)
    @Builder.Default
    private String folioStatus = "INACTIVE";

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 30)
    @Builder.Default
    private com.cruise.activity.enums.RegistrationStatus registrationStatus = com.cruise.activity.enums.RegistrationStatus.CONFIRMED;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_status", nullable = false, length = 30)
    @Builder.Default
    private CheckInStatus checkInStatus = CheckInStatus.NOT_CHECKED_IN;

    @Column(name = "boarded_at")
    private Instant boardedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
