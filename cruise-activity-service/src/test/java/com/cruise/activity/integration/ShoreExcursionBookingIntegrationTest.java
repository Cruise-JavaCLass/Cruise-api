package com.cruise.activity.integration;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.repository.*;
import com.cruise.activity.service.ShoreExcursionBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ShoreExcursionBookingIntegrationTest {

    @Autowired
    private ShoreExcursionBookingService bookingService;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    @Autowired
    private PassengerRepository cruisePassengerRepository;

    @Autowired
    private ExcursionVehicleRepository vehicleRepository;

    @Autowired
    private ExcursionPassengerRepository excursionPassengerRepository;

    @Autowired
    private ExcursionBookingLogRepository bookingLogRepository;

    private Long excursionId;
    private Long passengerId;

    @BeforeEach
    void setUp() {
        ShoreExcursion excursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(1L)
                .dayNumber(2)
                .title("Đà Nẵng & Hội An Ancient Town Tour")
                .portName("Cảng Tiên Sa")
                .meetingPoint("Cửa ra cảng số 1")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(16, 30))
                .maxCapacity(40)
                .currentParticipants(0)
                .price(new BigDecimal("95.00"))
                .currency("USD")
                .isIncluded(false)
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .active(true)
                .build();
        ShoreExcursion savedExc = excursionRepository.save(excursion);
        excursionId = savedExc.getId();

        Passenger passenger = Passenger.builder()
                .tourId(1L)
                .fullName("Nguyễn Văn An")
                .cabinNumber("Balcony-612")
                .email("an.nguyen@cruise.com")
                .phoneNumber("0912345678")
                .rfidTag("RFID-612-01")
                .tourPackage("STANDARD")
                .active(true)
                .build();
        Passenger savedPassenger = cruisePassengerRepository.save(passenger);
        passengerId = savedPassenger.getId();

        ExcursionVehicle vehicle = ExcursionVehicle.builder()
                .excursionId(excursionId)
                .vehicleCode("BUS-DN-01")
                .licensePlate("43B-12345")
                .vehicleType("Xe bus 45 chỗ")
                .driverName("Nguyễn Văn Tài")
                .capacity(30)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicleRepository.save(vehicle);
    }

    @Test
    @Transactional
    @DisplayName("End-to-end: Đặt tour -> Sinh vé EXC -> Tra cứu my bookings -> Chuyển xe -> Hủy tour hoàn tiền")
    void testFullBookingLifecycle() {
        // 1. Book excursion
        ExcursionBookingRequest bookReq = ExcursionBookingRequest.builder()
                .passengerId(passengerId)
                .cabinNumber("Balcony-612")
                .build();
        ExcursionBookingResponse bookResp = bookingService.bookExcursion(excursionId, bookReq);

        assertThat(bookResp).isNotNull();
        assertThat(bookResp.getTicketCode()).startsWith("EXC-" + excursionId + "-");
        assertThat(bookResp.getChargedAmount()).isEqualByComparingTo("95.00");
        assertThat(bookResp.getBusNumber()).isEqualTo("BUS-DN-01");

        Long bookingId = bookResp.getBookingId();

        // 2. Tra cứu my bookings
        List<PassengerExcursionBookingDto> myBookings = bookingService.getMyBookings(passengerId);
        assertThat(myBookings).hasSize(1);
        assertThat(myBookings.get(0).getTicketCode()).isEqualTo(bookResp.getTicketCode());

        // 3. Reassign bus
        ExcursionVehicle vehicle2 = ExcursionVehicle.builder()
                .excursionId(excursionId)
                .vehicleCode("BUS-DN-02")
                .licensePlate("43B-67890")
                .vehicleType("Xe bus 45 chỗ")
                .driverName("Lê Văn Đức")
                .capacity(30)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        vehicleRepository.save(vehicle2);

        ReassignBusResponse reassignResp = bookingService.reassignBus(excursionId, bookingId,
                new ReassignBusRequest("BUS-DN-02", "Nhóm 2"));
        assertThat(reassignResp.getNewBusNumber()).isEqualTo("BUS-DN-02");

        // 4. Cancel booking (trước 24h -> hoàn 100%)
        ExcursionCancellationResponse cancelResp = bookingService.cancelBooking(excursionId, bookingId, "Đổi lịch trình cá nhân");
        assertThat(cancelResp.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelResp.getRefundAmount()).isEqualByComparingTo("95.00");
        assertThat(cancelResp.getPenaltyFee()).isEqualByComparingTo("0.00");

        // 5. Kiểm tra audit log bất biến
        List<ExcursionBookingLog> logs = bookingLogRepository.findByBookingIdOrderByAuditTimeDesc(bookingId);
        assertThat(logs).hasSize(3); // BOOKING_CREATED, BUS_REASSIGNED, BOOKING_CANCELLED
    }
}
