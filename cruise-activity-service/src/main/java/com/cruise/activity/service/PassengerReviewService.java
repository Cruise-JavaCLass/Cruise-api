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

    @Transactional
    public PassengerServiceReviewResponse triageReview(Long reviewId, com.cruise.activity.dto.TriageReviewRequest request) {
        PassengerServiceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new com.cruise.activity.exception.ResourceNotFoundException("Review not found with id: " + reviewId));

        review.setStatus(request.getStatus());
        if (request.getAdminNotes() != null) {
            review.setAdminNotes(request.getAdminNotes());
        }
        if (request.getResolvedBy() != null) {
            review.setResolvedBy(request.getResolvedBy());
        }
        if (request.getStatus() == FeedbackStatus.RESOLVED || request.getStatus() == FeedbackStatus.DISMISSED) {
            review.setResolvedAt(Instant.now());
        }

        reviewRepository.save(review);
        log.info("Triaged review id: {} to status: {} by: {}", reviewId, request.getStatus(), request.getResolvedBy());
        return mapToResponse(review);
    }

    @Transactional(readOnly = true)
    public com.cruise.activity.dto.FeedbackAnalyticsDto getFeedbackAnalytics() {
        var all = reviewRepository.findAll();
        long total = all.size();
        if (total == 0) {
            return com.cruise.activity.dto.FeedbackAnalyticsDto.builder()
                    .totalReviews(0L)
                    .averageRating(0.0)
                    .lowRatingAlertCount(0L)
                    .escalatedCount(0L)
                    .resolvedCount(0L)
                    .ratingDistribution(java.util.Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                    .categoryAverages(java.util.Collections.emptyMap())
                    .build();
        }

        double avgRating = all.stream().mapToInt(PassengerServiceReview::getRating).average().orElse(0.0);
        long lowAlerts = all.stream().filter(r -> Boolean.TRUE.equals(r.getIsLowRatingAlert())).count();
        long escalated = all.stream().filter(r -> r.getStatus() == FeedbackStatus.ESCALATED).count();
        long resolved = all.stream().filter(r -> r.getStatus() == FeedbackStatus.RESOLVED).count();

        java.util.Map<Integer, Long> dist = new java.util.HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        for (PassengerServiceReview r : all) {
            dist.put(r.getRating(), dist.getOrDefault(r.getRating(), 0L) + 1);
        }

        java.util.Map<com.cruise.activity.enums.FeedbackCategory, Double> catAvg = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PassengerServiceReview::getCategory,
                        java.util.stream.Collectors.averagingInt(PassengerServiceReview::getRating)
                ));

        return com.cruise.activity.dto.FeedbackAnalyticsDto.builder()
                .totalReviews(total)
                .averageRating(Math.round(avgRating * 10.0) / 10.0)
                .lowRatingAlertCount(lowAlerts)
                .escalatedCount(escalated)
                .resolvedCount(resolved)
                .ratingDistribution(dist)
                .categoryAverages(catAvg)
                .build();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PassengerServiceReviewResponse> searchReviews(
            com.cruise.activity.enums.FeedbackStatus status,
            com.cruise.activity.enums.FeedbackCategory category,
            Boolean isAlert,
            org.springframework.data.domain.Pageable pageable
    ) {
        return reviewRepository.searchReviews(status, category, isAlert, pageable)
                .map(this::mapToResponse);
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
