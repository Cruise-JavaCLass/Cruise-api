package com.cruise.activity.dto;

import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.enums.VehicleStatus;
import lombok.*;

import java.time.Instant;

/**
 * Response DTO cho phương tiện vận chuyển tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionVehicleResponse {

    private String id;
    private Long dbId;
    private Long excursionId;
    private String licensePlate;
    private String vehicleType;
    private String driverName;
    private String driverPhone;
    private Integer capacity;
    private Integer assignedPassengers;
    private VehicleStatus status;
    private String operatorName;
    private String notes;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static ExcursionVehicleResponse fromEntity(ExcursionVehicle entity) {
        if (entity == null) {
            return null;
        }
        String formattedId = entity.getVehicleCode() != null && !entity.getVehicleCode().isBlank()
                ? entity.getVehicleCode()
                : String.format("VEH-%02d", entity.getId() != null ? entity.getId() : 1);

        return ExcursionVehicleResponse.builder()
                .id(formattedId)
                .dbId(entity.getId())
                .excursionId(entity.getExcursionId())
                .licensePlate(entity.getLicensePlate())
                .vehicleType(entity.getVehicleType())
                .driverName(entity.getDriverName())
                .driverPhone(entity.getDriverPhone())
                .capacity(entity.getCapacity())
                .assignedPassengers(entity.getAssignedPassengers() != null ? entity.getAssignedPassengers() : 0)
                .status(entity.getStatus() != null ? entity.getStatus() : VehicleStatus.READY)
                .operatorName(entity.getOperatorName())
                .notes(entity.getNotes())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
