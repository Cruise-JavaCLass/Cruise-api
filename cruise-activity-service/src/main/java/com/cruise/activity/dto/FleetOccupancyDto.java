package com.cruise.activity.dto;

import java.util.List;

public record FleetOccupancyDto(
        Long excursionId,
        String excursionTitle,
        Long portId,
        String portName,
        String localProvider,
        Integer totalBuses,
        Integer totalCapacity,
        Integer totalAssignedPassengers,
        Double occupancyRate,
        List<BusDetailDto> buses
) {
    public record BusDetailDto(
            Long vehicleId,
            String vehicleCode,
            String licensePlate,
            String vehicleType,
            String driverName,
            Integer capacity,
            Integer assignedPassengers,
            Double occupancyRate,
            String status
    ) {}
}
