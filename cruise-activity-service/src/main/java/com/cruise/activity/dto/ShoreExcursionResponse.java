package com.cruise.activity.dto;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
    private Instant createdAt;
    private Instant updatedAt;

    public static ShoreExcursionResponse fromEntity(ShoreExcursion entity) {
        if (entity == null) {
            return null;
        }
        int remaining = entity.getMaxCapacity() != null
                ? Math.max(0, entity.getMaxCapacity() - (entity.getCurrentParticipants() != null ? entity.getCurrentParticipants() : 0))
                : 0;

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
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
