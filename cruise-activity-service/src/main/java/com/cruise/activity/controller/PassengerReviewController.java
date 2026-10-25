package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.dto.TriageReviewRequest;
import com.cruise.activity.exception.AccessDeniedException;
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
    public ApiResponse<PassengerServiceReviewResponse> submitReview(
            @RequestHeader(value = "X-Caller-Id", required = false) Long callerPassengerId,
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @Valid @RequestBody SubmitPassengerReviewRequest request
    ) {
        if (callerPassengerId == null) {
            throw new AccessDeniedException("Yêu cầu định danh người gửi đánh giá (X-Caller-Id)");
        }
        boolean isStaffOrAdmin = callerRole != null && (
                "SYSTEM_ADMIN".equalsIgnoreCase(callerRole.trim()) ||
                "ADMIN".equalsIgnoreCase(callerRole.trim()) ||
                "STAFF".equalsIgnoreCase(callerRole.trim())
        );
        if (!isStaffOrAdmin && !callerPassengerId.equals(request.getPassengerId())) {
            throw new AccessDeniedException("Hành khách chỉ được gửi đánh giá cho chính mình");
        }
        log.info("Received passenger service review for passenger: {}, rating: {}", request.getPassengerId(), request.getRating());
        PassengerServiceReviewResponse response = reviewService.submitReview(request);
        return ApiResponse.ok("Đã tiếp nhận đánh giá dịch vụ thành công", response);
    }

    @GetMapping
    public ApiResponse<org.springframework.data.domain.Page<PassengerServiceReviewResponse>> getReviews(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @RequestParam(required = false) com.cruise.activity.enums.FeedbackStatus status,
            @RequestParam(required = false) com.cruise.activity.enums.FeedbackCategory category,
            @RequestParam(required = false) Boolean isAlert,
            org.springframework.data.domain.Pageable pageable
    ) {
        verifyAdminAuthorization(callerRole);
        log.info("Searching reviews with status: {}, category: {}, isAlert: {}", status, category, isAlert);
        return ApiResponse.ok(reviewService.searchReviews(status, category, isAlert, pageable));
    }

    @GetMapping("/analytics")
    public ApiResponse<com.cruise.activity.dto.FeedbackAnalyticsDto> getAnalytics(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole
    ) {
        verifyAdminAuthorization(callerRole);
        log.info("Fetching feedback analytics dashboard summary");
        return ApiResponse.ok(reviewService.getFeedbackAnalytics());
    }

    @PatchMapping("/{reviewId}/triage")
    public ApiResponse<PassengerServiceReviewResponse> triageReview(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @PathVariable Long reviewId,
            @Valid @RequestBody TriageReviewRequest request
    ) {
        verifyAdminAuthorization(callerRole);
        log.info("Triaging review id: {} to status: {}", reviewId, request.getStatus());
        PassengerServiceReviewResponse response = reviewService.triageReview(reviewId, request);
        return ApiResponse.ok("Cập nhật trạng thái xử lý đánh giá thành công", response);
    }

    private void verifyAdminAuthorization(String callerRole) {
        if (callerRole == null || !java.util.Set.of("SYSTEM_ADMIN", "ADMIN", "STAFF")
                .contains(callerRole.trim().toUpperCase())) {
            throw new AccessDeniedException("Yêu cầu quyền SYSTEM_ADMIN để truy cập quản trị đánh giá");
        }
    }
}
