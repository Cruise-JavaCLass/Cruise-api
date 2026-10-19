package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pos_dispute_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeHistory extends BaseEntity {

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private DisputeStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private DisputeStatus newStatus;

    @Column(name = "action_by", nullable = false, length = 64)
    private String actionBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
