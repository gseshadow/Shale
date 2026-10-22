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

## Phase 3B.2B authorization transaction candidate

Apply `phase3b2b.sql` only after the unchanged, receipted `phase3b1.sql` and existing durable
`UserSessions` prerequisite, in a fresh dbo session with both context keys NULL. It verifies the
foundation's catalog/source receipt before DDL. This is a separate manual additive candidate;
there is no startup installer, production execution, tenant opt-in or role membership change.
A repeat installation deliberately stops for review; it does not overwrite objects or receipts.

One strict tenant table, `AiAuthorizationTransaction`, extends the existing `AiConsentIntent` and
`AiDelegationGrant` persistence. It binds the original WEB SessionId (identifier only), immutable
tenant/user/integration/client/issuer/resource/redirect/S256 challenge, exact bounded client state,
requested/approved closed scope masks, registration RowVer, optional pinned-key ID, and transaction UUID.
State is limited to 512 UTF-16 units. Intent nonce and code are SHA-256 hash-only; no Java/Spring objects,
JWTs, verifiers, raw session tokens or arbitrary client metadata are serialized. New records have a
five-minute consent deadline; grants expire one hour from intent creation. Registration must have
more than one hour remaining at initiation. Code issuance uses database UTC, exactly 60 seconds, with
no expiry extension or retry grace. The migration preserves all old schema, procedures and receipts.

Seven bounded caller/RLS procedures load exactly one client under the authenticated human's tenant,
begin/find/approve/deny an intent, complete consent/code issuance, and consume a code. The existing
unique external ClientId establishes one owner. There is no general integration inventory or expanded
bootstrap/auth-pool permission. Fresh loading includes only registration identity/type/state/ceiling/
expiry, bounded consent display name, redirects and active pinned RSA public keys. Both profiles
require PKCE. Confidential authorization pins a currently live key reference; actual assertion signature,
exact token audience and shared JTI replay verification remain 3B.2C prerequisites. No assertion is
accepted or authenticated by this authorization endpoint; no remote JWKS resolution exists.

All transitions use existing `AiLockV1` capability/tenant locks, `AiActorV1`, `AiEnabledV1` and live
WEB-session checks. Completion holds actor/tenant rows, requires unchanged registration version,
current key, unconsumed intent and exact approved scope mask, and rejects a same-user/integration
revocation committed since intent creation. Approval writes only an intent decision. Final completion
atomically consumes that intent, inserts the existing delegation grant, appends its established
`AI_DELEGATION_GRANT/CREATED` audit, and records the hash-only code. An audit/commit/lease-close failure
withholds the callback. Denial records intrinsic durable intent/transaction history and creates no grant.
No accumulated Spring consent bypasses explicit approval on a later flow, including scope re-expansion.

Code consume checks exact client/issuer/resource/issued redirect and locally calculated S256 verifier
hash, live registration/key/grant/user/tenant/global state, expiry and consumed state under the same
SQL locks. Exactly one conditional transition wins. Its internal result is only a transaction/grant/scope
validation receipt. It creates no delegated session, access credential or refresh credential. This phase's
internal consume seam still requires trusted original WEB context; 3B.2C must introduce its reviewed
confined exchange lookup and client authentication before exposing token redemption to AI clients.

The table has strict existing TenantFilter FILTER/AFTER INSERT/AFTER UPDATE predicates, tenant-qualified
registration/user/grant/key FKs, unique intent/code hashes, immutable bindings and monotonic lifecycle
triggers. All existing AI roles are denied direct table access/DML/ALTER/CONTROL. Only runtime receives
EXECUTE on the new bounded procedures. No global resolver or auth pool receives new access.
Operational rollback is disable/route rollback with retained grant/code/revocation/audit history.

Audit uses the existing schema and closed metadata (`AI_SCOPE_MASK` only); grant creation and authorization
completion share one transaction. Intent approval/denial, issuance and consumption retain intrinsic bounded
security state, without adding a separate entity-action vocabulary for these rows. There are no sensitive
business reads or disclosure events to audit; Phase 3B.3 remains separate. No raw code, verifier, digest,
JWT, key material, redirect/state, prompts or Case data enters entity-action audit metadata.

