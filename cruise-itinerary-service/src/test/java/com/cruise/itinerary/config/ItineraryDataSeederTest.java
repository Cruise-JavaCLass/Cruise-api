package com.cruise.itinerary.config;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryDataSeeder Unit Tests")
class ItineraryDataSeederTest {

    @Mock
    private CruiseShipRepository shipRepository;

    @Mock
    private PortRepository portRepository;

    @Mock
    private CruiseTourRepository tourRepository;

    @Mock
    private DailyItineraryRepository dailyItineraryRepository;

    @Mock
    private CruiseStopRepository stopRepository;

    @Mock
    private ShipDeckZoneRepository deckZoneRepository;

    @Mock
    private SystemCancellationPolicyRepository cancellationPolicyRepository;

    @InjectMocks
    private ItineraryDataSeeder dataSeeder;

    @Test
    @DisplayName("run seeds initial data when repositories are empty")
    void testRunSeedsWhenEmpty() {
        when(shipRepository.findByCode("SHIP-001")).thenReturn(Optional.empty());
        CruiseShip mockShip = CruiseShip.builder().name("Grand Pearl Voyager").code("SHIP-001").build();
        mockShip.setId(1L);
        when(shipRepository.save(any(CruiseShip.class))).thenReturn(mockShip);

        when(portRepository.findByCodeIgnoreCase("VNHLG")).thenReturn(Optional.empty());
        Port mockPort1 = Port.builder().name("Cảng Quốc Tế Hạ Long").code("VNHLG").build();
        mockPort1.setId(1L);

        when(portRepository.findByCodeIgnoreCase("VNDAD")).thenReturn(Optional.empty());
        Port mockPort2 = Port.builder().name("Cảng Tiên Sa Đà Nẵng").code("VNDAD").build();
        mockPort2.setId(2L);

        when(portRepository.save(any(Port.class))).thenReturn(mockPort1).thenReturn(mockPort2);

        when(tourRepository.count()).thenReturn(0L);
        CruiseTour mockTour = CruiseTour.builder().name("Hải Trình Kỳ Quan Hạ Long - Đà Nẵng").code("TOUR-001").build();
        mockTour.setId(1L);
        when(tourRepository.save(any(CruiseTour.class))).thenReturn(mockTour);

        when(deckZoneRepository.findByShipIdOrderByDeckNumberAsc(1L)).thenReturn(Collections.emptyList());
        when(cancellationPolicyRepository.count()).thenReturn(0L);

        dataSeeder.run(new DefaultApplicationArguments(new String[0]));

        verify(shipRepository, atLeastOnce()).save(any());
        verify(portRepository, atLeast(2)).save(any());
        verify(tourRepository, times(1)).save(any());
        verify(dailyItineraryRepository, times(3)).save(any());
        verify(stopRepository, times(2)).save(any());
        verify(deckZoneRepository, times(3)).save(any());
        verify(cancellationPolicyRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("run skips seeding when data already exists")
    void testRunSkipsWhenPopulated() {
        CruiseShip existingShip = CruiseShip.builder().name("Existing").code("SHIP-001").build();
        existingShip.setId(1L);
        when(shipRepository.findByCode("SHIP-001")).thenReturn(Optional.of(existingShip));

        Port existingPort = Port.builder().name("Existing Port").code("VNHLG").build();
        existingPort.setId(1L);
        when(portRepository.findByCodeIgnoreCase("VNHLG")).thenReturn(Optional.of(existingPort));
        when(portRepository.findByCodeIgnoreCase("VNDAD")).thenReturn(Optional.of(existingPort));

        when(tourRepository.count()).thenReturn(5L);
        when(deckZoneRepository.findByShipIdOrderByDeckNumberAsc(1L)).thenReturn(Collections.singletonList(mock()));
        when(cancellationPolicyRepository.count()).thenReturn(3L);

        dataSeeder.run(new DefaultApplicationArguments(new String[0]));

        verify(shipRepository, never()).save(any());
        verify(tourRepository, never()).save(any());
        verify(dailyItineraryRepository, never()).save(any());
        verify(stopRepository, never()).save(any());
    }
}
