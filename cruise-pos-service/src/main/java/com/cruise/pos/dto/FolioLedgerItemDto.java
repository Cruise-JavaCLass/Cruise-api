package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioLedgerItemDto {
    private Long id;
    private String transactionId;
    private String description;
    private String serviceArea;
    private BigDecimal amount;
    private Boolean isReversal;
    private Instant postedAt;
    private BigDecimal runningBalance;
}
