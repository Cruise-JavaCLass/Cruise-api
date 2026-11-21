package com.cruise.activity.dto;
import jakarta.validation.constraints.*;
public final class SafetyConfigurationDtos {
    private SafetyConfigurationDtos() {}
    public record StationDefinition(@NotBlank @Size(max=32) String stationCode,
        @NotBlank @Size(max=100) String musterLeaderStaffId,@NotBlank @Size(max=32) String targetLifeboatNumber,
        @NotNull @Min(1) @Max(100000) Integer capacity) {}
    public record ZoneDefinition(@NotBlank @Size(max=100) String zoneCode,@NotBlank @Size(max=150) String zoneName,
        @NotNull @Min(1) @Max(14) Integer deckNumber,boolean musterStation,@Size(max=32) String musterStationCode,
        @NotNull @Min(1) @Max(100000) Integer capacity) {}
    public record BeaconDefinition(@NotBlank @Pattern(regexp="(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") String macAddress,
        @NotNull @Positive Long passengerId,@Min(0) @Max(100) Integer batteryLevel) {}
    public record AssignmentDefinition(@NotNull @Positive Long passengerId,@NotBlank @Size(max=32) String stationCode) {}
}
