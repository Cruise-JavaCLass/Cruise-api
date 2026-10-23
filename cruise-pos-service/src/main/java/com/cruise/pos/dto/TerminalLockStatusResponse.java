package com.cruise.pos.dto;

import com.cruise.pos.enums.TerminalLockStatus;

import java.time.Instant;

public record TerminalLockStatusResponse(
        String terminalId,
        TerminalLockStatus status,
        String lockedBy,
        Instant lockedAt,
        String justification
) {
}
