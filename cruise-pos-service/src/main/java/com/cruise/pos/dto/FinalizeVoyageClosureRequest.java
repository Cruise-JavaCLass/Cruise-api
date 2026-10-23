package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinalizeVoyageClosureRequest(
        @NotBlank(message = "closureAuditNotes is required")
        @Size(max = 1000)
        String closureAuditNotes
) {}
