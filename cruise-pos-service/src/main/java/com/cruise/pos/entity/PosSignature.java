package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_signatures", indexes = {
        @Index(name = "idx_sig_tx_uuid", columnList = "transaction_uuid", unique = true),
        @Index(name = "idx_sig_cabin", columnList = "cabin_number"),
        @Index(name = "idx_sig_passenger", columnList = "passenger_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSignature extends BaseEntity {

    @Column(name = "transaction_uuid", nullable = false, unique = true, length = 64)
    private String transactionUuid;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(name = "signer_name", nullable = false, length = 128)
    private String signerName;

    @Lob
    @Column(name = "signature_image_base64", nullable = false, columnDefinition = "TEXT")
    private String signatureImageBase64;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Column(name = "captured_by", length = 64)
    private String capturedBy;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;
}
