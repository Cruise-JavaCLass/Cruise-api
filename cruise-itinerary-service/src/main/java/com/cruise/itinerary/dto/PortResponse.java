package com.cruise.itinerary.dto;

import com.cruise.itinerary.entity.Port;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PortResponse {

    private Long id;
    private String name;
    private String code;
    private String country;
    private String city;
    private String timezone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static PortResponse fromEntity(Port port) {
        if (port == null) return null;
        return PortResponse.builder()
                .id(port.getId())
                .name(port.getName())
                .code(port.getCode())
                .country(port.getCountry())
                .city(port.getCity())
                .timezone(port.getTimezone())
                .latitude(port.getLatitude())
                .longitude(port.getLongitude())
                .description(port.getDescription())
                .active(port.getActive())
                .createdAt(port.getCreatedAt())
                .updatedAt(port.getUpdatedAt())
                .build();
    }
}
