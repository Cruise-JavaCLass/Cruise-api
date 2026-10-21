package com.cruise.activity.service;

import com.cruise.activity.dto.NotificationSummaryDto;
import com.cruise.activity.dto.PassengerNotificationDto;
import com.cruise.activity.dto.SendNotificationRequest;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassengerNotificationService {

    private final PassengerNotificationRepository notificationRepository;
    private final PassengerRepository passengerRepository;

    @Transactional(readOnly = true)
    public List<PassengerNotificationDto> getNotifications(Long passengerId, NotificationCategory category) {
        validatePassengerExists(passengerId);

        List<PassengerNotification> notifications;
        if (category != null) {
            notifications = notificationRepository.findByPassengerIdAndCategoryOrderByCreatedAtDesc(passengerId, category);
        } else {
            notifications = notificationRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId);
        }

        return notifications.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto getNotificationSummary(Long passengerId) {
        validatePassengerExists(passengerId);

        long totalUnread = notificationRepository.countByPassengerIdAndIsReadFalse(passengerId);
        long criticalUnread = notificationRepository.countByPassengerIdAndCategoryAndIsReadFalse(
                passengerId, NotificationCategory.CRITICAL);

        Map<String, Long> unreadByCategory = new HashMap<>();
        for (NotificationCategory cat : NotificationCategory.values()) {
            unreadByCategory.put(cat.name(),
                    notificationRepository.countByPassengerIdAndCategoryAndIsReadFalse(passengerId, cat));
        }

        return NotificationSummaryDto.builder()
                .totalUnread(totalUnread)
                .criticalUnread(criticalUnread)
                .unreadByCategory(unreadByCategory)
                .build();
    }

    @Transactional
    public PassengerNotificationDto markAsRead(Long passengerId, Long notificationId) {
        validatePassengerExists(passengerId);

        PassengerNotification notification = notificationRepository.findByIdAndPassengerId(notificationId, passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.info("Marked notification as read: id={}, passengerId={}", notificationId, passengerId);
        }

        return mapToDto(notification);
    }

    @Transactional
    public void markAllAsRead(Long passengerId) {
        validatePassengerExists(passengerId);
        int updatedCount = notificationRepository.markAllAsRead(passengerId, Instant.now());
        log.info("Marked all notifications as read for passengerId={}, count={}", passengerId, updatedCount);
    }

    @Transactional
    public PassengerNotificationDto sendNotification(Long passengerId, SendNotificationRequest request) {
        validatePassengerExists(passengerId);

        PassengerNotification notification = PassengerNotification.builder()
                .passengerId(passengerId)
                .title(request.getTitle())
                .message(request.getMessage())
                .category(request.getCategory())
                .actionUrl(request.getActionUrl())
                .metadata(request.getMetadata())
                .isRead(false)
                .build();

        PassengerNotification saved = notificationRepository.save(notification);
        log.info("Notification sent: id={}, passengerId={}, category={}", saved.getId(), passengerId, request.getCategory());
        return mapToDto(saved);
    }

    private void validatePassengerExists(Long passengerId) {
        if (!passengerRepository.existsById(passengerId)) {
            throw new ResourceNotFoundException("Passenger not found with id: " + passengerId);
        }
    }

    private PassengerNotificationDto mapToDto(PassengerNotification n) {
        return PassengerNotificationDto.builder()
                .id(n.getId())
                .passengerId(n.getPassengerId())
                .title(n.getTitle())
                .message(n.getMessage())
                .category(n.getCategory())
                .actionUrl(n.getActionUrl())
                .isRead(n.getIsRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .metadata(n.getMetadata())
                .build();
    }
}
