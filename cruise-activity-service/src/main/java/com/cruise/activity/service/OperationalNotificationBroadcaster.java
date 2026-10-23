package com.cruise.activity.service;

import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.enums.NotificationScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class OperationalNotificationBroadcaster {

    public record SubscriberContext(
            SseEmitter emitter,
            String callerRole,
            Integer deck,
            String zone
    ) {
        public boolean canReceive(OperationalNotificationEventDto event) {
            if (event.getScope() == null || event.getScope() == NotificationScope.ALL) {
                return true;
            }
            if (event.getScope() == NotificationScope.CREW_ONLY) {
                return callerRole != null && (
                        callerRole.equalsIgnoreCase("CREW")
                        || callerRole.equalsIgnoreCase("STAFF")
                        || callerRole.equalsIgnoreCase("OFFICER")
                        || callerRole.equalsIgnoreCase("ADMIN")
                );
            }
            if (event.getScope() == NotificationScope.PASSENGER_ONLY) {
                return callerRole == null
                        || callerRole.equalsIgnoreCase("PASSENGER")
                        || callerRole.equalsIgnoreCase("GUEST");
            }
            if (event.getScope() == NotificationScope.DECK) {
                return event.getTargetDeck() == null || event.getTargetDeck().equals(deck);
            }
            if (event.getScope() == NotificationScope.ZONE) {
                return event.getTargetZone() == null || event.getTargetZone().equalsIgnoreCase(zone);
            }
            return true;
        }
    }

    private final List<SubscriberContext> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        return subscribe(null, null, null);
    }

    public SseEmitter subscribe(String callerRole, Integer deck, String zone) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        SubscriberContext context = new SubscriberContext(emitter, callerRole, deck, zone);
        subscribers.add(context);

        emitter.onCompletion(() -> unsubscribe(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            unsubscribe(emitter);
        });
        emitter.onError(e -> unsubscribe(emitter));

        // Send handshake
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to Shipboard Operational Alert Gateway"));
        } catch (IOException e) {
            unsubscribe(emitter);
        }

        return emitter;
    }

    public void unsubscribe(SseEmitter emitter) {
        if (emitter != null) {
            subscribers.removeIf(sc -> sc.emitter() == emitter);
        }
    }

    public void broadcast(OperationalNotificationEventDto event) {
        log.info("Broadcasting operational alert to {} SSE subscribers: category={}, title={}, scope={}",
                subscribers.size(), event.getCategory(), event.getTitle(), event.getScope());

        List<SubscriberContext> deadSubscribers = new CopyOnWriteArrayList<>();
        for (SubscriberContext sub : subscribers) {
            if (sub.canReceive(event)) {
                try {
                    sub.emitter().send(SseEmitter.event()
                            .name("OPERATIONAL_ALERT")
                            .data(event));
                } catch (Exception e) {
                    deadSubscribers.add(sub);
                }
            }
        }
        subscribers.removeAll(deadSubscribers);
    }

    public int getSubscriberCount() {
        return subscribers.size();
    }
}
