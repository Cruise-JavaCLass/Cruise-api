package com.cruise.pos.service;

import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGatewayService {

    private static final String DEFAULT_BANK_CODE = "970436"; // Vietcombank
    private static final String DEFAULT_ACCOUNT_NUMBER = "1028391829";
    private static final String DEFAULT_ACCOUNT_NAME = "CRUISE LINE OPERATIONS CORP";

    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional
    public VietQrPaymentResponse generateVietQrPayment(GenerateVietQrRequest request) {
        Objects.requireNonNull(request, "GenerateVietQrRequest must not be null");

        // Idempotency check: lookup existing order
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing = paymentOrderRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                PaymentOrder stored = existing.get();
                validateIdempotentReplay(stored, request);
                log.info("Replaying existing payment order for idempotency key: {}", request.getIdempotencyKey());
                return mapToResponse(stored);
            }
        }

        String paymentRef = "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription()
                : "THANH TOAN FOLIO " + request.getCabinNumber();

        // Standard VietQR QuickLink format with URL encoding
        String qrImageUrl = UriComponentsBuilder
                .fromUriString("https://img.vietqr.io/image/{bank}-{acc}-compact2.png")
                .queryParam("amount", request.getAmount().toBigInteger().toString())
                .queryParam("addInfo", "{info}")
                .queryParam("accountName", "{name}")
                .encode()
                .buildAndExpand(DEFAULT_BANK_CODE, DEFAULT_ACCOUNT_NUMBER, description, DEFAULT_ACCOUNT_NAME)
                .toUriString();

        String qrCodeText = String.format("00020101021238570010A00000072701270006%s0110%s0208QRIBFTTA530370454%s5802VN62%s08%s6304",
                DEFAULT_BANK_CODE, DEFAULT_ACCOUNT_NUMBER, request.getAmount().toBigInteger().toString(),
                description.length() + 4, description);

        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        PaymentOrder order = PaymentOrder.builder()
                .paymentReference(paymentRef)
                .cabinNumber(request.getCabinNumber())
                .passengerId(request.getPassengerId())
                .amount(request.getAmount())
                .currency("VND")
                .bankCode(DEFAULT_BANK_CODE)
                .accountNumber(DEFAULT_ACCOUNT_NUMBER)
                .accountName(DEFAULT_ACCOUNT_NAME)
                .description(description)
                .qrDataUrl(qrImageUrl)
                .status(PaymentGatewayStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(expiresAt)
                .build();

        try {
            paymentOrderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
                throw e;
            }
            log.info("Concurrent insert conflict for idempotencyKey={}, recovering stored order", request.getIdempotencyKey());
            PaymentOrder stored = paymentOrderRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> e);
            validateIdempotentReplay(stored, request);
            return mapToResponse(stored);
        }

        log.info("Created payment order ref: {} for cabin: {} amount: {}", paymentRef, request.getCabinNumber(), request.getAmount());
        return mapToResponse(order, qrCodeText);
    }

    private void validateIdempotentReplay(PaymentOrder stored, GenerateVietQrRequest request) {
        if (!Objects.equals(stored.getCabinNumber(), request.getCabinNumber())) {
            throw new BusinessRuleException("Idempotent replay conflict: cabinNumber mismatch with existing payment order");
        }
        if (stored.getAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessRuleException("Idempotent replay conflict: amount mismatch with existing payment order");
        }
    }

    public PaymentGatewayStatus getOrderStatus(String reference) {
        return paymentOrderRepository.findByPaymentReference(reference)
                .map(PaymentOrder::getStatus)
                .orElse(PaymentGatewayStatus.FAILED);
    }

    private VietQrPaymentResponse mapToResponse(PaymentOrder order) {
        return mapToResponse(order, "VIETQR:" + order.getPaymentReference());
    }

    private VietQrPaymentResponse mapToResponse(PaymentOrder order, String qrCodeText) {
        return VietQrPaymentResponse.builder()
                .paymentReference(order.getPaymentReference())
                .cabinNumber(order.getCabinNumber())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .bankCode(order.getBankCode())
                .accountNumber(order.getAccountNumber())
                .accountName(order.getAccountName())
                .description(order.getDescription())
                .qrCodeText(qrCodeText)
                .qrImageUrl(order.getQrDataUrl())
                .status(order.getStatus())
                .expiresAt(order.getExpiresAt())
                .build();
    }
}
