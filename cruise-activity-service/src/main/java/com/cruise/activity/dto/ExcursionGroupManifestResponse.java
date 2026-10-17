package com.cruise.activity.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho một nhóm / đoàn hành khách tour bờ (KAN-178)
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionGroupManifestResponse {

    private String groupName;
    private String busNumber;
    private ExcursionVehicleResponse vehicleInfo;
    private ExcursionGuideResponse guideInfo;
    private Integer totalPassengers;
    private Integer checkedInCount;
    private Integer onTourCount;
    private Integer returnedSafeCount;
    private Integer pendingCount;

    @Builder.Default
    private List<ExcursionPassengerResponse> passengers = new ArrayList<>();
}
