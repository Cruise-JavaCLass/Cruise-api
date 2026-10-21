package com.cruise.activity.controller;

import com.cruise.activity.dto.ExcursionReviewRequest;
import com.cruise.activity.dto.ExcursionReviewResponse;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ExcursionReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExcursionFeedbackController.class)
@Import(GlobalExceptionHandler.class)
class ExcursionFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExcursionReviewService reviewService;

    @Test
    @DisplayName("POST /api/v1/shore-excursions/{id}/reviews: Gửi review thành công")
    void testSubmitReview_Success() throws Exception {
        ExcursionReviewRequest request = new ExcursionReviewRequest(10L, 5, 5, 5, 5, "Tuyệt vời");
        ExcursionReviewResponse response = new ExcursionReviewResponse(
                1L, 2L, 10L, 5, 5, 5, 5, "Tuyệt vời", false, null, LocalDateTime.now()
        );

        when(reviewService.submitReview(eq(2L), any(ExcursionReviewRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/shore-excursions/2/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.ratingOverall").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/shore-excursions/{id}/reviews: Lấy danh sách reviews thành công")
    void testGetReviews_Success() throws Exception {
        ExcursionReviewResponse response = new ExcursionReviewResponse(
                1L, 2L, 10L, 5, 5, 5, 5, "Tuyệt vời", false, null, LocalDateTime.now()
        );
        when(reviewService.getReviewsByExcursion(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/shore-excursions/2/reviews")
                        .header("X-Caller-Role", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/shore-excursions/reviews/flagged: Chặn 403 nếu caller role là PASSENGER")
    void testGetFlaggedReviews_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/shore-excursions/reviews/flagged")
                        .header("X-Caller-Role", "PASSENGER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/shore-excursions/reviews/flagged: Lấy danh sách flagged reviews thành công với STAFF")
    void testGetFlaggedReviews_Success() throws Exception {
        ExcursionReviewResponse response = new ExcursionReviewResponse(
                1L, 2L, 10L, 1, 1, 1, 1, "Nguy hiểm", true, "1 sao", LocalDateTime.now()
        );
        when(reviewService.getFlaggedReviews()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/shore-excursions/reviews/flagged")
                        .header("X-Caller-Role", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isFlagged").value(true));
    }
}
