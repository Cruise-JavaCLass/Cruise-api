package com.cruise.activity.service;

import com.cruise.activity.dto.ActivityCsatSummaryDto;
import com.cruise.activity.dto.ActivityFeedbackResponse;
import com.cruise.activity.dto.SubmitFeedbackRequest;
import com.cruise.activity.entity.ActivityFeedback;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityFeedbackRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityFeedbackService {

    private final ActivityFeedbackRepository feedbackRepository;
    private final OnboardActivityRepository activityRepository;
    private final PassengerRepository passengerRepository;

    public ActivityFeedbackResponse submitFeedback(Long activityId, SubmitFeedbackRequest request) {
        // Idempotency check 1: By explicit idempotencyKey
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<ActivityFeedback> existing = feedbackRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Feedback already submitted with idempotencyKey: {}", request.getIdempotencyKey());
                return mapToResponse(existing.get());
            }
        }

        // Idempotency check 2: Check if passenger already rated this activity
        Optional<ActivityFeedback> existingRating = feedbackRepository.findByActivityIdAndPassengerId(
                activityId, request.getPassengerId());
        if (existingRating.isPresent()) {
            log.info("Passenger {} already submitted feedback for activity {}", request.getPassengerId(), activityId);
            return mapToResponse(existingRating.get());
        }

        OnboardActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found with id: " + activityId));

        if (!passengerRepository.existsById(request.getPassengerId())) {
            throw new ResourceNotFoundException("Passenger not found with id: " + request.getPassengerId());
        }

        // Acceptance Criteria 3 & 4: Only allow feedback if activity is COMPLETED (or completed workflow)
        if (activity.getStatus() != ActivityStatus.COMPLETED) {
            throw new BusinessRuleException("Chỉ có thể gửi đánh giá cho hoạt động đã hoàn thành (COMPLETED)");
        }

        // Acceptance Criteria 5: Low-rating escalation flag (rating <= 2)
        boolean isEscalated = request.getRating() <= 2;
        String escalationReason = null;
        if (isEscalated) {
            escalationReason = "Đánh giá thấp (" + request.getRating() + " sao) cần chuyển Trưởng bộ phận CSKH xử lý";
            log.warn("Escalated low-rating feedback: activityId={}, passengerId={}, rating={}",
                    activityId, request.getPassengerId(), request.getRating());
        }

        ActivityFeedback feedback = ActivityFeedback.builder()
                .activityId(activityId)
                .passengerId(request.getPassengerId())
                .rating(request.getRating())
                .organizationRating(request.getOrganizationRating())
                .guideRating(request.getGuideRating())
                .valueRating(request.getValueRating())
                .comments(request.getComments())
                .escalated(isEscalated)
                .escalationReason(escalationReason)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        try {
            ActivityFeedback saved = feedbackRepository.saveAndFlush(feedback);
            log.info("Feedback saved: id={}, activityId={}, passengerId={}, rating={}, escalated={}",
                    saved.getId(), activityId, request.getPassengerId(), request.getRating(), isEscalated);
            return mapToResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Concurrent submission race condition handling outside failed transaction
            return feedbackRepository.findByActivityIdAndPassengerId(activityId, request.getPassengerId())
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new BusinessRuleException("Lỗi lưu trữ đánh giá trùng lặp"));
        }
    }

    @Transactional(readOnly = true)
    public List<ActivityFeedbackResponse> getFeedbacksByActivity(Long activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity not found with id: " + activityId);
        }
        return feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ActivityCsatSummaryDto getCsatSummary(Long activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity not found with id: " + activityId);
        }

        List<ActivityFeedback> list = feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        long totalReviews = list.size();
        long escalatedCount = list.stream().filter(f -> Boolean.TRUE.equals(f.getEscalated())).count();

        double avgRating = totalReviews > 0
                ? list.stream().mapToInt(ActivityFeedback::getRating).average().orElse(0.0)
                : 0.0;

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int star = i;
            distribution.put(star, list.stream().filter(f -> f.getRating() == star).count());
        }

        return ActivityCsatSummaryDto.builder()
                .activityId(activityId)
                .averageRating(Math.round(avgRating * 100.0) / 100.0)
                .totalReviews(totalReviews)
                .escalatedCount(escalatedCount)
                .ratingDistribution(distribution)
                .build();
    }

    private ActivityFeedbackResponse mapToResponse(ActivityFeedback f) {
        return ActivityFeedbackResponse.builder()
                .id(f.getId())
                .activityId(f.getActivityId())
                .passengerId(f.getPassengerId())
                .rating(f.getRating())
                .organizationRating(f.getOrganizationRating())
                .guideRating(f.getGuideRating())
                .valueRating(f.getValueRating())
                .comments(f.getComments())
                .escalated(f.getEscalated())
                .escalationReason(f.getEscalationReason())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
