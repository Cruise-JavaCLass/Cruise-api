package com.cruise.itinerary.controller;

import com.cruise.itinerary.dto.CreateCruiseTourRequest;
import com.cruise.itinerary.dto.CruiseTourResponse;
import com.cruise.itinerary.dto.UpdateCruiseTourRequest;
import com.cruise.itinerary.dto.UpdateTourStatusRequest;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.GlobalExceptionHandler;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.service.CruiseTourService;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CruiseTourController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CruiseTourController MockMvc Web Layer Tests")
class CruiseTourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CruiseTourService tourService;

    private CruiseTourResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = CruiseTourResponse.builder()
                .id(10L)
                .shipId(1L)
                .shipName("Halong Grand Harmony")
                .shipCode("SHIP-001")
                .code("TOUR-HL-01")
                .name("Hành trình Di sản Vịnh Hạ Long 3N2Đ")
                .description("Tour khám phá vẻ đẹp Vịnh Hạ Long")
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .departurePort("Cảng Hạ Long")
                .destinationPort("Cảng Hạ Long")
                .maxCapacity(1500)
                .basePrice(BigDecimal.valueOf(5000000))
                .currency("VND")
                .status(TourStatus.DRAFT)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // =========================================================================
    // POST /api/itinerary/tours
    // =========================================================================

    @Test
    @DisplayName("POST /api/itinerary/tours creates tour and returns 201 Created")
    void shouldCreateTourSuccessfully() throws Exception {
        when(tourService.createTour(any(CreateCruiseTourRequest.class))).thenReturn(sampleResponse);

        String jsonPayload = """
                {
                    "shipId": 1,
                    "code": "TOUR-HL-01",
                    "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ",
                    "description": "Tour khám phá vẻ đẹp Vịnh Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "maxCapacity": 1500,
                    "basePrice": 5000000,
                    "currency": "VND",
                    "status": "DRAFT"
                }
                """;

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo tour du lịch thành công"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-01"))
                .andExpect(jsonPath("$.data.shipName").value("Halong Grand Harmony"));
    }

    @Test
    @DisplayName("POST /api/itinerary/tours returns 400 Bad Request when validation fails")
    void shouldReturn400WhenCreatePayloadInvalid() throws Exception {
        String invalidPayload = """
                {
                    "shipId": null,
                    "code": "",
                    "name": "",
                    "durationDays": 0
                }
                """;

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/itinerary/tours returns 404 Not Found when ship does not exist")
    void shouldReturn404WhenShipNotFoundOnCreate() throws Exception {
        when(tourService.createTour(any(CreateCruiseTourRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: 99"));

        String jsonPayload = """
                {
                    "shipId": 99,
                    "code": "TOUR-HL-01",
                    "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "maxCapacity": 1000,
                    "basePrice": 5000000
                }
                """;

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy tàu du lịch với ID: 99"));
    }

    @Test
    @DisplayName("POST /api/itinerary/tours returns 409 Conflict when tour code exists")
    void shouldReturn409WhenTourCodeDuplicateOnCreate() throws Exception {
        when(tourService.createTour(any(CreateCruiseTourRequest.class)))
                .thenThrow(new DuplicateResourceException("Mã tour 'TOUR-HL-01' đã tồn tại trong hệ thống"));

        String jsonPayload = """
                {
                    "shipId": 1,
                    "code": "TOUR-HL-01",
                    "name": "Hành trình Di sản Vịnh Hạ Long 3N2Đ",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "maxCapacity": 1000,
                    "basePrice": 5000000
                }
                """;

        mockMvc.perform(post("/api/itinerary/tours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã tour 'TOUR-HL-01' đã tồn tại trong hệ thống"));
    }

    // =========================================================================
    // PUT /api/itinerary/tours/{id}
    // =========================================================================

    @Test
    @DisplayName("PUT /api/itinerary/tours/{id} updates tour and returns 200 OK")
    void shouldUpdateTourSuccessfully() throws Exception {
        sampleResponse.setName("Hành trình Di sản Vịnh Hạ Long Cao Cấp");
        when(tourService.updateTour(eq(10L), any(UpdateCruiseTourRequest.class))).thenReturn(sampleResponse);

        String updatePayload = """
                {
                    "shipId": 1,
                    "code": "TOUR-HL-01",
                    "name": "Hành trình Di sản Vịnh Hạ Long Cao Cấp",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "maxCapacity": 1000,
                    "basePrice": 5000000,
                    "status": "PUBLISHED"
                }
                """;

        mockMvc.perform(put("/api/itinerary/tours/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin tour du lịch thành công"))
                .andExpect(jsonPath("$.data.name").value("Hành trình Di sản Vịnh Hạ Long Cao Cấp"));
    }

    @Test
    @DisplayName("PUT /api/itinerary/tours/{id} returns 404 when tour not found")
    void shouldReturn404WhenTourNotFoundOnUpdate() throws Exception {
        when(tourService.updateTour(eq(999L), any(UpdateCruiseTourRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tour du lịch với ID: 999"));

        String updatePayload = """
                {
                    "shipId": 1,
                    "code": "TOUR-HL-01",
                    "name": "Hành trình Di sản Vịnh Hạ Long",
                    "departurePort": "Cảng Hạ Long",
                    "destinationPort": "Cảng Hạ Long",
                    "durationDays": 3,
                    "durationNights": 2,
                    "startDate": "2026-11-01",
                    "endDate": "2026-11-03",
                    "maxCapacity": 1000,
                    "basePrice": 5000000
                }
                """;

        mockMvc.perform(put("/api/itinerary/tours/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // PATCH /api/itinerary/tours/{id}/status
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/itinerary/tours/{id}/status updates tour status and returns 200 OK")
    void shouldUpdateTourStatusSuccessfully() throws Exception {
        sampleResponse.setStatus(TourStatus.IN_PROGRESS);
        when(tourService.updateTourStatus(eq(10L), any(UpdateTourStatusRequest.class))).thenReturn(sampleResponse);

        String patchPayload = """
                {
                    "status": "IN_PROGRESS"
                }
                """;

        mockMvc.perform(patch("/api/itinerary/tours/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái tour thành công"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("PATCH /api/itinerary/tours/{id}/status returns 400 when status is missing")
    void shouldReturn400WhenStatusIsMissing() throws Exception {
        String patchPayload = "{}";

        mockMvc.perform(patch("/api/itinerary/tours/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // GET /api/itinerary/tours/{id} & /code/{code}
    // =========================================================================

    @Test
    @DisplayName("GET /api/itinerary/tours/{id} returns tour by ID")
    void shouldGetTourByIdSuccessfully() throws Exception {
        when(tourService.getTourById(10L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/itinerary/tours/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-01"));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/code/{code} returns tour by Code")
    void shouldGetTourByCodeSuccessfully() throws Exception {
        when(tourService.getTourByCode("TOUR-HL-01")).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/itinerary/tours/code/TOUR-HL-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("TOUR-HL-01"));
    }

    // =========================================================================
    // GET /api/itinerary/tours & /active
    // =========================================================================

    @Test
    @DisplayName("GET /api/itinerary/tours returns paged tour list")
    void shouldGetAllToursPaged() throws Exception {
        when(tourService.getAllTours(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/itinerary/tours")
                        .param("keyword", "Hạ Long")
                        .param("shipId", "1")
                        .param("status", "DRAFT")
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("TOUR-HL-01"));
    }

    @Test
    @DisplayName("GET /api/itinerary/tours/active returns active tours list")
    void shouldGetActiveTours() throws Exception {
        when(tourService.getAllActiveTours()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/itinerary/tours/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("TOUR-HL-01"));
    }

    // =========================================================================
    // DELETE /api/itinerary/tours/{id}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/itinerary/tours/{id} soft deletes tour by default")
    void shouldSoftDeleteTour() throws Exception {
        doNothing().when(tourService).deleteTour(10L, false);

        mockMvc.perform(delete("/api/itinerary/tours/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã hủy kích hoạt tour du lịch thành công"));

        verify(tourService).deleteTour(10L, false);
    }

    @Test
    @DisplayName("DELETE /api/itinerary/tours/{id}?hard=true permanently deletes tour")
    void shouldHardDeleteTour() throws Exception {
        doNothing().when(tourService).deleteTour(10L, true);

        mockMvc.perform(delete("/api/itinerary/tours/10").param("hard", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xóa vĩnh viễn tour du lịch khỏi hệ thống"));

        verify(tourService).deleteTour(10L, true);
    }
}
