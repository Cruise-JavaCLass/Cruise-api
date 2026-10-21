package com.cruise.pos.dto;

import com.cruise.pos.enums.FolioStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioStatementResponse {
    private Long id;
    private String cabinNumber;
    private String primaryGuestName;
    private Long passengerId;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableLimit;
    private FolioStatus status;

    private BigDecimal totalCharges;
    private BigDecimal totalCredits;
    private BigDecimal netBalance;

    @Builder.Default
    private List<FolioLedgerItemDto> items = new ArrayList<>();
}
