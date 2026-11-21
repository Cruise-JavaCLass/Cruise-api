package com.cruise.activity.service;

import com.cruise.activity.dto.SafetyTelemetryDtos.LastKnownLocation;
import com.cruise.activity.entity.*;
import com.cruise.activity.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PassengerTelemetryService {
    private final BleBeaconDeviceRepository beacons;
    private final DeckZoneRepository zones;
    private final PassengerLocationLogRepository history;
    private final TelemetryBuffer buffer;
    private final Clock clock;
    private final Duration ttl;
    private final Map<String,BleBeaconDevice> knownBeacons = new ConcurrentHashMap<>();
    private final Map<String,DeckZone> knownZones = new ConcurrentHashMap<>();
    private final Map<Long,PassengerLocationLog> latest = new ConcurrentHashMap<>();
    private final Map<Long,PassengerLocationLog> lastAccepted = new ConcurrentHashMap<>();

    public PassengerTelemetryService(BleBeaconDeviceRepository beacons, DeckZoneRepository zones,
            PassengerLocationLogRepository history, TelemetryBuffer buffer, Clock clock,
            @Value("${cruise.safety.telemetry.ttl:PT2M}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("TTL must be positive");
        this.beacons=beacons; this.zones=zones; this.history=history; this.buffer=buffer; this.clock=clock; this.ttl=ttl;
    }
    public LastKnownLocation recordBeaconPing(String macAddress, String zoneCode, int rssi, Instant timestamp) {
        if (macAddress==null || !macAddress.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
                || zoneCode==null || zoneCode.isBlank() || timestamp==null || rssi < -127 || rssi > 20
                || timestamp.isAfter(clock.instant().plusSeconds(30))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid beacon ping");
        }
        BleBeaconDevice beacon = knownBeacons.computeIfAbsent(macAddress.toUpperCase(Locale.ROOT),
            key -> beacons.findByMacAddress(key).orElseThrow(() -> notFound("Unknown beacon")));
        DeckZone zone = knownZones.computeIfAbsent(zoneCode,
            key -> zones.findByZoneCode(key).orElseThrow(() -> notFound("Unknown deck zone")));
        PassengerLocationLog log = new PassengerLocationLog();
        log.setPassengerId(beacon.getPassengerId()); log.setDeckNumber(zone.getDeckNumber());
        log.setZoneCode(zone.getZoneCode()); log.setRssiSignalStrength(rssi); log.setRecordedAt(timestamp);
        synchronized (buffer) {
            // Cache mutation happens only after bounded admission; rejected pings have no side effects.
            buffer.offer(new TelemetryBuffer.Sample(beacon.getId(),log));
            latest.compute(log.getPassengerId(), (id, previous) -> previous==null
                || !timestamp.isBefore(previous.getRecordedAt()) ? log : previous);
            lastAccepted.compute(log.getPassengerId(), (id, previous) -> previous==null
                || !timestamp.isBefore(previous.getRecordedAt()) ? log : previous);
        }
        return location(latest.get(log.getPassengerId()));
    }
    public Map<Long,LastKnownLocation> getLastKnownLocations(Collection<Long> passengerIds) {
        Map<Long,LastKnownLocation> result = new HashMap<>();
        List<Long> uncached = new ArrayList<>();
        for (Long id : passengerIds) {
            PassengerLocationLog cached = latest.get(id);
            if (cached != null && fresh(cached)) result.put(id,location(cached));
            else {
                if (cached!=null) latest.remove(id,cached);
                // Recovery positions remain available as explicitly stale after the live-cache TTL.
                PassengerLocationLog accepted=lastAccepted.get(id);
                if (accepted!=null) result.put(id,location(accepted));
                uncached.add(id);
            }
        }
        if (!uncached.isEmpty()) for (PassengerLocationLog log : history.findLatestForPassengers(uncached)) {
            LastKnownLocation previous=result.get(log.getPassengerId());
            if (previous==null || log.getRecordedAt().isAfter(previous.recordedAt())) result.put(log.getPassengerId(),location(log));
            if (fresh(log)) latest.merge(log.getPassengerId(),log,(a,b)->a.getRecordedAt().isAfter(b.getRecordedAt())?a:b);
        }
        return Map.copyOf(result);
    }
    public Optional<LastKnownLocation> getLastKnownLocation(Long passengerId) {
        return Optional.ofNullable(getLastKnownLocations(List.of(passengerId)).get(passengerId));
    }
    public void invalidateConfiguration() { knownBeacons.clear(); knownZones.clear(); }
    private boolean fresh(PassengerLocationLog log) { return log.getRecordedAt().plus(ttl).isAfter(clock.instant()); }
    private LastKnownLocation location(PassengerLocationLog log) {
        return new LastKnownLocation(log.getPassengerId(),log.getDeckNumber(),log.getZoneCode(),log.getRssiSignalStrength(),log.getRecordedAt(),fresh(log));
    }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
}
