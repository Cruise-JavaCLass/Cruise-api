package com.cruise.activity.controller;

import com.cruise.activity.dto.PersonalScheduleItemDto;
import com.cruise.activity.dto.PersonalScheduleResponse;
import com.cruise.activity.dto.ScheduleConflictDto;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.service.PassengerScheduleService;
import com.cruise.activity.service.PassengerService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PassengerPersonalScheduleApiTest MockMvc Tests (KAN-351)")
class PassengerPersonalScheduleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PassengerService passengerService;

    @MockitoBean
    private PassengerScheduleService passengerScheduleService;

    @Test
    @DisplayName("GET /api/passengers/{id}/personal-schedule - Lấy lịch trình cá nhân thành công")
    void getPersonalSchedule_Success() throws Exception {
        PersonalScheduleItemDto item = PersonalScheduleItemDto.builder()
                .eventId(10L)
                .itemType("ACTIVITY")
                .title("Yoga Bình Minh")
                .category("Wellness")
                .dayNumber(1)
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(8, 0))
                .assemblyTime(LocalTime.of(7, 0))
                .location("Sundeck 12")
                .deck("Boong 12")
                .status("REGISTERED")
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("701")
                .ticketCode("ACT-101")
                .price(BigDecimal.ZERO)
                .isIncluded(true)
                .hasConflict(false)
                .build();

        PersonalScheduleResponse response = PersonalScheduleResponse.builder()
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("701")
                .dayNumber(1)
                .scope("my")
                .totalItems(1)
                .totalConflicts(0)
                .items(List.of(item))
                .conflicts(List.of())
                .build();

        when(passengerScheduleService.getPersonalSchedule(eq(1L), eq(1), eq("my"))).thenReturn(response);

        mockMvc.perform(get("/api/passengers/1/personal-schedule?day=1&scope=my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passengerId").value(1))
                .andExpect(jsonPath("$.data.scope").value("my"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Yoga Bình Minh"))
                .andExpect(jsonPath("$.data.items[0].status").value("REGISTERED"));
    }

    @Test
    @DisplayName("GET /api/activities/passengers/{id}/personal-schedule - Đường dẫn alias hoạt động thành công")
    void getPersonalSchedule_AliasPath_Success() throws Exception {
        PersonalScheduleResponse response = PersonalScheduleResponse.builder()
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("701")
                .scope("my")
                .totalItems(0)
                .totalConflicts(0)
                .items(List.of())
                .conflicts(List.of())
                .build();

        when(passengerScheduleService.getPersonalSchedule(eq(1L), eq(null), eq("my"))).thenReturn(response);

        mockMvc.perform(get("/api/activities/passengers/1/personal-schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passengerId").value(1));
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/personal-schedule - Cabin scope thành công")
    void getPersonalSchedule_CabinScope_Success() throws Exception {
        PersonalScheduleResponse response = PersonalScheduleResponse.builder()
                .passengerId(1L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("701")
                .dayNumber(2)
                .scope("cabin")
                .totalItems(2)
                .totalConflicts(0)
                .items(List.of())
                .conflicts(List.of())
                .build();

        when(passengerScheduleService.getPersonalSchedule(eq(1L), eq(2), eq("cabin"))).thenReturn(response);

        mockMvc.perform(get("/api/passengers/1/personal-schedule?day=2&scope=cabin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("cabin"))
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/personal-schedule/conflicts - Lấy danh sách xung đột thành công")
    void getScheduleConflicts_Success() throws Exception {
        ScheduleConflictDto conflict = ScheduleConflictDto.builder()
                .firstEventId(10L)
                .firstEventTitle("Yoga Bình Minh")
                .firstEventType("ACTIVITY")
                .firstStartTime(LocalTime.of(7, 0))
                .firstEndTime(LocalTime.of(8, 0))
                .secondEventId(20L)
                .secondEventTitle("Lớp Học Nấu Ăn")
                .secondEventType("ACTIVITY")
                .secondStartTime(LocalTime.of(7, 30))
                .secondEndTime(LocalTime.of(9, 0))
                .overlapMinutes(30L)
                .description("Xung đột thời gian 30 phút giữa 'Yoga Bình Minh' và 'Lớp Học Nấu Ăn'")
                .build();

        when(passengerScheduleService.getScheduleConflicts(eq(1L), eq(1))).thenReturn(List.of(conflict));

        mockMvc.perform(get("/api/passengers/1/personal-schedule/conflicts?day=1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].firstEventId").value(10))
                .andExpect(jsonPath("$.data[0].secondEventId").value(20))
                .andExpect(jsonPath("$.data[0].overlapMinutes").value(30));
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/personal-schedule - Trả về 404 khi không tìm thấy hành khách")
    void getPersonalSchedule_NotFound() throws Exception {
        when(passengerScheduleService.getPersonalSchedule(eq(999L), eq(null), eq("my")))
                .thenThrow(new ResourceNotFoundException("Hành khách với ID 999 không tồn tại"));

        mockMvc.perform(get("/api/passengers/999/personal-schedule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hành khách với ID 999 không tồn tại"));
    }
}
