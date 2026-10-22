package com.cruise.pos.service;

import com.cruise.pos.dto.IdentifyPassengerRequest;
import com.cruise.pos.dto.ParsedCredential;
import com.cruise.pos.dto.PassengerIdentificationResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PosCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosPassengerAuthService {

    private final PosCredentialRepository credentialRepository;
    private final CabinFolioAccountRepository folioRepository;
    private final GuestCardVerificationEngine verificationEngine;
    private final PosCredentialAuditAndManifestService auditAndManifestService;

    public ParsedCredential parseRawCredential(String raw) {
        Objects.requireNonNull(raw, "rawCredential must not be null");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("Credential cannot be empty");
        }

        if (trimmed.startsWith("QR:")) {
            String token = trimmed.substring(3).trim();
            if (token.isEmpty()) {
                throw new BusinessRuleException("QR token cannot be empty");
            }
            return new ParsedCredential(CredentialType.QR, token);
        } else if (trimmed.startsWith("CARD:")) {
            String card = trimmed.substring(5).trim();
            if (card.isEmpty()) {
                throw new BusinessRuleException("Card number cannot be empty");
            }
            return new ParsedCredential(CredentialType.CARD, card);
        } else if (trimmed.startsWith("RFID:")) {
            String uid = trimmed.substring(5).trim();
            if (uid.isEmpty()) {
                throw new BusinessRuleException("RFID UID cannot be empty");
            }
            return new ParsedCredential(CredentialType.RFID, uid);
        }

        // Fallback auto-detection if no prefix
        if (trimmed.startsWith("%") || trimmed.startsWith(";") || trimmed.matches("^[0-9]{16}$")) {
            return new ParsedCredential(CredentialType.CARD, trimmed);
        }
        if (trimmed.matches("^[0-9A-Fa-f]{8,14}$")) {
            return new ParsedCredential(CredentialType.RFID, trimmed.toUpperCase());
        }
        return new ParsedCredential(CredentialType.QR, trimmed);
    }

    public String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 4) {
            return "****";
        }
        int len = identifier.length();
        String suffix = identifier.substring(len - 4);
        return "*".repeat(len - 4) + suffix;
    }

    @Transactional(readOnly = true)
    public PassengerIdentificationResponse identify(IdentifyPassengerRequest request) {
        Objects.requireNonNull(request, "IdentifyPassengerRequest must not be null");
        ParsedCredential parsed = parseRawCredential(request.getRawCredential());
        String masked = maskIdentifier(parsed.getIdentifier());

        try {
            PosCredential credential = credentialRepository.findByIdentifier(parsed.getIdentifier())
                    .orElseThrow(() -> new ResourceNotFoundException("Credential not found for identifier: " + masked));

            // AC2: Security validation and fraud prevention
            verificationEngine.validateCredentialSecurity(credential, request);

            CabinFolioAccount folio = folioRepository.findByCabinNumber(credential.getCabinNumber())
                    .orElseGet(() -> folioRepository.findByPassengerId(credential.getPassengerId())
                            .orElse(null));

            BigDecimal creditLimit = folio != null && folio.getCreditLimit() != null ? folio.getCreditLimit() : BigDecimal.ZERO;
            BigDecimal currentBalance = folio != null && folio.getCurrentBalance() != null ? folio.getCurrentBalance() : BigDecimal.ZERO;
            BigDecimal availableLimit = folio != null ? folio.getAvailableLimit() : BigDecimal.ZERO;
            FolioStatus folioStatus = folio != null ? folio.getStatus() : FolioStatus.LOCKED;

            boolean finalSpendingAllowed = verificationEngine.evaluateSpendingAllowance(credential, folio);
            boolean alcoholRestricted = verificationEngine.isAlcoholRestricted(credential);

            PassengerIdentificationResponse response = PassengerIdentificationResponse.builder()
                    .passengerId(credential.getPassengerId())
                    .fullName(credential.getFullName())
                    .cabinNumber(credential.getCabinNumber())
                    .photoUrl(credential.getPhotoUrl())
                    .credentialType(credential.getCredentialType())
                    .maskedIdentifier(masked)
                    .credentialStatus(credential.getStatus())
                    .folioStatus(folioStatus)
                    .creditLimit(creditLimit)
                    .currentBalance(currentBalance)
                    .availableLimit(availableLimit)
                    .individualSpendingLimit(credential.getIndividualSpendingLimit())
                    .spendingAllowed(finalSpendingAllowed)
                    .alcoholRestricted(alcoholRestricted)
                    .isMinor(Boolean.TRUE.equals(credential.getIsMinor()))
                    .build();

            auditAndManifestService.recordAudit(
                    request.getStationId(),
                    request.getCashierId(),
                    credential.getCredentialType(),
                    masked,
                    "SUCCESS",
                    null
            );

            return response;
        } catch (Exception e) {
            auditAndManifestService.recordAudit(
                    request.getStationId(),
                    request.getCashierId(),
                    parsed.getType(),
                    masked,
                    "FAILED",
                    e.getMessage()
            );
            throw e;
        }
    }
}
