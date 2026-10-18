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
        @DisplayName("Check-in thành công qua QR Token hợp lệ")
        void testCheckInWithQrTokenSuccess() {
            String token = qrTokenService.generateToken(100L, 1L, "Suite-1402");

            when(activityRepository.findById(100L)).thenReturn(Optional.of(sampleActivity));
            when(passengerRepository.findById(1L)).thenReturn(Optional.of(samplePassenger));
            when(attendanceRepository.findByActivityIdAndPassengerId(100L, 1L))
                    .thenReturn(Optional.of(sampleAttendance));
            when(attendanceRepository.save(any(ActivityAttendance.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
            when(attendanceRepository.save(any(ActivityAttendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            when(attendanceRepository.save(any(ActivityAttendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

            verify(attendanceRepository, times(1)).save(att1);
            assertThat(att1.getStatus()).isEqualTo(ActivityAttendanceStatus.CHECKED_IN);
        }
    }
}
