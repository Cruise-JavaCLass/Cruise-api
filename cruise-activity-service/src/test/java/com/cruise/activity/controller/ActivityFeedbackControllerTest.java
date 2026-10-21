package com.cruise.activity.controller;

import com.cruise.activity.dto.ActivityCsatSummaryDto;
import com.cruise.activity.dto.ActivityFeedbackResponse;
import com.cruise.activity.dto.SubmitFeedbackRequest;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ActivityFeedbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityFeedbackController.class)
@Import(GlobalExceptionHandler.class)
class ActivityFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ActivityFeedbackService feedbackService;

    @Test
    @DisplayName("POST /api/activities/{id}/feedback: Gửi phản hồi thành công trả về 201 Created")
    void testSubmitFeedback_Success() throws Exception {
        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .passengerId(12L)
                .rating(5)
                .organizationRating(4)
                .comments("Rất hài lòng về dịch vụ!")
                .build();

        ActivityFeedbackResponse response = ActivityFeedbackResponse.builder()
                .id(100L)
                .activityId(20L)
                .passengerId(12L)
                .rating(5)
                .escalated(false)
                .createdAt(Instant.now())
                .build();

        when(feedbackService.submitFeedback(eq(20L), any(SubmitFeedbackRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/activities/20/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.escalated").value(false));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/feedback: Validation thất bại khi rating ngoài khoảng 1-5")
    void testSubmitFeedback_ValidationFailure() throws Exception {
        SubmitFeedbackRequest invalidRequest = SubmitFeedbackRequest.builder()
                .passengerId(12L)
                .rating(6) // vượt quá 5 sao
                .build();

        mockMvc.perform(post("/api/activities/20/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/activities/{id}/feedback: Lấy danh sách feedback theo hoạt động")
    void testGetFeedbacks() throws Exception {
        ActivityFeedbackResponse fb = ActivityFeedbackResponse.builder()
                .id(101L)
                .activityId(20L)
                .passengerId(15L)
                .rating(4)
                .escalated(false)
                .build();

        when(feedbackService.getFeedbacksByActivity(20L)).thenReturn(List.of(fb));

        mockMvc.perform(get("/api/activities/20/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/feedback/summary: Lấy CSAT summary")
    void testGetCsatSummary() throws Exception {
        ActivityCsatSummaryDto summary = ActivityCsatSummaryDto.builder()
                .activityId(20L)
                .averageRating(4.5)
                .totalReviews(10L)
                .escalatedCount(1L)
                .ratingDistribution(Map.of(5, 7L, 4, 2L, 1, 1L))
                .build();

        when(feedbackService.getCsatSummary(20L)).thenReturn(summary);

        mockMvc.perform(get("/api/activities/20/feedback/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(10));
    }
}
