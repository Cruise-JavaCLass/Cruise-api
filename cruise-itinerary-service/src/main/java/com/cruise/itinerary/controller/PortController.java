package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.PortRequest;
import com.cruise.itinerary.dto.PortResponse;
import com.cruise.itinerary.service.PortService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itinerary/ports")
public class PortController {

    private final PortService portService;

    public PortController(PortService portService) {
        this.portService = portService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortResponse>> createPort(@Valid @RequestBody PortRequest request) {
        PortResponse response = portService.createPort(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo cảng thành công", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PortResponse>> updatePort(
            @PathVariable Long id,
            @Valid @RequestBody PortRequest request
    ) {
        PortResponse response = portService.updatePort(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin cảng thành công", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PortResponse>> getPortById(@PathVariable Long id) {
        PortResponse response = portService.getPortById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin cảng thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PortResponse>>> getAllPorts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);
        Page<PortResponse> ports = portService.getAllPorts(keyword, activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cảng thành công", ports));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PortResponse>>> getActivePorts() {
        List<PortResponse> ports = portService.getActivePorts();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cảng hoạt động thành công", ports));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePort(@PathVariable Long id) {
        portService.deletePort(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa cảng thành công", null));
    }
}
