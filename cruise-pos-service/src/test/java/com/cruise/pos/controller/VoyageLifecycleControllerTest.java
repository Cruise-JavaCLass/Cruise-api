package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.VoyageOperationalPhase;
import com.cruise.pos.service.VoyageLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoyageLifecycleController Unit Tests")
class VoyageLifecycleControllerTest {

    @Mock
    private VoyageLifecycleService lifecycleService;

    @Mock
    private Principal principal;

    @InjectMocks
    private VoyageLifecycleController controller;

    private final VoyageLifecycleStateDto sampleDto = new VoyageLifecycleStateDto(
            1L,
            "VOY-2026-01",
            "SHIP-CELEBRATION",
            "Mediterranean Odyssey",
            VoyageOperationalPhase.PLANNING,
            "Barcelona",
            "Europe/Madrid",
            60,
            true,
            Instant.now(),
            "CAPTAIN",
            "Created",
            false,
            false,
            Instant.now()
    );

    @Test
    @DisplayName("createVoyageState: creates voyage and returns 201 Created")
    void testCreateVoyageState() {
        CreateVoyageStateRequest req = new CreateVoyageStateRequest("VOY-2026-01", "SHIP-CELEBRATION", "Mediterranean Odyssey", VoyageOperationalPhase.PLANNING, "Barcelona", "Europe/Madrid");
        when(principal.getName()).thenReturn("CAPTAIN");
        when(lifecycleService.createVoyageLifecycle(eq(req), eq("CAPTAIN"))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> response = controller.createVoyageState(req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().voyageNumber()).isEqualTo("VOY-2026-01");
        verify(lifecycleService).createVoyageLifecycle(req, "CAPTAIN");
    }

    @Test
    @DisplayName("createVoyageState: throws AccessDeniedException when principal is null")
    void testCreateVoyageStateUnauthenticated() {
        CreateVoyageStateRequest req = new CreateVoyageStateRequest("VOY-2026-01", "SHIP-CELEBRATION", "Mediterranean Odyssey", VoyageOperationalPhase.PLANNING, "Barcelona", "Europe/Madrid");
        assertThatThrownBy(() -> controller.createVoyageState(req, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getActiveVoyages: returns list of active voyages")
    void testGetActiveVoyages() {
        when(lifecycleService.getActiveVoyages()).thenReturn(List.of(sampleDto));

        ResponseEntity<ApiResponse<List<VoyageLifecycleStateDto>>> response = controller.getActiveVoyages();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getVoyageState: returns state by voyageNumber")
    void testGetVoyageState() {
        when(lifecycleService.getVoyageState("VOY-2026-01")).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> response = controller.getVoyageState("VOY-2026-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().voyageNumber()).isEqualTo("VOY-2026-01");
    }

    @Test
    @DisplayName("transitionPhase: transitions voyage phase successfully")
    void testTransitionPhase() {
        TransitionVoyagePhaseRequest req = new TransitionVoyagePhaseRequest(VoyageOperationalPhase.EMBARKING, "Boarding started", null);
        when(principal.getName()).thenReturn("CHIEF_PURSER");
        when(lifecycleService.transitionPhase(eq("VOY-2026-01"), eq(req), eq("CHIEF_PURSER"))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> response = controller.transitionPhase("VOY-2026-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(lifecycleService).transitionPhase("VOY-2026-01", req, "CHIEF_PURSER");
    }

    @Test
    @DisplayName("transitionPhase: throws AccessDeniedException when principal is null")
    void testTransitionPhaseUnauthenticated() {
        TransitionVoyagePhaseRequest req = new TransitionVoyagePhaseRequest(VoyageOperationalPhase.EMBARKING, "Boarding started", null);
        assertThatThrownBy(() -> controller.transitionPhase("VOY-2026-01", req, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("syncPortTimezone: synchronizes port timezone successfully")
    void testSyncPortTimezone() {
        SyncPortTimezoneRequest req = new SyncPortTimezoneRequest("Santorini", "Europe/Athens", 120, "Port approach");
        when(principal.getName()).thenReturn("NAVIGATOR");
        when(lifecycleService.syncPortTimezone(eq("VOY-2026-01"), eq(req), eq("NAVIGATOR"))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<VoyageLifecycleStateDto>> response = controller.syncPortTimezone("VOY-2026-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(lifecycleService).syncPortTimezone("VOY-2026-01", req, "NAVIGATOR");
    }

    @Test
    @DisplayName("syncPortTimezone: throws AccessDeniedException when principal is null")
    void testSyncPortTimezoneUnauthenticated() {
        SyncPortTimezoneRequest req = new SyncPortTimezoneRequest("Santorini", "Europe/Athens", 120, "Port approach");
        assertThatThrownBy(() -> controller.syncPortTimezone("VOY-2026-01", req, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getTimezoneSchedule: returns timezone shift schedule")
    void testGetTimezoneSchedule() {
        TimezoneShiftScheduleResponse schedule = new TimezoneShiftScheduleResponse(
                "VOY-2026-01", "Europe/Madrid", 60, OffsetDateTime.now(), List.of()
        );
        when(lifecycleService.getTimezoneSchedule("VOY-2026-01")).thenReturn(schedule);

        ResponseEntity<ApiResponse<TimezoneShiftScheduleResponse>> response = controller.getTimezoneSchedule("VOY-2026-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().voyageNumber()).isEqualTo("VOY-2026-01");
    }
}
