package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.VoyageLifecycleState;
import com.cruise.pos.enums.VoyageOperationalPhase;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.VoyageLifecycleStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoyageLifecycleService {

    private final VoyageLifecycleStateRepository stateRepository;
    private final PosAuditLogRepository auditLogRepository;

    @Transactional
    public VoyageLifecycleStateDto createVoyageLifecycle(CreateVoyageStateRequest request, String actor) {
        log.info("Creating voyage lifecycle state for voyageNumber={}, shipId={}, actor={}",
                request.voyageNumber(), request.shipId(), actor);

        if (stateRepository.existsByVoyageNumber(request.voyageNumber())) {
            throw new DuplicateResourceException("Voyage lifecycle for " + request.voyageNumber() + " already exists");
        }

        if (request.initialPhase() != null && request.initialPhase() != VoyageOperationalPhase.PLANNING) {
            throw new BusinessRuleException("New voyages must start in PLANNING");
        }
        VoyageOperationalPhase initialPhase = VoyageOperationalPhase.PLANNING;

        String timezone = "UTC";
        int initialOffset = 0;
        if (request.initialTimezone() != null && !request.initialTimezone().isBlank()) {
            try {
                ZoneId zoneId = ZoneId.of(request.initialTimezone());
                timezone = zoneId.getId();
                initialOffset = zoneId.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
            } catch (DateTimeException e) {
                throw new BusinessRuleException("Invalid initialTimezone: " + e.getMessage());
            }
        }

        VoyageLifecycleState state = VoyageLifecycleState.builder()
                .voyageNumber(request.voyageNumber())
                .shipId(request.shipId())
                .itineraryName(request.itineraryName())
                .currentPhase(initialPhase)
                .currentPortName(request.currentPortName())
                .currentTimezone(timezone)
                .vesselClockOffsetMinutes(initialOffset)
                .clockSyncEnabled(true)
                .phaseTransitionedAt(Instant.now())
                .transitionedBy(actor)
                .transitionReason("Initial voyage lifecycle creation")
                .taxExemptionActive(false)
                .casinoOpen(false)
                .build();

        VoyageLifecycleState saved;
        try {
            saved = stateRepository.saveAndFlush(state);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Voyage lifecycle for " + request.voyageNumber() + " already exists");
        }

        recordAudit(
                "VOYAGE_LIFECYCLE_CREATED",
                actor,
                "Initialized voyage lifecycle in phase " + initialPhase,
                "voyageNumber=" + request.voyageNumber()
        );

        return toDto(saved);
    }

    @Transactional
    public VoyageLifecycleStateDto transitionPhase(String voyageNumber, TransitionVoyagePhaseRequest request, String actor) {
        log.info("Transitioning voyage {} from current phase to {}, actor={}",
                voyageNumber, request.targetPhase(), actor);
        VoyageLifecycleState state = stateRepository.findByVoyageNumberWithLock(voyageNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage lifecycle state not found for: " + voyageNumber));

        VoyageOperationalPhase current = state.getCurrentPhase();
        VoyageOperationalPhase target = request.targetPhase();

        validatePhaseTransition(current, target);

        state.setCurrentPhase(target);
        state.setPhaseTransitionedAt(Instant.now());
        state.setTransitionedBy(actor);
        state.setTransitionReason(request.transitionReason() != null ? request.transitionReason() : "Operational state transition");

        if (request.currentPortName() != null && !request.currentPortName().isBlank()) {
            state.setCurrentPortName(request.currentPortName());
        }

        // Apply Maritime Operating Policies based on Target Phase: enable only for AT_SEA
        boolean atSea = target == VoyageOperationalPhase.AT_SEA;
        state.setTaxExemptionActive(atSea);
        state.setCasinoOpen(atSea);

        VoyageLifecycleState saved = stateRepository.save(state);

        recordAudit(
                "VOYAGE_PHASE_TRANSITION",
                actor,
                "Voyage phase transitioned: " + current + " -> " + target + ". Reason: " + request.transitionReason(),
                "voyageNumber=" + voyageNumber + ", taxExemption=" + saved.getTaxExemptionActive()
        );

        return toDto(saved);
    }

    @Transactional
    public VoyageLifecycleStateDto syncPortTimezone(String voyageNumber, SyncPortTimezoneRequest request, String actor) {
        log.info("Synchronizing port timezone for voyage {}: port={}, tz={}, offset={}m, actor={}",
                voyageNumber, request.portName(), request.timezone(), request.clockOffsetMinutes(), actor);
        VoyageLifecycleState state = findOrThrow(voyageNumber);

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(request.timezone());
        } catch (DateTimeException e) {
            throw new BusinessRuleException("Invalid timezone: " + e.getMessage());
        }

        int offsetMinutes = request.clockOffsetMinutes() != null
                ? request.clockOffsetMinutes()
                : zoneId.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;

        state.setCurrentPortName(request.portName());
        state.setCurrentTimezone(zoneId.getId());
        state.setVesselClockOffsetMinutes(offsetMinutes);

        VoyageLifecycleState saved = stateRepository.save(state);

        recordAudit(
                "PORT_TIMEZONE_SYNCHRONIZED",
                actor,
                "Vessel clock coordinated to " + zoneId.getId() + " (Offset: " + state.getVesselClockOffsetMinutes() + " min)",
                "port=" + request.portName() + ", reason=" + request.adjustmentReason()
        );

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TimezoneShiftScheduleResponse getTimezoneSchedule(String voyageNumber) {
        VoyageLifecycleState state = findOrThrow(voyageNumber);

        int offsetMinutes = state.getVesselClockOffsetMinutes() != null ? state.getVesselClockOffsetMinutes() : 0;
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
        OffsetDateTime currentShipClockTime = Instant.now().atOffset(zoneOffset);

        TimezoneShiftScheduleItem activeItem = new TimezoneShiftScheduleItem(
                state.getCurrentPortName() != null ? state.getCurrentPortName() : "At Sea",
                state.getCurrentTimezone(),
                offsetMinutes,
                Instant.now(),
                "CURRENT_ACTIVE"
        );

        return new TimezoneShiftScheduleResponse(
                state.getVoyageNumber(),
                state.getCurrentTimezone(),
                offsetMinutes,
                currentShipClockTime,
                List.of(activeItem)
        );
    }

    @Transactional(readOnly = true)
    public VoyageLifecycleStateDto getVoyageState(String voyageNumber) {
        return toDto(findOrThrow(voyageNumber));
    }

    @Transactional(readOnly = true)
    public List<VoyageLifecycleStateDto> getActiveVoyages() {
        return stateRepository.findByCurrentPhaseNot(VoyageOperationalPhase.CLOSED).stream()
                .map(this::toDto)
                .toList();
    }

    private void validatePhaseTransition(VoyageOperationalPhase current, VoyageOperationalPhase target) {
        if (current == target) {
            throw new BusinessRuleException("Voyage is already in phase: " + target);
        }

        // Strict state machine rules:
        // PLANNING -> EMBARKING
        // EMBARKING -> AT_SEA
        // AT_SEA -> PORT_VISIT or DEBARKING
        // PORT_VISIT -> AT_SEA or DEBARKING
        // DEBARKING -> CLOSED
        // CLOSED -> terminal state, no further transitions
        if (current == VoyageOperationalPhase.CLOSED) {
            throw new BusinessRuleException("Voyage is CLOSED. No further phase transitions are allowed.");
        }

        Set<VoyageOperationalPhase> validNextPhases = switch (current) {
            case PLANNING -> Set.of(VoyageOperationalPhase.EMBARKING);
            case EMBARKING -> Set.of(VoyageOperationalPhase.AT_SEA);
            case AT_SEA -> Set.of(VoyageOperationalPhase.PORT_VISIT, VoyageOperationalPhase.DEBARKING);
            case PORT_VISIT -> Set.of(VoyageOperationalPhase.AT_SEA, VoyageOperationalPhase.DEBARKING);
            case DEBARKING -> Set.of(VoyageOperationalPhase.CLOSED);
            case CLOSED -> Set.of();
        };

        if (!validNextPhases.contains(target)) {
            throw new BusinessRuleException(String.format(
                    "Illegal voyage phase transition from %s to %s. Permitted next phases: %s",
                    current, target, validNextPhases
            ));
        }
    }

    private VoyageLifecycleState findOrThrow(String voyageNumber) {
        return stateRepository.findByVoyageNumber(voyageNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage lifecycle state not found for: " + voyageNumber));
    }

    private void recordAudit(String action, String actor, String reason, String details) {
        PosAuditLog logEntry = PosAuditLog.builder()
                .action(action)
                .performedBy(actor != null ? actor : "CHIEF_PURSER")
                .reason(reason)
                .details(details)
                .build();
        auditLogRepository.save(logEntry);
    }

    private VoyageLifecycleStateDto toDto(VoyageLifecycleState s) {
        return new VoyageLifecycleStateDto(
                s.getId(),
                s.getVoyageNumber(),
                s.getShipId(),
                s.getItineraryName(),
                s.getCurrentPhase(),
                s.getCurrentPortName(),
                s.getCurrentTimezone(),
                s.getVesselClockOffsetMinutes(),
                s.getClockSyncEnabled(),
                s.getPhaseTransitionedAt(),
                s.getTransitionedBy(),
                s.getTransitionReason(),
                s.getTaxExemptionActive(),
                s.getCasinoOpen(),
                s.getCreatedAt() != null ? s.getCreatedAt() : Instant.now()
        );
    }
}
