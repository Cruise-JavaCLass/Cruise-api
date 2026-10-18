package com.cruise.activity.controller;

import com.cruise.activity.dto.ConfigureTimeAndLocationRequest;
import com.cruise.activity.dto.CreateActivityRequest;
import com.cruise.activity.dto.OnboardActivityResponse;
import com.cruise.activity.service.OnboardActivityService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final OnboardActivityService activityService;
    private final com.cruise.activity.service.ActivityAttendanceService attendanceService;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok("Cruise Activity and Shore Excursion Service is running", Map.of(
                "service", "cruise-activity-service",
                "status", "UP"
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OnboardActivityResponse> createActivity(
            @Valid @RequestBody CreateActivityRequest request
    ) {
        return ApiResponse.ok("Tạo hoạt động thành công", activityService.createActivity(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OnboardActivityResponse> updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.UpdateActivityRequest request
    ) {
        return ApiResponse.ok("Cập nhật hoạt động thành công", activityService.updateActivity(id, request));
    }

    @PatchMapping("/{id}/time-location")
    public ApiResponse<OnboardActivityResponse> configureTimeAndLocation(
            @PathVariable Long id,
            @Valid @RequestBody ConfigureTimeAndLocationRequest request
    ) {
        return ApiResponse.ok("Cấu hình thời gian và địa điểm thành công",
                activityService.configureTimeAndLocation(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OnboardActivityResponse> updateActivityStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.UpdateActivityStatusRequest request
    ) {
        return ApiResponse.ok("Cập nhật trạng thái hoạt động thành công",
                activityService.updateActivityStatus(id, request));
    }

    @GetMapping("/{id}/status/allowed-transitions")
    public ApiResponse<java.util.Set<com.cruise.activity.enums.ActivityStatus>> getAllowedStatusTransitions(
            @PathVariable Long id
    ) {
        return ApiResponse.ok("Lấy danh sách trạng thái hợp lệ thành công",
                activityService.getAllowedStatusTransitions(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<OnboardActivityResponse> getActivityById(@PathVariable Long id) {
        return ApiResponse.ok("Lấy chi tiết hoạt động thành công", activityService.getActivityById(id));
    }

    @GetMapping("/tour/{tourId}")
    public ApiResponse<List<OnboardActivityResponse>> getActivitiesByTour(@PathVariable Long tourId) {
        return ApiResponse.ok("Lấy danh sách hoạt động của tour thành công",
                activityService.getActivitiesByTour(tourId));
    }

    @GetMapping("/tour/{tourId}/day/{dayNumber}")
    public ApiResponse<List<OnboardActivityResponse>> getActivitiesByTourAndDay(
            @PathVariable Long tourId,
            @PathVariable Integer dayNumber
    ) {
        return ApiResponse.ok("Lấy danh sách hoạt động theo ngày thành công",
                activityService.getActivitiesByTourAndDay(tourId, dayNumber));
    }

    @GetMapping("/tour/{tourId}/paged")
    public ApiResponse<Page<OnboardActivityResponse>> getActivitiesPaged(
            @PathVariable Long tourId,
            @RequestParam(required = false) Integer dayNumber,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok("Lấy danh sách hoạt động phân trang thành công",
                activityService.getActivitiesPaged(tourId, dayNumber, category, keyword, pageable));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ApiResponse.ok("Xóa hoạt động thành công", null);
    }

    // ==========================================
    // ACTIVITY QR CHECK-IN & ATTENDANCE APIS (KAN-219)
    // ==========================================

    /**
     * Sinh mã QR Pass động bảo mật có TTL 60s cho hành khách (KAN-222)
     */
    @GetMapping("/{id}/passengers/{passengerId}/qr-pass")
    public ApiResponse<com.cruise.activity.dto.ActivityPassResponse> generateQrPass(
            @PathVariable Long id,
            @PathVariable Long passengerId
    ) {
        return ApiResponse.ok("Sinh mã QR Pass hoạt động thành công",
                attendanceService.generateQrPass(id, passengerId));
    }

    /**
     * Soát vé và điểm danh hoạt động đa kênh (QR, RFID, ID thủ công - KAN-222)
     */
    @PostMapping("/{id}/check-in")
    public ApiResponse<com.cruise.activity.dto.ActivityCheckInResponse> checkIn(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.ActivityCheckInRequest request
    ) {
        return ApiResponse.ok("Điểm danh hoạt động thành công",
                attendanceService.checkIn(id, request));
    }

}