package com.cruise.pos.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String transactionUuid;

    private Long transactionId;

    @NotBlank(message = "cabinNumber is required")
    private String cabinNumber;

    private Long passengerId;

    @NotBlank(message = "signerName is required")
    private String signerName;

    @NotBlank(message = "signatureImageBase64 is required")
    private String signatureImageBase64;

    private String capturedBy;

    private BigDecimal amount;
}
