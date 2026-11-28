package com.cruise.activity.dining.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableAvailabilityResponse {
    private Long tableId;
    private String tableNumber;
    private Integer capacity;
    private String tableType;
    private Long restaurantId;
    private String restaurantName;
}