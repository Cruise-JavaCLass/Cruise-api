package com.cruise.pos.controller;

import com.cruise.pos.dto.ManagerOverrideRequest;
import com.cruise.pos.dto.ManagerOverrideResponse;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.ManagerOverrideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit override endpoint for manager-approved POS actions (KAN-606).
 *
 * <p>Any POS_STAFF, OPERATIONS_MANAGER or SYSTEM_ADMIN may invoke this endpoint.
 * The manager identity and PIN embedded in the request body are validated server-side
 * by {@link com.cruise.pos.service.PosManagerAuthService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/pos/auth")
@RequiredArgsConstructor
public class ManagerOverrideController {

    private final ManagerOverrideService managerOverrideService;

    /**
     * Dispatches a manager override and creates an audit trail.
     *
     * <p>Returns {@code 201 Created} when the override is approved, or {@code 200 OK}
     * with {@code approved=false} when the manager credentials are invalid.
     *
     * @param req request body with manager credentials and justification
     * @return override outcome with audit log ID (when approved)
     */
    @PostMapping("/override")
    @PreAuthorize("hasAnyRole('POS_STAFF', 'OPERATIONS_MANAGER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ManagerOverrideResponse> dispatchOverride(
            @Valid @RequestBody ManagerOverrideRequest req) {

        String performedBy = resolvePerformedBy();

        ManagerOverrideResponse response = managerOverrideService.dispatchOverride(req, performedBy);

        HttpStatus status = response.approved() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Resolves the caller's username from the security context.
     * Falls back to {@code "ANONYMOUS"} when security is disabled (dev/demo profile).
     */
    private String resolvePerformedBy() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "ANONYMOUS";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return authPrincipal.username();
        }
        return auth.getName();
    }
}