Local verification extends `AiSecurityLocalSqlServerTest` (`#oauth*`) and the separately opt-in server
`OAuthAuthorizationFlowTest`. Both reuse only the clearly synthetic prerequisite fixture and loopback
SQL Server. The server fixture uses real restricted runtime SQL identity plus a synthetic WEB identity
store; existing authentication regressions independently verify the production session adapters. The
code-consume replay probe uses a replacement JVM and the same committed SQL record. Expiry tests
explicitly label owner-only timestamp fault injection; this is not elapsed-wall-clock or production evidence.

## Phase 3B.2C.1 exchange validation candidate

`phase3b2c1.sql` is a separate manual, additive, transactional candidate after unchanged
3B.1 and 3B.2B. It checks the foundation catalog/source receipt, exact 3B.2B procedure
source, ownership and enabled RLS before installation. Its own catalog/source receipts allow
an identical no-op repeat and stop on drift. Never change historical installers/receipts to
make a new candidate pass. No installation, role membership or tenant enablement runs at startup.

The only table added is strict tenant-owned `AiAssertionReplay`, with tenant/integration/key FK,
SHA-256 JTI digest, acceptance/expiry times, unique tenant/integration/key/digest and expiry index.
All three existing AI roles are denied direct reads/DML/ALTER/CONTROL. Runtime gets only
`AiExchangeOAuthCodeV1`; bootstrap gets only `ResolveAiOAuthCodeV1` in addition to its existing
confined modules. No auth-pool permission is expanded. Review effective deployed identity permissions
independently before any future activation. The new resolver executes as dbo owner, accepts only
exact code digest/client/configured issuer/resource with NULL context keys, and returns only
ShaleClientId/UserId. It cannot enumerate clients, users, grants, sessions or credentials. The Java
adapter opens a fresh lease through the existing runtime initializer, stamps both keys, and treats
that owner as provisional until full SQL authority validation.

The exchange procedure executes as caller, keeps RLS, takes existing shared global/exclusive tenant
transaction locks and holds live tenant/user/WEB-session/registration/key/grant authority. It checks
all immutable code bindings, registration version, S256, expiry and consume state. Confidential
exchanges must use the exact authorization-pinned key; rotation never silently substitutes another
key. Registration changes after issue fail closed and require new consent/code.

Replay reservation, conditional code consumption and existing `AI_DELEGATION_GRANT/UPDATED` audit
commit together. A duplicate JTI, failed verifier/binding/authority/expiry/audit or transaction failure
rolls back both. Failed validation does not permanently invalidate a code/assertion; successful validation
consumes both even though this phase deliberately returns an issuance-disabled error. Lost acknowledgement
or lease-close failure withholds the result; committed consumption remains permanent. Do not retry as
if that error were a successful token response: obtain a new authorization transaction.

JTI digests are retained through their exact assertion expiry (at most 60 seconds from iat), without
process-local replay authority. Each confidential exchange removes at most 256 expired rows in its own
tenant before inserting one. Inactive tenants may retain expired digests until the next exchange; they
never extend assertion validity. This is opportunistic bounded-batch cleanup, not a guaranteed timed purge.
There is no cross-tenant cleanup API or deletion of audit/code/revocation history.

Successful validation returns only immutable verified identities, scopes, issuer/resource, pinned-key
reference, transaction/grant and clipped absolute deadline/correlation. No delegated session or credential
row is inserted. Exchange audit uses only AI_SCOPE_MASK; audit append failure rolls back. Invalid attempts
have no additional entity-action row: no trusted owner/actor may exist, existing bounded code history
and replay facts supply security state, and raw inputs must never reach audit. Disclosure audit is 3B.3.

`shale.ai.oauth.exchange-enabled=false` remains explicit. Enabling validation also requires composition
and a **separate** `shale.ai.oauth.bootstrap.jdbc-url`, `.username`, `.password` SQL identity restricted to
confined bootstrap EXECUTE. Never substitute the ordinary auth/runtime identity. Defaults do not connect.
No runtime fallback exists if this configuration or SQL authority is unavailable. No token metadata is
advertised because validation cannot issue credentials. No production/Azure execution is authorized.
