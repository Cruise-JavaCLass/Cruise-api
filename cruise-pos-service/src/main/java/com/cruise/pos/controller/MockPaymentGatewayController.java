package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.service.MockPaymentGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

    @Value("${pos.payment.bank.webhook-secret:vietqr-bank-secret-key-32bytes-len}")
    private String webhookSecret;

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
        if (signatureHeader == null || signatureHeader.isBlank()) {
            // If signature is omitted in development / mock mode, allow if signatureHeader is not strictly enforced or check token
            return;
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
