package com.cruise.itinerary.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.service.DailyItineraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DailyItineraryResponse> createDailyItinerary(
            @PathVariable Long tourId,
            @Valid @RequestBody DailyItineraryRequest request
    ) {
        return ApiResponse.ok("Tạo lịch trình ngày thành công",
                dailyItineraryService.createDailyItinerary(tourId, request));
    }

    @PutMapping("/{itineraryId}")
    public ApiResponse<DailyItineraryResponse> updateDailyItinerary(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId,
            @Valid @RequestBody DailyItineraryRequest request
    ) {
        return ApiResponse.ok("Cập nhật lịch trình ngày thành công",
                dailyItineraryService.updateDailyItinerary(tourId, itineraryId, request));
    }

    @GetMapping
    public ApiResponse<List<DailyItineraryResponse>> getDailyItineraries(@PathVariable Long tourId) {
        return ApiResponse.ok("Lấy danh sách lịch trình theo ngày thành công",
                dailyItineraryService.getDailyItinerariesByTourId(tourId));
    }

    @GetMapping("/{itineraryId}")
    public ApiResponse<DailyItineraryResponse> getDailyItineraryById(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId
    ) {
        return ApiResponse.ok("Lấy chi tiết lịch trình ngày thành công",
                dailyItineraryService.getDailyItineraryById(tourId, itineraryId));
    }

    @DeleteMapping("/{itineraryId}")
    public ApiResponse<Void> deleteDailyItinerary(
            @PathVariable Long tourId,
            @PathVariable Long itineraryId
    ) {
        dailyItineraryService.deleteDailyItinerary(tourId, itineraryId);
        return ApiResponse.ok("Xóa lịch trình ngày thành công", null);
    }
}
