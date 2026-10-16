package com.cruise.activity.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO cập nhật phương tiện tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateExcursionVehicleRequest extends BaseVehicleRequest {

    @Builder
    public UpdateExcursionVehicleRequest(String licensePlate, String vehicleType, String driverName,
                                         String driverPhone, Integer capacity, Integer assignedPassengers,
                                         com.cruise.activity.enums.VehicleStatus status, String operatorName, String notes) {
        super(licensePlate, vehicleType, driverName, driverPhone, capacity, assignedPassengers, status, operatorName, notes);
    }
}
