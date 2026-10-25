package com.cruise.activity.service;

import com.cruise.activity.dto.SolasMusterCheckinRequest;
import com.cruise.activity.dto.SolasMusterIncidentDto;
import com.cruise.activity.entity.SolasMusterCheckin;
import com.cruise.activity.entity.SolasMusterIncident;
import com.cruise.activity.enums.MusterStatus;
import com.cruise.activity.enums.SolasEmergencyType;
import com.cruise.activity.exception.MusterIncidentNotActiveException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerRepository;
import com.cruise.activity.repository.SolasMusterCheckinRepository;
import com.cruise.activity.repository.SolasMusterIncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolasMusterEmergencyService Unit Tests (KAN-445)")
class SolasMusterEmergencyServiceTest {

    @Mock
    private SolasMusterIncidentRepository incidentRepository;

    @Mock
    private SolasMusterCheckinRepository checkinRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private SolasMusterEmergencyService emergencyService;

    @Test
    @DisplayName("triggerEmergencyMuster: uses manifest passenger count and does not invent 1200")
    void testTriggerEmergencyMuster_UsesRealCount() {
        when(passengerRepository.count()).thenReturn(350L);
        when(incidentRepository.save(any(SolasMusterIncident.class))).thenAnswer(inv -> {
            SolasMusterIncident inc = inv.getArgument(0);
            inc.setId(10L);
            return inc;
        });

        SolasMusterIncidentDto req = SolasMusterIncidentDto.builder()
                .emergencyType(SolasEmergencyType.FIRE_ALARM)
                .triggeredBy("CAPTAIN")
                .announcementText("Assemble at muster stations immediately")
                .build();

        SolasMusterIncidentDto created = emergencyService.triggerEmergencyMuster(req);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getTotalSoulsOnBoard()).isEqualTo(350);
        assertThat(created.getStatus()).isEqualTo(MusterStatus.ACTIVE_MUSTER);
        assertThat(created.getAcousticSignalCode()).isEqualTo("7_SHORT_1_LONG");
    }

    @Test
    @DisplayName("triggerEmergencyMuster: honors totalSoulsOnBoard from DTO if provided")
    void testTriggerEmergencyMuster_HonorsDtoCount() {
        when(incidentRepository.save(any(SolasMusterIncident.class))).thenAnswer(inv -> {
            SolasMusterIncident inc = inv.getArgument(0);
            inc.setId(11L);
            return inc;
        });

        SolasMusterIncidentDto req = SolasMusterIncidentDto.builder()
                .emergencyType(SolasEmergencyType.ABANDON_SHIP)
                .triggeredBy("SAFETY_OFFICER")
                .announcementText("Abandon ship drill")
                .totalSoulsOnBoard(800)
                .build();

        SolasMusterIncidentDto created = emergencyService.triggerEmergencyMuster(req);

        assertThat(created.getTotalSoulsOnBoard()).isEqualTo(800);
        verify(passengerRepository, never()).count();
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: successfully records checkin and updates tally")
    void testCheckinPassenger_Success() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .incidentCode("SOLAS-TEST")
                .status(MusterStatus.ACTIVE_MUSTER)
                .totalSoulsOnBoard(100)
                .musteredSoulsCount(0)
                .build();
        incident.setId(5L);

        when(incidentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(incident));
        when(passengerRepository.existsById(200L)).thenReturn(true);
        when(checkinRepository.findByIncidentIdAndPassengerId(5L, 200L)).thenReturn(Optional.empty());
        when(checkinRepository.countByIncidentId(5L)).thenReturn(1L);
        when(incidentRepository.save(any(SolasMusterIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        SolasMusterCheckin sampleCheckin = SolasMusterCheckin.builder()
                .incidentId(5L)
                .passengerId(200L)
                .musterStationCode("STATION-A")
                .build();
        when(checkinRepository.findByIncidentId(5L)).thenReturn(List.of(sampleCheckin));

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(5L)
                .passengerId(200L)
                .musterStationCode("station-a")
                .lifejacketVerified(true)
                .checkedInBy("SCANNER-1")
                .build();

        SolasMusterIncidentDto result = emergencyService.checkinPassengerToMusterStation(req);

        assertThat(result).isNotNull();
        assertThat(result.getMusteredSoulsCount()).isEqualTo(1);
        assertThat(result.getMusterCompletionRate()).isEqualTo(1.0);
        assertThat(result.getHeadCountByStation()).containsEntry("STATION-A", 1L);

        verify(checkinRepository).saveAndFlush(any(SolasMusterCheckin.class));
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: throws ResourceNotFoundException if incident not found")
    void testCheckinPassenger_IncidentNotFoundThrows() {
        when(incidentRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(999L)
                .passengerId(1L)
                .musterStationCode("STATION-A")
                .build();

        assertThatThrownBy(() -> emergencyService.checkinPassengerToMusterStation(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: throws MusterIncidentNotActiveException if incident is not active")
    void testCheckinPassenger_InactiveIncidentThrows() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ALL_CLEAR)
                .build();
        incident.setId(5L);

        when(incidentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(incident));

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(5L)
                .passengerId(1L)
                .musterStationCode("STATION-A")
                .build();

        assertThatThrownBy(() -> emergencyService.checkinPassengerToMusterStation(req))
                .isInstanceOf(MusterIncidentNotActiveException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: throws ResourceNotFoundException if passenger does not exist")
    void testCheckinPassenger_PassengerNotFoundThrows() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ACTIVE_MUSTER)
                .build();
        incident.setId(5L);

        when(incidentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(incident));
        when(passengerRepository.existsById(888L)).thenReturn(false);

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(5L)
                .passengerId(888L)
                .musterStationCode("STATION-A")
                .build();

        assertThatThrownBy(() -> emergencyService.checkinPassengerToMusterStation(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("888");
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: handles duplicate checkin gracefully")
    void testCheckinPassenger_DuplicateHandled() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ACTIVE_MUSTER)
                .totalSoulsOnBoard(50)
                .musteredSoulsCount(1)
                .build();
        incident.setId(5L);

        SolasMusterCheckin existing = SolasMusterCheckin.builder()
                .incidentId(5L)
                .passengerId(100L)
                .musterStationCode("STATION-A")
                .build();

        when(incidentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(incident));
        when(passengerRepository.existsById(100L)).thenReturn(true);
        when(checkinRepository.findByIncidentIdAndPassengerId(5L, 100L)).thenReturn(Optional.of(existing));
        when(checkinRepository.findByIncidentId(5L)).thenReturn(List.of(existing));

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(5L)
                .passengerId(100L)
                .musterStationCode("STATION-A")
                .build();

        SolasMusterIncidentDto dto = emergencyService.checkinPassengerToMusterStation(req);

        assertThat(dto).isNotNull();
        verify(checkinRepository, never()).saveAndFlush(any());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkinPassengerToMusterStation: handles concurrent DataIntegrityViolationException gracefully")
    void testCheckinPassenger_ConcurrentRaceHandled() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ACTIVE_MUSTER)
                .totalSoulsOnBoard(50)
                .musteredSoulsCount(0)
                .build();
        incident.setId(5L);

        when(incidentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(incident));
        when(passengerRepository.existsById(101L)).thenReturn(true);
        when(checkinRepository.findByIncidentIdAndPassengerId(5L, 101L)).thenReturn(Optional.empty());
        when(checkinRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_incident_passenger"));
        when(checkinRepository.countByIncidentId(5L)).thenReturn(1L);
        when(incidentRepository.save(any(SolasMusterIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkinRepository.findByIncidentId(5L)).thenReturn(Collections.emptyList());

        SolasMusterCheckinRequest req = SolasMusterCheckinRequest.builder()
                .incidentId(5L)
                .passengerId(101L)
                .musterStationCode("STATION-C")
                .build();

        SolasMusterIncidentDto dto = emergencyService.checkinPassengerToMusterStation(req);
        assertThat(dto).isNotNull();
        assertThat(dto.getMusteredSoulsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("setAllClear: transitions active incident to ALL_CLEAR status")
    void testSetAllClear_Success() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ACTIVE_MUSTER)
                .totalSoulsOnBoard(100)
                .musteredSoulsCount(100)
                .build();
        incident.setId(10L);

        when(incidentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SolasMusterIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checkinRepository.findByIncidentId(10L)).thenReturn(Collections.emptyList());

        SolasMusterIncidentDto res = emergencyService.setAllClear(10L);

        assertThat(res.getStatus()).isEqualTo(MusterStatus.ALL_CLEAR);
        assertThat(res.getAllClearAt()).isNotNull();
    }

    @Test
    @DisplayName("setAllClear: throws MusterIncidentNotActiveException if incident is already ALL_CLEAR")
    void testSetAllClear_AlreadyAllClearThrows() {
        SolasMusterIncident incident = SolasMusterIncident.builder()
                .status(MusterStatus.ALL_CLEAR)
                .build();
        incident.setId(10L);

        when(incidentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> emergencyService.setAllClear(10L))
                .isInstanceOf(MusterIncidentNotActiveException.class)
                .hasMessageContaining("Cannot declare ALL_CLEAR");
    }

    @Test
    @DisplayName("getActiveMusterIncident: returns active incident or empty")
    void testGetActiveMusterIncident() {
        when(incidentRepository.findFirstByStatusInOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        Optional<SolasMusterIncidentDto> empty = emergencyService.getActiveMusterIncident();
        assertThat(empty).isEmpty();

        SolasMusterIncident active = SolasMusterIncident.builder()
                .status(MusterStatus.ACTIVE_MUSTER)
                .totalSoulsOnBoard(500)
                .build();
        active.setId(1L);

        when(incidentRepository.findFirstByStatusInOrderByCreatedAtDesc(any())).thenReturn(Optional.of(active));
        when(checkinRepository.findByIncidentId(1L)).thenReturn(Collections.emptyList());

        Optional<SolasMusterIncidentDto> present = emergencyService.getActiveMusterIncident();
        assertThat(present).isPresent();
        assertThat(present.get().getStatus()).isEqualTo(MusterStatus.ACTIVE_MUSTER);
    }
}
