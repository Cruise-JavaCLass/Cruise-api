package com.cruise.activity.controller;

import com.cruise.activity.dto.OperationalBroadcastRequest;
import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.enums.NotificationScope;
import com.cruise.activity.service.OperationalNotificationBroadcaster;
import com.cruise.activity.service.OperationalNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalNotificationController.class)
@DisplayName("OperationalNotificationController Tests (KAN-263 & KAN-436)")
class OperationalNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OperationalNotificationService operationalNotificationService;

    @MockitoBean
    private OperationalNotificationBroadcaster broadcaster;

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast returns 201 Created and dispatches alert")
    void testBroadcastOperationalAlert() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Tập huấn an toàn khẩn cấp")
                .message("Tất cả thuyền viên và hành khách tập trung tại boong số 4")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .playAlertSound(true)
                .build();

        OperationalNotificationEventDto event = OperationalNotificationEventDto.builder()
                .broadcastId("BC-12345")
                .title("Tập huấn an toàn khẩn cấp")
                .message("Tất cả thuyền viên và hành khách tập trung tại boong số 4")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .broadcastAt(Instant.now())
                .recipientCount(500)
                .build();

        when(operationalNotificationService.broadcastOperationalNotification(any())).thenReturn(event);

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .header("X-Caller-Role", "OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.broadcastId").value("BC-12345"))
                .andExpect(jsonPath("$.data.category").value("CRITICAL"))
                .andExpect(jsonPath("$.data.recipientCount").value(500));
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast rejects passenger callers with 403 Forbidden")
    void testBroadcastOperationalAlertRejectsPassengerCaller() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Fake Emergency")
                .message("All passengers abandon ship")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .header("X-Caller-Role", "PASSENGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast with title > 200 chars returns 400 Bad Request")
    void testBroadcastRejectsOversizedTitle() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("T".repeat(201))
                .message("Valid message")
                .category(NotificationCategory.OPERATIONAL)
                .scope(NotificationScope.ALL)
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast with message > 1000 chars returns 400 Bad Request")
    void testBroadcastRejectsOversizedMessage() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Valid Title")
                .message("M".repeat(1001))
                .category(NotificationCategory.OPERATIONAL)
                .scope(NotificationScope.ALL)
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/operations/notifications/stream connects to SSE emitter with headers")
    void testSubscribeOperationalAlertStream() throws Exception {
        when(broadcaster.subscribe(eq("CREW"), eq(7), eq("AFT"))).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/operations/notifications/stream")
                        .header("X-Caller-Role", "CREW")
                        .header("X-Deck", "7")
                        .header("X-Zone", "AFT"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast rejects missing caller role with 403 Forbidden")
    void testBroadcastRejectsMissingCallerRole() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Valid Emergency Alert")
                .message("Valid alert message body")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast rejects DECK scope without targetDeck with 400 Bad Request")
    void testBroadcastRejectsDeckScopeWithoutTargetDeck() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Deck Evacuation")
                .message("Please proceed to muster station")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.DECK)
                .targetDeck(null)
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .header("X-Caller-Role", "OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/operations/notifications/broadcast rejects ZONE scope without targetZone with 400 Bad Request")
    void testBroadcastRejectsZoneScopeWithoutTargetZone() throws Exception {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Zone Maintenance")
                .message("Maintenance in progress")
                .category(NotificationCategory.OPERATIONAL)
                .scope(NotificationScope.ZONE)
                .targetZone("   ")
                .build();

        mockMvc.perform(post("/api/operations/notifications/broadcast")
                        .header("X-Caller-Role", "OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
