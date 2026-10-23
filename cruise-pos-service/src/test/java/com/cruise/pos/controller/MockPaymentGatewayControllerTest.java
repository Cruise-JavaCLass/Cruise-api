package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.BankWebhookPayload;
import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.dto.WebhookProcessingResult;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.service.BankWebhookProcessingService;
import com.cruise.pos.service.MockPaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayControllerTest {

    private static final String SECRET = "test-bank-webhook-secret-key-123456";

    @Mock
    private MockPaymentGatewayService paymentGatewayService;

    @Mock
    private BankWebhookProcessingService webhookProcessingService;

    @InjectMocks
    private MockPaymentGatewayController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(controller, "enforceSignature", true);
    }

    private String calculateSignature(String paymentRef, String txId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal((paymentRef + ":" + txId).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    @DisplayName("validateConfiguration succeeds when secret is configured")
    void testValidateConfigurationSuccess() {
        controller.validateConfiguration();
    }

    @Test
    @DisplayName("validateConfiguration throws when signature enforced and secret is blank")
    void testValidateConfigurationThrowsWhenBlank() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        assertThatThrownBy(() -> controller.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook-secret must be configured");
    }

    @Test
    @DisplayName("generateVietQr returns 200 OK with VietQR details")
    void testGenerateVietQr() {
        VietQrPaymentResponse mockResponse = VietQrPaymentResponse.builder()
                .paymentReference("PAY-888")
                .cabinNumber("CABIN-101")
                .amount(BigDecimal.valueOf(2000000.00))
                .status(PaymentGatewayStatus.PENDING)
                .qrImageUrl("https://img.vietqr.io/image/970436")
                .build();

        when(paymentGatewayService.generateVietQrPayment(any(GenerateVietQrRequest.class))).thenReturn(mockResponse);

        GenerateVietQrRequest request = GenerateVietQrRequest.builder()
                .cabinNumber("CABIN-101")
                .amount(BigDecimal.valueOf(2000000.00))
                .build();

        ApiResponse<VietQrPaymentResponse> response = controller.generateVietQr(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getPaymentReference()).isEqualTo("PAY-888");
        verify(paymentGatewayService).generateVietQrPayment(request);
    }

    @Test
    @DisplayName("processBankWebhook processes webhook callback with valid signature")
    void testProcessBankWebhookValidSignature() throws Exception {
        WebhookProcessingResult mockResult = WebhookProcessingResult.builder()
                .paymentReference("PAY-888")
                .paymentStatus(PaymentGatewayStatus.PAID)
                .folioSettled(true)
                .processedAt(Instant.now())
                .build();

        when(webhookProcessingService.processBankWebhook(any(BankWebhookPayload.class))).thenReturn(mockResult);

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-888")
                .transactionId("TX-123")
                .amount(BigDecimal.valueOf(2000000.00))
                .status("SUCCESS")
                .build();

        String sig = calculateSignature("PAY-888", "TX-123");
        ApiResponse<WebhookProcessingResult> response = controller.processBankWebhook(sig, payload);

        assertThat(response).isNotNull();
        assertThat(response.getData().getFolioSettled()).isTrue();
        verify(webhookProcessingService).processBankWebhook(payload);
    }

    @Test
    @DisplayName("processBankWebhook rejects request missing required signature header")
    void testProcessBankWebhookMissingSignature() {
        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-888")
                .transactionId("TX-123")
                .amount(BigDecimal.valueOf(2000000.00))
                .status("SUCCESS")
                .build();

        assertThatThrownBy(() -> controller.processBankWebhook(null, payload))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Missing required X-Bank-Signature");
                });
    }

    @Test
    @DisplayName("processBankWebhook rejects request with invalid HMAC signature")
    void testProcessBankWebhookInvalidSignature() {
        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-888")
                .transactionId("TX-123")
                .amount(BigDecimal.valueOf(2000000.00))
                .status("SUCCESS")
                .build();

        assertThatThrownBy(() -> controller.processBankWebhook("invalid-signature-hex", payload))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid bank webhook HMAC signature");
                });
    }

    @Test
    @DisplayName("processBankWebhook allows processing without signature when enforceSignature is false")
    void testProcessBankWebhookEnforceSignatureDisabled() {
        ReflectionTestUtils.setField(controller, "enforceSignature", false);

        WebhookProcessingResult mockResult = WebhookProcessingResult.builder()
                .paymentReference("PAY-888")
                .paymentStatus(PaymentGatewayStatus.PAID)
                .folioSettled(true)
                .build();
        when(webhookProcessingService.processBankWebhook(any(BankWebhookPayload.class))).thenReturn(mockResult);

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-888")
                .transactionId("TX-123")
                .amount(BigDecimal.valueOf(2000000.00))
                .status("SUCCESS")
                .build();

        ApiResponse<WebhookProcessingResult> response = controller.processBankWebhook(null, payload);
        assertThat(response).isNotNull();
        verify(webhookProcessingService).processBankWebhook(payload);
    }

    @Test
    @DisplayName("getOrderStatus returns payment order status from gateway service")
    void testGetOrderStatus() {
        when(paymentGatewayService.getOrderStatus("PAY-REF-123")).thenReturn(PaymentGatewayStatus.PAID);

        ApiResponse<PaymentGatewayStatus> response = controller.getOrderStatus("PAY-REF-123");

        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(PaymentGatewayStatus.PAID);
        verify(paymentGatewayService).getOrderStatus("PAY-REF-123");
    }
}
