package com.cruise.activity.service;

import com.cruise.activity.dto.AllAboardStatusDto;
import com.cruise.activity.dto.GangwayScanRequest;
import com.cruise.activity.dto.UrgentWatchdogAlertRequest;
import com.cruise.activity.dto.UrgentWatchdogAlertResponse;
import com.cruise.activity.entity.GangwayMovementLog;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.enums.GangwayMovementType;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.GangwayMovementLogRepository;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllAboardWatchdogServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private GangwayMovementLogRepository gangwayMovementLogRepository;

    @Mock
    private PassengerNotificationRepository passengerNotificationRepository;

    @InjectMocks
    private AllAboardWatchdogService watchdogService;

    private Passenger passenger1;
    private Passenger passenger2;

    @BeforeEach
    void setUp() {
        passenger1 = Passenger.builder()
                .tourId(10L)
                .fullName("John Doe")
                .cabinNumber("C-401")
                .rfidTag("RFID-JOHNDOE")
                .qrCode("QR-JOHNDOE")
                .phoneNumber("+84901234567")
                .emergencyContact("+84909999999")
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();
        passenger1.setId(101L);

        passenger2 = Passenger.builder()
                .tourId(10L)
                .fullName("Jane Smith")
                .cabinNumber("C-402")
                .rfidTag("RFID-JANESMITH")
                .phoneNumber("+84907654321")
                .checkInStatus(CheckInStatus.BOARDED)
                .active(true)
                .build();
        passenger2.setId(102L);
    }

    @Test
    @DisplayName("recordGangwayScan: successfully records gangway movement log")
    void recordGangwayScan_Success() {
        when(passengerRepository.findByRfidTag("RFID-JOHNDOE")).thenReturn(Optional.of(passenger1));
        when(gangwayMovementLogRepository.save(any(GangwayMovementLog.class))).thenAnswer(invocation -> {
            GangwayMovementLog log = invocation.getArgument(0);
            log.setId(1L);
            return log;
        });

        GangwayScanRequest request = GangwayScanRequest.builder()
                .tourId(10L)
                .portCallId(200L)
                .rfidTag("RFID-JOHNDOE")
                .movementType(GangwayMovementType.DISEMBARKED_SHORE)
                .gangwayTerminalId("GANGWAY-PORT")
                .operatorName("Officer Mike")
                .build();

        GangwayMovementLog result = watchdogService.recordGangwayScan(request);

        assertThat(result).isNotNull();
        assertThat(result.getPassengerId()).isEqualTo(101L);
        assertThat(result.getPassengerName()).isEqualTo("John Doe");
        assertThat(result.getMovementType()).isEqualTo(GangwayMovementType.DISEMBARKED_SHORE);
        verify(gangwayMovementLogRepository, times(1)).save(any(GangwayMovementLog.class));
    }

    @Test
    @DisplayName("recordGangwayScan: throws exception when passenger belongs to different tour")
    void recordGangwayScan_DifferentTour_ThrowsException() {
        passenger1.setTourId(99L);
        when(passengerRepository.findByRfidTag("RFID-JOHNDOE")).thenReturn(Optional.of(passenger1));

        GangwayScanRequest request = GangwayScanRequest.builder()
                .tourId(10L)
                .portCallId(200L)
                .rfidTag("RFID-JOHNDOE")
                .movementType(GangwayMovementType.DISEMBARKED_SHORE)
                .build();

        assertThatThrownBy(() -> watchdogService.recordGangwayScan(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to tourId: 10");
    }

    @Test
    @DisplayName("getAllAboardStatus: throws exception if schedule is not configured")
    void getAllAboardStatus_MissingSchedule_ThrowsException() {
        assertThatThrownBy(() -> watchdogService.getAllAboardStatus(10L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No All-Aboard schedule configured for portCallId: 999");
    }

    @Test
    @DisplayName("getAllAboardStatus: filters missing ashore passengers correctly")
    void getAllAboardStatus_CalculatesMissingAndAboardRates() {
        when(passengerRepository.findByTourIdAndActiveTrue(10L)).thenReturn(List.of(passenger1, passenger2));

        GangwayMovementLog p1ShoreLog = GangwayMovementLog.builder()
                .tourId(10L)
                .portCallId(200L)
                .passengerId(101L)
                .movementType(GangwayMovementType.DISEMBARKED_SHORE)
                .scannedAt(Instant.now().minus(Duration.ofHours(3)))
                .build();

        when(gangwayMovementLogRepository.findByTourIdAndPortCallIdOrderByScannedAtDesc(10L, 200L))
                .thenReturn(List.of(p1ShoreLog));

        watchdogService.configurePortDepartureSchedule(200L, "Phuket Port",
                Instant.now().plus(Duration.ofMinutes(45)),
                Instant.now().plus(Duration.ofMinutes(90)));

        AllAboardStatusDto status = watchdogService.getAllAboardStatus(10L, 200L);

        assertThat(status).isNotNull();
        assertThat(status.getPortName()).isEqualTo("Phuket Port");
        assertThat(status.getTotalEmbarkedPassengers()).isEqualTo(2);
        assertThat(status.getPassengersOnBoard()).isEqualTo(1);
        assertThat(status.getMissingAshoreCount()).isEqualTo(1);
        assertThat(status.getAboardPercentage()).isEqualTo(50.0);
        assertThat(status.isCountdownExpired()).isFalse();
        assertThat(status.getMissingPassengers()).hasSize(1);
        assertThat(status.getMissingPassengers().get(0).getPassengerId()).isEqualTo(101L);
        assertThat(status.getMissingPassengers().get(0).getFullName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("dispatchUrgentAlerts: sends SMS & In-App notifications to missing passengers")
    void dispatchUrgentAlerts_Success() {
        when(passengerRepository.findAllById(List.of(101L))).thenReturn(List.of(passenger1));
        when(passengerNotificationRepository.save(any(PassengerNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UrgentWatchdogAlertRequest request = UrgentWatchdogAlertRequest.builder()
                .tourId(10L)
                .portCallId(200L)
                .passengerIds(List.of(101L))
                .sendSms(true)
                .sendInAppNotification(true)
                .broadcastPaSystem(true)
                .customUrgentMessage("Attention John Doe: return to ship gangway now!")
                .dispatchedBy("Port Captain")
                .build();

        UrgentWatchdogAlertResponse response = watchdogService.dispatchUrgentAlerts(request);

        assertThat(response).isNotNull();
        assertThat(response.getAlertBatchId()).startsWith("ALERT-WDG-");
        assertThat(response.getTargetedPassengersCount()).isEqualTo(1);
        assertThat(response.getSmsSentCount()).isEqualTo(1);
        assertThat(response.getInAppSentCount()).isEqualTo(1);
        assertThat(response.isPaBroadcastTriggered()).isTrue();
        assertThat(response.getBroadcastMessage()).contains("Attention John Doe");
        verify(passengerNotificationRepository, times(1)).save(any(PassengerNotification.class));
    }

    @Test
    @DisplayName("dispatchUrgentAlerts: alert status scoped to specific port call")
    void alertStatus_ScopedToPortCall() {
        when(passengerRepository.findAllById(List.of(101L))).thenReturn(List.of(passenger1));
        when(passengerNotificationRepository.save(any(PassengerNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        watchdogService.configurePortDepartureSchedule(200L, "Port A", Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        watchdogService.configurePortDepartureSchedule(300L, "Port B", Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        UrgentWatchdogAlertRequest request = UrgentWatchdogAlertRequest.builder()
                .tourId(10L)
                .portCallId(200L)
                .passengerIds(List.of(101L))
                .sendInAppNotification(true)
                .build();

        watchdogService.dispatchUrgentAlerts(request);

        when(passengerRepository.findByTourIdAndActiveTrue(10L)).thenReturn(List.of(passenger1));
        GangwayMovementLog log200 = GangwayMovementLog.builder()
                .tourId(10L).portCallId(200L).passengerId(101L)
                .movementType(GangwayMovementType.DISEMBARKED_SHORE).scannedAt(Instant.now()).build();
        when(gangwayMovementLogRepository.findByTourIdAndPortCallIdOrderByScannedAtDesc(10L, 200L))
                .thenReturn(List.of(log200));

        AllAboardStatusDto status200 = watchdogService.getAllAboardStatus(10L, 200L);
        assertThat(status200.getMissingPassengers().get(0).isAlertSent()).isTrue();

        GangwayMovementLog log300 = GangwayMovementLog.builder()
                .tourId(10L).portCallId(300L).passengerId(101L)
                .movementType(GangwayMovementType.DISEMBARKED_SHORE).scannedAt(Instant.now()).build();
        when(gangwayMovementLogRepository.findByTourIdAndPortCallIdOrderByScannedAtDesc(10L, 300L))
                .thenReturn(List.of(log300));

        AllAboardStatusDto status300 = watchdogService.getAllAboardStatus(10L, 300L);
        assertThat(status300.getMissingPassengers().get(0).isAlertSent()).isFalse();
    }
}
