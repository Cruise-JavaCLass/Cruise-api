package com.cruise.pos.dto;

import jakarta.validation.constraints.Size;

public record RotateTerminalKeyRequest(
        @Size(max = 100)
        String reason
) {}
