package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateFolioDisputeRequest;
import com.cruise.pos.dto.DisputeResponse;
import com.cruise.pos.dto.FolioTransactionDto;
import com.cruise.pos.dto.PassengerFolioSummaryResponse;
import com.cruise.pos.service.PassengerFolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/passengers")
@RequiredArgsConstructor
public class PassengerFolioController {

    private final PassengerFolioService passengerFolioService;

    @GetMapping("/{id}/folio")
    public ApiResponse<PassengerFolioSummaryResponse> getFolioSummary(@PathVariable("id") Long passengerId) {
        PassengerFolioSummaryResponse response = passengerFolioService.getFolioSummary(passengerId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}/folio/transactions")
    public ApiResponse<Page<FolioTransactionDto>> getTransactions(
            @PathVariable("id") Long passengerId,
            @RequestParam(required = false, name = "category") String category,
            @RequestParam(defaultValue = "0", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "size") int size) {
        Page<FolioTransactionDto> response = passengerFolioService.getTransactions(
                passengerId,
                category,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ApiResponse.ok(response);
    }

    @PostMapping("/{id}/folio/transactions/{txId}/dispute")
    public ResponseEntity<ApiResponse<DisputeResponse>> disputeTransaction(
            @PathVariable("id") Long passengerId,
            @PathVariable("txId") Long txId,
            @Valid @RequestBody CreateFolioDisputeRequest request) {
        DisputeResponse response = passengerFolioService.disputeTransaction(passengerId, txId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi yêu cầu khiếu nại thành công", response));
    }
}
