package com.cruise.activity.config;

import com.cruise.activity.entity.*;
import com.cruise.activity.repository.*;
import com.cruise.activity.service.AllAboardWatchdogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityDataSeeder Unit Tests")
class ActivityDataSeederTest {

    @Mock
    private OnboardActivityRepository activityRepository;

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private TourProviderRepository providerRepository;

    @Mock
    private RfidCardInventoryRepository rfidRepository;

    @Mock
    private SolasMusterIncidentRepository incidentRepository;

    @Mock
    private PassengerNotificationRepository notificationRepository;

    @Mock
    private AllAboardWatchdogService watchdogService;

    @InjectMocks
    private ActivityDataSeeder dataSeeder;

    @Test
    @DisplayName("run seeds all default data when repositories are empty")
    void testRunSeedsWhenEmpty() {
        when(providerRepository.existsByCodeIgnoreCase("PROV-001")).thenReturn(false);
        when(activityRepository.count()).thenReturn(0L);
        when(excursionRepository.count()).thenReturn(0L);
        when(rfidRepository.count()).thenReturn(0L);
        when(incidentRepository.count()).thenReturn(0L);
        when(notificationRepository.count()).thenReturn(0L);

        dataSeeder.run(new DefaultApplicationArguments(new String[0]));

        verify(providerRepository, times(1)).save(any(TourProvider.class));
        verify(activityRepository, atLeast(1)).save(any(OnboardActivity.class));
        verify(excursionRepository, atLeast(1)).save(any(ShoreExcursion.class));
        verify(rfidRepository, atLeast(1)).save(any(RfidCardInventory.class));
        verify(incidentRepository, times(1)).save(any(SolasMusterIncident.class));
        verify(notificationRepository, times(1)).save(any(PassengerNotification.class));
        verify(watchdogService, times(1)).configurePortDepartureSchedule(eq(1L), any(), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("run skips seeding when data is already populated")
    void testRunSkipsWhenPopulated() {
        when(providerRepository.existsByCodeIgnoreCase("PROV-001")).thenReturn(true);
        when(activityRepository.count()).thenReturn(5L);
        when(excursionRepository.count()).thenReturn(3L);
        when(rfidRepository.count()).thenReturn(10L);
        when(incidentRepository.count()).thenReturn(1L);
        when(notificationRepository.count()).thenReturn(2L);

        dataSeeder.run(new DefaultApplicationArguments(new String[0]));

        verify(providerRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
        verify(excursionRepository, never()).save(any());
        verify(rfidRepository, never()).save(any());
        verify(incidentRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }
}
