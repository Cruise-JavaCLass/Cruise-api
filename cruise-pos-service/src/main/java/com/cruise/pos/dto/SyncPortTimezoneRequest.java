package com.cruise.pos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncPortTimezoneRequest(
        @NotBlank(message = "portName is required")
        @Size(max = 128)
        String portName,

        @NotBlank(message = "timezone is required")
        @Size(max = 64)
        String timezone, // e.g. "Europe/Athens", "UTC+02:00"

        @Min(-720)
        @Max(840)
        Integer clockOffsetMinutes,

        @Size(max = 255)
        String adjustmentReason
) {}
