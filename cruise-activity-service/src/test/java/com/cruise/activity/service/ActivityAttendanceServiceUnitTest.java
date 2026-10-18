package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ActivityAttendance;
import com.cruise.activity.entity.ActivityCheckInLog;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ActivityCheckInMethod;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateCheckInException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ActivityAttendanceRepository;
import com.cruise.activity.repository.ActivityCheckInLogRepository;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Attendance & QR Check-in Unit Tests (KAN-223)")
class ActivityAttendanceServiceUnitTest {

    @Mock
    private OnboardActivityRepository activityRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private ActivityAttendanceRepository attendanceRepository;

    @Mock
    private ActivityCheckInLogRepository checkInLogRepository;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Spy
    private QrTokenService qrTokenService = new QrTokenService("TestSecretKeyForActivityAttendanceHMAC1234567890");

    @InjectMocks
    private ActivityAttendanceService attendanceService;

    private OnboardActivity sampleActivity;
    private Passenger samplePassenger;
    private ActivityAttendance sampleAttendance;

    @BeforeEach
    void setUp() {
        sampleActivity = OnboardActivity.builder()
                .tourId(1L)
                .dayNumber(2)
                .title("Dạ tiệc Hoàng Hôn & Nhạc Sống")
                .description("Biểu diễn nhạc acoustic và cocktail tại Lounge tầng 14")
                .category("Giải Trí")
                .location("Sky Lounge (Tầng 14)")
                .activityDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .maxCapacity(50)
                .currentParticipants(10)
                .price(BigDecimal.valueOf(250000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();
        sampleActivity.setId(100L);

        samplePassenger = Passenger.builder()
                .tourId(1L)
                .fullName("Trần Minh Quân")
                .email("quan.tran@example.com")
                .phoneNumber("0912345678")
                .cabinNumber("Suite-1402")
                .rfidTag("RFID-VIP-9941")
                .qrCode("QR-PASS-100-1")
                .active(true)
                .build();
        samplePassenger.setId(1L);

        sampleAttendance = ActivityAttendance.builder()
                .activityId(100L)
                .passengerId(1L)
                .passengerName("Trần Minh Quân")
                .cabinNumber("Suite-1402")
                .rfidTag("RFID-VIP-9941")
                .status(ActivityAttendanceStatus.REGISTERED)
                .isWalkIn(false)
                .chargedAmount(BigDecimal.ZERO)
                .active(true)
                .build();
        sampleAttendance.setId(500L);

        org.mockito.Mockito.lenient().when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                .thenAnswer(invocation -> {
                    ActivityAttendance att = invocation.getArgument(0);
                    if (att.getId() == null) {
                        att.setId(999L);
                    }
                    return att;
                });
    }

    @Nested
    @DisplayName("1. QrTokenService Security Engine Tests (KAN-222)")
    class QrTokenServiceTests {

        @Test
        @DisplayName("Sinh mã QR token hợp lệ và giải mã thành công")
        void testGenerateAndVerifyTokenSuccess() {
            String token = qrTokenService.generateToken(100L, 1L, "Suite-1402");
            assertThat(token).isNotBlank().contains(".");

            QrTokenService.QrTokenPayload payload = qrTokenService.verifyAndDecode(token, 100L);
            assertThat(payload).isNotNull();
            assertThat(payload.getActivityId()).isEqualTo(100L);
            assertThat(payload.getPassengerId()).isEqualTo(1L);
            assertThat(payload.getCabinNumber()).isEqualTo("Suite-1402");
            assertThat(payload.getExpiresAt()).isGreaterThan(Instant.now().toEpochMilli());
        }

        @Test
        @DisplayName("Phát hiện mã QR bị chỉnh sửa chữ ký (Tampered HMAC)")
        void testTamperedTokenThrowsException() {
            String token = qrTokenService.generateToken(100L, 1L, "Suite-1402");
            String[] parts = token.split("\\.");
            String tampered = parts[0] + ".InvalidSignature123456789";

            assertThatThrownBy(() -> qrTokenService.verifyAndDecode(tampered, 100L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Tampered QR token");
        }

        @Test
        @DisplayName("Từ chối mã QR khi đã hết thời gian sống (Expired TTL)")
        void testExpiredTokenThrowsException() {
            // TTL 0s sẽ hết hạn ngay lập tức
            String expiredToken = qrTokenService.generateToken(100L, 1L, "Suite-1402", -5L);

            assertThatThrownBy(() -> qrTokenService.verifyAndDecode(expiredToken, 100L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Mã QR đã hết hạn");
        }

        @Test
        @DisplayName("Từ chối mã QR khi sử dụng cho hoạt động khác")
        void testMismatchedActivityThrowsException() {
            String token = qrTokenService.generateToken(100L, 1L, "Suite-1402");

            assertThatThrownBy(() -> qrTokenService.verifyAndDecode(token, 999L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("không thuộc hoạt động hiện tại");
        }

        @Test
        @DisplayName("Khởi tạo QrTokenService với secret dưới 32 bytes ném IllegalArgumentException")
        void testShortSecretThrowsException() {
            assertThatThrownBy(() -> new QrTokenService("short-secret"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be configured with at least 32 UTF-8 bytes");
        }
    }

    @Nested
    @DisplayName("2. Single Check-In & Idempotency Workflow Tests (KAN-222)")
    class SingleCheckInTests {

        @Test
        @DisplayName("Sinh ActivityPassResponse cho hành khách kèm TTL 60s và thông tin sự kiện")
        void testGenerateQrPassSuccess() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));

            ActivityPassResponse pass = attendanceService.generateQrPass(100L, 1L);

            assertThat(pass).isNotNull();
            assertThat(pass.getActivityId()).isEqualTo(100L);
            assertThat(pass.getActivityTitle()).isEqualTo("Dạ tiệc Hoàng Hôn & Nhạc Sống");
            assertThat(pass.getDeck()).isEqualTo("Deck 14");
            assertThat(pass.getPassengerName()).isEqualTo("Trần Minh Quân");
            assertThat(pass.getToken()).isNotBlank();
            assertThat(pass.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("Sinh QR Pass kích hoạt lại bản ghi attendance đang ở trạng thái active=false")
        void testGenerateQrPassReactivatingInactiveRecord() {
            sampleAttendance.setActive(false);
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.save(sampleAttendance)).thenReturn(sampleAttendance);

            ActivityPassResponse pass = attendanceService.generateQrPass(100L, 1L);

            assertThat(pass).isNotNull();
            assertThat(sampleAttendance.getActive()).isTrue();
            verify(attendanceRepository, times(1)).save(sampleAttendance);
        }

        @Test
        @DisplayName("Check-in thành công qua QR Token hợp lệ")
        void testCheckInWithQrTokenSuccess() {
            String token = qrTokenService.generateToken(100L, 1L, "Suite-1402");

            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(1L);

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .qrToken(token)
                    .staffId("STAFF-01")
                    .staffName("Lê Văn Kiểm Soát")
                    .deviceId("DEVICE-GATE-1")
                    .build();

            ActivityCheckInResponse response = attendanceService.checkIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
            assertThat(response.getPassengerName()).isEqualTo("Trần Minh Quân");
            assertThat(response.getMethod()).isEqualTo("QR_CODE");

            verify(checkInLogRepository, times(1)).save(any(ActivityCheckInLog.class));
            verify(activityRepository, times(1)).save(sampleActivity);
            assertThat(sampleActivity.getCurrentParticipants()).isEqualTo(1);
        }

        @Test
        @DisplayName("Check-in thành công qua thẻ RFID")
        void testCheckInWithRfidSuccess() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findByRfidTagAndActiveTrue("RFID-VIP-9941")).thenReturn(Optional.of(samplePassenger));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .rfidTag("RFID-VIP-9941")
                    .staffId("STAFF-02")
                    .build();

            ActivityCheckInResponse response = attendanceService.checkIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
            assertThat(response.getMethod()).isEqualTo("RFID");
        }

        @Test
        @DisplayName("Khóa chống quét trùng lặp: Ném DuplicateCheckInException khi quét lại lần 2")
        void testDuplicateCheckInThrowsException() {
            sampleAttendance.setStatus(ActivityAttendanceStatus.CHECKED_IN);
            sampleAttendance.setCheckedInAt(Instant.now().minusSeconds(120));
            sampleAttendance.setCheckedInByStaffId("STAFF-01");

            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .passengerId(1L)
                    .staffId("STAFF-03")
                    .build();

            assertThatThrownBy(() -> attendanceService.checkIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đã check-in lúc");

            verify(checkInLogRepository, times(1)).save(any(ActivityCheckInLog.class));
        }

        @Test
        @DisplayName("Check-in qua Cabin Number ném BusinessRuleException nếu có nhiều hơn 1 khách")
        void testCheckInMultipleGuestsInCabinThrows() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            Passenger p2 = Passenger.builder().fullName("Trần Thị Mai").cabinNumber("Suite-1402").active(true).build();
            p2.setId(2L);
            when(passengerRepository.findByCabinNumberAndActiveTrue("Suite-1402"))
                    .thenReturn(List.of(samplePassenger, p2));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .cabinNumber("Suite-1402")
                    .build();

            assertThatThrownBy(() -> attendanceService.checkIn(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("có 2 hành khách");
        }

        @Test
        @DisplayName("Check-in bắt ObjectOptimisticLockingFailureException dịch thành DuplicateCheckInException")
        void testCheckInOptimisticLockingThrows() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(ActivityAttendance.class, 500L));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.checkIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đang được check-in đồng thời");
        }

        @Test
        @DisplayName("Check-in bắt DataIntegrityViolationException uk_activity_passenger dịch thành DuplicateCheckInException")
        void testCheckInDataIntegrityViolationThrows() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.empty());
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique constraint uk_activity_passenger violated"));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.checkIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đã được điểm danh trước đó");
        }

        @Test
        @DisplayName("Check-in ném lại DataIntegrityViolationException không phải uk_activity_passenger")
        void testCheckInGenericDataIntegrityViolationThrows() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("foreign key violated"));

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.checkIn(100L, request))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .hasMessageContaining("foreign key violated");
        }

        @Test
        @DisplayName("Check-in kích hoạt lại bản ghi attendance đang ở trạng thái active=false")
        void testCheckInReusingInactiveRecord() {
            sampleAttendance.setActive(false);
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN)).thenReturn(1L);

