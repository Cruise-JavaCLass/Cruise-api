package com.cruise.activity.controller;

import com.cruise.activity.dto.OperationalBroadcastRequest;
import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.service.OperationalNotificationBroadcaster;
import com.cruise.activity.service.OperationalNotificationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller for operational announcements & real-time SSE stream gateway.
 * Architecture Note: Operates behind the API Gateway trust boundary where upstream API Gateway
 * authenticates JWT identities and injects trusted X-Caller-Role headers before forwarding downstream.
 */
@RestController
@RequestMapping("/api/operations/notifications")
@RequiredArgsConstructor
public class OperationalNotificationController {

    private final OperationalNotificationService operationalNotificationService;
    private final OperationalNotificationBroadcaster broadcaster;

    /**
     * Subscribes to operational alerts via Server-Sent Events (SSE).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeOperationalAlertStream(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @RequestHeader(value = "X-Deck", required = false) Integer deck,
            @RequestHeader(value = "X-Zone", required = false) String zone
    ) {
        return broadcaster.subscribe(callerRole, deck, zone);
    }

    /**
     * Dispatches an operational broadcast to crew, passengers, or specific ship decks.
     */
    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OperationalNotificationEventDto> broadcastOperationalAlert(
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @Valid @RequestBody OperationalBroadcastRequest request
    ) {
        if (callerRole == null || !java.util.Set.of("CREW", "STAFF", "OFFICER", "ADMIN", "SYSTEM")
                .contains(callerRole.trim().toUpperCase())) {
            throw new AccessDeniedException("Chỉ nhân viên hoặc hệ thống mới có quyền phát thông báo vận hành");
        }
        OperationalNotificationEventDto result = operationalNotificationService.broadcastOperationalNotification(request);
        return ApiResponse.ok("Phát thông báo vận hành thành công", result);
    }
}
