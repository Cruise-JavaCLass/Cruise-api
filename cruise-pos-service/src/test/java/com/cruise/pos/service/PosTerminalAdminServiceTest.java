package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.PosTerminal;
import com.cruise.pos.enums.PosTerminalStatus;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosTerminalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosTerminalAdminServiceTest {

    @Mock
    private PosTerminalRepository terminalRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private PosTerminalAdminService terminalAdminService;

    private PosTerminal sampleTerminal;

    @BeforeEach
    void setUp() {
        sampleTerminal = PosTerminal.builder()
                .deviceId("TERM-DECK9-01")
                .deviceName("Lido Deck Bar Terminal 1")
                .stationId("BAR-DECK-9")
                .ipAddress("192.168.1.101")
                .macAddress("00:1A:2B:3C:4D:5E")
                .firmwareVersion("v2.1.0")
                .status(PosTerminalStatus.ACTIVE)
                .apiKeyHash("existing_hash")
                .apiKeyPrefix("pos_sec_")
                .certificateFingerprint("fp_sample")
                .certificateExpiry(Instant.now().plus(30, ChronoUnit.DAYS))
                .tokenVersion(1)
                .build();
    }

    @Test
    @DisplayName("provisionTerminal: creates terminal with active status, hashed key, and generates cert")
    void testProvisionTerminalSuccess() {
        ProvisionTerminalRequest req = new ProvisionTerminalRequest(
                "TERM-NEW-01",
                "Promenade Cafe Station",
                "CAFE-DECK-5",
                "10.0.1.55",
                "AA:BB:CC:DD:EE:FF",
                "v3.0.0",
                null
        );

        when(terminalRepository.existsByDeviceId("TERM-NEW-01")).thenReturn(false);
        when(terminalRepository.saveAndFlush(any(PosTerminal.class))).thenAnswer(inv -> {
            PosTerminal t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        ProvisionTerminalResponse res = terminalAdminService.provisionTerminal(req, "ADMIN_JULES");

        assertThat(res).isNotNull();
        assertThat(res.terminal().deviceId()).isEqualTo("TERM-NEW-01");
        assertThat(res.terminal().status()).isEqualTo(PosTerminalStatus.ACTIVE);
        assertThat(res.rawApiKey()).startsWith("pos_sec_");
        assertThat(res.clientCertificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(res.certificateExpiry()).isAfter(Instant.now());

        verify(auditLogRepository).save(argThat(log -> "DEVICE_PROVISIONED".equals(log.getAction())));
    }

    @Test
    @DisplayName("provisionTerminal: throws IllegalArgumentException if deviceId already exists")
    void testProvisionTerminalDuplicateThrows() {
        ProvisionTerminalRequest req = new ProvisionTerminalRequest(
                "TERM-DECK9-01",
                "Duplicate Terminal",
                "BAR-DECK-9",
                null, null, null, null
        );

        when(terminalRepository.existsByDeviceId("TERM-DECK9-01")).thenReturn(true);

        assertThatThrownBy(() -> terminalAdminService.provisionTerminal(req, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(terminalRepository, never()).save(any());
    }

    @Test
    @DisplayName("provisionTerminal: handles concurrent DataIntegrityViolationException on saveAndFlush")
    void testProvisionTerminalDuplicateRaceThrows() {
        ProvisionTerminalRequest req = new ProvisionTerminalRequest(
                "TERM-RACE-01",
                "Race Terminal",
                "STATION-1",
                null, null, null, null
        );

        when(terminalRepository.existsByDeviceId("TERM-RACE-01")).thenReturn(false);
        when(terminalRepository.saveAndFlush(any(PosTerminal.class)))
                .thenThrow(new DataIntegrityViolationException("uk_pos_terminal_device_id"));

        assertThatThrownBy(() -> terminalAdminService.provisionTerminal(req, "ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("rotateApiKey: generates new key hash, increments tokenVersion, and logs audit")
    void testRotateApiKeySuccess() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        RotateTerminalKeyRequest req = new RotateTerminalKeyRequest("Routine security key cycle");
        ProvisionTerminalResponse res = terminalAdminService.rotateApiKey("TERM-DECK9-01", req, "SEC_ADMIN");

        assertThat(res).isNotNull();
        assertThat(res.rawApiKey()).startsWith("pos_sec_");
        assertThat(res.terminal().tokenVersion()).isEqualTo(2);

        ArgumentCaptor<PosTerminal> captor = ArgumentCaptor.forClass(PosTerminal.class);
        verify(terminalRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2);
        assertThat(captor.getValue().getApiKeyHash()).isNotEqualTo("existing_hash");

        verify(auditLogRepository).save(argThat(l -> "DEVICE_TOKEN_ROTATED".equals(l.getAction())));
    }

    @Test
    @DisplayName("rotateApiKey: throws IllegalStateException when terminal is REVOKED or WIPED")
    void testRotateApiKey_RevokedOrWipedThrows() {
        sampleTerminal.setStatus(PosTerminalStatus.REVOKED);
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));

        assertThatThrownBy(() -> terminalAdminService.rotateApiKey("TERM-DECK9-01", null, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVOKED");

        sampleTerminal.setStatus(PosTerminalStatus.WIPED);
        assertThatThrownBy(() -> terminalAdminService.rotateApiKey("TERM-DECK9-01", null, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WIPED");
    }

    @Test
    @DisplayName("revokeTerminal: updates status to REVOKED, removes key hash, and logs audit")
    void testRevokeTerminalSuccess() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        RevokeTerminalRequest req = new RevokeTerminalRequest("Suspected physical tampering");
        PosTerminalDto dto = terminalAdminService.revokeTerminal("TERM-DECK9-01", req, "SEC_ADMIN");

        assertThat(dto.status()).isEqualTo(PosTerminalStatus.REVOKED);

        ArgumentCaptor<PosTerminal> captor = ArgumentCaptor.forClass(PosTerminal.class);
        verify(terminalRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PosTerminalStatus.REVOKED);
        assertThat(captor.getValue().getApiKeyHash()).isNull();

        verify(auditLogRepository).save(argThat(l -> "DEVICE_REVOKED".equals(l.getAction())));
    }

    @Test
    @DisplayName("renewCertificate: issues updated cert expiry and fingerprint")
    void testRenewCertificateSuccess() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        RenewCertificateRequest req = new RenewCertificateRequest(Instant.now().plus(730, ChronoUnit.DAYS), null);
        CertificateRenewalResponse res = terminalAdminService.renewCertificate("TERM-DECK9-01", req, "SYS_ADMIN");

        assertThat(res.clientCertificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(res.certificateFingerprint()).isNotEmpty();

        verify(auditLogRepository).save(argThat(l -> "DEVICE_CERTIFICATE_RENEWED".equals(l.getAction())));
    }

    @Test
    @DisplayName("renewCertificate: throws IllegalArgumentException when customExpiry is in the past")
    void testRenewCertificate_PastExpiryThrows() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));

        RenewCertificateRequest req = new RenewCertificateRequest(Instant.now().minus(1, ChronoUnit.DAYS), null);
        assertThatThrownBy(() -> terminalAdminService.renewCertificate("TERM-DECK9-01", req, "SYS_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customExpiry must be in the future");
    }

    @Test
    @DisplayName("renewCertificate: uses provided CSR PEM when present")
    void testRenewCertificate_WithCsrPem() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        String customPem = "-----BEGIN CERTIFICATE REQUEST-----\nMOCK_CSR\n-----END CERTIFICATE REQUEST-----";
        RenewCertificateRequest req = new RenewCertificateRequest(Instant.now().plus(60, ChronoUnit.DAYS), customPem);
        CertificateRenewalResponse res = terminalAdminService.renewCertificate("TERM-DECK9-01", req, "SYS_ADMIN");

        assertThat(res.clientCertificatePem()).isEqualTo(customPem);
    }

    @Test
    @DisplayName("issueRemoteWipe & acknowledgeRemoteWipe: transitions status to WIPED and records ack")
    void testRemoteWipeFlow() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        RemoteWipeTerminalRequest req = new RemoteWipeTerminalRequest("Device lost off port terminal");
        PosTerminalDto dto = terminalAdminService.issueRemoteWipe("TERM-DECK9-01", req, "CHIEF_OFFICER");

        assertThat(dto.status()).isEqualTo(PosTerminalStatus.WIPED);
        assertThat(dto.remoteWipeCommand()).isEqualTo("FACTORY_RESET_AND_PURGE_KEYS");

        // Acknowledge wipe
        RemoteWipeAckRequest ackReq = new RemoteWipeAckRequest("COMPLETED", "Secure flash erasure verified");
        PosTerminalDto ackDto = terminalAdminService.acknowledgeRemoteWipe("TERM-DECK9-01", ackReq);

        assertThat(ackDto.remoteWipeAcknowledgedAt()).isNotNull();
        verify(auditLogRepository).save(argThat(l -> "DEVICE_REMOTE_WIPE_ACKNOWLEDGED".equals(l.getAction())));
    }

    @Test
    @DisplayName("acknowledgeRemoteWipe: throws IllegalStateException when terminal is not in WIPED state")
    void testAcknowledgeRemoteWipe_WithoutPendingWipeThrows() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));

        RemoteWipeAckRequest ackReq = new RemoteWipeAckRequest("COMPLETED", "No wipe was requested");
        assertThatThrownBy(() -> terminalAdminService.acknowledgeRemoteWipe("TERM-DECK9-01", ackReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pending remote wipe command");
    }

    @Test
    @DisplayName("getTerminal: throws EntityNotFoundException if not found")
    void testGetTerminalNotFoundThrows() {
        when(terminalRepository.findByDeviceId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> terminalAdminService.getTerminal("UNKNOWN"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("listTerminals: returns paginated terminal DTOs")
    void testListTerminals() {
        Page<PosTerminal> page = new PageImpl<>(List.of(sampleTerminal));
        when(terminalRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<PosTerminalDto> result = terminalAdminService.listTerminals(PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).deviceId()).isEqualTo("TERM-DECK9-01");
    }

    @Test
    @DisplayName("rotateApiKey and acknowledgeRemoteWipe: handle null requests gracefully")
    void testNullRequestHandling() {
        when(terminalRepository.findByDeviceId("TERM-DECK9-01")).thenReturn(Optional.of(sampleTerminal));
        when(terminalRepository.save(any(PosTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        ProvisionTerminalResponse res = terminalAdminService.rotateApiKey("TERM-DECK9-01", null, "ADMIN");
        assertThat(res).isNotNull();

        sampleTerminal.setStatus(PosTerminalStatus.WIPED);
        sampleTerminal.setRemoteWipeRequestedAt(Instant.now());

        PosTerminalDto ackDto = terminalAdminService.acknowledgeRemoteWipe("TERM-DECK9-01", null);
        assertThat(ackDto.remoteWipeAcknowledgedAt()).isNotNull();
    }
}
