package com.cruise.pos.service;

import com.cruise.pos.dto.GenerateVietQrRequest;
import com.cruise.pos.dto.VietQrPaymentResponse;
import com.cruise.pos.entity.PaymentOrder;
import com.cruise.pos.enums.PaymentGatewayStatus;
import com.cruise.pos.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing = paymentOrderRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Replaying existing payment order for idempotency key: {}", request.getIdempotencyKey());
                return mapToResponse(existing.get());
            }
        }

        String paymentRef = "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription()
                : "THANH TOAN FOLIO " + request.getCabinNumber();

        // Standard VietQR QuickLink format: https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-compact2.png?amount=<AMOUNT>&addInfo=<DESCRIPTION>&accountName=<ACCOUNT_NAME>
        String qrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                DEFAULT_BANK_CODE,
                DEFAULT_ACCOUNT_NUMBER,
                request.getAmount().toBigInteger().toString(),
                description.replace(" ", "%20"),
                DEFAULT_ACCOUNT_NAME.replace(" ", "%20")
        );

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

        paymentOrderRepository.save(order);
        log.info("Created payment order ref: {} for cabin: {} amount: {}", paymentRef, request.getCabinNumber(), request.getAmount());

        return mapToResponse(order, qrCodeText);
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
