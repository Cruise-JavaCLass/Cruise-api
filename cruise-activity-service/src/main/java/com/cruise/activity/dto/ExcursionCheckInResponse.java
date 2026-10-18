package com.cruise.activity.dto;

import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response kết quả điểm danh hành khách tour bờ (KAN-224)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcursionCheckInResponse {

    private Long excursionPassengerId;
    private Long excursionId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String ticketCode;
    private String rfidTag;
    private String groupName;
    private String busNumber;
    private String emergencyPhone;
    private ExcursionPassengerStatus previousStatus;
    private ExcursionPassengerStatus currentStatus;
    private Instant checkedInAt;
    private Instant returnedSafeAt;
    private String message;

    public static ExcursionCheckInResponse fromEntity(ExcursionPassenger entity, ExcursionPassengerStatus prevStatus, String message) {
        return ExcursionCheckInResponse.builder()
                .excursionPassengerId(entity.getId())
                .excursionId(entity.getExcursionId())
                .passengerId(entity.getPassengerId())
                .passengerName(entity.getPassengerName())
                .cabinNumber(entity.getCabinNumber())
                .ticketCode(entity.getTicketCode())
                .rfidTag(entity.getRfidTag())
                .groupName(entity.getGroupName())
                .busNumber(entity.getBusNumber())
                .emergencyPhone(entity.getEmergencyPhone())
                .previousStatus(prevStatus)
                .currentStatus(entity.getStatus())
                .checkedInAt(entity.getCheckedInAt())
                .returnedSafeAt(entity.getReturnedSafeAt())
                .message(message)
                .build();
    }
}
