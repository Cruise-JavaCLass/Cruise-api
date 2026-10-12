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
@DisplayName("DailyItineraryController MockMvc Tests (KAN-114, KAN-118)")
class DailyItineraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyItineraryService dailyItineraryService;

    @MockitoBean
    private com.cruise.itinerary.service.ItineraryChangeHistoryService historyService;

    @MockitoBean
    private com.cruise.itinerary.service.ItineraryNotificationTriggerService notificationTriggerService;

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
    @DisplayName("PATCH /api/itinerary/tours/{tourId}/daily/reorder - Sắp xếp lại thứ tự thành công")
    void reorderDailyItinerariesSuccess() throws Exception {
        DailyItineraryResponse item = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(2)
                .build();

        when(dailyItineraryService.reorderDailyItineraries(eq(10L), any(com.cruise.itinerary.dto.ReorderItineraryRequest.class)))
                .thenReturn(List.of(item));

        mockMvc.perform(patch("/api/itinerary/tours/10/daily/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "id": 1, "newOrder": 2 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dayNumber").value(2));
    }

    @Test
    @DisplayName("POST /api/itinerary/tours/{tourId}/daily - Lỗi Validation 400 khi thiếu trường bắt buộc")
    void createDailyItineraryValidationBadRequest() throws Exception {
        mockMvc.perform(post("/api/itinerary/tours/10/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Chỉ có tiêu đề"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily/{id} - Lỗi 404 khi không tìm thấy lịch trình")
    void getDailyItineraryNotFound() throws Exception {
        when(dailyItineraryService.getDailyItineraryById(10L, 999L))
                .thenThrow(new com.cruise.itinerary.exception.ResourceNotFoundException("Không tìm thấy lịch trình"));

        mockMvc.perform(get("/api/itinerary/tours/10/daily/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/tours/{tourId}/daily/reorder - Lỗi 400 khi danh sách items rỗng")
    void reorderDailyItinerariesValidationEmpty() throws Exception {
        mockMvc.perform(patch("/api/itinerary/tours/10/daily/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily/history - Lấy lịch sử thay đổi tour thành công")
    void getTourHistorySuccess() throws Exception {
        com.cruise.itinerary.dto.ItineraryChangeHistoryResponse historyResponse = com.cruise.itinerary.dto.ItineraryChangeHistoryResponse.builder()
                .id(1L)
                .tourId(10L)
                .changeType(com.cruise.itinerary.enums.ChangeType.CREATE)
                .reason("Khởi tạo")
                .changedBy("SYSTEM")
                .build();

        when(historyService.getHistoriesByTourId(10L)).thenReturn(List.of(historyResponse));

        mockMvc.perform(get("/api/itinerary/tours/10/daily/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].changeType").value("CREATE"));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily/{id}/history - Lấy lịch sử ngày thành công")
    void getDailyItineraryHistorySuccess() throws Exception {
        com.cruise.itinerary.dto.ItineraryChangeHistoryResponse historyResponse = com.cruise.itinerary.dto.ItineraryChangeHistoryResponse.builder()
                .id(2L)
                .tourId(10L)
                .dailyItineraryId(1L)
                .changeType(com.cruise.itinerary.enums.ChangeType.UPDATE)
                .reason("Cập nhật")
                .changedBy("SYSTEM")
                .build();

        when(historyService.getHistoriesByDailyItineraryId(1L)).thenReturn(List.of(historyResponse));

        mockMvc.perform(get("/api/itinerary/tours/10/daily/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2L))
                .andExpect(jsonPath("$.data[0].changeType").value("UPDATE"));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/tours/{tourId}/daily/{id}/affected - Cập nhật lịch trình bị ảnh hưởng thành công")
    void updateAffectedScheduleSuccess() throws Exception {
        DailyItineraryResponse response = DailyItineraryResponse.builder()
                .id(1L)
                .tourId(10L)
                .dayNumber(1)
                .title("Khởi hành tránh bão")
                .isAffected(true)
                .changeReason("Bão số 3")
                .changeType(com.cruise.itinerary.enums.ChangeType.WEATHER_UPDATE)
                .build();

        when(dailyItineraryService.updateAffectedSchedule(eq(10L), eq(1L), any(com.cruise.itinerary.dto.UpdateAffectedScheduleRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/itinerary/tours/10/daily/1/affected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "changeType": "WEATHER_UPDATE",
                                  "reason": "Bão số 3",
                                  "changedBy": "CAPTAIN",
                                  "weatherForecast": "Mưa to gió lớn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.isAffected").value(true))
                .andExpect(jsonPath("$.data.changeReason").value("Bão số 3"))
                .andExpect(jsonPath("$.data.changeType").value("WEATHER_UPDATE"));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/tours/{tourId}/daily/{id}/affected - Lỗi 400 khi thiếu changeType hoặc reason")
    void updateAffectedScheduleValidation() throws Exception {
        mockMvc.perform(patch("/api/itinerary/tours/10/daily/1/affected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/daily/notifications - Lấy danh sách thông báo thay đổi thành công (KAN-126)")
    void getTourNotificationsSuccess() throws Exception {
        com.cruise.itinerary.dto.ItineraryChangeNotificationDto notificationDto = com.cruise.itinerary.dto.ItineraryChangeNotificationDto.builder()
                .tourId(10L)
                .tourCode("TOUR-001")
                .tourName("Hạ Long Bay Cruise")
                .changeType(com.cruise.itinerary.enums.ChangeType.UPDATE)
                .subject("[Thông Báo Lịch Trình] Hạ Long Bay Cruise - Ngày 1")
                .message("Nội dung thay đổi")
                .urgency("URGENT")
                .build();

        when(notificationTriggerService.getNotificationsByTourId(10L)).thenReturn(List.of(notificationDto));

        mockMvc.perform(get("/api/itinerary/tours/10/daily/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].tourId").value(10L))
                .andExpect(jsonPath("$.data[0].tourCode").value("TOUR-001"))
                .andExpect(jsonPath("$.data[0].urgency").value("URGENT"));
    }
}
