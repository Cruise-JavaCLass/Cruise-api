package com.cruise.pos.dto;

import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlertDto {

    private String alertId;
    private String maskedIdentifier;
    private CredentialType credentialType;
    private CredentialStatus credentialStatus;
    private String reason;
    private String stationId;
    private String cashierId;
    private Instant timestamp;
}
