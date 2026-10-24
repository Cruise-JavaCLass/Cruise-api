package com.cruise.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashDropResponse(
        Long dropId,
        Long shiftId,
        String stationId,
        String cashierId,
        String witnessId,
        BigDecimal amount,
        BigDecimal safeBalanceAfter,
        String receiptNumber,
        String currencyBreakdown,
        Instant droppedAt,
        Boolean dualSignatureVerified,
        String escPosReceiptSlip
) {
}
