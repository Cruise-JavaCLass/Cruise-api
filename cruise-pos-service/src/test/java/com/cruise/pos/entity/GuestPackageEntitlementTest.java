package com.cruise.pos.entity;

import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GuestPackageEntitlementTest {

    @Test
    @DisplayName("getRemainingQuota correctly computes non-negative remaining quota")
    void testGetRemainingQuota() {
        GuestPackageEntitlement entitlement = GuestPackageEntitlement.builder()
                .totalQuota(5)
                .usedQuota(2)
                .build();
        assertThat(entitlement.getRemainingQuota()).isEqualTo(3);

        entitlement.setUsedQuota(6);
        assertThat(entitlement.getRemainingQuota()).isEqualTo(0);
    }

    @Test
    @DisplayName("isApplicable returns false when status is not ACTIVE")
    void testIsApplicable_NotActive() {
        GuestPackageEntitlement entitlement = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.EXHAUSTED)
                .totalQuota(5)
                .usedQuota(0)
                .build();
        assertThat(entitlement.isApplicable("BEVERAGE", BigDecimal.valueOf(10))).isFalse();
    }

    @Test
    @DisplayName("isApplicable returns false when remaining quota is 0")
    void testIsApplicable_NoRemainingQuota() {
        GuestPackageEntitlement entitlement = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.ACTIVE)
                .totalQuota(5)
                .usedQuota(5)
                .build();
        assertThat(entitlement.isApplicable("BEVERAGE", BigDecimal.valueOf(10))).isFalse();
    }

    @Test
    @DisplayName("isApplicable returns false outside valid time window")
    void testIsApplicable_TimeWindow() {
        Instant now = Instant.now();
        GuestPackageEntitlement beforeWindow = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.ACTIVE)
                .totalQuota(5)
                .usedQuota(0)
                .validFrom(now.plus(1, ChronoUnit.DAYS))
                .build();
        assertThat(beforeWindow.isApplicable("BEVERAGE", BigDecimal.valueOf(10))).isFalse();

        GuestPackageEntitlement afterWindow = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.ACTIVE)
                .totalQuota(5)
                .usedQuota(0)
                .validTo(now.minus(1, ChronoUnit.DAYS))
                .build();
        assertThat(afterWindow.isApplicable("BEVERAGE", BigDecimal.valueOf(10))).isFalse();
    }

    @Test
    @DisplayName("isApplicable checks eligible category matching and ALL category")
    void testIsApplicable_CategoryMatching() {
        GuestPackageEntitlement specificCat = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.ACTIVE)
                .totalQuota(5)
                .usedQuota(0)
                .eligibleCategory("BEVERAGE")
                .build();
        assertThat(specificCat.isApplicable("BEVERAGE", BigDecimal.valueOf(10))).isTrue();
        assertThat(specificCat.isApplicable("FOOD", BigDecimal.valueOf(10))).isFalse();

        GuestPackageEntitlement allCat = GuestPackageEntitlement.builder()
                .status(EntitlementStatus.ACTIVE)
                .totalQuota(5)
                .usedQuota(0)
                .eligibleCategory("ALL")
                .build();
        assertThat(allCat.isApplicable("FOOD", BigDecimal.valueOf(10))).isTrue();
    }
}
