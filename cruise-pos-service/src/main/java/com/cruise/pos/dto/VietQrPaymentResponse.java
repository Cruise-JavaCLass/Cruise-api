package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentGatewayStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VietQrPaymentResponse {

    private String paymentReference;
    private String cabinNumber;
    private BigDecimal amount;
    private String currency;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String description;
    private String qrCodeText;
    private String qrImageUrl;
    private PaymentGatewayStatus status;
    private Instant expiresAt;
}
