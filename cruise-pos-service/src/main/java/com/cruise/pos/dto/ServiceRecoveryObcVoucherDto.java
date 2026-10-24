package com.cruise.pos.dto;

import com.cruise.pos.enums.ObcVoucherStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceRecoveryObcVoucherDto(
        Long id,
        String voucherCode,
        Long caseId,
        String caseNumber,
        Long passengerId,
        String passengerName,
        String cabinNumber,
        BigDecimal creditAmount,
        ObcVoucherStatus status,
        String apologyTitle,
        String apologyLetterBody,
        String authorizedBy,
        Instant issuedAt,
        Instant expiresAt,
        Instant claimedAt,
        String folioTransactionId,
        Instant createdAt
) {}
