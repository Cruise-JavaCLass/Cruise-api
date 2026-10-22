package com.cruise.activity.service;

import com.cruise.activity.dto.FeedbackAnalyticsDto;
import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.dto.TriageReviewRequest;
import com.cruise.activity.entity.PassengerServiceReview;
import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.repository.PassengerServiceReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerReviewServiceTest {

    @Mock
    private PassengerServiceReviewRepository reviewRepository;

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
    @DisplayName("submitReview successfully persists review and assigns POSITIVE sentiment")
    void testSubmitReviewSuccess() {
        when(reviewRepository.countRecentSubmissionsByPassenger(eq(101L), any())).thenReturn(0L);

        PassengerServiceReviewResponse response = reviewService.submitReview(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getPassengerId()).isEqualTo(101L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getSentiment()).isEqualTo("POSITIVE");
        assertThat(response.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(response.getIsLowRatingAlert()).isFalse();

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
}
