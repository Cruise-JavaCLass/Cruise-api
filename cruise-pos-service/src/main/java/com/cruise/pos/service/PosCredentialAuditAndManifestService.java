package com.cruise.pos.service;

import com.cruise.pos.dto.CredentialManifestCacheResponse;
import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.entity.PosCredentialAuditLog;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import com.cruise.pos.repository.PosCredentialAuditLogRepository;
import com.cruise.pos.repository.PosCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosCredentialAuditAndManifestService {

    private final PosCredentialAuditLogRepository auditLogRepository;
    private final PosCredentialRepository credentialRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String stationId, String cashierId, CredentialType credentialType, String maskedIdentifier, String result, String failureReason) {
        try {
            String sanitizedFailureReason = failureReason;
            if (sanitizedFailureReason != null && sanitizedFailureReason.length() > 255) {
                sanitizedFailureReason = sanitizedFailureReason.substring(0, 255);
            }

            PosCredentialAuditLog logEntry = PosCredentialAuditLog.builder()
                    .stationId(stationId)
                    .cashierId(cashierId)
                    .credentialType(credentialType)
                    .maskedIdentifier(maskedIdentifier)
                    .timestamp(Instant.now())
                    .result(result)
                    .failureReason(sanitizedFailureReason)
                    .build();
            auditLogRepository.save(logEntry);
            log.info("Recorded credential audit log: identifier={}, result={}, station={}", maskedIdentifier, result, stationId);
        } catch (Exception e) {
            log.error("Failed to record credential audit log: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public CredentialManifestCacheResponse getActiveManifestCache() {
        List<PosCredential> activeCreds = credentialRepository.findAllByStatusActive(CredentialStatus.ACTIVE);
        List<CredentialManifestCacheResponse.ManifestCredentialEntry> entries = activeCreds.stream()
                .map(c -> CredentialManifestCacheResponse.ManifestCredentialEntry.builder()
                        .identifier(c.getIdentifier())
                        .credentialType(c.getCredentialType())
                        .passengerId(c.getPassengerId())
                        .fullName(c.getFullName())
                        .cabinNumber(c.getCabinNumber())
                        .photoUrl(c.getPhotoUrl())
                        .status(c.getStatus())
                        .spendingAllowed(Boolean.TRUE.equals(c.getSpendingAllowed()))
                        .alcoholRestricted(Boolean.TRUE.equals(c.getAlcoholRestricted()) || Boolean.TRUE.equals(c.getIsMinor()))
                        .isMinor(Boolean.TRUE.equals(c.getIsMinor()))
                        .individualSpendingLimit(c.getIndividualSpendingLimit())
                        .build())
                .collect(Collectors.toList());

        return CredentialManifestCacheResponse.builder()
                .generatedAt(Instant.now())
                .totalActiveCount(entries.size())
                .credentials(entries)
                .build();
    }
}
