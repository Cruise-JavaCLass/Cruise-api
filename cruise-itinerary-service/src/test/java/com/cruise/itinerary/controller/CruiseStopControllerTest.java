package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.CruiseStopRequest;
import com.cruise.itinerary.dto.CruiseStopResponse;
import com.cruise.itinerary.exception.GlobalExceptionHandler;
import com.cruise.itinerary.service.CruiseStopService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CruiseStopController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CruiseStopController MockMvc Tests (KAN-109)")
class CruiseStopControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CruiseStopService stopService;

    @Test
    @DisplayName("POST /api/itinerary/tours/{tourId}/stops tạo điểm dừng thành công")
    void createStopSuccess() throws Exception {
        CruiseStopResponse resp = CruiseStopResponse.builder()
                .id(1L)
                .tourId(10L)
                .portId(2L)
                .stopOrder(1)
                .arrivalTime(LocalDateTime.of(2026, 10, 15, 8, 0))
                .departureTime(LocalDateTime.of(2026, 10, 15, 18, 0))
                .build();

        when(stopService.createStop(eq(10L), any(CruiseStopRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/itinerary/tours/10/stops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "portId": 2,
                                  "stopOrder": 1,
                                  "arrivalTime": "2026-10-15T08:00:00",
                                  "departureTime": "2026-10-15T18:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stopOrder").value(1));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/{tourId}/stops lấy danh sách điểm dừng của tour")
    void getStopsByTourIdSuccess() throws Exception {
        CruiseStopResponse resp = CruiseStopResponse.builder()
                .id(1L)
                .tourId(10L)
                .portId(2L)
                .stopOrder(1)
                .build();

        when(stopService.getStopsByTourId(10L)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/itinerary/tours/10/stops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stopOrder").value(1));
    }

    @Test
    @DisplayName("DELETE /api/itinerary/tours/{tourId}/stops/{stopId} xóa điểm dừng thành công")
    void deleteStopSuccess() throws Exception {
        doNothing().when(stopService).deleteStop(10L, 1L);

        mockMvc.perform(delete("/api/itinerary/tours/10/stops/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
