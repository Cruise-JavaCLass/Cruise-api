package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.service.MockPaymentGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos/payments")
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGatewayController {

    private final MockPaymentGatewayService paymentGatewayService;

    @PostMapping("/vietqr/generate")
    public ApiResponse<VietQrPaymentResponse> generateVietQr(@Valid @RequestBody GenerateVietQrRequest request) {
        log.info("Generating VietQR payment for cabin: {}, amount: {}", request.getCabinNumber(), request.getAmount());
        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);
        return ApiResponse.ok(response);
    }
}
