package com.cruise.activity.entity;

import com.cruise.activity.enums.VehicleStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Phương tiện vận chuyển tour bờ (Xe du lịch, Tàu trung chuyển tender, Cano...) (KAN-177)
 */
@Entity
@Table(name = "excursion_vehicles", indexes = {
        @Index(name = "idx_exc_vehicle_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_exc_vehicle_code", columnList = "vehicle_code"),
        @Index(name = "idx_exc_vehicle_license_plate", columnList = "license_plate"),
        @Index(name = "idx_exc_vehicle_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionVehicle extends BaseEntity {

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "vehicle_code", length = 50)
    private String vehicleCode;

    @Column(name = "license_plate", nullable = false, length = 50)
    private String licensePlate;

    @Column(name = "vehicle_type", nullable = false, length = 100)
    private String vehicleType;

    @Column(name = "driver_name", nullable = false, length = 150)
    private String driverName;

    @Column(name = "driver_phone", length = 50)
    private String driverPhone;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "assigned_passengers", nullable = false)
    @Builder.Default
    private Integer assignedPassengers = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.READY;

    @Column(name = "operator_name", length = 150)
    private String operatorName;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
