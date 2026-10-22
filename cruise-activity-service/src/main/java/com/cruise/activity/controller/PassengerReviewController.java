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
}
