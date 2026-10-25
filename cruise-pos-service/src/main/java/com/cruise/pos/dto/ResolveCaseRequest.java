package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveCaseRequest(
        @NotBlank(message = "resolutionNotes is required")
        @Size(max = 1000)
        String resolutionNotes
) {}
