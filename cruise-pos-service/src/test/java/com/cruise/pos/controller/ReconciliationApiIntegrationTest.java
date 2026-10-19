package com.cruise.pos.controller;

import com.cruise.pos.dto.*;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.exception.GlobalExceptionHandler;
import com.cruise.pos.service.DisputeService;
import com.cruise.pos.service.ReconciliationMatchingService;
import com.cruise.pos.service.ReconciliationSyncService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ReconciliationController.class, DisputeController.class, ReconciliationSyncController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("ReconciliationApiIntegrationTest MockMvc Tests")
class ReconciliationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReconciliationMatchingService matchingService;

    @MockitoBean
    private DisputeService disputeService;

    @MockitoBean
    private ReconciliationSyncService syncService;

    @Test
    @DisplayName("API: Khởi chạy mẻ đối soát tự động")
    void testRunReconciliationEndpoint() throws Exception {
        ReconciliationBatchDto batchDto = ReconciliationBatchDto.builder()
                .id(1L)
                .batchNumber("RECON-12345")
                .matchedCount(50)
                .discrepancyCount(2)
                .build();

        when(matchingService.runReconciliation(any())).thenReturn(batchDto);

        mockMvc.perform(post("/api/pos/reconciliation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.batchNumber").value("RECON-12345"));
    }

    @Test
    @DisplayName("API: Lấy danh sách sai lệch đối soát")
    void testGetDiscrepanciesEndpoint() throws Exception {
        when(matchingService.getDiscrepancies(any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/pos/reconciliation/discrepancies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("API: Tạo hồ sơ khiếu nại chi phí")
    void testCreateDisputeEndpoint() throws Exception {
        DisputeCaseDto response = DisputeCaseDto.builder()
                .id(1L)
                .disputeNumber("DISP-123")
                .status(DisputeStatus.SUBMITTED)
                .build();

        when(disputeService.createDispute(any())).thenReturn(response);

        String jsonPayload = """
                {
                    "transactionId": "TX-123",
                    "folioId": "FOLIO-123",
                    "cabinNumber": "CABIN-101",
                    "reason": "DUPLICATE_BILLING",
                    "disputeAmount": 45.00,
                    "passengerNote": "Chi tiết khiếu nại"
                }
                """;

        mockMvc.perform(post("/api/pos/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disputeNumber").value("DISP-123"));
    }

    @Test
    @DisplayName("API: Phê duyệt giải quyết khiếu nại")
    void testResolveDisputeEndpoint() throws Exception {
        DisputeCaseDto response = DisputeCaseDto.builder()
                .id(1L)
                .disputeNumber("DISP-123")
                .status(DisputeStatus.APPROVED_ADJUSTED)
                .adjustmentTransactionId("ADJ-999")
                .build();

        when(disputeService.resolveDispute(eq(1L), any())).thenReturn(response);

        String jsonPayload = """
                {
                    "decisionStatus": "APPROVED_ADJUSTED",
                    "approvedRefundAmount": 45.00,
                    "reviewerNote": "Đồng ý hoàn trả"
                }
                """;

        mockMvc.perform(post("/api/pos/disputes/1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED_ADJUSTED"));
    }

    @Test
    @DisplayName("API: Đồng bộ mẻ giao dịch POS")
    void testSyncBatchEndpoint() throws Exception {
        BatchSyncJournalResponse response = BatchSyncJournalResponse.builder()
                .totalReceived(1)
                .insertedCount(1)
                .syncStatus("COMPLETED")
                .build();

        when(syncService.syncBatch(any())).thenReturn(response);

        String jsonPayload = """
                {
                    "terminalId": "TERM-01",
                    "transactions": [
                        {
                            "posTransactionId": "TX-1",
                            "terminalId": "TERM-01",
                            "serviceArea": "BAR",
                            "cabinNumber": "CABIN-101",
                            "amount": 10.00,
                            "transactionTime": "2026-10-19T10:00:00Z"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/pos/reconciliation/sync-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insertedCount").value(1));
    }

    @Test
    @DisplayName("API: Lấy thống kê KPI đối soát realtime")
    void testGetSummaryMetricsEndpoint() throws Exception {
        ReconciliationSummaryMetricsDto metrics = ReconciliationSummaryMetricsDto.builder()
                .totalTransactionsCount(150L)
                .openDisputesCount(3L)
                .discrepancyRatePercent(0.2)
                .build();

        when(syncService.getSummaryMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/pos/reconciliation/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTransactionsCount").value(150));
    }
}
