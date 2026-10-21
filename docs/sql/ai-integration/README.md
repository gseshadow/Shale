# Phase 3B.1 integration security candidate

This directory owns the additive SQL candidate and operator instructions, not another AI roadmap.
The [canonical contract and implementation record](../../architecture/ai-integration-and-mcp.md#1013-phase-3b1-integration-persistence-and-security-foundation)
owns status and evidence. Nothing here authorizes production execution or enablement.

## Dependencies and authority inventory

Before DDL, repository schema/migrations and affected tests were inspected at live base
`a97691ecb2147d9888d9f636aee8cc9e07842cdd`, following merged #1898–#1901.

| Existing dependency | Verified repository contract / use |
| --- | --- |
| `dbo.ShaleClients` | `Id`, `IsActive`; active tenant required at every tenant service gate. |
| `dbo.Users` | `id`, nullable `ShaleClientId`, `is_admin`, `is_deleted`, `IsRemoved`; current same-tenant actor required, no guessed active flag. |
| Tenant-qualified Users key | Existing unique `(ShaleClientId,id)` prerequisite from durable-session migrations; actual enabled unique key columns are checked before DDL. No parent key is added. |
| `sec.TenantFilter`, `sec.fn_FilterByTenant` | Existing enabled/schema-bound policy and strict function required. No shared function or unrelated predicate is changed. Reviewed ownership is dbo. |
| `dbo.EntityActionAuditLog` | Existing tenant, actor, numeric entity/action, UTC, source/correlation and bounded metadata. Add five mutation entity types using the established positive-allowlist parser; retain every prior token and unrelated check. |
| `dbo.GlobalControlPlaneAuditLog` | Existing operator/global entity/action, UTC and bounded metadata; capability events reuse this schema. |
| Existing session/runtime patterns | Both SQL context keys on each lease, explicit UTC JDBC conversion, hash-only random credentials, immutable ownership, RowVer and same-transaction audit. Ordinary UserSessions remains unchanged. |

These are source dependencies and local catalog checks, not claims about a deployed database. The missing
approved authoritative **Case** artifact remains a separate blocker; no Case object is a dependency here.

## Candidate application

`phase3b1.sql` is one fail-fast transaction, including tables, indexes, FKs, policy predicates, immutable-owner
triggers, audit vocabulary, bounded modules, empty roles and receipts. Run it manually only in a separately
approved database as dbo, in a fresh session with **both context keys NULL**, stopping on any error. Do not
install through application startup. Defaults insert only the disabled global singleton; no tenant policy,
registration, key, grant, session or credential is seeded. There is no enablement in the installer.

The migration refuses missing dependencies, wrong dbo ownership, missing/disabled/ambiguous RLS, incompatible
Users key and pre-existing unreceipted AI objects/roles. Matching receipts permit a no-op rerun. Ordered
catalog hashing covers columns/types/nullability/computed definitions, modules/owners, constraints and FK
columns/trust, indexes, predicates/enabled state, direct grants, trigger state, role ownership and the strict
predicate definition. A separate source receipt pins this installer revision. Drift stops for review; do not
replace receipts to make a changed schema pass. Compatible future changes need a new reviewed migration.

No historical migration is edited. No audit/history row is rewritten or deleted. Existing audit constraints
are parsed by actual column dependency, with exactly one fully recognized positive allowlist required;
mixed-column/unrelated restrictions remain unchanged. Unknown grammar or ambiguity aborts the whole install.

## Runtime permissions and tenant isolation

All nine tenant tables have non-null `ShaleClientId`, strict FILTER and AFTER INSERT/UPDATE BLOCK predicates
through existing `sec.fn_FilterByTenant`, never the tenant/global overlay. Composite, trusted, non-cascading
FKs bind tenant/integration/user/grant/session/key. Immutable-owner triggers reject owner/identity changes and
hard deletion, including privileged mistakes. Mutable admin entities carry RowVer; consent consumption and
quota release use conditional transitions. Grant scopes and ownership never expand in place.

Three dbo-owned roles are created **without members**:

- `AiIntegrationRuntime`: EXECUTE only on the bounded tenant control, consent, verification and quota modules.
- `AiIntegrationBootstrap`: EXECUTE only on the two confined provisional resolvers.
- `AiCapabilityOperator`: EXECUTE only on global inspect/change. Change additionally checks actual role
  membership and requires NULL context keys; a tenant administrator receives no global authority.

All three receive explicit SELECT/INSERT/UPDATE/DELETE/ALTER/CONTROL denials on the new tables. Tenant modules
execute as caller, retain RLS, derive both identities from context, and recheck current active tenant/user and
`Users.is_admin` for admin actions. Same-owner static module access supplies only the required operations;
ordinary callers cannot use it for direct DML, scope changes, revocation restoration or table reads. No role
membership, old auth-pool grant, ShaleAdmins membership or runtime impersonation is installed. Existing
deployed principal membership/effective permissions still require independent operator review before use.

Only `ResolveAiClientV1` and `ResolveAiCredentialV1` execute as owner, following existing V1/V2 confinement.
They accept bounded exact issuer/resource plus client ID or 32-byte digest, return only provisional owner
references, have no dynamic SQL or Case/User/password projection, and restore caller identity automatically.
There is no token validation endpoint. Their output cannot construct a verified invocation. Later OAuth must
use a new runtime lease stamped by the existing production initializer, then validate the entire live tuple,
credential scope/expiry/issuer/resource and ordinary operation authority. No business query uses this pool.

## Backend boundaries and lifecycle

`AiIntegrationSecurityService` takes the existing authenticated `DbSessionProvider`; it accepts no tenant/user
arguments. Configured issuer/resource are constructor inputs, not consent/tool inputs. Its default constructor
has startup capability false. An explicitly configured startup capability can only further deny the shared
SQL switches. The backend has tenant policy inspect/change, register, ceiling change/permanent revoke,
bounded registration/key inspection, pinned RSA public-key rotation/revoke, self-consent/revoke, session
revoke, live session context construction and quota reserve/release. It is deliberately not a Spring route
or public protocol component. `AiProductCapabilityService` requires the separate restricted operator provider.

Registration is immutable to one tenant, expires within 90 days, and has one random public integration UUID
and a distinct client ID. Redirects are one to five exact typed values. Public keys store only RSA modulus,
exponent and SHA-256 fingerprint; no private key or client secret can be represented. The pilot provisioner
accepts RSA 2048–4096 with exponent 65537 and RS256. At most two unexpired unrevoked keys may overlap;
rotation clips the previous key to 24 hours, increments registration RowVer and rejects competing stale
rotations. Emergency revoke is immediate at the live gate. Inspection returns references/state, not secrets.

Only two closed scope bits exist: 1 = `cases.search`, 2 = `cases.overview.read`; 0 is an empty ceiling.
Consent/session/credential snapshots require 1–3. Live intersection is ceiling AND grant AND session;
credential scope intersection belongs to 3B.2 token validation. Reducing a ceiling also permanently revokes
affected grants, so a later increase cannot silently restore previously removed consent.

Consent uses a server-generated 256-bit single-use intent, hash-only in SQL, bound to current authenticated
tenant/user/integration/scopes, at most five minutes to accept. Acceptance rechecks every gate and creates
only the same user's grant, expiring within 24 hours. Failed audit rolls back consumption and grant together.
This is an internal self-consent/CSRF foundation, **not an OAuth authorization transaction or endpoint**.
3B.2 must bind its maintained AS transaction, exact redirect/resource, PKCE and browser CSRF state before
exposing consent. Admins can revoke but cannot manufacture consent for another user.

Tenant disable revokes grants/sessions and consumes outstanding intents atomically. Reenable never restores
them. Integration/key/grant/session revocation and all expiries are checked durably. First revocation time is
retained; repeated revoke with the current RowVer is a no-op, while a stale RowVer fails. Active user/tenant
checks deny disabled/removed owners immediately. The future credential issuer must clip all deadlines and
connect password-reset/security lifecycle family revocation before issuing any credentials; existing ordinary
authentication is unchanged in this increment. No session/token issuance API exists here.

`AiDelegatedSession` is a separate family with grant/integration/tenant/user/key, scope snapshot, absolute
expiry, idle expiry, refresh generation, revocation and RowVer. `AiOAuthCredential` stores only unique SHA-256
digests and ACCESS/REFRESH snapshots, expiry/consume/revoke/generation. A unique refresh-generation index
supports later conditional rotation. It issues nothing. CODE/PKCE/assertion replay and maintained-AS
transaction storage are intentionally 3B.2 work, avoiding premature duplicate OAuth conventions.

`VerifiedIntegrationContext` has internal construction, immutable identities/scopes, operation, server-random
invocation/correlation IDs and a redacted string form. It is distinct from ServerPrincipal and carries no
bearer, email or mutable servlet state. It is a verified **snapshot**, not authority to disclose Case data;
3B.3 must add versions/deadline/reservation and the final disclosure gate at the existing shared worker.

## Quota admission and race boundary

Tenant modules take a transaction-owned shared `AiCapabilityV1` lock then an `AiTenantV1:<tenant>` lock.
Mutations/admission take the tenant lock exclusively; verification/inspection take it shared. Global change
takes the conflicting exclusive capability lock. Actor rows are held while the transaction completes.
This deliberately serializes the three quota dimensions by tenant, sufficient for the small pilot and shared
across replicas/processes; there are no process counters or positive authorization caches.

Committed reservations enforce exact trailing 24-hour row budgets 200/user, 500/integration, 1000/tenant;
sliding 60-second call budgets 10/20/60; and active lease concurrency 1/2/5. Search reserves ten rows and
Overview one, before future data work. A unique server invocation ID prevents duplicate accounting.
Quota rejections are durable zero-row-cost call attempts. Successful admission consumes worst-case rows even
on empty/error/uncertain completion. Release only ends concurrency; it never refunds rows/calls. A crash lease
expires within 15 seconds. No cleanup/purge API is added. Owner/time index supports all three aggregates;
history is retained through restart. Future adapters must route every verified attempt through admission,
add pre-auth/discovery/process limits and withhold output on lease/deadline loss. No data-work queue exists.

These locks protect the new control/admission transactions, not a future network disclosure. Final handoff,
async cancellation and shared Case worker auditing remain 3B.3–3B.6 work. Do not claim in-flight byte recall.

## Audit compatibility and rollback

Security mutations append to existing `EntityActionAuditLog` **inside the owning SQL transaction** with tenant,
current actor, stable numeric target, database UTC, closed action, server correlation UUID and fixed service
source. Types are `TENANT_AI_POLICY`, `AI_INTEGRATION`, `AI_CLIENT_KEY`, `AI_DELEGATION_GRANT`,
`AI_DELEGATED_SESSION`; actions reuse CREATED/UPDATED/DEACTIVATED. Metadata is only ACTIVE boolean and/or
AI_SCOPE_MASK 0–3. Global enable/disable uses `GlobalControlPlaneAuditLog` with the actual operator and UUID
correlation metadata. Missing/failed/zero-row audit aborts the mutation. The JDBC boundary drains procedure
completion and closes its lease before returning, withholding results on commit acknowledgement/close errors.

Bounded non-PHI configuration inspection, eligibility checks, consent-intent preparation and individual quota
facts intentionally have no entity-action row. Intents/reservations have intrinsic lifecycle records; accepting
consent is audited. No prompts, PHI, names, redirect/key material, secret/digest/token, SQL, exceptions or
arbitrary client metadata are logged. Future integration access/disclosure auditing remains 3B.3.

Rollback is **operational disable and code rollback with retained additive storage**, not dropping tables or
deleting revocation/audit facts. Keep startup off, use the restricted audited product switch to turn global off,
then authorized tenant services to disable any explicitly configured tenants/revoke families. Remove future
route composition if ever separately installed. This increment installs no route. Retain all tables, receipts,
audits, revocations and quota history; do not restore old grants, receipts or authentication permissions. A failed
installation rolls back its entire transaction. No destructive down migration or history deletion is provided.

## Local synthetic verification

Use only a **new dedicated loopback-only disposable SQL Server**, never Azure or production. The focused JUnit
fixture refuses execution without explicit opt-in and builds nonce-owned databases plus separate SQL logins
for setup, runtime, bootstrap, operator and a test-only RLS verifier. It drops only exact databases/logins it
created. `synthetic-prerequisites.sql` is clearly synthetic; it is not an approved Case artifact.

```bash
# Supply the generated disposable SA password privately in SHALE_AI_SQL_PASSWORD; never in argv/logs.
SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI SHALE_AI_SQL_PORT=14337 \
  mvn -pl shale-data -am \
  -Dtest=AiScopeTest,AiSecurityContractTest,AiSecurityLocalSqlServerTest,EntityActionAuditEventTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`SHALE_AI_SQL_PASSWORD` is read only for this explicit local fixture. Host is hardcoded `127.0.0.1`; the port
is validated, login timeout bounded, and self-signed TLS trust is confined to this synthetic fixture. Normal
runtime TLS configuration is unchanged. Tests prove actual per-table FILTER/BLOCK and effective ordinary
runtime denials, context reuse, conditional authority, audit failure, quota races/owner dimensions and a
replacement JVM observing committed SQL accounting. Injected failures are labelled as such. Executed results
and remaining production/authoritative gates belong to the canonical implementation record.
