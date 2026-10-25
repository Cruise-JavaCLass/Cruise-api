package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.ShipDeckZoneDto;
import com.cruise.itinerary.dto.SystemCancellationPolicyDto;
import com.cruise.itinerary.service.CruiseAreaPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cruise-area-policy")
@RequiredArgsConstructor
public class CruiseAreaPolicyController {

    private final CruiseAreaPolicyService areaPolicyService;

    @GetMapping("/ships/{shipId}/deck-zones")
    public ApiResponse<List<ShipDeckZoneDto>> getDeckZones(@PathVariable Long shipId) {
        return ApiResponse.ok("Lấy danh sách khu vực boong tàu thành công", areaPolicyService.getDeckZonesByShip(shipId));
    }

    @PostMapping("/deck-zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipDeckZoneDto> createDeckZone(@Valid @RequestBody ShipDeckZoneDto dto) {
        return ApiResponse.ok("Tạo khu vực boong tàu thành công", areaPolicyService.createDeckZone(dto));
    }

    @GetMapping("/cancellation-policies")
    public ApiResponse<List<SystemCancellationPolicyDto>> getCancellationPolicies(
            @RequestParam(required = false) String serviceType
    ) {
        return ApiResponse.ok("Lấy danh sách chính sách hủy thành công", areaPolicyService.getCancellationPolicies(serviceType));
    }

    @PostMapping("/cancellation-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SystemCancellationPolicyDto> createCancellationPolicy(
            @Valid @RequestBody SystemCancellationPolicyDto dto
    ) {
        return ApiResponse.ok("Tạo chính sách hủy thành công", areaPolicyService.createCancellationPolicy(dto));
    }
}
