package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO phản hồi bảng kê chi tiêu Folio tự phục vụ của hành khách (KAN-721).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerSelfFolioResponse {
    private Long passengerId;
    private String cabinNumber;
    private String bookingReference;
    private String currency;
    private BigDecimal creditLimit;
    private BigDecimal totalCharges;
    private BigDecimal remainingBalance;
    private String folioStatus;
    private List<FolioLineItemDto> transactions;
}
