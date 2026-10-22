package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.service.CabinBillingSplitService;
import com.cruise.pos.service.CabinCreditLimitEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos/cabin-billing")
@RequiredArgsConstructor
@Slf4j
public class CabinBillingController {

    private final CabinBillingSplitService billingSplitService;
    private final CabinCreditLimitEngine creditLimitEngine;

    @GetMapping("/{cabinNumber}/members")
    public ApiResponse<List<CabinMemberDto>> getCabinMembers(@PathVariable("cabinNumber") String cabinNumber) {
        List<CabinMemberDto> members = billingSplitService.getCabinMembers(cabinNumber);
        return ApiResponse.ok(members);
    }

    @PostMapping("/split")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CabinSplitBillingResponse> splitBilling(@Valid @RequestBody CabinSplitBillingRequest request) {
        CabinSplitBillingResponse response = billingSplitService.processSplitBilling(request);
        return ApiResponse.ok("Chia hóa đơn phòng thành công", response);
    }

    @PostMapping("/{cabinNumber}/check-limit")
    public ApiResponse<CabinCreditLimitCheckResponse> checkCreditLimit(
            @PathVariable("cabinNumber") String cabinNumber,
            @Valid @RequestBody CabinCreditLimitCheckRequest request
    ) {
        CabinCreditLimitCheckResponse response = creditLimitEngine.checkCreditLimit(cabinNumber, request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/signatures")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignatureResponse> archiveSignature(@Valid @RequestBody ArchiveSignatureRequest request) {
        SignatureResponse response = billingSplitService.archiveSignature(request);
        return ApiResponse.ok("Lưu trữ chữ ký số điện tử thành công", response);
    }

    @GetMapping("/signatures/{transactionUuid}")
    public ApiResponse<SignatureResponse> getSignature(@PathVariable("transactionUuid") String transactionUuid) {
        SignatureResponse response = billingSplitService.getSignatureByTransactionUuid(transactionUuid);
        return ApiResponse.ok(response);
    }
}
