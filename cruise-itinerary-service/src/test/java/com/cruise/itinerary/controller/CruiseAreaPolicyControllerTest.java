package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.ShipDeckZoneDto;
import com.cruise.itinerary.dto.SystemCancellationPolicyDto;
import com.cruise.itinerary.service.CruiseAreaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CruiseAreaPolicyController.class)
@DisplayName("CruiseAreaPolicyController Tests (KAN-282 & KAN-476)")
class CruiseAreaPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CruiseAreaPolicyService areaPolicyService;

    @Test
    @DisplayName("GET /api/admin/cruise-area-policy/ships/{id}/deck-zones returns deck zones list")
    void testGetDeckZones() throws Exception {
        ShipDeckZoneDto zone = ShipDeckZoneDto.builder()
                .id(1L)
                .shipId(10L)
                .deckNumber(7)
                .zoneName("Main Pool Deck")
                .zoneCode("DECK7-POOL")
                .maxSafetyCapacity(450)
                .zoneType("POOL_DECK")
                .build();

        when(areaPolicyService.getDeckZonesByShip(10L)).thenReturn(List.of(zone));

        mockMvc.perform(get("/api/admin/cruise-area-policy/ships/10/deck-zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].zoneName").value("Main Pool Deck"))
                .andExpect(jsonPath("$.data[0].maxSafetyCapacity").value(450));
    }

    @Test
    @DisplayName("POST /api/admin/cruise-area-policy/deck-zones creates new deck zone")
    void testCreateDeckZone() throws Exception {
        ShipDeckZoneDto zone = ShipDeckZoneDto.builder()
                .shipId(10L)
                .deckNumber(5)
                .zoneName("Starlight Theater")
                .zoneCode("DECK5-THEATER")
                .maxSafetyCapacity(800)
                .zoneType("THEATER")
                .build();

        when(areaPolicyService.createDeckZone(any())).thenReturn(zone);

        mockMvc.perform(post("/api/admin/cruise-area-policy/deck-zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zoneCode").value("DECK5-THEATER"));
    }

    @Test
    @DisplayName("GET /api/admin/cruise-area-policy/cancellation-policies returns policies")
    void testGetCancellationPolicies() throws Exception {
        SystemCancellationPolicyDto policy = SystemCancellationPolicyDto.builder()
                .id(1L)
                .policyName("Excursion Standard 24h")
                .serviceType("EXCURSION")
                .hoursBeforeCutoff(24)
                .refundPercentage(100)
                .cancellationFee(BigDecimal.ZERO)
                .isActive(true)
                .build();

        when(areaPolicyService.getCancellationPolicies("EXCURSION")).thenReturn(List.of(policy));

        mockMvc.perform(get("/api/admin/cruise-area-policy/cancellation-policies?serviceType=EXCURSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].policyName").value("Excursion Standard 24h"))
                .andExpect(jsonPath("$.data[0].refundPercentage").value(100));
    }
}
