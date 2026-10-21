package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.exception.DuplicateCheckInException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.service.EmbarkationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmbarkationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("EmbarkationController Integration & API Tests")
class EmbarkationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbarkationService embarkationService;

    @Test
    @DisplayName("GET /api/activities/passengers/embarkation/lookup - returns 200 OK with PassengerEmbarkationProfileDto")
    void lookupPassenger_Success() throws Exception {
        PassengerEmbarkationProfileDto profile = PassengerEmbarkationProfileDto.builder()
                .id(1L)
                .bookingCode("BK-1001")
                .fullName("John Doe")
                .passportNumber("P987654")
                .cabinNumber("Suite-101")
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .build();

        when(embarkationService.lookupPassenger("P987654")).thenReturn(profile);

        mockMvc.perform(get("/api/activities/passengers/embarkation/lookup")
                        .param("keyword", "P987654"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("John Doe"))
                .andExpect(jsonPath("$.data.passportNumber").value("P987654"))
                .andExpect(jsonPath("$.data.cabinNumber").value("Suite-101"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/embarkation/lookup - returns 404 when passenger not found")
    void lookupPassenger_NotFound() throws Exception {
        when(embarkationService.lookupPassenger("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy thông tin hành khách"));

        mockMvc.perform(get("/api/activities/passengers/embarkation/lookup")
                        .param("keyword", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin hành khách"));
    }

    @Test
    @DisplayName("POST /api/activities/passengers/{id}/embarkation/check-in - returns 200 OK with EmbarkationCheckInResponse")
    void checkIn_Success() throws Exception {
        EmbarkationCheckInResponse response = EmbarkationCheckInResponse.builder()
                .passengerId(1L)
                .checkInStatus(CheckInStatus.BOARDED)
                .boardedAt(Instant.now())
                .rfidTag("RFID-777")
                .cabinNumber("Suite-101")
                .folioStatus("ACTIVE")
                .build();

        when(embarkationService.processCheckIn(eq(1L), any())).thenReturn(response);

        String jsonBody = "{\"rfidTag\":\"RFID-777\",\"staffId\":\"STAFF-01\"}";

        mockMvc.perform(post("/api/activities/passengers/1/embarkation/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInStatus").value("BOARDED"))
                .andExpect(jsonPath("$.data.folioStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/activities/passengers/{id}/embarkation/check-in - returns 409 Conflict when already BOARDED")
    void checkIn_DuplicateConflict() throws Exception {
        when(embarkationService.processCheckIn(eq(1L), any()))
                .thenThrow(new DuplicateCheckInException("Hành khách đã làm thủ tục lên tàu lúc 10:15"));

        mockMvc.perform(post("/api/activities/passengers/1/embarkation/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hành khách đã làm thủ tục lên tàu lúc 10:15"));
    }

    @Test
    @DisplayName("POST /api/activities/passengers/{id}/embarkation/bind-rfid - returns 200 OK")
    void bindRfid_Success() throws Exception {
        BindRfidResponse response = BindRfidResponse.builder()
                .passengerId(1L)
                .rfidTag("RFID-CARD-888")
                .folioStatus("ACTIVE")
                .cabinNumber("Suite-101")
                .build();

        when(embarkationService.bindRfid(eq(1L), any())).thenReturn(response);

        String jsonBody = "{\"rfidTag\":\"RFID-CARD-888\",\"activateFolio\":true}";

        mockMvc.perform(post("/api/activities/passengers/1/embarkation/bind-rfid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rfidTag").value("RFID-CARD-888"))
                .andExpect(jsonPath("$.data.folioStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/activities/passengers/{id}/embarkation/bind-rfid - returns 409 Conflict when RFID already assigned")
    void bindRfid_Conflict() throws Exception {
        when(embarkationService.bindRfid(eq(1L), any()))
                .thenThrow(new DuplicateRegistrationException("Mã thẻ RFID đã được gán"));

        String jsonBody = "{\"rfidTag\":\"RFID-DUPLICATE\"}";

        mockMvc.perform(post("/api/activities/passengers/1/embarkation/bind-rfid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mã thẻ RFID đã được gán"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/embarkation/summary - returns 200 OK with progress metrics")
    void getSummary_Success() throws Exception {
        EmbarkationSummaryDto summary = EmbarkationSummaryDto.builder()
                .tourId(101L)
                .expectedTotal(100)
                .boardedCount(80)
                .remainingCount(20)
                .boardingProgressPercent(80.0)
                .categoryBreakdown(Map.of("Royal Suite", 20L))
                .build();

        when(embarkationService.getEmbarkationSummary(101L)).thenReturn(summary);

        mockMvc.perform(get("/api/activities/passengers/embarkation/summary")
                        .param("tourId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedTotal").value(100))
                .andExpect(jsonPath("$.data.boardedCount").value(80))
                .andExpect(jsonPath("$.data.boardingProgressPercent").value(80.0));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/embarkation/manifest/export - returns CSV file stream")
    void exportManifest_Success() throws Exception {
        byte[] csv = "ID,FullName\n1,John Doe\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(embarkationService.exportBorderControlManifestCsv(101L)).thenReturn(csv);

        mockMvc.perform(get("/api/activities/passengers/embarkation/manifest/export")
                        .param("tourId", "101"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"border_control_manifest_tour_101.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"));
    }
}
