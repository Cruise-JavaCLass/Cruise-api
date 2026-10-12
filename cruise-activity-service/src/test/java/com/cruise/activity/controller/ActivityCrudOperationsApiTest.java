package com.cruise.activity.controller;

import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.repository.OnboardActivityRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Activity CRUD Operations API Integration Tests (KAN-138)")
class ActivityCrudOperationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardActivityRepository activityRepository;

    private Long tourId;
    private OnboardActivity seededActivity;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();

        tourId = 500L;

        seededActivity = OnboardActivity.builder()
                .tourId(tourId)
                .dayNumber(1)
                .title("Workshop Nấu ăn Ẩm thực Ý")
                .description("Học làm pasta tươi cùng bếp trưởng")
                .category("Culinary")
                .location("Culinary Studio (Deck 8)")
                .activityDate(LocalDate.of(2026, 11, 10))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .maxCapacity(25)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(180000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        seededActivity = activityRepository.save(seededActivity);
    }

    @Nested
    @DisplayName("1. POST /api/activities - Create Activity API Tests")
    class CreateActivityApiTests {

        @Test
        @DisplayName("Tạo mới hoạt động thành công (201 Created) với đầy đủ thông tin hợp lệ")
        void createActivity_Success_FullPayload() throws Exception {
            String jsonPayload = """
                    {
                        "tourId": 500,
                        "dayNumber": 2,
                        "title": "Chinh phục Tường Leo núi",
                        "description": "Thử thách thể thao mạo hiểm ngoài trời",
                        "category": "Sports",
                        "location": "Rock Climbing Deck (Deck 15)",
                        "activityDate": "2026-11-11",
                        "startTime": "09:00:00",
                        "endTime": "11:30:00",
                        "maxCapacity": 40,
                        "price": 0,
                        "isIncluded": true,
                        "status": "SCHEDULED"
                    }
                    """;

            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Tạo hoạt động thành công"))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.tourId").value(500))
                    .andExpect(jsonPath("$.data.dayNumber").value(2))
                    .andExpect(jsonPath("$.data.title").value("Chinh phục Tường Leo núi"))
                    .andExpect(jsonPath("$.data.category").value("Sports"))
                    .andExpect(jsonPath("$.data.location").value("Rock Climbing Deck (Deck 15)"))
                    .andExpect(jsonPath("$.data.activityDate").value("2026-11-11"))
                    .andExpect(jsonPath("$.data.startTime").value("09:00:00"))
                    .andExpect(jsonPath("$.data.endTime").value("11:30:00"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(40))
                    .andExpect(jsonPath("$.data.currentParticipants").value(0))
                    .andExpect(jsonPath("$.data.isIncluded").value(true))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

            assertThat(activityRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Tạo mới hoạt động áp dụng các giá trị mặc định cho category, capacity, price khi không gửi")
        void createActivity_Success_DefaultValues() throws Exception {
            String jsonPayload = """
                    {
                        "tourId": 500,
                        "title": "Buổi hòa nhạc Hoàng hôn",
                        "location": "Aft Lounge",
                        "activityDate": "2026-11-10",
                        "startTime": "17:00:00",
                        "endTime": "18:30:00"
                    }
                    """;

            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.category").value("General"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(50))
                    .andExpect(jsonPath("$.data.price").value(0))
                    .andExpect(jsonPath("$.data.isIncluded").value(true))
                    .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi thiếu các trường bắt buộc (validation constraint)")
        void createActivity_ValidationError_MissingRequiredFields() throws Exception {
            String invalidPayload = """
                    {
                        "tourId": null,
                        "title": "",
                        "location": "  ",
                        "activityDate": null,
                        "startTime": null,
                        "endTime": null
                    }
                    """;

            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi giờ kết thúc không sau giờ bắt đầu")
        void createActivity_BusinessRuleError_EndTimeNotAfterStartTime() throws Exception {
            String jsonPayload = """
                    {
                        "tourId": 500,
                        "title": "Tiệc Đêm Trăng",
                        "location": "Poolside",
                        "activityDate": "2026-11-10",
                        "startTime": "21:00:00",
                        "endTime": "20:00:00"
                    }
                    """;

            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Thời gian kết thúc phải sau thời gian bắt đầu"));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi xung đột địa điểm và thời gian với hoạt động đã có")
        void createActivity_BusinessRuleError_ScheduleConflict() throws Exception {
            // seededActivity: Culinary Studio, 2026-11-10, 14:00 - 16:00
            String conflictingPayload = """
                    {
                        "tourId": 500,
                        "title": "Lớp học Làm Bánh Pastry",
                        "location": "Culinary Studio (Deck 8)",
                        "activityDate": "2026-11-10",
                        "startTime": "15:00:00",
                        "endTime": "17:00:00"
                    }
                    """;

            mockMvc.perform(post("/api/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(conflictingPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Xung đột lịch trình: Địa điểm 'Culinary Studio (Deck 8)'")));
        }
    }

    @Nested
    @DisplayName("2. GET /api/activities/{id} - Get Activity Detail API Tests")
    class GetActivityDetailApiTests {

        @Test
        @DisplayName("Lấy chi tiết hoạt động thành công (200 OK)")
        void getActivityById_Success() throws Exception {
            mockMvc.perform(get("/api/activities/{id}", seededActivity.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(seededActivity.getId()))
                    .andExpect(jsonPath("$.data.title").value("Workshop Nấu ăn Ẩm thực Ý"))
                    .andExpect(jsonPath("$.data.category").value("Culinary"))
                    .andExpect(jsonPath("$.data.location").value("Culinary Studio (Deck 8)"));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi ID hoạt động không tồn tại")
        void getActivityById_NotFound() throws Exception {
            mockMvc.perform(get("/api/activities/{id}", 99999L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy hoạt động với ID: 99999")));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi hoạt động đã bị xóa mềm (active = false)")
        void getActivityById_InactiveActivity_NotFound() throws Exception {
            seededActivity.setActive(false);
            activityRepository.save(seededActivity);

            mockMvc.perform(get("/api/activities/{id}", seededActivity.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("3. PUT /api/activities/{id} - Update Activity API Tests")
    class UpdateActivityApiTests {

        @Test
        @DisplayName("Cập nhật toàn diện hoạt động thành công (200 OK)")
        void updateActivity_Success() throws Exception {
            String updatePayload = """
                    {
                        "dayNumber": 1,
                        "title": "Masterclass Nấu ăn Ý & Rượu vang",
                        "description": "Phiên bản nâng cấp kèm thử nếm 5 loại rượu vang hảo hạng",
                        "category": "Culinary & Wine",
                        "location": "Culinary Studio (Deck 8)",
                        "activityDate": "2026-11-10",
                        "startTime": "14:00:00",
                        "endTime": "16:30:00",
                        "maxCapacity": 30,
                        "price": 250000,
                        "isIncluded": false,
                        "status": "SCHEDULED"
                    }
                    """;

            mockMvc.perform(put("/api/activities/{id}", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Cập nhật hoạt động thành công"))
                    .andExpect(jsonPath("$.data.id").value(seededActivity.getId()))
                    .andExpect(jsonPath("$.data.title").value("Masterclass Nấu ăn Ý & Rượu vang"))
                    .andExpect(jsonPath("$.data.category").value("Culinary & Wine"))
                    .andExpect(jsonPath("$.data.endTime").value("16:30:00"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(30))
                    .andExpect(jsonPath("$.data.price").value(250000));

            OnboardActivity updated = activityRepository.findById(seededActivity.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Masterclass Nấu ăn Ý & Rượu vang");
            assertThat(updated.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(250000));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi payload cập nhật rỗng hoặc sai dữ liệu validation")
        void updateActivity_ValidationError_EmptyFields() throws Exception {
            String invalidPayload = """
                    {
                        "title": "  ",
                        "location": "",
                        "activityDate": "2026-11-10",
                        "startTime": "14:00:00",
                        "endTime": "16:00:00"
                    }
                    """;

            mockMvc.perform(put("/api/activities/{id}", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi cập nhật hoạt động có ID không tồn tại")
        void updateActivity_NotFound() throws Exception {
            String updatePayload = """
                    {
                        "title": "Hoạt động bất kỳ",
                        "location": "Lounge",
                        "activityDate": "2026-11-10",
                        "startTime": "08:00:00",
                        "endTime": "09:00:00"
                    }
                    """;

            mockMvc.perform(put("/api/activities/{id}", 88888L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy hoạt động với ID: 88888")));
        }
    }

    @Nested
    @DisplayName("4. PATCH /api/activities/{id}/time-location - Configure Time and Location API Tests")
    class ConfigureTimeAndLocationApiTests {

        @Test
        @DisplayName("Cấu hình thời gian và địa điểm thành công (200 OK)")
        void configureTimeAndLocation_Success() throws Exception {
            String patchPayload = """
                    {
                        "location": "Gourmet Kitchen VIP",
                        "activityDate": "2026-11-10",
                        "startTime": "15:30:00",
                        "endTime": "17:30:00"
                    }
                    """;

            mockMvc.perform(patch("/api/activities/{id}/time-location", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Cấu hình thời gian và địa điểm thành công"))
                    .andExpect(jsonPath("$.data.location").value("Gourmet Kitchen VIP"))
                    .andExpect(jsonPath("$.data.startTime").value("15:30:00"))
                    .andExpect(jsonPath("$.data.endTime").value("17:30:00"));

            OnboardActivity updated = activityRepository.findById(seededActivity.getId()).orElseThrow();
            assertThat(updated.getLocation()).isEqualTo("Gourmet Kitchen VIP");
            assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(15, 30));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi cấu hình địa điểm trống")
        void configureTimeAndLocation_BlankLocation() throws Exception {
            String patchPayload = """
                    {
                        "location": "   ",
                        "startTime": "15:30:00",
                        "endTime": "17:30:00"
                    }
                    """;

            mockMvc.perform(patch("/api/activities/{id}/time-location", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("5. PATCH /api/activities/{id}/status - Activity Status API Tests")
    class ActivityStatusApiTests {

        @Test
        @DisplayName("Chuyển trạng thái sang IN_PROGRESS thành công (200 OK)")
        void updateStatus_ToInProgress_Success() throws Exception {
            String statusPayload = """
                    {
                        "status": "IN_PROGRESS"
                    }
                    """;

            mockMvc.perform(patch("/api/activities/{id}/status", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("Chuyển trạng thái sang CANCELLED và ghi nhận cancellationReason thành công")
        void updateStatus_ToCancelled_WithReason_Success() throws Exception {
            String statusPayload = """
                    {
                        "status": "CANCELLED",
                        "cancellationReason": "Bếp trưởng gặp sự cố sức khỏe đột xuất"
                    }
                    """;

            mockMvc.perform(patch("/api/activities/{id}/status", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancellationReason").value("Bếp trưởng gặp sự cố sức khỏe đột xuất"));
        }

        @Test
        @DisplayName("Trả về 400 Bad Request khi chuyển trạng thái vi phạm quy tắc State Machine (SCHEDULED -> COMPLETED)")
        void updateStatus_InvalidTransition() throws Exception {
            String statusPayload = """
                    {
                        "status": "COMPLETED"
                    }
                    """;

            mockMvc.perform(patch("/api/activities/{id}/status", seededActivity.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không thể chuyển trạng thái hoạt động")));
        }

        @Test
        @DisplayName("GET /api/activities/{id}/status/allowed-transitions - Tra cứu trạng thái hợp lệ tiếp theo")
        void getAllowedTransitions_Success() throws Exception {
            mockMvc.perform(get("/api/activities/{id}/status/allowed-transitions", seededActivity.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasItems("IN_PROGRESS", "CANCELLED")));
        }
    }

    @Nested
    @DisplayName("6. DELETE /api/activities/{id} - Delete Activity API Tests")
    class DeleteActivityApiTests {

        @Test
        @DisplayName("Xóa hoạt động thành công qua Soft Delete (200 OK)")
        void deleteActivity_Success() throws Exception {
            mockMvc.perform(delete("/api/activities/{id}", seededActivity.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Xóa hoạt động thành công"));

            OnboardActivity deleted = activityRepository.findById(seededActivity.getId()).orElseThrow();
            assertThat(deleted.getActive()).isFalse();
            assertThat(deleted.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        }

        @Test
        @DisplayName("Trả về 404 Not Found khi xóa hoạt động có ID không tồn tại")
        void deleteActivity_NotFound() throws Exception {
            mockMvc.perform(delete("/api/activities/{id}", 77777L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Không tìm thấy hoạt động với ID: 77777")));
        }
    }
}
