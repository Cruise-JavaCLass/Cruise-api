package com.cruise.itinerary.dto;

import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.enums.TourStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO phản hồi thông tin chi tiết tour du thuyền
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseTourResponse {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Long shipId;
    private String shipName;
    private String shipCode;
    private Integer durationDays;
    private Integer durationNights;
    private String departurePort;
    private String destinationPort;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxCapacity;
    private BigDecimal basePrice;
    private String currency;
    private String itineraryOverview;
    private String imageUrl;
    private TourStatus status;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static CruiseTourResponse fromEntity(CruiseTour tour) {
        if (tour == null) {
            return null;
        }

        Long shipId = null;
        String shipName = null;
        String shipCode = null;

        if (tour.getShip() != null) {
            shipId = tour.getShip().getId();
            shipName = tour.getShip().getName();
            shipCode = tour.getShip().getCode();
        }

        return CruiseTourResponse.builder()
                .id(tour.getId())
                .name(tour.getName())
                .code(tour.getCode())
                .description(tour.getDescription())
                .shipId(shipId)
                .shipName(shipName)
                .shipCode(shipCode)
                .durationDays(tour.getDurationDays())
                .durationNights(tour.getDurationNights())
                .departurePort(tour.getDeparturePort())
                .destinationPort(tour.getDestinationPort())
                .startDate(tour.getStartDate())
                .endDate(tour.getEndDate())
                .maxCapacity(tour.getMaxCapacity())
                .basePrice(tour.getBasePrice())
                .currency(tour.getCurrency())
                .itineraryOverview(tour.getItineraryOverview())
                .imageUrl(tour.getImageUrl())
                .status(tour.getStatus())
                .active(tour.getActive())
                .createdAt(tour.getCreatedAt())
                .updatedAt(tour.getUpdatedAt())
                .build();
    }
}
