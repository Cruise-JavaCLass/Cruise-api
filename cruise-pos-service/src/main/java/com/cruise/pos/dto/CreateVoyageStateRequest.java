package com.cruise.pos.dto;

import com.cruise.pos.enums.VoyageOperationalPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVoyageStateRequest(
        @NotBlank(message = "voyageNumber is required")
        @Size(max = 64)
        String voyageNumber,

        @NotBlank(message = "shipId is required")
        @Size(max = 64)
        String shipId,

        @NotBlank(message = "itineraryName is required")
        @Size(max = 128)
        String itineraryName,

        VoyageOperationalPhase initialPhase,
        @Size(max = 64)
        String initialTimezone,
        @Size(max = 128)
        String currentPortName
) {}
