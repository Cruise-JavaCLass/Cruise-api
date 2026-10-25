package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_cancellation_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemCancellationPolicy extends BaseEntity {

    @Column(name = "policy_name", nullable = false, length = 150)
    private String policyName;

    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType; // EXCURSION, ONBOARD_ACTIVITY, SPA, DINING

    @Column(name = "hours_before_cutoff", nullable = false)
    private Integer hoursBeforeCutoff;

    @Column(name = "refund_percentage", nullable = false)
    private Integer refundPercentage;

    @Column(name = "cancellation_fee", precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal cancellationFee = java.math.BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;
}
