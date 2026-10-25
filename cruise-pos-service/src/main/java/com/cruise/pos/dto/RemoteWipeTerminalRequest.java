package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoteWipeTerminalRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 255)
        String reason
) {}
