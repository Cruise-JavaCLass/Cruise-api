package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.DailyItineraryRequest;
import com.cruise.itinerary.dto.DailyItineraryResponse;
import com.cruise.itinerary.exception.GlobalExceptionHandler;
import com.cruise.itinerary.service.DailyItineraryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyItineraryController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("DailyItineraryController MockMvc Tests (KAN-114)")
class DailyItineraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyItineraryService dailyItineraryService;

    @Test
    @DisplayName("POST /api/itinerary/tours/{tourId}/daily - Tạo lịch trình ngày thành công")
    void createDailyItinerarySuccess() throws Exception {
        DailyItineraryResponse response = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Khởi hành từ Cảng Tuần Châu")
                .isSeaDay(false)
                .build();

        when(dailyItineraryService.createDailyItinerary(eq(10L), any(DailyItineraryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/itinerary/tours/10/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayNumber": 1,
                                  "itineraryDate": "2026-11-01",
                                  "title": "Khởi hành từ Cảng Tuần Châu",
                                  "isSeaDay": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.dayNumber").value(1))
                .andExpect(jsonPath("$.data.title").value("Khởi hành từ Cảng Tuần Châu"));
    }

    @Test
    @DisplayName("PUT /api/itinerary/tours/{tourId}/daily/{id} - Cập nhật lịch trình ngày thành công")
    void updateDailyItinerarySuccess() throws Exception {
        DailyItineraryResponse response = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Cập nhật ngày 1")
                .isSeaDay(true)
                .build();

        when(dailyItineraryService.updateDailyItinerary(eq(10L), eq(1L), any(DailyItineraryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/itinerary/tours/10/daily/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayNumber": 1,
                                  "itineraryDate": "2026-11-01",
                                  "title": "Cập nhật ngày 1",
                                  "isSeaDay": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Cập nhật ngày 1"));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily - Lấy danh sách lịch trình theo tour")
    void getDailyItinerariesSuccess() throws Exception {
        DailyItineraryResponse item = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .title("Ngày 1")
                .build();

        when(dailyItineraryService.getDailyItinerariesByTourId(10L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/itinerary/tours/10/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dayNumber").value(1));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily/{id} - Lấy chi tiết lịch trình")
    void getDailyItineraryByIdSuccess() throws Exception {
        DailyItineraryResponse item = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(1)
                .itineraryDate(LocalDate.of(2026, 11, 1))
                .build();

        when(dailyItineraryService.getDailyItineraryById(10L, 1L)).thenReturn(item);

        mockMvc.perform(get("/api/itinerary/tours/10/daily/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/itinerary/tours/{tourId}/daily/{id} - Xóa lịch trình ngày")
    void deleteDailyItinerarySuccess() throws Exception {
        doNothing().when(dailyItineraryService).deleteDailyItinerary(10L, 1L);

        mockMvc.perform(delete("/api/itinerary/tours/10/daily/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xóa lịch trình ngày thành công"));
    }

    @Test
    @DisplayName("POST /api/itinerary/tours/{tourId}/daily - Báo lỗi validation khi thiếu ngày")
    void createDailyItineraryValidationFail() throws Exception {
        mockMvc.perform(post("/api/itinerary/tours/10/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Thiếu thông tin"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
