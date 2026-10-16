package com.cruise.activity.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO tổng hợp toàn bộ danh sách phân nhóm hành khách tour bờ (KAN-178)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionManifestSummaryResponse {

    private Long excursionId;
    private String excursionTitle;
    private LocalDate excursionDate;
    private LocalTime gatheringTime;
    private LocalTime departureTime;
    private LocalTime returnToShipTime;
    private Integer totalPassengers;
    private Integer totalGroups;

    @Builder.Default
    private List<ExcursionGroupManifestResponse> groups = new ArrayList<>();
}
