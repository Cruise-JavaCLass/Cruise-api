package com.cruise.activity.controller;

import com.cruise.activity.dto.ExcursionReviewRequest;
import com.cruise.activity.dto.ExcursionReviewResponse;
import com.cruise.activity.service.ExcursionReviewService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý Feedback và Review cho Shore Excursion (KAN-341)
 * Hỗ trợ cả 2 chuẩn đường dẫn: /api/v1/shore-excursions và /api/excursions
 */
@RestController
@RequiredArgsConstructor
public class ExcursionFeedbackController {

    private final ExcursionReviewService reviewService;

    @PostMapping({"/api/v1/shore-excursions/{id}/reviews", "/api/excursions/{id}/reviews"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExcursionReviewResponse> submitReview(
            @PathVariable("id") Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExcursionReviewRequest request
    ) {
        ExcursionReviewResponse response = reviewService.submitReview(id, request, idempotencyKey);
        return ApiResponse.ok("Gửi đánh giá tour bờ thành công", response);
    }

    @GetMapping({"/api/v1/shore-excursions/{id}/reviews", "/api/excursions/{id}/reviews"})
    public ApiResponse<Page<ExcursionReviewResponse>> getReviews(
            @PathVariable("id") Long id,
            Pageable pageable
    ) {
        return ApiResponse.ok("Lấy danh sách đánh giá thành công", reviewService.getReviewsByExcursion(id, pageable));
    }

    @GetMapping({"/api/v1/shore-excursions/reviews/flagged", "/api/excursions/reviews/flagged"})
    public ApiResponse<List<ExcursionReviewResponse>> getFlaggedReviews() {
        return ApiResponse.ok("Lấy danh sách đánh giá cảnh báo an toàn & tiêu cực thành công",
                reviewService.getFlaggedReviews());
    }
}
