package com.cruise.activity.service;

import com.cruise.activity.dto.OperationalBroadcastRequest;
import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.NotificationScope;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalNotificationService {

    private final PassengerNotificationRepository notificationRepository;
    private final PassengerRepository passengerRepository;
    private final OperationalNotificationBroadcaster broadcaster;

    @Transactional
    public OperationalNotificationEventDto broadcastOperationalNotification(OperationalBroadcastRequest request) {
        String broadcastId = "BC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Processing operational broadcast: id={}, title={}, category={}, scope={}",
                broadcastId, request.getTitle(), request.getCategory(), request.getScope());

        List<Long> recipientPassengerIds = new ArrayList<>();

        if (request.getScope() == NotificationScope.ALL || request.getScope() == NotificationScope.PASSENGER_ONLY) {
            passengerRepository.findAll().forEach(p -> recipientPassengerIds.add(p.getId()));

            String metadataStr = null;
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                metadataStr = request.getMetadata().toString();
            }

            List<PassengerNotification> batch = new ArrayList<>();
            for (Long passengerId : recipientPassengerIds) {
                batch.add(PassengerNotification.builder()
                        .passengerId(passengerId)
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .category(request.getCategory())
                        .actionUrl(request.getActionUrl())
                        .metadata(metadataStr)
                        .isRead(false)
                        .build());
            }

            if (!batch.isEmpty()) {
                notificationRepository.saveAll(batch);
                log.info("Persisted broadcast notifications for {} passengers", batch.size());
            }
        }

        OperationalNotificationEventDto event = OperationalNotificationEventDto.builder()
                .broadcastId(broadcastId)
                .title(request.getTitle())
                .message(request.getMessage())
                .category(request.getCategory())
                .scope(request.getScope())
                .targetDeck(request.getTargetDeck())
                .targetZone(request.getTargetZone())
                .actionUrl(request.getActionUrl())
                .playAlertSound(Boolean.TRUE.equals(request.getPlayAlertSound()))
                .requireAcknowledgment(Boolean.TRUE.equals(request.getRequireAcknowledgment()))
                .broadcastAt(Instant.now())
                .recipientCount(recipientPassengerIds.size())
                .metadata(request.getMetadata())
                .build();

        // Push real-time to active SSE connections
        broadcaster.broadcast(event);

        return event;
    }
}
