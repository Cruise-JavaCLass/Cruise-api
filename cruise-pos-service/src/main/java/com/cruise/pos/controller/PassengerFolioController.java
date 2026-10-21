package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateFolioDisputeRequest;
import com.cruise.pos.dto.DisputeResponse;
import com.cruise.pos.dto.FolioTransactionDto;
import com.cruise.pos.dto.PassengerFolioSummaryResponse;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.PassengerFolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/passengers")
@RequiredArgsConstructor
public class PassengerFolioController {

    private final PassengerFolioService passengerFolioService;

    @GetMapping("/{id}/folio")
    public ApiResponse<PassengerFolioSummaryResponse> getFolioSummary(@PathVariable("id") Long passengerId) {
        verifyPassengerOwnership(passengerId);
        PassengerFolioSummaryResponse response = passengerFolioService.getFolioSummary(passengerId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}/folio/transactions")
    public ApiResponse<Page<FolioTransactionDto>> getTransactions(
            @PathVariable("id") Long passengerId,
            @RequestParam(required = false, name = "category") String category,
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "size") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("Page phải >= 0 và size từ 1 đến 100");
        }
        verifyPassengerOwnership(passengerId);
        Page<FolioTransactionDto> response = passengerFolioService.getTransactions(
                passengerId,
                category,
                PageRequest.of(page, size)
        );
        return ApiResponse.ok(response);
    }

    @PostMapping("/{id}/folio/transactions/{txId}/dispute")
    public ResponseEntity<ApiResponse<DisputeResponse>> disputeTransaction(
            @PathVariable("id") Long passengerId,
            @PathVariable("txId") Long txId,
            @Valid @RequestBody CreateFolioDisputeRequest request) {
        verifyPassengerOwnership(passengerId);
        DisputeResponse response = passengerFolioService.disputeTransaction(passengerId, txId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi yêu cầu khiếu nại thành công", response));
    }

    private void verifyPassengerOwnership(Long passengerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthPrincipal principal) {
            String role = principal.role();
            if (role != null && (role.contains("STAFF") || role.contains("ADMIN") || role.contains("MANAGER"))) {
                return;
            }
            if (principal.username() != null) {
                try {
                    Long authPassengerId = Long.parseLong(principal.username());
                    if (!authPassengerId.equals(passengerId)) {
                        throw new AccessDeniedException("Hành khách không có quyền truy cập dữ liệu của hành khách khác");
                    }
                } catch (NumberFormatException ignored) {
                    // Non-numeric username, allow pass-through if not strictly mapping to passenger ID
                }
            }
        }
    }
}
