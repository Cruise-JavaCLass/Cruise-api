package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.PaymentRequiredException;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoreExcursionBookingServiceTest {

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

    @InjectMocks
    private ShoreExcursionBookingService bookingService;

    private ShoreExcursion excursion;
    private Passenger passenger;
    private ExcursionVehicle vehicle1;
    private ExcursionVehicle vehicle2;

    @BeforeEach
    void setUp() {
        excursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(20L)
                .dayNumber(2)
                .title("Hạ Long Bay Cruise & Kayaking")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu cảng số 2")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(16, 0))
                .maxCapacity(50)
                .currentParticipants(10)
                .price(new BigDecimal("120.00"))
                .currency("USD")
                .isIncluded(false)
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .active(true)
                .build();
        excursion.setId(100L);

        passenger = Passenger.builder()
                .tourId(10L)
                .fullName("Trần Gia Huy")
                .cabinNumber("Suite-801")
                .email("huytran@cruise.com")
                .phoneNumber("0987654321")
                .rfidTag("RFID-801-01")
                .tourPackage("STANDARD")
                .active(true)
                .build();
        passenger.setId(200L);

        vehicle1 = ExcursionVehicle.builder()
                .excursionId(100L)
                .vehicleCode("BUS-01")
                .licensePlate("14B-8888")
                .capacity(30)
                .assignedPassengers(10)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicle1.setId(1L);

        vehicle2 = ExcursionVehicle.builder()
                .excursionId(100L)
                .vehicleCode("BUS-02")
                .licensePlate("14B-9999")
                .capacity(30)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicle2.setId(2L);
    }

    @Test
    @DisplayName("Đặt tour thành công với vé điện tử EXC-{id}-{timestamp} và ghi nợ Folio")
    void bookExcursion_Success_ChargesFolio() {
        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(cruisePassengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(passengerRepository.findByExcursionIdAndPassengerIdAndActiveTrue(100L, 200L)).thenReturn(Optional.empty());
        when(vehicleRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(vehicle1, vehicle2));
        when(passengerRepository.countByExcursionIdAndActiveTrue(100L)).thenReturn(10L);

        when(passengerRepository.save(any(ExcursionPassenger.class))).thenAnswer(invocation -> {
            ExcursionPassenger ep = invocation.getArgument(0);
            ep.setId(501L);
            return ep;
        });

        ExcursionBookingRequest request = ExcursionBookingRequest.builder()
                .passengerId(200L)
                .cabinNumber("Suite-801")
                .build();

        ExcursionBookingResponse resp = bookingService.bookExcursion(100L, request);

        assertThat(resp).isNotNull();
        assertThat(resp.getBookingId()).isEqualTo(501L);
        assertThat(resp.getTicketCode()).startsWith("EXC-100-");
        assertThat(resp.getChargedAmount()).isEqualByComparingTo("120.00");
        assertThat(resp.getTransactionId()).startsWith("FOLIO-EXC-");
        assertThat(resp.getIsIncluded()).isFalse();

        verify(bookingLogRepository, times(1)).save(any(ExcursionBookingLog.class));
        verify(excursionRepository, times(1)).save(excursion);
    }

    @Test
    @DisplayName("Đặt tour thành công với gói VIP/Included: Giá vé 0 USD")
    void bookExcursion_Success_PackageIncluded_FreeTicket() {
        passenger.setTourPackage("VIP_ALL_INCLUSIVE");

        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(cruisePassengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(passengerRepository.findByExcursionIdAndPassengerIdAndActiveTrue(100L, 200L)).thenReturn(Optional.empty());
        when(vehicleRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(vehicle1));
        when(passengerRepository.countByExcursionIdAndActiveTrue(100L)).thenReturn(5L);

        when(passengerRepository.save(any(ExcursionPassenger.class))).thenAnswer(invocation -> {
            ExcursionPassenger ep = invocation.getArgument(0);
            ep.setId(502L);
            return ep;
        });

        ExcursionBookingRequest request = ExcursionBookingRequest.builder().passengerId(200L).build();
        ExcursionBookingResponse resp = bookingService.bookExcursion(100L, request);

        assertThat(resp.getChargedAmount()).isEqualByComparingTo("0.00");
        assertThat(resp.getIsIncluded()).isTrue();
        assertThat(resp.getTransactionId()).isNull();
    }

    @Test
    @DisplayName("Từ chối đặt tour khi tổng sức chứa các xe hoạt động đã đầy")
    void bookExcursion_ThrowsWhenVehicleCapacityExceeded() {
        vehicle1.setCapacity(10);
        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(cruisePassengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(passengerRepository.findByExcursionIdAndPassengerIdAndActiveTrue(100L, 200L)).thenReturn(Optional.empty());
        when(vehicleRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(vehicle1));
        when(passengerRepository.countByExcursionIdAndActiveTrue(100L)).thenReturn(10L);

        ExcursionBookingRequest request = ExcursionBookingRequest.builder().passengerId(200L).build();

        assertThatThrownBy(() -> bookingService.bookExcursion(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tổng sức chứa của các xe đang hoạt động đã đầy");
    }

    @Test
    @DisplayName("Từ chối đặt chỗ khi tài khoản Folio bị khóa chi tiêu")
    void bookExcursion_ThrowsWhenFolioLocked() {
        passenger.setCabinNumber("LOCKED-102");
        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(cruisePassengerRepository.findById(200L)).thenReturn(Optional.of(passenger));

        ExcursionBookingRequest request = ExcursionBookingRequest.builder().passengerId(200L).build();

        assertThatThrownBy(() -> bookingService.bookExcursion(100L, request))
                .isInstanceOf(PaymentRequiredException.class)
                .hasMessageContaining("đang bị khóa");
    }

    @Test
    @DisplayName("Từ chối đặt trùng lặp cùng 1 tour bờ")
    void bookExcursion_ThrowsOnDuplicateBooking() {
        ExcursionPassenger existing = ExcursionPassenger.builder().ticketCode("EXC-100-999").build();
        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(cruisePassengerRepository.findById(200L)).thenReturn(Optional.of(passenger));
        when(passengerRepository.findByExcursionIdAndPassengerIdAndActiveTrue(100L, 200L)).thenReturn(Optional.of(existing));

        ExcursionBookingRequest request = ExcursionBookingRequest.builder().passengerId(200L).build();

        assertThatThrownBy(() -> bookingService.bookExcursion(100L, request))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessageContaining("đã đăng ký tour bờ này trước đó");
    }

    @Test
    @DisplayName("Hủy tour trước 24h: Hoàn tiền 100%, 0 phạt")
    void cancelBooking_FreeCancellation_Before24Hours() {
        excursion.setExcursionDate(LocalDate.now().plusDays(3));
        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .chargedAmount(new BigDecimal("100.00"))
                .active(true)
                .build();
        booking.setId(901L);

        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(901L, 100L)).thenReturn(Optional.of(booking));

        ExcursionCancellationResponse res = bookingService.cancelBooking(100L, 901L, "Thay đổi kế hoạch");

        assertThat(res.getStatus()).isEqualTo("CANCELLED");
        assertThat(res.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(res.getPenaltyFee()).isEqualByComparingTo("0.00");
        verify(bookingLogRepository, times(1)).save(any(ExcursionBookingLog.class));
    }

    @Test
    @DisplayName("Hủy tour từ 12-24h: Phạt 50%, hoàn 50%")
    void cancelBooking_50PercentPenalty_Between12And24Hours() {
        // Gathering time cách thời điểm hiện tại 18h
        LocalDateTime target = LocalDateTime.now().plusHours(18);
        excursion.setExcursionDate(target.toLocalDate());
        excursion.setGatheringTime(target.toLocalTime());

        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .chargedAmount(new BigDecimal("100.00"))
                .active(true)
                .build();
        booking.setId(902L);

        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(902L, 100L)).thenReturn(Optional.of(booking));

        ExcursionCancellationResponse res = bookingService.cancelBooking(100L, 902L, "Kế hoạch gấp");

        assertThat(res.getPenaltyFee()).isEqualByComparingTo("50.00");
        assertThat(res.getRefundAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Hủy tour dưới 12h: Phạt 100%, không hoàn tiền")
    void cancelBooking_100PercentPenalty_Under12Hours() {
        LocalDateTime target = LocalDateTime.now().plusHours(4);
        excursion.setExcursionDate(target.toLocalDate());
        excursion.setGatheringTime(target.toLocalTime());

        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .chargedAmount(new BigDecimal("100.00"))
                .active(true)
                .build();
        booking.setId(903L);

        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(903L, 100L)).thenReturn(Optional.of(booking));

        ExcursionCancellationResponse res = bookingService.cancelBooking(100L, 903L, "Muộn giờ");

        assertThat(res.getPenaltyFee()).isEqualByComparingTo("100.00");
        assertThat(res.getRefundAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Hủy tour do thời tiết xấu (Weather): Hoàn 100% dù dưới 12h")
    void cancelBooking_WeatherForceMajeure_FullRefund() {
        LocalDateTime target = LocalDateTime.now().plusHours(2);
        excursion.setExcursionDate(target.toLocalDate());
        excursion.setGatheringTime(target.toLocalTime());

        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .chargedAmount(new BigDecimal("150.00"))
                .active(true)
                .build();
        booking.setId(904L);

        when(excursionRepository.findByIdWithLock(100L)).thenReturn(Optional.of(excursion));
        when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(904L, 100L)).thenReturn(Optional.of(booking));

        ExcursionCancellationResponse res = bookingService.cancelBooking(100L, 904L, "Hủy cảng do bão thời tiết xấu (weather)");

        assertThat(res.getRefundAmount()).isEqualByComparingTo("150.00");
        assertThat(res.getPenaltyFee()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Chuyển xe thành công và cập nhật tải trọng xe")
    void reassignBus_Success() {
        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .ticketCode("EXC-100-01")
                .busNumber("BUS-01")
                .groupName("Nhóm 1")
                .active(true)
                .build();
        booking.setId(905L);

        when(excursionRepository.findById(100L)).thenReturn(Optional.of(excursion));
        when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(905L, 100L)).thenReturn(Optional.of(booking));
        when(vehicleRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(vehicle1, vehicle2));
        when(passengerRepository.countByExcursionIdAndBusNumberAndActiveTrue(100L, "BUS-02")).thenReturn(5L);

        ReassignBusResponse res = bookingService.reassignBus(100L, 905L, new ReassignBusRequest("BUS-02", "Nhóm 2"));

        assertThat(res.getOldBusNumber()).isEqualTo("BUS-01");
        assertThat(res.getNewBusNumber()).isEqualTo("BUS-02");
        assertThat(booking.getBusNumber()).isEqualTo("BUS-02");
        verify(bookingLogRepository, times(1)).save(any(ExcursionBookingLog.class));
    }

    @Test
    @DisplayName("Tra cứu danh sách tour cá nhân đã đặt (getMyBookings)")
    void getMyBookings_Success() {
        ExcursionPassenger booking = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerId(200L)
                .passengerName("Trần Gia Huy")
                .cabinNumber("Suite-801")
                .ticketCode("EXC-100-111")
                .busNumber("BUS-01")
                .groupName("Nhóm 1")
                .chargedAmount(new BigDecimal("120.00"))
                .isIncluded(false)
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        booking.setId(906L);

        when(passengerRepository.findByPassengerIdAndActiveTrue(200L)).thenReturn(List.of(booking));
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(excursion));

        List<PassengerExcursionBookingDto> list = bookingService.getMyBookings(200L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTicketCode()).isEqualTo("EXC-100-111");
        assertThat(list.get(0).getExcursionTitle()).isEqualTo(excursion.getTitle());
    }
}
