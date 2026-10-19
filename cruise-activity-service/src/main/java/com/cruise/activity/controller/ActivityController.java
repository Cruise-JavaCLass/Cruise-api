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
    private final com.cruise.activity.service.ActivityRegistrationService registrationService;

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

    /**
     * Đăng ký và điểm danh khách vãng lai (Walk-in) tại cửa (KAN-320)
     */
    @PostMapping("/{id}/check-in/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<com.cruise.activity.dto.WalkInCheckInResponse> walkInCheckIn(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.WalkInCheckInRequest request
    ) {
        return ApiResponse.ok("Đăng ký và điểm danh khách vãng lai thành công",
                attendanceService.walkInCheckIn(id, request));
    }

    /**
     * Thống kê tổng hợp số liệu điểm danh sự kiện theo thời gian thực (KAN-321)
     */
    @GetMapping("/{id}/attendance/summary")
    public ApiResponse<com.cruise.activity.dto.ActivityAttendanceSummaryResponse> getAttendanceSummary(
            @PathVariable Long id
    ) {
        return ApiResponse.ok("Lấy thống kê điểm danh hoạt động thành công",
                attendanceService.getAttendanceSummary(id));
    }

    /**
     * Danh sách hành khách điểm danh phân trang (Roster) có bộ lọc (KAN-321)
     */
    @GetMapping("/{id}/attendance/roster")
    public ApiResponse<Page<com.cruise.activity.dto.ActivityRosterPassengerResponse>> getActivityRoster(
            @PathVariable Long id,
            @RequestParam(required = false) com.cruise.activity.enums.ActivityAttendanceStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "checkedInAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok("Lấy danh sách điểm danh hoạt động thành công",
                attendanceService.getActivityRoster(id, status, keyword, pageable));
    }

    /**
     * Đồng bộ hàng loạt lượt quét điểm danh ngoại tuyến khi phục hồi kết nối (KAN-321)
     */
    @PostMapping("/{id}/check-in/offline-sync")
    public ApiResponse<com.cruise.activity.dto.OfflineCheckInBatchResponse> syncOfflineCheckIns(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.OfflineCheckInBatchRequest request
    ) {
        return ApiResponse.ok("Đồng bộ điểm danh ngoại tuyến thành công",
                attendanceService.syncOfflineCheckIns(id, request));
    }

    /**
     * Đăng ký tham gia hoạt động giải trí (KAN-216)
     */
    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<com.cruise.activity.dto.ActivityRegistrationResponse> registerActivity(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.activity.dto.ActivityRegistrationRequest request
    ) {
        return ApiResponse.ok("Đăng ký hoạt động thành công",
                registrationService.registerActivity(id, request));
    }

    /**
     * Hủy đăng ký hoạt động với chính sách hoàn tiền Folio và tự động đôn Waitlist (KAN-369)
     */
    @DeleteMapping("/{id}/registrations/{registrationId}")
    public ApiResponse<com.cruise.activity.dto.ActivityCancellationResponse> cancelRegistration(
            @PathVariable Long id,
            @PathVariable Long registrationId,
            @RequestParam(required = false) Long passengerId,
            @RequestParam(required = false) String reason
    ) {
        return ApiResponse.ok("Hủy đăng ký hoạt động thành công",
                registrationService.cancelRegistration(id, registrationId, passengerId, reason));
    }

    /**
     * Đôn suất thủ công từ Waitlist lên Confirmed (KAN-369)
     */
    @PostMapping("/{id}/waitlist/{waitlistId}/promote")
    public ApiResponse<com.cruise.activity.dto.ActivityRegistrationResponse> promoteWaitlist(
            @PathVariable Long id,
            @PathVariable Long waitlistId
    ) {
        return ApiResponse.ok("Đôn suất danh sách chờ thành công",
                registrationService.promoteWaitlist(id, waitlistId));
    }

    /**
     * Lấy danh sách đăng ký phân trang (Roster) có bộ lọc tìm kiếm (KAN-370)
     */
    @GetMapping("/{id}/registrations/roster")
    public ApiResponse<Page<com.cruise.activity.dto.ActivityRosterDto>> getRosterPaged(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) com.cruise.activity.enums.RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "registeredAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok("Lấy danh sách đăng ký hoạt động thành công",
                registrationService.getRosterPaged(id, keyword, status, pageable));
    }

    /**
     * Lấy danh sách hàng đợi Waitlist theo thứ tự ưu tiên (KAN-370)
     */
    @GetMapping("/{id}/waitlist")
    public ApiResponse<List<com.cruise.activity.dto.WaitlistQueueItemDto>> getWaitlistQueue(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách hàng đợi thành công",
                registrationService.getWaitlistQueue(id));
    }

    /**
     * Lấy danh sách các hoạt động đã đăng ký của hành khách (KAN-370)
     */
    @GetMapping("/{id}/registrations/my")
    public ApiResponse<List<com.cruise.activity.dto.PassengerRegistrationDto>> getMyRegistrations(
            @PathVariable Long id,
            @RequestParam Long passengerId
    ) {
        return ApiResponse.ok("Lấy danh sách hoạt động đã đăng ký thành công",
                registrationService.getMyRegistrations(passengerId));
    }

    /**
     * Xuất danh sách đăng ký hoạt động dạng CSV chuẩn UTF-8 BOM (KAN-370)
     */
    @GetMapping(value = "/{id}/registrations/export/csv", produces = "text/csv; charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> exportRosterCsv(@PathVariable Long id) {
        byte[] csv = registrationService.exportRosterCsv(id);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-" + id + "-roster.csv\"")
                .body(csv);
    }
}
