package com.cruise.pos.dto;

import com.cruise.pos.enums.ServiceRecoveryRootCause;
import com.cruise.pos.enums.ServiceRecoverySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateServiceRecoveryCaseRequest(
        @NotNull(message = "passengerId is required")
        Long passengerId,

        @NotBlank(message = "passengerName is required")
        @Size(max = 128)
        String passengerName,

        @NotBlank(message = "cabinNumber is required")
        @Size(max = 32)
        String cabinNumber,

        @NotNull(message = "rootCause is required")
        ServiceRecoveryRootCause rootCause,

        @NotNull(message = "severity is required")
        ServiceRecoverySeverity severity,

        @NotBlank(message = "complaintSummary is required")
        @Size(max = 255)
        String complaintSummary,

        String complaintDetails,

        @Size(max = 64)
        String assignedHandler
) {}
