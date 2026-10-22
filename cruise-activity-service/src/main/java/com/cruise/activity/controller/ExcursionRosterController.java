package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.service.ShoreExcursionBookingService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý danh sách đăng ký Tour trên bờ (Passenger Roster & Manifest Control - KAN-324 / KAN-325, KAN-326)
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

    /**
     * Tra cứu hàng đợi danh sách chờ (Waitlist Queue) theo thứ tự FIFO (AC2)
     */
    @GetMapping("/{id}/waitlist")
    public ApiResponse<List<ExcursionWaitlistEntryDto>> getWaitlistQueue(@PathVariable Long id) {
        return ApiResponse.ok("Lấy hàng đợi danh sách chờ thành công", bookingService.getWaitlistQueue(id));
    }

    /**
     * Thủ công đôn hành khách đầu tiên trong danh sách chờ lên CONFIRMED (AC2)
     */
    @PostMapping("/{id}/waitlist/promote")
    public ApiResponse<WaitlistPromotionResultDto> promoteNextWaitlistPassenger(@PathVariable Long id) {
        return ApiResponse.ok("Đôn vé danh sách chờ thành công", bookingService.promoteNextWaitlistPassenger(id));
    }

    /**
     * Hủy vé với quyền điều phối viên và áp dụng miễn phạt y tế (Medical Waiver Override - AC4)
     */
    @PostMapping("/{id}/bookings/{bookingId}/cancel-waiver")
    public ApiResponse<ExcursionCancellationResponse> cancelWithMedicalWaiver(
            @PathVariable Long id,
            @PathVariable Long bookingId,
            @Valid @RequestBody MedicalWaiverCancellationRequest request
    ) {
        return ApiResponse.ok("Hủy vé với miễn trừ y tế thành công",
                bookingService.cancelBookingWithMedicalWaiver(id, bookingId, request));
    }
}
