package com.cruise.pos.dto;

import com.cruise.pos.enums.VoyageOperationalPhase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionVoyagePhaseRequest(
        @NotNull(message = "targetPhase is required")
        VoyageOperationalPhase targetPhase,

        @Size(max = 255)
        String transitionReason,

        @Size(max = 128)
        String currentPortName
) {}
