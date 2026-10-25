package com.cruise.activity.controller;

import com.cruise.activity.dto.OperationsKpiSummaryDto;
import com.cruise.activity.dto.OperationsLiveRadarDto;
import com.cruise.activity.service.OperationsDashboardService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/dashboard")
@RequiredArgsConstructor
public class OperationsDashboardController {

    private final OperationsDashboardService dashboardService;

    /**
     * Aggregates fleet-wide operational KPIs and summary statistics.
     */
    @GetMapping("/kpis")
    public ApiResponse<OperationsKpiSummaryDto> getOperationsKpis() {
        return ApiResponse.ok("Lấy chỉ số KPI vận hành thành công", dashboardService.getOperationsKpiSummary());
    }

    /**
     * Returns real-time attendance radar & shore excursion departures.
     */
    @GetMapping("/live-radar")
    public ApiResponse<OperationsLiveRadarDto> getLiveRadar() {
        return ApiResponse.ok("Lấy dữ liệu radar trực tiếp thành công", dashboardService.getLiveOperationsRadar());
    }
}
