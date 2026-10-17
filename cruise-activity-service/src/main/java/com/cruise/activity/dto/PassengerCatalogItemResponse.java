package com.cruise.activity.dto;

import com.cruise.activity.enums.CatalogItemType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO tổng hợp hiển thị mục hoạt động hoặc tour bờ trong Catalog cho hành khách (KAN-211)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerCatalogItemResponse {

    private Long id;
    private Long tourId;
    private CatalogItemType itemType;
    private String title;
    private String description;
    private String category;
    private String locationOrPort;
    private Long portId;
    private Integer dayNumber;
    private LocalDate activityDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime gatheringTime;
    private LocalTime departureTime;
    private LocalTime returnToShipTime;
    private Integer maxCapacity;
    private Integer currentParticipants;
    private Integer remainingCapacity;
    private Boolean isIncluded;
    private BigDecimal price;
    private BigDecimal childPrice;
    private String currency;
    private String status;
    private Boolean isSoldOut;
}
