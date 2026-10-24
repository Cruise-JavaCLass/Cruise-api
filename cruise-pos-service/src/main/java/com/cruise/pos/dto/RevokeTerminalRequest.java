package com.cruise.pos.dto;

import jakarta.validation.constraints.Size;

public record RevokeTerminalRequest(
        @Size(max = 255)
        String reason
) {}
