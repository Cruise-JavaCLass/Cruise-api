package com.cruise.activity.service;

import com.cruise.activity.dto.ActivityCancellationResponse;
import com.cruise.activity.dto.ActivityRegistrationRequest;
import com.cruise.activity.dto.ActivityRegistrationResponse;
import com.cruise.activity.entity.ActivityRegistration;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.PaymentRequiredException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityRegistrationAuditLogRepository;
import com.cruise.activity.repository.ActivityRegistrationRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Registration & Cancellation Service Unit Tests (KAN-218)")
class ActivityRegistrationServiceTest {

    @Mock
    private OnboardActivityRepository activityRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private ActivityRegistrationRepository registrationRepository;

    @Mock
    private ActivityRegistrationAuditLogRepository auditLogRepository;

    @InjectMocks
    private ActivityRegistrationService registrationService;

    private OnboardActivity activity;
    private Passenger passenger;

    @BeforeEach
    void setUp() {
        activity = OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(1)
                .title("Cooking Masterclass with Chef")
                .description("Học nấu ăn cùng đầu bếp 5 sao")
                .category("Workshop")
                .location("Galley B - Deck 4")
                .activityDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .maxCapacity(2)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(500000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .cancellationCutOffMinutes(120)
                .refundPercentage(100)
                .active(true)
                .build();
        activity.setId(10L);

        passenger = Passenger.builder()
                .tourId(1L)
                .fullName("Nguyen Van A")
                .email("nguyenvana@gmail.com")
                .phoneNumber("0901234567")
                .cabinNumber("701")
                .rfidTag("RFID-701-A")
                .tourPackage("VIP Suite")
                .active(true)
                .build();
        passenger.setId(101L);
    }

    @Test
    @DisplayName("Đăng ký thành công khi còn chỗ và tính phí folio (CONFIRMED)")
    void registerActivity_Success_Confirmed() {
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .cabinNumber("701")
                .notes("Dị ứng hải sản")
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));
        when(registrationRepository.findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(eq(10L), eq(101L), any()))
                .thenReturn(Optional.empty());
        when(registrationRepository.countByActivityIdAndStatusAndActiveTrue(10L, RegistrationStatus.CONFIRMED))
                .thenReturn(0L);

        when(registrationRepository.save(any(ActivityRegistration.class))).thenAnswer(invocation -> {
            ActivityRegistration reg = invocation.getArgument(0);
            reg.setId(500L);
            return reg;
        });

        ActivityRegistrationResponse response = registrationService.registerActivity(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getRegistrationId()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getChargedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(response.getTransactionId()).startsWith("FOLIO-REG-");
        assertThat(activity.getCurrentParticipants()).isEqualTo(1);

        verify(auditLogRepository, times(1)).save(any());
        verify(activityRepository, times(1)).save(activity);
    }

