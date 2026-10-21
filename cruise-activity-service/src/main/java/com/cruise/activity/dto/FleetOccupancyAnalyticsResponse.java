package com.cruise.activity.dto;

import java.util.List;

public record FleetOccupancyAnalyticsResponse(
        Integer totalFleetCount,
        Integer totalFleetCapacity,
        Integer totalPassengersSeated,
        Double overallFleetOccupancyRate,
        List<PortFleetSummaryDto> portSummaries,
        List<ProviderFleetSummaryDto> providerSummaries,
        List<FleetOccupancyDto> excursions
) {
    public record PortFleetSummaryDto(
            Long portId,
            String portName,
            Integer busCount,
            Integer totalCapacity,
            Integer assignedPassengers,
            Double occupancyRate
    ) {}

    public record ProviderFleetSummaryDto(
            String providerName,
            Integer busCount,
            Integer totalCapacity,
            Integer assignedPassengers,
            Double occupancyRate
    ) {}
}
