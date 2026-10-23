package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.GangwayMovementLog;
import com.cruise.activity.service.AllAboardWatchdogService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/operations/all-aboard-watchdog")
@RequiredArgsConstructor
public class AllAboardWatchdogController {

    private final AllAboardWatchdogService watchdogService;

    @PostMapping("/gangway-scan")
    public ResponseEntity<ApiResponse<GangwayMovementLog>> recordGangwayScan(@Valid @RequestBody GangwayScanRequest request) {
        GangwayMovementLog log = watchdogService.recordGangwayScan(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gangway movement recorded successfully", log));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AllAboardStatusDto>> getAllAboardStatus(
            @RequestParam Long tourId,
            @RequestParam Long portCallId) {
        AllAboardStatusDto status = watchdogService.getAllAboardStatus(tourId, portCallId);
        return ResponseEntity.ok(ApiResponse.ok("All-Aboard status retrieved", status));
    }

    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<String>> configurePortSchedule(
            @RequestParam Long portCallId,
            @RequestParam String portName,
            @RequestParam Instant allAboardTime,
            @RequestParam Instant departureTime) {
        watchdogService.configurePortDepartureSchedule(portCallId, portName, allAboardTime, departureTime);
        return ResponseEntity.ok(ApiResponse.ok("Port departure schedule updated successfully", "SCHEDULE_CONFIGURED"));
    }

    @PostMapping("/dispatch-alert")
    public ResponseEntity<ApiResponse<UrgentWatchdogAlertResponse>> dispatchAlerts(
            @Valid @RequestBody UrgentWatchdogAlertRequest request) {
        UrgentWatchdogAlertResponse response = watchdogService.dispatchUrgentAlerts(request);
        return ResponseEntity.ok(ApiResponse.ok("Watchdog urgent alerts dispatched", response));
    }
}
