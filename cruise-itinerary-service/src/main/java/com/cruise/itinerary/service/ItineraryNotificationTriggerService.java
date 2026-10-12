package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ItineraryChangeNotificationDto;
import com.cruise.itinerary.enums.ChangeType;
import com.cruise.itinerary.event.ItineraryChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service xử lý kích hoạt và phát thông báo khi lịch trình thay đổi (KAN-126)
 */
@Service
public class ItineraryNotificationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryNotificationTriggerService.class);

    private final ApplicationEventPublisher eventPublisher;
    // Lưu trữ danh sách thông báo gửi gần nhất trong bộ nhớ phục vụ truy vấn & kiểm tra
    private final List<ItineraryChangeNotificationDto> triggeredNotifications = new CopyOnWriteArrayList<>();

    public ItineraryNotificationTriggerService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Kích hoạt phát tán Event thay đổi lịch trình
     */
    public void triggerChangeNotification(ItineraryChangedEvent event) {
        log.info("Triggering itinerary change notification for Tour [{}] (ID: {}), ChangeType: {}",
                event.getTourCode(), event.getTourId(), event.getChangeType());
        eventPublisher.publishEvent(event);
    }

    /**
     * Xử lý lắng nghe Event và tạo thông báo tương ứng
     */
    @EventListener
    public void onItineraryChanged(ItineraryChangedEvent event) {
        String urgency = determineUrgency(event.getChangeType());
        String subject = buildSubject(event);
        String message = buildMessage(event);

        ItineraryChangeNotificationDto notification = ItineraryChangeNotificationDto.builder()
                .tourId(event.getTourId())
                .tourCode(event.getTourCode())
                .tourName(event.getTourName())
                .dailyItineraryId(event.getDailyItineraryId())
                .dayNumber(event.getDayNumber())
                .changeType(event.getChangeType())
                .subject(subject)
                .message(message)
                .urgency(urgency)
                .createdAt(Instant.now())
                .build();

        triggeredNotifications.add(0, notification);

        log.info("Notification dispatched: [{}] Subject: '{}', Urgency: {}",
                notification.getTourCode(), notification.getSubject(), notification.getUrgency());
    }

    public List<ItineraryChangeNotificationDto> getNotificationsByTourId(Long tourId) {
        return triggeredNotifications.stream()
                .filter(n -> n.getTourId() != null && n.getTourId().equals(tourId))
                .toList();
    }

    public List<ItineraryChangeNotificationDto> getAllRecentNotifications() {
        return List.copyOf(triggeredNotifications);
    }

    public void clearHistory() {
        triggeredNotifications.clear();
    }

    private String determineUrgency(ChangeType changeType) {
        if (changeType == null) {
            return "NORMAL";
        }
        return switch (changeType) {
            case DELETE, EMERGENCY_CHANGE -> "CRITICAL";
            case UPDATE, REORDER, WEATHER_UPDATE -> "URGENT";
            case CREATE -> "NORMAL";
        };
    }

    private String buildSubject(ItineraryChangedEvent event) {
        String tourDisplay = event.getTourName() != null ? event.getTourName() : ("Tour #" + event.getTourId());
        if (event.getDayNumber() != null) {
            return String.format("[Thông Báo Lịch Trình] %s - Ngày %d", tourDisplay, event.getDayNumber());
        }
        return String.format("[Thông Báo Lịch Trình] %s", tourDisplay);
    }

    private String buildMessage(ItineraryChangedEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getChangeType() != null) {
            sb.append("Loại thay đổi: ").append(event.getChangeType().name()).append(". ");
        }
        if (event.getTitle() != null) {
            sb.append("Nội dung: ").append(event.getTitle()).append(". ");
        }
        if (event.getReason() != null) {
            sb.append("Lý do: ").append(event.getReason()).append(". ");
        }
        if (event.getPreviousState() != null && event.getNewState() != null) {
            sb.append(String.format("Chi tiết: '%s' -> '%s'. ", event.getPreviousState(), event.getNewState()));
        }
        return sb.toString().trim();
    }
}
