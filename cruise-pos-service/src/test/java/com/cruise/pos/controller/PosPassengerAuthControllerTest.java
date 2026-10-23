package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CredentialManifestCacheResponse;
import com.cruise.pos.dto.IdentifyPassengerRequest;
import com.cruise.pos.dto.PassengerIdentificationResponse;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.CredentialSecurityException;
import com.cruise.pos.service.PosCredentialAuditAndManifestService;
import com.cruise.pos.service.PosPassengerAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosPassengerAuthControllerTest {

    @Mock
    private PosPassengerAuthService posPassengerAuthService;

    @Mock
    private PosCredentialAuditAndManifestService auditAndManifestService;

    @InjectMocks
    private PosPassengerAuthController controller;

    @Test
    @DisplayName("identify returns 200 OK with passenger details")
    void testIdentifySuccess() {
        PassengerIdentificationResponse mockResponse = PassengerIdentificationResponse.builder()
                .passengerId(12L)
                .fullName("Alice Wonder")
                .cabinNumber("CABIN-502")
                .credentialType(CredentialType.RFID)
                .maskedIdentifier("****9876")
                .credentialStatus(CredentialStatus.ACTIVE)
                .folioStatus(FolioStatus.ACTIVE)
                .creditLimit(BigDecimal.valueOf(2000.00))
                .availableLimit(BigDecimal.valueOf(1850.00))
                .spendingAllowed(true)
                .alcoholRestricted(false)
                .isMinor(false)
                .build();

        when(posPassengerAuthService.identify(any(IdentifyPassengerRequest.class))).thenReturn(mockResponse);

        IdentifyPassengerRequest request = IdentifyPassengerRequest.builder()
                .rawCredential("RFID:E00401009876")
                .stationId("BAR-DECK-9")
                .cashierId("STAFF-01")
                .build();

        ApiResponse<PassengerIdentificationResponse> response = controller.identify(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getFullName()).isEqualTo("Alice Wonder");
        assertThat(response.getData().getCabinNumber()).isEqualTo("CABIN-502");
        assertThat(response.getData().getSpendingAllowed()).isTrue();
        verify(posPassengerAuthService).identify(request);
    }

    @Test
    @DisplayName("identify propagates CredentialSecurityException for lost/blocked cards")
    void testIdentifyForbidden() {
        when(posPassengerAuthService.identify(any(IdentifyPassengerRequest.class)))
                .thenThrow(new CredentialSecurityException("Credential has been REPORTED_LOST"));

        IdentifyPassengerRequest request = IdentifyPassengerRequest.builder()
                .rawCredential("CARD:1111222233334444")
                .stationId("BAR-DECK-9")
                .cashierId("STAFF-01")
                .build();

        assertThatThrownBy(() -> controller.identify(request))
                .isInstanceOf(CredentialSecurityException.class)
                .hasMessageContaining("REPORTED_LOST");
    }

    @Test
    @DisplayName("getManifestCache returns 200 OK with manifest data")
    void testManifestCache() {
        CredentialManifestCacheResponse cacheResponse = CredentialManifestCacheResponse.builder()
                .generatedAt(Instant.now())
                .totalActiveCount(1)
                .credentials(Collections.emptyList())
                .build();

        when(auditAndManifestService.getActiveManifestCache()).thenReturn(cacheResponse);

        ApiResponse<CredentialManifestCacheResponse> response = controller.getManifestCache();

        assertThat(response).isNotNull();
        assertThat(response.getData().getTotalActiveCount()).isEqualTo(1);
        verify(auditAndManifestService).getActiveManifestCache();
    }
}
