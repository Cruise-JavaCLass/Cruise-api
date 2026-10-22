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
import com.cruise.pos.exception.CredentialSecurityException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PosCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosPassengerAuthServiceTest {

    @Mock
    private PosCredentialRepository credentialRepository;

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private GuestCardVerificationEngine verificationEngine;

    @Mock
    private PosCredentialAuditAndManifestService auditAndManifestService;

    @InjectMocks
    private PosPassengerAuthService authService;

    private PosCredential activeCredential;
    private CabinFolioAccount activeFolio;

    @BeforeEach
    void setUp() {
        activeCredential = PosCredential.builder()
                .credentialType(CredentialType.CARD)
                .identifier("1234567890123456")
                .passengerId(101L)
                .fullName("John Doe")
                .cabinNumber("CABIN-101")
                .photoUrl("https://example.com/photo.jpg")
                .status(CredentialStatus.ACTIVE)
                .spendingAllowed(true)
                .alcoholRestricted(false)
                .isMinor(false)
                .build();

        activeFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-101")
                .primaryGuestName("John Doe")
                .creditLimit(BigDecimal.valueOf(1500.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("parseRawCredential parses QR, CARD, RFID prefixes correctly")
    void testParseRawCredentialPrefixes() {
        ParsedCredential qr = authService.parseRawCredential("QR:TOKEN-XYZ-123");
        assertThat(qr.getType()).isEqualTo(CredentialType.QR);
        assertThat(qr.getIdentifier()).isEqualTo("TOKEN-XYZ-123");

        ParsedCredential card = authService.parseRawCredential("CARD:9876543210987654");
        assertThat(card.getType()).isEqualTo(CredentialType.CARD);
        assertThat(card.getIdentifier()).isEqualTo("9876543210987654");

        ParsedCredential rfid = authService.parseRawCredential("RFID:E0040100");
        assertThat(rfid.getType()).isEqualTo(CredentialType.RFID);
        assertThat(rfid.getIdentifier()).isEqualTo("E0040100");
    }

    @Test
    @DisplayName("parseRawCredential auto-detects 16-digit card and hex RFID")
    void testParseRawCredentialAutoDetect() {
        ParsedCredential card = authService.parseRawCredential("1234567890123456");
        assertThat(card.getType()).isEqualTo(CredentialType.CARD);
        assertThat(card.getIdentifier()).isEqualTo("1234567890123456");

        ParsedCredential rfid = authService.parseRawCredential("A1B2C3D4");
        assertThat(rfid.getType()).isEqualTo(CredentialType.RFID);
        assertThat(rfid.getIdentifier()).isEqualTo("A1B2C3D4");

        ParsedCredential qr = authService.parseRawCredential("UNKNOWN_STRING_ABC");
        assertThat(qr.getType()).isEqualTo(CredentialType.QR);
        assertThat(qr.getIdentifier()).isEqualTo("UNKNOWN_STRING_ABC");
    }

    @Test
    @DisplayName("parseRawCredential throws exception for empty input")
    void testParseRawCredentialEmpty() {
        assertThatThrownBy(() -> authService.parseRawCredential("   "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("identify successfully resolves active card with folio")
    void testIdentifySuccess() {
        when(credentialRepository.findByIdentifier("1234567890123456")).thenReturn(Optional.of(activeCredential));
        when(folioRepository.findByCabinNumber("CABIN-101")).thenReturn(Optional.of(activeFolio));
        when(verificationEngine.evaluateSpendingAllowance(activeCredential, activeFolio)).thenReturn(true);
        when(verificationEngine.isAlcoholRestricted(activeCredential)).thenReturn(false);

        IdentifyPassengerRequest request = IdentifyPassengerRequest.builder()
                .rawCredential("CARD:1234567890123456")
                .stationId("POS-01")
                .cashierId("EMP-99")
                .build();

        PassengerIdentificationResponse response = authService.identify(request);

        assertThat(response).isNotNull();
        assertThat(response.getPassengerId()).isEqualTo(101L);
        assertThat(response.getFullName()).isEqualTo("John Doe");
        assertThat(response.getCabinNumber()).isEqualTo("CABIN-101");
        assertThat(response.getMaskedIdentifier()).isEqualTo("************3456");
        assertThat(response.getSpendingAllowed()).isTrue();
        assertThat(response.getAlcoholRestricted()).isFalse();

        verify(auditAndManifestService).recordAudit(
                eq("POS-01"), eq("EMP-99"), eq(CredentialType.CARD), eq("************3456"), eq("SUCCESS"), isNull());
    }

    @Test
    @DisplayName("identify throws ResourceNotFoundException when identifier not found")
    void testIdentifyNotFound() {
        when(credentialRepository.findByIdentifier("NONEXISTENT")).thenReturn(Optional.empty());

        IdentifyPassengerRequest request = IdentifyPassengerRequest.builder()
                .rawCredential("QR:NONEXISTENT")
                .stationId("POS-02")
                .cashierId("EMP-01")
                .build();

        assertThatThrownBy(() -> authService.identify(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(auditAndManifestService).recordAudit(
                eq("POS-02"), eq("EMP-01"), eq(CredentialType.QR), any(), eq("FAILED"), any());
    }

    @Test
    @DisplayName("identify propagates CredentialSecurityException when card is lost or blocked")
    void testIdentifySecurityException() {
        when(credentialRepository.findByIdentifier("1234567890123456")).thenReturn(Optional.of(activeCredential));
        doThrow(new CredentialSecurityException("Credential has been REPORTED_LOST"))
                .when(verificationEngine).validateCredentialSecurity(eq(activeCredential), any());

        IdentifyPassengerRequest request = IdentifyPassengerRequest.builder()
                .rawCredential("CARD:1234567890123456")
                .stationId("POS-01")
                .cashierId("EMP-99")
                .build();

        assertThatThrownBy(() -> authService.identify(request))
                .isInstanceOf(CredentialSecurityException.class);

        verify(auditAndManifestService).recordAudit(
                eq("POS-01"), eq("EMP-99"), eq(CredentialType.CARD), eq("************3456"), eq("FAILED"), any());
    }
}
