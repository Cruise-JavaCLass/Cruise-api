package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.service.DailyItineraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itinerary/tours/{tourId}/daily")
public class DailyItineraryController {

    private final DailyItineraryService dailyItineraryService;

    public DailyItineraryController(DailyItineraryService dailyItineraryService) {
        this.dailyItineraryService = dailyItineraryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DailyItineraryResponse>> createDailyItinerary(
            @PathVariable Long tourId,
            @Valid @RequestBody DailyItineraryRequest request
    ) {
        DailyItineraryResponse response = dailyItineraryService.createDailyItinerary(tourId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo lịch trình ngày thành công", response));
    }

    @PutMapping("/{itineraryId}")
    public ResponseEntity<ApiResponse<DailyItineraryResponse>> updateDailyItinerary(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId,
            @Valid @RequestBody DailyItineraryRequest request
    ) {
        DailyItineraryResponse response = dailyItineraryService.updateDailyItinerary(tourId, itineraryId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật lịch trình ngày thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DailyItineraryResponse>>> getDailyItineraries(
            @PathVariable Long tourId
    ) {
        List<DailyItineraryResponse> responses = dailyItineraryService.getDailyItinerariesByTourId(tourId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách lịch trình theo ngày thành công", responses));
    }

    @GetMapping("/{itineraryId}")
    public ResponseEntity<ApiResponse<DailyItineraryResponse>> getDailyItineraryById(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId
    ) {
        DailyItineraryResponse response = dailyItineraryService.getDailyItineraryById(tourId, itineraryId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết lịch trình ngày thành công", response));
    }

    @DeleteMapping("/{itineraryId}")
    public ResponseEntity<ApiResponse<Void>> deleteDailyItinerary(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId
    ) {
        dailyItineraryService.deleteDailyItinerary(tourId, itineraryId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa lịch trình ngày thành công", null));
    }
}
