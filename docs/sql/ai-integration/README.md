# Phase 3B.1 integration security candidate

This directory owns the additive SQL candidate and operator instructions, not another AI roadmap.
The [canonical contract and implementation record](../../architecture/ai-integration-and-mcp.md#1013-phase-3b1-integration-persistence-and-security-foundation)
owns status and evidence. Nothing here authorizes production execution or enablement.

Azure SQL Database successor: [platform and contained-principal contract](../../architecture/azure-sql-case-attestation.md),
[acceptance status](../../testing/azure-sql-case-attestation.md). Server metadata prerequisites below
apply to SQL Server/MI. Azure uses contained SQL users and database-scoped evidence; it must not
receive broader server/control grants to satisfy the old model. The read-only Stage 0 dispatcher is
`stage0-platform-verify.sql`; no historical migration or receipt is changed.

## Phase 3B.7 confined Case data candidate

The approved [confinement contract](../../architecture/phase3b7-sql-confinement-contract.md) is implemented
by additive `phase3b7-confinement.sql`, after `phase3b3.sql`. Historical migrations and receipts are
unchanged. The earlier [review](../../testing/phase3b7-sql-confinement-review.md) describes the vulnerable
shared-runtime baseline; it is historical evidence, not the new execution boundary. This candidate does
not complete Phase 3B.7 or resume its broader OPEN acceptance ledger.

Apply manually as dbo in a fresh autocommit session with both tenant/user context keys NULL, only in a
separately approved disposable database. No application startup installs SQL or grants permissions.
The migration refuses incompatible authoritative Case/RLS shape, altered historical dependencies,
unsafe TRUSTWORTHY/cross-database chaining, unreceipted new objects, or receipt drift. A matching rerun
is a no-op. `verify.sql` selects the latest installed cumulative verifier; the standalone
`phase3b7-confinement-verify.sql` is read-only and identical to the packaged startup catalog verifier.
Never overwrite receipts to approve drift.

The source schema used for local proof is the supplied `shale-schema-source.sql`, SHA-256
`4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`.
It is external evidence and must not be edited or committed. The candidate retains its authoritative
Case definitions and the reviewed Users/auditLog strict and Statuses overlay predicates. The migration
pins their portable structural catalog independently of database object IDs. A different catalog needs
review and a new additive migration, not edits to historical SQL.

| Identity | Membership / effective application authority |
| --- | --- |
| AI Case application login | Only own SQL user, `public`, `AiCaseDataRuntime`; six object EXECUTEs below. No other role, ownership, delegation, impersonation, cross-database route, direct table or column access. Unavoidable SQL public connection/encryption-key-definition metadata rights and public CONNECT on the four non-admin default TSQL endpoints are explicitly bounded. |
| `AiCaseDataRuntime` | EXECUTE only `AiDataAuthenticateV1`, `AiDataSearchCasesV1`, `AiDataGetCaseOverviewV1`, `AiDataRecordCaseFailureV1`, `AiDataFinishCaseQuotaV1`, `AiDataAuthorizeCaseHandoffV1`. Explicit direct-table and 42 legacy-procedure denials. No wildcard EXECUTE. |
| Catalog verifier | Separate governed login in empty `AiCaseDataCatalogVerifier`: VIEW DEFINITION, SELECT on `sys.sql_expression_dependencies` and the four noncredential deployment/Unicode reference tables. No Case/PHI/control/credential access. Needs separately provisioned server VIEW ANY DEFINITION and VIEW ANY DATABASE; startup checks specific server grants and cross-database AI access. Never give this role to the AI application login. |
| Ordinary runtime/auth/bootstrap/operator | Existing separately governed identities and grants. Never a fallback for protected MCP Case reads. |

Provision logins/users and role membership separately; the migration creates empty roles and no login.
The application login must not own schemas, objects, roles or modules. Exclude direct or inherited
`AiIntegrationRuntime`, `AiIntegrationBootstrap`, `AiCapabilityOperator`, `ShaleAdmins`, SQL fixed
reader/writer/owner/admin roles, and all equivalent custom/bypass authority. Review actual login tokens
and effective permissions, including public, column grants, views/functions/synonyms and server grants.
A direct SQL permission denial is required for the two #1919 attacks; a rolled-back successful statement
is a failure.

Enabled isolated test composition requires these independent properties (or the documented AI pool
environment aliases in `Config`):

```properties
shale.ai.data.jdbc-url=jdbc:sqlserver://<host>:1433;databaseName=<expected-database>
shale.ai.data.username=<restricted-ai-login>
shale.ai.data.password=<secret>
shale.ai.data.database=<expected-database>
shale.ai.data.verifier.jdbc-url=jdbc:sqlserver://<host>:1433;databaseName=<expected-database>
shale.ai.data.verifier.username=<separate-catalog-login>
shale.ai.data.verifier.password=<secret>
```

Omit TLS/authentication/timeout/MARS options from these URLs: protected settings cannot be overridden.
The AI pool requires encrypted certificate-validated SQL, uses independent `ai-case-data-pool`
metrics/lifecycle and never replaces ordinary pools. Its missing/partial configuration prevents handler
activation. Packaged OFF mode requires none of these properties. Actual self-signed loopback tests use
explicit test-owned datasource objects and still run the complete live identity/catalog attestation.

Separately governed provisioning must insert exactly one `AiCaseDataDeployment` row, Id=1,
Contract=`case-read-v1`, canonical Issuer/Resource, the existing JVM local JavaTimeZone and corresponding
SQL TimeZone. The table starts empty and cannot be changed/deleted after provisioning; configuration
changes require drained pools and a reviewed additive migration. It contains no tenant enablement.
Local fixtures use UTC. Startup checks live SIDs against ordinary/auth/bootstrap identities, database,
exact tokens and permissions, source/catalog/module receipts, call graph, owners, predicates/triggers,
reference data and SQL/Java wall-time mapping. Failure removes readiness; no unchecked file attestation.
Pool rotation must drain AI leases and reattest before accepting work; each protected request rechecks.

`ProofDigest` retains a defensive mutable digest copy outside `VerifiedIntegrationContext`, exposes only
scoped JDBC binary binding, clears bindings and request storage in finally, and survives cancellation
through bounded failure/quota cleanup. Never log it, serialize it, add it to MDC/DTOs/audits, or enable
JDBC parameter tracing, APM bind capture, RPC parameter capture or heap/debug capture in this boundary.
Application JDBC JUL and SLF4J payload logging are suppressed; privileged diagnostics remain an
operational trust boundary. Raw ACCESS is never sent to SQL and stored digests are never returned.

Only the tiny private locator runs as owner to locate retained immutable digest lineage through RLS.
It returns provisional IDs to caller-executed static same-owner modules. It is not executable by the AI
login. All other modules retain non-bypass caller execution; static ownership chaining permits only the
reviewed body. Derived context is overwritten/read back/restored as defense in depth; connection faults
physically discard the lease. Context alone authorizes no protected operation.

Search/Overview each commit durable quota admission separately, then atomically commit minimized read,
required success audits and invocation READ state. Overview alone appends existing PHI history in that
read transaction. Failures preserve committed charges, audit only retained proof-bound history, and
release concurrency without refund. A fresh data-free one-shot final gate reauthenticates after all
locks, releases quota tentatively, samples database UTC after housekeeping waits, and commits only a
positive conservative remaining-life receipt. Java consumes query/commit/drain/close time from that
receipt before the private buffer handoff; no SQL transaction spans network output.

Regenerate reviewed SQL/resources with JDK 21 on PATH:

```bash
python3 build/sql-confinement/generate.py
```

Generated Unicode ranges/lowercase/word-break tables implement the supported JDK Locale.ROOT contract;
changes require executable DAO/SQL parity, not source comparison. `AiCaseDataLocalSqlServerTest` requires
explicit disposable loopback opt-in and the hash-gated authoritative candidate. Independent clients use
`AuthoritativeCaseMcpLocalSqlServerTest`, maintained OAuth issuance and the actual restricted AI login.
Skipped tests are not acceptance evidence.

Rollback is containment: turn handler/transport flags OFF, revoke the dedicated login's connect and six
EXECUTEs, drain/close the AI pool, retain invocation/accounting/audits and receipts for investigation.
Do not enable shared-runtime fallback, drop history, restore broad grants or automatically uninstall
SQL. Ordinary Shale behavior continues through its original identities. No production/Azure SQL,
real tenant/integration enablement, deployment or installer/version publication is authorized here.

See the [scoped implementation evidence](../../testing/phase3b7-sql-confinement-implementation.md) for
actual restricted-login denials, parity, audit/expiry/client results and the exact verification limits.

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

These are source dependencies and local catalog checks, not claims about a deployed database. The **Case**
artifact was recovered in the [resumed 3B.7 acceptance](../../testing/phase3b7-adversarial-acceptance.md#resumed-authoritative-acceptance--recovered-input-2026-10-24);
runtime permission/serving expiry acceptance fails. No Case object is a foundation dependency here.

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

## Phase 3B.2C.2 opaque credential lifecycle candidate

Apply `phase3b2c2.sql` after the receipted 3B.1 → 3B.2B → 3B.2C.1 candidates, only with
separately approved migration authority in a fresh dbo session with both context keys NULL.
This task executes disposable loopback SQL only, never production/Azure SQL. Existing installations
apply only unapplied migrations in that order: historical receipts intentionally reject a catalog
changed by a later migration. Repeat the latest migration for its own source/catalog no-op check;
do not overwrite old receipts or rerun historical installers against the upgraded catalog.

No parallel token table is added. `AiDelegatedSession` gains nullable, immutable authorization lineage,
exact client/issuer/resource, consent timestamp and global capability revision. Nullable columns preserve
older foundation fixtures; newly issued families require the complete tuple. A unique transaction index
prevents two families from one validated exchange. A tenant-qualified authorization FK binds grant,
integration and user. Existing strict FILTER/INSERT BLOCK/UPDATE BLOCK, direct table denials and
hash uniqueness remain authoritative. `AiOAuthCredential` binds tenant/user/integration/grant/session,
kind, scopes, generation and UTC issue/expiry/consume/revoke. Immutable session lineage supplies exact
client/issuer/resource/key and consent authority without duplicate mutable claims. Generation zero is
the initial refresh; each subsequent generation uniquely identifies its parent and replacement within
that same family. Consumed rows are never deleted by rotation, including expired replay evidence.

`ResolveAiRefreshV1` alone adds confined owner bootstrap EXECUTE. It accepts one exact REFRESH digest,
client and configured issuer/resource, including consumed/expired history to permit reuse detection;
it returns only tenant/user. ACCESS records cannot resolve through this procedure. There is no access-token
presentation, bearer validation, tenant initialization from an access token or resource authentication.
Runtime gains only bounded issuance/refresh EXECUTE; direct reads/DML and general auth-pool grants do not expand.
The cumulative receipt covers old/new tables, modules, triggers, constraints, indexes, permissions and RLS.

The internal issuer takes only `ValidatedExchange`; the HTTP provider first proves PKCE/client authentication
and creates that receipt through the existing exchange. A fresh JDBC transaction retains the global/tenant
locks through code/JTI consumption, live lineage checks, family/token insertion and required audit. The
exchange procedure commits only when it owns the transaction; the composed path commits after issuance.
Any failure rolls back all of it. Raw credentials are withheld until commit acknowledgement and lease close.
SQL never receives raw access/refresh values: Java creates independent 32-byte CSPRNG values and supplies
SHA-256 digests only. Clients receive opaque 43-character unpadded base64url, with no claims or signing keys.
Access is at most five minutes; refresh idle is at most eight hours. Absolute expiry is fixed at issuance,
no later than consent+24 hours, and clipped to grant, registration, original WEB-session and pinned-key expiry.
Every refresh recomputes the earliest live deadline; it never extends session absolute expiry. Current 3B.2B
consent grants are deliberately one hour, so those grants normally shorten these maximum lifetimes.

Refresh uses the same public `none` / confidential pinned RS256 `private_key_jwt` profile. It requires exact
client/resource, no requested scopes, fresh confidential assertion/JTI and the original authenticating key.
A replacement key never inherits existing families. Shared capability/tenant locks, held authority rows,
conditional consume and unique generation enforce one winner. Rotation inserts one ACCESS and one REFRESH,
consumes the previous REFRESH and updates last refresh/idle/generation in one audited transaction.

**No retry grace:** after successful rotation, retrying the consumed token (including when the response
was lost) revokes the complete family. The SQL procedure commits revocation and audit before returning
a denial marker; Java converts that committed marker into `invalid_grant`. Throwing inside that transaction
would undo revocation and is forbidden. The client must obtain fresh authorization/consent. The concurrent
loser is reuse, so even the winner's newly created credentials become revoked. Consumed-history retention
allows detection after restart, across replicas and after the consumed credential's expiry.

Session-revoke triggers mark every family credential revoked. Grant/key/integration revocation and original
WEB-session revocation cascade through session to credentials; user delete/remove and tenant deactivate do
the same. UserDao's existing administrative password/security reset revokes original WEB sessions in its
own transaction, activating that cascade without changing ordinary authentication. Automatic security
revocations append bounded session DEACTIVATED audit in that same transaction. Actor context is used where
present; a system-originated cascade with no actor context records the affected user as the service subject.
First session revocation timestamp/reason and token timestamps are immutable. Global revision mismatch
permanently denies a pre-disable family after re-enable; its next authenticated refresh durably records
DISABLED family revocation. Existing internal session/quota gates also compare the recorded global revision
on lifecycle-bound families, so their trusted session references cannot restore pre-disable authority either.
The global switch's existing transaction-coupled control-plane audit records
the invalidating revision; there is no cross-tenant sweep or global tenant-data grant. Other live checks
remain mandatory even if a cascade already marked the family revoked. No positive authorization cache.

Initial creation/rotation/reuse use existing AI_DELEGATED_SESSION CREATED/UPDATED/DEACTIVATED audit and
AI_SCOPE_MASK/ACTIVE metadata, with no audit schema change. Audit failure rolls back the coupled state;
unknown malformed/wrong-client input has no new entity row because no verified subject exists. No token,
digest, code, verifier, assertion, JTI, key material, PHI, prompt or Case data is audited. 3B.3 remains separate.

RFC 8414 advertises code+refresh and none/private_key_jwt/RS256 only when exchange is explicitly enabled;
S256 remains mandatory. Default switches remain OFF. RFC 7009 revocation is deferred to the smallest
client-authenticated family-revocation follow-on before 3B.2D; no custom unauthenticated endpoint is added.
Existing authenticated internal session revocation remains usable. No token is accepted by a Shale data resource.

## RFC 7009 revocation closeout candidate

Apply `revocation.sql` only after the unchanged receipted 3B.1 → 3B.2B → 3B.2C.1 → 3B.2C.2
candidates, with separately approved migration authority in a fresh dbo session with both context keys
NULL. This is a manual additive candidate, not startup installation or authorization for production SQL.
It checks the cumulative credential catalog/source receipt, adds three confined procedures without new
tables or changes to historical modules, and records a cumulative catalog/source receipt. Identical repeat
is a no-op; drift stops for review. `verify.sql` checks the extension when installed.

With composition and exchange explicitly enabled, the maintained Spring Authorization Server endpoint
`POST /oauth/mcp/revoke` accepts singleton form fields `client_id`, `token`, optional `token_type_hint`,
and confidential assertion fields. No resource or caller tenant/user/integration/session authority is
accepted. Public clients use `none` with no secret. Confidential clients use a fresh pinned active RSA
RS256 `private_key_jwt`, exact issuer/subject client and audience `<configured issuer>/revoke`, at most
60 seconds, with the existing shared SQL JTI replay table. No Basic, secret, remote JWKS or other algorithm.
Revocation permits any currently active pinned key of the same client: reducing a family's authority does
not transfer issuance/refresh authority to that key. Refresh still requires the family's original key.

`ResolveAiRevocationV1` is bootstrap-only, requires NULL context keys, and accepts one exact locally
computed SHA-256 credential digest plus client/configured issuer/resource. It returns only provisional
tenant/user for an ACCESS or REFRESH family, including consumed/expired/revoked history. Unknown or foreign
credentials return the exact registration's creator solely as a configuration/revocation runtime subject.
There is no credential inventory or resource-token authentication. A fresh normally initialized runtime
lease then loads exactly that client's configuration through `AiLoadRevocationClientV1`. Unlike issuance,
this bounded configuration seam does not require an eligible human: inactive subjects must not turn known
versus unknown credentials into distinguishable client-authentication outcomes. It checks tenant activity,
subject membership, capability/policy and live registration/keys. It cannot authorize business access.

`AiRevokeOAuthCredentialV1` revalidates exact client/integration/tenant/configured identities, context-subject
membership, live registration and current key under existing global/tenant transaction locks and held rows.
Confidential replay reservation uses the existing shared `AiAssertionReplay` uniqueness and bounded cleanup,
even for unknown credentials. The lookup repeats all credential/session/user/grant/integration/client
bindings under caller RLS. Either credential kind revokes the entire delegated session/family; the existing
session trigger revokes all active access credentials and the entire refresh lineage. Consumed/expired history
remains stored. It neither requires an unexpired grant/token nor restores authority to an inactive subject.
Concurrent refresh/family activity uses the same locks; a committed revoke prevents later finalization.
First timestamp/reason is retained, with the existing SELF reason for client-requested family revocation.
Re-enable cannot resurrect descendants. No partial-access revocation model is introduced.

Authenticated known, unknown, malformed-presented, foreign and repeated revocations return empty HTTP 200;
hints are advisory, including unknown/mismatched hints. Missing/duplicate/unbounded form fields are
`invalid_request`; invalid client/proof or replay fails client authentication. SQL/audit failures withhold
success. Success has no credential/existence details, cookies or resource capability. Enabled RFC 8414
metadata adds only the revocation endpoint and its actual none/private_key_jwt/RS256 profile. Defaults OFF
continue to withhold these claims.

A newly revoked family appends exactly one existing `AI_DELEGATED_SESSION/DEACTIVATED` entity-action audit
with server-derived affected subject, numeric family row, service source, correlation UUID and ACTIVE=false.
This describes the delegated subject, not an assertion that a human initiated the client request. Revocation,
replay reservation and required audit commit together; an audit failure rolls all back. Repeated/no-op
requests add no entity row because no new family mutation occurred; confidential no-ops still retain replay
state. No token/digest/assertion/JTI/key, secret, prompt, PHI or Case data enters audit. No schema change;
3B.3 disclosure audit remains separate. Disable the endpoint/code on rollback and retain all history and
receipts; no destructive down migration or automatic tenant enablement.

Verification extends `AiOAuthRevocationContractTest`, `OAuthRevocationPolicyTest`,
`AiSecurityLocalSqlServerTest#revocation*` and `OAuthAuthorizationFlowTest`, using only dedicated loopback
SQL and distinct restricted identities. Replacement JVMs inspect persisted revocation and reject refresh
and confidential JTI replay. Expiry tests label owner-only timestamp injection. Results and remaining
acceptance limits are recorded in the canonical roadmap; skips are not acceptance.

## Phase 3B.2D internal ACCESS validation candidate

Apply `phase3b2d.sql` after `revocation.sql` with the same fresh dbo, NULL-context, manual migration
rules. No table or duplicate credential authority is introduced. The new cumulative source/catalog receipt
includes both new procedures and all previous objects; an identical latest repeat is a no-op and drift
fails before changes. Historical migrations remain unchanged. Run `verify.sql` after the accumulated
migrations; older receipt stages continue to cover their original object sets. Operational rollback disables
this internal service/code and retains history/receipts. Nothing enables a tenant or installs at startup.

`ResolveAiAccessV1` is bootstrap-only EXECUTE AS OWNER, one exact SHA-256 digest and configured
issuer/resource, requiring both context keys NULL. It accepts only unconsumed/unrevoked/unexpired ACCESS
with durable lifecycle lineage. It returns only tenant/user/integration/grant/session, ACCESS row ID,
OAuth client ID and optional authenticating key ID. No fallback registration owner, scopes, broad rows,
credential hash, refresh credential, secrets, User fields or Case data. Direct table denials and strict
RLS are unchanged; general auth and runtime identities receive no resolver permission.

`AiValidateAccessV1` is runtime-only, caller/RLS and tenant/user-context bound. A fresh production
RuntimeSessionService instance stamps both existing mutable context keys with bounded setup statements;
the adapter verifies exact tenant/user before invoking SQL. Failed stamps/verification/SQL/close withhold
the result and close the lease. There are no integration/grant SQL context keys or remembered pool identity.

Every gate takes existing capability/tenant locks, then held rows in fixed order global → tenant policy →
registration/integration → grant → delegated session → user. It rechecks the full durable ACCESS tuple,
including original consumed authorization/WEB lineage, exact client/key/issuer/resource, active tenant/user,
all revocations, capability epoch, expiry and current scope ceilings. Database UTC is sampled after lock
waits, without expiry skew. The deadline is the earliest live credential/session/idle/grant/registration/
WEB/key deadline. No positive cache. Effective scopes intersect credential/session/grant/registration,
prior snapshot ceiling and the two supported scopes. Existing reduction procedures permanently revoke
affected grant families; expansion/re-enable never restores those descendants. A recheck can only narrow
its input snapshot.

The immutable VerifiedIntegrationContext carries bounded IDs, OAuth client/key/credential reference,
configured issuer/resource, effective scopes, capability/policy/registration/grant revisions, clipped
deadline and server correlation. Admission does not invent an operation or invocation ID; existing trusted
session-control construction retains its operation identities and cannot pass the credential recheck.
All fields are final, scope sets copied, and toString redacted. No credential/digest/request object is retained.
Pass the context explicitly between tasks; cancellation/setup/close failure fails closed.

The internal `recheck` seam proves the short SQL gate and returns only after commit and successful close;
it performs no transport handoff. 3B.3 must compose the final buffered handoff boundary and mandatory
disclosure audit. No network transaction is held. No route/filter/bean accepts AI bearer tokens for data.
The unregistered header parser consumes only one Authorization header and the exact issued opaque syntax;
query/cookie/alternate sources never enter that API. All invalid credentials share one safe authentication
error without cause/ownership details. No fallback to WEB/DESKTOP.

Audit compatibility: this is a read-only authentication check with no data disclosure or domain mutation.
Existing entity-action allowlists do not represent authentication ALLOWED/DENIED outcomes. No ad-hoc audit
row or sensitive token/digest is emitted; lifecycle/security mutations remain transactionally audited by
their existing owners. Any future authentication-outcome event family needs a separately scoped bounded
schema/vocabulary review. IntegrationAccessAudit and Case PHI reads remain 3B.3 work.

Evidence and exact local verification are recorded in the canonical architecture §10.21. Disposable
synthetic SQL does not establish deployed permissions, authoritative Case RLS or independent client acceptance.

## Phase 3B.3 internal access audit and disclosure candidate

`phase3b3.sql` follows `phase3b2d.sql`. It is manual, additive, transactional, source/catalog receipted,
repeat-safe and fail-fast on drift or missing existing Overview PHI columns; it seeds/enables nothing. Run only the accumulated **unapplied** migrations
in order, followed by cumulative `verify.sql` and effective permission checks. Do not edit an old receipt or
run an old installer over a newer catalog as a repair. Rollback is capability OFF and family revocation;
retain all committed history and accounting. There is no purge, delete cascade, runtime installer or deployment.

The new `IntegrationAccessAuditLog` stores a bigint internal key, server UUID InvocationId/CorrelationId,
tenant/user/integration/grant/session/client references, database UTC, fixed MCP/case-read-v1, closed tool,
shared operation, outcome, reason and READ/TERMINAL stage, returned count and bounded search page/fixed size.
Unique `(InvocationId,Stage)` prevents duplicate stage writes. ALLOWED_READ means committed authorized server
access, never receipt by an AI or human. Security lifecycle mutations retain the already implemented bounded
entity-action events; this migration does not relabel them or add READ/FAILED to mutation vocabulary.

`IntegrationAccessAuditResource` contains only `(tenant,event,CaseId,RETURNED|REQUESTED)`. Its only resource
relationship is the non-cascading tenant-qualified parent FK; there is deliberately no Cases FK. The bounded
append procedure accepts ten typed Case slots and one mutually exclusive requested ID, validates cardinality
and checks parent/child persistence before returning its receipt. Search writes only returned IDs, including
zero children for an empty page; Overview writes one RETURNED ID. Failed syntactically valid Overview IDs
are REQUESTED without an existence lookup. Malformed IDs produce no child. Names, query/hash/filter, prompt,
DTO, token/digest/JTI, email, SQL/exception, URL and arbitrary JSON columns are absent.

Both tables use existing strict TenantFilter FILTER and AFTER INSERT/UPDATE BLOCK predicates. Runtime and
bootstrap roles have explicit SELECT/INSERT/UPDATE/DELETE/ALTER/CONTROL denials. Public receives no table or
procedure grant. The tenant modules retain caller/RLS execution and ownership chains; none executes as owner.
Append-only triggers reject UPDATE/DELETE even for a direct setup-owner attempt; the parent FK also blocks
parent deletion while children exist. No unrestricted runtime review or table INSERT is added.

New bounded runtime EXECUTEs are:

- `AiAppendCaseAccessV1`: one typed event and at most ten children in the caller's transaction. Retained
  owner-tuple validation permits failure history after disable/revoke; it does not authorize data reads.
- `AiGateCaseReadV1`: existing complete ACCESS lineage/policy checks, expected revisions and required scope,
  plus invocation deadline and live durable reservation, in the caller's transaction. Held authority order
  is global → tenant policy → integration → grant → session → user. Database UTC is sampled after all
  possible authority/reservation lock waits. No positive cache or connection-level integration identity.
- `AiOverviewPhiCountV1`: only the current tenant/actor/Case's exact existing Overview PHI-event count,
  locked within the owning transaction. Before/after delta must be one. This detects a suppressing trigger
  even if the driver reports one inserted row. It is not a PHI viewer or a provenance/linkage field.
- `AiAccountCaseAttemptV1`: records zero-row-cost rejected verified calls when admission has not already
  accounted the invocation; this includes invalid input and now-disabled owners. It does not refund credits.
- `AiFinishCaseQuotaV1`: releases only concurrency, including for now-disabled owners; consumed facts remain.
- `AiReviewCaseAccessV1`: active same-tenant admin revalidation, keyset cursor and at most 100 parents/1000
  typed resource rows. `IntegrationAccessReview` maps only the closed typed projection, rejects overbounds,
  and is independent of the known-unverified PHI viewer. No review UI or dump endpoint exists.

`IntegrationCaseReadService` is an explicitly composed internal service, default OFF and not registered as a
route/bean. It accepts only ACCESS-proven `VerifiedIntegrationContext`, creates immutable `IntegrationInvocation`,
and uses connection-scoped overloads on the existing CaseServicePort → adapter → production gateway →
CaseSummaryDao. Both ordinary and composed operations reuse `selectMinimized`; exactly one existing PHI
append is shared. Ordinary search remains exempt and ordinary Overview retains its own transaction.
The connection overloads are trusted application transaction seams, never credential authenticators or
new public endpoints; no protocol adapter may use them to skip the integration service.

Admission uses the existing independently committed durable reservation **before opening the read
transaction**. This intentionally prevents read rollback from refunding rows/calls. Costs are 10/1 regardless
of actual results. The 200/user, 500/integration, 1000/tenant trailing-day, sliding-minute and distributed
concurrency limits remain the 3B.1 authority, with a process maximum of five active operations. Per-borrow
initialization stamps both existing SQL keys. Bounded acquisition tasks can only borrow/stamp; timed-out
late leases close and cannot start protected work. No data-work queue or detached Case read exists.

The read transaction gates current authority, executes one existing operation, verifies/persists required
PHI and integration audits, gates again, commits and closes before encoding. Failed reads/audits/commit/close
withhold all protected output. After successful close, the encoder writes the closed minimized projection
into a bounded private sink (32 KiB search/8 KiB Overview); only one completed immutable read-only buffer is
eligible for handoff. Serialization/size/cancellation faults retain committed ALLOWED_READ and append a
separate terminal failure. No partial structured output is exposed. A fresh short transaction rechecks live
authority/reservation/deadline and completes concurrency accounting before commit/close and one internal
buffer return. Revoke committed first denies; authorization first may complete. No network I/O holds locks.

Failures append in a separate short transaction after read rollback/close. An uncertain commit requests
TERMINAL; SQL selects READ only if no committed READ exists, preserving unique stage history without
replaying or deleting anything. Failure-audit outage emits only `INTEGRATION_ACCESS_AUDIT_OUTAGE`; no raw
exception or sensitive emergency spool. Cancellation is temporarily cleared only for bounded cleanup/audit
and restored afterward. Explicit immutable contexts carry all authority; no servlet, ThreadLocal, AppState,
shared mutable RuntimeSessionService, or remembered integration/grant SQL key is introduced.

Verification receipts and remaining authoritative/deployed acceptance limits are in canonical roadmap
§10.22. Synthetic Case shapes in the opt-in fixture are explicitly not the unavailable authoritative Case
schema. No production/Azure SQL, external client acceptance, tenant/integration enablement or deployment.

Protected audit review intentionally adds no Case PHI success or entity-action event: it returns only
bounded history identifiers/outcomes, reads no Case projection and changes no administrative state.
A separate audit-review access vocabulary, if required later, must be reviewed rather than misusing the
current Case tools, PHI value fields or administrative instance-read enum.

## Production composition candidate and readiness

No migration changes accompany the [production composition candidate](../../testing/production-mcp-case-composition.md).
Its independent default-OFF `shale.ai.mcp.case-data-composition-enabled` control can instantiate
and attest the same restricted SQL boundary while OAuth/MCP/handlers/capabilities remain OFF.
Mandatory local production-profile acceptance passed, including the inherited-public-authority
regression for the catalog verifier. No migration changed. Exact membership/own grants alone are
insufficient: unexpected public application authority must also remove readiness. Credentials and
this setting do not grant deployment or pilot authorization; platform/legal/SDK/pilot gates remain OPEN. Follow the [staged preflight](../../production-mcp-deployment-preflight.md).
