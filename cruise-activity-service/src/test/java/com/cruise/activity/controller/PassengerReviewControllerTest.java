package com.cruise.activity.controller;

import com.cruise.activity.dto.FeedbackAnalyticsDto;
import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.dto.TriageReviewRequest;
import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.service.PassengerReviewService;
import com.cruise.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassengerReviewController Unit Tests")
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

        ApiResponse<PassengerServiceReviewResponse> response = controller.submitReview(101L, "PASSENGER", request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getRating()).isEqualTo(5);
        verify(reviewService).submitReview(request);
    }

    @Test
    @DisplayName("submitReview throws AccessDeniedException when caller passenger does not match request passengerId")
    void testSubmitReviewMismatchedCaller() {
        SubmitPassengerReviewRequest request = SubmitPassengerReviewRequest.builder()
                .passengerId(999L)
                .passengerName("Victim")
                .cabinNumber("CABIN-100")
                .category(FeedbackCategory.CABIN_SERVICE)
                .rating(1)
                .build();

        assertThatThrownBy(() -> controller.submitReview(101L, "PASSENGER", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Hành khách chỉ được gửi đánh giá cho chính mình");
    }

    @Test
    @DisplayName("getAnalytics returns aggregated CSAT metrics for SYSTEM_ADMIN")
    void testGetAnalytics() {
        FeedbackAnalyticsDto analytics = FeedbackAnalyticsDto.builder()
                .totalReviews(20L)
                .averageRating(4.5)
                .lowRatingAlertCount(2L)
                .build();

        when(reviewService.getFeedbackAnalytics()).thenReturn(analytics);

        ApiResponse<FeedbackAnalyticsDto> response = controller.getAnalytics("SYSTEM_ADMIN");

        assertThat(response).isNotNull();
        assertThat(response.getData().getTotalReviews()).isEqualTo(20L);
        verify(reviewService).getFeedbackAnalytics();
    }

    @Test
    @DisplayName("getAnalytics throws AccessDeniedException for unauthorized callers")
    void testGetAnalyticsUnauthorized() {
        assertThatThrownBy(() -> controller.getAnalytics(null))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> controller.getAnalytics("PASSENGER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getReviews delegates to reviewService for STAFF")
    void testGetReviews() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(reviewService.searchReviews(eq(FeedbackStatus.NEW), eq(FeedbackCategory.CABIN_SERVICE), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        ApiResponse<Page<PassengerServiceReviewResponse>> resp = controller.getReviews("STAFF", FeedbackStatus.NEW, FeedbackCategory.CABIN_SERVICE, false, pageable);
        assertThat(resp).isNotNull();
    }

    @Test
    @DisplayName("triageReview updates review status for ADMIN")
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

        ApiResponse<PassengerServiceReviewResponse> response = controller.triageReview("ADMIN", 1L, triageReq);

        assertThat(response).isNotNull();
        assertThat(response.getData().getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
        verify(reviewService).triageReview(1L, triageReq);
    }
}
