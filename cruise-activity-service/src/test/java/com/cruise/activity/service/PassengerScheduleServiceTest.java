package com.cruise.activity.service;

import com.cruise.activity.dto.PersonalScheduleItemDto;
import com.cruise.activity.dto.PersonalScheduleResponse;
import com.cruise.activity.dto.ScheduleConflictDto;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassengerScheduleService Unit Tests (KAN-351)")
class PassengerScheduleServiceTest {

    @Mock
    private PassengerRepository passengerRepository;
    @Mock
    private ActivityRegistrationRepository activityRegistrationRepository;
    @Mock
    private OnboardActivityRepository onboardActivityRepository;
    @Mock
    private ExcursionPassengerRepository excursionPassengerRepository;
    @Mock
    private ShoreExcursionRepository shoreExcursionRepository;
    @Mock
    private ActivityAttendanceRepository activityAttendanceRepository;

    @InjectMocks
    private PassengerScheduleService scheduleService;

    private Passenger passenger1;
    private Passenger passenger2;
    private OnboardActivity activity1;
    private OnboardActivity activity2;
    private ShoreExcursion excursion1;

    @BeforeEach
    void setUp() {
        passenger1 = Passenger.builder()
                .tourId(100L)
                .fullName("Nguyễn Văn A")
                .cabinNumber("701")
                .active(true)
                .build();
        passenger1.setId(1L);

        passenger2 = Passenger.builder()
                .tourId(100L)
                .fullName("Trần Thị B")
                .cabinNumber("701")
                .active(true)
                .build();
        passenger2.setId(2L);

        activity1 = OnboardActivity.builder()
                .tourId(100L)
                .dayNumber(1)
                .title("Yoga Bình Minh")
                .category("Wellness")
                .location("Sundeck 12")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(8, 0))
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .active(true)
                .build();
        activity1.setId(10L);

