package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.service.CruiseTourService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller cung cấp RESTful API quản lý Tour du thuyền nhiều ngày (Cruise Tour Management - KAN-96)
 */
@RestController
@RequestMapping("/api/itinerary/tours")
public class CruiseTourController {

    private final CruiseTourService tourService;

    public CruiseTourController(CruiseTourService tourService) {
        this.tourService = tourService;
    }

    /**
     * Tạo mới tour du thuyền trong hệ thống
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CruiseTourResponse>> createTour(
            @Valid @RequestBody CreateCruiseTourRequest request
    ) {
        CruiseTourResponse response = tourService.createTour(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo tour du lịch thành công", response));
    }

    /**
     * Cập nhật thông tin chi tiết của tour du thuyền
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CruiseTourResponse>> updateTour(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCruiseTourRequest request
    ) {
        CruiseTourResponse response = tourService.updateTour(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin tour du lịch thành công", response));
    }

    /**
     * Cập nhật trạng thái hoạt động của tour du thuyền
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CruiseTourResponse>> updateTourStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTourStatusRequest request
    ) {
        CruiseTourResponse response = tourService.updateTourStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái tour thành công", response));
    }

    /**
     * Lấy thông tin chi tiết tour theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CruiseTourResponse>> getTourById(@PathVariable Long id) {
        CruiseTourResponse response = tourService.getTourById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin chi tiết tour thành công", response));
    }

    /**
     * Lấy thông tin chi tiết tour theo mã định danh (Code)
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CruiseTourResponse>> getTourByCode(@PathVariable String code) {
        CruiseTourResponse response = tourService.getTourByCode(code);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin chi tiết tour theo mã thành công", response));
    }

    /**
     * Lấy danh sách tour du thuyền (phân trang, tìm kiếm và lọc theo trạng thái/tàu)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CruiseTourResponse>>> getAllTours(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TourStatus status,
            @RequestParam(required = false) Long shipId,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);

        Page<CruiseTourResponse> tours = tourService.getAllTours(keyword, status, shipId, activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tour du lịch thành công", tours));
    }

    /**
     * Lấy danh sách toàn bộ các tour đang hoạt động (không phân trang)
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CruiseTourResponse>>> getActiveTours() {
        List<CruiseTourResponse> tours = tourService.getAllActiveTours();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tour đang hoạt động thành công", tours));
    }

    /**
     * Xóa tour du thuyền (hỗ trợ xóa mềm hoặc xóa cứng)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTour(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean hard
    ) {
        tourService.deleteTour(id, hard);
        String message = hard ? "Đã xóa vĩnh viễn tour du lịch khỏi hệ thống" : "Đã hủy kích hoạt tour du lịch thành công";
        return ResponseEntity.ok(ApiResponse.ok(message, null));
    }
}
