package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.SettlementStatus;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ProviderReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourSettlementController.class)
@Import(GlobalExceptionHandler.class)
class TourSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderReconciliationService reconciliationService;

    private SettlementDetailDto createSampleDto() {
        return new SettlementDetailDto(
                1L,
                "phuket-tours",
                "Phuket Tours Co.",
                10L,
                "Phuket Island Trip",
                100L,
                200L,
                "Phuket Port",
                BigDecimal.valueOf(100.0),
                20,
                30,
                28,
                2,
                28,
                BigDecimal.valueOf(2800.0),
                BigDecimal.valueOf(-100.0),
                BigDecimal.valueOf(2700.0),
                "USD",
                SettlementStatus.DRAFT,
                null,
                null,
                "Note",
                Instant.now(),
                Instant.now(),
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("POST /api/v1/tour-providers/settlements - Khởi tạo đối soát thành công")
    void testInitializeSettlement() throws Exception {
        SettlementDetailDto sample = createSampleDto();
        when(reconciliationService.initializeOrRecalculateSettlement(any())).thenReturn(sample);

        String jsonPayload = """
                {
                    "excursionId": 10,
                    "providerId": "phuket-tours",
                    "agreedUnitRate": 100.0,
                    "minGuaranteePax": 20,
                    "notes": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/tour-providers/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.providerId").value("phuket-tours"))
                .andExpect(jsonPath("$.data.totalAmount").value(2700.0));
    }

    @Test
    @DisplayName("POST /api/v1/tour-providers/settlements/{id}/adjustments - Ghi nhận dispute/khấu trừ")
    void testAddAdjustment() throws Exception {
        SettlementDetailDto sample = createSampleDto();
        when(reconciliationService.addAdjustment(eq(1L), any())).thenReturn(sample);

        String jsonPayload = """
                {
                    "adjustmentType": "PENALTY_WEATHER_DELAY",
                    "amount": 100.0,
                    "invoiceReference": "INV-100",
                    "explanation": "Trễ do thời tiết xấu",
                    "recordedBy": "MANAGER"
                }
                """;

        mockMvc.perform(post("/api/v1/tour-providers/settlements/1/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/tour-providers/settlements/{id}/status - Cập nhật trạng thái thành công")
    void testUpdateStatus() throws Exception {
        SettlementDetailDto sample = createSampleDto();
        when(reconciliationService.updateStatus(eq(1L), any())).thenReturn(sample);

        String jsonPayload = """
                {
                    "targetStatus": "PENDING_VERIFICATION",
                    "actor": "EXC_MGR",
                    "note": "Gửi duyệt"
                }
                """;

        mockMvc.perform(patch("/api/v1/tour-providers/settlements/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/tour-providers/{id}/settlement-summary - Xuất biên bản đối soát thành công")
    void testGetSettlementSummary() throws Exception {
        ProviderSettlementSummaryResponse summary = new ProviderSettlementSummaryResponse(
                "phuket-tours",
                "Phuket Tours Co.",
                100L,
                1,
                30,
                28,
                2,
                28,
                BigDecimal.valueOf(2800.0),
                BigDecimal.valueOf(-100.0),
                BigDecimal.valueOf(2700.0),
                "USD",
                List.of(createSampleDto())
        );

        when(reconciliationService.getSettlementSummary("phuket-tours", 100L)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/tour-providers/phuket-tours/settlement-summary")
                        .param("voyageId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grandTotalAmount").value(2700.0))
                .andExpect(jsonPath("$.data.totalExcursions").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/tour-providers/settlements/{id} - Lấy chi tiết quyết toán thành công")
    void testGetSettlementById() throws Exception {
        SettlementDetailDto sample = createSampleDto();
        when(reconciliationService.getSettlementById(1L)).thenReturn(sample);

        mockMvc.perform(get("/api/v1/tour-providers/settlements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
