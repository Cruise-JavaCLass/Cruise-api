package com.cruise.activity.service;

import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse;
import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse.PortFleetSummaryDto;
import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse.ProviderFleetSummaryDto;
import com.cruise.activity.dto.FleetOccupancyDto;
import com.cruise.activity.dto.FleetOccupancyDto.BusDetailDto;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.repository.ExcursionVehicleRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregator tính toán và tổng hợp tỷ lệ lấp đầy ghế xe tour bờ (KAN-342)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FleetOccupancyAggregator {

    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionVehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public FleetOccupancyAnalyticsResponse aggregateFleetOccupancy(Long portIdFilter, String providerFilter) {
        List<ShoreExcursion> excursions = excursionRepository.findAll();
        List<ExcursionVehicle> allVehicles = vehicleRepository.findAll();

        Map<Long, List<ExcursionVehicle>> vehiclesByExcursion = allVehicles.stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .collect(Collectors.groupingBy(ExcursionVehicle::getExcursionId));

        List<FleetOccupancyDto> excursionDtos = new ArrayList<>();

        for (ShoreExcursion exc : excursions) {
            if (portIdFilter != null && !Objects.equals(exc.getPortId(), portIdFilter)) {
                continue;
            }
            if (providerFilter != null && !providerFilter.isBlank() &&
                    (exc.getLocalProvider() == null || !exc.getLocalProvider().toLowerCase().contains(providerFilter.toLowerCase().trim()))) {
                continue;
            }

            List<ExcursionVehicle> vehicles = vehiclesByExcursion.getOrDefault(exc.getId(), Collections.emptyList());
            List<BusDetailDto> busDtos = vehicles.stream()
                    .map(v -> {
                        int cap = v.getCapacity() != null ? v.getCapacity() : 0;
                        int assigned = v.getAssignedPassengers() != null ? v.getAssignedPassengers() : 0;
                        double occ = cap > 0 ? roundDouble((double) assigned * 100.0 / cap) : 0.0;
                        return new BusDetailDto(
                                v.getId(),
                                v.getVehicleCode(),
                                v.getLicensePlate(),
                                v.getVehicleType(),
                                v.getDriverName(),
                                cap,
                                assigned,
                                occ,
                                v.getStatus() != null ? v.getStatus().name() : "READY"
                        );
                    })
                    .toList();

            int totalCap = busDtos.stream().mapToInt(BusDetailDto::capacity).sum();
            int totalAssigned = busDtos.stream().mapToInt(BusDetailDto::assignedPassengers).sum();
            double occRate = totalCap > 0 ? roundDouble((double) totalAssigned * 100.0 / totalCap) : 0.0;

            excursionDtos.add(new FleetOccupancyDto(
                    exc.getId(),
                    exc.getTitle(),
                    exc.getPortId(),
                    exc.getPortName(),
                    exc.getLocalProvider() != null ? exc.getLocalProvider() : "Chưa chỉ định",
                    busDtos.size(),
                    totalCap,
                    totalAssigned,
                    occRate,
                    busDtos
            ));
        }

        int fleetCount = excursionDtos.stream().mapToInt(FleetOccupancyDto::totalBuses).sum();
        int fleetCapacity = excursionDtos.stream().mapToInt(FleetOccupancyDto::totalCapacity).sum();
        int fleetAssigned = excursionDtos.stream().mapToInt(FleetOccupancyDto::totalAssignedPassengers).sum();
        double overallOccupancy = fleetCapacity > 0 ? roundDouble((double) fleetAssigned * 100.0 / fleetCapacity) : 0.0;

        List<PortFleetSummaryDto> portSummaries = buildPortSummaries(excursionDtos);
        List<ProviderFleetSummaryDto> providerSummaries = buildProviderSummaries(excursionDtos);

        log.info("Fleet occupancy aggregated: totalBuses={}, totalCapacity={}, seated={}, rate={}%",
                fleetCount, fleetCapacity, fleetAssigned, overallOccupancy);

        return new FleetOccupancyAnalyticsResponse(
                fleetCount,
                fleetCapacity,
                fleetAssigned,
                overallOccupancy,
                portSummaries,
                providerSummaries,
                excursionDtos
        );
    }

    private List<PortFleetSummaryDto> buildPortSummaries(List<FleetOccupancyDto> dtos) {
        Map<Long, List<FleetOccupancyDto>> byPort = dtos.stream()
                .filter(d -> d.portId() != null)
                .collect(Collectors.groupingBy(FleetOccupancyDto::portId));

        return byPort.entrySet().stream()
                .map(entry -> {
                    Long portId = entry.getKey();
                    List<FleetOccupancyDto> list = entry.getValue();
                    String portName = list.isEmpty() ? "Unknown Port" : list.get(0).portName();
                    int busCount = list.stream().mapToInt(FleetOccupancyDto::totalBuses).sum();
                    int cap = list.stream().mapToInt(FleetOccupancyDto::totalCapacity).sum();
                    int seated = list.stream().mapToInt(FleetOccupancyDto::totalAssignedPassengers).sum();
                    double rate = cap > 0 ? roundDouble((double) seated * 100.0 / cap) : 0.0;
                    return new PortFleetSummaryDto(portId, portName, busCount, cap, seated, rate);
                })
                .sorted(Comparator.comparing(PortFleetSummaryDto::portName))
                .toList();
    }

    private List<ProviderFleetSummaryDto> buildProviderSummaries(List<FleetOccupancyDto> dtos) {
        Map<String, List<FleetOccupancyDto>> byProvider = dtos.stream()
                .collect(Collectors.groupingBy(FleetOccupancyDto::localProvider));

        return byProvider.entrySet().stream()
                .map(entry -> {
                    String providerName = entry.getKey();
                    List<FleetOccupancyDto> list = entry.getValue();
                    int busCount = list.stream().mapToInt(FleetOccupancyDto::totalBuses).sum();
                    int cap = list.stream().mapToInt(FleetOccupancyDto::totalCapacity).sum();
                    int seated = list.stream().mapToInt(FleetOccupancyDto::totalAssignedPassengers).sum();
                    double rate = cap > 0 ? roundDouble((double) seated * 100.0 / cap) : 0.0;
                    return new ProviderFleetSummaryDto(providerName, busCount, cap, seated, rate);
                })
                .sorted(Comparator.comparing(ProviderFleetSummaryDto::providerName))
                .toList();
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
