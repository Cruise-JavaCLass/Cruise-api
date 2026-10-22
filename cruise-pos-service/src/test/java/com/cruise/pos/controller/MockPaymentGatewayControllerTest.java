package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.BankWebhookPayload;
import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.dto.WebhookProcessingResult;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.service.BankWebhookProcessingService;
import com.cruise.pos.service.MockPaymentGatewayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayControllerTest {

    @Mock
    private MockPaymentGatewayService paymentGatewayService;

    @Mock
    private BankWebhookProcessingService webhookProcessingService;

    @InjectMocks
    private MockPaymentGatewayController controller;

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
    @DisplayName("processBankWebhook processes webhook callback")
    void testProcessBankWebhook() {
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

        ApiResponse<WebhookProcessingResult> response = controller.processBankWebhook(payload);

        assertThat(response).isNotNull();
        assertThat(response.getData().getFolioSettled()).isTrue();
        verify(webhookProcessingService).processBankWebhook(payload);
    }
}
