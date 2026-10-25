package com.cruise.activity.controller;

import com.cruise.activity.dto.SolasMusterCheckinRequest;
import com.cruise.activity.dto.SolasMusterIncidentDto;
import com.cruise.activity.service.SolasMusterEmergencyService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations/solas-muster")
@RequiredArgsConstructor
public class SolasMusterEmergencyController {

    private final SolasMusterEmergencyService emergencyService;

    @GetMapping("/active")
    public ApiResponse<SolasMusterIncidentDto> getActiveIncident() {
        return emergencyService.getActiveMusterIncident()
                .map(i -> ApiResponse.ok("Lấy trạng thái tập trung khẩn cấp thành công", i))
                .orElseGet(() -> ApiResponse.ok("Không có tình huống khẩn cấp nào đang diễn ra", null));
    }

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SolasMusterIncidentDto> triggerEmergency(@Valid @RequestBody SolasMusterIncidentDto dto) {
        return ApiResponse.ok("Kích hoạt báo động tập trung khẩn cấp SOLAS thành công", emergencyService.triggerEmergencyMuster(dto));
    }

    @PostMapping("/checkin")
    public ApiResponse<SolasMusterIncidentDto> checkinPassenger(@Valid @RequestBody SolasMusterCheckinRequest req) {
        return ApiResponse.ok("Điểm danh hành khách tại trạm tập trung thành công", emergencyService.checkinPassengerToMusterStation(req));
    }

    @PostMapping("/{id}/all-clear")
    public ApiResponse<SolasMusterIncidentDto> setAllClear(@PathVariable Long id) {
        return ApiResponse.ok("Đã thông báo an toàn (All Clear)", emergencyService.setAllClear(id));
    }
}
