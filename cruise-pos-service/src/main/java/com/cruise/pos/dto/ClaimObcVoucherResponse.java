package com.cruise.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimObcVoucherResponse(
        ServiceRecoveryObcVoucherDto voucher,
        String folioTransactionId,
        BigDecimal creditedAmount,
        String cabinNumber,
        BigDecimal updatedAvailableLimit,
        Instant processedAt,
        String message
) {}
