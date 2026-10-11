package com.cruise.itinerary.dto;

import com.cruise.itinerary.entity.CruiseStop;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class CruiseStopResponse {

    private Long id;
    private Long tourId;
    private String tourName;
    private String tourCode;
    private Long portId;
    private String portName;
    private String portCode;
    private String portCity;
    private Integer stopOrder;
    private LocalDateTime arrivalTime;
    private LocalDateTime departureTime;
    private LocalDateTime allAboardTime;
    private String description;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static CruiseStopResponse fromEntity(CruiseStop stop) {
        if (stop == null) return null;
        return CruiseStopResponse.builder()
                .id(stop.getId())
                .tourId(stop.getTour() != null ? stop.getTour().getId() : null)
                .tourName(stop.getTour() != null ? stop.getTour().getName() : null)
                .tourCode(stop.getTour() != null ? stop.getTour().getCode() : null)
                .portId(stop.getPort() != null ? stop.getPort().getId() : null)
                .portName(stop.getPort() != null ? stop.getPort().getName() : null)
                .portCode(stop.getPort() != null ? stop.getPort().getCode() : null)
                .portCity(stop.getPort() != null ? stop.getPort().getCity() : null)
                .stopOrder(stop.getStopOrder())
                .arrivalTime(stop.getArrivalTime())
                .departureTime(stop.getDepartureTime())
                .allAboardTime(stop.getAllAboardTime())
                .description(stop.getDescription())
                .active(stop.getActive())
                .createdAt(stop.getCreatedAt())
                .updatedAt(stop.getUpdatedAt())
                .build();
    }
}
