package com.cruise.activity.dto;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Response DTO trả về thông tin chi tiết Tour bờ (KAN-168)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoreExcursionResponse {

    private Long id;
    private Long tourId;
    private Long portId;
    private Integer dayNumber;
    private String title;
    private String description;
    private String portName;
    private String meetingPoint;
    private String localProvider;
    private String providerContact;
    private LocalDate excursionDate;
    private LocalTime gatheringTime;
    private LocalTime departureTime;
    private LocalTime returnToShipTime;
    private Integer maxCapacity;
    private Integer currentParticipants;
    private Integer remainingCapacity;
    private BigDecimal price;
    private BigDecimal childPrice;
    private String currency;
    private Boolean isIncluded;
    private ExcursionStatus status;
    private String cancellationReason;
    private Boolean active;
    private List<ExcursionPassengerResponse> passengers;
    private List<ExcursionVehicleResponse> vehicles;
    private List<ExcursionGuideResponse> guides;
    private Instant createdAt;
    private Instant updatedAt;

    public static ShoreExcursionResponse fromEntity(ShoreExcursion entity) {
        return fromEntity(entity, null, null, null);
    }

    public static ShoreExcursionResponse fromEntity(ShoreExcursion entity, java.util.List<com.cruise.activity.entity.ExcursionPassenger> excursionPassengers) {
        return fromEntity(entity, excursionPassengers, null, null);
    }

    public static ShoreExcursionResponse fromEntity(
            ShoreExcursion entity,
            java.util.List<com.cruise.activity.entity.ExcursionPassenger> excursionPassengers,
            java.util.List<com.cruise.activity.entity.ExcursionVehicle> excursionVehicles,
            java.util.List<com.cruise.activity.entity.ExcursionGuide> excursionGuides
    ) {
        if (entity == null) {
            return null;
        }
        int remaining = entity.getMaxCapacity() != null
                ? Math.max(0, entity.getMaxCapacity() - (entity.getCurrentParticipants() != null ? entity.getCurrentParticipants() : 0))
                : 0;

        java.util.List<ExcursionPassengerResponse> passengerList = excursionPassengers != null
                ? excursionPassengers.stream().map(ExcursionPassengerResponse::fromEntity).toList()
                : null;

        java.util.List<ExcursionVehicleResponse> vehicleList = excursionVehicles != null
                ? excursionVehicles.stream().map(ExcursionVehicleResponse::fromEntity).toList()
                : null;

        java.util.List<ExcursionGuideResponse> guideList = excursionGuides != null
                ? excursionGuides.stream().map(ExcursionGuideResponse::fromEntity).toList()
                : null;

        return ShoreExcursionResponse.builder()
                .id(entity.getId())
                .tourId(entity.getTourId())
                .portId(entity.getPortId())
                .dayNumber(entity.getDayNumber())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .portName(entity.getPortName())
                .meetingPoint(entity.getMeetingPoint())
                .localProvider(entity.getLocalProvider())
                .providerContact(entity.getProviderContact())
                .excursionDate(entity.getExcursionDate())
                .gatheringTime(entity.getGatheringTime())
                .departureTime(entity.getDepartureTime())
                .returnToShipTime(entity.getReturnToShipTime())
                .maxCapacity(entity.getMaxCapacity())
                .currentParticipants(entity.getCurrentParticipants())
                .remainingCapacity(remaining)
                .price(entity.getPrice())
                .childPrice(entity.getChildPrice())
                .currency(entity.getCurrency())
                .isIncluded(entity.getIsIncluded())
                .status(entity.getStatus())
                .cancellationReason(entity.getCancellationReason())
                .active(entity.getActive())
                .passengers(passengerList)
                .vehicles(vehicleList)
                .guides(guideList)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
