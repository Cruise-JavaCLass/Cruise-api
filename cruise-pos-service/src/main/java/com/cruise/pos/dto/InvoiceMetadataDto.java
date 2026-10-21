package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata của hoá đơn PDF đã phát hành.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceMetadataDto {

    private String invoiceNumber;

    private String settlementUuid;

    private String cabinNumber;

    private String sha256;

    private Long byteSize;

    private Instant generatedAt;

    private int regeneratedCount;
}
