package com.cruise.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO thêm phương tiện mới vào tour bờ (KAN-177)
 */
@Getter
@Setter
@NoArgsConstructor
public class AddExcursionVehicleRequest extends BaseVehicleRequest {

    @NotBlank(message = "Biển số phương tiện không được để trống")
    @Override
    public String getLicensePlate() {
        return super.getLicensePlate();
    }

    @NotBlank(message = "Loại phương tiện không được để trống")
    @Override
    public String getVehicleType() {
        return super.getVehicleType();
    }

    @NotBlank(message = "Tên tài xế không được để trống")
    @Override
    public String getDriverName() {
        return super.getDriverName();
    }

    @NotNull(message = "Sức chứa phương tiện không được để trống")
    @Min(value = 1, message = "Sức chứa phương tiện phải lớn hơn 0")
    @Override
    public Integer getCapacity() {
        return super.getCapacity();
    }

    @Builder
    public AddExcursionVehicleRequest(String licensePlate, String vehicleType, String driverName,
                                      String driverPhone, Integer capacity, Integer assignedPassengers,
                                      com.cruise.activity.enums.VehicleStatus status, String operatorName, String notes) {
        super(licensePlate, vehicleType, driverName, driverPhone, capacity, assignedPassengers, status, operatorName, notes);
    }
}
