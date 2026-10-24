package com.cruise.pos.dto;

import com.cruise.pos.enums.PosTerminalStatus;
import java.time.Instant;

public record PosTerminalDto(
        Long id,
        String deviceId,
        String deviceName,
        String stationId,
        String ipAddress,
        String macAddress,
        PosTerminalStatus status,
        String apiKeyPrefix,
        String certificateFingerprint,
        Instant certificateExpiry,
        Integer tokenVersion,
        Instant lastSeenAt,
        String firmwareVersion,
        String remoteWipeCommand,
        Instant remoteWipeRequestedAt,
        Instant remoteWipeAcknowledgedAt,
        Instant createdAt
) {}
