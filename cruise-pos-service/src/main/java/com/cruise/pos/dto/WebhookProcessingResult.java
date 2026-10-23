package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentGatewayStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookProcessingResult {

    private String paymentReference;
    private PaymentGatewayStatus paymentStatus;
    private Boolean folioSettled;
    private String message;
    private Instant processedAt;
}