    @Test
    @DisplayName("Đăng ký khi hoạt động đã đầy chỗ -> chuyển vào WAITLISTED")
    void registerActivity_Success_Waitlisted() {
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));
        when(registrationRepository.findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(eq(10L), eq(101L), any()))
                .thenReturn(Optional.empty());
        when(registrationRepository.countByActivityIdAndStatusAndActiveTrue(10L, RegistrationStatus.CONFIRMED))
                .thenReturn(2L); // Max capacity is 2
        when(registrationRepository.countByActivityIdAndStatusAndActiveTrue(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(1L);

        when(registrationRepository.save(any(ActivityRegistration.class))).thenAnswer(invocation -> {
            ActivityRegistration reg = invocation.getArgument(0);
            reg.setId(501L);
            return reg;
        });

        ActivityRegistrationResponse response = registrationService.registerActivity(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("WAITLISTED");
        assertThat(response.getChargedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTransactionId()).isNull();
        assertThat(response.getWaitlistPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("Đăng ký trùng lặp ném ngoại lệ DuplicateRegistrationException (409)")
    void registerActivity_Duplicate_ThrowsException() {
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));

        ActivityRegistration existing = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .status(RegistrationStatus.CONFIRMED)
                .build();
        when(registrationRepository.findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(eq(10L), eq(101L), any()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> registrationService.registerActivity(10L, request))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessageContaining("đã đăng ký hoạt động này trước đó");
    }

    @Test
    @DisplayName("Tài khoản cabin bị khóa chi tiêu ném ngoại lệ PaymentRequiredException (402)")
    void registerActivity_FolioLocked_ThrowsPaymentRequired() {
        passenger.setEmail("locked_passenger@cruise.com");
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> registrationService.registerActivity(10L, request))
                .isInstanceOf(PaymentRequiredException.class)
                .hasMessageContaining("đang bị khóa chi tiêu");
    }

    @Test
    @DisplayName("Hủy trước hạn cut-off được hoàn 100% và tự động đôn suất Waitlist")
    void cancelRegistration_BeforeCutoff_FullRefundAndAutoPromote() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .transactionId("FOLIO-REG-1234")
                .registeredAt(Instant.now().minusSeconds(3600))
                .active(true)
                .build();
        reg.setId(500L);

        ActivityRegistration waitlisted = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(102L)
                .cabinNumber("702")
                .status(RegistrationStatus.WAITLISTED)
                .chargedAmount(BigDecimal.ZERO)
                .registeredAt(Instant.now().minusSeconds(1800))
                .active(true)
                .build();
        waitlisted.setId(502L);

        activity.setCurrentParticipants(2);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of(waitlisted));

        ActivityCancellationResponse response = registrationService.cancelRegistration(10L, 500L, 101L, "Thay đổi lịch trình");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(response.getWaitlistPromotedPassengerId()).isEqualTo(102L);
        assertThat(waitlisted.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(waitlisted.getTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("Hủy quá hạn cut-off áp dụng phạt hoàn tiền")
    void cancelRegistration_AfterCutoff_PenaltyApplied() {
        activity.setActivityDate(LocalDate.now());
        activity.setStartTime(LocalTime.now().plusMinutes(30)); // Less than 120 minutes
        activity.setRefundPercentage(50); // 50% refund

        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .transactionId("FOLIO-REG-1234")
                .registeredAt(Instant.now().minusSeconds(3600))
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of());

        ActivityCancellationResponse response = registrationService.cancelRegistration(10L, 500L, 101L, "Hủy muộn");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(250000)); // 50% of 500000
    }

    @Test
    @DisplayName("Đôn suất thủ công từ Waitlist lên Confirmed thành công")
    void promoteWaitlist_Success() {
        ActivityRegistration waitlisted = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(102L)
                .passengerName("Tran Thi B")
                .cabinNumber("802")
                .status(RegistrationStatus.WAITLISTED)
                .chargedAmount(BigDecimal.ZERO)
                .registeredAt(Instant.now().minusSeconds(1800))
                .active(true)
                .build();
        waitlisted.setId(502L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(502L)).thenReturn(Optional.of(waitlisted));
        when(registrationRepository.save(any(ActivityRegistration.class))).thenReturn(waitlisted);

        ActivityRegistrationResponse response = registrationService.promoteWaitlist(10L, 502L);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getChargedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(response.getTransactionId()).startsWith("FOLIO-MAN-");
    }

    @Test
    @DisplayName("Lấy danh sách Roster phân trang và tìm kiếm thành công")
    void getRosterPaged_Success() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .registeredAt(Instant.now())
                .active(true)
                .build();
        reg.setId(500L);

        Pageable pageable = PageRequest.of(0, 10);
        when(registrationRepository.findRosterPaged(10L, "Nguyen", RegistrationStatus.CONFIRMED, pageable))
                .thenReturn(new PageImpl<>(List.of(reg)));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));

        Page<?> page = registrationService.getRosterPaged(10L, "Nguyen", RegistrationStatus.CONFIRMED, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Xuất CSV danh sách đăng ký chuẩn UTF-8 BOM")
    void exportRosterCsv_Success() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .transactionId("FOLIO-REG-1234")
                .registeredAt(Instant.now())
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findById(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(reg));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of());

        byte[] csvBytes = registrationService.exportRosterCsv(10L);

        assertThat(csvBytes).isNotEmpty();
        String csvString = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvString).startsWith("\uFEFF");
        assertThat(csvString).contains("BẢNG KÊ ĐĂNG KÝ HOẠT ĐỘNG: Cooking Masterclass with Chef");
        assertThat(csvString).contains("Nguyen Van A");
    }

    @Test
    @DisplayName("Lấy danh sách hàng đợi Waitlist thành công")
    void getWaitlistQueue_Success() {
        ActivityRegistration w1 = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status(RegistrationStatus.WAITLISTED)
                .registeredAt(Instant.now().minusSeconds(100))
                .active(true)
                .build();
        w1.setId(1L);

        ActivityRegistration w2 = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(102L)
                .passengerName("Tran Thi B")
                .cabinNumber("702")
                .status(RegistrationStatus.WAITLISTED)
                .registeredAt(Instant.now().minusSeconds(50))
                .active(true)
                .build();
        w2.setId(2L);

        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of(w1, w2));

        var queue = registrationService.getWaitlistQueue(10L);

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).getPosition()).isEqualTo(1);
        assertThat(queue.get(0).getPassengerName()).isEqualTo("Nguyen Van A");
        assertThat(queue.get(1).getPosition()).isEqualTo(2);
        assertThat(queue.get(1).getPassengerName()).isEqualTo("Tran Thi B");
    }

    @Test
    @DisplayName("Lấy danh sách hoạt động của hành khách bao gồm vị trí hàng đợi Waitlist")
    void getMyRegistrations_WithWaitlistPosition() {
        ActivityRegistration regConfirmed = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .registeredAt(Instant.now())
                .active(true)
                .build();
        regConfirmed.setId(1L);

        ActivityRegistration regWaitlisted = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .cabinNumber("701")
                .status(RegistrationStatus.WAITLISTED)
                .chargedAmount(BigDecimal.ZERO)
                .registeredAt(Instant.now())
                .active(true)
                .build();
        regWaitlisted.setId(2L);

        when(registrationRepository.findByPassengerIdAndActiveTrueOrderByRegisteredAtDesc(101L))
                .thenReturn(List.of(regConfirmed, regWaitlisted));
        when(activityRepository.findById(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of(regWaitlisted));

        var list = registrationService.getMyRegistrations(101L);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getStatus()).isEqualTo("CONFIRMED");
        assertThat(list.get(0).getWaitlistPosition()).isNull();
        assertThat(list.get(1).getStatus()).isEqualTo("WAITLISTED");
        assertThat(list.get(1).getWaitlistPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("Xuất CSV danh sách đăng ký bao gồm cả danh sách chờ Waitlist")
    void exportRosterCsv_WithWaitlist_Success() {
        ActivityRegistration confirmed = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status(RegistrationStatus.CONFIRMED)
                .chargedAmount(BigDecimal.valueOf(500000))
                .transactionId("FOLIO-REG-1234")
                .registeredAt(Instant.now())
                .active(true)
                .build();
        confirmed.setId(500L);

        ActivityRegistration waitlisted = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(102L)
                .passengerName("Tran Thi B")
                .cabinNumber("702")
                .status(RegistrationStatus.WAITLISTED)
                .chargedAmount(BigDecimal.ZERO)
                .registeredAt(Instant.now())
                .active(true)
                .build();
        waitlisted.setId(501L);

        when(activityRepository.findById(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(confirmed));
        when(registrationRepository.findByActivityIdAndStatusAndActiveTrueOrderByRegisteredAtAscIdAsc(10L, RegistrationStatus.WAITLISTED))
                .thenReturn(List.of(waitlisted));

        byte[] csvBytes = registrationService.exportRosterCsv(10L);

        assertThat(csvBytes).isNotEmpty();
        String csvString = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvString).contains("Tran Thi B");
        assertThat(csvString).contains("WAITLISTED");
    }

    @Test
    @DisplayName("Hủy đăng ký không thuộc hoạt động ném BusinessRuleException")
    void cancelRegistration_WrongActivity_ThrowsException() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(99L)
                .passengerId(101L)
                .status(RegistrationStatus.CONFIRMED)
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> registrationService.cancelRegistration(10L, 500L, 101L, "Lý do"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thuộc hoạt động");
    }

    @Test
    @DisplayName("Hủy đăng ký không thuộc hành khách ném BusinessRuleException")
    void cancelRegistration_WrongPassenger_ThrowsException() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(999L)
                .status(RegistrationStatus.CONFIRMED)
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> registrationService.cancelRegistration(10L, 500L, 101L, "Lý do"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thuộc về hành khách");
    }

    @Test
    @DisplayName("Hủy đăng ký đã bị hủy trước đó ném BusinessRuleException")
    void cancelRegistration_AlreadyCancelled_ThrowsException() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .status(RegistrationStatus.CANCELLED)
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> registrationService.cancelRegistration(10L, 500L, 101L, "Lý do"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã bị hủy trước đó");
    }

    @Test
    @DisplayName("Đôn suất thủ công khi bản ghi không ở trạng thái WAITLISTED ném BusinessRuleException")
    void promoteWaitlist_NotWaitlisted_ThrowsException() {
        ActivityRegistration reg = ActivityRegistration.builder()
                .activityId(10L)
                .passengerId(101L)
                .status(RegistrationStatus.CONFIRMED)
                .active(true)
                .build();
        reg.setId(500L);

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(registrationRepository.findById(500L)).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> registrationService.promoteWaitlist(10L, 500L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Chỉ có thể đôn suất");
    }

    @Test
    @DisplayName("Đăng ký thành công cho hoạt động miễn phí")
    void registerActivity_FreeActivity_Success() {
        activity.setPrice(BigDecimal.ZERO);
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));
        when(registrationRepository.findByActivityIdAndPassengerIdAndStatusInAndActiveTrue(eq(10L), eq(101L), any()))
                .thenReturn(Optional.empty());

        when(registrationRepository.save(any(ActivityRegistration.class))).thenAnswer(invocation -> {
            ActivityRegistration reg = invocation.getArgument(0);
            reg.setId(600L);
            return reg;
        });

        ActivityRegistrationResponse response = registrationService.registerActivity(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getChargedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTransactionId()).isNull();
    }

    @Test
    @DisplayName("Đăng ký thất bại khi tài khoản bị khóa qua cabin LOCKED")
    void registerActivity_CabinLocked_ThrowsPaymentRequired() {
        passenger.setCabinNumber("LOCKED-ROOM");
        ActivityRegistrationRequest request = ActivityRegistrationRequest.builder()
                .passengerId(101L)
                .build();

        when(activityRepository.findByIdWithLock(10L)).thenReturn(Optional.of(activity));
        when(passengerRepository.findById(101L)).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> registrationService.registerActivity(10L, request))
                .isInstanceOf(PaymentRequiredException.class);
    }
}