        activity2 = OnboardActivity.builder()
                .tourId(100L)
                .dayNumber(1)
                .title("Lớp Học Nấu Ăn")
                .category("Culinary")
                .location("Galley Studio")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(9, 0))
                .price(BigDecimal.valueOf(25))
                .isIncluded(false)
                .active(true)
                .build();
        activity2.setId(20L);

        excursion1 = ShoreExcursion.builder()
                .tourId(100L)
                .dayNumber(1)
                .title("Khám phá Di sản Chàm")
                .portName("Cảng Chân Mây")
                .meetingPoint("Cầu tàu số 2")
                .excursionDate(LocalDate.of(2026, 11, 1))
                .gatheringTime(LocalTime.of(9, 30))
                .departureTime(LocalTime.of(10, 0))
                .returnToShipTime(LocalTime.of(14, 0))
                .price(BigDecimal.valueOf(80))
                .isIncluded(false)
                .active(true)
                .build();
        excursion1.setId(30L);
    }

    @Test
    @DisplayName("Lấy lịch trình phạm vi cá nhân (my) thành công và phát hiện xung đột")
    void getPersonalSchedule_MyScope_SuccessWithConflict() {
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger1));

        ActivityRegistration reg1 = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(1L)
                .status(RegistrationStatus.CONFIRMED)
                .cabinNumber("701")
                .build();
        reg1.setId(101L);

        ActivityRegistration reg2 = ActivityRegistration.builder()
                .activityId(20L)
                .passengerId(1L)
                .status(RegistrationStatus.CONFIRMED)
                .cabinNumber("701")
                .build();
        reg2.setId(102L);

        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(1L))
                .thenReturn(List.of(reg1, reg2));
        when(onboardActivityRepository.findById(10L)).thenReturn(Optional.of(activity1));
        when(onboardActivityRepository.findById(20L)).thenReturn(Optional.of(activity2));

        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("701"))
                .thenReturn(Collections.emptyList());

        PersonalScheduleResponse response = scheduleService.getPersonalSchedule(1L, 1, "my");

        assertThat(response).isNotNull();
        assertThat(response.getPassengerId()).isEqualTo(1L);
        assertThat(response.getScope()).isEqualTo("my");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalItems()).isEqualTo(2);

        // Do activity1 (07:00-08:00) và activity2 (07:30-09:00) trùng nhau 30 phút
        assertThat(response.getTotalConflicts()).isEqualTo(1);
        assertThat(response.getItems().get(0).getHasConflict()).isTrue();
        assertThat(response.getItems().get(1).getHasConflict()).isTrue();
        assertThat(response.getItems().get(0).getConflictWithIds()).contains(20L);
        assertThat(response.getItems().get(1).getConflictWithIds()).contains(10L);

        ScheduleConflictDto conflict = response.getConflicts().get(0);
        assertThat(conflict.getOverlapMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Lấy lịch trình phạm vi Cabin (cabin) tổng hợp cả 2 thành viên và phân loại xung đột")
    void getPersonalSchedule_CabinScope_Success() {
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger1));
        when(passengerRepository.findByCabinNumberAndActiveTrue("701")).thenReturn(List.of(passenger1, passenger2));

        ActivityRegistration reg1 = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .status(RegistrationStatus.CONFIRMED)
                .cabinNumber("701")
                .build();
        reg1.setId(101L);

        ActivityRegistration reg2 = ActivityRegistration.builder()
                .activityId(20L)
                .passengerId(2L)
                .passengerName("Trần Thị B")
                .status(RegistrationStatus.CONFIRMED)
                .cabinNumber("701")
                .build();
        reg2.setId(102L);

        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(1L))
                .thenReturn(List.of(reg1));
        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(2L))
                .thenReturn(List.of(reg2));

        when(onboardActivityRepository.findById(10L)).thenReturn(Optional.of(activity1));
        when(onboardActivityRepository.findById(20L)).thenReturn(Optional.of(activity2));

        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("701"))
                .thenReturn(Collections.emptyList());

        PersonalScheduleResponse response = scheduleService.getPersonalSchedule(1L, 1, "cabin");

        assertThat(response).isNotNull();
        assertThat(response.getScope()).isEqualTo("cabin");
        assertThat(response.getTotalItems()).isEqualTo(2);

        // Vì 2 người khác nhau trong cabin tham gia cùng khung giờ, không bị tính là xung đột cá nhân
        assertThat(response.getTotalConflicts()).isEqualTo(0);
        assertThat(response.getItems().get(0).getHasConflict()).isFalse();
        assertThat(response.getItems().get(1).getHasConflict()).isFalse();
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi hành khách không tồn tại")
    void getPersonalSchedule_PassengerNotFound_ThrowsException() {
        when(passengerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getPersonalSchedule(999L, 1, "my"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Tính toán cảnh báo giờ tập trung cho Tour tham quan trên bờ")
    void computeAssemblyWarning_Success() {
        String warning = scheduleService.computeAssemblyWarning(excursion1);
        assertThat(warning).isEqualTo("Tập trung trước giờ khởi hành 30 phút tại Cầu tàu số 2");

        // Khi giờ tập trung bằng giờ xuất phát hoặc null
        ShoreExcursion noGathering = ShoreExcursion.builder()
                .departureTime(LocalTime.of(10, 0))
                .gatheringTime(null)
                .build();
        assertThat(scheduleService.computeAssemblyWarning(noGathering)).isNull();
        assertThat(scheduleService.computeAssemblyWarning(null)).isNull();
    }

    @Test
    @DisplayName("Hai sự kiện liên tiếp liền kề (Contiguous) không bị coi là xung đột")
    void detectScheduleConflicts_ContiguousEvents_NoConflict() {
        PersonalScheduleItemDto itemA = PersonalScheduleItemDto.builder()
                .eventId(1L)
                .title("Sự kiện 1")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status("REGISTERED")
                .passengerId(1L)
                .build();

        PersonalScheduleItemDto itemB = PersonalScheduleItemDto.builder()
                .eventId(2L)
                .title("Sự kiện 2")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status("REGISTERED")
                .passengerId(1L)
                .build();

        List<PersonalScheduleItemDto> items = new ArrayList<>(List.of(itemA, itemB));
        List<ScheduleConflictDto> conflicts = scheduleService.detectScheduleConflicts(items, "my");

        assertThat(conflicts).isEmpty();
        assertThat(itemA.getHasConflict()).isFalse();
        assertThat(itemB.getHasConflict()).isFalse();
    }

    @Test
    @DisplayName("Sự kiện bị hủy (CANCELLED) không gây ra cảnh báo xung đột")
    void detectScheduleConflicts_CancelledEvents_Ignored() {
        PersonalScheduleItemDto itemA = PersonalScheduleItemDto.builder()
                .eventId(1L)
                .title("Sự kiện 1")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .status("CANCELLED")
                .passengerId(1L)
                .build();

        PersonalScheduleItemDto itemB = PersonalScheduleItemDto.builder()
                .eventId(2L)
                .title("Sự kiện 2")
                .startTime(LocalTime.of(9, 30))
                .endTime(LocalTime.of(10, 30))
                .status("REGISTERED")
                .passengerId(1L)
                .build();

        List<ScheduleConflictDto> conflicts = scheduleService.detectScheduleConflicts(new ArrayList<>(List.of(itemA, itemB)), "my");
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("Lấy danh sách xung đột trực tiếp qua getScheduleConflicts")
    void getScheduleConflicts_DirectCall_ReturnsConflicts() {
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger1));

        ActivityRegistration reg1 = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(1L)
                .status(RegistrationStatus.CONFIRMED)
                .build();
        ActivityRegistration reg2 = ActivityRegistration.builder()
                .activityId(20L)
                .passengerId(1L)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(1L))
                .thenReturn(List.of(reg1, reg2));
        when(onboardActivityRepository.findById(10L)).thenReturn(Optional.of(activity1));
        when(onboardActivityRepository.findById(20L)).thenReturn(Optional.of(activity2));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("701")).thenReturn(Collections.emptyList());

        List<ScheduleConflictDto> conflicts = scheduleService.getScheduleConflicts(1L, 1);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getFirstEventTitle()).isEqualTo("Yoga Bình Minh");
    }

    @Test
    @DisplayName("Kiểm tra ánh xạ trạng thái điểm danh khi đã check-in")
    void getPersonalSchedule_CheckedInActivity_StatusCheckedIn() {
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger1));

        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(1L)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(1L))
                .thenReturn(List.of(reg));
        when(onboardActivityRepository.findById(10L)).thenReturn(Optional.of(activity1));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("701")).thenReturn(Collections.emptyList());

        ActivityAttendance attendance = ActivityAttendance.builder()
                .activityId(10L)
                .passengerId(1L)
                .status(ActivityAttendanceStatus.CHECKED_IN)
                .build();
        when(activityAttendanceRepository.findByActivityIdAndPassengerIdAndActiveTrue(10L, 1L))
                .thenReturn(Optional.of(attendance));

        PersonalScheduleResponse response = scheduleService.getPersonalSchedule(1L, 1, "my");
        assertThat(response.getItems().get(0).getStatus()).isEqualTo("CHECKED_IN");
    }

    @Test
    @DisplayName("Kiểm tra ánh xạ tour trên bờ có ExcursionPassengerStatus.RETURNED_SAFE")
    void getPersonalSchedule_ExcursionReturnedSafe_StatusCompleted() {
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger1));
        when(activityRegistrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(1L))
                .thenReturn(Collections.emptyList());

        ExcursionPassenger ep = ExcursionPassenger.builder()
                .excursionId(30L)
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("701")
                .ticketCode("TCK-9901")
                .status(ExcursionPassengerStatus.RETURNED_SAFE)
                .build();
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("701"))
                .thenReturn(List.of(ep));
        when(shoreExcursionRepository.findById(30L)).thenReturn(Optional.of(excursion1));

        PersonalScheduleResponse response = scheduleService.getPersonalSchedule(1L, 1, "my");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getItems().get(0).getItemType()).isEqualTo("EXCURSION");
        assertThat(response.getItems().get(0).getAssemblyWarning()).isNotNull();
    }
}
