package com.cruise.activity.dto;

import com.cruise.activity.enums.SolasEmergencyType;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class MusterSafetyDtos {
    private MusterSafetyDtos() {}
    public enum CheckinMethod { RFID, NFC, BLE, MANUAL }
    public enum PassengerStatus { MISSING, WRONG_MUSTER_STATION, ARRIVED }
    public record DrillStartRequest(@NotNull SolasEmergencyType drillType,@NotNull @Positive Long tourId) {}
    public record MusterArrivalRequest(@NotNull @Positive Long drillId,@NotBlank @Size(max=32) String stationCode,
        @NotNull @Positive Long passengerId,@NotNull CheckinMethod method) {}
    public record ArrivalResult(Long drillId,Long passengerId,String assignedStation,String observedStation,
        PassengerStatus status,boolean duplicate) {}
    public record StationSummary(String stationCode,int totalPassengers,int arrived,int missing) {}
    public record LiveDashboard(Long drillId,Long tourId,String drillType,String status,int totalPassengers,
        int arrived,int missing,double completionPercentage,List<StationSummary> stations) {}
    public record MissingPassenger(Long passengerId,String passengerName,String cabinNumber,String assignedStation,
        String observedStation,PassengerStatus status,Integer lastKnownDeck,String lastKnownZone,
        Instant lastLocationRecordedAt,boolean locationFresh) {}
}
