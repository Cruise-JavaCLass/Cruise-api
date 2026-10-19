package com.cruise.pos.controller;

import com.cruise.pos.dto.ShiftSummaryResponse;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.service.PosShiftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosShiftController.class)
@Import(com.cruise.pos.config.PosConfig.class)
@DisplayName("PosShiftController WebMvc Tests")
class PosShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosShiftService shiftService;

    @Test
    @DisplayName("POST /api/pos/shifts/open opens a shift")
    void testOpenShift() throws Exception {
        PosShift shift = PosShift.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .status(ShiftStatus.OPEN)
                .build();
        shift.setId(1L);

        when(shiftService.openShift(any(), any(), any())).thenReturn(shift);

        mockMvc.perform(post("/api/pos/shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stationId", "BAR-DECK-9",
                                "cashierId", "CASHIER-01",
                                "startingCash", 100.00
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stationId").value("BAR-DECK-9"));
    }

    @Test
    @DisplayName("POST /api/pos/shifts/open rejects missing stationId")
    void testOpenShiftMissingStationId() throws Exception {
        mockMvc.perform(post("/api/pos/shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cashierId", "CASHIER-01"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("stationId không được để trống khi mở ca làm việc"));
    }

    @Test
    @DisplayName("POST /api/pos/shifts/{id}/close closes a shift")
    void testCloseShift() throws Exception {
        PosShift shift = PosShift.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .status(ShiftStatus.CLOSED)
                .closedByManagerId("MGR-01")
                .build();
        shift.setId(1L);

        when(shiftService.closeShift(any(), any(), any())).thenReturn(shift);

        mockMvc.perform(post("/api/pos/shifts/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "managerId", "MGR-01",
                                "endingCash", 250.00
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @DisplayName("GET /api/pos/shifts/active returns active shift")
    void testGetActiveShift() throws Exception {
        PosShift shift = PosShift.builder()
                .stationId("BAR-DECK-9")
                .status(ShiftStatus.OPEN)
                .build();
        shift.setId(1L);

        when(shiftService.getActiveShift("BAR-DECK-9")).thenReturn(Optional.of(shift));

        mockMvc.perform(get("/api/pos/shifts/active")
                        .param("stationId", "BAR-DECK-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /api/pos/reports/shift-summary returns report")
    void testGetShiftSummary() throws Exception {
        ShiftSummaryResponse response = ShiftSummaryResponse.builder()
                .shiftId(1L)
                .reportType("X_REPORT")
                .netRevenue(BigDecimal.valueOf(500.00))
                .build();

        when(shiftService.generateShiftSummary(1L, "X_REPORT")).thenReturn(response);

        mockMvc.perform(get("/api/pos/reports/shift-summary")
                        .param("shiftId", "1")
                        .param("reportType", "X_REPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportType").value("X_REPORT"));
    }
}
