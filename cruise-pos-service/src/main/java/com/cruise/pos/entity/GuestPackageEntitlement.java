package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_guest_package_entitlements", indexes = {
        @Index(name = "idx_entitlement_passenger", columnList = "passenger_id"),
        @Index(name = "idx_entitlement_status", columnList = "status"),
        @Index(name = "idx_entitlement_pkg_type", columnList = "package_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestPackageEntitlement extends BaseEntity {

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "package_name", nullable = false, length = 100)
    private String packageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 50)
    private PackageType packageType;

    @Column(name = "eligible_category", length = 50)
    private String eligibleCategory;

    @Column(name = "total_quota", nullable = false)
    @Builder.Default
    private Integer totalQuota = 0;

    @Column(name = "used_quota", nullable = false)
    @Builder.Default
    private Integer usedQuota = 0;

    @Column(name = "max_item_price_cap", precision = 10, scale = 2)
    private BigDecimal maxItemPriceCap;

    @Column(name = "priority_level", nullable = false)
    @Builder.Default
    private Integer priorityLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private EntitlementStatus status = EntitlementStatus.ACTIVE;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    public int getRemainingQuota() {
        return Math.max(0, totalQuota - usedQuota);
    }

    public boolean isApplicable(String category, BigDecimal itemPrice) {
        if (status != EntitlementStatus.ACTIVE) return false;
        if (getRemainingQuota() <= 0) return false;
        if (eligibleCategory != null && !eligibleCategory.equalsIgnoreCase(category) && !"ALL".equalsIgnoreCase(eligibleCategory)) {
            return false;
        }
        return true;
    }
}
