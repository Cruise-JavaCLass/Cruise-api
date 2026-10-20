package com.cruise.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Bộ đếm cấp số hoá đơn theo từng cabin. Khoá dòng (PESSIMISTIC_WRITE)
 * đảm bảo cấp số đơn điệu và duy nhất kể cả khi chạy nhiều instance.
 */
@Entity
@Table(name = "pos_invoice_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSequence {

    @Id
    @Column(name = "sequence_key", nullable = false, length = 64)
    private String sequenceKey;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;
}
