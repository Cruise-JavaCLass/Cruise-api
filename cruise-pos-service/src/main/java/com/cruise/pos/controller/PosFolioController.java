package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.FolioAccountResponse;
import com.cruise.pos.dto.SettleFolioRequest;
import com.cruise.pos.dto.SettlementPreviewResponse;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.FolioSettlementService;
import com.cruise.pos.service.PosFolioService;
import com.cruise.pos.service.SettleOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pos/folios")
@RequiredArgsConstructor
public class PosFolioController {

    private static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    private final PosFolioService folioService;
    private final FolioSettlementService settlementService;

    @GetMapping("/{cabinNumber}")
    public ApiResponse<FolioAccountResponse> getFolio(@PathVariable("cabinNumber") String cabinNumber) {
        FolioAccountResponse response = folioService.getFolioByCabin(cabinNumber);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{cabinNumber}/settlement-preview")
    public ApiResponse<SettlementPreviewResponse> previewSettlement(
            @PathVariable("cabinNumber") String cabinNumber) {
        return ApiResponse.ok(settlementService.preview(cabinNumber));
    }

    @PostMapping("/{cabinNumber}/settle")
    public ResponseEntity<ApiResponse<SettlementResponse>> settleFolio(
            @PathVariable("cabinNumber") String cabinNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SettleFolioRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SettleOutcome outcome = settlementService.settle(
                cabinNumber,
                idempotencyKey,
                request.getPaymentMethod(),
                request.getAmountTendered(),
                request.getNotes(),
                request.isForceWithHold(),
                resolveActor(authentication),
                resolveRoles(authentication));

        ResponseEntity.BodyBuilder builder = outcome.replayed()
                ? ResponseEntity.status(HttpStatus.OK)
                : ResponseEntity.status(HttpStatus.CREATED);
        builder.header(IDEMPOTENT_REPLAY_HEADER, Boolean.toString(outcome.replayed()));

        ApiResponse<SettlementResponse> body = ApiResponse.ok("Tất toán thành công", outcome.response());
        // Timestamp lấy theo thời điểm tất toán để replay trả body giống hệt byte-for-byte.
        body.setTimestamp(outcome.response().getSettledAt());
        return builder.body(body);
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        if (authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.username();
        }
        return authentication.getName();
    }

    private Set<String> resolveRoles(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}
