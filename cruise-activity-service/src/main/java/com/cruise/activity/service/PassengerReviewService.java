package com.cruise.activity.service;

import com.cruise.activity.dto.PassengerServiceReviewResponse;
import com.cruise.activity.dto.SubmitPassengerReviewRequest;
import com.cruise.activity.entity.PassengerServiceReview;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.repository.PassengerServiceReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerReviewService {

    private static final int MAX_SUBMISSIONS_PER_HOUR = 5;
    private final PassengerServiceReviewRepository reviewRepository;

    public String computeSentiment(int rating) {
        if (rating >= 4) return "POSITIVE";
        if (rating == 3) return "NEUTRAL";
        return "NEGATIVE";
    }

    @Transactional
    public PassengerServiceReviewResponse submitReview(SubmitPassengerReviewRequest request) {
        Objects.requireNonNull(request, "SubmitPassengerReviewRequest must not be null");

        // Anti-spam validation: no more than 5 reviews submitted in past hour by same passenger
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = reviewRepository.countRecentSubmissionsByPassenger(request.getPassengerId(), oneHourAgo);
        if (recentCount >= MAX_SUBMISSIONS_PER_HOUR) {
            log.warn("Anti-spam triggered: passenger {} exceeded hourly review threshold", request.getPassengerId());
            throw new BusinessRuleException("Quá nhiều lượt gửi đánh giá trong thời gian ngắn. Vui lòng thử lại sau.");
        }

        String sentiment = computeSentiment(request.getRating());
        boolean isLowRating = request.getRating() <= 2;
        String escalationReason = isLowRating ? "Low CSAT Score (" + request.getRating() + "/5) requiring Guest Relations Attention" : null;
        FeedbackStatus initialStatus = isLowRating ? FeedbackStatus.ESCALATED : FeedbackStatus.NEW;

        PassengerServiceReview review = PassengerServiceReview.builder()
                .passengerId(request.getPassengerId())
                .passengerName(request.getPassengerName())
                .cabinNumber(request.getCabinNumber())
                .category(request.getCategory())
                .rating(request.getRating())
                .sentiment(sentiment)
                .comments(request.getComments())
                .serviceOutlet(request.getServiceOutlet())
                .status(initialStatus)
                .isLowRatingAlert(isLowRating)
                .escalationReason(escalationReason)
                .build();

        reviewRepository.save(review);
        log.info("Saved review id: {} for passenger: {} with rating: {} (Low rating alert: {})",
                review.getId(), request.getPassengerId(), request.getRating(), isLowRating);

        return mapToResponse(review);
    }

    public PassengerServiceReviewResponse mapToResponse(PassengerServiceReview review) {
        return PassengerServiceReviewResponse.builder()
                .id(review.getId())
                .passengerId(review.getPassengerId())
                .passengerName(review.getPassengerName())
                .cabinNumber(review.getCabinNumber())
                .category(review.getCategory())
                .rating(review.getRating())
                .sentiment(review.getSentiment())
                .comments(review.getComments())
                .serviceOutlet(review.getServiceOutlet())
                .status(review.getStatus())
                .isLowRatingAlert(review.getIsLowRatingAlert())
                .escalationReason(review.getEscalationReason())
                .adminNotes(review.getAdminNotes())
                .resolvedBy(review.getResolvedBy())
                .resolvedAt(review.getResolvedAt())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
