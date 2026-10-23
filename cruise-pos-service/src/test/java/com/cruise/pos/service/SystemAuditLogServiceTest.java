package com.cruise.pos.service;

import com.cruise.pos.dto.IngestAuditLogRequest;
import com.cruise.pos.dto.SystemAuditLogDto;
import com.cruise.pos.entity.SystemAuditLog;
import com.cruise.pos.repository.SystemAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAuditLogServiceTest {

    @Mock
    private SystemAuditLogRepository auditLogRepository;

    @InjectMocks
    private SystemAuditLogService auditLogService;

    @Test
    @DisplayName("ingestAuditLog: creates log entry with computed immutable SHA-256 hash and verifiable integrity")
    void testIngestAuditLog() {
        IngestAuditLogRequest req = new IngestAuditLogRequest(
                "SECURITY",
                "MEMBER_PASSWORD_RESET",
                "ADMIN_JULES",
                "USER",
                "USR-9988",
                "10.0.4.15",
                "Requested by passenger at reception",
                "{\"locked\": true}",
                "{\"locked\": false}"
        );

        when(auditLogRepository.save(any(SystemAuditLog.class))).thenAnswer(inv -> {
            SystemAuditLog entry = inv.getArgument(0);
            entry.setId(101L);
            return entry;
        });

        SystemAuditLogDto dto = auditLogService.ingestAuditLog(req);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(101L);
        assertThat(dto.category()).isEqualTo("SECURITY");
        assertThat(dto.immutableHash()).isNotEmpty();
        assertThat(dto.immutableHash()).hasSize(64); // SHA-256 hex length

        SystemAuditLog savedEntity = SystemAuditLog.builder()
                .category(dto.category())
                .action(dto.action())
                .actor(dto.actor())
                .targetResource(dto.targetResource())
                .targetId(dto.targetId())
                .ipAddress(dto.ipAddress())
                .reason(dto.reason())
                .payloadBefore(dto.payloadBefore())
                .payloadAfter(dto.payloadAfter())
                .immutableHash(dto.immutableHash())
                .build();
        savedEntity.setCreatedAt(dto.createdAt());

        assertThat(auditLogService.verifyLogIntegrity(savedEntity)).isTrue();

        savedEntity.setReason("Tampered reason!");
        assertThat(auditLogService.verifyLogIntegrity(savedEntity)).isFalse();
    }

    @Test
    @DisplayName("queryAuditLogs: delegates to repository with criteria parameters")
    void testQueryAuditLogs() {
        SystemAuditLog entry = SystemAuditLog.builder()
                .category("DEVICE")
                .action("DEVICE_PROVISIONED")
                .actor("SYSTEM_ADMIN")
                .targetId("TERM-1")
                .build();
        entry.setId(1L);

        Page<SystemAuditLog> page = new PageImpl<>(List.of(entry));
        when(auditLogRepository.searchAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<SystemAuditLogDto> result = auditLogService.queryAuditLogs(
                "DEVICE", "DEVICE_PROVISIONED", "SYSTEM_ADMIN", "TERM-1", null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).category()).isEqualTo("DEVICE");
    }

    @Test
    @DisplayName("exportAuditLogsToCsv: formats CSV headers, escaping, and neutralizes formula injection")
    void testExportAuditLogsToCsv() {
        SystemAuditLog entry = SystemAuditLog.builder()
                .category("DEVICE")
                .action("REMOTE_WIPE")
                .actor("CHIEF_SECURITY")
                .targetResource("POS_TERMINAL")
                .targetId("TERM-007")
                .ipAddress("127.0.0.1")
                .reason("=cmd|' /C calc'!A0")
                .immutableHash("hash123456")
                .build();
        entry.setId(5L);

        when(auditLogRepository.exportAuditLogs(any(), any(), any(), any(), any()))
                .thenReturn(List.of(entry));

        String csv = auditLogService.exportAuditLogsToCsv("DEVICE", "REMOTE_WIPE", "CHIEF_SECURITY", "TERM-007");

        assertThat(csv).contains("ID,Timestamp,Category,Action,Actor,TargetResource,TargetID,IPAddress,Reason,ImmutableHash");
        assertThat(csv).contains("REMOTE_WIPE");
        // Check CSV formula injection defense (prefixed with single quote)
        assertThat(csv).contains("'=cmd|' /C calc'!A0");
        assertThat(csv).contains("hash123456");
    }

    @Test
    @DisplayName("verifyLogIntegrity: handles null inputs safely")
    void testVerifyLogIntegrityNulls() {
        assertThat(auditLogService.verifyLogIntegrity(null)).isFalse();

        SystemAuditLog noHash = SystemAuditLog.builder().build();
        assertThat(auditLogService.verifyLogIntegrity(noHash)).isFalse();

        SystemAuditLog noCreatedAt = SystemAuditLog.builder().immutableHash("abc").build();
        assertThat(auditLogService.verifyLogIntegrity(noCreatedAt)).isFalse();
    }

    @Test
    @DisplayName("onCreate: preserves preset createdAt for integrity hash verification")
    void testOnCreatePreservesPresetCreatedAt() {
        Instant preset = Instant.now().minusSeconds(60);
        SystemAuditLog log = SystemAuditLog.builder().build();
        log.setCreatedAt(preset);

        log.onCreate();

        assertThat(log.getCreatedAt()).isEqualTo(preset);
        assertThat(log.getUpdatedAt()).isNotNull();
    }
}
