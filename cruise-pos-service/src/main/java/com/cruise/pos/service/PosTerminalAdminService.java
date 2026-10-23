package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.PosTerminal;
import com.cruise.pos.enums.PosTerminalStatus;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosTerminalRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosTerminalAdminService {

    private final PosTerminalRepository terminalRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ProvisionTerminalResponse provisionTerminal(ProvisionTerminalRequest request, String performedBy) {
        log.info("Provisioning POS terminal deviceId={}, stationId={}, performedBy={}", 
                request.deviceId(), request.stationId(), performedBy);

        if (terminalRepository.existsByDeviceId(request.deviceId())) {
            throw new IllegalArgumentException("Terminal with deviceId '" + request.deviceId() + "' already exists");
        }

        String rawApiKey = generateSecureApiKey();
        String keyHash = hashSha256(rawApiKey);
        String keyPrefix = rawApiKey.substring(0, Math.min(8, rawApiKey.length()));

        Instant certExpiry = Instant.now().plus(365, ChronoUnit.DAYS);
        String mockClientCertPem = generateMockCertificatePem(request.deviceId(), certExpiry);
        String fingerprint = request.certificateFingerprint() != null && !request.certificateFingerprint().isBlank()
                ? request.certificateFingerprint()
                : hashSha256(mockClientCertPem);

        PosTerminal terminal = PosTerminal.builder()
                .deviceId(request.deviceId())
                .deviceName(request.deviceName())
                .stationId(request.stationId())
                .ipAddress(request.ipAddress())
                .macAddress(request.macAddress())
                .firmwareVersion(request.firmwareVersion() != null ? request.firmwareVersion() : "v1.0.0")
                .status(PosTerminalStatus.ACTIVE)
                .apiKeyHash(keyHash)
                .apiKeyPrefix(keyPrefix)
                .certificateFingerprint(fingerprint)
                .certificateExpiry(certExpiry)
                .tokenVersion(1)
                .lastSeenAt(Instant.now())
                .build();

        PosTerminal saved;
        try {
            saved = terminalRepository.saveAndFlush(terminal);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Terminal with deviceId '" + request.deviceId() + "' already exists", ex);
        }

        recordAuditLog(
                "DEVICE_PROVISIONED",
                performedBy,
                "Device " + request.deviceId() + " successfully provisioned with mTLS cert & API Key",
                "stationId=" + request.stationId() + ", fingerPrint=" + fingerprint
        );

        return new ProvisionTerminalResponse(
                toDto(saved),
                rawApiKey,
                mockClientCertPem,
                certExpiry,
                "POS terminal provisioned successfully. Raw API key is shown only once."
        );
    }

    @Transactional
    public ProvisionTerminalResponse rotateApiKey(String deviceId, RotateTerminalKeyRequest request, String performedBy) {
        log.info("Rotating API key for terminal deviceId={}, performedBy={}", deviceId, performedBy);
        PosTerminal terminal = findTerminalOrThrow(deviceId);

        if (terminal.getStatus() == PosTerminalStatus.REVOKED || terminal.getStatus() == PosTerminalStatus.WIPED) {
            throw new IllegalStateException("Cannot rotate API key for terminal in status " + terminal.getStatus());
        }

        String rawApiKey = generateSecureApiKey();
        String keyHash = hashSha256(rawApiKey);
        String keyPrefix = rawApiKey.substring(0, Math.min(8, rawApiKey.length()));

        terminal.setApiKeyHash(keyHash);
        terminal.setApiKeyPrefix(keyPrefix);
        terminal.setTokenVersion(terminal.getTokenVersion() + 1);

        PosTerminal saved = terminalRepository.save(terminal);

        recordAuditLog(
                "DEVICE_TOKEN_ROTATED",
                performedBy,
                "API Key rotated. Reason: " + (request != null && request.reason() != null ? request.reason() : "Regular rotation"),
                "deviceId=" + deviceId + ", newVersion=" + saved.getTokenVersion()
        );

        return new ProvisionTerminalResponse(
                toDto(saved),
                rawApiKey,
                null,
                saved.getCertificateExpiry(),
                "API key rotated successfully. Raw API key is shown only once."
        );
    }

    @Transactional
    public PosTerminalDto revokeTerminal(String deviceId, RevokeTerminalRequest request, String performedBy) {
        log.warn("Revoking POS terminal deviceId={}, reason={}, performedBy={}", deviceId, request.reason(), performedBy);
        PosTerminal terminal = findTerminalOrThrow(deviceId);
        PosTerminalStatus previousStatus = terminal.getStatus();

        terminal.setStatus(PosTerminalStatus.REVOKED);
        terminal.setApiKeyHash(null);
        terminal.setTokenVersion(terminal.getTokenVersion() + 1);

        PosTerminal saved = terminalRepository.save(terminal);

        recordAuditLog(
                "DEVICE_REVOKED",
                performedBy,
                "Device revoked. Reason: " + request.reason(),
                "deviceId=" + deviceId + ", previousStatus=" + previousStatus
        );

        return toDto(saved);
    }

    @Transactional
    public CertificateRenewalResponse renewCertificate(String deviceId, RenewCertificateRequest request, String performedBy) {
        log.info("Renewing mTLS certificate for terminal deviceId={}, performedBy={}", deviceId, performedBy);
        PosTerminal terminal = findTerminalOrThrow(deviceId);

        if (terminal.getStatus() == PosTerminalStatus.REVOKED || terminal.getStatus() == PosTerminalStatus.WIPED) {
            throw new IllegalStateException("Cannot renew certificate for terminal in status " + terminal.getStatus());
        }

        if (request != null && request.customExpiry() != null && request.customExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("customExpiry must be in the future");
        }

        Instant newExpiry = (request != null && request.customExpiry() != null)
                ? request.customExpiry()
                : Instant.now().plus(365, ChronoUnit.DAYS);

        if (request != null && request.csrPem() != null && !request.csrPem().isBlank()) {
            throw new IllegalArgumentException("CSR-based renewal is not supported yet");
        }
        String certPem = generateMockCertificatePem(deviceId, newExpiry);
        String fingerprint = hashSha256(certPem);

        terminal.setCertificateExpiry(newExpiry);
        terminal.setCertificateFingerprint(fingerprint);
        PosTerminal saved = terminalRepository.save(terminal);

        recordAuditLog(
                "DEVICE_CERTIFICATE_RENEWED",
                performedBy,
                "mTLS Certificate renewed until " + newExpiry,
                "deviceId=" + deviceId + ", fingerprint=" + fingerprint
        );

        return new CertificateRenewalResponse(
                saved.getDeviceId(),
                certPem,
                fingerprint,
                newExpiry,
                "Certificate renewed successfully"
        );
    }

    @Transactional
    public PosTerminalDto issueRemoteWipe(String deviceId, RemoteWipeTerminalRequest request, String performedBy) {
        log.warn("Initiating REMOTE WIPE for terminal deviceId={}, performedBy={}", deviceId, performedBy);
        PosTerminal terminal = findTerminalOrThrow(deviceId);

        terminal.setStatus(PosTerminalStatus.WIPED);
        terminal.setRemoteWipeCommand("FACTORY_RESET_AND_PURGE_KEYS");
        terminal.setRemoteWipeRequestedAt(Instant.now());
        terminal.setApiKeyHash(null);
        terminal.setTokenVersion(terminal.getTokenVersion() + 1);

        PosTerminal saved = terminalRepository.save(terminal);

        recordAuditLog(
                "DEVICE_REMOTE_WIPE_ISSUED",
                performedBy,
                "Remote wipe command issued. Reason: " + request.reason(),
                "deviceId=" + deviceId + ", command=FACTORY_RESET_AND_PURGE_KEYS"
        );

        return toDto(saved);
    }

    @Transactional
    public PosTerminalDto acknowledgeRemoteWipe(String deviceId, RemoteWipeAckRequest request) {
        PosTerminal terminal = findTerminalOrThrow(deviceId);
        if (terminal.getRemoteWipeRequestedAt() == null || terminal.getStatus() != PosTerminalStatus.WIPED) {
            throw new IllegalStateException("No pending remote wipe command for terminal " + deviceId);
        }

        terminal.setRemoteWipeAcknowledgedAt(Instant.now());
        PosTerminal saved = terminalRepository.save(terminal);

        recordAuditLog(
                "DEVICE_REMOTE_WIPE_ACKNOWLEDGED",
                "SYSTEM_AGENT",
                "Terminal acknowledged wipe execution: " + (request != null ? request.wipeStatus() : "SUCCESS"),
                "deviceId=" + deviceId + ", details=" + (request != null ? request.executionDetails() : "WIPED")
        );

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PosTerminalDto> listTerminals(Pageable pageable) {
        return terminalRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PosTerminalDto getTerminal(String deviceId) {
        return toDto(findTerminalOrThrow(deviceId));
    }

    private PosTerminal findTerminalOrThrow(String deviceId) {
        return terminalRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Terminal not found with deviceId: " + deviceId));
    }

    private String generateSecureApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "pos_sec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateMockCertificatePem(String deviceId, Instant expiry) {
        String payload = "MOCK_CERT_FOR_" + deviceId + "_EXP_" + expiry.toEpochMilli();
        String base64 = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----";
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private void recordAuditLog(String action, String actor, String reason, String details) {
        try {
            PosAuditLog auditLog = PosAuditLog.builder()
                    .action(action)
                    .performedBy(actor)
                    .reason(reason)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed to record terminal audit log: {}", ex.getMessage(), ex);
        }
    }

    private PosTerminalDto toDto(PosTerminal t) {
        return new PosTerminalDto(
                t.getId(),
                t.getDeviceId(),
                t.getDeviceName(),
                t.getStationId(),
                t.getIpAddress(),
                t.getMacAddress(),
                t.getStatus(),
                t.getApiKeyPrefix(),
                t.getCertificateFingerprint(),
                t.getCertificateExpiry(),
                t.getTokenVersion(),
                t.getLastSeenAt(),
                t.getFirmwareVersion(),
                t.getRemoteWipeCommand(),
                t.getRemoteWipeRequestedAt(),
                t.getRemoteWipeAcknowledgedAt(),
                t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now()
        );
    }
}
