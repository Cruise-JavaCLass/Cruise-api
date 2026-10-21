package com.cruise.activity.controller;

import com.cruise.activity.dto.NotificationSummaryDto;
import com.cruise.activity.dto.PassengerNotificationDto;
import com.cruise.activity.dto.SendNotificationRequest;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.exception.AccessDeniedException;
import com.cruise.activity.service.PassengerNotificationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passengers/{passengerId}/notifications")
@RequiredArgsConstructor
public class PassengerNotificationController {

    private final PassengerNotificationService notificationService;

    @GetMapping
    public ApiResponse<List<PassengerNotificationDto>> getNotifications(
            @PathVariable Long passengerId,
            @RequestParam(required = false) NotificationCategory category
    ) {
        return ApiResponse.ok("Lấy danh sách thông báo thành công",
                notificationService.getNotifications(passengerId, category));
    }

    @GetMapping("/summary")
    public ApiResponse<NotificationSummaryDto> getNotificationSummary(@PathVariable Long passengerId) {
        return ApiResponse.ok("Lấy thống kê thông báo thành công",
                notificationService.getNotificationSummary(passengerId));
    }

    @PostMapping("/{notifId}/read")
    public ApiResponse<PassengerNotificationDto> markAsRead(
            @PathVariable Long passengerId,
            @PathVariable Long notifId
    ) {
        return ApiResponse.ok("Đánh dấu đã đọc thành công",
                notificationService.markAsRead(passengerId, notifId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@PathVariable Long passengerId) {
        notificationService.markAllAsRead(passengerId);
        return ApiResponse.ok("Đánh dấu toàn bộ thông báo đã đọc thành công", null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PassengerNotificationDto> sendNotification(
            @PathVariable Long passengerId,
            @RequestHeader(value = "X-Caller-Role", required = false) String callerRole,
            @Valid @RequestBody SendNotificationRequest request
    ) {
        verifyStaffOrSystemAuthorization(callerRole);
        return ApiResponse.ok("Phát thông báo thành công",
                notificationService.sendNotification(passengerId, request));
    }

    private void verifyStaffOrSystemAuthorization(String callerRole) {
        if (callerRole != null && !callerRole.isBlank()) {
            String upper = callerRole.toUpperCase();
            if (upper.contains("PASSENGER") || upper.contains("GUEST") || upper.contains("CUSTOMER")) {
                throw new AccessDeniedException("Chỉ nhân viên hoặc hệ thống mới có quyền phát thông báo cho hành khách");
            }
        }
    }
}
