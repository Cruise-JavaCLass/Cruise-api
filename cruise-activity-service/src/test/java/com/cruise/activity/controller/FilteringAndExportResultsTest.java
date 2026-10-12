package com.cruise.activity.controller;

import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite kiểm thử tích hợp chuyên sâu cho Lọc và Xuất Dữ liệu Hành khách (KAN-150)
 * Kiểm chứng toàn diện:
 * 1. Lọc danh sách theo trạng thái đăng ký (RegistrationStatus: CONFIRMED, PENDING, CANCELLED).
 * 2. Tìm kiếm phân trang kết hợp keyword và filter status.
 * 3. Xuất file CSV toàn bộ hành khách của tour (kiểm tra header BOM UTF-8, định dạng cột, dữ liệu các hàng).
 * 4. Xuất file CSV có lọc theo RegistrationStatus (chỉ chứa các hành khách có status khớp).
 * 5. Xuất file CSV khi danh sách rỗng (chỉ có dòng Header + BOM UTF-8).
 * 6. Escape dữ liệu đặc biệt trong file CSV (dấu phẩy, dấu nháy kép, ký tự xuống dòng).
 * 7. Kiểm tra Content-Type, Content-Disposition header của response CSV.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Filtering and Export Results End-to-End Tests (KAN-150)")
class FilteringAndExportResultsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PassengerRepository passengerRepository;

    private static final Long TOUR_ID = 555L;
    private static final Long EMPTY_TOUR_ID = 888L;

    @BeforeEach
    void setUp() {
        passengerRepository.deleteAll();

        // 1. CONFIRMED passenger - Standard
        Passenger p1 = Passenger.builder()
                .tourId(TOUR_ID)
                .fullName("Trần Đình Trọng")
                .email("trong.tran@cruise.com")
                .phoneNumber("0912345678")
                .cabinNumber("Suite-101")
                .rfidTag("RFID-55501")
                .qrCode("QR-55501")
                .tourPackage("All-Inclusive Diamond")
                .bleZone("Sky Lounge")
                .registrationStatus(RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();

        // 2. CONFIRMED passenger with special characters needing CSV escaping
        Passenger p2 = Passenger.builder()
                .tourId(TOUR_ID)
                .fullName("Vũ Hoàng, \"Nam\"")
                .email("nam.vu@cruise.com")
                .phoneNumber("0987654321")
                .cabinNumber("Balcony-202\r\nSpecial")
                .rfidTag("RFID-55502")
                .qrCode("QR-55502")
                .tourPackage("Premium, Ocean View")
                .bleZone("Deck 9 Pool, Bar")
                .registrationStatus(RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        // 3. PENDING passenger
        Passenger p3 = Passenger.builder()
                .tourId(TOUR_ID)
                .fullName("Lê Ngọc Bích")
                .email("bich.le@cruise.com")
                .phoneNumber("0934567890")
                .cabinNumber("Interior-303")
                .rfidTag("RFID-55503")
                .qrCode("QR-55503")
                .tourPackage("Standard Saver")
                .bleZone("Restaurant Horizon")
                .registrationStatus(RegistrationStatus.PENDING)
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        // 4. CANCELLED passenger
        Passenger p4 = Passenger.builder()
                .tourId(TOUR_ID)
                .fullName("Đỗ Minh Quân")
                .email("quan.do@cruise.com")
                .phoneNumber("0945678901")
                .cabinNumber("Ocean-404")
                .rfidTag("RFID-55504")
                .qrCode("QR-55504")
                .tourPackage("Family Delight")
                .bleZone("Kids Club")
                .registrationStatus(RegistrationStatus.CANCELLED)
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        // 5. Inactive / Soft-deleted passenger (không được xuất hiện trong filter hay export)
        Passenger p5 = Passenger.builder()
                .tourId(TOUR_ID)
                .fullName("Nguyễn Thị Xóa")
                .email("xoa.nguyen@cruise.com")
                .phoneNumber("0956789012")
                .cabinNumber("Suite-999")
                .rfidTag("RFID-99999")
                .qrCode("QR-99999")
                .tourPackage("VIP Exclusive")
                .bleZone("Casino Royale")
                .registrationStatus(RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.BOARDED)
                .active(false)
                .build();

        passengerRepository.save(p1);
        passengerRepository.save(p2);
        passengerRepository.save(p3);
        passengerRepository.save(p4);
        passengerRepository.save(p5);
    }

    @Nested
    @DisplayName("1. Kiểm tra kết quả lọc danh sách (Filtering Results Tests)")
    class FilteringResultsTests {

        @Test
        @DisplayName("Lọc danh sách theo CONFIRMED: Chỉ trả về các hành khách có trạng thái CONFIRMED và active=true")
        void filterByConfirmedStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", TOUR_ID)
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[*].fullName", containsInAnyOrder("Trần Đình Trọng", "Vũ Hoàng, \"Nam\"")))
                    .andExpect(jsonPath("$.data[*].registrationStatus", everyItem(is("CONFIRMED"))));
        }

        @Test
        @DisplayName("Lọc danh sách theo PENDING: Chỉ trả về hành khách PENDING")
        void filterByPendingStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", TOUR_ID)
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Lê Ngọc Bích"))
                    .andExpect(jsonPath("$.data[0].registrationStatus").value("PENDING"));
        }

        @Test
        @DisplayName("Lọc danh sách theo CANCELLED: Chỉ trả về hành khách CANCELLED")
        void filterByCancelledStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", TOUR_ID)
                            .param("status", "CANCELLED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Đỗ Minh Quân"))
                    .andExpect(jsonPath("$.data[0].registrationStatus").value("CANCELLED"));
        }

        @Test
        @DisplayName("Lọc phân trang: Kết hợp tìm kiếm keyword cabinNumber và lọc status CONFIRMED")
        void filterAndSearchPagedResults() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", TOUR_ID)
                            .param("keyword", "Suite-101")
                            .param("status", "CONFIRMED")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].fullName").value("Trần Đình Trọng"))
                    .andExpect(jsonPath("$.data.content[0].cabinNumber").value("Suite-101"));
        }

        @Test
        @DisplayName("Lọc phân trang không khớp: Keyword đúng nhưng status khác -> Trả về 0 kết quả")
        void filterAndSearchPagedNoMatch() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", TOUR_ID)
                            .param("keyword", "Trần Đình Trọng")
                            .param("status", "CANCELLED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("2. Kiểm tra kết quả xuất file CSV (Export Results Tests)")
    class ExportResultsTests {

        @Test
        @DisplayName("Xuất CSV toàn bộ tour: Chứa BOM UTF-8, header chuẩn và tất cả 4 hành khách active")
        void exportAllPassengersToCsv() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", TOUR_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"passengers_tour_555.csv\""))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8"))
                    .andReturn();

            byte[] contentBytes = result.getResponse().getContentAsByteArray();
            assertThat(contentBytes).isNotEmpty();

            String csvText = new String(contentBytes, StandardCharsets.UTF_8);

            // 1. Kiểm tra UTF-8 BOM
            assertThat(csvText).startsWith("\ufeff");

            // 2. Kiểm tra Header
            assertThat(csvText).contains("ID,Tour ID,Họ và Tên,Số Cabin,Mã Thẻ RFID,Mã QR,Gói Tour,Vị Trí BLE,Trạng Thái Check-in,Thời Gian Lên Tàu");

            // 3. Kiểm tra dữ liệu hành khách active xuất hiện
            assertThat(csvText).contains("Trần Đình Trọng");
            assertThat(csvText).contains("RFID-55501");
            assertThat(csvText).contains("Lê Ngọc Bích");
            assertThat(csvText).contains("Đỗ Minh Quân");

            // 4. Kiểm tra dữ liệu hành khách bị xóa mềm không xuất hiện
            assertThat(csvText).doesNotContain("Nguyễn Thị Xóa");
            assertThat(csvText).doesNotContain("RFID-99999");
        }

        @Test
        @DisplayName("Xuất CSV có lọc status CONFIRMED: Chỉ chứa hành khách CONFIRMED")
        void exportPassengersFilteredByConfirmedStatus() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", TOUR_ID)
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"passengers_tour_555.csv\""))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8"))
                    .andReturn();

            String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

            assertThat(csvText).startsWith("\ufeff");
            assertThat(csvText).contains("Trần Đình Trọng");
            assertThat(csvText).contains("RFID-55501");

            // Không được chứa các hành khách PENDING hoặc CANCELLED
            assertThat(csvText).doesNotContain("Lê Ngọc Bích");
            assertThat(csvText).doesNotContain("Đỗ Minh Quân");
            assertThat(csvText).doesNotContain("Nguyễn Thị Xóa");
        }

        @Test
        @DisplayName("Xuất CSV có lọc status PENDING: Chỉ chứa hành khách PENDING")
        void exportPassengersFilteredByPendingStatus() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", TOUR_ID)
                            .param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andReturn();

            String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

            assertThat(csvText).contains("Lê Ngọc Bích");
            assertThat(csvText).contains("RFID-55503");
            assertThat(csvText).doesNotContain("Trần Đình Trọng");
            assertThat(csvText).doesNotContain("Đỗ Minh Quân");
        }

        @Test
        @DisplayName("Xuất CSV có lọc status CANCELLED: Chỉ chứa hành khách CANCELLED")
        void exportPassengersFilteredByCancelledStatus() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", TOUR_ID)
                            .param("status", "CANCELLED"))
                    .andExpect(status().isOk())
                    .andReturn();

            String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

            assertThat(csvText).contains("Đỗ Minh Quân");
            assertThat(csvText).contains("RFID-55504");
            assertThat(csvText).doesNotContain("Trần Đình Trọng");
            assertThat(csvText).doesNotContain("Lê Ngọc Bích");
        }

        @Test
        @DisplayName("Xuất CSV xử lý chính xác ký tự đặc biệt cần escape (dấu phẩy, dấu nháy kép, xuống dòng)")
        void exportCsvEscapesSpecialCharactersCorrectly() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", TOUR_ID)
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andReturn();

            String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

            // Kiểm tra escape dấu nháy kép và phẩy trong họ tên: Vũ Hoàng, "Nam" -> "Vũ Hoàng, ""Nam"""
            assertThat(csvText).contains("\"Vũ Hoàng, \"\"Nam\"\"\"");

            // Kiểm tra escape xuống dòng trong số cabin: Balcony-202\r\nSpecial -> "Balcony-202\r\nSpecial"
            assertThat(csvText).contains("\"Balcony-202\r\nSpecial\"");

            // Kiểm tra escape dấu phẩy trong gói tour: Premium, Ocean View -> "Premium, Ocean View"
            assertThat(csvText).contains("\"Premium, Ocean View\"");

            // Kiểm tra escape dấu phẩy trong vùng BLE: Deck 9 Pool, Bar -> "Deck 9 Pool, Bar"
            assertThat(csvText).contains("\"Deck 9 Pool, Bar\"");
        }

        @Test
        @DisplayName("Xuất CSV khi tour không có hành khách: Chỉ sinh ra BOM UTF-8 và dòng Header")
        void exportCsvForEmptyTour() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/export/csv", EMPTY_TOUR_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"passengers_tour_888.csv\""))
                    .andReturn();

            String csvText = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

            assertThat(csvText).startsWith("\ufeff");
            String[] lines = csvText.replace("\ufeff", "").trim().split("\n");
            // Chỉ có đúng 1 dòng là tiêu đề cột
            assertThat(lines).hasSize(1);
            assertThat(lines[0]).isEqualTo("ID,Tour ID,Họ và Tên,Số Cabin,Mã Thẻ RFID,Mã QR,Gói Tour,Vị Trí BLE,Trạng Thái Check-in,Thời Gian Lên Tàu");
        }
    }
}
