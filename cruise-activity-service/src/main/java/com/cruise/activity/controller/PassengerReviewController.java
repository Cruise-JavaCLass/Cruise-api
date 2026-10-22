package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.service.PassengerReviewService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback/reviews")
@RequiredArgsConstructor
@Slf4j
public class PassengerReviewController {

    private final PassengerReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PassengerServiceReviewResponse> submitReview(@Valid @RequestBody SubmitPassengerReviewRequest request) {
        log.info("Received passenger service review for passenger: {}, rating: {}", request.getPassengerId(), request.getRating());
        PassengerServiceReviewResponse response = reviewService.submitReview(request);
        return ApiResponse.ok("Đã tiếp nhận đánh giá dịch vụ thành công", response);
    }

    @GetMapping
    public ApiResponse<org.springframework.data.domain.Page<PassengerServiceReviewResponse>> getReviews(
            @RequestParam(required = false) com.cruise.activity.enums.FeedbackStatus status,
            @RequestParam(required = false) com.cruise.activity.enums.FeedbackCategory category,
            @RequestParam(required = false) Boolean isAlert,
            org.springframework.data.domain.Pageable pageable
    ) {
        log.info("Searching reviews with status: {}, category: {}, isAlert: {}", status, category, isAlert);
        return ApiResponse.ok(reviewService.searchReviews(status, category, isAlert, pageable));
    }

    @GetMapping("/analytics")
    public ApiResponse<com.cruise.activity.dto.FeedbackAnalyticsDto> getAnalytics() {
        log.info("Fetching feedback analytics dashboard summary");
        return ApiResponse.ok(reviewService.getFeedbackAnalytics());
    }

    @PatchMapping("/{reviewId}/triage")
    public ApiResponse<PassengerServiceReviewResponse> triageReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody com.cruise.activity.dto.TriageReviewRequest request
    ) {
        log.info("Triaging review id: {} to status: {}", reviewId, request.getStatus());
        PassengerServiceReviewResponse response = reviewService.triageReview(reviewId, request);
        return ApiResponse.ok("Cập nhật trạng thái xử lý đánh giá thành công", response);
    }
}
