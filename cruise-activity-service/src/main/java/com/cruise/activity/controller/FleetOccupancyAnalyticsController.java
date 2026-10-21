package com.cruise.activity.controller;

import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse;
import com.cruise.activity.service.FleetOccupancyAggregator;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp API phân tích hiệu suất và tỷ lệ lấp đầy ghế xe tour bờ (KAN-342)
 */
@RestController
@RequiredArgsConstructor
public class FleetOccupancyAnalyticsController {

    private final FleetOccupancyAggregator aggregator;

    @GetMapping({"/api/v1/shore-excursions/analytics/fleet-occupancy", "/api/excursions/analytics/fleet-occupancy"})
    public ApiResponse<FleetOccupancyAnalyticsResponse> getFleetOccupancy(
            @RequestParam(value = "portId", required = false) Long portId,
            @RequestParam(value = "provider", required = false) String provider
    ) {
        FleetOccupancyAnalyticsResponse data = aggregator.aggregateFleetOccupancy(portId, provider);
        return ApiResponse.ok("Lấy dữ liệu phân tích tỷ lệ lấp đầy ghế xe thành công", data);
    }
}
