package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.GangwayMovementLog;
import com.cruise.activity.enums.GangwayMovementType;
import com.cruise.activity.service.AllAboardWatchdogService;
import com.cruise.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllAboardWatchdogController Unit Tests")
class AllAboardWatchdogControllerTest {

    @Mock
    private AllAboardWatchdogService watchdogService;

    @InjectMocks
    private AllAboardWatchdogController controller;

    @Test
    @DisplayName("recordGangwayScan: records gangway movement and returns 201 Created")
    void testRecordGangwayScan() {
        GangwayScanRequest request = GangwayScanRequest.builder()
                .passengerId(10L)
                .movementType(GangwayMovementType.DISEMBARKED_SHORE)
                .portCallId(100L)
                .tourId(50L)
                .rfidTag("RFID-TEST")
                .gangwayTerminalId("PORT_SIDE_A")
                .operatorName("OFFICER_01")
                .build();

        GangwayMovementLog log = GangwayMovementLog.builder()
                .passengerId(10L)
                .movementType(GangwayMovementType.DISEMBARKED_SHORE)
                .portCallId(100L)
                .tourId(50L)
                .scannedAt(Instant.now())
                .build();
        log.setId(1L);

        when(watchdogService.recordGangwayScan(request)).thenReturn(log);

        ResponseEntity<ApiResponse<GangwayMovementLog>> response = controller.recordGangwayScan(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
        verify(watchdogService).recordGangwayScan(request);
    }

    @Test
    @DisplayName("getAllAboardStatus: returns All-Aboard status DTO")
    void testGetAllAboardStatus() {
        AllAboardStatusDto status = AllAboardStatusDto.builder()
                .portName("Santorini Port")
                .totalEmbarkedPassengers(100)
                .passengersOnBoard(90)
                .missingAshoreCount(10)
                .aboardPercentage(90.0)
                .countdownExpired(false)
                .missingPassengers(List.of())
                .build();

        when(watchdogService.getAllAboardStatus(50L, 100L)).thenReturn(status);

        ResponseEntity<ApiResponse<AllAboardStatusDto>> response = controller.getAllAboardStatus(50L, 100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getPortName()).isEqualTo("Santorini Port");
        verify(watchdogService).getAllAboardStatus(50L, 100L);
    }

    @Test
    @DisplayName("configurePortSchedule: configures port departure schedule")
    void testConfigurePortSchedule() {
        Instant allAboard = Instant.now().plusSeconds(1800);
        Instant departure = Instant.now().plusSeconds(3600);

        ResponseEntity<ApiResponse<String>> response = controller.configurePortSchedule(100L, "Santorini Port", allAboard, departure);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEqualTo("SCHEDULE_CONFIGURED");
        verify(watchdogService).configurePortDepartureSchedule(100L, "Santorini Port", allAboard, departure);
    }

    @Test
    @DisplayName("dispatchAlerts: dispatches urgent alerts and returns response")
    void testDispatchAlerts() {
        UrgentWatchdogAlertRequest request = UrgentWatchdogAlertRequest.builder()
                .portCallId(100L)
                .tourId(50L)
                .passengerIds(List.of(10L))
                .sendSms(true)
                .sendInAppNotification(true)
                .broadcastPaSystem(true)
                .customUrgentMessage("Please return immediately")
                .dispatchedBy("Officer")
                .build();

        UrgentWatchdogAlertResponse alertResponse = UrgentWatchdogAlertResponse.builder()
                .alertBatchId("ALERT-WDG-100")
                .targetedPassengersCount(1)
                .smsSentCount(1)
                .inAppSentCount(1)
                .paBroadcastTriggered(true)
                .broadcastMessage("Please return immediately")
                .build();

        when(watchdogService.dispatchUrgentAlerts(request)).thenReturn(alertResponse);

        ResponseEntity<ApiResponse<UrgentWatchdogAlertResponse>> response = controller.dispatchAlerts(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getTargetedPassengersCount()).isEqualTo(1);
        verify(watchdogService).dispatchUrgentAlerts(request);
    }
}
