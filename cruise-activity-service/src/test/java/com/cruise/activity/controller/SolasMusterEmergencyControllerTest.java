package com.cruise.activity.controller;

import com.cruise.activity.dto.SolasMusterCheckinRequest;
import com.cruise.activity.dto.SolasMusterIncidentDto;
import com.cruise.activity.enums.MusterStatus;
import com.cruise.activity.enums.SolasEmergencyType;
import com.cruise.activity.service.SolasMusterEmergencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SolasMusterEmergencyController.class)
@DisplayName("SolasMusterEmergencyController Tests (KAN-443, KAN-444 & KAN-445)")
class SolasMusterEmergencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SolasMusterEmergencyService emergencyService;

    @Test
    @DisplayName("POST /api/operations/solas-muster/trigger creates emergency muster incident")
    void testTriggerEmergencyMuster() throws Exception {
        SolasMusterIncidentDto req = SolasMusterIncidentDto.builder()
                .emergencyType(SolasEmergencyType.GENERAL_EMERGENCY)
                .triggeredBy("CAPTAIN_JOHN")
                .announcementText("All passengers assemble at designated muster stations with lifejackets")
                .build();

        SolasMusterIncidentDto resp = SolasMusterIncidentDto.builder()
                .id(1L)
                .incidentCode("SOLAS-ABCD1234")
                .emergencyType(SolasEmergencyType.GENERAL_EMERGENCY)
                .status(MusterStatus.ACTIVE_MUSTER)
                .triggeredBy("CAPTAIN_JOHN")
                .announcementText("All passengers assemble at designated muster stations with lifejackets")
                .totalSoulsOnBoard(1500)
                .musteredSoulsCount(0)
                .musterCompletionRate(0.0)
                .triggeredAt(Instant.now())
                .build();

        when(emergencyService.triggerEmergencyMuster(any())).thenReturn(resp);

        mockMvc.perform(post("/api/operations/solas-muster/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.incidentCode").value("SOLAS-ABCD1234"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE_MUSTER"));
    }

    @Test
    @DisplayName("GET /api/operations/solas-muster/active returns active muster state")
    void testGetActiveMusterIncident() throws Exception {
        SolasMusterIncidentDto active = SolasMusterIncidentDto.builder()
                .id(1L)
                .incidentCode("SOLAS-123")
                .emergencyType(SolasEmergencyType.DRILL_PRACTICE)
                .status(MusterStatus.DRILL_IN_PROGRESS)
                .totalSoulsOnBoard(1200)
                .musteredSoulsCount(600)
                .musterCompletionRate(50.0)
                .build();

        when(emergencyService.getActiveMusterIncident()).thenReturn(Optional.of(active));

        mockMvc.perform(get("/api/operations/solas-muster/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.musteredSoulsCount").value(600))
                .andExpect(jsonPath("$.data.musterCompletionRate").value(50.0));
    }

    @Test
    @DisplayName("POST /api/operations/solas-muster/checkin records passenger arrival at muster station")
    void testCheckinPassenger() throws Exception {
        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(1L)
                .passengerId(100L)
                .musterStationCode("STATION-B")
                .lifejacketVerified(true)
                .build();

        SolasMusterIncidentDto updated = SolasMusterIncidentDto.builder()
                .id(1L)
                .incidentCode("SOLAS-123")
                .musteredSoulsCount(601)
                .musterCompletionRate(50.1)
                .build();

        when(emergencyService.checkinPassengerToMusterStation(any())).thenReturn(updated);

        mockMvc.perform(post("/api/operations/solas-muster/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.musteredSoulsCount").value(601));
    }
}
