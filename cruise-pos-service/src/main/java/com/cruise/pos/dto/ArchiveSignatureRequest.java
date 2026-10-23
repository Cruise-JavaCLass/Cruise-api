package com.cruise.pos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveSignatureRequest {

    @NotBlank(message = "transactionUuid is required")
    @Size(max = 64, message = "transactionUuid must not exceed 64 characters")
    private String transactionUuid;

    private Long transactionId;

    @NotBlank(message = "cabinNumber is required")
    @Size(max = 32, message = "cabinNumber must not exceed 32 characters")
    private String cabinNumber;

    private Long passengerId;

    @NotBlank(message = "signerName is required")
    @Size(max = 128, message = "signerName must not exceed 128 characters")
    private String signerName;

    @NotBlank(message = "signatureImageBase64 is required")
    @Size(max = 2_000_000, message = "signatureImageBase64 exceeds maximum allowed size")
    private String signatureImageBase64;

    private String capturedBy;

    @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;
}
