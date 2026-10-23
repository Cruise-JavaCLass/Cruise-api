package com.cruise.pos.service;

import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        verify(paymentOrderRepository).saveAndFlush(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("generateVietQrPayment applies default description when description is blank or null")
    void testGenerateVietQrDefaultDescription() {
        GenerateVietQrRequest reqWithoutDesc = GenerateVietQrRequest.builder()
                .cabinNumber("CABIN-802")
                .amount(BigDecimal.valueOf(500000.00))
                .build();

        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(reqWithoutDesc);

        assertThat(response).isNotNull();
        assertThat(response.getDescription()).isEqualTo("THANH TOAN FOLIO CABIN-802");
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
        verify(paymentOrderRepository, never()).saveAndFlush(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("generateVietQrPayment throws BusinessRuleException when idempotency replay has different cabin")
    void testGenerateVietQrIdempotentCabinMismatch() {
        PaymentOrder existing = PaymentOrder.builder()
                .paymentReference("PAY-EXISTING-123")
                .cabinNumber("CABIN-OTHER")
                .amount(BigDecimal.valueOf(1500000.00))
                .status(PaymentGatewayStatus.PENDING)
                .idempotencyKey("IDEM-KEY-999")
                .build();

        when(paymentOrderRepository.findByIdempotencyKey("IDEM-KEY-999")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentGatewayService.generateVietQrPayment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cabinNumber mismatch");
    }

    @Test
    @DisplayName("generateVietQrPayment throws BusinessRuleException when idempotency replay has different amount")
    void testGenerateVietQrIdempotentAmountMismatch() {
        PaymentOrder existing = PaymentOrder.builder()
                .paymentReference("PAY-EXISTING-123")
                .cabinNumber("CABIN-801")
                .amount(BigDecimal.valueOf(999999.00))
                .status(PaymentGatewayStatus.PENDING)
                .idempotencyKey("IDEM-KEY-999")
                .build();

        when(paymentOrderRepository.findByIdempotencyKey("IDEM-KEY-999")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentGatewayService.generateVietQrPayment(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    @DisplayName("generateVietQrPayment recovers existing order on concurrent DataIntegrityViolationException")
    void testGenerateVietQrConcurrentConstraintRecovery() {
        when(paymentOrderRepository.findByIdempotencyKey("IDEM-KEY-999"))
                .thenReturn(Optional.empty()) // First check missed
                .thenReturn(Optional.of(PaymentOrder.builder()
                        .paymentReference("PAY-CONCURRENT-1")
                        .cabinNumber("CABIN-801")
                        .amount(BigDecimal.valueOf(1500000.00))
                        .currency("VND")
                        .status(PaymentGatewayStatus.PENDING)
                        .idempotencyKey("IDEM-KEY-999")
                        .build()));

        doThrow(new DataIntegrityViolationException("duplicate key")).when(paymentOrderRepository).saveAndFlush(any());

        VietQrPaymentResponse response = paymentGatewayService.generateVietQrPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentReference()).isEqualTo("PAY-CONCURRENT-1");
    }

    @Test
    @DisplayName("generateVietQrPayment rethrows DataIntegrityViolationException when idempotency key is null")
    void testGenerateVietQrRethrowsWhenNoIdempotencyKey() {
        GenerateVietQrRequest reqNoKey = GenerateVietQrRequest.builder()
                .cabinNumber("CABIN-801")
                .amount(BigDecimal.valueOf(100000.00))
                .build();

        doThrow(new DataIntegrityViolationException("DB error")).when(paymentOrderRepository).saveAndFlush(any());

        assertThatThrownBy(() -> paymentGatewayService.generateVietQrPayment(reqNoKey))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("getOrderStatus returns order status or FAILED if not found")
    void testGetOrderStatus() {
        PaymentOrder existing = PaymentOrder.builder()
                .paymentReference("PAY-100")
                .status(PaymentGatewayStatus.PAID)
                .build();

        when(paymentOrderRepository.findByPaymentReference("PAY-100")).thenReturn(Optional.of(existing));
        when(paymentOrderRepository.findByPaymentReference("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(paymentGatewayService.getOrderStatus("PAY-100")).isEqualTo(PaymentGatewayStatus.PAID);
        assertThat(paymentGatewayService.getOrderStatus("UNKNOWN")).isEqualTo(PaymentGatewayStatus.FAILED);
    }
}
