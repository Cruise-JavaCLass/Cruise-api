package com.cruise.pos.dto;

import com.cruise.pos.enums.FolioStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolioAccountResponse {
    private Long id;
    private String cabinNumber;
    private String primaryGuestName;
    private Long passengerId;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableLimit;
    private FolioStatus status;
}
