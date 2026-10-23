package com.cruise.pos.service;

import com.cruise.pos.dto.BankWebhookPayload;
import com.cruise.pos.dto.WebhookProcessingResult;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankWebhookProcessingServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private FolioSettlementService settlementService;

    @InjectMocks
    private BankWebhookProcessingService webhookProcessingService;

    private PaymentOrder pendingOrder;
    private CabinFolioAccount activeFolio;

    @BeforeEach
    void setUp() {
        pendingOrder = PaymentOrder.builder()
                .paymentReference("PAY-100")
                .cabinNumber("CABIN-801")
                .amount(BigDecimal.valueOf(1500000.00))
                .status(PaymentGatewayStatus.PENDING)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();

        activeFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-801")
                .currentBalance(BigDecimal.valueOf(1500000.00))
                .status(FolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("processBankWebhook processes SUCCESS payment and triggers settlement")
    void testProcessWebhookSuccess() {
        when(paymentOrderRepository.findByPaymentReferenceForUpdate("PAY-100")).thenReturn(Optional.of(pendingOrder));
        when(folioRepository.findByCabinNumber("CABIN-801")).thenReturn(Optional.of(activeFolio));

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-100")
                .transactionId("BANK-TX-999")
                .amount(BigDecimal.valueOf(1500000.00))
                .status("SUCCESS")
                .build();

        WebhookProcessingResult result = webhookProcessingService.processBankWebhook(payload);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentGatewayStatus.PAID);
        assertThat(result.getFolioSettled()).isTrue();
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentGatewayStatus.PAID);
        assertThat(pendingOrder.getBankTransactionId()).isEqualTo("BANK-TX-999");

        verify(paymentOrderRepository).save(pendingOrder);
        verify(settlementService).settle(
                eq("CABIN-801"),
                contains("VIETQR-PAY-100"),
                eq(PaymentMethod.VIETQR),
                eq(BigDecimal.valueOf(1500000.00)),
                anyString(),
                eq(false),
                eq("SYSTEM_VIETQR"),
                anySet()
        );
    }

    @Test
    @DisplayName("processBankWebhook is idempotent when order is already PAID")
    void testProcessWebhookIdempotent() {
        pendingOrder.setStatus(PaymentGatewayStatus.PAID);
        pendingOrder.setSettledAt(Instant.now());
        CabinFolioAccount settledFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-801")
                .status(FolioStatus.SETTLED)
                .build();

        when(paymentOrderRepository.findByPaymentReferenceForUpdate("PAY-100")).thenReturn(Optional.of(pendingOrder));
        when(folioRepository.findByCabinNumber("CABIN-801")).thenReturn(Optional.of(settledFolio));

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-100")
                .transactionId("BANK-TX-999")
                .amount(BigDecimal.valueOf(1500000.00))
                .status("SUCCESS")
                .build();

        WebhookProcessingResult result = webhookProcessingService.processBankWebhook(payload);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentGatewayStatus.PAID);
        assertThat(result.getFolioSettled()).isTrue();
        verify(settlementService, never()).settle(any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("processBankWebhook rejects underpayment with BusinessRuleException")
    void testProcessWebhookUnderpaid() {
        when(paymentOrderRepository.findByPaymentReferenceForUpdate("PAY-100")).thenReturn(Optional.of(pendingOrder));

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-100")
                .transactionId("BANK-TX-999")
                .amount(BigDecimal.valueOf(1000000.00)) // Expected 1,500,000
                .status("SUCCESS")
                .build();

        assertThatThrownBy(() -> webhookProcessingService.processBankWebhook(payload))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Underpaid amount");
    }

    @Test
    @DisplayName("processBankWebhook rejects expired order")
    void testProcessWebhookExpired() {
        pendingOrder.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(paymentOrderRepository.findByPaymentReferenceForUpdate("PAY-100")).thenReturn(Optional.of(pendingOrder));

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .paymentReference("PAY-100")
                .transactionId("BANK-TX-999")
                .amount(BigDecimal.valueOf(1500000.00))
                .status("SUCCESS")
                .build();

        assertThatThrownBy(() -> webhookProcessingService.processBankWebhook(payload))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("EXPIRED");
    }
}
