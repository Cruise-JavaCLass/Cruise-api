package com.cruise.pos.service;

import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @InjectMocks
    private MockPaymentGatewayService paymentGatewayService;

    private GenerateVietQrRequest request;

    @BeforeEach
    void setUp() {
        request = GenerateVietQrRequest.builder()
                .cabinNumber("CABIN-801")
                .passengerId(501L)
                .amount(BigDecimal.valueOf(1500000.00))
                .description("THANH TOAN FOLIO CABIN-801")
                .idempotencyKey("IDEM-KEY-999")
                .build();
    }

    @Test
    @DisplayName("generateVietQrPayment creates new order and returns valid VietQR payload")
    void testGenerateVietQrSuccess() {
        when(paymentOrderRepository.findByIdempotencyKey("IDEM-KEY-999")).thenReturn(Optional.empty());

        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getCabinNumber()).isEqualTo("CABIN-801");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500000.00));
        assertThat(response.getBankCode()).isEqualTo("970436");
        assertThat(response.getQrImageUrl()).contains("https://img.vietqr.io/image/970436");
        assertThat(response.getStatus()).isEqualTo(PaymentGatewayStatus.PENDING);

        verify(paymentOrderRepository).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("generateVietQrPayment replays existing order when idempotency key matches")
    void testGenerateVietQrIdempotentReplay() {
        PaymentOrder existing = PaymentOrder.builder()
                .paymentReference("PAY-EXISTING-123")
                .cabinNumber("CABIN-801")
                .amount(BigDecimal.valueOf(1500000.00))
                .currency("VND")
                .status(PaymentGatewayStatus.PENDING)
                .idempotencyKey("IDEM-KEY-999")
                .build();

        when(paymentOrderRepository.findByIdempotencyKey("IDEM-KEY-999")).thenReturn(Optional.of(existing));

        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);

        assertThat(response.getPaymentReference()).isEqualTo("PAY-EXISTING-123");
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }
}
