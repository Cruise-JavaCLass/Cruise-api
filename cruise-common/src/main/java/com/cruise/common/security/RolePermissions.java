package com.cruise.common.security;

import com.cruise.common.enums.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import static com.cruise.common.security.Permission.*;

/** No implicit role hierarchy and no legacy ADMIN/STAFF aliases. */
public final class RolePermissions {
    private RolePermissions() {}

    private static final Map<Role, Set<Permission>> GRANTS = Map.of(
        Role.SYSTEM_ADMIN, Set.copyOf(EnumSet.allOf(Permission.class)),
        Role.OPERATIONS_MANAGER, Set.of(ITINERARY_READ, ITINERARY_MANAGE,
            ACTIVITIES_READ, ACTIVITIES_MANAGE, EXCURSIONS_READ, EXCURSIONS_MANAGE,
            PASSENGERS_READ, PASSENGERS_MANAGE, POS_READ, POS_OPERATE,
            FINANCE_MANAGE, REPORTS_READ, OPERATIONS_MANAGE, CATALOG_READ),
        Role.ITINERARY_COORDINATOR, Set.of(ITINERARY_READ, ITINERARY_MANAGE, CATALOG_READ),
        Role.ACTIVITY_MANAGER, Set.of(ITINERARY_READ, ACTIVITIES_READ, ACTIVITIES_MANAGE,
            PASSENGERS_READ, CATALOG_READ),
        Role.SHORE_EXCURSION_MANAGER, Set.of(ITINERARY_READ, EXCURSIONS_READ, EXCURSIONS_MANAGE,
            PASSENGERS_READ, CATALOG_READ),
        Role.POS_STAFF, Set.of(ITINERARY_READ, POS_READ, POS_OPERATE, PASSENGERS_READ),
        Role.FINANCE_FRONT_DESK, Set.of(ITINERARY_READ, POS_READ, FINANCE_MANAGE,
            REPORTS_READ, PASSENGERS_READ, PASSENGERS_MANAGE),
        Role.PASSENGER, Set.of(ITINERARY_READ, CATALOG_READ)
    );

    public static Set<Permission> permissions(Role role) {
        return role == null ? Set.of() : GRANTS.getOrDefault(role, Set.of());
    }

    public static boolean allows(Role role, Permission permission) {
        return permission != null && permissions(role).contains(permission);
    }
}
