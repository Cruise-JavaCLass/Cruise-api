package com.cruise.common.security;

import com.cruise.common.enums.Role;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static com.cruise.common.security.Permission.*;
import static org.assertj.core.api.Assertions.*;

class PermissionRulesTest {
    private final ServiceAccessPolicy policy = new ServiceAccessPolicy();

    static Stream<Arguments> matrix() {
        return Stream.of(
            Arguments.of("POST", "/api/auth/users", Set.of(Role.SYSTEM_ADMIN)),
            Arguments.of("GET", "/api/auth/roles", Set.of(Role.SYSTEM_ADMIN)),
            Arguments.of("GET", "/api/admin/audit/events", Set.of(Role.SYSTEM_ADMIN)),
            Arguments.of("POST", "/api/admin/pos/terminals", Set.of(Role.SYSTEM_ADMIN)),
            Arguments.of("POST", "/api/itinerary/ships", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.ITINERARY_COORDINATOR)),
            Arguments.of("POST", "/api/activities", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.ACTIVITY_MANAGER)),
            Arguments.of("PUT", "/api/excursions/7", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.SHORE_EXCURSION_MANAGER)),
            Arguments.of("POST", "/api/activities/excursions/7/check-in", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.SHORE_EXCURSION_MANAGER)),
            Arguments.of("POST", "/api/v1/tour-providers", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.SHORE_EXCURSION_MANAGER)),
            Arguments.of("POST", "/api/v1/tour-providers/settlements", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("POST", "/api/passengers/register", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("POST", "/api/pos/transactions", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.POS_STAFF)),
            Arguments.of("POST", "/api/pos/transactions/5/refund", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("POST", "/api/pos/folios/A101/settle", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("PUT", "/api/pos/folios/A101/credit-limit", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("POST", "/api/pos/folios/A101/ledger", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.POS_STAFF)),
            Arguments.of("GET", "/api/pos/reports/revenue", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER, Role.FINANCE_FRONT_DESK)),
            Arguments.of("POST", "/api/operations/solas-muster/trigger", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER)),
            Arguments.of("GET", "/api/passenger-portal/catalog/tour/1", Set.of(Role.SYSTEM_ADMIN, Role.OPERATIONS_MANAGER,
                Role.ITINERARY_COORDINATOR, Role.ACTIVITY_MANAGER, Role.SHORE_EXCURSION_MANAGER, Role.PASSENGER))
        );
    }

    @ParameterizedTest @MethodSource("matrix")
    void enforcesTheReviewedBusinessMatrixForEveryRole(String method, String path, Set<Role> allowed) {
        var rule = policy.requirement(method, path);
        assertThat(rule.mode()).isEqualTo(AccessRequirement.Mode.PERMISSION);
        for (Role role : Role.values()) {
            assertThat(RolePermissions.allows(role, rule.permission())).as("%s %s %s", role, method, path)
                    .isEqualTo(allowed.contains(role));
        }
    }

    @Test void grantsAreCompleteImmutableAndFailClosed() {
        for (Role role : Role.values()) {
            assertThat(RolePermissions.permissions(role)).isNotEmpty();
            assertThatThrownBy(() -> RolePermissions.permissions(role).clear()).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(RolePermissions.permissions(Role.SYSTEM_ADMIN)).containsExactlyInAnyOrder(Permission.values());
        assertThat(RolePermissions.permissions(null)).isEmpty();
        assertThat(RolePermissions.allows(null, USERS_MANAGE)).isFalse();
        assertThat(RolePermissions.allows(Role.SYSTEM_ADMIN, null)).isFalse();
        assertThat(RolePermissions.permissions(Role.OPERATIONS_MANAGER)).doesNotContain(USERS_MANAGE, SECURITY_AUDIT_READ, TERMINALS_MANAGE);
    }

    @Test void aliasesAndHeadsHaveTheSameRules() {
        assertThat(policy.requirement("PATCH", "/api/activities/passengers/5/check-in"))
                .isEqualTo(policy.requirement("PATCH", "/api/passengers/5/check-in"));
        assertThat(policy.requirement("POST", "/api/v1/pos/shifts/1/cash-drops"))
                .isEqualTo(policy.requirement("POST", "/api/pos/shifts/1/cash-drops"));
        assertThat(policy.requirement("HEAD", "/api/activities/tour/1"))
                .isEqualTo(policy.requirement("GET", "/api/activities/tour/1"));
    }

    @Test void unknownResourceFamiliesAndSimilarPrefixesDenyEvenAdmin() {
        for (String path : Arrays.asList("/api/new-admin-feature", "/api/activities-evil", "/api/auth/users-evil", "/api/admin")) {
            assertThat(policy.requirement("GET", path).mode()).as(path).isEqualTo(AccessRequirement.Mode.DENY);
        }
        assertThat(policy.requirement("POST", "/api/passenger-portal/catalog").mode()).isEqualTo(AccessRequirement.Mode.DENY);
        assertThat(policy.requirement("POST", "/api/activities/health").mode()).isNotEqualTo(AccessRequirement.Mode.PUBLIC);
    }
}
