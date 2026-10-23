package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.IngestAuditLogRequest;
import com.cruise.pos.dto.SystemAuditLogDto;
import com.cruise.pos.service.SystemAuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemAuditLogController Unit Tests")
class SystemAuditLogControllerTest {

    @Mock
    private SystemAuditLogService auditLogService;

    @InjectMocks
    private SystemAuditLogController controller;

    private final SystemAuditLogDto sampleDto = new SystemAuditLogDto(
            1L,
            "SECURITY",
            "KEY_ROTATED",
            "ADMIN",
            "TERMINAL",
            "TERM-01",
            "127.0.0.1",
            "Routine rotation",
            null,
            null,
            "hash123",
            Instant.now()
    );

    @Test
    @DisplayName("ingestAuditLog: returns 201 Created on log ingestion")
    void testIngestAuditLog() {
        IngestAuditLogRequest req = new IngestAuditLogRequest("SECURITY", "KEY_ROTATED", "ADMIN", "TERMINAL", "TERM-01", "127.0.0.1", "Routine rotation", null, null);
        when(auditLogService.ingestAuditLog(req)).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<SystemAuditLogDto>> response = controller.ingestAuditLog(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().action()).isEqualTo("KEY_ROTATED");
        verify(auditLogService).ingestAuditLog(req);
    }

    @Test
    @DisplayName("queryAuditLogs: returns paginated audit logs")
    void testQueryAuditLogs() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<SystemAuditLogDto> page = new PageImpl<>(List.of(sampleDto));
        when(auditLogService.queryAuditLogs(any(), any(), any(), any(), any(), any(), eq(pageable))).thenReturn(page);

        ResponseEntity<ApiResponse<Page<SystemAuditLogDto>>> response = controller.queryAuditLogs(
                "SECURITY", "KEY_ROTATED", "ADMIN", "TERM-01", null, null, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("exportAuditLogsCsv: returns text/csv attachment")
    void testExportAuditLogsCsv() {
        when(auditLogService.exportAuditLogsToCsv(any(), any(), any(), any())).thenReturn("logId,category,action\nLOG-01,SECURITY,KEY_ROTATED");

        ResponseEntity<byte[]> response = controller.exportAuditLogsCsv("SECURITY", "KEY_ROTATED", "ADMIN", "TERM-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(new String(response.getBody())).contains("LOG-01");
    }
}
