package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionBookingLog;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcursionRosterAndWaitlistServiceTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository passengerRepository;

    @Mock
    private ExcursionVehicleRepository vehicleRepository;

    @Mock
    private PassengerRepository cruisePassengerRepository;

    @Mock
    private ExcursionBookingLogRepository bookingLogRepository;

    @Mock
    private PassengerNotificationService notificationService;

    @InjectMocks
    private ShoreExcursionBookingService bookingService;

    private ShoreExcursion sampleExcursion;
    private Passenger samplePassenger;

    @BeforeEach
    void setUp() {
        sampleExcursion = ShoreExcursion.builder()
                .title("Tour Thác Bạc Sapa")
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .maxCapacity(2)
                .currentParticipants(2)
                .price(new BigDecimal("100.00"))
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .active(true)
                .build();
        sampleExcursion.setId(50L);

        samplePassenger = Passenger.builder()
                .fullName("Nguyen Van A")
                .cabinNumber("A-201")
                .phoneNumber("+84901234567")
                .active(true)
                .build();
        samplePassenger.setId(201L);
    }

    @Nested
    @DisplayName("Roster Retrieval (AC1)")
    class RosterRetrievalTests {

        @Test
        @DisplayName("Lấy danh sách Roster phân trang và tổng hợp số liệu chính xác")
        void shouldReturnRosterSummaryCorrectly() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger p1 = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .passengerName("Nguyen Van A")
                    .cabinNumber("A-201")
                    .ticketCode("EXC-50-1")
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .status(ExcursionPassengerStatus.REGISTERED)
                    .specialRequirements("Vegetarian meal")
                    .active(true)
                    .build();
            p1.setId(101L);

            Pageable pageable = PageRequest.of(0, 10);
            Page<ExcursionPassenger> page = new PageImpl<>(List.of(p1), pageable, 1);

            given(passengerRepository.findRosterWithFilters(eq(50L), any(), any(), any(), any(), eq(pageable)))
                    .willReturn(page);
            given(passengerRepository.countByExcursionIdAndBookingStatusAndActiveTrue(50L, ExcursionBookingStatus.CONFIRMED))
                    .willReturn(1L);
            given(passengerRepository.countByExcursionIdAndBookingStatusAndActiveTrue(50L, ExcursionBookingStatus.WAITLISTED))
                    .willReturn(0L);
            given(passengerRepository.countByExcursionId(50L)).willReturn(1L);
            given(passengerRepository.countByExcursionIdAndActiveTrue(50L)).willReturn(1L);
            given(passengerRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(List.of(p1));

            ExcursionRosterSummaryDto summary = bookingService.getExcursionRoster(
                    50L, null, null, null, false, pageable
            );

            assertThat(summary).isNotNull();
            assertThat(summary.getExcursionId()).isEqualTo(50L);
            assertThat(summary.getConfirmedCount()).isEqualTo(1L);
            assertThat(summary.getSpecialAssistanceCount()).isEqualTo(1L);
            assertThat(summary.getItems()).hasSize(1);
            assertThat(summary.getItems().get(0).getNeedsSpecialAssistance()).isTrue();
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException khi tour không tồn tại")
        void shouldThrowExceptionWhenExcursionNotFound() {
            given(excursionRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.getExcursionRoster(999L, null, null, null, false, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Waitlist Queue & Ingestion (AC2)")
    class WaitlistIngestionTests {

        @Test
        @DisplayName("Khi tour đạt maxCapacity, đăng ký mới tự động được xếp vào WAITLIST theo thứ tự FIFO")
        void shouldIngestIntoWaitlistWhenCapacityFull() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));
            given(cruisePassengerRepository.findById(201L)).willReturn(Optional.of(samplePassenger));
            given(passengerRepository.findMaxWaitlistPosition(50L)).willReturn(null);

            ExcursionPassenger savedWaitlist = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .passengerName(samplePassenger.getFullName())
                    .cabinNumber(samplePassenger.getCabinNumber())
                    .ticketCode("WAIT-50-999")
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .waitlistPosition(1)
                    .chargedAmount(BigDecimal.ZERO)
                    .active(true)
                    .build();
            savedWaitlist.setId(301L);

            given(passengerRepository.save(any(ExcursionPassenger.class))).willReturn(savedWaitlist);

            ExcursionBookingRequest req = new ExcursionBookingRequest();
            req.setPassengerId(201L);

            ExcursionBookingResponse resp = bookingService.joinWaitlist(50L, req);

            assertThat(resp).isNotNull();
            assertThat(resp.getStatus()).isEqualTo(ExcursionBookingStatus.WAITLISTED.name());
            assertThat(resp.getChargedAmount()).isEqualTo(BigDecimal.ZERO);
            verify(bookingLogRepository, atLeastOnce()).save(any(ExcursionBookingLog.class));
        }

        @Test
        @DisplayName("Tra cứu danh sách chờ theo thứ tự vị trí hàng đợi FIFO")
        void shouldReturnWaitlistQueueInFifoOrder() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger w1 = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .passengerName("Passenger One")
                    .cabinNumber("A-101")
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .waitlistPosition(1)
                    .active(true)
                    .build();
            w1.setId(11L);

            ExcursionPassenger w2 = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(202L)
                    .passengerName("Passenger Two")
                    .cabinNumber("A-102")
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .waitlistPosition(2)
                    .active(true)
                    .build();
            w2.setId(12L);

            given(passengerRepository.findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(
                    50L, ExcursionBookingStatus.WAITLISTED)).willReturn(List.of(w1, w2));

            List<ExcursionWaitlistEntryDto> queue = bookingService.getWaitlistQueue(50L);

            assertThat(queue).hasSize(2);
            assertThat(queue.get(0).getPosition()).isEqualTo(1);
            assertThat(queue.get(1).getPosition()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Waitlist Promotion & Auto-promote (AC2)")
    class PromotionTests {

        @Test
        @DisplayName("Đôn vé thủ công đẩy người đầu tiên trong waitlist lên CONFIRMED")
        void shouldPromoteFirstWaitlistPassengerSuccessfully() {
            given(excursionRepository.findByIdWithLock(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger w1 = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .passengerName("Passenger One")
                    .cabinNumber("A-101")
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .waitlistPosition(1)
                    .active(true)
                    .build();
            w1.setId(11L);

            given(passengerRepository.findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(
                    50L, ExcursionBookingStatus.WAITLISTED)).willReturn(List.of(w1));
            given(cruisePassengerRepository.findById(201L)).willReturn(Optional.of(samplePassenger));
            given(vehicleRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(new ArrayList<>());
            given(passengerRepository.save(any(ExcursionPassenger.class))).willReturn(w1);

            WaitlistPromotionResultDto result = bookingService.promoteNextWaitlistPassenger(50L);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExcursionBookingStatus.CONFIRMED);
            assertThat(result.getBookingId()).isEqualTo(11L);
            verify(bookingLogRepository, atLeastOnce()).save(any(ExcursionBookingLog.class));
        }

        @Test
        @DisplayName("Ném BusinessRuleException nếu hàng đợi chờ rỗng khi đôn vé")
        void shouldThrowExceptionWhenWaitlistEmpty() {
            given(excursionRepository.findByIdWithLock(50L)).willReturn(Optional.of(sampleExcursion));
            given(passengerRepository.findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(
                    50L, ExcursionBookingStatus.WAITLISTED)).willReturn(List.of());

            assertThatThrownBy(() -> bookingService.promoteNextWaitlistPassenger(50L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("danh sách chờ của tour này đang trống");
        }
    }

    @Nested
    @DisplayName("Medical Waiver Cancellation (AC4)")
    class MedicalWaiverTests {

        @Test
        @DisplayName("Hủy vé y tế khẩn cấp miễn 100% phí phạt và tự động kích hoạt đôn người chờ")
        void shouldCancelWithMedicalWaiverAndTriggerPromotion() {
            given(excursionRepository.findByIdWithLock(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger booking = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .ticketCode("EXC-50-888")
                    .chargedAmount(new BigDecimal("150.00"))
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .active(true)
                    .build();
            booking.setId(901L);

            given(passengerRepository.findByIdAndExcursionIdAndActiveTrue(901L, 50L))
                    .willReturn(Optional.of(booking));
            given(passengerRepository.save(any(ExcursionPassenger.class))).willReturn(booking);

            MedicalWaiverCancellationRequest req = MedicalWaiverCancellationRequest.builder()
                    .reason("Sốt xuất huyết chuyển cấp cứu")
                    .approvedByStaffId("DOCTOR-01")
                    .build();

            ExcursionCancellationResponse resp = bookingService.cancelBookingWithMedicalWaiver(50L, 901L, req);

            assertThat(resp).isNotNull();
            assertThat(resp.getStatus()).isEqualTo("CANCELLED_MEDICAL_WAIVER");
            assertThat(resp.getRefundAmount()).isEqualTo(new BigDecimal("150.00"));
            assertThat(resp.getPenaltyFee()).isEqualTo(BigDecimal.ZERO);
            assertThat(booking.getMedicalWaiver()).isTrue();
            verify(bookingLogRepository, atLeastOnce()).save(any(ExcursionBookingLog.class));
        }
    }

    @Nested
    @DisplayName("CSV Export & Schedule Alert (KAN-327)")
    class ExportAndAlertTests {

        @Test
        @DisplayName("Xuất CSV Roster sinh dữ liệu byte chuẩn RFC 4180")
        void shouldExportCsvData() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger p = ExcursionPassenger.builder()
                    .ticketCode("EXC-50-1")
                    .passengerName("John, Doe")
                    .cabinNumber("C-101")
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .chargedAmount(new BigDecimal("120.00"))
                    .active(true)
                    .build();

            given(passengerRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(List.of(p));

            byte[] csv = bookingService.exportRosterCsv(50L);

            assertThat(csv).isNotNull();
            String csvText = new String(csv);
            assertThat(csvText).contains("Ticket Code,Passenger Name");
            assertThat(csvText).contains("\"John, Doe\"");
            verify(bookingLogRepository, atLeastOnce()).save(any(ExcursionBookingLog.class));
        }

        @Test
        @DisplayName("Phát cảnh báo thay đổi lịch trình gửi thông báo tới các hành khách trong tour")
        void shouldDispatchAlertToPassengers() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger p = ExcursionPassenger.builder()
                    .passengerId(201L)
                    .active(true)
                    .build();

            given(passengerRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(List.of(p));

            ExcursionScheduleAlertRequest req = ExcursionScheduleAlertRequest.builder()
                    .title("Thay đổi giờ tập trung")
                    .message("Tập trung sớm hơn 30 phút tại Boong 3")
                    .isUrgent(true)
                    .build();

            ExcursionScheduleAlertResponse resp = bookingService.triggerScheduleAlert(50L, req);

            assertThat(resp).isNotNull();
            assertThat(resp.getRecipientsCount()).isEqualTo(1);
            assertThat(resp.getStatus()).isEqualTo("BROADCAST_SUCCESS");
            verify(notificationService, times(1)).sendNotification(eq(201L), any());
        }
    }

        @Test
        @DisplayName("Huy ve WAITLISTED khong lam giam so nguoi tham gia va khong don nguoi khac")
        void shouldNotDecrementParticipantsWhenWaitlistedBookingCancelled() {
            given(excursionRepository.findByIdWithLock(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger waitlistedBooking = ExcursionPassenger.builder()
                    .excursionId(50L)
                    .passengerId(201L)
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .waitlistPosition(1)
                    .active(true)
                    .chargedAmount(BigDecimal.ZERO)
                    .build();
            waitlistedBooking.setId(101L);

            given(passengerRepository.findByIdAndExcursionIdAndActiveTrue(101L, 50L))
                    .willReturn(Optional.of(waitlistedBooking));
            given(passengerRepository.save(any(ExcursionPassenger.class))).willReturn(waitlistedBooking);

            int initialParticipants = sampleExcursion.getCurrentParticipants();
            ExcursionCancellationResponse resp = bookingService.cancelBooking(50L, 101L, "Khach huy");

            assertThat(resp).isNotNull();
            assertThat(sampleExcursion.getCurrentParticipants()).isEqualTo(initialParticipants);
            verify(passengerRepository, never()).findByExcursionIdAndBookingStatusAndActiveTrueOrderByWaitlistPositionAsc(anyLong(), any());
        }

        @Test
        @DisplayName("Xuat CSV phong chong Spreadsheet Formula Injection va dung CRLF")
        void shouldEscapeFormulaInjectionInCsv() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger p = ExcursionPassenger.builder()
                    .ticketCode("=cmd|' /C calc'!A0")
                    .passengerName("+DangerousName")
                    .cabinNumber("-101")
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .chargedAmount(new BigDecimal("120.00"))
                    .active(true)
                    .build();

            given(passengerRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(List.of(p));

            byte[] csv = bookingService.exportRosterCsv(50L);
            String csvText = new String(csv);

            assertThat(csvText).contains("\r\n");
            assertThat(csvText).contains("'=cmd|' /C calc'!A0");
            assertThat(csvText).contains("'+DangerousName");
            assertThat(csvText).contains("'-101");
        }

        @Test
        @DisplayName("Phat canh bao dinh kem dia diem va gio tap trung, loai tru khach waitlist")
        void shouldIncludeStructuredScheduleDetailsAndExcludeWaitlist() {
            given(excursionRepository.findById(50L)).willReturn(Optional.of(sampleExcursion));

            ExcursionPassenger confirmed = ExcursionPassenger.builder()
                    .passengerId(201L)
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .active(true)
                    .build();

            ExcursionPassenger waitlisted = ExcursionPassenger.builder()
                    .passengerId(202L)
                    .bookingStatus(ExcursionBookingStatus.WAITLISTED)
                    .active(true)
                    .build();

            given(passengerRepository.findByExcursionIdAndActiveTrue(50L)).willReturn(List.of(confirmed, waitlisted));

            ExcursionScheduleAlertRequest req = ExcursionScheduleAlertRequest.builder()
                    .title("Thay doi gio tap trung")
                    .message("Vui long co mat dung gio")
                    .updatedMeetingLocation("Boong 4")
                    .updatedGatheringTime("08:30")
                    .isUrgent(true)
                    .build();

            ExcursionScheduleAlertResponse resp = bookingService.triggerScheduleAlert(50L, req);

            assertThat(resp).isNotNull();
            assertThat(resp.getRecipientsCount()).isEqualTo(1);
            assertThat(resp.getFailedCount()).isEqualTo(0);
            assertThat(resp.getStatus()).isEqualTo("BROADCAST_SUCCESS");
            assertThat(resp.getMessage()).contains("Boong 4");
            assertThat(resp.getMessage()).contains("08:30");

            verify(notificationService, times(1)).sendNotification(eq(201L), any());
            verify(notificationService, never()).sendNotification(eq(202L), any());
        }

}
