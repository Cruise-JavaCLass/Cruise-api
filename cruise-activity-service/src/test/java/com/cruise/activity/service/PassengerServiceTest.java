package com.cruise.activity.service;

import com.cruise.activity.dto.PassengerResponse;
import com.cruise.activity.dto.RegisterPassengerRequest;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Optional;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ShoreExcursion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassengerService Unit Tests (KAN-146)")
class PassengerServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private com.cruise.activity.repository.ExcursionPassengerRepository excursionPassengerRepository;

    @Mock
    private com.cruise.activity.repository.ShoreExcursionRepository shoreExcursionRepository;

    @InjectMocks
    private PassengerService passengerService;

    private Passenger samplePassenger;
    private RegisterPassengerRequest registerRequest;

    @BeforeEach
    void setUp() {
        samplePassenger = Passenger.builder()
                .tourId(1L)
                .fullName("Nguyễn Văn Minh")
                .email("minh.nguyen@example.com")
                .phoneNumber("0901234567")
                .cabinNumber("402")
                .rfidTag("RFID-99412")
                .qrCode("QR-99412")
                .tourPackage("All-Inclusive VIP")
                .bleZone("Zone A - Sky Lounge")
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();
        samplePassenger.setId(100L);

        registerRequest = RegisterPassengerRequest.builder()
                .tourId(1L)
                .fullName("Nguyễn Văn Minh")
                .email("minh.nguyen@example.com")
                .phoneNumber("0901234567")
                .cabinNumber("402")
                .rfidTag("RFID-99412")
                .qrCode("QR-99412")
                .tourPackage("All-Inclusive VIP")
                .bleZone("Zone A - Sky Lounge")
                .checkInStatus(CheckInStatus.BOARDED)
                .build();
    }

    @Test
    @DisplayName("registerPassenger - Đăng ký thông tin hành khách thành công")
    void registerPassengerSuccess() {
        when(passengerRepository.save(any(Passenger.class))).thenReturn(samplePassenger);

        PassengerResponse response = passengerService.registerPassenger(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn Minh");
        assertThat(response.getCabinNumber()).isEqualTo("402");
        assertThat(response.getRfidTag()).isEqualTo("RFID-99412");
        assertThat(response.getTourPackage()).isEqualTo("All-Inclusive VIP");
        verify(passengerRepository, times(1)).save(any(Passenger.class));
    }

    @Test
    @DisplayName("getRegisteredPassengersByTour - Lấy danh sách hành khách theo tour")
    void getRegisteredPassengersByTourSuccess() {
        when(passengerRepository.findByTourIdAndActiveTrue(1L)).thenReturn(List.of(samplePassenger));

        List<PassengerResponse> responses = passengerService.getRegisteredPassengersByTour(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCabinNumber()).isEqualTo("402");
    }

    @Test
    @DisplayName("getRegisteredPassengersByTourPaged - Tìm kiếm và phân trang danh sách hành khách có filter status")
    void getRegisteredPassengersByTourPagedSuccess() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Passenger> page = new PageImpl<>(List.of(samplePassenger), pageable, 1);

        when(passengerRepository.searchPassengersWithStatus(1L, "Minh", com.cruise.activity.enums.RegistrationStatus.CONFIRMED, pageable))
                .thenReturn(page);

        Page<PassengerResponse> result = passengerService.getRegisteredPassengersByTourPaged(
                1L, "Minh", com.cruise.activity.enums.RegistrationStatus.CONFIRMED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFullName()).isEqualTo("Nguyễn Văn Minh");
        assertThat(result.getContent().get(0).getRegistrationStatus()).isEqualTo(com.cruise.activity.enums.RegistrationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("getRegisteredPassengersByTourAndStatus - Lọc hành khách theo status")
    void getRegisteredPassengersByTourAndStatusSuccess() {
        when(passengerRepository.findByTourIdAndRegistrationStatusAndActiveTrue(1L, com.cruise.activity.enums.RegistrationStatus.WAITLIST))
                .thenReturn(List.of(samplePassenger));

        List<PassengerResponse> responses = passengerService.getRegisteredPassengersByTourAndStatus(
                1L, com.cruise.activity.enums.RegistrationStatus.WAITLIST);

        assertThat(responses).hasSize(1);
        verify(passengerRepository, times(1)).findByTourIdAndRegistrationStatusAndActiveTrue(1L, com.cruise.activity.enums.RegistrationStatus.WAITLIST);
    }

    @Test
    @DisplayName("getRegisteredPassengersByTourAndStatus - Không truyền status trả về toàn bộ")
    void getRegisteredPassengersByTourAndStatusNull() {
        when(passengerRepository.findByTourIdAndActiveTrue(1L)).thenReturn(List.of(samplePassenger));

        List<PassengerResponse> responses = passengerService.getRegisteredPassengersByTourAndStatus(1L, null);

        assertThat(responses).hasSize(1);
        verify(passengerRepository, times(1)).findByTourIdAndActiveTrue(1L);
    }

    @Test
    @DisplayName("getPassengerById - Lấy chi tiết hành khách theo ID")
    void getPassengerByIdSuccess() {
        when(passengerRepository.findById(100L)).thenReturn(Optional.of(samplePassenger));

        PassengerResponse response = passengerService.getPassengerById(100L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getPassengerById - Ném lỗi khi ID không tồn tại")
    void getPassengerByIdNotFound() {
        when(passengerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passengerService.getPassengerById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    @DisplayName("getPassengerByRfidTag - Tra cứu hành khách theo thẻ RFID")
    void getPassengerByRfidTagSuccess() {
        when(passengerRepository.findByRfidTagAndActiveTrue("RFID-99412")).thenReturn(Optional.of(samplePassenger));

        PassengerResponse response = passengerService.getPassengerByRfidTag("RFID-99412");

        assertThat(response).isNotNull();
        assertThat(response.getRfidTag()).isEqualTo("RFID-99412");
    }

    @Test
    @DisplayName("updateCheckInStatus - Cập nhật trạng thái check-in thành công")
    void updateCheckInStatusSuccess() {
        when(passengerRepository.findById(100L)).thenReturn(Optional.of(samplePassenger));
        when(passengerRepository.save(any(Passenger.class))).thenReturn(samplePassenger);

        PassengerResponse response = passengerService.updateCheckInStatus(100L, CheckInStatus.BOARDED);

        assertThat(response).isNotNull();
        verify(passengerRepository, times(1)).save(samplePassenger);
    }

    @Test
    @DisplayName("updateRegistrationStatus - Cập nhật trạng thái đăng ký thành công")
    void updateRegistrationStatusSuccess() {
        when(passengerRepository.findById(100L)).thenReturn(Optional.of(samplePassenger));
        when(passengerRepository.save(any(Passenger.class))).thenReturn(samplePassenger);

        PassengerResponse response = passengerService.updateRegistrationStatus(100L, com.cruise.activity.enums.RegistrationStatus.CANCELLED);

        assertThat(response).isNotNull();
        verify(passengerRepository, times(1)).save(samplePassenger);
    }

    @Test
    @DisplayName("exportRegisteredPassengersCsv - Xuất danh sách hành khách dạng CSV có BOM UTF-8")
    void exportRegisteredPassengersCsvSuccess() {
        when(passengerRepository.findByTourIdAndActiveTrue(1L)).thenReturn(List.of(samplePassenger));

        byte[] csvBytes = passengerService.exportRegisteredPassengersCsv(1L);

        assertThat(csvBytes).isNotEmpty();
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).startsWith("\ufeff");
        assertThat(csvContent).contains("ID,Tour ID,Họ và Tên,Số Cabin,Mã Thẻ RFID,Mã QR,Gói Tour,Vị Trí BLE,Trạng Thái Check-in,Thời Gian Lên Tàu");
        assertThat(csvContent).contains("Nguyễn Văn Minh");
        assertThat(csvContent).contains("402");
        assertThat(csvContent).contains("RFID-99412");
        assertThat(csvContent).contains("All-Inclusive VIP");
    }

    @Test
    @DisplayName("exportRegisteredPassengersCsv - Xử lý ký tự đặc biệt cần escape CSV và các trường null")
    void exportRegisteredPassengersCsvWithSpecialCharactersAndNulls() {
        Passenger passengerSpecial = Passenger.builder()
                .tourId(1L)
                .fullName("Nguyễn \"Văn\", Nam\r\nTest")
                .cabinNumber(null)
                .rfidTag(null)
                .qrCode(null)
                .tourPackage(null)
                .bleZone(null)
                .checkInStatus(null)
                .boardedAt(null)
                .active(true)
                .build();
        passengerSpecial.setId(2L);

        when(passengerRepository.findByTourIdAndActiveTrue(1L)).thenReturn(List.of(passengerSpecial));

        byte[] csvBytes = passengerService.exportRegisteredPassengersCsv(1L);

        assertThat(csvBytes).isNotEmpty();
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).contains("\"Nguyễn \"\"Văn\"\", Nam\r\nTest\"");
    }

    @Test
    @DisplayName("exportRegisteredPassengersCsv - Lọc xuất danh sách theo trạng thái RegistrationStatus")
    void exportRegisteredPassengersCsvWithStatusSuccess() {
        when(passengerRepository.findByTourIdAndRegistrationStatusAndActiveTrue(1L, com.cruise.activity.enums.RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(samplePassenger));

        byte[] csvBytes = passengerService.exportRegisteredPassengersCsv(1L, com.cruise.activity.enums.RegistrationStatus.CONFIRMED);

        assertThat(csvBytes).isNotEmpty();
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvContent).contains("Nguyễn Văn Minh");
        verify(passengerRepository, times(1)).findByTourIdAndRegistrationStatusAndActiveTrue(1L, com.cruise.activity.enums.RegistrationStatus.CONFIRMED);
    }

    /**
     * Verifies the summary retains passenger details and reports no excursions
     * when both cabin and passenger-name lookups return no bookings.
     */
    @Test
    @DisplayName("getPassengerBookingSummary - Lấy thông tin tổng quan booking thành công")
    void getPassengerBookingSummarySuccess() {
        when(passengerRepository.findById(100L)).thenReturn(Optional.of(samplePassenger));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("402")).thenReturn(List.of());
        when(excursionPassengerRepository.findByPassengerNameIgnoreCaseAndActiveTrue("Nguyễn Văn Minh")).thenReturn(List.of());

        com.cruise.activity.dto.PassengerBookingSummaryResponse summary = passengerService.getPassengerBookingSummary(100L);

        assertThat(summary).isNotNull();
        assertThat(summary.getPassengerId()).isEqualTo(100L);
        assertThat(summary.getFullName()).isEqualTo("Nguyễn Văn Minh");
        assertThat(summary.getCabinNumber()).isEqualTo("402");
        assertThat(summary.getTotalRegisteredExcursions()).isZero();
    }

    /**
     * Verifies a summary request for a missing passenger raises ResourceNotFoundException.
     */
    @Test
    @DisplayName("getPassengerBookingSummary - Báo lỗi khi không tìm thấy hành khách")
    void getPassengerBookingSummaryNotFound() {
        when(passengerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passengerService.getPassengerBookingSummary(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getPassengerBookingSummary - Tính đúng tổng chi phí, map tour và phân loại các loại cabin")
    void getPassengerBookingSummaryWithExcursionsAndCabinCategories() {
        Passenger suitePassenger = Passenger.builder()
                .tourId(1L)
                .fullName("Lê Hoàng Nam")
                .email("nam@cruise.com")
                .phoneNumber("0912345678")
                .cabinNumber("Suite 1205")
                .rfidTag("RFID-SUITE")
                .qrCode("QR-SUITE")
                .tourPackage("VIP")
                .bleZone("Deck 12")
                .registrationStatus(com.cruise.activity.enums.RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.BOARDED)
                .boardedAt(java.time.Instant.now())
                .active(true)
                .build();
        suitePassenger.setId(101L);

        when(passengerRepository.findById(101L)).thenReturn(Optional.of(suitePassenger));

        ExcursionPassenger ep1 = ExcursionPassenger.builder()
                .excursionId(501L)
                .passengerName("Lê Hoàng Nam")
                .cabinNumber("Suite 1205")
                .ticketCode("TCK-501")
                .groupName("Đoàn VIP")
                .busNumber("BUS-01")
                .status(com.cruise.activity.enums.ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();

        ExcursionPassenger ep2 = ExcursionPassenger.builder()
                .excursionId(502L)
                .passengerName("Lê Hoàng Nam")
                .cabinNumber("Suite 1205")
                .ticketCode("TCK-502")
                .groupName(null)
                .busNumber(null)
                .status(null)
                .active(true)
                .build();

        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("Suite 1205")).thenReturn(List.of(ep1, ep2));

        ShoreExcursion exc1 = ShoreExcursion.builder()
                .title("Tour Trực Thăng Vịnh Hạ Long")
                .price(new BigDecimal("2500000"))
                .isIncluded(false)
                .portName("Cảng Hạ Long")
                .gatheringTime(java.time.LocalTime.of(8, 0))
                .returnToShipTime(java.time.LocalTime.of(12, 0))
                .build();

        ShoreExcursion exc2 = ShoreExcursion.builder()
                .title("Tour Thuyền Kayak (Đã bao gồm)")
                .price(new BigDecimal("500000"))
                .isIncluded(true)
                .portName("Vịnh Lan Hạ")
                .build();

        when(shoreExcursionRepository.findById(501L)).thenReturn(Optional.of(exc1));
        when(shoreExcursionRepository.findById(502L)).thenReturn(Optional.of(exc2));

        com.cruise.activity.dto.PassengerBookingSummaryResponse summary = passengerService.getPassengerBookingSummary(101L);

        assertThat(summary).isNotNull();
        assertThat(summary.getCabinCategory()).isEqualTo("Royal Suite");
        assertThat(summary.getTotalRegisteredExcursions()).isEqualTo(2);
        assertThat(summary.getTotalExcursionSpent()).isEqualByComparingTo(new BigDecimal("2500000"));
        assertThat(summary.getRegisteredExcursions()).hasSize(2);
        assertThat(summary.getRegisteredExcursions().get(0).getExcursionTitle()).isEqualTo("Tour Trực Thăng Vịnh Hạ Long");
        assertThat(summary.getRegisteredExcursions().get(1).getGroupName()).isEqualTo("Chưa xếp nhóm");
        assertThat(summary.getRegisteredExcursions().get(1).getBusNumber()).isEqualTo("Chưa phân xe");
        assertThat(summary.getRegisteredExcursions().get(1).getStatus()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("getPassengerBookingSummary - Kiểm tra fallback tên và phân loại các loại cabin khác")
    void getPassengerBookingSummaryCabinFallbacks() {
        Passenger balconyPassenger = Passenger.builder()
                .tourId(1L)
                .fullName("Trần Thị Mai")
                .cabinNumber("Balcony 301")
                .active(true)
                .build();
        balconyPassenger.setId(102L);

        when(passengerRepository.findById(102L)).thenReturn(Optional.of(balconyPassenger));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("Balcony 301")).thenReturn(List.of());

        ExcursionPassenger ep = ExcursionPassenger.builder()
                .excursionId(999L)
                .passengerName("Trần Thị Mai")
                .active(true)
                .build();
        when(excursionPassengerRepository.findByPassengerNameIgnoreCaseAndActiveTrue("Trần Thị Mai")).thenReturn(List.of(ep));
        when(shoreExcursionRepository.findById(999L)).thenReturn(Optional.empty());

        com.cruise.activity.dto.PassengerBookingSummaryResponse summary = passengerService.getPassengerBookingSummary(102L);

        assertThat(summary.getCabinCategory()).isEqualTo("Balcony Stateroom");
        assertThat(summary.getRegisteredExcursions()).hasSize(1);
        assertThat(summary.getRegisteredExcursions().get(0).getExcursionTitle()).isEqualTo("Tour bờ #999");
        assertThat(summary.getTotalExcursionSpent()).isEqualByComparingTo(BigDecimal.ZERO);

        // Test Oceanview and null cabin
        Passenger oceanviewPassenger = Passenger.builder().cabinNumber("Oceanview 204").active(true).build();
        oceanviewPassenger.setId(103L);
        when(passengerRepository.findById(103L)).thenReturn(Optional.of(oceanviewPassenger));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue("Oceanview 204")).thenReturn(List.of());
        when(excursionPassengerRepository.findByPassengerNameIgnoreCaseAndActiveTrue(any())).thenReturn(List.of());

        com.cruise.activity.dto.PassengerBookingSummaryResponse sOcean = passengerService.getPassengerBookingSummary(103L);
        assertThat(sOcean.getCabinCategory()).isEqualTo("Oceanview Stateroom");

        Passenger nullCabin = Passenger.builder().cabinNumber(null).active(true).build();
        nullCabin.setId(104L);
        when(passengerRepository.findById(104L)).thenReturn(Optional.of(nullCabin));
        when(excursionPassengerRepository.findByCabinNumberAndActiveTrue(null)).thenReturn(List.of());
        when(excursionPassengerRepository.findByPassengerNameIgnoreCaseAndActiveTrue(any())).thenReturn(List.of());

        com.cruise.activity.dto.PassengerBookingSummaryResponse sNull = passengerService.getPassengerBookingSummary(104L);
        assertThat(sNull.getCabinCategory()).isEqualTo("Standard Cabin");
    }
}
