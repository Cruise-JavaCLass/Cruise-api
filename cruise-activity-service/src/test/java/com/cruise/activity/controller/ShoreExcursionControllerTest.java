package com.cruise.activity.controller;

import com.cruise.activity.dto.ConfigureExcursionPriceCapacityScheduleRequest;
import com.cruise.activity.dto.CreateShoreExcursionRequest;
import com.cruise.activity.dto.ShoreExcursionResponse;
import com.cruise.activity.dto.UpdateExcursionStatusRequest;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.service.ShoreExcursionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShoreExcursionController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ShoreExcursionController Web Layer Tests (KAN-168)")
class ShoreExcursionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShoreExcursionService excursionService;

    private ShoreExcursionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = ShoreExcursionResponse.builder()
                .id(1L)
                .tourId(1L)
                .portId(2L)
                .dayNumber(2)
                .title("Khám phá Hang Sửng Sốt")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu tàu 1")
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(0)
                .remainingCapacity(50)
                .price(BigDecimal.valueOf(1200000))
                .childPrice(BigDecimal.valueOf(600000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("POST /api/excursions: creates excursion successfully (201 Created)")
    void createExcursion_Success() throws Exception {
        String reqJson = """
                {
                    "tourId": 1,
                    "portId": 2,
                    "dayNumber": 2,
                    "title": "Khám phá Hang Sửng Sốt",
                    "portName": "Cảng Quốc Tế Hạ Long",
                    "meetingPoint": "Cầu tàu 1",
                    "excursionDate": "2026-11-02",
                    "gatheringTime": "08:00:00",
                    "departureTime": "08:30:00",
                    "returnToShipTime": "12:00:00",
                    "maxCapacity": 50,
                    "price": 1200000,
                    "childPrice": 600000
                }
                """;

        when(excursionService.createExcursion(any(CreateShoreExcursionRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/excursions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Khám phá Hang Sửng Sốt"));
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/price-capacity-schedule: configures price, capacity and schedule (KAN-168)")
    void configurePriceCapacityAndSchedule_Success() throws Exception {
        String reqJson = """
                {
                    "excursionDate": "2026-11-02",
                    "gatheringTime": "07:45:00",
                    "departureTime": "08:15:00",
                    "returnToShipTime": "12:30:00",
                    "maxCapacity": 60,
                    "price": 1500000,
                    "childPrice": 750000
                }
                """;

        sampleResponse.setMaxCapacity(60);
        sampleResponse.setPrice(BigDecimal.valueOf(1500000));
        when(excursionService.configurePriceCapacityAndSchedule(eq(1L), any(ConfigureExcursionPriceCapacityScheduleRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/excursions/1/price-capacity-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxCapacity").value(60))
                .andExpect(jsonPath("$.data.price").value(1500000));
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/price-capacity-schedule: returns 400 when validation fails")
    void configurePriceCapacityAndSchedule_ValidationError() throws Exception {
        String reqJson = """
                {
                    "gatheringTime": "08:00:00",
                    "departureTime": "08:30:00",
                    "returnToShipTime": "12:00:00",
                    "maxCapacity": -5,
                    "price": 1000000
                }
                """;

        mockMvc.perform(patch("/api/excursions/1/price-capacity-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/price-capacity-schedule: returns 400 on business rule violation")
    void configurePriceCapacityAndSchedule_BusinessRuleError() throws Exception {
        String reqJson = """
                {
                    "gatheringTime": "08:00:00",
                    "departureTime": "08:30:00",
                    "returnToShipTime": "12:00:00",
                    "maxCapacity": 10,
                    "price": 1000000
                }
                """;

        when(excursionService.configurePriceCapacityAndSchedule(eq(1L), any(ConfigureExcursionPriceCapacityScheduleRequest.class)))
                .thenThrow(new BusinessRuleException("Sức chứa tối đa không được nhỏ hơn số người đã đăng ký"));

        mockMvc.perform(patch("/api/excursions/1/price-capacity-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sức chứa tối đa không được nhỏ hơn số người đã đăng ký"));
    }

    @Test
    @DisplayName("GET /api/excursions/{id}: returns excursion detail (200 OK)")
    void getExcursionById_Success() throws Exception {
        when(excursionService.getExcursionById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/excursions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Khám phá Hang Sửng Sốt"));
    }

    @Test
    @DisplayName("GET /api/excursions/{id}: returns 404 when not found")
    void getExcursionById_NotFound() throws Exception {
        when(excursionService.getExcursionById(999L))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tour bờ với ID: 999"));

        mockMvc.perform(get("/api/excursions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tour bờ với ID: 999"));
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/status: updates status successfully")
    void updateStatus_Success() throws Exception {
        String reqJson = """
                {
                    "status": "OPEN_FOR_BOOKING"
                }
                """;

        sampleResponse.setStatus(ExcursionStatus.OPEN_FOR_BOOKING);
        when(excursionService.updateStatus(eq(1L), any(UpdateExcursionStatusRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/excursions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN_FOR_BOOKING"));
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/status: returns 400 when transition is invalid")
    void updateStatus_InvalidTransition() throws Exception {
        String reqJson = """
                {
                    "status": "COMPLETED"
                }
                """;

        when(excursionService.updateStatus(eq(1L), any(UpdateExcursionStatusRequest.class)))
                .thenThrow(new BusinessRuleException("Không thể chuyển trạng thái tour bờ"));

        mockMvc.perform(patch("/api/excursions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể chuyển trạng thái tour bờ"));
    }

    @Test
    @DisplayName("PATCH /api/excursions/{id}/status: returns 400 when status is null")
    void updateStatus_NullStatus() throws Exception {
        String reqJson = """
                {
                    "cancellationReason": "Hủy không lý do"
                }
                """;

        mockMvc.perform(patch("/api/excursions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/excursions/tour/{tourId}/status/{status}: returns excursions by status")
    void getExcursionsByStatus_Success() throws Exception {
        when(excursionService.getExcursionsByTourAndStatus(1L, ExcursionStatus.OPEN_FOR_BOOKING))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/excursions/tour/1/status/OPEN_FOR_BOOKING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/excursions/{id}/status/allowed-transitions: returns allowed transitions")
    void getAllowedTransitions_Success() throws Exception {
        when(excursionService.getAllowedStatusTransitions(1L))
                .thenReturn(Set.of(ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.CANCELLED));

        mockMvc.perform(get("/api/excursions/1/status/allowed-transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/excursions/tour/{tourId}: returns list of excursions for tour")
    void getExcursionsByTour_Success() throws Exception {
        when(excursionService.getExcursionsByTour(1L)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/excursions/tour/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/excursions/{id}: deletes excursion successfully")
    void deleteExcursion_Success() throws Exception {
        doNothing().when(excursionService).deleteExcursion(1L);

        mockMvc.perform(delete("/api/excursions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa tour bờ thành công"));
    }
}
