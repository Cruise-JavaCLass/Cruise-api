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
    private final com.cruise.pos.service.BankWebhookProcessingService webhookProcessingService;

    @PostMapping("/vietqr/generate")
    public ApiResponse<VietQrPaymentResponse> generateVietQr(@Valid @RequestBody GenerateVietQrRequest request) {
        log.info("Generating VietQR payment for cabin: {}, amount: {}", request.getCabinNumber(), request.getAmount());
        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/webhook/bank-transfer")
    public ApiResponse<com.cruise.pos.dto.WebhookProcessingResult> processBankWebhook(@Valid @RequestBody com.cruise.pos.dto.BankWebhookPayload payload) {
        log.info("Received bank webhook for ref: {}, tx: {}, amount: {}", payload.getPaymentReference(), payload.getTransactionId(), payload.getAmount());
        com.cruise.pos.dto.WebhookProcessingResult result = webhookProcessingService.processBankWebhook(payload);
        return ApiResponse.ok(result);
    }

    @GetMapping("/orders/{reference}/status")
    public ApiResponse<com.cruise.pos.enums.PaymentGatewayStatus> getOrderStatus(@PathVariable("reference") String reference) {
        log.info("Polling payment order status for ref: {}", reference);
        return ApiResponse.ok(paymentGatewayService.getOrderStatus(reference));
    }
}
