package com.cruise.pos.service;

import com.cruise.pos.dto.IngestAuditLogRequest;
import com.cruise.pos.dto.SystemAuditLogDto;
import com.cruise.pos.entity.SystemAuditLog;
import com.cruise.pos.repository.SystemAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAuditLogService {

    private final SystemAuditLogRepository auditLogRepository;

    @Transactional
    public SystemAuditLogDto ingestAuditLog(IngestAuditLogRequest request) {
        log.info("Ingesting system audit log: category={}, action={}, actor={}", 
                request.category(), request.action(), request.actor());

        Instant now = Instant.now();
        String hashMaterial = buildHashMaterial(
                request.category(),
                request.action(),
                request.actor(),
                request.targetResource(),
                request.targetId(),
                request.ipAddress(),
                request.reason(),
                request.payloadBefore(),
                request.payloadAfter(),
                now.toEpochMilli()
        );
        String immutableHash = computeSha256(hashMaterial);

        SystemAuditLog auditLog = SystemAuditLog.builder()
                .category(request.category())
                .action(request.action())
                .actor(request.actor())
                .targetResource(request.targetResource())
                .targetId(request.targetId())
                .ipAddress(request.ipAddress())
                .reason(request.reason())
                .payloadBefore(request.payloadBefore())
                .payloadAfter(request.payloadAfter())
                .immutableHash(immutableHash)
                .build();
        auditLog.setCreatedAt(now);

        SystemAuditLog saved = auditLogRepository.save(auditLog);
        return toDto(saved);
    }

    public boolean verifyLogIntegrity(SystemAuditLog logEntry) {
        if (logEntry == null || logEntry.getImmutableHash() == null || logEntry.getCreatedAt() == null) {
            return false;
        }
        String material = buildHashMaterial(
                logEntry.getCategory(),
                logEntry.getAction(),
                logEntry.getActor(),
                logEntry.getTargetResource(),
                logEntry.getTargetId(),
                logEntry.getIpAddress(),
                logEntry.getReason(),
                logEntry.getPayloadBefore(),
                logEntry.getPayloadAfter(),
                logEntry.getCreatedAt().toEpochMilli()
        );
        return logEntry.getImmutableHash().equalsIgnoreCase(computeSha256(material));
    }

    @Transactional(readOnly = true)
    public Page<SystemAuditLogDto> queryAuditLogs(
            String category,
            String action,
            String actor,
            String targetId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {
        return auditLogRepository.searchAuditLogs(category, action, actor, targetId, fromDate, toDate, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsToCsv(
            String category,
            String action,
            String actor,
            String targetId) {
        // Bound export to at most 5000 records to prevent memory exhaustion
        Pageable bound = PageRequest.of(0, 5000);
        List<SystemAuditLog> logs = auditLogRepository.exportAuditLogs(category, action, actor, targetId, bound);

        StringBuilder sb = new StringBuilder();
        sb.append("ID,Timestamp,Category,Action,Actor,TargetResource,TargetID,IPAddress,Reason,ImmutableHash\n");

        for (SystemAuditLog logEntry : logs) {
            sb.append(escapeCsv(String.valueOf(logEntry.getId()))).append(",")
              .append(escapeCsv(String.valueOf(logEntry.getCreatedAt()))).append(",")
              .append(escapeCsv(logEntry.getCategory())).append(",")
              .append(escapeCsv(logEntry.getAction())).append(",")
              .append(escapeCsv(logEntry.getActor())).append(",")
              .append(escapeCsv(logEntry.getTargetResource())).append(",")
              .append(escapeCsv(logEntry.getTargetId())).append(",")
              .append(escapeCsv(logEntry.getIpAddress())).append(",")
              .append(escapeCsv(logEntry.getReason())).append(",")
              .append(escapeCsv(logEntry.getImmutableHash())).append("\n");
        }

        return sb.toString();
    }

    private String buildHashMaterial(
            String category,
            String action,
            String actor,
            String targetResource,
            String targetId,
            String ipAddress,
            String reason,
            String payloadBefore,
            String payloadAfter,
            long epochMilli) {
        return String.join("|",
                category != null ? category : "",
                action != null ? action : "",
                actor != null ? actor : "",
                targetResource != null ? targetResource : "",
                targetId != null ? targetId : "",
                ipAddress != null ? ipAddress : "",
                reason != null ? reason : "",
                payloadBefore != null ? payloadBefore : "",
                payloadAfter != null ? payloadAfter : "",
                String.valueOf(epochMilli)
        );
    }

    private SystemAuditLogDto toDto(SystemAuditLog logEntry) {
        return new SystemAuditLogDto(
                logEntry.getId(),
                logEntry.getCategory(),
                logEntry.getAction(),
                logEntry.getActor(),
                logEntry.getTargetResource(),
                logEntry.getTargetId(),
                logEntry.getIpAddress(),
                logEntry.getReason(),
                logEntry.getPayloadBefore(),
                logEntry.getPayloadAfter(),
                logEntry.getImmutableHash(),
                logEntry.getCreatedAt() != null ? logEntry.getCreatedAt() : Instant.now()
        );
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String sanitized = value;
        // Formula injection defense (CWE-1236)
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("-") 
                || sanitized.startsWith("@") || sanitized.startsWith("\t") || sanitized.startsWith("\r")) {
            sanitized = "'" + sanitized;
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }
}
