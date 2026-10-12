package com.cruise.activity.service;

import com.cruise.activity.dto.ConfigureTimeAndLocationRequest;
import com.cruise.activity.dto.CreateActivityRequest;
import com.cruise.activity.dto.OnboardActivityResponse;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.OnboardActivityRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardActivityService Unit Tests (KAN-144)")
class OnboardActivityServiceTest {

    @Mock
    private OnboardActivityRepository activityRepository;

    @InjectMocks
    private OnboardActivityService activityService;

    private OnboardActivity sampleActivity;

    @BeforeEach
    void setUp() {
        sampleActivity = OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(1)
                .title("Dạ tiệc Chào mừng Hoàng gia")
                .description("Bữa tối sang trọng với nhạc sống")
                .category("Dining")
                .location("Grand Dining Hall")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 30))
                .maxCapacity(200)
                .currentParticipants(0)
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();
        sampleActivity.setId(10L);
    }

    @Test
    @DisplayName("createActivity: should succeed when input and timeslot are valid")
    void createActivity_Success() {
        CreateActivityRequest req = CreateActivityRequest.builder()
                .tourId(1L)
                .dayNumber(1)
                .title("Yoga Bình Minh")
                .location("Sundeck 12")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .maxCapacity(30)
                .build();

        when(activityRepository.existsOverlappingActivity(any(), any(), any(), any(), isNull()))
                .thenReturn(false);
        when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(invocation -> {
            OnboardActivity saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        OnboardActivityResponse res = activityService.createActivity(req);

        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(11L);
        assertThat(res.getTitle()).isEqualTo("Yoga Bình Minh");
        assertThat(res.getLocation()).isEqualTo("Sundeck 12");
        assertThat(res.getStartTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(res.getEndTime()).isEqualTo(LocalTime.of(7, 30));
        verify(activityRepository).save(any(OnboardActivity.class));
    }

    @Test
    @DisplayName("configureTimeAndLocation: should successfully update time and location (KAN-144)")
    void configureTimeAndLocation_Success() {
        ConfigureTimeAndLocationRequest req = ConfigureTimeAndLocationRequest.builder()
                .location("Sky Lounge (Tầng 14)")
                .activityDate(LocalDate.of(2026, 11, 2))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));
        when(activityRepository.existsOverlappingActivity(
                eq("Sky Lounge (Tầng 14)"),
                eq(LocalDate.of(2026, 11, 2)),
                eq(LocalTime.of(19, 0)),
                eq(LocalTime.of(21, 0)),
                eq(10L)
        )).thenReturn(false);
        when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

        OnboardActivityResponse res = activityService.configureTimeAndLocation(10L, req);

        assertThat(res).isNotNull();
        assertThat(res.getLocation()).isEqualTo("Sky Lounge (Tầng 14)");
        assertThat(res.getActivityDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(res.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(res.getEndTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("updateActivity: should successfully update all activity fields (KAN-134)")
    void updateActivity_Success() {
        com.cruise.activity.dto.UpdateActivityRequest req = com.cruise.activity.dto.UpdateActivityRequest.builder()
                .dayNumber(2)
                .title("Dạ tiệc Khiêu vũ & Ẩm thực Ý")
                .description("Bữa tối phong cách Địa Trung Hải")
                .category("Dining")
                .location("Venetian Ballroom")
                .activityDate(LocalDate.of(2026, 11, 2))
                .startTime(LocalTime.of(19, 30))
                .endTime(LocalTime.of(22, 0))
                .maxCapacity(250)
                .price(BigDecimal.valueOf(150000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));
        when(activityRepository.existsOverlappingActivity(
                eq("Venetian Ballroom"),
                eq(LocalDate.of(2026, 11, 2)),
                eq(LocalTime.of(19, 30)),
                eq(LocalTime.of(22, 0)),
                eq(10L)
        )).thenReturn(false);
        when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

        OnboardActivityResponse res = activityService.updateActivity(10L, req);

        assertThat(res).isNotNull();
        assertThat(res.getTitle()).isEqualTo("Dạ tiệc Khiêu vũ & Ẩm thực Ý");
        assertThat(res.getLocation()).isEqualTo("Venetian Ballroom");
        assertThat(res.getDayNumber()).isEqualTo(2);
        assertThat(res.getMaxCapacity()).isEqualTo(250);
        assertThat(res.getPrice()).isEqualTo(BigDecimal.valueOf(150000));
        assertThat(res.getIsIncluded()).isFalse();
    }

    @Test
    @DisplayName("updateActivity: throws ResourceNotFoundException when activity id does not exist")
    void updateActivity_NotFound() {
        com.cruise.activity.dto.UpdateActivityRequest req = com.cruise.activity.dto.UpdateActivityRequest.builder()
                .title("Test")
                .location("Hall")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .build();

        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.updateActivity(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hoạt động với ID: 99");
    }

    @Test
    @DisplayName("configureTimeAndLocation: throws ResourceNotFoundException when activity id does not exist")
    void configureTimeAndLocation_NotFound() {
        ConfigureTimeAndLocationRequest req = ConfigureTimeAndLocationRequest.builder()
                .location("Sky Lounge")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.configureTimeAndLocation(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hoạt động với ID: 99");
    }

    @Test
    @DisplayName("validateTimeAndLocation: throws BusinessRuleException when location is blank")
    void validateTimeAndLocation_BlankLocation() {
        ConfigureTimeAndLocationRequest req = ConfigureTimeAndLocationRequest.builder()
                .location("   ")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        assertThatThrownBy(() -> activityService.configureTimeAndLocation(10L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Địa điểm tổ chức không được để trống");
    }

    @Test
    @DisplayName("validateTimeAndLocation: throws BusinessRuleException when endTime is not after startTime")
    void validateTimeAndLocation_EndTimeBeforeStartTime() {
        ConfigureTimeAndLocationRequest req = ConfigureTimeAndLocationRequest.builder()
                .location("Main Theatre")
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(14, 0))
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        assertThatThrownBy(() -> activityService.configureTimeAndLocation(10L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Thời gian kết thúc phải sau thời gian bắt đầu");
    }

    @Test
    @DisplayName("validateTimeAndLocation: throws BusinessRuleException when time conflict occurs at same location")
    void validateTimeAndLocation_TimeConflict() {
        ConfigureTimeAndLocationRequest req = ConfigureTimeAndLocationRequest.builder()
                .location("Grand Dining Hall")
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(20, 0))
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));
        when(activityRepository.existsOverlappingActivity(any(), any(), any(), any(), eq(10L)))
                .thenReturn(true);

        assertThatThrownBy(() -> activityService.configureTimeAndLocation(10L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Xung đột lịch trình: Địa điểm 'Grand Dining Hall'");
    }

    @Test
    @DisplayName("getActivityById: returns activity when found")
    void getActivityById_Success() {
        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        OnboardActivityResponse res = activityService.getActivityById(10L);
        assertThat(res.getId()).isEqualTo(10L);
        assertThat(res.getTitle()).isEqualTo("Dạ tiệc Chào mừng Hoàng gia");
    }

    @Test
    @DisplayName("getActivitiesByTour: returns list of activities")
    void getActivitiesByTour_Success() {
        when(activityRepository.findByTourIdAndActiveTrueOrderByActivityDateAscStartTimeAsc(1L))
                .thenReturn(List.of(sampleActivity));

        List<OnboardActivityResponse> list = activityService.getActivitiesByTour(1L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("Dạ tiệc Chào mừng Hoàng gia");
    }

    @Test
    @DisplayName("getActivitiesByTourAndDay: returns list of activities for day")
    void getActivitiesByTourAndDay_Success() {
        when(activityRepository.findByTourIdAndDayNumberAndActiveTrueOrderByStartTimeAsc(1L, 1))
                .thenReturn(List.of(sampleActivity));

        List<OnboardActivityResponse> list = activityService.getActivitiesByTourAndDay(1L, 1);
        assertThat(list).hasSize(1);
    }

    @Test
    @DisplayName("getActivitiesPaged: returns paged activities")
    void getActivitiesPaged_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(activityRepository.findPagedActivities(1L, 1, "Dining", "tiệc", pageable))
                .thenReturn(new PageImpl<>(List.of(sampleActivity), pageable, 1));

        Page<OnboardActivityResponse> page = activityService.getActivitiesPaged(1L, 1, "Dining", "tiệc", pageable);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Dạ tiệc Chào mừng Hoàng gia");
    }

    @Test
    @DisplayName("deleteActivity: soft deletes activity successfully")
    void deleteActivity_Success() {
        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        activityService.deleteActivity(10L);

        assertThat(sampleActivity.getActive()).isFalse();
        assertThat(sampleActivity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        verify(activityRepository).save(sampleActivity);
    }

    @Test
    @DisplayName("updateActivityStatus: should successfully transition SCHEDULED -> IN_PROGRESS (KAN-136)")
    void updateActivityStatus_Success() {
        com.cruise.activity.dto.UpdateActivityStatusRequest req = com.cruise.activity.dto.UpdateActivityStatusRequest.builder()
                .status(ActivityStatus.IN_PROGRESS)
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));
        when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

        OnboardActivityResponse res = activityService.updateActivityStatus(10L, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateActivityStatus: should transition to CANCELLED with cancellation reason (KAN-136)")
    void updateActivityStatus_CancelledWithReason() {
        com.cruise.activity.dto.UpdateActivityStatusRequest req = com.cruise.activity.dto.UpdateActivityStatusRequest.builder()
                .status(ActivityStatus.CANCELLED)
                .cancellationReason("Thời tiết giông bão ngoài khơi")
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));
        when(activityRepository.save(any(OnboardActivity.class))).thenAnswer(i -> i.getArgument(0));

        OnboardActivityResponse res = activityService.updateActivityStatus(10L, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(res.getCancellationReason()).isEqualTo("Thời tiết giông bão ngoài khơi");
    }

    @Test
    @DisplayName("updateActivityStatus: throws BusinessRuleException when transition is invalid (KAN-136)")
    void updateActivityStatus_InvalidTransition() {
        // sampleActivity has status SCHEDULED, cannot jump directly to COMPLETED
        com.cruise.activity.dto.UpdateActivityStatusRequest req = com.cruise.activity.dto.UpdateActivityStatusRequest.builder()
                .status(ActivityStatus.COMPLETED)
                .build();

        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        assertThatThrownBy(() -> activityService.updateActivityStatus(10L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Không thể chuyển trạng thái hoạt động");
    }

    @Test
    @DisplayName("getAllowedStatusTransitions: returns allowed next states for activity (KAN-136)")
    void getAllowedStatusTransitions_Success() {
        when(activityRepository.findById(10L)).thenReturn(Optional.of(sampleActivity));

        java.util.Set<ActivityStatus> allowed = activityService.getAllowedStatusTransitions(10L);

        assertThat(allowed).containsExactlyInAnyOrder(ActivityStatus.IN_PROGRESS, ActivityStatus.CANCELLED);
    }

    @Test
    @DisplayName("updateActivityStatus: throws ResourceNotFoundException when activity does not exist (KAN-136)")
    void updateActivityStatus_NotFound() {
        com.cruise.activity.dto.UpdateActivityStatusRequest req = com.cruise.activity.dto.UpdateActivityStatusRequest.builder()
                .status(ActivityStatus.IN_PROGRESS)
                .build();

        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.updateActivityStatus(999L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hoạt động với ID: 999");
    }

    @Test
    @DisplayName("getAllowedStatusTransitions: throws ResourceNotFoundException when activity does not exist (KAN-136)")
    void getAllowedStatusTransitions_NotFound() {
        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getAllowedStatusTransitions(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hoạt động với ID: 999");
    }
}
