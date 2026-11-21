package com.cruise.activity.controller;
import com.cruise.activity.dto.MusterSafetyDtos.*;
import com.cruise.activity.service.MusterDrillService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/v1/safety/muster") @RequiredArgsConstructor
public class MusterSafetyController {
    private final MusterDrillService service;
    @PostMapping("/drill/{id}/close")
    public ApiResponse<LiveDashboard> close(@PathVariable Long id) {
        return ApiResponse.ok("Muster drill closed",service.closeDrill(id));
    }
    @PostMapping("/drill/start") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LiveDashboard> start(@Valid @RequestBody DrillStartRequest request,Principal actor) {
        return ApiResponse.ok("Muster drill started",service.triggerEmergencyAlarm(request.drillType(),request.tourId(),actor.getName()));
    }
    @PostMapping("/checkin")
    public ApiResponse<ArrivalResult> checkin(@Valid @RequestBody MusterArrivalRequest request) {
        return ApiResponse.ok("Muster arrival reconciled",service.recordMusterArrival(request.drillId(),request.stationCode(),request.passengerId(),request.method()));
    }
    @GetMapping("/live-dashboard")
    public ApiResponse<LiveDashboard> dashboard(@RequestParam Long drillId,@RequestParam(required=false) String stationCode) {
        return ApiResponse.ok(service.getLiveDashboard(drillId,stationCode));
    }
    @GetMapping("/missing-passengers")
    public ApiResponse<List<MissingPassenger>> missing(@RequestParam Long drillId,@RequestParam(required=false) String stationCode,
            @RequestParam(required=false) PassengerStatus status) {
        return ApiResponse.ok(service.getMissingPassengersSummary(drillId,stationCode,status));
    }
}
