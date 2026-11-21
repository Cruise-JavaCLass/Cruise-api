package com.cruise.activity.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class SafetyTelemetryDtos {
    private SafetyTelemetryDtos() {}
    public record BeaconPing(
        @NotBlank @Pattern(regexp="(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") String macAddress,
        @NotBlank @Size(max=100) String zoneCode,
        @NotNull @Min(-127) @Max(20) Integer rssi,
        @NotNull Instant timestamp) {}
    public record LastKnownLocation(Long passengerId, Integer deckNumber, String zoneCode,
        Integer rssiSignalStrength, Instant recordedAt, boolean fresh) {}
}
