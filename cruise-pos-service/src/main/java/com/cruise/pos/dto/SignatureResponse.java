package com.cruise.pos.dto;

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
public class SignatureResponse {
    private Long id;
    private String transactionUuid;
    private Long transactionId;
    private String cabinNumber;
    private Long passengerId;
    private String signerName;
    private String signatureImageBase64;
    private String sha256Checksum;
    private String capturedBy;
    private BigDecimal amount;
    private Instant createdAt;
}
