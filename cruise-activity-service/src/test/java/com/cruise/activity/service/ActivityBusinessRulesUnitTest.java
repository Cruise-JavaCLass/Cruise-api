package com.cruise.activity.service;

import com.cruise.activity.dto.ConfigureTimeAndLocationRequest;
import com.cruise.activity.dto.CreateActivityRequest;
import com.cruise.activity.dto.UpdateActivityRequest;
import com.cruise.activity.dto.UpdateActivityStatusRequest;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.OnboardActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Business Rules Unit Tests (KAN-137)")
class ActivityBusinessRulesUnitTest {

    @Mock
    private OnboardActivityRepository activityRepository;

    @InjectMocks
    private OnboardActivityService activityService;

    private OnboardActivity existingActivity;

    @BeforeEach
    void setUp() {
        existingActivity = OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(1)
                .title("Gala Dinner & Live Acoustic")
                .description("Buổi tiệc tối giao lưu âm nhạc ấm cúng")
                .category("Entertainment")
                .location("Starlight Lounge")
                .activityDate(LocalDate.of(2026, 11, 5))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 30))
                .maxCapacity(120)
                .currentParticipants(10)
                .price(BigDecimal.valueOf(250000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();
        existingActivity.setId(100L);
    }

    @Nested
    @DisplayName("1. Quy tắc tính hợp lệ của Địa điểm (Location Validation)")
    class LocationValidationRules {

        @ParameterizedTest(name = "Địa điểm không hợp lệ: ''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Ném BusinessRuleException khi địa điểm bị bỏ trống hoặc chỉ chứa khoảng trắng")
        void shouldRejectBlankOrNullLocation(String invalidLocation) {
            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    invalidLocation,
                    LocalDate.of(2026, 11, 5),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Địa điểm tổ chức không được để trống");

            verifyNoInteractions(activityRepository);
        }

        @Test
        @DisplayName("Địa điểm hợp lệ được chuẩn hóa trim khoảng trắng trước khi kiểm tra trùng lặp")
        void shouldTrimLocationBeforeCheckingConflict() {
            String paddedLocation = "   Oceanview Deck 10   ";
            when(activityRepository.existsOverlappingActivity(
                    eq("Oceanview Deck 10"),
                    eq(LocalDate.of(2026, 11, 5)),
                    eq(LocalTime.of(8, 0)),
                    eq(LocalTime.of(9, 30)),
                    isNull()
            )).thenReturn(false);

            activityService.validateTimeAndLocation(
                    paddedLocation,
                    LocalDate.of(2026, 11, 5),
                    LocalTime.of(8, 0),
                    LocalTime.of(9, 30),
                    null
            );

            verify(activityRepository).existsOverlappingActivity(
                    eq("Oceanview Deck 10"),
                    eq(LocalDate.of(2026, 11, 5)),
                    eq(LocalTime.of(8, 0)),
                    eq(LocalTime.of(9, 30)),
                    isNull()
            );
        }
    }

    @Nested
    @DisplayName("2. Quy tắc Khung thời gian & Thứ tự giờ (Time & Date Validation)")
    class TimeAndDateValidationRules {

        @Test
        @DisplayName("Ném BusinessRuleException khi ngày diễn ra bị null")
        void shouldRejectNullDate() {
            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    "Grand Ballroom",
                    null,
                    LocalTime.of(14, 0),
                    LocalTime.of(16, 0),
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Ngày tổ chức không được để trống");
        }

        @Test
        @DisplayName("Ném BusinessRuleException khi giờ bắt đầu bị null")
        void shouldRejectNullStartTime() {
            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    "Grand Ballroom",
                    LocalDate.of(2026, 11, 5),
                    null,
                    LocalTime.of(16, 0),
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Thời gian bắt đầu và kết thúc không được để trống");
        }

        @Test
        @DisplayName("Ném BusinessRuleException khi giờ kết thúc bị null")
        void shouldRejectNullEndTime() {
            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    "Grand Ballroom",
                    LocalDate.of(2026, 11, 5),
                    LocalTime.of(14, 0),
                    null,
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Thời gian bắt đầu và kết thúc không được để trống");
        }

        @Test
        @DisplayName("Ném BusinessRuleException khi giờ kết thúc trùng với giờ bắt đầu (thời lượng bằng 0)")
        void shouldRejectSameStartAndEndTime() {
            LocalTime exactTime = LocalTime.of(15, 30);
            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    "Spa Sanctuary",
                    LocalDate.of(2026, 11, 5),
                    exactTime,
                    exactTime,
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        @Test
        @DisplayName("Ném BusinessRuleException khi giờ kết thúc xảy ra trước giờ bắt đầu")
        void shouldRejectEndTimeBeforeStartTime() {
            LocalTime start = LocalTime.of(16, 0);
            LocalTime end = LocalTime.of(15, 0);

            assertThatThrownBy(() -> activityService.validateTimeAndLocation(
                    "Fitness Center",
                    LocalDate.of(2026, 11, 5),
                    start,
                    end,
                    null
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }

    @Nested
    @DisplayName("3. Quy tắc Xung đột Lịch trình & Trùng lặp Địa điểm (Schedule Conflict & Overlap Detection)")
    class ScheduleConflictRules {

        @Test
        @DisplayName("Ném BusinessRuleException khi phát hiện hoạt động khác đã chiếm chỗ tại cùng địa điểm và thời gian")
        void shouldRejectWhenOverlappingActivityExists() {
            String venue = "Starlight Lounge";
            LocalDate date = LocalDate.of(2026, 11, 5);
            LocalTime start = LocalTime.of(18, 30);
            LocalTime end = LocalTime.of(20, 0);

            when(activityRepository.existsOverlappingActivity(eq(venue), eq(date), eq(start), eq(end), isNull()))
                    .thenReturn(true);

            assertThatThrownBy(() -> activityService.validateTimeAndLocation(venue, date, start, end, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Xung đột lịch trình")
                    .hasMessageContaining(venue)
                    .hasMessageContaining("18:30 - 20:00");
        }

        @Test
        @DisplayName("Chấp nhận khi không có xung đột lịch trình tại địa điểm")
        void shouldPassWhenNoOverlapExists() {
            String venue = "Main Pool Bar";
            LocalDate date = LocalDate.of(2026, 11, 6);
            LocalTime start = LocalTime.of(14, 0);
            LocalTime end = LocalTime.of(16, 0);

            when(activityRepository.existsOverlappingActivity(eq(venue), eq(date), eq(start), eq(end), eq(100L)))
                    .thenReturn(false);

            activityService.validateTimeAndLocation(venue, date, start, end, 100L);

            verify(activityRepository).existsOverlappingActivity(eq(venue), eq(date), eq(start), eq(end), eq(100L));
        }

        @Test
        @DisplayName("Cập nhật hoạt động cho phép loại trừ chính ID của nó (excludeId) khi đối soát trùng lặp")
        void shouldExcludeCurrentActivityIdWhenUpdating() {
            UpdateActivityRequest req = UpdateActivityRequest.builder()
                    .title("Gala Dinner & Acoustic (Updated)")
                    .location("Starlight Lounge")
                    .activityDate(LocalDate.of(2026, 11, 5))
                    .startTime(LocalTime.of(19, 0))
                    .endTime(LocalTime.of(22, 0))
                    .build();

            when(activityRepository.findById(100L)).thenReturn(Optional.of(existingActivity));
            when(activityRepository.existsOverlappingActivity(
                    eq("Starlight Lounge"),
                    eq(LocalDate.of(2026, 11, 5)),
                    eq(LocalTime.of(19, 0)),
                    eq(LocalTime.of(22, 0)),
                    eq(100L)
            )).thenReturn(false);
            when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

            activityService.updateActivity(100L, req);

            verify(activityRepository).existsOverlappingActivity(
                    eq("Starlight Lounge"),
                    eq(LocalDate.of(2026, 11, 5)),
                    eq(LocalTime.of(19, 0)),
                    eq(LocalTime.of(22, 0)),
                    eq(100L)
            );
        }
    }

    @Nested
    @DisplayName("4. Quy tắc Ma trận Trạng thái Hoạt động (State Machine Transition Rules)")
    class StateMachineBusinessRules {

        @Test
        @DisplayName("SCHEDULED cho phép chuyển sang IN_PROGRESS và CANCELLED")
        void scheduledAllowedTransitions() {
            assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isTrue();
            assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.CANCELLED)).isTrue();
            assertThat(ActivityStatus.SCHEDULED.canTransitionTo(ActivityStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("IN_PROGRESS cho phép chuyển sang COMPLETED và CANCELLED")
        void inProgressAllowedTransitions() {
            assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.COMPLETED)).isTrue();
            assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.CANCELLED)).isTrue();
            assertThat(ActivityStatus.IN_PROGRESS.canTransitionTo(ActivityStatus.SCHEDULED)).isFalse();
        }

        @Test
        @DisplayName("COMPLETED là trạng thái kết thúc, không được phép chuyển sang bất kỳ trạng thái nào")
        void completedIsTerminalState() {
            assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.SCHEDULED)).isFalse();
            assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isFalse();
            assertThat(ActivityStatus.COMPLETED.canTransitionTo(ActivityStatus.CANCELLED)).isFalse();
            assertThat(ActivityStatus.COMPLETED.getAllowedTransitions()).isEmpty();
        }

        @Test
        @DisplayName("CANCELLED cho phép khôi phục lại lịch SCHEDULED nhưng không được nhảy cóc sang IN_PROGRESS hoặc COMPLETED")
        void cancelledTransitions() {
            assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.SCHEDULED)).isTrue();
            assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.IN_PROGRESS)).isFalse();
            assertThat(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("Từ chối vi phạm ma trận chuyển trạng thái trong Service và ném BusinessRuleException")
        void shouldRejectInvalidStateTransitionInService() {
            UpdateActivityStatusRequest req = UpdateActivityStatusRequest.builder()
                    .status(ActivityStatus.COMPLETED)
                    .build();

            when(activityRepository.findById(100L)).thenReturn(Optional.of(existingActivity));

            assertThatThrownBy(() -> activityService.updateActivityStatus(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái hoạt động từ 'SCHEDULED' sang 'COMPLETED'");

            verify(activityRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lưu trữ cancellationReason khi hủy hoạt động (CANCELLED)")
        void shouldRecordCancellationReasonWhenCancelled() {
            UpdateActivityStatusRequest req = UpdateActivityStatusRequest.builder()
                    .status(ActivityStatus.CANCELLED)
                    .cancellationReason("Thời tiết bão to tại Vịnh Hạ Long")
                    .build();

            when(activityRepository.findById(100L)).thenReturn(Optional.of(existingActivity));
            when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

            var res = activityService.updateActivityStatus(100L, req);

            assertThat(res.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
            assertThat(res.getCancellationReason()).isEqualTo("Thời tiết bão to tại Vịnh Hạ Long");
        }
    }

    @Nested
    @DisplayName("5. Quy tắc Giá trị Mặc định & Toàn vẹn Dữ liệu (Defaults & Integrity)")
    class DefaultValuesAndIntegrityRules {

        @Test
        @DisplayName("createActivity gán giá trị mặc định cho category, maxCapacity, price, isIncluded, status, active")
        void shouldApplyBusinessDefaultsWhenFieldsOmitted() {
            CreateActivityRequest req = CreateActivityRequest.builder()
                    .tourId(1L)
                    .title("Bình minh Yoga")
                    .location("Helipad Deck")
                    .activityDate(LocalDate.of(2026, 11, 5))
                    .startTime(LocalTime.of(6, 0))
                    .endTime(LocalTime.of(7, 0))
                    // Omitted: category, maxCapacity, price, isIncluded, status
                    .build();

            when(activityRepository.existsOverlappingActivity(any(), any(), any(), any(), isNull()))
                    .thenReturn(false);
            when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> {
                OnboardActivity saved = i.getArgument(0);
                saved.setId(101L);
                return saved;
            });

            var res = activityService.createActivity(req);

            assertThat(res.getCategory()).isEqualTo("General");
            assertThat(res.getMaxCapacity()).isEqualTo(50);
            assertThat(res.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(res.getIsIncluded()).isTrue();
            assertThat(res.getStatus()).isEqualTo(ActivityStatus.SCHEDULED);
            assertThat(res.getCurrentParticipants()).isZero();
        }

        @Test
        @DisplayName("deleteActivity thực hiện Soft Delete và chuyển trạng thái sang CANCELLED")
        void shouldSoftDeleteAndCancelActivity() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(existingActivity));

            activityService.deleteActivity(100L);

            assertThat(existingActivity.getActive()).isFalse();
            assertThat(existingActivity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
            verify(activityRepository).save(existingActivity);
        }

        @Test
        @DisplayName("Không tìm thấy hoạt động (hoặc đã bị xóa active=false) ném ResourceNotFoundException")
        void shouldRejectWhenActivityNotFoundOrInactive() {
            when(activityRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> activityService.getActivityById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy hoạt động với ID: 999");
        }
    }
}
