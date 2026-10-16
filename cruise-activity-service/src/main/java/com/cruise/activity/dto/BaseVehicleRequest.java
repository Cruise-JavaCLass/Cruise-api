package com.cruise.activity.dto;

import com.cruise.activity.enums.VehicleStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base DTO chứa các trường thông tin chung của Phương tiện tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseVehicleRequest {

    private String licensePlate;
    private String vehicleType;
    private String driverName;
    private String driverPhone;
    private Integer capacity;
    private Integer assignedPassengers;
    private VehicleStatus status;
    private String operatorName;
    private String notes;
}
