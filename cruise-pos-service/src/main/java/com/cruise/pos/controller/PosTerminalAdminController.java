package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.service.PosTerminalAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/pos/terminals")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ADMIN')")
public class PosTerminalAdminController {

    private final PosTerminalAdminService terminalAdminService;

    @PostMapping("/provision")
    public ResponseEntity<ApiResponse<ProvisionTerminalResponse>> provisionTerminal(
            @Valid @RequestBody ProvisionTerminalRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN_OPERATOR";
        ProvisionTerminalResponse response = terminalAdminService.provisionTerminal(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Terminal provisioned successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PosTerminalDto>>> listTerminals(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PosTerminalDto> page = terminalAdminService.listTerminals(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<PosTerminalDto>> getTerminal(@PathVariable String deviceId) {
        PosTerminalDto dto = terminalAdminService.getTerminal(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/{deviceId}/rotate-key")
    public ResponseEntity<ApiResponse<ProvisionTerminalResponse>> rotateApiKey(
            @PathVariable String deviceId,
            @Valid @RequestBody(required = false) RotateTerminalKeyRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN_OPERATOR";
        ProvisionTerminalResponse response = terminalAdminService.rotateApiKey(deviceId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("API key rotated successfully", response));
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<ApiResponse<PosTerminalDto>> revokeTerminal(
            @PathVariable String deviceId,
            @Valid @RequestBody RevokeTerminalRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN_OPERATOR";
        PosTerminalDto dto = terminalAdminService.revokeTerminal(deviceId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Terminal revoked successfully", dto));
    }

    @PostMapping("/{deviceId}/renew-cert")
    public ResponseEntity<ApiResponse<CertificateRenewalResponse>> renewCertificate(
            @PathVariable String deviceId,
            @Valid @RequestBody(required = false) RenewCertificateRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN_OPERATOR";
        CertificateRenewalResponse response = terminalAdminService.renewCertificate(deviceId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("mTLS certificate renewed", response));
    }

    @PostMapping("/{deviceId}/remote-wipe")
    public ResponseEntity<ApiResponse<PosTerminalDto>> issueRemoteWipe(
            @PathVariable String deviceId,
            @Valid @RequestBody RemoteWipeTerminalRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN_OPERATOR";
        PosTerminalDto dto = terminalAdminService.issueRemoteWipe(deviceId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Emergency remote wipe command issued", dto));
    }

    @PostMapping("/{deviceId}/remote-wipe/ack")
    public ResponseEntity<ApiResponse<PosTerminalDto>> acknowledgeRemoteWipe(
            @PathVariable String deviceId,
            @RequestBody(required = false) RemoteWipeAckRequest request) {
        PosTerminalDto dto = terminalAdminService.acknowledgeRemoteWipe(deviceId, request);
        return ResponseEntity.ok(ApiResponse.ok("Remote wipe acknowledgement recorded", dto));
    }
}
