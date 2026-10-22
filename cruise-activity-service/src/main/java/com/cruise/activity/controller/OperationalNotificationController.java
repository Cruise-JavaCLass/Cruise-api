package com.cruise.activity.controller;

import com.cruise.activity.dto.OperationalBroadcastRequest;
import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.service.OperationalNotificationBroadcaster;
import com.cruise.activity.service.OperationalNotificationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    public SseEmitter subscribeOperationalAlertStream() {
        return broadcaster.subscribe();
    }

    /**
     * Dispatches an operational broadcast to crew, passengers, or specific ship decks.
     */
    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OperationalNotificationEventDto> broadcastOperationalAlert(
            @Valid @RequestBody OperationalBroadcastRequest request
    ) {
        OperationalNotificationEventDto result = operationalNotificationService.broadcastOperationalNotification(request);
        return ApiResponse.ok("Phát thông báo vận hành thành công", result);
    }
}
