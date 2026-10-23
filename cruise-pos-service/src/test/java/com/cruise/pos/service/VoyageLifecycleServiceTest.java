package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.VoyageLifecycleState;
import com.cruise.pos.enums.VoyageOperationalPhase;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.VoyageLifecycleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoyageLifecycleServiceTest {

    @Mock
    private VoyageLifecycleStateRepository stateRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private VoyageLifecycleService voyageLifecycleService;

    private VoyageLifecycleState sampleState;

    @BeforeEach
    void setUp() {
        sampleState = VoyageLifecycleState.builder()
                .voyageNumber("VOY-2026-MED-05")
                .shipId("SHIP-CELEBRATION")
                .itineraryName("Aegean Odyssey 7-Day")
                .currentPhase(VoyageOperationalPhase.PLANNING)
                .currentTimezone("UTC")
                .vesselClockOffsetMinutes(0)
                .clockSyncEnabled(true)
                .phaseTransitionedAt(Instant.now())
                .transitionedBy("CHIEF_PURSER")
                .taxExemptionActive(false)
                .casinoOpen(false)
                .build();
        sampleState.setId(1L);
    }

    @Test
    @DisplayName("createVoyageLifecycle: sets initial state and logs audit")
    void testCreateVoyageLifecycleSuccess() {
        CreateVoyageStateRequest req = new CreateVoyageStateRequest(
                "VOY-NEW-01",
                "SHIP-1",
                "Western Med",
                VoyageOperationalPhase.PLANNING,
                "UTC",
                "Barcelona Port"
        );

        when(stateRepository.existsByVoyageNumber("VOY-NEW-01")).thenReturn(false);
        when(stateRepository.saveAndFlush(any(VoyageLifecycleState.class))).thenAnswer(inv -> {
            VoyageLifecycleState s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        VoyageLifecycleStateDto dto = voyageLifecycleService.createVoyageLifecycle(req, "CHIEF_PURSER");

        assertThat(dto).isNotNull();
        assertThat(dto.voyageNumber()).isEqualTo("VOY-NEW-01");
        assertThat(dto.currentPhase()).isEqualTo(VoyageOperationalPhase.PLANNING);
        verify(auditLogRepository).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("createVoyageLifecycle: rejects non-PLANNING initialPhase")
    void testCreateVoyageLifecycleNonPlanningThrows() {
        CreateVoyageStateRequest req = new CreateVoyageStateRequest(
                "VOY-NEW-02", "SHIP-1", "Western Med",
                VoyageOperationalPhase.AT_SEA, "UTC", "Barcelona Port"
        );

        when(stateRepository.existsByVoyageNumber("VOY-NEW-02")).thenReturn(false);

        assertThatThrownBy(() -> voyageLifecycleService.createVoyageLifecycle(req, "ADMIN"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("New voyages must start in PLANNING");
    }

    @Test
    @DisplayName("transitionPhase: enforces valid sequence PLANNING -> EMBARKING -> AT_SEA")
    void testTransitionPhaseValidSequence() {
        when(stateRepository.findByVoyageNumberWithLock("VOY-2026-MED-05")).thenReturn(Optional.of(sampleState));
        when(stateRepository.save(any(VoyageLifecycleState.class))).thenAnswer(inv -> inv.getArgument(0));

        // PLANNING -> EMBARKING
        TransitionVoyagePhaseRequest req1 = new TransitionVoyagePhaseRequest(
                VoyageOperationalPhase.EMBARKING, "Boarding gates open", "Piraeus Port"
        );
        VoyageLifecycleStateDto dto1 = voyageLifecycleService.transitionPhase("VOY-2026-MED-05", req1, "CHIEF_PURSER");
        assertThat(dto1.currentPhase()).isEqualTo(VoyageOperationalPhase.EMBARKING);

        // EMBARKING -> AT_SEA (Activates Tax Exemption & Casino)
        TransitionVoyagePhaseRequest req2 = new TransitionVoyagePhaseRequest(
                VoyageOperationalPhase.AT_SEA, "Departed port limits into international waters", null
        );
        VoyageLifecycleStateDto dto2 = voyageLifecycleService.transitionPhase("VOY-2026-MED-05", req2, "CAPTAIN");
        assertThat(dto2.currentPhase()).isEqualTo(VoyageOperationalPhase.AT_SEA);
        assertThat(dto2.taxExemptionActive()).isTrue();
        assertThat(dto2.casinoOpen()).isTrue();
    }

    @Test
    @DisplayName("transitionPhase: rejects invalid illegal transitions (e.g. PLANNING -> AT_SEA)")
    void testTransitionPhaseIllegalJumpThrows() {
        when(stateRepository.findByVoyageNumberWithLock("VOY-2026-MED-05")).thenReturn(Optional.of(sampleState));

        TransitionVoyagePhaseRequest illegalReq = new TransitionVoyagePhaseRequest(
                VoyageOperationalPhase.AT_SEA, "Skipping embarking", null
        );

        assertThatThrownBy(() -> voyageLifecycleService.transitionPhase("VOY-2026-MED-05", illegalReq, "OFFICER"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Illegal voyage phase transition");

        verify(stateRepository, never()).save(any());
    }

    @Test
    @DisplayName("transitionPhase: disables taxExemption and casino when moving from AT_SEA to DEBARKING")
    void testTransitionPhaseDebarkingPolicyFlags() {
        sampleState.setCurrentPhase(VoyageOperationalPhase.AT_SEA);
        sampleState.setTaxExemptionActive(true);
        sampleState.setCasinoOpen(true);

        when(stateRepository.findByVoyageNumberWithLock("VOY-2026-MED-05")).thenReturn(Optional.of(sampleState));
        when(stateRepository.save(any(VoyageLifecycleState.class))).thenAnswer(inv -> inv.getArgument(0));

        TransitionVoyagePhaseRequest req = new TransitionVoyagePhaseRequest(
                VoyageOperationalPhase.DEBARKING, "Arrived at final port for debarkation", "Piraeus"
        );

        VoyageLifecycleStateDto dto = voyageLifecycleService.transitionPhase("VOY-2026-MED-05", req, "PURSER");

        assertThat(dto.currentPhase()).isEqualTo(VoyageOperationalPhase.DEBARKING);
        assertThat(dto.taxExemptionActive()).isFalse();
        assertThat(dto.casinoOpen()).isFalse();
    }

    @Test
    @DisplayName("syncPortTimezone: adjusts timezone and vessel clock offset minutes")
    void testSyncPortTimezoneSuccess() {
        when(stateRepository.findByVoyageNumber("VOY-2026-MED-05")).thenReturn(Optional.of(sampleState));
        when(stateRepository.save(any(VoyageLifecycleState.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncPortTimezoneRequest req = new SyncPortTimezoneRequest(
                "Mykonos Port", "Europe/Athens", 120, "Port approach clock shift"
        );

        VoyageLifecycleStateDto dto = voyageLifecycleService.syncPortTimezone("VOY-2026-MED-05", req, "NAVIGATOR");

        assertThat(dto.currentTimezone()).isEqualTo("Europe/Athens");
        assertThat(dto.currentPortName()).isEqualTo("Mykonos Port");
        assertThat(dto.vesselClockOffsetMinutes()).isEqualTo(120);
        verify(auditLogRepository).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("getTimezoneSchedule: returns calculated ship time and upcoming port shifts")
    void testGetTimezoneSchedule() {
        sampleState.setCurrentTimezone("Europe/Athens");
        sampleState.setVesselClockOffsetMinutes(120);

        when(stateRepository.findByVoyageNumber("VOY-2026-MED-05")).thenReturn(Optional.of(sampleState));

        TimezoneShiftScheduleResponse res = voyageLifecycleService.getTimezoneSchedule("VOY-2026-MED-05");

        assertThat(res).isNotNull();
        assertThat(res.currentTimezone()).isEqualTo("Europe/Athens");
        assertThat(res.currentOffsetMinutes()).isEqualTo(120);
        assertThat(res.currentShipClockTime()).isNotNull();
        assertThat(res.plannedPortShifts()).hasSize(1);
    }

    @Test
    @DisplayName("getActiveVoyages: excludes CLOSED voyages from returned list")
    void testGetActiveVoyagesExcludesClosed() {
        when(stateRepository.findByCurrentPhaseNot(VoyageOperationalPhase.CLOSED)).thenReturn(java.util.List.of(sampleState));

        java.util.List<VoyageLifecycleStateDto> active = voyageLifecycleService.getActiveVoyages();

        assertThat(active).hasSize(1);
        assertThat(active.get(0).currentPhase()).isNotEqualTo(VoyageOperationalPhase.CLOSED);
    }
}
