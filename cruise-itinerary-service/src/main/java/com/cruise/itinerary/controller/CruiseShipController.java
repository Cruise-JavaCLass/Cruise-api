package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.service.CruiseShipService;
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
 * Controller cung cấp RESTful API quản lý tàu du lịch (Cruise Ship Management)
 */
@RestController
@RequestMapping("/api/itinerary/ships")
public class CruiseShipController {

    private final CruiseShipService shipService;

    public CruiseShipController(CruiseShipService shipService) {
        this.shipService = shipService;
    }

    /**
     * Tạo mới tàu du lịch trong hệ thống
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CruiseShipResponse>> createShip(
            @Valid @RequestBody CreateCruiseShipRequest request
    ) {
        CruiseShipResponse response = shipService.createShip(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo tàu du lịch thành công", response));
    }

    /**
     * Lấy danh sách tàu du lịch (phân trang, tìm kiếm và lọc theo trạng thái)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CruiseShipResponse>>> getAllShips(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ShipStatus status,
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

        Page<CruiseShipResponse> ships = shipService.getAllShips(keyword, status, activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tàu du lịch thành công", ships));
    }

    /**
     * Lấy danh sách toàn bộ các tàu đang hoạt động (không phân trang, dùng cho dropdown chọn tàu)
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CruiseShipResponse>>> getActiveShips() {
        List<CruiseShipResponse> ships = shipService.getAllActiveShips();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tàu đang hoạt động thành công", ships));
    }

    /**
     * Lấy thông tin chi tiết tàu theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CruiseShipResponse>> getShipById(@PathVariable Long id) {
        CruiseShipResponse response = shipService.getShipById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin chi tiết tàu thành công", response));
    }

    /**
     * Lấy thông tin chi tiết tàu theo mã định danh (Code)
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CruiseShipResponse>> getShipByCode(@PathVariable String code) {
        CruiseShipResponse response = shipService.getShipByCode(code);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin chi tiết tàu theo mã thành công", response));
    }

    /**
     * Cập nhật thông tin chi tiết của tàu du lịch
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CruiseShipResponse>> updateShip(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCruiseShipRequest request
    ) {
        CruiseShipResponse response = shipService.updateShip(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin tàu du lịch thành công", response));
    }

    /**
     * Cập nhật trạng thái hoạt động của tàu du lịch
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CruiseShipResponse>> updateShipStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipStatusRequest request
    ) {
        CruiseShipResponse response = shipService.updateShipStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái tàu thành công", response));
    }

    /**
     * Xóa tàu du lịch (hỗ trợ xóa mềm hoặc xóa cứng)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteShip(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean hard
    ) {
        shipService.deleteShip(id, hard);
        String message = hard ? "Đã xóa vĩnh viễn tàu du lịch khỏi hệ thống" : "Đã hủy kích hoạt tàu du lịch thành công";
        return ResponseEntity.ok(ApiResponse.ok(message, null));
    }
}
