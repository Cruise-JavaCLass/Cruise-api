package com.cruise.activity.service;

import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.enums.NotificationScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OperationalNotificationBroadcaster Unit Tests")
class OperationalNotificationBroadcasterTest {

    @Test
    @DisplayName("subscribe and unsubscribe on completion")
    void testSubscribeAndUnsubscribe() {
        OperationalNotificationBroadcaster broadcaster = new OperationalNotificationBroadcaster();
        assertThat(broadcaster.getSubscriberCount()).isEqualTo(0);

        SseEmitter emitter = broadcaster.subscribe("CREW", 5, "FORWARD");
        assertThat(broadcaster.getSubscriberCount()).isEqualTo(1);

        broadcaster.unsubscribe(emitter);
        assertThat(broadcaster.getSubscriberCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("broadcast sends events matching subscriber scope")
    void testBroadcastScopeFiltering() {
        OperationalNotificationBroadcaster broadcaster = new OperationalNotificationBroadcaster();

        OperationalNotificationBroadcaster.SubscriberContext crewContext =
                new OperationalNotificationBroadcaster.SubscriberContext(mock(SseEmitter.class), "CREW", 7, "AFT");
        OperationalNotificationBroadcaster.SubscriberContext passengerContext =
                new OperationalNotificationBroadcaster.SubscriberContext(mock(SseEmitter.class), "PASSENGER", 4, "MID");

        OperationalNotificationEventDto allEvent = OperationalNotificationEventDto.builder()
                .scope(NotificationScope.ALL)
                .category(NotificationCategory.OPERATIONAL)
                .title("All Broadcast")
                .build();

        OperationalNotificationEventDto crewEvent = OperationalNotificationEventDto.builder()
                .scope(NotificationScope.CREW_ONLY)
                .category(NotificationCategory.CRITICAL)
                .title("Crew Alert")
                .build();

        OperationalNotificationEventDto passengerEvent = OperationalNotificationEventDto.builder()
                .scope(NotificationScope.PASSENGER_ONLY)
                .category(NotificationCategory.REMINDER)
                .title("Passenger Entertainment")
                .build();

        OperationalNotificationEventDto deck7Event = OperationalNotificationEventDto.builder()
                .scope(NotificationScope.DECK)
                .targetDeck(7)
                .category(NotificationCategory.OPERATIONAL)
                .title("Deck 7 Clean")
                .build();

        OperationalNotificationEventDto zoneMidEvent = OperationalNotificationEventDto.builder()
                .scope(NotificationScope.ZONE)
                .targetZone("MID")
                .category(NotificationCategory.OPERATIONAL)
                .title("Midship Notice")
                .build();

        // Check canReceive rules
        assertThat(crewContext.canReceive(allEvent)).isTrue();
        assertThat(passengerContext.canReceive(allEvent)).isTrue();

        assertThat(crewContext.canReceive(crewEvent)).isTrue();
        assertThat(passengerContext.canReceive(crewEvent)).isFalse();

        assertThat(crewContext.canReceive(passengerEvent)).isFalse();
        assertThat(passengerContext.canReceive(passengerEvent)).isTrue();

        assertThat(crewContext.canReceive(deck7Event)).isTrue();
        assertThat(passengerContext.canReceive(deck7Event)).isFalse();

        assertThat(crewContext.canReceive(zoneMidEvent)).isFalse();
        assertThat(passengerContext.canReceive(zoneMidEvent)).isTrue();
    }

    @Test
    @DisplayName("broadcast removes dead emitters when send throws exception")
    void testBroadcastRemovesDeadEmitters() throws Exception {
        OperationalNotificationBroadcaster broadcaster = new OperationalNotificationBroadcaster();
        SseEmitter activeEmitter = broadcaster.subscribe();
        assertThat(broadcaster.getSubscriberCount()).isEqualTo(1);

        // Force complete or error so send will fail
        activeEmitter.completeWithError(new IOException("Simulated network drop"));

        OperationalNotificationEventDto event = OperationalNotificationEventDto.builder()
                .broadcastId("BC-1")
                .title("Test Alert")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .broadcastAt(Instant.now())
                .build();

        broadcaster.broadcast(event);
        assertThat(broadcaster.getSubscriberCount()).isEqualTo(0);
    }
}
