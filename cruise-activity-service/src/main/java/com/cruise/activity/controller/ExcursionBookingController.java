package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.service.ShoreExcursionBookingService;
import com.cruise.activity.service.ShoreExcursionService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý đặt chỗ, hủy tour và điều phối xe Tour tham quan trên bờ (KAN-374, KAN-375, KAN-376)
 */
@RestController
@RequestMapping("/api/activities/excursions")
@RequiredArgsConstructor
public class ExcursionBookingController {

    private final ShoreExcursionBookingService bookingService;
    private final ShoreExcursionService excursionService;

    /**
     * Hành khách đặt tour trên bờ (KAN-374 / POST /api/activities/excursions/{id}/bookings)
     */
    @PostMapping("/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExcursionBookingResponse> bookExcursion(
            @PathVariable Long id,
            @Valid @RequestBody ExcursionBookingRequest request
    ) {
        return ApiResponse.ok("Đặt tour trên bờ thành công", bookingService.bookExcursion(id, request));
    }

    /**
     * Hủy vé tour trên bờ theo Cut-off policy (KAN-375 / DELETE /api/activities/excursions/{id}/bookings/{bookingId})
     */
    @DeleteMapping("/{id}/bookings/{bookingId}")
    public ApiResponse<ExcursionCancellationResponse> cancelBooking(
            @PathVariable Long id,
            @PathVariable Long bookingId,
            @RequestParam(required = false, defaultValue = "Hành khách hủy tour") String reason
    ) {
        return ApiResponse.ok("Hủy vé tour thành công", bookingService.cancelBooking(id, bookingId, reason));
    }

    /**
     * Tra cứu danh sách tour bờ đã đặt của hành khách (KAN-374 / GET /api/activities/excursions/{id}/bookings/my)
     */
    @GetMapping("/my")
    public ApiResponse<List<PassengerExcursionBookingDto>> getMyBookings(
            @RequestParam(defaultValue = "1") Long passengerId
    ) {
        return ApiResponse.ok("Lấy danh sách tour đã đặt thành công", bookingService.getMyBookings(passengerId));
    }

    /**
     * Tra cứu bảng phân xe và danh sách hành khách theo xe (KAN-376 / GET /api/activities/excursions/{id}/manifest)
     */
    @GetMapping("/{id}/manifest")
    public ApiResponse<ExcursionManifestSummaryResponse> getExcursionManifest(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách phân xe và điều phối hành khách thành công",
                excursionService.getPassengerListsByGroup(id));
    }

    /**
     * Điều phối viên đổi xe cho hành khách (KAN-376 / PATCH /api/activities/excursions/{id}/bookings/{bookingId}/reassign-bus)
     */
    @PatchMapping("/{id}/bookings/{bookingId}/reassign-bus")
    public ApiResponse<ReassignBusResponse> reassignBus(
            @PathVariable Long id,
            @PathVariable Long bookingId,
            @Valid @RequestBody ReassignBusRequest request
    ) {
        return ApiResponse.ok("Chuyển xe thành công", bookingService.reassignBus(id, bookingId, request));
    }
}
