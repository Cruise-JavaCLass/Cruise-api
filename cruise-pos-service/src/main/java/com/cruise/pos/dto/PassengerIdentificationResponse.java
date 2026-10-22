package com.cruise.pos.dto;

import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
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
public class PassengerIdentificationResponse {

    private Long passengerId;
    private String fullName;
    private String cabinNumber;
    private String photoUrl;
    private CredentialType credentialType;
    private String maskedIdentifier;
    private CredentialStatus credentialStatus;
    private FolioStatus folioStatus;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableLimit;
    private BigDecimal individualSpendingLimit;
    private Boolean spendingAllowed;
    private Boolean alcoholRestricted;
    private Boolean isMinor;
}
