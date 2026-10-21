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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityFeedbackServiceTest {

    @Mock
    private ActivityFeedbackRepository feedbackRepository;

    @Mock
    private OnboardActivityRepository activityRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private ActivityFeedbackService feedbackService;

    private Long activityId;
    private Long passengerId;
    private OnboardActivity completedActivity;
    private OnboardActivity scheduledActivity;

    @BeforeEach
    void setUp() {
        activityId = 10L;
        passengerId = 50L;

        completedActivity = OnboardActivity.builder()
                .title("Yoga Bình Minh")
                .status(ActivityStatus.COMPLETED)
                .build();
        completedActivity.setId(activityId);

        scheduledActivity = OnboardActivity.builder()
                .title("Cooking Class")
                .status(ActivityStatus.SCHEDULED)
                .build();
        scheduledActivity.setId(11L);
    }

    @Test
    @DisplayName("submitFeedback: Gửi phản hồi thành công và không bị escalated khi rating >= 3")
    void testSubmitFeedback_SuccessHighRating() {
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .passengerId(passengerId)
                .rating(5)
                .organizationRating(5)
                .guideRating(5)
                .valueRating(4)
                .comments("Hoạt động rất tuyệt vời!")
                .build();

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(completedActivity));
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(feedbackRepository.findByActivityIdAndPassengerId(activityId, passengerId)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(ActivityFeedback.class))).thenAnswer(invocation -> {
            ActivityFeedback fb = invocation.getArgument(0);
            fb.setId(101L);
            fb.setCreatedAt(Instant.now());
            return fb;
        });

        ActivityFeedbackResponse response = feedbackService.submitFeedback(activityId, request);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getEscalated()).isFalse();
        assertThat(response.getEscalationReason()).isNull();
    }

    @Test
    @DisplayName("submitFeedback: Tự động gắn cờ escalated = true khi rating <= 2 (Low-rating escalation)")
    void testSubmitFeedback_EscalatedLowRating() {
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .passengerId(passengerId)
                .rating(2)
                .comments("Hướng dẫn viên đến trễ và chất lượng dịch vụ không đạt")
                .build();

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(completedActivity));
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(feedbackRepository.findByActivityIdAndPassengerId(activityId, passengerId)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(ActivityFeedback.class))).thenAnswer(invocation -> {
            ActivityFeedback fb = invocation.getArgument(0);
            fb.setId(102L);
            fb.setCreatedAt(Instant.now());
            return fb;
        });

        ActivityFeedbackResponse response = feedbackService.submitFeedback(activityId, request);

        assertThat(response.getRating()).isEqualTo(2);
        assertThat(response.getEscalated()).isTrue();
        assertThat(response.getEscalationReason()).contains("cần chuyển Trưởng bộ phận CSKH xử lý");
    }

    @Test
    @DisplayName("submitFeedback: Từ chối đánh giá khi hoạt động chưa hoàn thành (chưa COMPLETED)")
    void testSubmitFeedback_ActivityNotCompleted() {
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .passengerId(passengerId)
                .rating(4)
                .build();

        when(activityRepository.findById(11L)).thenReturn(Optional.of(scheduledActivity));
        when(passengerRepository.existsById(passengerId)).thenReturn(true);

        assertThatThrownBy(() -> feedbackService.submitFeedback(11L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("submitFeedback: Idempotency - Trả về phản hồi đã lưu nếu trùng lặp")
    void testSubmitFeedback_IdempotentDuplicate() {
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .passengerId(passengerId)
                .rating(5)
                .idempotencyKey("KEY-12345")
                .build();

        ActivityFeedback existing = ActivityFeedback.builder()
                .activityId(activityId)
                .passengerId(passengerId)
                .rating(5)
                .idempotencyKey("KEY-12345")
                .build();
        existing.setId(999L);
        existing.setCreatedAt(Instant.now());

        when(feedbackRepository.findByIdempotencyKey("KEY-12345")).thenReturn(Optional.of(existing));

        ActivityFeedbackResponse response = feedbackService.submitFeedback(activityId, request);

        assertThat(response.getId()).isEqualTo(999L);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCsatSummary: Thống kê chính xác điểm trung bình và phân phối sao")
    void testGetCsatSummary() {
        ActivityFeedback fb1 = ActivityFeedback.builder().activityId(activityId).rating(5).escalated(false).build();
        ActivityFeedback fb2 = ActivityFeedback.builder().activityId(activityId).rating(1).escalated(true).build();

        when(activityRepository.existsById(activityId)).thenReturn(true);
        when(feedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId)).thenReturn(List.of(fb1, fb2));

        ActivityCsatSummaryDto summary = feedbackService.getCsatSummary(activityId);

        assertThat(summary.getTotalReviews()).isEqualTo(2);
        assertThat(summary.getAverageRating()).isEqualTo(3.0);
        assertThat(summary.getEscalatedCount()).isEqualTo(1);
        assertThat(summary.getRatingDistribution()).containsEntry(5, 1L);
        assertThat(summary.getRatingDistribution()).containsEntry(1, 1L);
    }
}
