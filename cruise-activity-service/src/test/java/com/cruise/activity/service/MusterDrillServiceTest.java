package com.cruise.activity.service;

import com.cruise.activity.dto.MusterSafetyDtos.*;
import com.cruise.activity.entity.*;
import com.cruise.activity.enums.*;
import com.cruise.activity.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"cruise.safety.telemetry.scheduling-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:safety_load;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
    "spring.datasource.hikari.maximum-pool-size=16","spring.datasource.hikari.connection-timeout=60000"})
class MusterDrillServiceTest {
    @Autowired MusterDrillService service;
    @Autowired PassengerRepository passengers;
    @Autowired MusterAssignmentRepository assignments;
    @Autowired MusterStationRepository stations;
    @Autowired MusterRosterEntryRepository roster;
    @Autowired SolasMusterCheckinRepository checkins;
    @Autowired SolasMusterIncidentRepository incidents;
    @Autowired PassengerTelemetryService telemetry;
    @Autowired BleBeaconDeviceRepository beacons;
    @Autowired DeckZoneRepository zones;
    @Autowired PassengerLocationLogRepository locations;
    @Autowired TelemetryBatchFlusher flusher;
    @Autowired TelemetryBuffer buffer;
    private static long tourSequence=7000;
    private Long tourId;
    @BeforeEach void setup() {
        tourId=++tourSequence;
        for (String code : List.of("STATION-A","STATION-B","STATION-C","STATION-D")) {
            if (stations.findByStationCode(code).isEmpty()) {
                MusterStation station=new MusterStation(); station.setStationCode(code); station.setCapacity(1000);
                station.setMusterLeaderStaffId("officer"); station.setTargetLifeboatNumber(code); stations.saveAndFlush(station);
            }
        }
    }
    private Passenger passenger(String code) {
        Passenger passenger=Passenger.builder().tourId(tourId).fullName("Passenger "+UUID.randomUUID()).cabinNumber("901")
            .checkInStatus(CheckInStatus.BOARDED).active(true).build();
        passengers.saveAndFlush(passenger);
        MusterAssignment assignment=new MusterAssignment(); assignment.setPassengerId(passenger.getId()); assignment.setStationCode(code);
        assignments.saveAndFlush(assignment); return passenger;
    }
    private Long start() { return service.triggerEmergencyAlarm(SolasEmergencyType.DRILL_PRACTICE,tourId,"officer").drillId(); }
    @Test void wrongStationRemainsMissingUntilArrivalAtAssignedStationAndDuplicateDoesNotCountTwice() {
        Passenger first=passenger("STATION-A"); Passenger second=passenger("STATION-B"); Long drill=start();
        assertThat(service.recordMusterArrival(drill,"STATION-B",first.getId(),CheckinMethod.NFC).status()).isEqualTo(PassengerStatus.WRONG_MUSTER_STATION);
        assertThat(service.getMissingPassengersSummary(drill,"STATION-A",null)).extracting(MissingPassenger::passengerId).containsExactly(first.getId());
        assertThat(service.getLiveDashboard(drill,null).arrived()).isZero();
        service.recordMusterArrival(drill,"STATION-A",first.getId(),CheckinMethod.RFID);
        assertThat(service.recordMusterArrival(drill,"STATION-A",first.getId(),CheckinMethod.RFID).duplicate()).isTrue();
        assertThat(checkins.countByIncidentId(drill)).isEqualTo(1);
        assertThat(service.getMissingPassengersSummary(drill,null,null)).extracting(MissingPassenger::passengerId).containsExactly(second.getId());
        assertThat(service.getLiveDashboard(drill,null).completionPercentage()).isEqualTo(50.0);
    }
    @Test void missingSummaryIncludesLatestLocationAndSnapshotSurvivesAssignmentChanges() {
        Passenger passenger=passenger("STATION-A");
        DeckZone zone=new DeckZone(); zone.setZoneCode("ZONE-"+tourId); zone.setZoneName("Pool"); zone.setDeckNumber(9); zone.setCapacity(100); zones.saveAndFlush(zone);
        BleBeaconDevice beacon=new BleBeaconDevice(); beacon.setMacAddress("AA:BB:CC:DD:EE:01"); beacon.setPassengerId(passenger.getId()); beacons.saveAndFlush(beacon);
        Long drill=start(); telemetry.invalidateConfiguration();
        telemetry.recordBeaconPing(beacon.getMacAddress(),zone.getZoneCode(),-55,Instant.now());
        flusher.flushPending();
        MusterAssignment assignment=assignments.findByPassengerId(passenger.getId()).orElseThrow(); assignment.setStationCode("STATION-B"); assignments.saveAndFlush(assignment);
        MissingPassenger missing=service.getMissingPassengersSummary(drill,null,null).getFirst();
        assertThat(missing.assignedStation()).isEqualTo("STATION-A"); assertThat(missing.lastKnownDeck()).isEqualTo(9);
        assertThat(missing.lastKnownZone()).isEqualTo(zone.getZoneCode()); assertThat(missing.locationFresh()).isTrue();
    }
    @Test void concurrent1000CheckinsHaveNoDuplicatesOrDeadlock() throws Exception {
        List<Passenger> people=new ArrayList<>(); for (int i=0;i<200;i++) people.add(passenger("STATION-A"));
        Long drill=start(); CountDownLatch start=new CountDownLatch(1);
        for(int i=0;i<5;i++) service.getMissingPassengersSummary(drill,null,null);
        long[] missingTimings=new long[15];
        for(int i=0;i<missingTimings.length;i++){long before=System.nanoTime();assertThat(service.getMissingPassengersSummary(drill,null,null)).hasSize(200);missingTimings[i]=System.nanoTime()-before;}
        Arrays.sort(missingTimings);double missingMedian=missingTimings[missingTimings.length/2]/1_000_000.0;
        System.out.printf("SAFETY_MISSING_FULL_ROSTER passengers=200 median_ms=%.3f%n",missingMedian);assertThat(missingMedian).isLessThan(50.0);
        long began=System.nanoTime();
        try (ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ArrivalResult>> requests=new ArrayList<>();
            for (int i=0;i<1000;i++) {
                Long id=people.get(i%people.size()).getId();
                requests.add(executor.submit(()->{ start.await(); return service.recordMusterArrival(drill,"STATION-A",id,CheckinMethod.BLE); }));
            }
            start.countDown();
            for (Future<ArrivalResult> request:requests) assertThat(request.get(90,TimeUnit.SECONDS).status()).isEqualTo(PassengerStatus.ARRIVED);
        }
        assertThat(checkins.countByIncidentId(drill)).isEqualTo(200); assertThat(service.getLiveDashboard(drill,null).arrived()).isEqualTo(200);
        assertThat(service.getMissingPassengersSummary(drill,null,null)).isEmpty();
        System.out.printf("SAFETY_LOAD checkins=1000 unique=200 elapsed_ms=%.3f%n",(System.nanoTime()-began)/1_000_000.0);
        for(int i=0;i<5;i++) service.getMissingPassengersSummary(drill,null,null);
        long[] durations=new long[15];
        for(int i=0;i<durations.length;i++){ long before=System.nanoTime(); service.getMissingPassengersSummary(drill,null,null); durations[i]=System.nanoTime()-before; }
        Arrays.sort(durations); double medianMs=durations[durations.length/2]/1_000_000.0;
        System.out.printf("SAFETY_RECONCILIATION median_ms=%.3f%n",medianMs); assertThat(medianMs).isLessThan(50.0);
    }
    @Test void ingestsAtLeast500PingsPerSecondWithoutDatabaseWritesOnTheIngestionThread() {
        Passenger passenger=passenger("STATION-A");
        String mac="AA:BB:CC:DD:EE:02";
        BleBeaconDevice beacon=new BleBeaconDevice(); beacon.setMacAddress(mac); beacon.setPassengerId(passenger.getId()); beacons.saveAndFlush(beacon);
        DeckZone zone=new DeckZone(); zone.setZoneCode("LOAD-"+tourId); zone.setZoneName("Deck"); zone.setDeckNumber(5); zone.setCapacity(100); zones.saveAndFlush(zone);
        telemetry.invalidateConfiguration(); telemetry.recordBeaconPing(mac,zone.getZoneCode(),-50,Instant.now()); flusher.flushPending();
        long previous=locations.count(); long began=System.nanoTime(); Instant timestamp=Instant.now();
        for(int i=0;i<5000;i++) telemetry.recordBeaconPing(mac,zone.getZoneCode(),-50,timestamp);
        double perSecond=5000.0/((System.nanoTime()-began)/1_000_000_000.0);
        assertThat(locations.count()).isEqualTo(previous); assertThat(buffer.size()).isEqualTo(5000);
        assertThat(perSecond).isGreaterThanOrEqualTo(500.0);
        while(buffer.size()>0) flusher.flushPending();
        assertThat(locations.count()).isEqualTo(previous+5000);
        System.out.printf("SAFETY_TELEMETRY pings=5000 rate_per_second=%.3f%n",perSecond);
    }
    @Test void refusesRosterWithUnassignedPassengersAndOutsiders() {
        Passenger person=passenger("STATION-A"); Long drill=start();
        assertThatThrownBy(()->service.recordMusterArrival(drill,"STATION-A",Long.MAX_VALUE,CheckinMethod.NFC)).hasMessageContaining("not in this drill");
        MusterAssignment assignment=assignments.findByPassengerId(person.getId()).orElseThrow(); assignments.delete(assignment);
        assertThatThrownBy(()->service.triggerEmergencyAlarm(SolasEmergencyType.FIRE_ALARM,tourId,"officer")).hasMessageContaining("needs a configured");
        assertThatThrownBy(()->service.triggerEmergencyAlarm(SolasEmergencyType.FIRE_ALARM,tourId+100000,"officer")).hasMessageContaining("No boarded");
    }
    @Test void closingAnIncidentStopsArrivalsAndReleasesTourForNextDrill() {
        Passenger person=passenger("STATION-A");Long drill=start();
        assertThat(service.closeDrill(drill).status()).isEqualTo("ALL_CLEAR");
        assertThatThrownBy(()->service.recordMusterArrival(drill,"STATION-A",person.getId(),CheckinMethod.BLE)).hasMessageContaining("not active");
        assertThat(start()).isNotEqualTo(drill);
    }
}
