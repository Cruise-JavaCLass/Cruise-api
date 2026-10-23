package com.cruise.pos.service;

import com.cruise.pos.dto.CredentialManifestCacheResponse;
import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.entity.PosCredentialAuditLog;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import com.cruise.pos.repository.PosCredentialAuditLogRepository;
import com.cruise.pos.repository.PosCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosCredentialAuditAndManifestService Unit Tests")
class PosCredentialAuditAndManifestServiceTest {

    @Mock
    private PosCredentialAuditLogRepository auditLogRepository;

    @Mock
    private PosCredentialRepository credentialRepository;

    @InjectMocks
    private PosCredentialAuditAndManifestService auditAndManifestService;

    @Test
    @DisplayName("recordAudit saves log entry with exact fields when failureReason <= 255 chars")
    void testRecordAuditNormal() {
        auditAndManifestService.recordAudit("STA-1", "CASH-1", CredentialType.CARD, "************3456", "FAILED", "Invalid PIN");

        ArgumentCaptor<PosCredentialAuditLog> captor = ArgumentCaptor.forClass(PosCredentialAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        PosCredentialAuditLog saved = captor.getValue();
        assertThat(saved.getStationId()).isEqualTo("STA-1");
        assertThat(saved.getCashierId()).isEqualTo("CASH-1");
        assertThat(saved.getCredentialType()).isEqualTo(CredentialType.CARD);
        assertThat(saved.getMaskedIdentifier()).isEqualTo("************3456");
        assertThat(saved.getResult()).isEqualTo("FAILED");
        assertThat(saved.getFailureReason()).isEqualTo("Invalid PIN");
    }

    @Test
    @DisplayName("recordAudit truncates failureReason when it exceeds 255 characters")
    void testRecordAuditTruncatesOversizedFailureReason() {
        String longFailure = "A".repeat(300);
        auditAndManifestService.recordAudit("STA-1", "CASH-1", CredentialType.QR, "************3456", "FAILED", longFailure);

        ArgumentCaptor<PosCredentialAuditLog> captor = ArgumentCaptor.forClass(PosCredentialAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        PosCredentialAuditLog saved = captor.getValue();
        assertThat(saved.getFailureReason()).hasSize(255);
        assertThat(saved.getFailureReason()).isEqualTo("A".repeat(255));
    }

    @Test
    @DisplayName("recordAudit handles exception gracefully without throwing")
    void testRecordAuditSwallowsException() {
        doThrow(new RuntimeException("DB Connection timeout")).when(auditLogRepository).save(any());

        auditAndManifestService.recordAudit("STA-1", "CASH-1", CredentialType.RFID, "************3456", "SUCCESS", null);

        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("getActiveManifestCache maps active credentials to response")
    void testGetActiveManifestCache() {
        PosCredential cred = PosCredential.builder()
                .identifier("1111222233334444")
                .credentialType(CredentialType.CARD)
                .passengerId(101L)
                .fullName("John Doe")
                .cabinNumber("CABIN-101")
                .photoUrl("https://photo/101.jpg")
                .status(CredentialStatus.ACTIVE)
                .spendingAllowed(true)
                .alcoholRestricted(false)
                .isMinor(false)
                .individualSpendingLimit(BigDecimal.valueOf(500.00))
                .build();

        when(credentialRepository.findAllByStatusActive(CredentialStatus.ACTIVE)).thenReturn(List.of(cred));

        CredentialManifestCacheResponse response = auditAndManifestService.getActiveManifestCache();

        assertThat(response).isNotNull();
        assertThat(response.getTotalActiveCount()).isEqualTo(1);
        assertThat(response.getCredentials()).hasSize(1);
        assertThat(response.getCredentials().get(0).getFullName()).isEqualTo("John Doe");
        assertThat(response.getCredentials().get(0).getIdentifier()).isEqualTo("1111222233334444");
    }
}
