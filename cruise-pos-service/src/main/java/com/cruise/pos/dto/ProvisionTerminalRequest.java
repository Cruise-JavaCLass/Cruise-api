package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvisionTerminalRequest(
        @NotBlank(message = "deviceId is required")
        @Size(max = 64)
        String deviceId,

        @NotBlank(message = "deviceName is required")
        @Size(max = 100)
        String deviceName,

        @NotBlank(message = "stationId is required")
        @Size(max = 64)
        String stationId,

        @Size(max = 64)
        String ipAddress,

        @Size(max = 32)
        String macAddress,

        @Size(max = 32)
        String firmwareVersion,

        @Size(max = 128)
        String certificateFingerprint
) {}
