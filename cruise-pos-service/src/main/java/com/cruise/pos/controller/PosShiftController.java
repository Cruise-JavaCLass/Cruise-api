package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.dto.ShiftSummaryResponse;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.service.PosShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosShiftController {

    private final PosShiftService shiftService;

    @PostMapping("/shifts/open")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PosShift> openShift(@RequestBody Map<String, Object> payload) {
        Object stationObj = payload != null ? payload.get("stationId") : null;
        if (stationObj == null || stationObj.toString().isBlank()) {
            throw new BusinessRuleException("stationId không được để trống khi mở ca làm việc");
        }
        String stationId = stationObj.toString().trim();
        String cashierId = payload.get("cashierId") != null ? payload.get("cashierId").toString().trim() : "DEFAULT-CASHIER";
        BigDecimal startingCash = payload.get("startingCash") != null
                ? new BigDecimal(payload.get("startingCash").toString())
                : BigDecimal.ZERO;

        PosShift shift = shiftService.openShift(stationId, cashierId, startingCash);
        return ApiResponse.ok("Mở ca làm việc thành công", shift);
    }

    @PostMapping("/shifts/{id}/close")
    public ApiResponse<PosShift> closeShift(
            @PathVariable("id") Long shiftId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        String managerId = payload != null && payload.containsKey("managerId")
                ? (String) payload.get("managerId")
                : "MANAGER-DEFAULT";
        BigDecimal endingCash = payload != null && payload.get("endingCash") != null
                ? new BigDecimal(payload.get("endingCash").toString())
                : null;

        PosShift shift = shiftService.closeShift(shiftId, managerId, endingCash);
        return ApiResponse.ok("Chốt ca quyết toán (Z-Report) thành công", shift);
    }

    @GetMapping("/shifts/active")
    public ApiResponse<PosShift> getActiveShift(@RequestParam("stationId") String stationId) {
        return shiftService.getActiveShift(stationId)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok("Không có ca đang mở", null));
    }

    @GetMapping("/reports/shift-summary")
    public ApiResponse<ShiftSummaryResponse> getShiftSummary(
            @RequestParam("shiftId") Long shiftId,
            @RequestParam(value = "reportType", defaultValue = "X_REPORT") String reportType
    ) {
        ShiftSummaryResponse response = shiftService.generateShiftSummary(shiftId, reportType);
        return ApiResponse.ok(response);
    }
}
