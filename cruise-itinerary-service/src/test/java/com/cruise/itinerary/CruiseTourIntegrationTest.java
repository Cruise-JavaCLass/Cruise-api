package com.cruise.itinerary;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("CruiseTour End-to-End Integration Tests")
class CruiseTourIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CruiseTourRepository tourRepository;

    @Autowired
    private CruiseShipRepository shipRepository;

    private CruiseShip testShip;

    @BeforeEach
    void setUp() {
        tourRepository.deleteAll();
        shipRepository.deleteAll();

        // Chuẩn bị tàu du lịch mẫu để gán tour
        testShip = shipRepository.save(CruiseShip.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-HARMONY")
                .operator("Paradise Cruises")
                .capacity(2000)
                .crewCapacity(600)
                .totalCabins(900)
                .totalDecks(12)
                .manufacturingYear(2023)
                .lengthMeters(300.0)
                .beamMeters(35.0)
                .draftMeters(8.0)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Complete Tour Lifecycle: Create -> Get -> Search -> Update -> Patch Status -> Soft Delete -> Hard Delete")
    void shouldHandleCompleteTourLifecycle() throws Exception {
        Long shipId = testShip.getId();

        // 1. Tạo mới tour du lịch (POST)
        String createPayload = String.format("""
                {
                    "shipId": %d,
                    "code": "TOUR-HL-3N2D",
                    "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ",
                    "description": "Tour khám phá vẻ đẹp hang Sửng Sốt và đảo Ti Tốp",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "departurePort": "Cảng tàu khách quốc tế Hạ Long",
                    "destinationPort": "Cảng tàu khách quốc tế Hạ Long",
                    "maxCapacity": 1500,
                    "basePrice": 5500000,
                    "currency": "VND",
                    "status": "DRAFT"
                }
                """, shipId);

        MvcResult createResult = mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-3N2D"))
                .andExpect(jsonPath("$.data.name").value("Hành trình Di sản Vịnh Hạ Long 3N2Đ"))
                .andExpect(jsonPath("$.data.shipId").value(shipId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        Integer createdTourIdInt = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long tourId = createdTourIdInt.longValue();

        // 2. Lấy thông tin tour theo ID (GET /{id})
        mockMvc.perform(get("/api/itinerary/tours/" + tourId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tourId))
                .andExpect(jsonPath("$.data.maxCapacity").value(1500))
                .andExpect(jsonPath("$.data.durationDays").value(3))
                .andExpect(jsonPath("$.data.durationNights").value(2));

        // 3. Lấy thông tin tour theo Code (GET /code/{code})
        mockMvc.perform(get("/api/itinerary/tours/code/TOUR-HL-3N2D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-3N2D"))
                .andExpect(jsonPath("$.data.shipName").value("Halong Grand Harmony"));

        // 4. Tìm kiếm tour trong danh sách (GET /api/itinerary/tours)
        mockMvc.perform(get("/api/itinerary/tours")
                        .param("keyword", "Di sản")
                        .param("shipId", String.valueOf(shipId))
                        .param("status", "DRAFT")
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("TOUR-HL-3N2D"));

        // Lấy danh sách tour đang active (GET /api/itinerary/tours/active)
        mockMvc.perform(get("/api/itinerary/tours/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(tourId));

        // 5. Cập nhật thông tin tour (PUT /{id})
        String updatePayload = String.format("""
                {
                    "shipId": %d,
                    "code": "TOUR-HL-3N2D-VIP",
                    "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ VIP",
                    "description": "Tour nâng cấp dịch vụ 5 sao bao gồm trọn gói dịch vụ",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "departurePort": "Cảng tàu khách quốc tế Hạ Long",
                    "destinationPort": "Cảng tàu khách quốc tế Hạ Long",
                    "maxCapacity": 1600,
                    "basePrice": 6500000,
                    "currency": "VND",
                    "status": "PUBLISHED"
                }
                """, shipId);

        mockMvc.perform(put("/api/itinerary/tours/" + tourId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin tour du lịch thành công"))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-3N2D-VIP"))
                .andExpect(jsonPath("$.data.name").value("Hành trình Di sản Vịnh Hạ Long 3N2Đ VIP"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 6. Cập nhật trạng thái tour (PATCH /{id}/status)
        String statusPayload = """
                {
                    "status": "IN_PROGRESS"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/tours/" + tourId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 7. Vô hiệu hóa (Soft Delete) tour (DELETE /{id})
        mockMvc.perform(delete("/api/itinerary/tours/" + tourId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã hủy kích hoạt tour du lịch thành công"));

        CruiseTour softDeleted = tourRepository.findById(tourId).orElseThrow();
        assertThat(softDeleted.getActive()).isFalse();

        // 8. Xóa vĩnh viễn (Hard Delete) tour (DELETE /{id}?hard=true)
        mockMvc.perform(delete("/api/itinerary/tours/" + tourId).param("hard", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xóa vĩnh viễn tour du lịch khỏi hệ thống"));

        assertThat(tourRepository.findById(tourId)).isEmpty();

        // Sau khi xóa, truy vấn trả về 404 Not Found
        mockMvc.perform(get("/api/itinerary/tours/" + tourId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should return 409 Conflict when creating tour with existing code")
    void shouldReturn409WhenDuplicateTourCode() throws Exception {
        // Tạo tour đầu tiên trực tiếp
        tourRepository.save(CruiseTour.builder()
                .ship(testShip)
                .code("TOUR-EXISTING")
                .name("Tour hiện có")
                .departurePort("Cảng Hạ Long")
                .destinationPort("Cảng Hạ Long")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 2))
                .durationDays(2)
                .durationNights(1)
                .maxCapacity(500)
                .basePrice(BigDecimal.valueOf(2000000))
                .status(TourStatus.DRAFT)
                .active(true)
                .build());

        String duplicatePayload = String.format("""
                {
                    "shipId": %d,
                    "code": "TOUR-EXISTING",
                    "name": "Tour trùng mã khác tên",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 2,
                    "durationNights": 1,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-02",
                    "maxCapacity": 500,
                    "basePrice": 2000000
                }
                """, testShip.getId());

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tour 'TOUR-EXISTING' đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when creating tour with non-existent ship")
    void shouldReturn404WhenShipNotFound() throws Exception {
        String payload = """
                {
                    "shipId": 99999,
                    "code": "TOUR-NO-SHIP",
                    "name": "Tour không có tàu",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 2,
                    "durationNights": 1,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-02",
                    "maxCapacity": 500,
                    "basePrice": 2000000
                }
                """;

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 99999"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when tour maxCapacity exceeds ship capacity")
    void shouldReturn400WhenMaxCapacityExceedsShipCapacity() throws Exception {
        // Sức chứa của testShip là 2000, cấu hình tour 2500
        String payload = String.format("""
                {
                    "shipId": %d,
                    "code": "TOUR-OVER-CAP",
                    "name": "Tour vượt sức chứa",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 2,
                    "durationNights": 1,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-02",
                    "maxCapacity": 2500,
                    "basePrice": 2000000
                }
                """, testShip.getId());

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sức chứa của tour (2500) không thể vượt quá sức chứa tối đa của tàu Halong Grand Harmony (2000)"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when end date is before start date")
    void shouldReturn400WhenEndDateBeforeStartDate() throws Exception {
        String payload = String.format("""
                {
                    "shipId": %d,
                    "code": "TOUR-INVALID-DATES",
                    "name": "Tour ngày không hợp lệ",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "maxCapacity": 1000,
                    "basePrice": 2000000,
                    "startDate": "2026-11-10",
                    "endDate": "2026-11-05"
                }
                """, testShip.getId());

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ngày kết thúc tour không thể trước ngày bắt đầu"));
    }
}
