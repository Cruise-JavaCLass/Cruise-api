package com.cruise.pos.service;

import com.cruise.pos.dto.SettlementResponse;

/**
 * Kết quả một lần gọi tất toán, kèm cờ cho biết đây là replay của request trước đó.
 */
public record SettleOutcome(SettlementResponse response, boolean replayed) {
}
