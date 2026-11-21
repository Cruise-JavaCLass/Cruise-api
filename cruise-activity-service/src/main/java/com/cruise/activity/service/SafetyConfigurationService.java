package com.cruise.activity.service;
import com.cruise.activity.dto.SafetyConfigurationDtos.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class SafetyConfigurationService {
    private final DeckZoneRepository zones;
    private final MusterStationRepository stations;
    private final BleBeaconDeviceRepository beacons;
    private final MusterAssignmentRepository assignments;
    private final PassengerRepository passengers;
    private final PassengerTelemetryService telemetry;
    @Transactional public MusterStation configureStation(StationDefinition request) {
        String code=MusterDrillService.canonicalStation(request.stationCode());
        MusterStation station=stations.findByStationCode(code).orElseGet(MusterStation::new);
        station.setStationCode(code); station.setMusterLeaderStaffId(request.musterLeaderStaffId());
        station.setTargetLifeboatNumber(request.targetLifeboatNumber()); station.setCapacity(request.capacity());
        stations.saveAndFlush(station); invalidateAfterCommit(); return station;
    }
    @Transactional public DeckZone configureZone(ZoneDefinition request) {
        if (request.musterStation() != (request.musterStationCode()!=null && !request.musterStationCode().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Muster zone requires its station code; other zones must not specify one");
        }
        String station=request.musterStation()?requireStation(request.musterStationCode()):null;
        DeckZone zone=zones.findByZoneCode(request.zoneCode()).orElseGet(DeckZone::new);
        zone.setZoneCode(request.zoneCode()); zone.setZoneName(request.zoneName()); zone.setDeckNumber(request.deckNumber());
        zone.setMusterStation(request.musterStation()); zone.setMusterStationCode(station); zone.setCapacity(request.capacity());
        zones.saveAndFlush(zone); invalidateAfterCommit(); return zone;
    }
    @Transactional public BleBeaconDevice configureBeacon(BeaconDefinition request) {
        requirePassenger(request.passengerId());
        String mac=request.macAddress().toUpperCase(Locale.ROOT);
        BleBeaconDevice beacon=beacons.findByMacAddress(mac).orElseGet(BleBeaconDevice::new);
        beacon.setMacAddress(mac); beacon.setPassengerId(request.passengerId()); beacon.setBatteryLevel(request.batteryLevel());
        beacons.saveAndFlush(beacon); invalidateAfterCommit(); return beacon;
    }
    @Transactional public MusterAssignment assign(AssignmentDefinition request) {
        requirePassenger(request.passengerId()); String station=requireStation(request.stationCode());
        MusterAssignment assignment=assignments.findByPassengerId(request.passengerId()).orElseGet(MusterAssignment::new);
        assignment.setPassengerId(request.passengerId()); assignment.setStationCode(station);
        return assignments.saveAndFlush(assignment);
    }
    private String requireStation(String code) {
        String canonical=MusterDrillService.canonicalStation(code);
        stations.findByStationCode(canonical).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown station"));
        return canonical;
    }
    private void requirePassenger(Long id) {
        passengers.findById(id).filter(p->Boolean.TRUE.equals(p.getActive()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown active passenger"));
    }
    private void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { telemetry.invalidateConfiguration(); }
            });
        } else telemetry.invalidateConfiguration();
    }
}
