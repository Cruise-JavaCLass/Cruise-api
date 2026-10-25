package com.cruise.pos.dto;

import java.time.Instant;

/**
 * Response payload for a manager override request (KAN-606).
 *
 * @param approved    whether the override was approved
 * @param managerId   identifier of the manager who was consulted
 * @param action      the action that was overridden
 * @param auditLogId  ID of the created {@code PosAuditLog} record (null when rejected)
 * @param timestamp   server-side time of evaluation
 * @param message     human-readable result message
 */
public record ManagerOverrideResponse(

        boolean approved,

        String managerId,

        String action,

        Long auditLogId,

        Instant timestamp,

        String message
) {
}
