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

The service-specific `RbacConfig` classes are opt-in imports only. Filter registration
is defined once in `RbacFilterConfiguration`; itinerary, activity and POS also import
the single `RemoteRbacConfiguration` for live identity verification. Auth supplies its
local verifier. Keeping these imports in service configuration preserves MVC test
slice isolation while removing the repeated remote-verifier/filter bean definitions.

## KAN-70 / KAN-71 / KAN-72: validation

Validated locally on 2026-11-02 with Java 25 (compiler target 21), Maven 3.9.15,
Node 24.22.1 and npm 12.2.1:

- `mvn -q test`: 1,953 tests, zero failures/errors/skips across all six modules.
- `mvn -q -DskipTests package`: all modules package successfully.
- Web: `npx vitest run --maxWorkers=2`: 107 files, 736 tests passed; the separate
  `node --test .github/scripts/jules-client.test.mjs` suite also passes.
- Web `npm run lint` exits 0 with repository warnings; `npm run build` succeeds
  (TypeScript + Vite). The existing Lottie dependency emits an eval build warning.

The new tests exercise all eight roles at real Spring HTTP boundaries in auth,
itinerary, activity and POS, including positive persistence and denied writes
without mutations. Auth integration uses real signed access/refresh JWTs and H2
user state; domain cross-role tests mock only the remote identity verifier. The
remote verifier itself is covered against a loopback HTTP identity-service stub.
Existing domain tests use an explicit test-only authenticated fixture and keep
the production authorization filter active.

Web tests cover deep links, menus, landing pages, read-only roster controls,
unknown roles, actual login payload/token contract and Bearer injection. A server
401 clears the staff session; 403 opens the denied page, even if a domain service
contains an offline/demo fallback. Demo staff accounts now require explicit
development opt-in and cannot be enabled in a production build.

Review and staging sign-off remain for the team: PostgreSQL schema deployment,
reverse-proxy routing, TLS to the private auth service, and real browser sessions
for representative staff roles. GitHub CI and staging were not run from this
workspace. The three unrelated bugs listed after the RBAC story in the source
document are excluded from this change.

### Review follow-up — 2026-11-03

The initial SonarCloud analysis reported 3.2% new-code duplication against a 3%
limit, caused by repeated remote RBAC bean wiring in itinerary, activity and POS.
That wiring now lives in the shared configurations described above. Configuration
context tests verify both local-verifier and remote-verifier imports, single filter
registration and role enforcement. The full backend `mvn -B verify` run and the
common-module verification pass; the combined suite contains 1,955 passing tests,
with zero failures, errors or skips. This is a follow-up review fix on KAN-66;
the six original KAN-67–72 task commits remain intact.
