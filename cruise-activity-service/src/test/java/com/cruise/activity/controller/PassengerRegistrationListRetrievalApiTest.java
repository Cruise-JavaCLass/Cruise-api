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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Passenger Registration List Retrieval API Integration Tests (KAN-149)")
class PassengerRegistrationListRetrievalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PassengerRepository passengerRepository;

    private Long tourId1;
    private Long tourId2;
    private Passenger p1;
    private Passenger p2;
    private Passenger p3;
    private Passenger p4;

    @BeforeEach
    void setUp() {
        passengerRepository.deleteAll();

        tourId1 = 100L;
        tourId2 = 200L;

        p1 = Passenger.builder()
                .tourId(tourId1)
                .fullName("Nguyễn Văn An")
                .email("an.nguyen@example.com")
                .phoneNumber("0901111222")
                .cabinNumber("A101")
                .rfidTag("RFID-A101")
                .qrCode("QR-A101")
                .tourPackage("VIP Premium")
                .bleZone("Deck 5 Lounge")
                .registrationStatus(RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();

        p2 = Passenger.builder()
                .tourId(tourId1)
                .fullName("Trần Thị Bình")
                .email("binh.tran@example.com")
                .phoneNumber("0903333444")
                .cabinNumber("B202")
                .rfidTag("RFID-B202")
                .qrCode("QR-B202")
                .tourPackage("Standard")
                .bleZone("Deck 4 Restaurant")
                .registrationStatus(RegistrationStatus.PENDING)
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        p3 = Passenger.builder()
                .tourId(tourId1)
                .fullName("Lê Hoàng Cường")
                .email("cuong.le@example.com")
                .phoneNumber("0905555666")
                .cabinNumber("C303")
                .rfidTag("RFID-C303")
                .qrCode("QR-C303")
                .tourPackage("Deluxe Ocean")
                .bleZone("Deck 6 Pool")
                .registrationStatus(RegistrationStatus.CANCELLED)
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .active(true)
                .build();

        p4 = Passenger.builder()
                .tourId(tourId2)
                .fullName("Phạm Minh Dũng")
                .email("dung.pham@example.com")
                .phoneNumber("0907777888")
                .cabinNumber("D404")
                .rfidTag("RFID-D404")
                .qrCode("QR-D404")
                .tourPackage("VIP Suite")
                .bleZone("Deck 7 Helipad")
                .registrationStatus(RegistrationStatus.CONFIRMED)
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();

        p1 = passengerRepository.save(p1);
        p2 = passengerRepository.save(p2);
        p3 = passengerRepository.save(p3);
        p4 = passengerRepository.save(p4);
    }

    @Nested
    @DisplayName("GET /api/activities/passengers/tour/{tourId} - List Retrieval Tests")
    class GetRegisteredPassengersByTourTests {

        @Test
        @DisplayName("Lấy tất cả hành khách của một tour khi không truyền param status")
        void shouldRetrieveAllActivePassengersForGivenTour() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", tourId1)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Lấy danh sách hành khách đăng ký theo tour thành công"))
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[*].fullName", containsInAnyOrder("Nguyễn Văn An", "Trần Thị Bình", "Lê Hoàng Cường")))
                    .andExpect(jsonPath("$.data[*].tourId", everyItem(is(tourId1.intValue()))));
        }

        @Test
        @DisplayName("Lọc danh sách hành khách theo RegistrationStatus CONFIRMED")
        void shouldFilterPassengersByConfirmedStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", tourId1)
                            .param("status", "CONFIRMED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Nguyễn Văn An"))
                    .andExpect(jsonPath("$.data[0].registrationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data[0].cabinNumber").value("A101"));
        }

        @Test
        @DisplayName("Lọc danh sách hành khách theo RegistrationStatus PENDING")
        void shouldFilterPassengersByPendingStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", tourId1)
                            .param("status", "PENDING")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Trần Thị Bình"))
                    .andExpect(jsonPath("$.data[0].registrationStatus").value("PENDING"));
        }

        @Test
        @DisplayName("Lọc danh sách hành khách theo RegistrationStatus CANCELLED")
        void shouldFilterPassengersByCancelledStatus() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", tourId1)
                            .param("status", "CANCELLED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].fullName").value("Lê Hoàng Cường"))
                    .andExpect(jsonPath("$.data[0].registrationStatus").value("CANCELLED"));
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi tour không có hành khách nào")
        void shouldReturnEmptyListWhenTourHasNoPassengers() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", 9999L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Không trả về hành khách đã bị soft-delete (active = false)")
        void shouldNotReturnSoftDeletedPassengers() throws Exception {
            p1.setActive(false);
            passengerRepository.save(p1);

            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}", tourId1)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[*].fullName", not(hasItem("Nguyễn Văn An"))));
        }
    }

    @Nested
    @DisplayName("GET /api/activities/passengers/tour/{tourId}/paged - Paged and Search Tests")
    class GetRegisteredPassengersByTourPagedTests {

        @Test
        @DisplayName("Phân trang mặc định page=0, size=10, sortBy=cabinNumber asc")
        void shouldRetrieveDefaultPagedPassengersSortedByCabinNumberAsc() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.content", hasSize(3)))
                    .andExpect(jsonPath("$.data.content[0].cabinNumber").value("A101"))
                    .andExpect(jsonPath("$.data.content[1].cabinNumber").value("B202"))
                    .andExpect(jsonPath("$.data.content[2].cabinNumber").value("C303"));
        }

        @Test
        @DisplayName("Phân trang với sắp xếp desc theo cabinNumber")
        void shouldRetrievePagedPassengersSortedDesc() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("sortBy", "cabinNumber")
                            .param("sortDir", "desc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].cabinNumber").value("C303"))
                    .andExpect(jsonPath("$.data.content[1].cabinNumber").value("B202"))
                    .andExpect(jsonPath("$.data.content[2].cabinNumber").value("A101"));
        }

        @Test
        @DisplayName("Tìm kiếm theo keyword tên hành khách")
        void shouldSearchPassengersByFullNameKeyword() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("keyword", "Trần Thị")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].fullName").value("Trần Thị Bình"));
        }

        @Test
        @DisplayName("Tìm kiếm theo keyword cabinNumber")
        void shouldSearchPassengersByCabinNumberKeyword() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("keyword", "A101")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].cabinNumber").value("A101"))
                    .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyễn Văn An"));
        }

        @Test
        @DisplayName("Tìm kiếm theo keyword rfidTag")
        void shouldSearchPassengersByRfidKeyword() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("keyword", "RFID-B202")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].rfidTag").value("RFID-B202"));
        }

        @Test
        @DisplayName("Kết hợp tìm kiếm keyword và lọc status CONFIRMED")
        void shouldCombineSearchKeywordAndStatusFilter() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("keyword", "Nguyễn")
                            .param("status", "CONFIRMED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyễn Văn An"));

            // Tìm kiếm keyword khớp tên nhưng status khác
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("keyword", "Nguyễn")
                            .param("status", "CANCELLED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("Phân trang có giới hạn size và số trang")
        void shouldPaginateCorrectlyWithSizeAndPage() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("page", "0")
                            .param("size", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.content", hasSize(2)));

            mockMvc.perform(get("/api/activities/passengers/tour/{tourId}/paged", tourId1)
                            .param("page", "1")
                            .param("size", "2")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/activities/passengers/{id} & /rfid/{rfidTag} - Passenger Details and Lookup Tests")
    class PassengerDetailLookupTests {

        @Test
        @DisplayName("Lấy chi tiết hành khách theo ID thành công")
        void shouldGetPassengerByIdSuccessfully() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/{id}", p1.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(p1.getId()))
                    .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"))
                    .andExpect(jsonPath("$.data.email").value("an.nguyen@example.com"))
                    .andExpect(jsonPath("$.data.tourPackage").value("VIP Premium"));
        }

        @Test
        @DisplayName("Trả về 404 NOT_FOUND khi tìm theo ID không tồn tại")
        void shouldReturn404WhenPassengerIdNotFound() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/{id}", 88888L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("88888")));
        }

        @Test
        @DisplayName("Tra cứu hành khách theo mã thẻ RFID thành công")
        void shouldLookupPassengerByRfidTagSuccessfully() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/rfid/{rfidTag}", "RFID-A101")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.rfidTag").value("RFID-A101"))
                    .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"));
        }

        @Test
        @DisplayName("Trả về 404 NOT_FOUND khi tra cứu RFID không tồn tại")
        void shouldReturn404WhenRfidTagNotFound() throws Exception {
            mockMvc.perform(get("/api/activities/passengers/rfid/{rfidTag}", "RFID-UNKNOWN")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("RFID-UNKNOWN")));
        }
    }
}
