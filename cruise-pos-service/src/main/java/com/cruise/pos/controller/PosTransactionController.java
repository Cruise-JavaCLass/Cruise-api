package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.service.PosTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/pos/transactions")
@RequiredArgsConstructor
public class PosTransactionController {

    private final PosTransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> createTransaction(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionResponse response = transactionService.processChargeTransaction(idempotencyKey, request);
        return ApiResponse.ok("Giao dịch thanh toán POS thành công", response);
    }

    @PostMapping("/{id}/void")
    public ApiResponse<TransactionResponse> voidTransaction(
            @PathVariable("id") Long id,
            @Valid @RequestBody VoidTransactionRequest request
    ) {
        TransactionResponse response = transactionService.voidTransaction(id, request);
        return ApiResponse.ok("Hủy giao dịch (Void) trong ca thành công", response);
    }

    @PostMapping("/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> refundTransaction(
            @PathVariable("id") Long id,
            @Valid @RequestBody RefundTransactionRequest request
    ) {
        TransactionResponse response = transactionService.refundTransaction(id, request);
        return ApiResponse.ok("Hoàn tiền giao dịch thành công", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> getTransactionById(@PathVariable("id") Long id) {
        TransactionResponse response = transactionService.getTransactionById(id);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<Page<TransactionResponse>> getTransactions(
            @RequestParam(value = "stationId", required = false) String stationId,
            @RequestParam(value = "cashierId", required = false) String cashierId,
            @RequestParam(value = "status", required = false) TransactionStatus status,
            @RequestParam(value = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(value = "cabinNumber", required = false) String cabinNumber,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1) {
            throw new com.cruise.pos.exception.BusinessRuleException("page phải >= 0 và size phải >= 1");
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionResponse> response = transactionService.getTransactionsWithFilters(
                stationId, cashierId, status, paymentMethod, cabinNumber, startDate, endDate, pageable
        );
        return ApiResponse.ok(response);
    }

    @PostMapping("/batch-sync")
    public ApiResponse<BatchSyncResponse> syncBatch(@Valid @RequestBody BatchSyncRequest request) {
        BatchSyncResponse response = transactionService.processBatchSync(request);
        return ApiResponse.ok("Đồng bộ giao dịch ngoại tuyến thành công", response);
    }
}