            ActivityCheckInRequest request = ActivityCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            ActivityCheckInResponse response = attendanceService.checkIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(sampleAttendance.getActive()).isTrue();
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
        }
    }

    @Nested
    @DisplayName("3. Walk-In Guest & Capacity Enforcement Tests (KAN-320)")
    class WalkInGuestTests {

        @Test
        @DisplayName("Khách vãng lai check-in thành công, ghi nợ folio và tăng số lượng tham gia")
        void testWalkInSuccessWithBilling() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(15L)
                    .thenReturn(16L);
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.empty());

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .staffId("STAFF-WALKIN-1")
                    .note("Khách đến sớm tại cửa")
                    .build();

            WalkInCheckInResponse response = attendanceService.walkInCheckIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getIsWalkIn()).isTrue();
            assertThat(response.getChargedAmount()).isEqualTo(BigDecimal.valueOf(250000));
            assertThat(response.getTransactionId()).isEqualTo("PENDING_FOLIO_BILLING");
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");

            verify(checkInLogRepository, times(1)).save(any(ActivityCheckInLog.class));
            assertThat(sampleActivity.getCurrentParticipants()).isEqualTo(16);
        }

        @Test
        @DisplayName("Từ chối khách vãng lai khi hoạt động đã đạt sức chứa tối đa (Full Capacity)")
        void testWalkInFullCapacityThrowsException() {
            sampleActivity.setMaxCapacity(20);
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(20L); // Đã đầy 20/20

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Hoạt động đã hết chỗ");
        }

        @Test
        @DisplayName("Walk-in với RFID tìm thấy khách thành công và cập nhật lại bản ghi cũ chưa điểm danh")
        void testWalkInWithRfidReusingExistingRecord() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L)
                    .thenReturn(6L);
            when(passengerRepository.findByRfidTagAndActiveTrue("RFID-VIP-9941")).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .rfidTag("RFID-VIP-9941")
                    .staffId("STAFF-01")
                    .build();

            WalkInCheckInResponse response = attendanceService.walkInCheckIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getPassengerId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
        }

        @Test
        @DisplayName("Walk-in ném BusinessRuleException khi không tìm thấy hành khách hợp lệ")
        void testWalkInUnresolvedPassengerThrows() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(0L);

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .rfidTag("UNKNOWN-RFID")
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể xác định hành khách hợp lệ");
        }

        @Test
        @DisplayName("Walk-in ném DuplicateCheckInException khi hành khách đã check-in trước đó")
        void testWalkInAlreadyCheckedInThrows() {
            sampleAttendance.setStatus(ActivityAttendanceStatus.CHECKED_IN);
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L);
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đã check-in hoạt động này trước đó");
        }

        @Test
        @DisplayName("Walk-in với số cabin có 1 hành khách thành công kèm ghi chú")
        void testWalkInWithSingleCabinPassengerSuccess() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L)
                    .thenReturn(6L);
            when(passengerRepository.findByCabinNumberAndActiveTrue("Suite-1402"))
                    .thenReturn(List.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .cabinNumber("Suite-1402")
                    .note("Khách đổi ý tham gia")
                    .staffId("STAFF-03")
                    .build();

            WalkInCheckInResponse response = attendanceService.walkInCheckIn(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getPassengerId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo("CHECKED_IN");
            assertThat(sampleAttendance.getNote()).isEqualTo("Khách đổi ý tham gia");
        }

        @Test
        @DisplayName("Walk-in với số cabin có nhiều hơn 1 hành khách ném BusinessRuleException")
        void testWalkInWithMultipleCabinPassengersThrows() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L);
            Passenger p2 = Passenger.builder().fullName("Hành Khách Cùng Phòng").cabinNumber("Suite-1402").active(true).build();
            p2.setId(2L);
            when(passengerRepository.findByCabinNumberAndActiveTrue("Suite-1402"))
                    .thenReturn(List.of(samplePassenger, p2));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .cabinNumber("Suite-1402")
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("có 2 hành khách");
        }

        @Test
        @DisplayName("Walk-in ném DuplicateCheckInException khi gặp ObjectOptimisticLockingFailureException")
        void testWalkInOptimisticLockingThrows() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L);
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(ActivityAttendance.class, 500L));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đang được xử lý đồng thời bởi thiết bị khác");
        }

        @Test
        @DisplayName("Walk-in ném DuplicateCheckInException khi vi phạm ràng buộc uk_activity_passenger")
        void testWalkInDataIntegrityViolationThrows() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L);
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.empty());
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique constraint uk_activity_passenger"));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đã check-in hoạt động này");
        }

        @Test
        @DisplayName("Walk-in ném lại DataIntegrityViolationException không phải uk_activity_passenger")
        void testWalkInGenericDataIntegrityViolationThrows() {
            when(activityRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(5L);
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.saveAndFlush(any(ActivityAttendance.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("other constraint"));

            WalkInCheckInRequest request = WalkInCheckInRequest.builder()
                    .passengerId(1L)
                    .build();

            assertThatThrownBy(() -> attendanceService.walkInCheckIn(100L, request))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .hasMessageContaining("other constraint");
        }
    }

    @Nested
    @DisplayName("4. Attendance Summary, Roster & Batch Offline Sync Tests (KAN-321)")
    class SummaryRosterAndOfflineSyncTests {

        @Test
        @DisplayName("Lấy thống kê điểm danh sự kiện realtime chính xác")
        void testGetAttendanceSummarySuccess() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(attendanceRepository.countByActivityIdAndActiveTrue(100L)).thenReturn(30L);
            when(attendanceRepository.countByActivityIdAndStatusAndActiveTrue(100L, ActivityAttendanceStatus.CHECKED_IN))
                    .thenReturn(25L);
            when(attendanceRepository.countByActivityIdAndIsWalkInTrueAndActiveTrue(100L)).thenReturn(5L);

            ActivityAttendanceSummaryResponse summary = attendanceService.getAttendanceSummary(100L);

            assertThat(summary).isNotNull();
            assertThat(summary.getMaxCapacity()).isEqualTo(50);
            assertThat(summary.getTotalRegistered()).isEqualTo(30L);
            assertThat(summary.getCheckedInCount()).isEqualTo(25L);
            assertThat(summary.getRemainingCapacity()).isEqualTo(25); // 50 - 25 = 25
            assertThat(summary.getAttendanceRate()).isEqualTo(50.0); // 25 / 50 * 100%
            assertThat(summary.getWalkInCount()).isEqualTo(5L);
            assertThat(summary.getIsCapacityFull()).isFalse();
        }

        @Test
        @DisplayName("Truy xuất danh sách điểm danh phân trang (Roster)")
        void testGetActivityRosterSuccess() {
            when(activityRepository.existsById(100L)).thenReturn(true);
            Pageable pageable = PageRequest.of(0, 10);
            Page<ActivityAttendance> page = new PageImpl<>(List.of(sampleAttendance), pageable, 1);

            when(attendanceRepository.searchRoster(eq(100L), any(), any(), eq(pageable)))
                    .thenReturn(page);

            Page<ActivityRosterPassengerResponse> roster = attendanceService.getActivityRoster(100L, null, "Quân", pageable);

            assertThat(roster).isNotNull();
            assertThat(roster.getTotalElements()).isEqualTo(1);
            assertThat(roster.getContent().get(0).getPassengerName()).isEqualTo("Trần Minh Quân");
        }

        @Test
        @DisplayName("Đồng bộ hàng loạt lượt quét ngoại tuyến thành công, bỏ qua trùng lặp an toàn")
        void testSyncOfflineCheckInsSuccess() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));

            // Bản ghi 1: Chưa điểm danh -> thành công
            ActivityAttendance att1 = ActivityAttendance.builder()
                    .activityId(100L)
                    .passengerId(1L)
                    .passengerName("Trần Minh Quân")
                    .cabinNumber("Suite-1402")
                    .status(ActivityAttendanceStatus.REGISTERED)
                    .active(true)
                    .build();
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(att1));

            // Bản ghi 2: Đã điểm danh trước đó -> trùng lặp
            Passenger p2 = Passenger.builder().fullName("Lê Thị B").cabinNumber("Deluxe-501").active(true).build();
            p2.setId(2L);
            when(passengerRepository.findById(2L)).thenReturn(Optional.of(p2));
            ActivityAttendance att2 = ActivityAttendance.builder()
                    .activityId(100L)
                    .passengerId(2L)
                    .passengerName("Lê Thị B")
                    .status(ActivityAttendanceStatus.CHECKED_IN)
                    .active(true)
                    .build();
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 2L))
                    .thenReturn(Optional.of(att2));

            OfflineCheckInBatchRequest batchRequest = OfflineCheckInBatchRequest.builder()
                    .checkIns(List.of(
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder()
                                    .passengerId(1L)
                                    .staffId("OFFLINE-POS-1")
                                    .scannedAt(Instant.now().minusSeconds(300))
                                    .method(ActivityCheckInMethod.QR_CODE)
                                    .offlineQueueId("QUEUE-ITEM-1")
                                    .build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder()
                                    .passengerId(2L)
                                    .staffId("OFFLINE-POS-1")
                                    .scannedAt(Instant.now().minusSeconds(200))
                                    .method(ActivityCheckInMethod.RFID)
                                    .offlineQueueId("QUEUE-ITEM-2")
                                    .build()
                    ))
                    .build();

            OfflineCheckInBatchResponse result = attendanceService.syncOfflineCheckIns(100L, batchRequest);

            assertThat(result).isNotNull();
            assertThat(result.getProcessedCount()).isEqualTo(1);
            assertThat(result.getDuplicateCount()).isEqualTo(1);
            assertThat(result.getFailedCount()).isEqualTo(0);

            verify(attendanceRepository, times(1)).saveAndFlush(att1);
            assertThat(att1.getStatus()).isEqualTo(ActivityAttendanceStatus.CHECKED_IN);
        }

        @Test
        @DisplayName("Đồng bộ ngoại tuyến xử lý các trường hợp: QR Token, RFID, Cabin 1 khách, Cabin nhiều khách, không tìm thấy khách, lỗi lưu concurrency")
        void testSyncOfflineCheckInsEdgeCases() {
            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));

            // Item 1: Resolve qua QR Token
            String qrToken = qrTokenService.generateToken(100L, 1L, "Suite-1402");
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L)).thenReturn(Optional.empty());

            // Item 2: Resolve qua RFID
            Passenger p2 = Passenger.builder().fullName("Nguyễn Văn B").cabinNumber("A101").active(true).build();
            p2.setId(2L);
            when(passengerRepository.findByRfidTagAndActiveTrue("RFID-TAG-2")).thenReturn(Optional.of(p2));
            when(passengerRepository.findById(2L)).thenReturn(Optional.of(p2));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 2L)).thenReturn(Optional.empty());

            // Item 3: Resolve qua Cabin (1 khách)
            Passenger p3 = Passenger.builder().fullName("Trần C").cabinNumber("B202").active(true).build();
            p3.setId(3L);
            when(passengerRepository.findByCabinNumberAndActiveTrue("B202")).thenReturn(List.of(p3));
            when(passengerRepository.findById(3L)).thenReturn(Optional.of(p3));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 3L)).thenReturn(Optional.empty());

            // Item 4: Cabin có nhiều hơn 1 khách -> passId null -> failed
            Passenger p4a = Passenger.builder().fullName("Khách 4A").cabinNumber("C303").active(true).build();
            Passenger p4b = Passenger.builder().fullName("Khách 4B").cabinNumber("C303").active(true).build();
            when(passengerRepository.findByCabinNumberAndActiveTrue("C303")).thenReturn(List.of(p4a, p4b));

            // Item 5: Không có thông tin định danh nào -> failed

            // Item 6: Passenger inactive -> failed
            Passenger p6 = Passenger.builder().fullName("Khách Không Active").cabinNumber("D404").active(false).build();
            p6.setId(6L);
            when(passengerRepository.findById(6L)).thenReturn(Optional.of(p6));

            // Item 7: Concurrency exception khi lưu -> xem như duplicate
            Passenger p7 = Passenger.builder().fullName("Khách Trùng Ghi Nhận").cabinNumber("E505").active(true).build();
            p7.setId(7L);
            when(passengerRepository.findById(7L)).thenReturn(Optional.of(p7));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 7L)).thenReturn(Optional.empty());

            doAnswer(invocation -> {
                ActivityAttendance att = invocation.getArgument(0);
                if (att != null && Long.valueOf(7L).equals(att.getPassengerId())) {
                    throw new org.springframework.dao.DataIntegrityViolationException("unique");
                }
                return att;
            }).when(attendanceRepository).saveAndFlush(any(ActivityAttendance.class));

            // Item 8: QR token bị lỗi định dạng -> catch Exception -> passId null -> failed
            // Item 9: Tái kích hoạt attendance inactive thành active=true
            Passenger p9 = Passenger.builder().fullName("Khách Tái Kích Hoạt").cabinNumber("F606").active(true).build();
            p9.setId(9L);
            when(passengerRepository.findById(9L)).thenReturn(Optional.of(p9));
            ActivityAttendance att9 = ActivityAttendance.builder()
                    .activityId(100L)
                    .passengerId(9L)
                    .passengerName("Khách Tái Kích Hoạt")
                    .status(ActivityAttendanceStatus.REGISTERED)
                    .active(false)
                    .build();
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 9L)).thenReturn(Optional.of(att9));

            OfflineCheckInBatchRequest batchRequest = OfflineCheckInBatchRequest.builder()
                    .checkIns(List.of(
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().qrToken(qrToken).offlineQueueId("Q1").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().rfidTag("RFID-TAG-2").offlineQueueId("Q2").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().cabinNumber("B202").offlineQueueId("Q3").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().cabinNumber("C303").offlineQueueId("Q4").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().offlineQueueId("Q5").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().passengerId(6L).offlineQueueId("Q6").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().passengerId(7L).offlineQueueId("Q7").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().qrToken("invalid.corrupted.token").offlineQueueId("Q8").build(),
                            OfflineCheckInBatchRequest.OfflineCheckInItem.builder().passengerId(9L).offlineQueueId("Q9").build()
                    ))
                    .build();

            OfflineCheckInBatchResponse result = attendanceService.syncOfflineCheckIns(100L, batchRequest);

            assertThat(result).isNotNull();
            assertThat(result.getProcessedCount()).isEqualTo(4); // Q1, Q2, Q3, Q9
            assertThat(result.getDuplicateCount()).isEqualTo(1); // Q7 (DataIntegrityViolation)
            assertThat(result.getFailedCount()).isEqualTo(4);    // Q4, Q5, Q6, Q8
            assertThat(att9.getActive()).isTrue();
        }
    }
}
