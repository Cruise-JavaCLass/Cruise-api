package com.cruise.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Role Enum Unit Tests")
class RoleTest {

    @Test
    @DisplayName("Should contain all expected cruise role definitions")
    void shouldContainExpectedRoles() {
        Role[] roles = Role.values();

        assertThat(roles).contains(
                Role.PASSENGER,
                Role.ITINERARY_COORDINATOR,
                Role.ACTIVITY_MANAGER,
                Role.SHORE_EXCURSION_MANAGER,
                Role.POS_STAFF,
                Role.FINANCE_FRONT_DESK,
                Role.OPERATIONS_MANAGER,
                Role.SYSTEM_ADMIN
        );
    }

    @Test
    @DisplayName("Should resolve Role from valid string names")
    void shouldResolveRoleByName() {
        assertThat(Role.valueOf("PASSENGER")).isEqualTo(Role.PASSENGER);
        assertThat(Role.valueOf("SYSTEM_ADMIN")).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(Role.valueOf("ITINERARY_COORDINATOR")).isEqualTo(Role.ITINERARY_COORDINATOR);
        assertThat(Role.valueOf("POS_STAFF")).isEqualTo(Role.POS_STAFF);
    }
}
