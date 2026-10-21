package com.cruise.activity.controller;

import com.cruise.activity.dto.NotificationSummaryDto;
import com.cruise.activity.dto.PassengerNotificationDto;
import com.cruise.activity.dto.SendNotificationRequest;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.PassengerNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerNotificationController.class)
@Import(GlobalExceptionHandler.class)
class PassengerNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PassengerNotificationService notificationService;

    @Test
    @DisplayName("GET /api/passengers/{id}/notifications: Trả về 200 OK với danh sách thông báo")
    void testGetNotifications() throws Exception {
        PassengerNotificationDto dto = PassengerNotificationDto.builder()
                .id(1L)
                .passengerId(10L)
                .title("Cảnh báo thời tiết")
                .message("Biển động nhẹ khu vực vịnh Hạ Long")
                .category(NotificationCategory.CRITICAL)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getNotifications(10L, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/passengers/10/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].category").value("CRITICAL"));
    }

    @Test
    @DisplayName("GET /api/passengers/{id}/notifications/summary: Trả về thống kê badge số tin chưa đọc")
    void testGetSummary() throws Exception {
        NotificationSummaryDto summary = NotificationSummaryDto.builder()
                .totalUnread(3L)
                .criticalUnread(1L)
                .unreadByCategory(Map.of("CRITICAL", 1L))
                .build();

        when(notificationService.getNotificationSummary(10L)).thenReturn(summary);

        mockMvc.perform(get("/api/passengers/10/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnread").value(3))
                .andExpect(jsonPath("$.data.criticalUnread").value(1));
    }

    @Test
    @DisplayName("POST /api/passengers/{id}/notifications/{notifId}/read: Đánh dấu đã đọc thành công")
    void testMarkAsRead() throws Exception {
        PassengerNotificationDto dto = PassengerNotificationDto.builder()
                .id(5L)
                .passengerId(10L)
                .isRead(true)
                .build();

        when(notificationService.markAsRead(10L, 5L)).thenReturn(dto);

        mockMvc.perform(post("/api/passengers/10/notifications/5/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    @DisplayName("POST /api/passengers/{id}/notifications: Phát thông báo trả về 201 Created")
    void testSendNotification() throws Exception {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .title("Thông báo họp đoàn")
                .message("Tập trung tại sảnh Deck 3")
                .category(NotificationCategory.REMINDER)
                .build();

        PassengerNotificationDto dto = PassengerNotificationDto.builder()
                .id(7L)
                .passengerId(10L)
                .title("Thông báo họp đoàn")
                .category(NotificationCategory.REMINDER)
                .isRead(false)
                .build();

        when(notificationService.sendNotification(eq(10L), any(SendNotificationRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/passengers/10/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7));
    }
}
