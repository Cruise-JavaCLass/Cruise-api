package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.service.MockPaymentGatewayService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/pos/payments")
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGatewayController {

    private final MockPaymentGatewayService paymentGatewayService;
    private final com.cruise.pos.service.BankWebhookProcessingService webhookProcessingService;

    @Value("${pos.payment.bank.webhook-secret}")
    private String webhookSecret;

    @Value("${pos.payment.bank.enforce-signature:true}")
    private boolean enforceSignature;

    @PostConstruct
    public void validateConfiguration() {
        if (enforceSignature && (webhookSecret == null || webhookSecret.isBlank())) {
            throw new IllegalStateException("pos.payment.bank.webhook-secret must be configured and not blank when signature enforcement is active");
        }
    }

    @PostMapping("/vietqr/generate")
    public ApiResponse<VietQrPaymentResponse> generateVietQr(@Valid @RequestBody GenerateVietQrRequest request) {
        log.info("Generating VietQR payment for cabin: {}, amount: {}", request.getCabinNumber(), request.getAmount());
        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/webhook/bank-transfer")
    public ApiResponse<com.cruise.pos.dto.WebhookProcessingResult> processBankWebhook(
            @RequestHeader(value = "X-Bank-Signature", required = false) String signatureHeader,
            @Valid @RequestBody com.cruise.pos.dto.BankWebhookPayload payload) {
        log.info("Received bank webhook for ref: {}, tx: {}, amount: {}", payload.getPaymentReference(), payload.getTransactionId(), payload.getAmount());

        verifyWebhookSignature(payload.getPaymentReference(), payload.getTransactionId(), signatureHeader);

        com.cruise.pos.dto.WebhookProcessingResult result = webhookProcessingService.processBankWebhook(payload);
        return ApiResponse.ok(result);
    }

    @GetMapping("/orders/{reference}/status")
    public ApiResponse<com.cruise.pos.enums.PaymentGatewayStatus> getOrderStatus(@PathVariable("reference") String reference) {
        log.info("Polling payment order status for ref: {}", reference);
        return ApiResponse.ok(paymentGatewayService.getOrderStatus(reference));
    }

    private void verifyWebhookSignature(String paymentRef, String txId, String signatureHeader) {
        if (!enforceSignature) {
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing required X-Bank-Signature header");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payloadData = paymentRef + ":" + txId;
            byte[] hmacBytes = mac.doFinal(payloadData.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hmacBytes);

            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bank webhook HMAC signature");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying bank webhook signature: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature verification failed", e);
        }
    }
}
