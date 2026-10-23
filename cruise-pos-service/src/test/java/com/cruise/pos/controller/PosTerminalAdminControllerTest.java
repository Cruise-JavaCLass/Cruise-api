package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.PosTerminalStatus;
import com.cruise.pos.service.PosTerminalAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosTerminalAdminController Unit Tests")
class PosTerminalAdminControllerTest {

    @Mock
    private PosTerminalAdminService terminalAdminService;

    @Mock
    private Principal principal;

    @InjectMocks
    private PosTerminalAdminController controller;

    private final PosTerminalDto sampleDto = new PosTerminalDto(
            1L,
            "TERM-01",
            "Handheld Terminal 1",
            "BAR-DECK-9",
            "192.168.1.100",
            "AA:BB:CC:DD:EE:FF",
            PosTerminalStatus.ACTIVE,
            "pos_sec_",
            "fp_sample",
            Instant.now().plusSeconds(86400),
            1,
            Instant.now(),
            "1.0.0",
            null,
            null,
            null,
            Instant.now()
    );

    @Test
    @DisplayName("provisionTerminal: provisions and returns 201 Created")
    void testProvisionTerminal() {
        ProvisionTerminalRequest req = new ProvisionTerminalRequest("TERM-01", "Handheld Terminal 1", "BAR-DECK-9", "192.168.1.100", "AA:BB:CC:DD:EE:FF", "1.0.0", null);
        ProvisionTerminalResponse res = new ProvisionTerminalResponse(sampleDto, "pos_sec_raw", "cert_pem", Instant.now().plusSeconds(86400), "Success");

        when(principal.getName()).thenReturn("ADMIN");
        when(terminalAdminService.provisionTerminal(eq(req), eq("ADMIN"))).thenReturn(res);

        ResponseEntity<ApiResponse<ProvisionTerminalResponse>> response = controller.provisionTerminal(req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().terminal().deviceId()).isEqualTo("TERM-01");
        verify(terminalAdminService).provisionTerminal(req, "ADMIN");
    }

    @Test
    @DisplayName("listTerminals: returns paginated terminal list")
    void testListTerminals() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<PosTerminalDto> p = new PageImpl<>(List.of(sampleDto));
        when(terminalAdminService.listTerminals(pageable)).thenReturn(p);

        ResponseEntity<ApiResponse<Page<PosTerminalDto>>> response = controller.listTerminals(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getTerminal: returns terminal by deviceId")
    void testGetTerminal() {
        when(terminalAdminService.getTerminal("TERM-01")).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<PosTerminalDto>> response = controller.getTerminal("TERM-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().deviceId()).isEqualTo("TERM-01");
    }

    @Test
    @DisplayName("rotateApiKey: rotates key and returns new secret")
    void testRotateApiKey() {
        RotateTerminalKeyRequest req = new RotateTerminalKeyRequest("Routine security rotation");
        ProvisionTerminalResponse res = new ProvisionTerminalResponse(sampleDto, "pos_sec_raw_new", "cert_pem", Instant.now().plusSeconds(86400), "Success");
        when(principal.getName()).thenReturn("ADMIN");
        when(terminalAdminService.rotateApiKey(eq("TERM-01"), eq(req), eq("ADMIN"))).thenReturn(res);

        ResponseEntity<ApiResponse<ProvisionTerminalResponse>> response = controller.rotateApiKey("TERM-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().rawApiKey()).isEqualTo("pos_sec_raw_new");
    }

    @Test
    @DisplayName("revokeTerminal: revokes terminal successfully")
    void testRevokeTerminal() {
        RevokeTerminalRequest req = new RevokeTerminalRequest("Device damaged");
        when(principal.getName()).thenReturn("ADMIN");
        when(terminalAdminService.revokeTerminal(eq("TERM-01"), eq(req), eq("ADMIN"))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<PosTerminalDto>> response = controller.revokeTerminal("TERM-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalAdminService).revokeTerminal("TERM-01", req, "ADMIN");
    }

    @Test
    @DisplayName("renewCertificate: renews mTLS cert successfully")
    void testRenewCertificate() {
        RenewCertificateRequest req = new RenewCertificateRequest(Instant.now().plusSeconds(86400), "csr-pem");
        CertificateRenewalResponse res = new CertificateRenewalResponse("TERM-01", "renewed-cert", "fp_renewed", Instant.now().plusSeconds(86400), "Success");
        when(principal.getName()).thenReturn("ADMIN");
        when(terminalAdminService.renewCertificate(eq("TERM-01"), eq(req), eq("ADMIN"))).thenReturn(res);

        ResponseEntity<ApiResponse<CertificateRenewalResponse>> response = controller.renewCertificate("TERM-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().clientCertificatePem()).isEqualTo("renewed-cert");
    }

    @Test
    @DisplayName("issueRemoteWipe: issues wipe command successfully")
    void testIssueRemoteWipe() {
        RemoteWipeTerminalRequest req = new RemoteWipeTerminalRequest("Lost device");
        when(principal.getName()).thenReturn("ADMIN");
        when(terminalAdminService.issueRemoteWipe(eq("TERM-01"), eq(req), eq("ADMIN"))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<PosTerminalDto>> response = controller.issueRemoteWipe("TERM-01", req, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalAdminService).issueRemoteWipe("TERM-01", req, "ADMIN");
    }

    @Test
    @DisplayName("acknowledgeRemoteWipe: records remote wipe confirmation")
    void testAcknowledgeRemoteWipe() {
        RemoteWipeAckRequest req = new RemoteWipeAckRequest("ACKNOWLEDGED", "Wiped successfully");
        when(terminalAdminService.acknowledgeRemoteWipe(eq("TERM-01"), eq(req))).thenReturn(sampleDto);

        ResponseEntity<ApiResponse<PosTerminalDto>> response = controller.acknowledgeRemoteWipe("TERM-01", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalAdminService).acknowledgeRemoteWipe("TERM-01", req);
    }
}
