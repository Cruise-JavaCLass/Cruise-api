package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CashDropRequest;
import com.cruise.pos.dto.CashDropResponse;
import com.cruise.pos.entity.PosCashDrop;
import com.cruise.pos.service.PosCashDropService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/pos/shifts", "/api/pos/shifts"})
@RequiredArgsConstructor
@Slf4j
public class PosCashDropController {

    private final PosCashDropService cashDropService;

    @PostMapping("/{id}/cash-drop")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('POS_STAFF', 'OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<CashDropResponse> recordCashDrop(
            @PathVariable("id") Long shiftId,
            @Valid @RequestBody CashDropRequest request
    ) {
        CashDropResponse response = cashDropService.processCashDrop(shiftId, request);
        return ApiResponse.ok("Mid-shift cash drop recorded successfully", response);
    }

    @GetMapping("/{id}/cash-drops")
    @PreAuthorize("hasAnyRole('POS_STAFF', 'OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<List<PosCashDrop>> getCashDrops(@PathVariable("id") Long shiftId) {
        List<PosCashDrop> drops = cashDropService.getCashDropsByShift(shiftId);
        return ApiResponse.ok("Cash drop records fetched successfully", drops);
    }
}
