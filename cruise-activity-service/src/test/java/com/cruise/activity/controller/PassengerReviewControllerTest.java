package com.cruise.activity.controller;

import com.cruise.activity.dto.FeedbackAnalyticsDto;
import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.dto.TriageReviewRequest;
import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.activity.service.PassengerReviewService;
import com.cruise.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerReviewControllerTest {

    @Mock
    private PassengerReviewService reviewService;

    @InjectMocks
    private PassengerReviewController controller;

    @Test
    @DisplayName("submitReview returns 201 Created with response")
    void testSubmitReview() {
        PassengerServiceReviewResponse mockResponse = PassengerServiceReviewResponse.builder()
                .id(1L)
                .passengerId(101L)
                .rating(5)
                .sentiment("POSITIVE")
                .status(FeedbackStatus.NEW)
                .build();

        when(reviewService.submitReview(any(SubmitPassengerReviewRequest.class))).thenReturn(mockResponse);

        SubmitPassengerReviewRequest request = SubmitPassengerReviewRequest.builder()
                .passengerId(101L)
                .passengerName("Alice")
                .cabinNumber("CABIN-701")
                .category(FeedbackCategory.CABIN_SERVICE)
                .rating(5)
                .build();

        ApiResponse<PassengerServiceReviewResponse> response = controller.submitReview(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getRating()).isEqualTo(5);
        verify(reviewService).submitReview(request);
    }

    @Test
    @DisplayName("getAnalytics returns aggregated CSAT metrics")
    void testGetAnalytics() {
        FeedbackAnalyticsDto analytics = FeedbackAnalyticsDto.builder()
                .totalReviews(20L)
                .averageRating(4.5)
                .lowRatingAlertCount(2L)
                .build();

        when(reviewService.getFeedbackAnalytics()).thenReturn(analytics);

        ApiResponse<FeedbackAnalyticsDto> response = controller.getAnalytics();

        assertThat(response).isNotNull();
        assertThat(response.getData().getTotalReviews()).isEqualTo(20L);
        verify(reviewService).getFeedbackAnalytics();
    }

    @Test
    @DisplayName("triageReview updates review status")
    void testTriageReview() {
        PassengerServiceReviewResponse mockResponse = PassengerServiceReviewResponse.builder()
                .id(1L)
                .status(FeedbackStatus.RESOLVED)
                .build();

        TriageReviewRequest triageReq = TriageReviewRequest.builder()
                .status(FeedbackStatus.RESOLVED)
                .resolvedBy("ADMIN")
                .build();

        when(reviewService.triageReview(eq(1L), any(TriageReviewRequest.class))).thenReturn(mockResponse);

        ApiResponse<PassengerServiceReviewResponse> response = controller.triageReview(1L, triageReq);

        assertThat(response).isNotNull();
        assertThat(response.getData().getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
        verify(reviewService).triageReview(1L, triageReq);
    }
}
