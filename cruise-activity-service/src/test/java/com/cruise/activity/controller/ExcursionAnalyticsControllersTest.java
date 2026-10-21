package com.cruise.activity.controller;

import com.cruise.activity.dto.ExcursionOpsKpiResponse;
import com.cruise.activity.dto.FleetOccupancyAnalyticsResponse;
import com.cruise.activity.dto.ProviderScorecardDto;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ExcursionOpsAnalyticsService;
import com.cruise.activity.service.FleetOccupancyAggregator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({FleetOccupancyAnalyticsController.class, ExcursionOpsKpiController.class})
@Import(GlobalExceptionHandler.class)
class ExcursionAnalyticsControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FleetOccupancyAggregator fleetAggregator;

    @MockitoBean
    private ExcursionOpsAnalyticsService opsAnalyticsService;

    @Test
    @DisplayName("GET /api/v1/shore-excursions/analytics/fleet-occupancy: Thành công 200 OK")
    void testGetFleetOccupancy() throws Exception {
        FleetOccupancyAnalyticsResponse response = new FleetOccupancyAnalyticsResponse(
                4, 160, 120, 75.0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
        when(fleetAggregator.aggregateFleetOccupancy(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/shore-excursions/analytics/fleet-occupancy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFleetCount").value(4))
                .andExpect(jsonPath("$.data.overallFleetOccupancyRate").value(75.0));
    }

    @Test
    @DisplayName("GET /api/v1/shore-excursions/analytics/kpi: Thành công 200 OK")
    void testGetOpsKpi() throws Exception {
        ExcursionOpsKpiResponse response = new ExcursionOpsKpiResponse(
                5L, 20L, 4.8, 4.9, 4.7, 4.8, 1L, Collections.emptyList(), Collections.emptyList()
        );
        when(opsAnalyticsService.getOpsKpiMetrics()).thenReturn(response);

        mockMvc.perform(get("/api/v1/shore-excursions/analytics/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExcursions").value(5))
                .andExpect(jsonPath("$.data.averageOverallRating").value(4.8));
    }

    @Test
    @DisplayName("GET /api/v1/shore-excursions/analytics/provider-scorecards: Thành công 200 OK")
    void testGetProviderScorecards() throws Exception {
        ProviderScorecardDto scorecard = new ProviderScorecardDto(
                "Halong Heritage Tours", 3L, 10L, 4.8, 4.9, 4.7, 4.8, 0L, "EXCELLENT"
        );
        when(opsAnalyticsService.getProviderScorecards()).thenReturn(List.of(scorecard));

        mockMvc.perform(get("/api/v1/shore-excursions/analytics/provider-scorecards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerName").value("Halong Heritage Tours"))
                .andExpect(jsonPath("$.data[0].performanceTier").value("EXCELLENT"));
    }
}
