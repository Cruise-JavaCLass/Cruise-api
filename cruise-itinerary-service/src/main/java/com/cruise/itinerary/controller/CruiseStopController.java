package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.dto.CruiseStopResponse;
import com.cruise.itinerary.service.CruiseStopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itinerary/tours/{tourId}/stops")
public class CruiseStopController {

    private final CruiseStopService stopService;

    public CruiseStopController(CruiseStopService stopService) {
        this.stopService = stopService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CruiseStopResponse>> createStop(
            @PathVariable Long tourId,
            @Valid @RequestBody CruiseStopRequest request
    ) {
        CruiseStopResponse response = stopService.createStop(tourId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo điểm dừng hải trình thành công", response));
    }

    @PutMapping("/{stopId}")
    public ResponseEntity<ApiResponse<CruiseStopResponse>> updateStop(
            @PathVariable Long tourId,
            @PathVariable Long stopId,
            @Valid @RequestBody CruiseStopRequest request
    ) {
        CruiseStopResponse response = stopService.updateStop(tourId, stopId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật điểm dừng hải trình thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CruiseStopResponse>>> getStopsByTourId(
            @PathVariable Long tourId
    ) {
        List<CruiseStopResponse> stops = stopService.getStopsByTourId(tourId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách điểm dừng thành công", stops));
    }

    @GetMapping("/{stopId}")
    public ResponseEntity<ApiResponse<CruiseStopResponse>> getStopById(
            @PathVariable Long tourId,
            @PathVariable Long stopId
    ) {
        CruiseStopResponse response = stopService.getStopById(tourId, stopId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết điểm dừng thành công", response));
    }

    @DeleteMapping("/{stopId}")
    public ResponseEntity<ApiResponse<Void>> deleteStop(
            @PathVariable Long tourId,
            @PathVariable Long stopId
    ) {
        stopService.deleteStop(tourId, stopId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa điểm dừng thành công", null));
    }
}
