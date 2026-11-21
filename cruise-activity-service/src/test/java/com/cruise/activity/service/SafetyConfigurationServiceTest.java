package com.cruise.activity.service;
import com.cruise.activity.dto.SafetyConfigurationDtos.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyConfigurationServiceTest {
    @Mock DeckZoneRepository zones;
    @Mock MusterStationRepository stations;
    @Mock BleBeaconDeviceRepository beacons;
    @Mock MusterAssignmentRepository assignments;
    @Mock PassengerRepository passengers;
    @Mock PassengerTelemetryService telemetry;
    @InjectMocks SafetyConfigurationService service;
    private void knownPassenger() {Passenger p=new Passenger();p.setActive(true);when(passengers.findById(7L)).thenReturn(Optional.of(p));}
    private void knownStation() {when(stations.findByStationCode("STATION-A")).thenReturn(Optional.of(new MusterStation()));}
    @Test void stationMetadataIsNormalizedAndInvalidationOccursOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            MusterStation station=service.configureStation(new StationDefinition("station-a","officer","LIFEBOAT-1",100));
            assertThat(station.getStationCode()).isEqualTo("STATION-A");assertThat(station.getCapacity()).isEqualTo(100);
            verifyNoInteractions(telemetry);
            TransactionSynchronizationManager.getSynchronizations().forEach(callback->callback.afterCommit());
            verify(telemetry).invalidateConfiguration();
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void musterZonesRequireARealStationWhileOrdinaryZonesDoNot() {
        assertThatThrownBy(()->service.configureZone(new ZoneDefinition("POOL","Pool",9,true,null,20))).hasMessageContaining("requires its station");
        knownStation();DeckZone zone=service.configureZone(new ZoneDefinition("MUSTER","Assembly",5,true,"station-a",100));
        assertThat(zone.getMusterStationCode()).isEqualTo("STATION-A");assertThat(zone.isMusterStation()).isTrue();
        assertThat(service.configureZone(new ZoneDefinition("POOL","Pool",9,false,null,20)).isMusterStation()).isFalse();
    }
    @Test void beaconsRequireActivePassengersAndNormalizeMacAddress() {
        assertThatThrownBy(()->service.configureBeacon(new BeaconDefinition("aa:bb:cc:dd:ee:ff",7L,70))).hasMessageContaining("Unknown active passenger");
        knownPassenger();BleBeaconDevice beacon=service.configureBeacon(new BeaconDefinition("aa:bb:cc:dd:ee:ff",7L,70));
        assertThat(beacon.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:FF");assertThat(beacon.getPassengerId()).isEqualTo(7L);
    }
    @Test void assignmentUsesConfiguredStationAndRejectsUnknownStation() {
        knownPassenger();
        assertThatThrownBy(()->service.assign(new AssignmentDefinition(7L,"STATION-A"))).hasMessageContaining("Unknown station");
        knownStation();when(assignments.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));
        assertThat(service.assign(new AssignmentDefinition(7L,"station-a")).getStationCode()).isEqualTo("STATION-A");
    }
}
