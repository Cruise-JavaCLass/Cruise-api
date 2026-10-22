package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionPassengerRosterDto {

    private Long id;
    private Long excursionId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String ticketCode;
    private String rfidTag;
    private String busNumber;
    private String groupName;
    private String emergencyPhone;
    private ExcursionBookingStatus bookingStatus;
    private Integer waitlistPosition;
    private ExcursionPassengerStatus status;
    private String specialRequirements;
    private Boolean needsSpecialAssistance;
    private BigDecimal chargedAmount;
    private BigDecimal refundAmount;
    private BigDecimal penaltyFee;
    private Boolean isIncluded;
    private Instant checkedInAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private Boolean medicalWaiver;
    private String medicalWaiverReason;
    private Instant createdAt;
}
