package com.cruise.activity.service;

import com.cruise.activity.dto.ExcursionReviewRequest;
import com.cruise.activity.dto.ExcursionReviewResponse;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ExcursionReviewRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcursionReviewService {

    private static final Set<ExcursionPassengerStatus> ATTENDED_STATUSES = Set.of(
            ExcursionPassengerStatus.CHECKED_IN,
            ExcursionPassengerStatus.ON_TOUR,
            ExcursionPassengerStatus.RETURNED_SAFE
    );

    private final ExcursionReviewRepository reviewRepository;
    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository passengerRepository;

    @Transactional
    public ExcursionReviewResponse submitReview(Long excursionId, ExcursionReviewRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "ExcursionReviewRequest must not be null");

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existingOpt = reviewRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                ExcursionReview existing = existingOpt.get();
                if (Objects.equals(existing.getExcursionId(), excursionId) &&
                        Objects.equals(existing.getPassengerId(), request.passengerId())) {
                    log.info("Idempotent hit for excursion review: key={}", idempotencyKey);
                    return mapToResponse(existing);
                } else {
                    log.warn("Idempotency key collision with different excursion/passenger: key={}", idempotencyKey);
                    throw new DuplicateRegistrationException("Idempotency key đã được sử dụng cho một yêu cầu khác");
                }
            }
        }

        ShoreExcursion excursion = excursionRepository.findById(excursionId)
                .orElseThrow(() -> new ResourceNotFoundException("Shore Excursion không tồn tại với ID: " + excursionId));

        validateAttendanceGuard(excursionId, request.passengerId());

        if (reviewRepository.existsByExcursionIdAndPassengerId(excursionId, request.passengerId())) {
            throw new DuplicateRegistrationException("Hành khách đã gửi đánh giá cho tour này trước đó");
        }

        boolean isNegativeOrSafetyConcern = evaluateEscalation(request);
        String escalationReason = isNegativeOrSafetyConcern ? determineEscalationReason(request) : null;

        ExcursionReview review = ExcursionReview.builder()
                .excursionId(excursion.getId())
                .passengerId(request.passengerId())
                .ratingOverall(request.ratingOverall())
                .ratingGuide(request.ratingGuide())
                .ratingBus(request.ratingBus())
                .ratingValue(request.ratingValue())
                .comments(request.comments())
                .isFlagged(isNegativeOrSafetyConcern)
                .flagReason(escalationReason)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            ExcursionReview saved = reviewRepository.save(review);
            log.info("Excursion review submitted: excursionId={}, passengerId={}, overallRating={}, flagged={}",
                    excursionId, request.passengerId(), request.ratingOverall(), isNegativeOrSafetyConcern);
            return mapToResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate submission or constraint violation detected for excursionId={}, passengerId={}",
                    excursionId, request.passengerId());
            return reviewRepository.findByExcursionIdAndPassengerId(excursionId, request.passengerId())
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new DuplicateRegistrationException("Lỗi lưu trữ đánh giá trùng lặp hoặc xung đột khóa"));
        }
    }

    @Transactional(readOnly = true)
    public Page<ExcursionReviewResponse> getReviewsByExcursion(Long excursionId, Pageable pageable) {
        if (!excursionRepository.existsById(excursionId)) {
            throw new ResourceNotFoundException("Shore Excursion không tồn tại với ID: " + excursionId);
        }
        return reviewRepository.findByExcursionId(excursionId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ExcursionReviewResponse> getFlaggedReviews() {
        return reviewRepository.findByIsFlaggedTrue().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void validateAttendanceGuard(Long excursionId, Long passengerId) {
        List<ExcursionPassenger> bookings = passengerRepository.findByExcursionIdAndActiveTrue(excursionId);
        boolean hasAttended = bookings.stream()
                .filter(p -> Objects.equals(p.getPassengerId(), passengerId))
                .anyMatch(p -> ATTENDED_STATUSES.contains(p.getStatus()));

        if (!hasAttended) {
            log.warn("Attendance guard triggered: passengerId={} has not attended excursionId={}", passengerId, excursionId);
            throw new AccessDeniedException("Chỉ hành khách đã tham gia điểm danh tour (ATTENDED) mới được gửi đánh giá");
        }
    }

    private boolean evaluateEscalation(ExcursionReviewRequest request) {
        if (request.ratingOverall() != null && request.ratingOverall() <= 1) {
            return true;
        }
        if (request.comments() != null) {
            String lower = request.comments().toLowerCase();
            return lower.contains("danger") || lower.contains("unsafe") || lower.contains("accident") ||
                    lower.contains("nguy hiểm") || lower.contains("tai nạn") || lower.contains("khẩn cấp") ||
                    lower.contains("mất an toàn");
        }
        return false;
    }

    private String determineEscalationReason(ExcursionReviewRequest request) {
        if (request.ratingOverall() != null && request.ratingOverall() <= 1) {
            return "Đánh giá tiêu cực mức độ cao (1 sao)";
        }
        return "Nội dung phản ánh có yếu tố cảnh báo an toàn khẩn cấp";
    }

    private ExcursionReviewResponse mapToResponse(ExcursionReview review) {
        return new ExcursionReviewResponse(
                review.getId(),
                review.getExcursionId(),
                review.getPassengerId(),
                review.getRatingOverall(),
                review.getRatingGuide(),
                review.getRatingBus(),
                review.getRatingValue(),
                review.getComments(),
                review.getIsFlagged(),
                review.getFlagReason(),
                review.getCreatedAt() != null ? review.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null
        );
    }
}
