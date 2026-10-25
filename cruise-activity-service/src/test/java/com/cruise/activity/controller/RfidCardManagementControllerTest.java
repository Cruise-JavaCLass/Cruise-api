package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.RfidCardStatus;
import com.cruise.activity.service.RfidCardManagementService;
import com.cruise.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RfidCardManagementController Unit Tests")
class RfidCardManagementControllerTest {

    @Mock
    private RfidCardManagementService rfidService;

    @InjectMocks
    private RfidCardManagementController controller;

    @Test
    @DisplayName("ingestBatch: ingests batch and returns 201 Created")
    void testIngestBatch() {
        BulkRfidIngestionRequest request = BulkRfidIngestionRequest.builder()
                .batchNumber("BATCH-100")
                .cardUids(List.of("UID-1", "UID-2"))
                .build();

        BulkRfidIngestionResponse response = BulkRfidIngestionResponse.builder()
                .batchNumber("BATCH-100")
                .totalSubmitted(2)
                .successfullyIngested(2)
                .duplicateSkipped(0)
                .build();

        when(rfidService.ingestBlankCardBatch(request)).thenReturn(response);

        ResponseEntity<ApiResponse<BulkRfidIngestionResponse>> result = controller.ingestBatch(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getData().getSuccessfullyIngested()).isEqualTo(2);
        verify(rfidService).ingestBlankCardBatch(request);
    }

    @Test
    @DisplayName("blacklistCard: blacklists card and returns 200 OK")
    void testBlacklistCard() {
        BlacklistRfidCardRequest request = BlacklistRfidCardRequest.builder()
                .cardUid("UID-1")
                .reason("LOST")
                .build();

        BlacklistRfidCardResponse response = BlacklistRfidCardResponse.builder()
                .cardUid("UID-1")
                .status(RfidCardStatus.BLACKLISTED)
                .blacklistedAt(Instant.now())
                .reason("LOST")
                .build();

        when(rfidService.blacklistCard(request)).thenReturn(response);

        ResponseEntity<ApiResponse<BlacklistRfidCardResponse>> result = controller.blacklistCard(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData().getStatus()).isEqualTo(RfidCardStatus.BLACKLISTED);
        verify(rfidService).blacklistCard(request);
    }

    @Test
    @DisplayName("getDashboard: returns dashboard metrics")
    void testGetDashboard() {
        RfidInventoryDashboardDto metrics = RfidInventoryDashboardDto.builder()
                .totalCards(100L)
                .availableCards(80L)
                .issuedCards(15L)
                .lostOrBlacklistedCards(5L)
                .build();

        when(rfidService.getDashboardMetrics()).thenReturn(metrics);

        ResponseEntity<ApiResponse<RfidInventoryDashboardDto>> result = controller.getDashboard();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData().getTotalCards()).isEqualTo(100L);
        verify(rfidService).getDashboardMetrics();
    }

    @Test
    @DisplayName("getActiveBlacklist: returns list of active blacklisted UIDs")
    void testGetActiveBlacklist() {
        when(rfidService.getActiveBlacklistedUids()).thenReturn(List.of("UID-BAD-1", "UID-BAD-2"));

        ResponseEntity<ApiResponse<List<String>>> result = controller.getActiveBlacklist();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData()).containsExactly("UID-BAD-1", "UID-BAD-2");
        verify(rfidService).getActiveBlacklistedUids();
    }
}
