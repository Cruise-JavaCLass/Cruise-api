package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.exception.PaymentRequiredException;
import com.cruise.activity.service.ActivityAttendanceService;
import com.cruise.activity.service.ActivityRegistrationService;
import com.cruise.activity.service.OnboardActivityService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Activity Registration & Cancellation Controller MockMvc Tests (KAN-218)")
class ActivityRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardActivityService activityService;

    @MockitoBean
    private ActivityAttendanceService attendanceService;

    @MockitoBean
    private ActivityRegistrationService registrationService;

    @Test
    @DisplayName("POST /api/activities/{id}/registrations - 201 Created khi đăng ký thành công")
    void registerActivity_Success_Returns201() throws Exception {
        ActivityRegistrationResponse response = ActivityRegistrationResponse.builder()
                .registrationId(500L)
                .activityId(10L)
                .activityTitle("Wine Tasting")
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status("CONFIRMED")
                .chargedAmount(BigDecimal.valueOf(300000))
                .transactionId("FOLIO-REG-987654")
                .registeredAt(Instant.now())
                .build();

        when(registrationService.registerActivity(eq(10L), any(ActivityRegistrationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/activities/10/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "passengerId": 101,
                                    "cabinNumber": "701",
                                    "notes": "Standard"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registrationId").value(500))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.transactionId").value("FOLIO-REG-987654"));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/registrations - 409 Conflict khi trùng lặp đăng ký")
    void registerActivity_Duplicate_Returns409() throws Exception {
        when(registrationService.registerActivity(eq(10L), any(ActivityRegistrationRequest.class)))
                .thenThrow(new DuplicateRegistrationException("Hành khách đã đăng ký trước đó"));

        mockMvc.perform(post("/api/activities/10/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "passengerId": 101
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hành khách đã đăng ký trước đó"));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/registrations - 402 Payment Required khi Folio bị khóa")
    void registerActivity_FolioLocked_Returns402() throws Exception {
        when(registrationService.registerActivity(eq(10L), any(ActivityRegistrationRequest.class)))
                .thenThrow(new PaymentRequiredException("Tài khoản cabin folio đang bị khóa chi tiêu"));

        mockMvc.perform(post("/api/activities/10/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "passengerId": 101
                                }
                                """))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tài khoản cabin folio đang bị khóa chi tiêu"));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/registrations - 400 Bad Request khi thiếu passengerId")
    void registerActivity_InvalidPayload_Returns400() throws Exception {
        mockMvc.perform(post("/api/activities/10/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "cabinNumber": "701"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/activities/{id}/registrations/{registrationId} - 200 OK khi hủy đăng ký")
    void cancelRegistration_Success_Returns200() throws Exception {
        ActivityCancellationResponse response = ActivityCancellationResponse.builder()
                .registrationId(500L)
                .status("CANCELLED")
                .refundAmount(BigDecimal.valueOf(300000))
                .waitlistPromotedPassengerId(102L)
                .cancellationReason("Lịch trình thay đổi")
                .cancelledAt(Instant.now())
                .build();

        when(registrationService.cancelRegistration(10L, 500L, 101L, "Lịch trình thay đổi"))
                .thenReturn(response);

        mockMvc.perform(delete("/api/activities/10/registrations/500")
                        .param("passengerId", "101")
                        .param("reason", "Lịch trình thay đổi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").value(300000))
                .andExpect(jsonPath("$.data.waitlistPromotedPassengerId").value(102));
    }

    @Test
    @DisplayName("POST /api/activities/{id}/waitlist/{waitlistId}/promote - 200 OK khi đôn suất")
    void promoteWaitlist_Success_Returns200() throws Exception {
        ActivityRegistrationResponse response = ActivityRegistrationResponse.builder()
                .registrationId(502L)
                .activityId(10L)
                .status("CONFIRMED")
                .chargedAmount(BigDecimal.valueOf(300000))
                .transactionId("FOLIO-MAN-112233")
                .build();

        when(registrationService.promoteWaitlist(10L, 502L))
                .thenReturn(response);

        mockMvc.perform(post("/api/activities/10/waitlist/502/promote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/registrations/roster - 200 OK lấy danh sách phân trang")
    void getRosterPaged_Success_Returns200() throws Exception {
        ActivityRosterDto dto = ActivityRosterDto.builder()
                .registrationId(500L)
                .activityId(10L)
                .passengerId(101L)
                .passengerName("Nguyen Van A")
                .cabinNumber("701")
                .status("CONFIRMED")
                .build();

        when(registrationService.getRosterPaged(eq(10L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/activities/10/registrations/roster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].passengerName").value("Nguyen Van A"));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/waitlist - 200 OK lấy hàng đợi")
    void getWaitlistQueue_Success_Returns200() throws Exception {
        WaitlistQueueItemDto item = WaitlistQueueItemDto.builder()
                .position(1)
                .registrationId(502L)
                .passengerId(102L)
                .passengerName("Tran Thi B")
                .cabinNumber("802")
                .status("WAITLISTED")
                .build();

        when(registrationService.getWaitlistQueue(10L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/activities/10/waitlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].position").value(1))
                .andExpect(jsonPath("$.data[0].passengerName").value("Tran Thi B"));
    }

    @Test
    @DisplayName("GET /api/activities/{id}/registrations/export/csv - 200 OK tải file CSV UTF-8")
    void exportRosterCsv_Success_ReturnsCsvFile() throws Exception {
        byte[] csv = "\uFEFFSTT,Mã Đăng Ký\n1,500".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(registrationService.exportRosterCsv(10L)).thenReturn(csv);

        mockMvc.perform(get("/api/activities/10/registrations/export/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"activity-10-roster.csv\""));
    }
}
