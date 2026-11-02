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

## KAN-68 / KAN-69: enforcement and contracts

- Auth, itinerary, activity and POS register the authorization filter for `/api/*`.
  Protection applies on direct service ports as well as behind a reverse proxy.
- Production auth startup requires `JWT_SECRET` (use a randomly generated secret
  of at least 32 bytes). The old publicly known fallback is no longer used by
  Spring injection; the `dev` profile and isolated test configuration have explicit
  non-production keys.
- `401 UNAUTHENTICATED`: missing/malformed bearer token, invalid signature,
  expiration, refresh token used as access token, revoked token, deleted or disabled
  user, or a token whose role no longer matches the database.
- `403 FORBIDDEN`: valid identity without the required permission; undefined API
  families deny even administrators. Ambiguous encoded/dot/semicolon paths return 400.
- `503 AUTH_UNAVAILABLE`: remote introspection fails/times out; access is denied.
  Set `CRUISE_AUTH_BASE_URL` to the private auth-service origin in deployed services
  (Spring property `cruise.auth.base-url`; default `http://localhost:8081`). Calls
  have 1s connection / 2s read timeouts and no positive cache.
- `POST /api/auth/access-context` accepts `{ "token": "<access token>" }` and returns
  a minimal verified identity, or a successful envelope without data for invalid
  tokens. It validates issuer/type/expiry/signature/revocation and current user state.
- `GET /api/auth/roles`: administrator only; returns all grants.
- `GET /api/auth/permissions`: authenticated user; returns own permissions.
- Existing credential/token proof endpoints (login, refresh, verify-token,
  verify-password, logout, revoke-token and aliases) retain their public contracts.
  Logout stays idempotent. They do not grant access to internal management APIs.
- `X-Caller-Role` is replaced with the verified canonical role. `X-Caller-Id` is
  stripped because auth and passenger record IDs have no established mapping.
- Successful user creation/deletion appends `identity_audit_events` in the same
  transaction. Failure to persist the event rolls back the identity mutation.
  Structured denial/write logs contain method, path, status, role and numeric user ID,
  never credentials or tokens. Apply `docs/db/KAN-66-identity-audit.sql` when schema
  changes are managed manually; development `ddl-auto=update` creates the table.
- POS uses the verified caller for its existing Spring Security method checks.
  The shared RBAC filter is active even if `POS_SECURITY_ENABLED=false`.
- Web route guard, sidebar and command palette use one permission matrix. Unknown
  roles cannot become staff. Passenger roster mutation controls require manage
  permission. UI checks supplement server authorization, never replace it.
- The web client sends `usernameOrEmail` and reads `accessToken` from the actual
  auth contract, and attaches the session Bearer token to API requests. Default
  `/api` uses Vite's development proxy to the owning service (the current gateway
  only exposes system telemetry). Production hosting must provide equivalent
  reverse-proxy routing or set `VITE_API_BASE_URL` to that API origin.

No role table migration is needed: role storage already exists. Permissions are
versioned code. Token revocation storage remains the existing auth-service in-memory
implementation; a persistent/distributed revocation store is a separate deployment
concern, and this change does not claim restart-safe or multi-instance revocation.
