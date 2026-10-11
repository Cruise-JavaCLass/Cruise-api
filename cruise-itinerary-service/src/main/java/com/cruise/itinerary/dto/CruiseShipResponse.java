package com.cruise.itinerary.dto;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO dữ liệu phản hồi thông tin tàu du lịch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseShipResponse {

    private Long id;
    private String name;
    private String code;
    private String operator;
    private Integer capacity;
    private Integer crewCapacity;
    private Integer totalCabins;
    private Integer totalDecks;
    private Integer manufacturingYear;
    private Double lengthMeters;
    private Double beamMeters;
    private Double draftMeters;
    private ShipStatus status;
    private String description;
    private String amenities;
    private String imageUrl;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static CruiseShipResponse fromEntity(CruiseShip ship) {
        if (ship == null) {
            return null;
        }
        return CruiseShipResponse.builder()
                .id(ship.getId())
                .name(ship.getName())
                .code(ship.getCode())
                .operator(ship.getOperator())
                .capacity(ship.getCapacity())
                .crewCapacity(ship.getCrewCapacity())
                .totalCabins(ship.getTotalCabins())
                .totalDecks(ship.getTotalDecks())
                .manufacturingYear(ship.getManufacturingYear())
                .lengthMeters(ship.getLengthMeters())
                .beamMeters(ship.getBeamMeters())
                .draftMeters(ship.getDraftMeters())
                .status(ship.getStatus())
                .description(ship.getDescription())
                .amenities(ship.getAmenities())
                .imageUrl(ship.getImageUrl())
                .active(ship.getActive())
                .createdAt(ship.getCreatedAt())
                .updatedAt(ship.getUpdatedAt())
                .build();
    }
}
