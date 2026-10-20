package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.service.PosFolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
        Pageable pageable = PageRequest.of(page, size, Sort.by("cabinNumber").ascending());
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
}
