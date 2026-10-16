package com.cruise.activity.dto;

import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import lombok.*;

import java.time.Instant;

/**
 * Response DTO cho thông tin hành khách tour bờ (KAN-176)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionPassengerResponse {

    private Long id;
    private Long excursionId;
    private String passengerName;
    private String cabinNumber;
    private String ticketCode;
    private String busNumber;
    private String groupName;
    private String emergencyPhone;
    private ExcursionPassengerStatus status;
    private String specialRequirements;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static ExcursionPassengerResponse fromEntity(ExcursionPassenger entity) {
        if (entity == null) {
            return null;
        }
        return ExcursionPassengerResponse.builder()
                .id(entity.getId())
                .excursionId(entity.getExcursionId())
                .passengerName(entity.getPassengerName())
                .cabinNumber(entity.getCabinNumber())
                .ticketCode(entity.getTicketCode())
                .busNumber(entity.getBusNumber())
                .groupName(entity.getGroupName())
                .emergencyPhone(entity.getEmergencyPhone())
                .status(entity.getStatus())
                .specialRequirements(entity.getSpecialRequirements())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
