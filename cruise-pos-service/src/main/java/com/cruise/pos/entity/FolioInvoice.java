package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Metadata của hoá đơn PDF đã phát hành cho một settlement.
 * Không lưu bytes PDF: hoá đơn được render lại tất định từ settlement.
 */
@Entity
@Table(name = "pos_folio_invoices", indexes = {
        @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true),
        @Index(name = "idx_invoice_settlement", columnList = "settlement_uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolioInvoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, unique = true, length = 64)
    private String invoiceNumber;

    @Column(name = "settlement_uuid", nullable = false, length = 64)
    private String settlementUuid;

    @Column(name = "folio_id", nullable = false)
    private Long folioId;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "regenerated_count", nullable = false)
    @Builder.Default
    private Integer regeneratedCount = 0;
}
