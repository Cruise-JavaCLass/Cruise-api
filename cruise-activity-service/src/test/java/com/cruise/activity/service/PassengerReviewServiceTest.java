package com.cruise.activity.service;

import com.cruise.activity.dto.FeedbackAnalyticsDto;
import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.dto.TriageReviewRequest;
import com.cruise.activity.entity.PassengerServiceReview;
import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.PassengerServiceReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassengerReviewService Unit Tests")
class PassengerReviewServiceTest {

    @Mock
    private PassengerServiceReviewRepository reviewRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private PassengerReviewService reviewService;

    private SubmitPassengerReviewRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = SubmitPassengerReviewRequest.builder()
                .passengerId(101L)
                .passengerName("Alice Wonder")
                .cabinNumber("CABIN-701")
                .category(FeedbackCategory.DINING_EXPERIENCE)
                .rating(5)
                .comments("Outstanding dinner and service!")
                .serviceOutlet("Le Bistro")
                .build();
    }

    @Test
    @DisplayName("submitReview successfully persists review, locks passenger, and assigns POSITIVE sentiment")
    void testSubmitReviewSuccess() {
        when(reviewRepository.countRecentSubmissionsByPassenger(eq(101L), any())).thenReturn(0L);

        PassengerServiceReviewResponse response = reviewService.submitReview(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getPassengerId()).isEqualTo(101L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getSentiment()).isEqualTo("POSITIVE");
        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(response.getIsLowRatingAlert()).isFalse();

        verify(passengerRepository).findByIdForUpdate(101L);
        verify(reviewRepository).save(any(PassengerServiceReview.class));
    }

    @Test
    @DisplayName("submitReview flags rating <= 2 as low-rating alert and automatically escalates")
    void testSubmitLowRatingReview() {
        validRequest.setRating(2);
        validRequest.setComments("Cold food and very slow service");
        when(reviewRepository.countRecentSubmissionsByPassenger(eq(101L), any())).thenReturn(0L);

        PassengerServiceReviewResponse response = reviewService.submitReview(validRequest);

        assertThat(response.getRating()).isEqualTo(2);
        assertThat(response.getSentiment()).isEqualTo("NEGATIVE");
        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.ESCALATED);
        assertThat(response.getIsLowRatingAlert()).isTrue();
        assertThat(response.getEscalationReason()).contains("Low CSAT Score");
    }

    @Test
    @DisplayName("submitReview assigns NEUTRAL sentiment for rating 3")
    void testSubmitNeutralRatingReview() {
        validRequest.setRating(3);
        when(reviewRepository.countRecentSubmissionsByPassenger(eq(101L), any())).thenReturn(0L);

        PassengerServiceReviewResponse response = reviewService.submitReview(validRequest);

        assertThat(response.getRating()).isEqualTo(3);
        assertThat(response.getSentiment()).isEqualTo("NEUTRAL");
        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.NEW);
    }

    @Test
    @DisplayName("submitReview blocks spam when passenger exceeds 5 submissions per hour")
    void testAntiSpamProtection() {
        when(reviewRepository.countRecentSubmissionsByPassenger(eq(101L), any())).thenReturn(5L);

        assertThatThrownBy(() -> reviewService.submitReview(validRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Quá nhiều lượt gửi");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("triageReview updates status, notes, and records resolution time")
    void testTriageReview() {
        PassengerServiceReview review = PassengerServiceReview.builder()
                .passengerId(101L)
                .status(FeedbackStatus.ESCALATED)
                .rating(2)
                .build();
        review.setId(12L);

        when(reviewRepository.findById(12L)).thenReturn(Optional.of(review));

        TriageReviewRequest triageReq = TriageReviewRequest.builder()
                .status(FeedbackStatus.RESOLVED)
                .adminNotes("Offered complimentary dessert voucher to guest")
                .resolvedBy("MGR-SMITH")
                .build();

        PassengerServiceReviewResponse response = reviewService.triageReview(12L, triageReq);

        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(response.getAdminNotes()).contains("complimentary dessert");
        assertThat(response.getResolvedBy()).isEqualTo("MGR-SMITH");
        assertThat(response.getResolvedAt()).isNotNull();

        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("triageReview clears resolution metadata when review reopens to UNDER_REVIEW")
    void testTriageReviewReopenClearsResolutionMetadata() {
        Instant pastResolution = Instant.now().minusSeconds(1000);
        PassengerServiceReview review = PassengerServiceReview.builder()
                .passengerId(101L)
                .status(FeedbackStatus.RESOLVED)
                .resolvedBy("OLD_ADMIN")
                .resolvedAt(pastResolution)
                .rating(2)
                .build();
        review.setId(15L);

        when(reviewRepository.findById(15L)).thenReturn(Optional.of(review));

        TriageReviewRequest reopenReq = TriageReviewRequest.builder()
                .status(FeedbackStatus.UNDER_REVIEW)
                .adminNotes("Guest appealed, reopening case")
                .build();

        PassengerServiceReviewResponse response = reviewService.triageReview(15L, reopenReq);

        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.UNDER_REVIEW);
        assertThat(response.getResolvedAt()).isNull();
        assertThat(response.getResolvedBy()).isNull();
        assertThat(response.getAdminNotes()).isEqualTo("Guest appealed, reopening case");
    }

    @Test
    @DisplayName("triageReview throws ResourceNotFoundException when review does not exist")
    void testTriageReviewNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        TriageReviewRequest req = TriageReviewRequest.builder().status(FeedbackStatus.RESOLVED).build();
        assertThatThrownBy(() -> reviewService.triageReview(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getFeedbackAnalytics aggregates review counts, CSAT average, and distribution")
    void testGetFeedbackAnalytics() {
        PassengerServiceReview r1 = PassengerServiceReview.builder().rating(5).category(FeedbackCategory.CABIN_SERVICE).sentiment("POSITIVE").status(FeedbackStatus.NEW).isLowRatingAlert(false).build();
        PassengerServiceReview r2 = PassengerServiceReview.builder().rating(1).category(FeedbackCategory.CABIN_SERVICE).sentiment("NEGATIVE").status(FeedbackStatus.ESCALATED).isLowRatingAlert(true).build();

        when(reviewRepository.findAll()).thenReturn(List.of(r1, r2));

        FeedbackAnalyticsDto analytics = reviewService.getFeedbackAnalytics();
        assertThat(analytics.getTotalReviews()).isEqualTo(2L);
        assertThat(analytics.getAverageRating()).isEqualTo(3.0);
        assertThat(analytics.getLowRatingAlertCount()).isEqualTo(1L);
        assertThat(analytics.getEscalatedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getFeedbackAnalytics returns empty metrics when no reviews exist")
    void testGetFeedbackAnalyticsEmpty() {
        when(reviewRepository.findAll()).thenReturn(List.of());

        FeedbackAnalyticsDto analytics = reviewService.getFeedbackAnalytics();
        assertThat(analytics.getTotalReviews()).isEqualTo(0L);
        assertThat(analytics.getAverageRating()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("searchReviews delegates to repository")
    void testSearchReviews() {
        PassengerServiceReview review = PassengerServiceReview.builder().passengerId(100L).rating(4).build();
        review.setId(1L);
        PageRequest pageable = PageRequest.of(0, 10);
        when(reviewRepository.searchReviews(eq(FeedbackStatus.NEW), eq(FeedbackCategory.ENTERTAINMENT_ACTIVITY), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(review)));

        Page<PassengerServiceReviewResponse> page = reviewService.searchReviews(FeedbackStatus.NEW, FeedbackCategory.ENTERTAINMENT_ACTIVITY, false, pageable);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
