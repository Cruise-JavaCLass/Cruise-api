package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.service.ShoreExcursionService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Controller quản lý Tour tham quan trên bờ (Shore Excursion)
 * Cung cấp API cấu hình Giá, Sức chứa và Lịch trình (KAN-168)
 */
@RestController
@RequestMapping("/api/excursions")
@RequiredArgsConstructor
public class ShoreExcursionController {

    private final ShoreExcursionService excursionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShoreExcursionResponse> createExcursion(
            @Valid @RequestBody CreateShoreExcursionRequest request
    ) {
        return ApiResponse.ok("Tạo tour bờ thành công", excursionService.createExcursion(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShoreExcursionResponse> updateExcursion(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShoreExcursionRequest request
    ) {
        return ApiResponse.ok("Cập nhật tour bờ thành công", excursionService.updateExcursion(id, request));
    }

    /**
     * Endpoint chuyên biệt: Cấu hình giá, sức chứa và lịch trình tour bờ (KAN-168)
     */
    @PatchMapping("/{id}/price-capacity-schedule")
    public ApiResponse<ShoreExcursionResponse> configurePriceCapacityAndSchedule(
            @PathVariable Long id,
            @Valid @RequestBody ConfigureExcursionPriceCapacityScheduleRequest request
    ) {
        return ApiResponse.ok("Cấu hình giá, sức chứa và lịch trình thành công",
                excursionService.configurePriceCapacityAndSchedule(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ShoreExcursionResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateExcursionStatusRequest request
    ) {
        return ApiResponse.ok("Cập nhật trạng thái tour bờ thành công",
                excursionService.updateStatus(id, request));
    }

    @GetMapping("/{id}/status/allowed-transitions")
    public ApiResponse<Set<ExcursionStatus>> getAllowedTransitions(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách trạng thái hợp lệ thành công",
                excursionService.getAllowedStatusTransitions(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShoreExcursionResponse> getExcursionById(@PathVariable Long id) {
        return ApiResponse.ok("Lấy thông tin tour bờ thành công",
                excursionService.getExcursionById(id));
    }

    @GetMapping("/tour/{tourId}")
    public ApiResponse<List<ShoreExcursionResponse>> getExcursionsByTour(@PathVariable Long tourId) {
        return ApiResponse.ok("Lấy danh sách tour bờ thành công",
                excursionService.getExcursionsByTour(tourId));
    }

    @GetMapping("/tour/{tourId}/port/{portId}")
    public ApiResponse<List<ShoreExcursionResponse>> getExcursionsByPort(
            @PathVariable Long tourId,
            @PathVariable Long portId
    ) {
        return ApiResponse.ok("Lấy danh sách tour bờ theo cảng thành công",
                excursionService.getExcursionsByTourAndPort(tourId, portId));
    }

    @GetMapping("/tour/{tourId}/day/{dayNumber}")
    public ApiResponse<List<ShoreExcursionResponse>> getExcursionsByDay(
            @PathVariable Long tourId,
            @PathVariable Integer dayNumber
    ) {
        return ApiResponse.ok("Lấy danh sách tour bờ theo ngày thành công",
                excursionService.getExcursionsByTourAndDay(tourId, dayNumber));
    }

    @GetMapping("/tour/{tourId}/status/{status}")
    public ApiResponse<List<ShoreExcursionResponse>> getExcursionsByStatus(
            @PathVariable Long tourId,
            @PathVariable ExcursionStatus status
    ) {
        return ApiResponse.ok("Lấy danh sách tour bờ theo trạng thái thành công",
                excursionService.getExcursionsByTourAndStatus(tourId, status));
    }

    @GetMapping("/tour/{tourId}/paged")
    public ApiResponse<Page<ShoreExcursionResponse>> getExcursionsPaged(
            @PathVariable Long tourId,
            @RequestParam(required = false) Long portId,
            @RequestParam(required = false) Integer dayNumber,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "gatheringTime") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = "desc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok("Lấy danh sách phân trang tour bờ thành công",
                excursionService.getExcursionsPaged(tourId, portId, dayNumber, keyword, pageable));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExcursion(@PathVariable Long id) {
        excursionService.deleteExcursion(id);
        return ApiResponse.ok("Xóa tour bờ thành công", null);
    }

    // ==========================================
    // PASSENGER ASSIGNMENT TO EXCURSION GROUPS (KAN-176)
    // ==========================================

    @GetMapping("/{id}/passengers")
    public ApiResponse<List<ExcursionPassengerResponse>> getExcursionPassengers(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách hành khách tour bờ thành công",
                excursionService.getPassengersByExcursion(id));
    }

    @PostMapping("/{id}/passengers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShoreExcursionResponse> addPassengerToExcursion(
            @PathVariable Long id,
            @Valid @RequestBody AddExcursionPassengerRequest request
    ) {
        return ApiResponse.ok("Thêm hành khách vào tour bờ thành công",
                excursionService.addPassengerToExcursion(id, request));
    }

    @PatchMapping("/{id}/passengers/{passengerId}/group")
    public ApiResponse<ShoreExcursionResponse> assignPassengerToGroup(
            @PathVariable Long id,
            @PathVariable Long passengerId,
            @Valid @RequestBody AssignPassengerGroupRequest request
    ) {
        return ApiResponse.ok("Gán đoàn/xe cho hành khách thành công",
                excursionService.assignPassengerToGroup(id, passengerId, request));
    }

    @PatchMapping("/{id}/passengers/bulk-group")
    public ApiResponse<ShoreExcursionResponse> bulkAssignPassengersToGroup(
            @PathVariable Long id,
            @Valid @RequestBody BulkAssignPassengerGroupRequest request
    ) {
        return ApiResponse.ok("Gán đoàn/xe hàng loạt cho hành khách thành công",
                excursionService.bulkAssignPassengersToGroup(id, request));
    }

    @DeleteMapping("/{id}/passengers/{passengerId}")
    public ApiResponse<ShoreExcursionResponse> removePassengerFromExcursion(
            @PathVariable Long id,
            @PathVariable Long passengerId
    ) {
        return ApiResponse.ok("Xóa hành khách khỏi tour bờ thành công",
                excursionService.removePassengerFromExcursion(id, passengerId));
    }

    // ==========================================
    // VEHICLE & GUIDE MANAGEMENT (KAN-177)
    // ==========================================

    @GetMapping("/{id}/vehicles")
    public ApiResponse<List<ExcursionVehicleResponse>> getVehiclesByExcursion(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách phương tiện tour bờ thành công",
                excursionService.getVehiclesByExcursion(id));
    }

    @PostMapping("/{id}/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShoreExcursionResponse> addVehicleToExcursion(
            @PathVariable Long id,
            @Valid @RequestBody AddExcursionVehicleRequest request
    ) {
        return ApiResponse.ok("Thêm phương tiện vào tour bờ thành công",
                excursionService.addVehicleToExcursion(id, request));
    }

    @PatchMapping("/{id}/vehicles/{vehicleId}")
    public ApiResponse<ShoreExcursionResponse> updateVehicleInExcursion(
            @PathVariable Long id,
            @PathVariable String vehicleId,
            @RequestBody UpdateExcursionVehicleRequest request
    ) {
        return ApiResponse.ok("Cập nhật phương tiện tour bờ thành công",
                excursionService.updateVehicleInExcursion(id, vehicleId, request));
    }

    @DeleteMapping("/{id}/vehicles/{vehicleId}")
    public ApiResponse<ShoreExcursionResponse> removeVehicleFromExcursion(
            @PathVariable Long id,
            @PathVariable String vehicleId
    ) {
        return ApiResponse.ok("Xóa phương tiện khỏi tour bờ thành công",
                excursionService.removeVehicleFromExcursion(id, vehicleId));
    }

    @GetMapping("/{id}/guides")
    public ApiResponse<List<ExcursionGuideResponse>> getGuidesByExcursion(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách hướng dẫn viên tour bờ thành công",
                excursionService.getGuidesByExcursion(id));
    }

    @PostMapping("/{id}/guides")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShoreExcursionResponse> addGuideToExcursion(
            @PathVariable Long id,
            @Valid @RequestBody AddExcursionGuideRequest request
    ) {
        return ApiResponse.ok("Thêm hướng dẫn viên vào tour bờ thành công",
                excursionService.addGuideToExcursion(id, request));
    }

    @PatchMapping("/{id}/guides/{guideId}")
    public ApiResponse<ShoreExcursionResponse> updateGuideInExcursion(
            @PathVariable Long id,
            @PathVariable String guideId,
            @RequestBody UpdateExcursionGuideRequest request
    ) {
        return ApiResponse.ok("Cập nhật hướng dẫn viên tour bờ thành công",
                excursionService.updateGuideInExcursion(id, guideId, request));
    }

    @DeleteMapping("/{id}/guides/{guideId}")
    public ApiResponse<ShoreExcursionResponse> removeGuideFromExcursion(
            @PathVariable Long id,
            @PathVariable String guideId
    ) {
        return ApiResponse.ok("Xóa hướng dẫn viên khỏi tour bờ thành công",
                excursionService.removeGuideFromExcursion(id, guideId));
    }

    /**
     * Tra cứu danh sách hành khách phân nhóm theo từng đoàn/xe (KAN-178)
     */
    @GetMapping("/{id}/groups/manifest")
    public ApiResponse<ExcursionManifestSummaryResponse> getPassengerListsByGroup(@PathVariable Long id) {
        return ApiResponse.ok("Lấy danh sách phân nhóm hành khách tour bờ thành công",
                excursionService.getPassengerListsByGroup(id));
    }

    /**
     * Tự động sinh và phân bổ nhóm / xe cho hành khách tour bờ (KAN-178)
     */
    @PostMapping("/{id}/groups/auto-generate")
    public ApiResponse<ExcursionManifestSummaryResponse> autoGeneratePassengerGroups(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AutoGenerateGroupsRequest request
    ) {
        return ApiResponse.ok("Tự động sinh và phân bổ nhóm hành khách thành công",
                excursionService.autoGeneratePassengerGroups(id, request));
    }

    /**
     * Xuất danh sách hành khách theo nhóm dạng CSV (KAN-178)
     */
    @GetMapping(value = "/{id}/groups/export/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportPassengerGroupsCsv(
            @PathVariable Long id,
            @RequestParam(required = false) String groupName
    ) {
        byte[] csvData = excursionService.exportPassengerGroupsCsv(id, groupName);
        String fileName = String.format("excursion_%d_passenger_groups.csv", id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csvData);
    }

    /**
     * Điểm danh hành khách tour bờ theo vé QR / RFID / ID (KAN-224)
     */
    @PostMapping("/{id}/check-in")
    public ApiResponse<ExcursionCheckInResponse> checkInPassenger(
            @PathVariable Long id,
            @Valid @RequestBody ExcursionCheckInRequest request
    ) {
        return ApiResponse.ok("Điểm danh hành khách thành công",
                excursionService.checkInPassenger(id, request));
    }

    /**
     * Điểm danh hàng loạt hành khách tour bờ theo ID, xe hoặc đoàn (KAN-316)
     */
    @PostMapping("/{id}/attendance/bulk")
    public ApiResponse<BulkExcursionAttendanceResponse> bulkCheckInPassengers(
            @PathVariable Long id,
            @Valid @RequestBody BulkExcursionAttendanceRequest request
    ) {
        return ApiResponse.ok("Điểm danh hàng loạt thành công",
                excursionService.bulkCheckInPassengers(id, request));
    }

    /**
     * Thống kê tỷ lệ điểm danh và cảnh báo hành khách chưa về tàu (KAN-317)
     */
    @GetMapping("/{id}/attendance/summary")
    public ApiResponse<ExcursionAttendanceSummaryResponse> getAttendanceSummary(@PathVariable Long id) {
        return ApiResponse.ok("Lấy thống kê điểm danh tour bờ thành công",
                excursionService.getAttendanceSummary(id));
    }
}
