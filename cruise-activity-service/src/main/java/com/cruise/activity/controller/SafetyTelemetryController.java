package com.cruise.activity.controller;
import com.cruise.activity.dto.SafetyTelemetryDtos.*;
import com.cruise.activity.dto.SafetyConfigurationDtos.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.service.*;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/safety") @RequiredArgsConstructor
public class SafetyTelemetryController {
    private final PassengerTelemetryService telemetry;
    private final SafetyConfigurationService configuration;
    @PostMapping("/telemetry/pings") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<LastKnownLocation> ping(@Valid @RequestBody BeaconPing request) {
        return ApiResponse.ok("Ping admitted for asynchronous persistence",telemetry.recordBeaconPing(request.macAddress(),request.zoneCode(),request.rssi(),request.timestamp()));
    }
    @GetMapping("/telemetry/passengers/{passengerId}")
    public ApiResponse<LastKnownLocation> location(@PathVariable Long passengerId) {
        return ApiResponse.ok(telemetry.getLastKnownLocation(passengerId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No known location")));
    }
    @PostMapping("/config/stations") public ApiResponse<MusterStation> station(@Valid @RequestBody StationDefinition request) { return ApiResponse.ok(configuration.configureStation(request)); }
    @PostMapping("/config/zones") public ApiResponse<DeckZone> zone(@Valid @RequestBody ZoneDefinition request) { return ApiResponse.ok(configuration.configureZone(request)); }
    @PostMapping("/config/beacons") public ApiResponse<BleBeaconDevice> beacon(@Valid @RequestBody BeaconDefinition request) { return ApiResponse.ok(configuration.configureBeacon(request)); }
    @PostMapping("/config/assignments") public ApiResponse<MusterAssignment> assignment(@Valid @RequestBody AssignmentDefinition request) { return ApiResponse.ok(configuration.assign(request)); }
}
