package com.cruise.pos.service;

import com.cruise.pos.dto.IdentifyPassengerRequest;
import com.cruise.pos.dto.PassengerIdentificationResponse;
import com.cruise.pos.dto.SecurityAlertDto;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.CredentialSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestCardVerificationEngine {

    public void validateCredentialSecurity(PosCredential credential, IdentifyPassengerRequest request) {
        if (credential.getStatus() == CredentialStatus.REPORTED_LOST) {
            recordSecurityAlert(credential, request, "Card reported lost by passenger/security");
            throw new CredentialSecurityException("Credential has been REPORTED_LOST. Transaction blocked and security alerted.");
        }
        if (credential.getStatus() == CredentialStatus.BLOCKED) {
            recordSecurityAlert(credential, request, "Card is administratively BLOCKED");
            throw new CredentialSecurityException("Credential is BLOCKED. Transaction not permitted.");
        }
    }

    public SecurityAlertDto recordSecurityAlert(PosCredential credential, IdentifyPassengerRequest request, String reason) {
        String alertId = UUID.randomUUID().toString();
        log.warn("SECURITY ALERT [{}] - Credential status: {}, Identifier: {}, Station: {}, Cashier: {}, Reason: {}",
                alertId, credential.getStatus(), mask(credential.getIdentifier()), request.getStationId(), request.getCashierId(), reason);

        return SecurityAlertDto.builder()
                .alertId(alertId)
                .maskedIdentifier(mask(credential.getIdentifier()))
                .credentialType(credential.getCredentialType())
                .credentialStatus(credential.getStatus())
                .reason(reason)
                .stationId(request.getStationId())
                .cashierId(request.getCashierId())
                .timestamp(Instant.now())
                .build();
    }

    public boolean evaluateSpendingAllowance(PosCredential credential, CabinFolioAccount folio) {
        if (credential.getStatus() != CredentialStatus.ACTIVE) {
            return false;
        }
        if (!Boolean.TRUE.equals(credential.getSpendingAllowed())) {
            return false;
        }
        if (folio == null || folio.getStatus() != FolioStatus.ACTIVE) {
            return false;
        }
        return folio.getAvailableLimit() != null && folio.getAvailableLimit().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isAlcoholRestricted(PosCredential credential) {
        return Boolean.TRUE.equals(credential.getIsMinor()) || Boolean.TRUE.equals(credential.getAlcoholRestricted());
    }

    private String mask(String id) {
        if (id == null || id.length() <= 4) return "****";
        return "*".repeat(id.length() - 4) + id.substring(id.length() - 4);
    }
}
