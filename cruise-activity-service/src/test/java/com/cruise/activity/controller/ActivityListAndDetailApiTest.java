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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Activity List and Detail API Integration Tests (KAN-135)")
class ActivityListAndDetailApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardActivityRepository activityRepository;

    private Long tourId1;
    private Long tourId2;
    private OnboardActivity a1;
    private OnboardActivity a2;
    private OnboardActivity a3;
    private OnboardActivity a4;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();

        tourId1 = 101L;
        tourId2 = 102L;

        // Activity 1: Day 1, Morning Yoga, Tour 101
        a1 = OnboardActivity.builder()
                .tourId(tourId1)
                .dayNumber(1)
                .title("Yoga Bình Minh")
                .description("Buổi tập thể dục sáng sớm trên boong tàu")
                .category("Wellness")
                .location("Sundeck 12")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .maxCapacity(30)
                .currentParticipants(12)
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        // Activity 2: Day 1, Evening Gala Dinner, Tour 101
        a2 = OnboardActivity.builder()
                .tourId(tourId1)
                .dayNumber(1)
                .title("Dạ tiệc Hoàng Gia Gala")
                .description("Bữa tối ẩm thực 5 sao kết hợp hòa nhạc thính phòng")
                .category("Dining")
                .location("Grand Dining Hall")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(18, 30))
                .endTime(LocalTime.of(21, 30))
                .maxCapacity(250)
                .currentParticipants(180)
                .price(BigDecimal.valueOf(250000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        // Activity 3: Day 2, Sunset Wine Tasting, Tour 101
        a3 = OnboardActivity.builder()
                .tourId(tourId1)
                .dayNumber(2)
                .title("Thưởng rượu Vang Hoàng Hôn")
                .description("Nếm thử 5 dòng vang hảo hạng cùng chuyên gia")
                .category("Entertainment")
                .location("Sky Lounge (Tầng 14)")
                .activityDate(LocalDate.of(2026, 11, 2))
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(18, 30))
                .maxCapacity(40)
                .currentParticipants(25)
                .price(BigDecimal.valueOf(150000))
                .isIncluded(false)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        // Activity 4: Tour 102
        a4 = OnboardActivity.builder()
                .tourId(tourId2)
                .dayNumber(1)
                .title("Lớp học Nấu ăn Ẩm thực Việt")
                .description("Học làm phở cuốn và chả giò hải sản")
                .category("Workshop")
                .location("Culinary Studio")
                .activityDate(LocalDate.of(2026, 11, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .maxCapacity(20)
                .currentParticipants(8)
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .build();

        a1 = activityRepository.save(a1);
        a2 = activityRepository.save(a2);
        a3 = activityRepository.save(a3);
        a4 = activityRepository.save(a4);
    }

    @Nested
    @DisplayName("GET /api/activities/{id} - Lấy chi tiết hoạt động")
    class GetActivityDetailTests {

        @Test
        @DisplayName("Trả về chi tiết hoạt động thành công khi ID tồn tại")
        void getActivityById_Success() throws Exception {
            mockMvc.perform(get("/api/activities/{id}", a1.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(a1.getId()))
                    .andExpect(jsonPath("$.data.title").value("Yoga Bình Minh"))
                    .andExpect(jsonPath("$.data.location").value("Sundeck 12"))
                    .andExpect(jsonPath("$.data.category").value("Wellness"))
                    .andExpect(jsonPath("$.data.maxCapacity").value(30))
                    .andExpect(jsonPath("$.data.currentParticipants").value(12))
                    .andExpect(jsonPath("$.data.isIncluded").value(true));
        }

        @Test
        @DisplayName("Trả về 404 khi ID hoạt động không tồn tại")
        void getActivityById_NotFound() throws Exception {
            mockMvc.perform(get("/api/activities/{id}", 999999L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("Không tìm thấy hoạt động")));
        }

        @Test
        @DisplayName("Không trả về hoạt động đã bị xóa mềm (active = false)")
        void getActivityById_SoftDeleted_NotFound() throws Exception {
            a1.setActive(false);
            activityRepository.save(a1);

            mockMvc.perform(get("/api/activities/{id}", a1.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/activities/tour/{tourId} - Lấy danh sách hoạt động theo tour")
    class GetActivitiesByTourTests {

        @Test
        @DisplayName("Trả về toàn bộ danh sách hoạt động của tour được sắp xếp theo ngày và giờ bắt đầu")
        void getActivitiesByTour_Success() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}", tourId1)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[0].id").value(a1.getId()))
                    .andExpect(jsonPath("$.data[1].id").value(a2.getId()))
                    .andExpect(jsonPath("$.data[2].id").value(a3.getId()));
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi tour không có hoạt động nào")
        void getActivitiesByTour_EmptyList() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}", 9999L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/activities/tour/{tourId}/day/{dayNumber} - Lấy danh sách hoạt động theo tour và ngày")
    class GetActivitiesByTourAndDayTests {

        @Test
        @DisplayName("Trả về đúng các hoạt động trong Ngày 1 của Tour 101")
        void getActivitiesByTourAndDay_Day1_Success() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/day/{dayNumber}", tourId1, 1)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].title").value("Yoga Bình Minh"))
                    .andExpect(jsonPath("$.data[1].title").value("Dạ tiệc Hoàng Gia Gala"));
        }

        @Test
        @DisplayName("Trả về đúng các hoạt động trong Ngày 2 của Tour 101")
        void getActivitiesByTourAndDay_Day2_Success() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/day/{dayNumber}", tourId1, 2)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].title").value("Thưởng rượu Vang Hoàng Hôn"));
        }

        @Test
        @DisplayName("Trả về mảng rỗng khi ngày không có hoạt động nào")
        void getActivitiesByTourAndDay_DayNotFound() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/day/{dayNumber}", tourId1, 5)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/activities/tour/{tourId}/paged - Phân trang, tìm kiếm và lọc danh sách hoạt động")
    class GetActivitiesPagedTests {

        @Test
        @DisplayName("Phân trang mặc định trang 0 kích thước 10")
        void getActivitiesPaged_Default() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/paged", tourId1)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.content", hasSize(3)));
        }

        @Test
        @DisplayName("Lọc theo danh mục category (Dining)")
        void getActivitiesPaged_FilterByCategory() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/paged", tourId1)
                            .param("category", "Dining")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("Dạ tiệc Hoàng Gia Gala"));
        }

        @Test
        @DisplayName("Tìm kiếm từ khóa theo tên hoặc địa điểm")
        void getActivitiesPaged_SearchKeyword() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/paged", tourId1)
                            .param("keyword", "Lounge")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].location").value("Sky Lounge (Tầng 14)"));
        }

        @Test
        @DisplayName("Lọc kết hợp theo ngày số (dayNumber) và từ khóa")
        void getActivitiesPaged_FilterDayAndKeyword() throws Exception {
            mockMvc.perform(get("/api/activities/tour/{tourId}/paged", tourId1)
                            .param("dayNumber", "1")
                            .param("keyword", "Yoga")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("Yoga Bình Minh"));
        }
    }
}
