package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.ShoreExcursionBookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcursionRosterController.class)
@Import(GlobalExceptionHandler.class)
class ExcursionRosterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShoreExcursionBookingService bookingService;

    @Nested
    @DisplayName("GET /api/v1/shore-excursions/{id}/roster")
    class GetRosterTests {

        @Test
        @DisplayName("AC1: Lấy danh sách Roster thành công trả về HTTP 200 và cấu trúc phân trang")
        void shouldReturnRosterSuccessfully() throws Exception {
            ExcursionPassengerRosterDto item = ExcursionPassengerRosterDto.builder()
                    .id(1L)
                    .excursionId(10L)
                    .passengerId(100L)
                    .passengerName("John Doe")
                    .cabinNumber("C-101")
                    .ticketCode("EXC-10-123456")
                    .bookingStatus(ExcursionBookingStatus.CONFIRMED)
                    .status(ExcursionPassengerStatus.REGISTERED)
                    .chargedAmount(new BigDecimal("120.00"))
                    .build();

            ExcursionRosterSummaryDto summary = ExcursionRosterSummaryDto.builder()
                    .excursionId(10L)
                    .excursionTitle("Hạ Long Bay Cruise")
                    .maxCapacity(40)
                    .confirmedCount(1L)
                    .waitlistCount(0L)
                    .cancelledCount(0L)
                    .checkedInCount(0L)
                    .specialAssistanceCount(0L)
                    .items(List.of(item))
                    .page(0)
                    .size(20)
                    .totalElements(1L)
                    .totalPages(1)
                    .build();

            given(bookingService.getExcursionRoster(eq(10L), any(), any(), any(), any(), any(Pageable.class)))
                    .willReturn(summary);

            mockMvc.perform(get("/api/v1/shore-excursions/10/roster")
                            .param("search", "John")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.excursionId").value(10))
                    .andExpect(jsonPath("$.data.confirmedCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].passengerName").value("John Doe"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shore-excursions/{id}/waitlist")
    class GetWaitlistTests {

        @Test
        @DisplayName("AC2: Lấy hàng đợi danh sách chờ FIFO trả về HTTP 200")
        void shouldReturnWaitlistQueue() throws Exception {
            ExcursionWaitlistEntryDto entry = ExcursionWaitlistEntryDto.builder()
                    .position(1)
                    .bookingId(55L)
                    .excursionId(10L)
                    .passengerId(102L)
                    .passengerName("Alice Smith")
                    .cabinNumber("C-102")
                    .bookedAt(Instant.now())
                    .build();

            given(bookingService.getWaitlistQueue(10L)).willReturn(List.of(entry));

            mockMvc.perform(get("/api/v1/shore-excursions/10/waitlist")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].position").value(1))
                    .andExpect(jsonPath("$.data[0].passengerName").value("Alice Smith"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/shore-excursions/{id}/waitlist/promote")
    class PromoteWaitlistTests {

        @Test
        @DisplayName("AC2: Đôn hành khách đầu tiên trong danh sách chờ lên CONFIRMED thành công")
        void shouldPromoteNextWaitlistPassenger() throws Exception {
            WaitlistPromotionResultDto result = WaitlistPromotionResultDto.builder()
                    .excursionId(10L)
                    .bookingId(55L)
                    .passengerId(102L)
                    .passengerName("Alice Smith")
                    .cabinNumber("C-102")
                    .ticketCode("EXC-10-999999")
                    .assignedBus("BUS-01")
                    .status(ExcursionBookingStatus.CONFIRMED)
                    .chargedAmount(new BigDecimal("120.00"))
                    .message("Đã đôn hành khách lên vé chính thức")
                    .build();

            given(bookingService.promoteNextWaitlistPassenger(10L)).willReturn(result);

            mockMvc.perform(post("/api/v1/shore-excursions/10/waitlist/promote")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.ticketCode").value("EXC-10-999999"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/shore-excursions/{id}/bookings/{bookingId}/cancel-waiver")
    class CancelWaiverTests {

        @Test
        @DisplayName("AC4: Hủy vé kèm Medical Waiver Override trả về HTTP 200 và hoàn 100%")
        void shouldCancelWithMedicalWaiver() throws Exception {
            ExcursionCancellationResponse resp = ExcursionCancellationResponse.builder()
                    .bookingId(88L)
                    .excursionId(10L)
                    .passengerId(100L)
                    .status("CANCELLED_MEDICAL_WAIVER")
                    .refundAmount(new BigDecimal("150.00"))
                    .penaltyFee(BigDecimal.ZERO)
                    .cancellationReason("Miễn trừ y tế/khẩn cấp")
                    .build();

            given(bookingService.cancelBookingWithMedicalWaiver(eq(10L), eq(88L), any(MedicalWaiverCancellationRequest.class)))
                    .willReturn(resp);

            mockMvc.perform(post("/api/v1/shore-excursions/10/bookings/88/cancel-waiver")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Hành khách sốt cấp tính có chỉ định y tế tại trạm xá tàu\",\"approvedByStaffId\":\"MGR-DOC-01\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED_MEDICAL_WAIVER"))
                    .andExpect(jsonPath("$.data.penaltyFee").value(0));
        }

        @Test
        @DisplayName("Validation: Hủy vé thiếu lý do y tế trả về HTTP 400 Bad Request")
        void shouldRejectEmptyReason() throws Exception {
            mockMvc.perform(post("/api/v1/shore-excursions/10/bookings/88/cancel-waiver")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shore-excursions/{id}/roster/export")
    class ExportRosterTests {

        @Test
        @DisplayName("KAN-327: Xuất CSV Roster trả về HTTP 200 và content-type text/csv")
        void shouldExportCsvSuccessfully() throws Exception {
            byte[] csvBytes = "Ticket Code,Passenger Name\nEXC-10-1,John Doe".getBytes();
            given(bookingService.exportRosterCsv(10L)).willReturn(csvBytes);

            mockMvc.perform(get("/api/v1/shore-excursions/10/roster/export"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"excursion-10-roster.csv\""))
                    .andExpect(content().contentType("text/csv; charset=UTF-8"))
                    .andExpect(content().bytes(csvBytes));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/shore-excursions/{id}/alerts/schedule-change")
    class AlertScheduleChangeTests {

        @Test
        @DisplayName("KAN-327: Phát cảnh báo thay đổi lịch trình trả về HTTP 200")
        void shouldBroadcastAlertSuccessfully() throws Exception {
            ExcursionScheduleAlertResponse resp = ExcursionScheduleAlertResponse.builder()
                    .excursionId(10L)
                    .recipientsCount(25)
                    .title("Cập nhật giờ đón tour")
                    .message("Xe sẽ khởi hành sớm hơn 15 phút tại sảnh A")
                    .status("BROADCAST_SUCCESS")
                    .build();

            given(bookingService.triggerScheduleAlert(eq(10L), any(ExcursionScheduleAlertRequest.class)))
                    .willReturn(resp);

            mockMvc.perform(post("/api/v1/shore-excursions/10/alerts/schedule-change")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Cập nhật giờ đón tour\",\"message\":\"Xe sẽ khởi hành sớm hơn 15 phút tại sảnh A\",\"isUrgent\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.recipientsCount").value(25))
                    .andExpect(jsonPath("$.data.status").value("BROADCAST_SUCCESS"));
        }
    }
}
