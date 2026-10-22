package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.service.ShoreExcursionBookingService;
import com.cruise.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý danh sách đăng ký Tour trên bờ (Passenger Roster & Manifest Control - KAN-324 / KAN-325)
 */
@RestController
@RequestMapping({"/api/v1/shore-excursions", "/api/activities/excursions"})
@RequiredArgsConstructor
public class ExcursionRosterController {

    private final ShoreExcursionBookingService bookingService;

    /**
     * Tra cứu danh sách hành khách đăng ký tour bờ kèm bộ lọc và phân trang (AC1 / SLA < 300ms)
     */
    @GetMapping("/{id}/roster")
    public ApiResponse<ExcursionRosterSummaryDto> getExcursionRoster(
            @PathVariable Long id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ExcursionBookingStatus bookingStatus,
            @RequestParam(required = false) Boolean specialAssistance,
            @RequestParam(required = false, defaultValue = "false") Boolean includeCancelled,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok("Lấy danh sách đăng ký tour bờ thành công",
                bookingService.getExcursionRoster(id, search, bookingStatus, specialAssistance, includeCancelled, pageable));
    }
}
