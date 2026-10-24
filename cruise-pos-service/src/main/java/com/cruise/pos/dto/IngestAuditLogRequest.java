package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IngestAuditLogRequest(
        @NotBlank(message = "category is required")
        @Size(max = 64)
        String category,

        @NotBlank(message = "action is required")
        @Size(max = 64)
        String action,

        @NotBlank(message = "actor is required")
        @Size(max = 64)
        String actor,

        @Size(max = 64)
        String targetResource,

        @Size(max = 64)
        String targetId,

        @Size(max = 64)
        String ipAddress,

        @Size(max = 512)
        String reason,

        @Size(max = 10000, message = "payloadBefore must not exceed 10000 characters")
        String payloadBefore,

        @Size(max = 10000, message = "payloadAfter must not exceed 10000 characters")
        String payloadAfter
) {}
