package com.cruise.pos.service;

import com.cruise.pos.dto.BankWebhookPayload;
import com.cruise.pos.dto.SettleFolioRequest;
import com.cruise.pos.dto.WebhookProcessingResult;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankWebhookProcessingService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final CabinFolioAccountRepository folioRepository;
    private final FolioSettlementService settlementService;

    @Transactional
    public WebhookProcessingResult processBankWebhook(BankWebhookPayload payload) {
        Objects.requireNonNull(payload, "BankWebhookPayload must not be null");

        PaymentOrder order = paymentOrderRepository.findByPaymentReference(payload.getPaymentReference())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found for ref: " + payload.getPaymentReference()));

        // Idempotency: If already marked as PAID, return idempotently
        if (order.getStatus() == PaymentGatewayStatus.PAID) {
            log.info("Payment order {} already settled, returning idempotent success", payload.getPaymentReference());
            return WebhookProcessingResult.builder()
                    .paymentReference(order.getPaymentReference())
                    .paymentStatus(order.getStatus())
                    .folioSettled(true)
                    .message("Payment already processed and folio settled")
                    .processedAt(order.getSettledAt())
                    .build();
        }

        if ("FAILED".equalsIgnoreCase(payload.getStatus())) {
            order.setStatus(PaymentGatewayStatus.FAILED);
            paymentOrderRepository.save(order);
            return WebhookProcessingResult.builder()
                    .paymentReference(order.getPaymentReference())
                    .paymentStatus(PaymentGatewayStatus.FAILED)
                    .folioSettled(false)
                    .message("Bank payment transaction marked as failed")
                    .processedAt(Instant.now())
                    .build();
        }

        // Amount verification
        if (payload.getAmount().compareTo(order.getAmount()) < 0) {
            throw new BusinessRuleException("Underpaid amount: expected " + order.getAmount() + " but received " + payload.getAmount());
        }

        order.setStatus(PaymentGatewayStatus.PAID);
        order.setBankTransactionId(payload.getTransactionId());
        order.setSettledAt(Instant.now());
        paymentOrderRepository.save(order);

        // Auto settle cabin folio
        boolean folioSettled = false;
        try {
            var folioOpt = folioRepository.findByCabinNumber(order.getCabinNumber());
            if (folioOpt.isPresent()) {
                CabinFolioAccount folio = folioOpt.get();
                if (folio.getStatus() == FolioStatus.ACTIVE || folio.getStatus() == FolioStatus.LOCKED) {
                    String idempotencyKey = "VIETQR-" + order.getPaymentReference();
                    java.util.Set<String> roles = java.util.Set.of("ROLE_CASHIER", "ROLE_ADMIN");
                    settlementService.settle(
                            folio.getCabinNumber(),
                            idempotencyKey,
                            PaymentMethod.VIETQR,
                            folio.getCurrentBalance(),
                            "Settled via VietQR Bank Webhook - Ref: " + order.getPaymentReference(),
                            false,
                            "SYSTEM_VIETQR",
                            roles
                    );
                    folioSettled = true;
                    log.info("Successfully auto-settled folio for cabin: {} via VietQR", order.getCabinNumber());
                }
            }
        } catch (Exception e) {
            log.error("Failed to auto-settle folio for cabin {}: {}", order.getCabinNumber(), e.getMessage(), e);
        }

        return WebhookProcessingResult.builder()
                .paymentReference(order.getPaymentReference())
                .paymentStatus(PaymentGatewayStatus.PAID)
                .folioSettled(folioSettled)
                .message("Bank payment received and processed successfully")
                .processedAt(order.getSettledAt())
                .build();
    }
}
