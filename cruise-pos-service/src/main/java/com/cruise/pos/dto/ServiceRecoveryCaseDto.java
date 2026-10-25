package com.cruise.pos.dto;

import com.cruise.pos.enums.ServiceRecoveryCaseStatus;
import com.cruise.pos.enums.ServiceRecoveryRootCause;
import com.cruise.pos.enums.ServiceRecoverySeverity;

import java.time.Instant;

public record ServiceRecoveryCaseDto(
        Long id,
        String caseNumber,
        Long passengerId,
        String passengerName,
        String cabinNumber,
        ServiceRecoveryRootCause rootCause,
        ServiceRecoverySeverity severity,
        ServiceRecoveryCaseStatus status,
        String complaintSummary,
        String complaintDetails,
        String assignedHandler,
        Instant slaDueAt,
        Instant escalatedAt,
        String escalationReason,
        String resolutionNotes,
        Instant resolvedAt,
        Instant createdAt
) {}
