package com.cruise.activity.controller;

import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.repository.ShoreExcursionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bộ kiểm thử tích hợp API toàn diện cho các thao tác CRUD và quy trình Tour bờ (Shore Excursion CRUD Operations API Integration Tests - KAN-171).
 * Bao gồm kiểm thử End-to-End từ Web Layer qua Service đến Database Repository:
 * 1. POST /api/excursions - Tạo mới tour bờ (thành công, validation, business rule violations).
 * 2. GET /api/excursions/{id} - Xem chi tiết tour bờ (thành công, 404 khi không tìm thấy hoặc đã xóa mềm).
 * 3. GET /api/excursions/tour/{tourId}... - Truy vấn danh sách theo tour, cảng ghé, ngày, trạng thái và phân trang.
 * 4. PUT /api/excursions/{id} - Cập nhật toàn diện tour bờ (thành công, validation, 404).
 * 5. PATCH /api/excursions/{id}/price-capacity-schedule - Cấu hình chuyên biệt giá, sức chứa, lịch trình.
 * 6. PATCH /api/excursions/{id}/status & GET allowed-transitions - Quản lý trạng thái và danh sách trạng thái cho phép.
 * 7. DELETE /api/excursions/{id} - Xóa mềm tour bờ (active=false, status=CANCELLED, 404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Shore Excursion CRUD Operations API Integration Tests (KAN-171)")
class ExcursionCrudOperationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShoreExcursionRepository excursionRepository;

    private Long tourId;
    private Long portId;
    private ShoreExcursion seededExcursion;

    @BeforeEach
    void setUp() {
        excursionRepository.deleteAll();

        tourId = 800L;
        portId = 900L;

        seededExcursion = ShoreExcursion.builder()
                .tourId(tourId)
                .portId(portId)
                .dayNumber(2)
                .title("Khám phá Hang Đầu Gỗ & Hòn Gà Chọi")
                .description("Hành trình khám phá vẻ đẹp kỳ vĩ của Vịnh Hạ Long")
                .portName("Cảng Quốc Tế Tuần Châu")
                .meetingPoint("Cầu tàu số 3")
                .localProvider("Ha Long Heritage Travel")
                .providerContact("+84 905 123 456")
                .excursionDate(LocalDate.of(2026, 11, 20))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(10)
                .price(BigDecimal.valueOf(850000))
                .childPrice(BigDecimal.valueOf(450000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();

        seededExcursion = excursionRepository.save(seededExcursion);
    }

    @Nested
    @DisplayName("1. POST /api/excursions - Create Excursion API Tests")
    class CreateExcursionApiTests {

        @Test
        @DisplayName("Tạo mới tour bờ thành công (201 Created) với đầy đủ thông tin hợp lệ")
        void createExcursion_Success() throws Exception {
            String createJson = """
                    {
                        "tourId": 800,
                        "portId": 900,
                        "dayNumber": 2,
                        "title": "Chèo Thuyền Kayak Làng Chài Ba Hang",
                        "description": "Trải nghiệm chèo kayak qua các hang luồn kỳ bí",
                        "portName": "Cảng Quốc Tế Tuần Châu",
                        "meetingPoint": "Cầu cảng A",
                        "localProvider": "Kayak Halong Club",
                        "providerContact": "+84 988 777 666",
                        "excursionDate": "2026-11-20",
                        "gatheringTime": "13:00:00",
                        "departureTime": "13:30:00",
                        "returnToShipTime": "17:00:00",
                        "maxCapacity": 30,
                        "price": 600000,
                        "childPrice": 300000,
                        "currency": "VND",
                        "isIncluded": false
                    }
                    """;

            mockMvc.perform(post("/api/excursions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Tạo tour bờ thành công"))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.tourId").value(800))
                    .andExpect(jsonPath("$.data.portId").value(900))
                    .andExpect(jsonPath("$.data.title").value("Chèo Thuyền Kayak Làng Chài Ba Hang"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(30))
                    .andExpect(jsonPath("$.data.remainingCapacity").value(30))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

            assertThat(excursionRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi thiếu trường bắt buộc (@NotNull / @NotBlank validation)")
        void createExcursion_ValidationError_MissingRequiredFields() throws Exception {
            String invalidJson = """
                    {
                        "tourId": null,
                        "title": "",
                        "portName": "  ",
                        "gatheringTime": null
                    }
                    """;

            mockMvc.perform(post("/api/excursions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi giờ khởi hành không sau giờ tập trung")
        void createExcursion_BusinessRuleError_DepartureBeforeGathering() throws Exception {
            String invalidScheduleJson = """
                    {
                        "tourId": 800,
                        "portId": 900,
                        "dayNumber": 2,
                        "title": "Tour Biển Đảo Ngắm Hoàng Hôn",
                        "portName": "Cảng Tuần Châu",
                        "meetingPoint": "Cầu cảng 1",
                        "excursionDate": "2026-11-20",
                        "gatheringTime": "15:00:00",
                        "departureTime": "14:30:00",
                        "returnToShipTime": "18:00:00",
                        "maxCapacity": 40,
                        "price": 500000
                    }
                    """;

            mockMvc.perform(post("/api/excursions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidScheduleJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Giờ khởi hành phải sau giờ tập trung")));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi trùng lặp tour bờ tại cùng cảng vào cùng ngày")
        void createExcursion_BusinessRuleError_DuplicateTitle() throws Exception {
            String duplicateJson = """
                    {
                        "tourId": 800,
                        "portId": 900,
                        "dayNumber": 2,
                        "title": "Khám phá Hang Đầu Gỗ & Hòn Gà Chọi",
                        "portName": "Cảng Quốc Tế Tuần Châu",
                        "meetingPoint": "Cầu tàu số 3",
                        "excursionDate": "2026-11-20",
                        "gatheringTime": "09:00:00",
                        "departureTime": "09:30:00",
                        "returnToShipTime": "13:00:00",
                        "maxCapacity": 40,
                        "price": 850000
                    }
                    """;

            mockMvc.perform(post("/api/excursions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(duplicateJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("đã tồn tại tại cảng này vào ngày 2026-11-20")));
        }
    }

    @Nested
    @DisplayName("2. GET /api/excursions/{id} - Get Excursion Detail API Tests")
    class GetExcursionDetailApiTests {

        @Test
        @DisplayName("Lấy chi tiết tour bờ thành công (200 OK) và tính đúng remainingCapacity")
        void getExcursionById_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(seededExcursion.getId()))
                    .andExpect(jsonPath("$.data.title").value("Khám phá Hang Đầu Gỗ & Hòn Gà Chọi"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(50))
                    .andExpect(jsonPath("$.data.currentParticipants").value(10))
                    .andExpect(jsonPath("$.data.remainingCapacity").value(40)) // 50 - 10
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi ID tour bờ không tồn tại")
        void getExcursionById_NotFound() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}", 99999L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy tour bờ với ID: 99999")));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi tour bờ đã bị xóa mềm (active = false)")
        void getExcursionById_Inactive_NotFound() throws Exception {
            seededExcursion.setActive(false);
            excursionRepository.save(seededExcursion);

            mockMvc.perform(get("/api/excursions/{id}", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("3. GET Queries - Query Excursions by Tour, Port, Day, Status & Paged")
    class QueryExcursionsApiTests {

        @Test
        @DisplayName("GET /api/excursions/tour/{tourId} - Lấy danh sách tour bờ theo chuyến hải trình")
        void getExcursionsByTour_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/{tourId}", tourId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(seededExcursion.getId()));
        }

        @Test
        @DisplayName("GET /api/excursions/tour/{tourId}/port/{portId} - Lấy danh sách tour bờ theo cảng")
        void getExcursionsByPort_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/{tourId}/port/{portId}", tourId, portId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/excursions/tour/{tourId}/day/{dayNumber} - Lấy danh sách tour bờ theo ngày")
        void getExcursionsByDay_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/{tourId}/day/{dayNumber}", tourId, 2)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/excursions/tour/{tourId}/status/{status} - Lấy danh sách tour bờ theo trạng thái")
        void getExcursionsByStatus_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/{tourId}/status/{status}", tourId, "SCHEDULED")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("GET /api/excursions/tour/{tourId}/paged - Phân trang và tìm kiếm tour bờ theo từ khóa")
        void getExcursionsPaged_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/tour/{tourId}/paged", tourId)
                            .param("keyword", "Hang Đầu Gỗ")
                            .param("page", "0")
                            .param("size", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("4. PUT /api/excursions/{id} - Update Excursion API Tests")
    class UpdateExcursionApiTests {

        @Test
        @DisplayName("Cập nhật toàn diện tour bờ thành công (200 OK)")
        void updateExcursion_Success() throws Exception {
            String updateJson = """
                    {
                        "portId": 900,
                        "dayNumber": 2,
                        "title": "Khám phá Hang Đầu Gỗ & Động Thiên Cung VIP",
                        "description": "Bổ sung hướng dẫn viên tiếng Anh và xe điện đưa đón",
                        "portName": "Cảng Quốc Tế Tuần Châu",
                        "meetingPoint": "Cầu cảng VIP số 1",
                        "localProvider": "Ha Long Heritage Travel",
                        "providerContact": "+84 905 123 456",
                        "excursionDate": "2026-11-20",
                        "gatheringTime": "08:15:00",
                        "departureTime": "08:45:00",
                        "returnToShipTime": "12:30:00",
                        "maxCapacity": 60,
                        "price": 1050000,
                        "childPrice": 500000,
                        "currency": "VND",
                        "isIncluded": false
                    }
                    """;

            mockMvc.perform(put("/api/excursions/{id}", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("Khám phá Hang Đầu Gỗ & Động Thiên Cung VIP"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(60))
                    .andExpect(jsonPath("$.data.price").value(1050000));

            ShoreExcursion updated = excursionRepository.findById(seededExcursion.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Khám phá Hang Đầu Gỗ & Động Thiên Cung VIP");
            assertThat(updated.getMaxCapacity()).isEqualTo(60);
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi cập nhật tour bờ có ID không tồn tại")
        void updateExcursion_NotFound() throws Exception {
            String updateJson = """
                    {
                        "portId": 900,
                        "dayNumber": 2,
                        "title": "Tour Bất Kỳ",
                        "portName": "Cảng Tuần Châu",
                        "meetingPoint": "Cầu cảng 1",
                        "excursionDate": "2026-11-20",
                        "gatheringTime": "08:00:00",
                        "departureTime": "08:30:00",
                        "returnToShipTime": "12:00:00",
                        "maxCapacity": 50,
                        "price": 800000
                    }
                    """;

            mockMvc.perform(put("/api/excursions/{id}", 88888L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy tour bờ với ID: 88888")));
        }
    }

    @Nested
    @DisplayName("5. PATCH /api/excursions/{id}/price-capacity-schedule - Price, Capacity and Schedule API Tests")
    class ConfigurePriceCapacityAndScheduleApiTests {

        @Test
        @DisplayName("Cấu hình giá, sức chứa và lịch trình thành công (200 OK)")
        void configurePriceCapacityAndSchedule_Success() throws Exception {
            String patchJson = """
                    {
                        "gatheringTime": "07:30:00",
                        "departureTime": "08:00:00",
                        "returnToShipTime": "12:00:00",
                        "maxCapacity": 45,
                        "price": 990000,
                        "childPrice": 490000
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/price-capacity-schedule", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.maxCapacity").value(45))
                    .andExpect(jsonPath("$.data.price").value(990000))
                    .andExpect(jsonPath("$.data.gatheringTime").value("07:30:00"))
                    .andExpect(jsonPath("$.data.departureTime").value("08:00:00"))
                    .andExpect(jsonPath("$.data.remainingCapacity").value(35)); // 45 - 10
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi giảm sức chứa nhỏ hơn số khách đã đăng ký (currentParticipants = 10)")
        void configurePriceCapacityAndSchedule_CapacityTooLow() throws Exception {
            String patchJson = """
                    {
                        "gatheringTime": "08:00:00",
                        "departureTime": "08:30:00",
                        "returnToShipTime": "12:00:00",
                        "maxCapacity": 8,
                        "price": 850000
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/price-capacity-schedule", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Sức chứa tối đa (8) không được nhỏ hơn số người đã đăng ký hiện tại (10)")));
        }
    }

    @Nested
    @DisplayName("6. PATCH /api/excursions/{id}/status - Status Management API Tests")
    class ExcursionStatusApiTests {

        @Test
        @DisplayName("Chuyển trạng thái sang OPEN_FOR_BOOKING thành công (200 OK)")
        void updateStatus_ToOpenForBooking_Success() throws Exception {
            String statusJson = """
                    {
                        "status": "OPEN_FOR_BOOKING"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/status", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("OPEN_FOR_BOOKING"));
        }

        @Test
        @DisplayName("Chuyển trạng thái sang CANCELLED và ghi nhận cancellationReason")
        void updateStatus_ToCancelled_WithReason_Success() throws Exception {
            String statusJson = """
                    {
                        "status": "CANCELLED",
                        "cancellationReason": "Bão biển cấp 8 tràn vào vịnh Bắc Bộ"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/status", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancellationReason").value("Bão biển cấp 8 tràn vào vịnh Bắc Bộ"));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi chuyển trạng thái vi phạm quy tắc State Machine (SCHEDULED -> COMPLETED)")
        void updateStatus_InvalidTransition() throws Exception {
            String statusJson = """
                    {
                        "status": "COMPLETED"
                    }
                    """;

            mockMvc.perform(patch("/api/excursions/{id}/status", seededExcursion.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không thể chuyển trạng thái tour bờ")));
        }

        @Test
        @DisplayName("GET /api/excursions/{id}/status/allowed-transitions - Tra cứu trạng thái hợp lệ tiếp theo")
        void getAllowedTransitions_Success() throws Exception {
            mockMvc.perform(get("/api/excursions/{id}/status/allowed-transitions", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasItems("OPEN_FOR_BOOKING", "DELAYED", "CANCELLED")));
        }
    }

    @Nested
    @DisplayName("7. DELETE /api/excursions/{id} - Delete Excursion API Tests")
    class DeleteExcursionApiTests {

        @Test
        @DisplayName("Xóa tour bờ thành công qua Soft Delete (active=false, status=CANCELLED)")
        void deleteExcursion_Success() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}", seededExcursion.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Xóa tour bờ thành công"));

            ShoreExcursion deleted = excursionRepository.findById(seededExcursion.getId()).orElseThrow();
            assertThat(deleted.getActive()).isFalse();
            assertThat(deleted.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi xóa tour bờ có ID không tồn tại")
        void deleteExcursion_NotFound() throws Exception {
            mockMvc.perform(delete("/api/excursions/{id}", 77777L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy tour bờ với ID: 77777")));
        }
    }
}
