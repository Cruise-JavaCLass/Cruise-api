package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.TerminalLockActionRequest;
import com.cruise.pos.dto.TerminalLockCommand;
import com.cruise.pos.dto.TerminalLockStatusResponse;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.TerminalLockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos/terminals")
@RequiredArgsConstructor
@Slf4j
public class TerminalLockController {

    private final TerminalLockService terminalLockService;

    @PostMapping("/{terminalId}/lock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<TerminalLockStatusResponse> lockTerminal(
            @PathVariable String terminalId,
            @Valid @RequestBody(required = false) TerminalLockActionRequest req) {
        String issuedBy = resolveCurrentUser();
        String justification = req != null ? req.justification() : null;
        TerminalLockCommand effectiveCmd = new TerminalLockCommand(
                terminalId, "LOCK", issuedBy, justification);
        TerminalLockStatusResponse response = terminalLockService.lockTerminal(effectiveCmd);
        return ApiResponse.ok("Terminal locked successfully", response);
    }

    @PostMapping("/{terminalId}/unlock")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<TerminalLockStatusResponse> unlockTerminal(
            @PathVariable String terminalId,
            @Valid @RequestBody(required = false) TerminalLockActionRequest req) {
        String issuedBy = resolveCurrentUser();
        String justification = req != null ? req.justification() : null;
        TerminalLockCommand effectiveCmd = new TerminalLockCommand(
                terminalId, "UNLOCK", issuedBy, justification);
        TerminalLockStatusResponse response = terminalLockService.lockTerminal(effectiveCmd);
        return ApiResponse.ok("Terminal unlocked successfully", response);
    }

    @PostMapping("/{terminalId}/force-unlock")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<TerminalLockStatusResponse> forceUnlockTerminal(
            @PathVariable String terminalId,
            @Valid @RequestBody(required = false) TerminalLockActionRequest req) {
        String issuedBy = resolveCurrentUser();
        String justification = req != null ? req.justification() : null;
        TerminalLockCommand effectiveCmd = new TerminalLockCommand(
                terminalId, "FORCE_UNLOCK", issuedBy, justification);
        TerminalLockStatusResponse response = terminalLockService.lockTerminal(effectiveCmd);
        return ApiResponse.ok("Force unlock issued", response);
    }

    @GetMapping("/{terminalId}/status")
    @PreAuthorize("hasAnyRole('POS_STAFF', 'OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<TerminalLockStatusResponse> getTerminalStatus(
            @PathVariable String terminalId) {
        TerminalLockStatusResponse response = terminalLockService.getTerminalStatus(terminalId);
        return ApiResponse.ok("Terminal status retrieved", response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ApiResponse<List<TerminalLockStatusResponse>> getAllTerminalStatuses() {
        List<TerminalLockStatusResponse> statuses = terminalLockService.getAllTerminalStatuses();
        return ApiResponse.ok("All terminal statuses retrieved", statuses);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return "SYSTEM";
        }
        return principal.username();
    }
}
