package com.cruise.activity.service;

import com.cruise.activity.dto.MusterSafetyDtos.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.*;
import com.cruise.activity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class MusterDrillService {
    private final SolasMusterIncidentRepository incidents;
    private final SolasMusterCheckinRepository arrivals;
    private final PassengerRepository passengers;
    private final MusterAssignmentRepository assignments;
    private final MusterStationRepository stations;
    private final MusterRosterEntryRepository roster;
    private final PassengerTelemetryService telemetry;
    private final Clock clock;
    private final SolasMusterEmergencyService legacyEmergency;

    @Transactional
    public LiveDashboard closeDrill(Long drillId) {
        legacyEmergency.setAllClear(drillId);
        return getLiveDashboard(drillId,null);
    }

    @Transactional
    public LiveDashboard triggerEmergencyAlarm(SolasEmergencyType drillType,Long tourId,String actor) {
        if (drillType==null || tourId==null || tourId<=0) throw badRequest("Drill type and tour are required");
        List<Passenger> onboard = passengers.findByTourIdAndCheckInStatusAndActiveTrue(tourId,CheckInStatus.BOARDED);
        if (onboard.isEmpty()) throw badRequest("No boarded passengers for this tour");
        Map<Long,String> assigned = assignments.findByPassengerIdIn(onboard.stream().map(Passenger::getId).toList())
            .stream().collect(Collectors.toMap(MusterAssignment::getPassengerId,MusterAssignment::getStationCode));
        Map<String,MusterStation> configured = stations.findAll().stream().collect(Collectors.toMap(MusterStation::getStationCode,Function.identity()));
        Map<String,Integer> occupancy = new HashMap<>();
        for (Passenger passenger : onboard) {
            String code=assigned.get(passenger.getId());
            if (code==null || !configured.containsKey(code)) throw badRequest("Every boarded passenger needs a configured muster assignment");
            if (occupancy.merge(code,1,Integer::sum)>configured.get(code).getCapacity()) throw badRequest("Muster station capacity exceeded: "+code);
        }
        SolasMusterIncident incident = SolasMusterIncident.builder().incidentCode("SOLAS-"+UUID.randomUUID())
            .tourId(tourId).activeTourId(tourId).emergencyType(drillType)
            .status(drillType==SolasEmergencyType.DRILL_PRACTICE ? MusterStatus.DRILL_IN_PROGRESS : MusterStatus.ACTIVE_MUSTER)
            .triggeredBy(actor).announcementText("Assemble at your assigned muster station")
            .acousticSignalCode("7_SHORT_1_LONG").totalSoulsOnBoard(onboard.size()).musteredSoulsCount(0)
            .triggeredAt(clock.instant()).build();
        incidents.saveAndFlush(incident);
        roster.saveAll(onboard.stream().map(passenger -> {
            MusterRosterEntry entry=new MusterRosterEntry(); entry.setIncidentId(incident.getId());
            entry.setPassengerId(passenger.getId()); entry.setPassengerName(passenger.getFullName());
            entry.setCabinNumber(passenger.getCabinNumber()); entry.setStationCode(assigned.get(passenger.getId()));
            return entry;
        }).toList());
        return getLiveDashboard(incident.getId(),null);
    }

    @Transactional
    public ArrivalResult recordMusterArrival(Long drillId,String stationCode,Long passengerId,CheckinMethod method) {
        String station=canonicalStation(stationCode);
        if (method==null) throw badRequest("Check-in method required");
        SolasMusterIncident incident=incidents.findByIdForUpdate(drillId).orElseThrow(()->notFound("Unknown drill"));
        if (!active(incident)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Drill is not active");
        if (stations.findByStationCode(station).isEmpty()) throw notFound("Unknown muster station");
        MusterRosterEntry entry=roster.findByIncidentIdAndPassengerId(drillId,passengerId)
            .orElseThrow(()->notFound("Passenger is not in this drill roster"));
        Optional<SolasMusterCheckin> existing=arrivals.findByIncidentIdAndPassengerId(drillId,passengerId);
        boolean alreadyCorrect=existing.filter(item->entry.getStationCode().equals(item.getMusterStationCode())).isPresent();
        if (alreadyCorrect) return new ArrivalResult(drillId,passengerId,entry.getStationCode(),station,PassengerStatus.ARRIVED,true);
        entry.setLastObservedStation(station); entry.setCheckinMethod(method.name()); roster.save(entry);
        if (!entry.getStationCode().equals(station)) {
            return new ArrivalResult(drillId,passengerId,entry.getStationCode(),station,PassengerStatus.WRONG_MUSTER_STATION,false);
        }
        SolasMusterCheckin arrival=existing.orElseGet(SolasMusterCheckin::new);
        arrival.setIncidentId(drillId); arrival.setPassengerId(passengerId); arrival.setMusterStationCode(station);
        arrival.setLifejacketVerified(false); // Presence does not establish lifejacket verification.
        arrival.setCheckedInBy(method.name()); arrival.setCheckedInAt(clock.instant()); arrivals.saveAndFlush(arrival);
        // Incident row lock serializes all writers, including the existing legacy endpoint.
        incident.setMusteredSoulsCount(Math.toIntExact(roster.countCorrectArrivals(drillId)));
        incidents.save(incident);
        return new ArrivalResult(drillId,passengerId,entry.getStationCode(),station,PassengerStatus.ARRIVED,false);
    }

    @Transactional(readOnly=true)
    public LiveDashboard getLiveDashboard(Long drillId,String stationCode) {
        SolasMusterIncident incident=incidents.findById(drillId).orElseThrow(()->notFound("Unknown drill"));
        List<MusterRosterEntry> entries=roster.findByIncidentIdOrderByPassengerIdAsc(drillId);
        Map<Long,SolasMusterCheckin> checked=arrivals.findByIncidentId(drillId).stream().collect(Collectors.toMap(SolasMusterCheckin::getPassengerId,Function.identity()));
        List<StationSummary> summary=new ArrayList<>(); int total=0; int arrived=0;
        String filter=stationCode==null ? null : canonicalStation(stationCode);
        for (MusterStation station : stations.findAll().stream().sorted(Comparator.comparing(MusterStation::getStationCode)).toList()) {
            if (filter!=null && !filter.equals(station.getStationCode())) continue;
            List<MusterRosterEntry> group=entries.stream().filter(e->station.getStationCode().equals(e.getStationCode())).toList();
            int correct=(int)group.stream().filter(e->isCorrect(e,checked.get(e.getPassengerId()))).count();
            summary.add(new StationSummary(station.getStationCode(),group.size(),correct,group.size()-correct));
            total+=group.size(); arrived+=correct;
        }
        return new LiveDashboard(drillId,incident.getTourId(),incident.getEmergencyType().name(),incident.getStatus().name(),
            total,arrived,total-arrived,total==0 ? 0 : Math.round(arrived*1000.0/total)/10.0,List.copyOf(summary));
    }

    @Transactional(readOnly=true)
    public List<MissingPassenger> getMissingPassengersSummary(Long drillId,String stationCode,PassengerStatus filter) {
        if (!incidents.existsById(drillId)) throw notFound("Unknown drill");
        List<MusterRosterEntry> entries=roster.findByIncidentIdOrderByPassengerIdAsc(drillId);
        Map<Long,SolasMusterCheckin> checked=arrivals.findByIncidentId(drillId).stream().collect(Collectors.toMap(SolasMusterCheckin::getPassengerId,Function.identity()));
        String station=stationCode==null ? null : canonicalStation(stationCode);
        List<MusterRosterEntry> selected=entries.stream().filter(e->station==null || station.equals(e.getStationCode()))
            .filter(e->filter==null ? status(e,checked.get(e.getPassengerId()))!=PassengerStatus.ARRIVED : status(e,checked.get(e.getPassengerId()))==filter).toList();
        var locations=telemetry.getLastKnownLocations(selected.stream().map(MusterRosterEntry::getPassengerId).toList());
        return selected.stream().map(e->{
            var location=locations.get(e.getPassengerId());
            return new MissingPassenger(e.getPassengerId(),e.getPassengerName(),e.getCabinNumber(),e.getStationCode(),
                e.getLastObservedStation(),status(e,checked.get(e.getPassengerId())),location==null?null:location.deckNumber(),
                location==null?null:location.zoneCode(),location==null?null:location.recordedAt(),location!=null&&location.fresh());
        }).toList();
    }
    private static boolean isCorrect(MusterRosterEntry entry,SolasMusterCheckin checkin) { return checkin!=null && entry.getStationCode().equals(checkin.getMusterStationCode()); }
    private static PassengerStatus status(MusterRosterEntry entry,SolasMusterCheckin checkin) {
        if (isCorrect(entry,checkin)) return PassengerStatus.ARRIVED;
        return entry.getLastObservedStation()!=null || checkin!=null ? PassengerStatus.WRONG_MUSTER_STATION : PassengerStatus.MISSING;
    }
    private static boolean active(SolasMusterIncident incident) { return incident.getStatus()==MusterStatus.ACTIVE_MUSTER || incident.getStatus()==MusterStatus.DRILL_IN_PROGRESS; }
    public static String canonicalStation(String station) {
        if (station==null || station.isBlank() || station.length()>32) throw badRequest("Invalid station code");
        return station.trim().toUpperCase(Locale.ROOT);
    }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
}
