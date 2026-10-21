package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ShoreExcursionBookingService;
import com.cruise.activity.service.ShoreExcursionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcursionBookingController.class)
@Import(GlobalExceptionHandler.class)
class ExcursionBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShoreExcursionBookingService bookingService;

    @MockitoBean
    private ShoreExcursionService excursionService;

    @Test
    @DisplayName("POST /api/activities/excursions/{id}/bookings -> 201 Created")
    void testBookExcursion() throws Exception {
        ExcursionBookingResponse resp = ExcursionBookingResponse.builder()
                .bookingId(10L)
                .excursionId(1L)
                .passengerId(200L)
                .ticketCode("EXC-1-999")
                .busNumber("BUS-01")
                .groupName("Nhóm 1")
                .chargedAmount(new BigDecimal("120.00"))
                .transactionId("FOLIO-EXC-123")
                .status("REGISTERED")
                .bookedAt(Instant.now())
                .build();

        when(bookingService.bookExcursion(eq(1L), any(ExcursionBookingRequest.class))).thenReturn(resp);

        String jsonPayload = """
                {
                    "passengerId": 200,
                    "cabinNumber": "Suite-801"
                }
                """;

        mockMvc.perform(post("/api/activities/excursions/1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketCode").value("EXC-1-999"))
                .andExpect(jsonPath("$.data.chargedAmount").value(120.00));
    }

    @Test
    @DisplayName("DELETE /api/activities/excursions/{id}/bookings/{bookingId} -> 200 OK")
    void testCancelBooking() throws Exception {
        ExcursionCancellationResponse resp = ExcursionCancellationResponse.builder()
                .bookingId(10L)
                .excursionId(1L)
                .passengerId(200L)
                .status("CANCELLED")
                .refundAmount(new BigDecimal("120.00"))
                .penaltyFee(BigDecimal.ZERO)
                .cancellationReason("Khách hủy trước 24h")
                .cancelledAt(Instant.now())
                .build();

        when(bookingService.cancelBooking(eq(1L), eq(10L), any())).thenReturn(resp);

        mockMvc.perform(delete("/api/activities/excursions/1/bookings/10")
                        .param("reason", "Khách hủy trước 24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(120.00));
    }

    @Test
    @DisplayName("GET /api/activities/excursions/bookings/my -> 200 OK")
    void testGetMyBookings() throws Exception {
        PassengerExcursionBookingDto dto = PassengerExcursionBookingDto.builder()
                .bookingId(10L)
                .excursionId(1L)
                .excursionTitle("Hạ Long Bay Cruise")
                .ticketCode("EXC-1-999")
                .busNumber("BUS-01")
                .build();

        when(bookingService.getMyBookings(200L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/activities/excursions/bookings/my")
                        .param("passengerId", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].ticketCode").value("EXC-1-999"));
    }

    @Test
    @DisplayName("PATCH /api/activities/excursions/{id}/bookings/{bookingId}/reassign-bus -> 200 OK")
    void testReassignBus() throws Exception {
        ReassignBusResponse resp = ReassignBusResponse.builder()
                .bookingId(10L)
                .excursionId(1L)
                .ticketCode("EXC-1-999")
                .oldBusNumber("BUS-01")
                .newBusNumber("BUS-02")
                .groupName("Nhóm 2")
                .message("Chuyển xe thành công")
                .build();

        when(bookingService.reassignBus(eq(1L), eq(10L), any(ReassignBusRequest.class))).thenReturn(resp);

        String jsonPayload = """
                {
                    "newBusNumber": "BUS-02",
                    "newGroupName": "Nhóm 2"
                }
                """;

        mockMvc.perform(patch("/api/activities/excursions/1/bookings/10/reassign-bus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newBusNumber").value("BUS-02"));
    }

    @Test
    @DisplayName("GET /api/activities/excursions/{id}/manifest -> 200 OK")
    void testGetExcursionManifest() throws Exception {
        when(excursionService.getPassengerListsByGroup(1L)).thenReturn(new ExcursionManifestSummaryResponse());

        mockMvc.perform(get("/api/activities/excursions/1/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
