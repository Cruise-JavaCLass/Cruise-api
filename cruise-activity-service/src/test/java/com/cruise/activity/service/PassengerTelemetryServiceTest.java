package com.cruise.activity.service;

import com.cruise.activity.entity.*;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerTelemetryServiceTest {
    @Mock BleBeaconDeviceRepository beacons;
    @Mock DeckZoneRepository zones;
    @Mock PassengerLocationLogRepository history;
    private final Instant now=Instant.parse("2026-11-21T00:00:00Z");
    private PassengerTelemetryService telemetry;
    private TelemetryBuffer buffer;
    @BeforeEach void setup() {
        buffer=new TelemetryBuffer(2);
        telemetry=new PassengerTelemetryService(beacons,zones,history,buffer,Clock.fixed(now,ZoneOffset.UTC),Duration.ofMinutes(2));
        BleBeaconDevice beacon=new BleBeaconDevice(); beacon.setId(1L); beacon.setPassengerId(7L);
        DeckZone zone=new DeckZone(); zone.setDeckNumber(9); zone.setZoneCode("DECK-09-POOL");
        lenient().when(beacons.findByMacAddress("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(beacon));
        lenient().when(zones.findByZoneCode("DECK-09-POOL")).thenReturn(Optional.of(zone));
    }
    @Test void acceptsWithoutSynchronousHistoryWritesAndIgnoresOutOfOrderPosition() {
        telemetry.recordBeaconPing("aa:bb:cc:dd:ee:ff","DECK-09-POOL",-50,now);
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-80,now.minusSeconds(20));
        assertThat(telemetry.getLastKnownLocation(7L).orElseThrow().rssiSignalStrength()).isEqualTo(-50);
        assertThat(buffer.size()).isEqualTo(2); verifyNoInteractions(history);
        verify(beacons,times(1)).findByMacAddress(anyString());
    }
    @Test void saturationRejectsBeforeChangingLocation() {
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-80,now.minusSeconds(2));
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-60,now.minusSeconds(1));
        assertThatThrownBy(()->telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-30,now))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("429");
        assertThat(telemetry.getLastKnownLocation(7L).orElseThrow().rssiSignalStrength()).isEqualTo(-60);
    }
    @Test void expiredAcceptedPositionIsExplicitlyStaleAndRemainsAvailableUntilFlush() {
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-50,now.minusSeconds(121));
        telemetry.evictExpiredLocations();
        assertThat(telemetry.getLastKnownLocation(7L).orElseThrow().fresh()).isFalse();
        assertThat(telemetry.getLastKnownLocation(7L).orElseThrow().zoneCode()).isEqualTo("DECK-09-POOL");
    }
    @Test void rejectsUnknownAndFuturePings() {
        assertThatThrownBy(()->telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-50,now.plusSeconds(31))).hasMessageContaining("Invalid");
        assertThatThrownBy(()->telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","UNKNOWN",-50,now)).hasMessageContaining("Unknown deck");
        assertThat(buffer.size()).isZero();
    }
    @Test void failedDatabaseBatchIsRetainedAndRetriedWithoutDrainingNewerSamples() {
        TelemetryBatchWriter writer=mock(TelemetryBatchWriter.class);
        TelemetryBatchFlusher flusher=new TelemetryBatchFlusher(buffer,writer);
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-50,now);
        doThrow(new IllegalStateException("database offline")).doNothing().when(writer).write(anyList());
        assertThatThrownBy(flusher::flushPending).hasMessageContaining("offline");
        telemetry.recordBeaconPing("AA:BB:CC:DD:EE:FF","DECK-09-POOL",-40,now);
        flusher.flushPending();
        @SuppressWarnings("unchecked") ArgumentCaptor<List<TelemetryBuffer.Sample>> batches=ArgumentCaptor.forClass(List.class);
        verify(writer,times(2)).write(batches.capture());
        assertThat(batches.getAllValues().get(0)).isSameAs(batches.getAllValues().get(1));
        assertThat(buffer.size()).isEqualTo(1);
        flusher.flushPending(); assertThat(buffer.size()).isZero();
    }
}
