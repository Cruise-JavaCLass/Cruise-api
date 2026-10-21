package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.FolioSettlementService;
import com.cruise.pos.service.PosFolioService;
import com.cruise.pos.service.SettleOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FolioAccountResponse> createFolio(@Valid @RequestBody CreateFolioRequest request) {
        FolioAccountResponse response = folioService.createFolio(request);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<Page<FolioAccountResponse>> getFolios(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) FolioStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by("cabinNumber").ascending());
        Page<FolioAccountResponse> response = folioService.getFolios(search, status, pageable);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{cabinNumber}")
    public ApiResponse<FolioAccountResponse> getFolio(@PathVariable("cabinNumber") String cabinNumber) {
        FolioAccountResponse response = folioService.getFolioByCabin(cabinNumber);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{cabinNumber}/credit-limit")
    public ApiResponse<FolioAccountResponse> updateCreditLimit(
            @PathVariable("cabinNumber") String cabinNumber,
            @Valid @RequestBody UpdateCreditLimitRequest request
    ) {
        FolioAccountResponse response = folioService.updateCreditLimit(cabinNumber, request);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{cabinNumber}/status")
    public ApiResponse<FolioAccountResponse> updateStatus(
            @PathVariable("cabinNumber") String cabinNumber,
            @Valid @RequestBody UpdateFolioStatusRequest request
    ) {
        FolioAccountResponse response = folioService.updateStatus(cabinNumber, request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{cabinNumber}/ledger")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FolioLedgerItemDto> postExpense(
            @PathVariable("cabinNumber") String cabinNumber,
            @Valid @RequestBody PostExpenseRequest request
    ) {
        FolioLedgerItemDto response = folioService.postExpense(cabinNumber, request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{cabinNumber}/ledger")
    public ApiResponse<FolioStatementResponse> getFolioStatement(
            @PathVariable("cabinNumber") String cabinNumber,
            @RequestParam(name = "serviceArea", required = false) String serviceArea
    ) {
        FolioStatementResponse response = folioService.getFolioStatement(cabinNumber, serviceArea);
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
