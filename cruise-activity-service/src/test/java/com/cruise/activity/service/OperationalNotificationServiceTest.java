package com.cruise.activity.service;

import com.cruise.activity.dto.OperationalBroadcastRequest;
import com.cruise.activity.dto.OperationalNotificationEventDto;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.enums.NotificationScope;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationalNotificationService Unit Tests")
class OperationalNotificationServiceTest {

    @Mock
    private PassengerNotificationRepository notificationRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private OperationalNotificationBroadcaster broadcaster;

    @InjectMocks
    private OperationalNotificationService service;

    private Passenger passenger1;
    private Passenger passenger2;

    @BeforeEach
    void setUp() {
        passenger1 = Passenger.builder().tourId(1L).build();
        passenger1.setId(10L);

        passenger2 = Passenger.builder().tourId(1L).build();
        passenger2.setId(20L);
    }

    @Test
    @DisplayName("broadcastOperationalNotification with scope ALL persists notifications for passengers and broadcasts")
    void testBroadcastScopeAllPersistsAndBroadcasts() {
        when(passengerRepository.findAll()).thenReturn(List.of(passenger1, passenger2));

        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Weather Alert")
                .message("High waves expected this evening")
                .category(NotificationCategory.CRITICAL)
                .scope(NotificationScope.ALL)
                .actionUrl("https://cruise.com/safety")
                .playAlertSound(true)
                .requireAcknowledgment(true)
                .metadata(Map.of("windSpeed", 45))
                .build();

        OperationalNotificationEventDto event = service.broadcastOperationalNotification(req);

        assertThat(event).isNotNull();
        assertThat(event.getBroadcastId()).startsWith("BC-");
        assertThat(event.getTitle()).isEqualTo("Weather Alert");
        assertThat(event.getRecipientCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PassengerNotification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<PassengerNotification> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getTitle()).isEqualTo("Weather Alert");
        assertThat(saved.get(0).getPassengerId()).isEqualTo(10L);
        assertThat(saved.get(1).getPassengerId()).isEqualTo(20L);

        verify(broadcaster).broadcast(any(OperationalNotificationEventDto.class));
    }

    @Test
    @DisplayName("broadcastOperationalNotification with scope CREW_ONLY does not persist to passenger repository")
    void testBroadcastScopeCrewOnlyDoesNotPersistPassengers() {
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Crew Meeting")
                .message("Briefing at 14:00 in Mess hall")
                .category(NotificationCategory.OPERATIONAL)
                .scope(NotificationScope.CREW_ONLY)
                .build();

        OperationalNotificationEventDto event = service.broadcastOperationalNotification(req);

        assertThat(event).isNotNull();
        assertThat(event.getRecipientCount()).isEqualTo(0);
        verify(passengerRepository, never()).findAll();
        verify(notificationRepository, never()).saveAll(any());
        verify(broadcaster).broadcast(any(OperationalNotificationEventDto.class));
    }

    @Test
    @DisplayName("broadcastOperationalNotification truncates metadata exceeding 500 characters")
    void testBroadcastTruncatesOversizedMetadata() {
        when(passengerRepository.findAll()).thenReturn(List.of(passenger1));

        String longText = "X".repeat(600);
        OperationalBroadcastRequest req = OperationalBroadcastRequest.builder()
                .title("Maintenance Alert")
                .message("Water pump test")
                .category(NotificationCategory.OPERATIONAL)
                .scope(NotificationScope.PASSENGER_ONLY)
                .metadata(Map.of("details", longText))
                .build();

        service.broadcastOperationalNotification(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PassengerNotification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<PassengerNotification> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMetadata()).isNotNull();
        assertThat(saved.get(0).getMetadata().length()).isLessThanOrEqualTo(500);
    }
}
