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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerReviewService {

    private static final int MAX_SUBMISSIONS_PER_HOUR = 5;
    private final PassengerServiceReviewRepository reviewRepository;
    private final PassengerRepository passengerRepository;

    public String computeSentiment(int rating) {
        if (rating >= 4) return "POSITIVE";
        if (rating == 3) return "NEUTRAL";
        return "NEGATIVE";
    }

    @Transactional
    public PassengerServiceReviewResponse submitReview(SubmitPassengerReviewRequest request) {
        Objects.requireNonNull(request, "SubmitPassengerReviewRequest must not be null");

        // Serialize review submissions per passenger
        if (request.getPassengerId() != null) {
            passengerRepository.findByIdForUpdate(request.getPassengerId());
        }

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
    public PassengerServiceReviewResponse triageReview(Long reviewId, TriageReviewRequest request) {
        PassengerServiceReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        review.setStatus(request.getStatus());
        if (request.getAdminNotes() != null) {
            review.setAdminNotes(request.getAdminNotes());
        }
        if (request.getResolvedBy() != null) {
            review.setResolvedBy(request.getResolvedBy());
        }

        boolean closed = request.getStatus() == FeedbackStatus.RESOLVED || request.getStatus() == FeedbackStatus.DISMISSED;
        if (closed) {
            if (review.getResolvedAt() == null) {
                review.setResolvedAt(Instant.now());
            }
        } else {
            review.setResolvedAt(null);
            review.setResolvedBy(null);
        }

        reviewRepository.save(review);
        log.info("Triaged review id: {} to status: {} by: {}", reviewId, request.getStatus(), request.getResolvedBy());
        return mapToResponse(review);
    }

    @Transactional(readOnly = true)
    public FeedbackAnalyticsDto getFeedbackAnalytics() {
        var all = reviewRepository.findAll();
        long total = all.size();
        if (total == 0) {
            return FeedbackAnalyticsDto.builder()
                    .totalReviews(0L)
                    .averageRating(0.0)
                    .lowRatingAlertCount(0L)
                    .escalatedCount(0L)
                    .resolvedCount(0L)
                    .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                    .categoryAverages(Collections.emptyMap())
                    .build();
        }

        double avgRating = all.stream().mapToInt(PassengerServiceReview::getRating).average().orElse(0.0);
        long lowAlerts = all.stream().filter(r -> Boolean.TRUE.equals(r.getIsLowRatingAlert())).count();
        long escalated = all.stream().filter(r -> r.getStatus() == FeedbackStatus.ESCALATED).count();
        long resolved = all.stream().filter(r -> r.getStatus() == FeedbackStatus.RESOLVED).count();

        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        for (PassengerServiceReview r : all) {
            dist.put(r.getRating(), dist.getOrDefault(r.getRating(), 0L) + 1);
        }

        Map<FeedbackCategory, Double> catAvg = all.stream()
                .collect(Collectors.groupingBy(
                        PassengerServiceReview::getCategory,
                        Collectors.averagingInt(PassengerServiceReview::getRating)
                ));

        return FeedbackAnalyticsDto.builder()
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
    public Page<PassengerServiceReviewResponse> searchReviews(
            FeedbackStatus status,
            FeedbackCategory category,
            Boolean isAlert,
            Pageable pageable
    ) {
        return reviewRepository.searchReviews(status, category, isAlert, pageable)
                .map(this::mapToResponse);
    }

    private PassengerServiceReviewResponse mapToResponse(PassengerServiceReview r) {
        return PassengerServiceReviewResponse.builder()
                .id(r.getId())
                .passengerId(r.getPassengerId())
                .passengerName(r.getPassengerName())
                .cabinNumber(r.getCabinNumber())
                .category(r.getCategory())
                .rating(r.getRating())
                .sentiment(r.getSentiment())
                .comments(r.getComments())
                .serviceOutlet(r.getServiceOutlet())
                .status(r.getStatus())
                .isLowRatingAlert(r.getIsLowRatingAlert())
                .escalationReason(r.getEscalationReason())
                .adminNotes(r.getAdminNotes())
                .resolvedAt(r.getResolvedAt())
                .resolvedBy(r.getResolvedBy())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
