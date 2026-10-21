package com.cruise.activity.controller;

import com.cruise.activity.dto.ActivityCsatSummaryDto;
import com.cruise.activity.dto.ActivityFeedbackResponse;
import com.cruise.activity.dto.SubmitFeedbackRequest;
import com.cruise.activity.service.ActivityFeedbackService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities/{activityId}/feedback")
@RequiredArgsConstructor
public class ActivityFeedbackController {

    private final ActivityFeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivityFeedbackResponse> submitFeedback(
            @PathVariable Long activityId,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        return ApiResponse.ok("Gửi đánh giá trải nghiệm thành công",
                feedbackService.submitFeedback(activityId, request));
    }

    @GetMapping
    public ApiResponse<List<ActivityFeedbackResponse>> getFeedbacks(@PathVariable Long activityId) {
        return ApiResponse.ok("Lấy danh sách đánh giá của hoạt động thành công",
                feedbackService.getFeedbacksByActivity(activityId));
    }

    @GetMapping("/summary")
    public ApiResponse<ActivityCsatSummaryDto> getCsatSummary(@PathVariable Long activityId) {
        return ApiResponse.ok("Lấy thống kê đánh giá CSAT thành công",
                feedbackService.getCsatSummary(activityId));
    }
}
