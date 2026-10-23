package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinMemberDto {
    private Long id;
    private String cabinNumber;
    private Long passengerId;
    private String guestName;
    private Integer age;
    private Boolean isMinor;
    private Boolean isAuthorizedSigner;
    private BigDecimal individualCreditLimit;
    private BigDecimal individualBalance;
    private BigDecimal availableLimit;
}
