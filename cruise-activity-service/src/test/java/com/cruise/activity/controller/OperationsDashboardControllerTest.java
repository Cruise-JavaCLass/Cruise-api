package com.cruise.activity.controller;

import com.cruise.activity.dto.OperationsKpiSummaryDto;
import com.cruise.activity.dto.OperationsLiveRadarDto;
import com.cruise.activity.service.OperationsDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationsDashboardController.class)
@DisplayName("OperationsDashboardController Tests (KAN-272 & KAN-456)")
class OperationsDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperationsDashboardService dashboardService;

    @Test
    @DisplayName("GET /api/operations/dashboard/kpis returns aggregated fleet KPIs")
    void testGetOperationsKpis() throws Exception {
        OperationsKpiSummaryDto kpis = OperationsKpiSummaryDto.builder()
                .totalPassengers(2500)
                .checkedInPassengers(2250)
                .embarkationRate(90.0)
                .totalActivitiesScheduled(15)
                .totalActivitiesActive(12)
                .totalActivityRegistrations(600)
                .totalShoreExcursions(8)
                .departingExcursionsToday(4)
                .totalExcursionBookings(450)
                .averageActivityRating(4.8)
                .totalFeedbackSubmitted(320)
                .generatedAt(Instant.now())
                .activitiesByCategory(Map.of("ENTERTAINMENT", 5L, "FITNESS", 4L))
                .build();

        when(dashboardService.getOperationsKpiSummary()).thenReturn(kpis);

        mockMvc.perform(get("/api/operations/dashboard/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPassengers").value(2500))
                .andExpect(jsonPath("$.data.checkedInPassengers").value(2250))
                .andExpect(jsonPath("$.data.embarkationRate").value(90.0))
                .andExpect(jsonPath("$.data.averageActivityRating").value(4.8));
    }

    @Test
    @DisplayName("GET /api/operations/dashboard/live-radar returns live occupancy and departure radar")
    void testGetLiveRadar() throws Exception {
        OperationsLiveRadarDto radar = OperationsLiveRadarDto.builder()
                .timestamp(Instant.now())
                .activityRadar(Collections.emptyList())
                .excursionRadar(Collections.emptyList())
                .build();

        when(dashboardService.getLiveOperationsRadar()).thenReturn(radar);

        mockMvc.perform(get("/api/operations/dashboard/live-radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activityRadar").isArray())
                .andExpect(jsonPath("$.data.excursionRadar").isArray());
    }
}
