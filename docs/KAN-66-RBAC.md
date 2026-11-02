# KAN-66 — Implement Role-Based Access Control

## KAN-67: roles and permissions

The existing eight `Role` enum values and persisted user role column remain the
source of truth. Grants are immutable in `RolePermissions`; changing a user's
role requires an administrator. Unknown roles and legacy aliases such as `ADMIN`,
`STAFF`, and `MANAGER` do not acquire system permissions.

| Role | Domain access |
| --- | --- |
| SYSTEM_ADMIN | All defined permissions, including users, audit and terminal administration |
| OPERATIONS_MANAGER | All operational domains, excluding identity/security administration |
| ITINERARY_COORDINATOR | Manage itinerary; read catalog |
| ACTIVITY_MANAGER | Manage activities; read passenger rosters, itinerary and catalog |
| SHORE_EXCURSION_MANAGER | Manage excursions/providers; read passenger rosters, itinerary and catalog |
| POS_STAFF | Operate POS; read passenger rosters and itinerary |
| FINANCE_FRONT_DESK | Manage financial settlement and passenger check-in; read reports, POS and itinerary |
| PASSENGER | Read itinerary and guest catalog; no internal management access |

This story governs role access to internal APIs and staff screens. Passenger
self-service ownership (mapping an auth user to a passenger record, booking,
folio or notification) must be implemented before granting passengers those
ID-addressed endpoints. An auth user ID is not assumed to be a passenger ID.
Existing anonymous demo self-service calls to internal endpoints are rejected.

Task mapping: KAN-67 roles/grants; KAN-68 authorization middleware; KAN-69 API and
web restrictions; KAN-70 permission unit tests; KAN-71 unauthenticated API tests;
KAN-72 cross-role API and web tests.
