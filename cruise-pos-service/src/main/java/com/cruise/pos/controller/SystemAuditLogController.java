package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.IngestAuditLogRequest;
import com.cruise.pos.dto.SystemAuditLogDto;
import com.cruise.pos.service.SystemAuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemAuditLogController {

    private final SystemAuditLogService auditLogService;

    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<SystemAuditLogDto>> ingestAuditLog(
            @Valid @RequestBody IngestAuditLogRequest request) {
        SystemAuditLogDto dto = auditLogService.ingestAuditLog(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Audit log recorded securely", dto));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<SystemAuditLogDto>>> queryAuditLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 50) Pageable pageable) {
        Page<SystemAuditLogDto> page = auditLogService.queryAuditLogs(
                category, action, actor, targetId, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportAuditLogsCsv(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String targetId) {
        String csvContent = auditLogService.exportAuditLogsToCsv(category, action, actor, targetId);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs_" + Instant.now().toEpochMilli() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
