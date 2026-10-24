package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.GangwayMovementLog;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.enums.GangwayMovementType;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.GangwayMovementLogRepository;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AllAboardWatchdogService {

    public record AlertKey(Long tourId, Long portCallId, Long passengerId) {}

    private final PassengerRepository passengerRepository;
    private final GangwayMovementLogRepository gangwayMovementLogRepository;
    private final PassengerNotificationRepository passengerNotificationRepository;

    // Cache schedules per portCall: portCallId -> schedule info (allAboardTime, departureTime, portName)
    private final Map<Long, PortDepartureSchedule> portSchedules = new ConcurrentHashMap<>();
    // Track alerts sent: AlertKey -> lastAlertInstant
    private final Map<AlertKey, Instant> lastAlertRegistry = new ConcurrentHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortDepartureSchedule {
        private Long portCallId;
        private String portName;
        private Instant allAboardTime;
        private Instant departureTime;
    }

    public void configurePortDepartureSchedule(Long portCallId, String portName, Instant allAboardTime, Instant departureTime) {
        portSchedules.put(portCallId, new PortDepartureSchedule(portCallId, portName, allAboardTime, departureTime));
        log.info("Configured All-Aboard Watchdog schedule for portCallId={}: port={}, allAboard={}, dep={}",
                portCallId, portName, allAboardTime, departureTime);
    }

    @Transactional
    public GangwayMovementLog recordGangwayScan(GangwayScanRequest request) {
        log.info("Processing Gangway scan: tourId={}, portCallId={}, type={}, rfid={}, pId={}",
                request.getTourId(), request.getPortCallId(), request.getMovementType(),
                request.getRfidTag(), request.getPassengerId());

        Passenger passenger = findPassengerByRequest(request);
        if (!Objects.equals(passenger.getTourId(), request.getTourId())) {
            throw new ResourceNotFoundException("Passenger " + passenger.getId()
                    + " does not belong to tourId: " + request.getTourId());
        }

        GangwayMovementLog movementLog = GangwayMovementLog.builder()
                .tourId(request.getTourId())
                .portCallId(request.getPortCallId())
                .passengerId(passenger.getId())
                .passengerName(passenger.getFullName())
                .cabinNumber(passenger.getCabinNumber())
                .rfidTag(passenger.getRfidTag())
                .movementType(request.getMovementType())
                .scannedAt(Instant.now())
                .gangwayTerminalId(request.getGangwayTerminalId() != null ? request.getGangwayTerminalId() : "GANGWAY-1")
                .operatorName(request.getOperatorName() != null ? request.getOperatorName() : "Gangway Officer")
                .build();

        return gangwayMovementLogRepository.save(movementLog);
    }

    @Transactional(readOnly = true)
    public AllAboardStatusDto getAllAboardStatus(Long tourId, Long portCallId) {
        PortDepartureSchedule schedule = portSchedules.get(portCallId);
        if (schedule == null) {
            throw new ResourceNotFoundException("No All-Aboard schedule configured for portCallId: " + portCallId);
        }

        Instant now = Instant.now();
        long secondsRemaining = Duration.between(now, schedule.getAllAboardTime()).getSeconds();
        boolean countdownExpired = secondsRemaining <= 0;

        List<Passenger> tourPassengers = passengerRepository.findByTourIdAndActiveTrue(tourId).stream()
                .filter(p -> p.getCheckInStatus() == CheckInStatus.BOARDED || p.getCheckInStatus() == CheckInStatus.CHECKED_IN)
                .collect(Collectors.toList());

        List<GangwayMovementLog> logs = gangwayMovementLogRepository.findByTourIdAndPortCallIdOrderByScannedAtDesc(tourId, portCallId);

        // Find latest status of each passenger
        Map<Long, GangwayMovementLog> latestLogsByPassenger = new HashMap<>();
        for (GangwayMovementLog gLog : logs) {
            latestLogsByPassenger.putIfAbsent(gLog.getPassengerId(), gLog);
        }

        List<MissingPassengerDto> missingList = new ArrayList<>();
        int onBoardCount = 0;

        for (Passenger passenger : tourPassengers) {
            GangwayMovementLog latestMovement = latestLogsByPassenger.get(passenger.getId());
            if (latestMovement != null && latestMovement.getMovementType() == GangwayMovementType.DISEMBARKED_SHORE) {
                Instant lastAlert = lastAlertRegistry.get(new AlertKey(tourId, portCallId, passenger.getId()));
                missingList.add(MissingPassengerDto.builder()
                        .passengerId(passenger.getId())
                        .fullName(passenger.getFullName())
                        .cabinNumber(passenger.getCabinNumber())
                        .rfidTag(passenger.getRfidTag())
                        .phoneNumber(passenger.getPhoneNumber())
                        .emergencyContact(passenger.getEmergencyContact())
                        .currentStatus(GangwayMovementType.DISEMBARKED_SHORE)
                        .disembarkedAt(latestMovement.getScannedAt())
                        .lastKnownLocation("Shore Excursion / Ashore")
                        .alertSent(lastAlert != null)
                        .lastAlertSentAt(lastAlert)
                        .build());
            } else {
                onBoardCount++;
            }
        }

        int totalAboardTarget = tourPassengers.size();
        double aboardPercentage = totalAboardTarget > 0 ? ((double) onBoardCount / totalAboardTarget) * 100.0 : 100.0;

        return AllAboardStatusDto.builder()
                .tourId(tourId)
                .portCallId(portCallId)
                .portName(schedule.getPortName())
                .allAboardTime(schedule.getAllAboardTime())
                .departureTime(schedule.getDepartureTime())
                .secondsRemaining(secondsRemaining)
                .countdownExpired(countdownExpired)
                .totalEmbarkedPassengers(totalAboardTarget)
                .passengersOnBoard(onBoardCount)
                .missingAshoreCount(missingList.size())
                .aboardPercentage(Math.round(aboardPercentage * 10.0) / 10.0)
                .missingPassengers(missingList)
                .build();
    }

    @Transactional
    public UrgentWatchdogAlertResponse dispatchUrgentAlerts(UrgentWatchdogAlertRequest request) {
        log.warn("Dispatching urgent watchdog alert for tourId={}, portCallId={}, targetPassengers={}",
                request.getTourId(), request.getPortCallId(), request.getPassengerIds());

        List<Passenger> targetPassengers;
        if (request.getPassengerIds() != null && !request.getPassengerIds().isEmpty()) {
            targetPassengers = passengerRepository.findAllById(request.getPassengerIds()).stream()
                    .filter(p -> Objects.equals(p.getTourId(), request.getTourId()))
                    .collect(Collectors.toList());
        } else {
            AllAboardStatusDto status = getAllAboardStatus(request.getTourId(), request.getPortCallId());
            List<Long> missingIds = status.getMissingPassengers().stream()
                    .map(MissingPassengerDto::getPassengerId)
                    .collect(Collectors.toList());
            targetPassengers = passengerRepository.findAllById(missingIds);
        }

        Instant now = Instant.now();
        int smsSent = 0;
        int inAppSent = 0;
        List<Long> contactedPassengerIds = new ArrayList<>();

        String message = request.getCustomUrgentMessage() != null
                ? request.getCustomUrgentMessage()
                : "URGENT: All-Aboard countdown closing. Please return to the gangway immediately.";

        for (Passenger passenger : targetPassengers) {
            boolean deliveredAny = false;

            if (request.isSendSms() && passenger.getPhoneNumber() != null && !passenger.getPhoneNumber().isBlank()) {
                smsSent++;
                deliveredAny = true;
            }

            if (request.isSendInAppNotification()) {
                PassengerNotification notification = PassengerNotification.builder()
                        .passengerId(passenger.getId())
                        .title("URGENT: All-Aboard Watchdog Alert")
                        .message(message)
                        .category(NotificationCategory.CRITICAL)
                        .isRead(false)
                        .build();
                passengerNotificationRepository.save(notification);
                inAppSent++;
                deliveredAny = true;
            }

            if (deliveredAny || request.isBroadcastPaSystem()) {
                lastAlertRegistry.put(new AlertKey(request.getTourId(), request.getPortCallId(), passenger.getId()), now);
                contactedPassengerIds.add(passenger.getId());
            }
        }

        String batchId = "ALERT-WDG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return UrgentWatchdogAlertResponse.builder()
                .alertBatchId(batchId)
                .targetedPassengersCount(targetPassengers.size())
                .smsSentCount(smsSent)
                .inAppSentCount(inAppSent)
                .paBroadcastTriggered(request.isBroadcastPaSystem())
                .broadcastMessage(message)
                .dispatchedAt(now)
                .contactedPassengerIds(contactedPassengerIds)
                .build();
    }

    private Passenger findPassengerByRequest(GangwayScanRequest request) {
        if (request.getPassengerId() != null) {
            return passengerRepository.findById(request.getPassengerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + request.getPassengerId()));
        }
        if (request.getRfidTag() != null && !request.getRfidTag().isBlank()) {
            return passengerRepository.findByRfidTag(request.getRfidTag())
                    .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with RFID tag: " + request.getRfidTag()));
        }
        if (request.getQrCode() != null && !request.getQrCode().isBlank()) {
            return passengerRepository.findByQrCodeAndActiveTrue(request.getQrCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with QR code: " + request.getQrCode()));
        }
        throw new IllegalArgumentException("Either passengerId, rfidTag, or qrCode must be provided for gangway scan");
    }
}
