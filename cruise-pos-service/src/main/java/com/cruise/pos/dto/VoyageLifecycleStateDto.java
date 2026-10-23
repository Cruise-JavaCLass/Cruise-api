package com.cruise.pos.dto;

import com.cruise.pos.enums.VoyageOperationalPhase;

import java.time.Instant;

public record VoyageLifecycleStateDto(
        Long id,
        String voyageNumber,
        String shipId,
        String itineraryName,
        VoyageOperationalPhase currentPhase,
        String currentPortName,
        String currentTimezone,
        Integer vesselClockOffsetMinutes,
        Boolean clockSyncEnabled,
        Instant phaseTransitionedAt,
        String transitionedBy,
        String transitionReason,
        Boolean taxExemptionActive,
        Boolean casinoOpen,
        Instant createdAt
) {}
