package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.service.VoyageLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/voyage-lifecycle")
@PreAuthorize("hasAnyRole('OPERATIONS_MANAGER', 'SYSTEM_ADMIN', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class VoyageLifecycleController {

    private final VoyageLifecycleService lifecycleService;

    @PostMapping
    public ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> createVoyageState(
            @Valid @RequestBody CreateVoyageStateRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        VoyageLifecycleStateDto dto = lifecycleService.createVoyageLifecycle(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Voyage lifecycle created", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoyageLifecycleStateDto>>> getActiveVoyages() {
        List<VoyageLifecycleStateDto> list = lifecycleService.getActiveVoyages();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{voyageNumber}")
    public ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> getVoyageState(
            @PathVariable String voyageNumber) {
        VoyageLifecycleStateDto dto = lifecycleService.getVoyageState(voyageNumber);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/{voyageNumber}/transition")
    public ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> transitionPhase(
            @PathVariable String voyageNumber,
            @Valid @RequestBody TransitionVoyagePhaseRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        VoyageLifecycleStateDto dto = lifecycleService.transitionPhase(voyageNumber, request, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok("Voyage phase successfully transitioned", dto));
    }

    @PostMapping("/{voyageNumber}/sync-timezone")
    public ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> syncPortTimezone(
            @PathVariable String voyageNumber,
            @Valid @RequestBody SyncPortTimezoneRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        VoyageLifecycleStateDto dto = lifecycleService.syncPortTimezone(voyageNumber, request, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok("Maritime port timezone synchronized with ship clock", dto));
    }

    @GetMapping("/{voyageNumber}/timezone-schedule")
    public ResponseEntity<ApiResponse<TimezoneShiftScheduleResponse>> getTimezoneSchedule(
            @PathVariable String voyageNumber) {
        TimezoneShiftScheduleResponse schedule = lifecycleService.getTimezoneSchedule(voyageNumber);
        return ResponseEntity.ok(ApiResponse.ok(schedule));
    }
}
