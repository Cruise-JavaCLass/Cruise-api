package com.cruise.pos.dto;

import java.time.Instant;

public record SystemAuditLogDto(
        Long id,
        String category,
        String action,
        String actor,
        String targetResource,
        String targetId,
        String ipAddress,
        String reason,
        String payloadBefore,
        String payloadAfter,
        String immutableHash,
        Instant createdAt
) {}
