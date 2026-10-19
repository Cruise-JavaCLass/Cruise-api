package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pos_idempotency_records", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosIdempotencyRecord extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_path", length = 256)
    private String requestPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "transaction_id")
    private Long transactionId;
}
