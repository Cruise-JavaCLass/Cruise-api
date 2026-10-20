package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSyncJournalResponse {
    private int totalReceived;
    private int insertedCount;
    private int duplicateSkippedCount;
    private BigDecimal totalAmountSynced;
    private List<String> skippedTransactionIds;
    private String syncStatus;
}
