package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscalateCaseRequest(
        @NotBlank(message = "escalationReason is required")
        @Size(max = 255)
        String escalationReason
) {}
