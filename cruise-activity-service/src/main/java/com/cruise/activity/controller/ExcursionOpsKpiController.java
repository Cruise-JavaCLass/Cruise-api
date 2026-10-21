package com.cruise.activity.controller;

import com.cruise.activity.dto.ExcursionOpsKpiResponse;
import com.cruise.activity.dto.ProviderScorecardDto;
import com.cruise.activity.service.ExcursionOpsAnalyticsService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller cho Excursion Ops KPI & Provider Performance Scorecard (KAN-343)
 */
@RestController
@RequiredArgsConstructor
public class ExcursionOpsKpiController {

    private final ExcursionOpsAnalyticsService opsAnalyticsService;

    @GetMapping({"/api/v1/shore-excursions/analytics/kpi", "/api/excursions/analytics/kpi"})
    public ApiResponse<ExcursionOpsKpiResponse> getOpsKpi() {
        return ApiResponse.ok("Lấy chỉ số KPI vận hành tour bờ thành công", opsAnalyticsService.getOpsKpiMetrics());
    }

    @GetMapping({"/api/v1/shore-excursions/analytics/provider-scorecards", "/api/excursions/analytics/provider-scorecards"})
    public ApiResponse<List<ProviderScorecardDto>> getProviderScorecards() {
        return ApiResponse.ok("Lấy bảng điểm đánh giá đối tác tour bờ thành công", opsAnalyticsService.getProviderScorecards());
    }
}
