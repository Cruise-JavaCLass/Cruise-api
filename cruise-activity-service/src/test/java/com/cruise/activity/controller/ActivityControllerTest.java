package com.cruise.activity.controller;

import com.cruise.activity.dto.ConfigureTimeAndLocationRequest;
import com.cruise.activity.dto.CreateActivityRequest;
import com.cruise.activity.dto.OnboardActivityResponse;
import com.cruise.activity.enums.ActivityStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.service.OnboardActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ActivityController WebMvc Integration Tests (KAN-144)")
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardActivityService activityService;

    private OnboardActivityResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = OnboardActivityResponse.builder()
                .id(1L)
                .tourId(1L)
                .dayNumber(1)
                .title("Yoga Bình Minh")
                .description("Buổi tập thể dục sáng sớm ngắm bình minh trên biển")
                .category("Wellness")
                .location("Sundeck 12")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .maxCapacity(30)
                .currentParticipants(5)
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .status(ActivityStatus.SCHEDULED)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/activities/health: returns UP status")
    void testActivityHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/activities/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("cruise-activity-service"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("POST /api/activities: creates new activity successfully")
    void createActivity_Success() throws Exception {
        String jsonPayload = """
                {
                    "tourId": 1,
                    "dayNumber": 1,
                    "title": "Yoga Bình Minh",
                    "location": "Sundeck 12",
                    "activityDate": "2026-11-01",
                    "startTime": "06:00:00",
                    "endTime": "07:30:00",
                    "maxCapacity": 30
                }
                """;

        when(activityService.createActivity(any(CreateActivityRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.title").value("Yoga Bình Minh"))
                .andExpect(jsonPath("$.data.location").value("Sundeck 12"));
    }

    @Test
    @DisplayName("PUT /api/activities/{id}: updates activity successfully (KAN-134)")
    void updateActivity_Success() throws Exception {
        String jsonPayload = """
                {
                    "dayNumber": 2,
                    "title": "Dạ tiệc Hoàng Gia",
                    "description": "Bữa tối sang trọng",
                    "category": "Dining",
                    "location": "Grand Ballroom",
                    "activityDate": "2026-11-02",
                    "startTime": "18:30:00",
                    "endTime": "21:30:00",
                    "maxCapacity": 200,
                    "price": 100000,
                    "isIncluded": false,
                    "status": "SCHEDULED"
                }
                """;

        sampleResponse.setTitle("Dạ tiệc Hoàng Gia");
        sampleResponse.setLocation("Grand Ballroom");
        sampleResponse.setDayNumber(2);

        when(activityService.updateActivity(eq(1L), any(com.cruise.activity.dto.UpdateActivityRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/activities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Dạ tiệc Hoàng Gia"))
                .andExpect(jsonPath("$.data.location").value("Grand Ballroom"));
    }

    @Test
    @DisplayName("PUT /api/activities/{id}: returns 400 when title or location is blank (KAN-134)")
    void updateActivity_ValidationFailed() throws Exception {
        String jsonPayload = """
                {
                    "title": "",
                    "location": "",
                    "activityDate": "2026-11-02",
                    "startTime": "18:30:00",
                    "endTime": "21:30:00"
                }
                """;

        mockMvc.perform(put("/api/activities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/activities/{id}/time-location: configures time and location successfully (KAN-144)")
    void configureTimeAndLocation_Success() throws Exception {
        String jsonPayload = """
                {
                    "location": "Sky Lounge (Tầng 14)",
                    "activityDate": "2026-11-02",
                    "startTime": "19:00:00",
                    "endTime": "21:00:00"
                }
                """;

        sampleResponse.setLocation("Sky Lounge (Tầng 14)");
        sampleResponse.setActivityDate(LocalDate.of(2026, 11, 2));
        sampleResponse.setStartTime(LocalTime.of(19, 0));
        sampleResponse.setEndTime(LocalTime.of(21, 0));

        when(activityService.configureTimeAndLocation(eq(1L), any(ConfigureTimeAndLocationRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/activities/1/time-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.location").value("Sky Lounge (Tầng 14)"))
                .andExpect(jsonPath("$.data.startTime").value("19:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("21:00:00"));
    }

    @Test
    @DisplayName("PATCH /api/activities/{id}/time-location: returns 400 when endTime <= startTime")
    void configureTimeAndLocation_InvalidTimeRange() throws Exception {
        String jsonPayload = """
                {
                    "location": "Sky Lounge",
                    "startTime": "20:00:00",
                    "endTime": "19:00:00"
                }
                """;

        when(activityService.configureTimeAndLocation(eq(1L), any(ConfigureTimeAndLocationRequest.class)))
                .thenThrow(new BusinessRuleException("Thời gian kết thúc phải sau thời gian bắt đầu"));

        mockMvc.perform(patch("/api/activities/1/time-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Thời gian kết thúc phải sau thời gian bắt đầu"));
    }

    @Test
    @DisplayName("PATCH /api/activities/{id}/time-location: returns 404 when activity not found")
    void configureTimeAndLocation_NotFound() throws Exception {
        String jsonPayload = """
                {
                    "location": "Sky Lounge",
                    "startTime": "10:00:00",
                    "endTime": "11:00:00"
                }
                """;

        when(activityService.configureTimeAndLocation(eq(999L), any(ConfigureTimeAndLocationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy hoạt động với ID: 999"));

        mockMvc.perform(patch("/api/activities/999/time-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy hoạt động với ID: 999"));
    }

    @Test
    @DisplayName("GET /api/activities/{id}: returns activity details")
    void getActivityById_Success() throws Exception {
        when(activityService.getActivityById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.title").value("Yoga Bình Minh"));
    }

    @Test
    @DisplayName("GET /api/activities/tour/{tourId}: returns activities by tour")
    void getActivitiesByTour_Success() throws Exception {
        when(activityService.getActivitiesByTour(1L)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/activities/tour/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /api/activities/tour/{tourId}/day/{dayNumber}: returns activities by tour and day")
    void getActivitiesByTourAndDay_Success() throws Exception {
        when(activityService.getActivitiesByTourAndDay(1L, 1)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/activities/tour/1/day/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    @DisplayName("GET /api/activities/tour/{tourId}/paged: returns paged activities")
    void getActivitiesPaged_Success() throws Exception {
        when(activityService.getActivitiesPaged(eq(1L), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/activities/tour/1/paged")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("DELETE /api/activities/{id}: deletes activity successfully")
    void deleteActivity_Success() throws Exception {
        mockMvc.perform(delete("/api/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
