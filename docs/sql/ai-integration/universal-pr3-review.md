# Universal MCP PR 3: registry and tenant approval review

Baseline: `85f8b9d9e43d0f7c991bd6bf608252a8c7bfeddc`, merged #1946 on `codex/latest`.
This is an additive, offline service/database foundation. No Spring bean, endpoint, advertisement,
automatic registration, configuration property or production feature is added.

## Persistence and authority

| Record | Identity and security contract |
| --- | --- |
| `AiExternalApplication` | Tenant-neutral UUID; exact issuer and CIMD URL, immutable resource/source; SHA-256 index plus **full binary byte comparison** on a hash match; current validated publication digest, supported-method mask, revision and operator disable state. Exact case/encoding is retained. No tenant/user/token data. |
| `AiExternalApplicationRevision` | Append-only publication history, keyed by application/revision. Digest covers validated metadata and public keys, supplied by the governed publisher. It does not contain private keys or raw metadata. |
| `AiTenantApplicationApproval` | Non-null tenant, application/revision FK, immutable requester, requested scopes, current ceiling, current actor, expiry, RowVer, terminal history and tenant-qualified integration FK. Strict existing FILTER and AFTER INSERT/UPDATE BLOCK predicates. One unclosed row per tenant/application. |
| Existing integration/grant/family | Approval creates a **SUSPENDED** registration with a new opaque internal client ID. No redirects, key, consent, grant, session, assertion or credential is created. Existing manual rows/IDs remain unchanged. PR 4 owns the exact external wire binding and activation path. |

The publisher has no tenant methods or tenant inventory. Its dedicated empty `AiApplicationPublisher`
role grants only `AiPublishApplicationV1`. The tenant service's separate empty `AiApplicationTenant`
role grants only `AiRequestApplicationV1` and `AiDecideApplicationV1`. Neither role grants direct old-table
access. Both roles and existing runtime/bootstrap/operator roles are denied direct reads/DML/ALTER/CONTROL
on all three new tables. No existing role receives a new EXECUTE, no login/user/member is provisioned,
and no new owner-executed module, pre-login resolver or bypass is introduced. Tenant modules retain caller
execution and same-owner static chaining, current `Users.is_admin`, active tenant and active user checks.
A registry writer cannot approve clients; an administrator cannot publish global identity metadata.

The provider must derive and stamp both context keys from authenticated Shale authority. There are no
caller tenant/user parameters in either Java tenant method. As in the established ordinary administrative
SQL boundary, mutable SESSION_CONTEXT is **not database authentication** against a stolen shared credential.
This PR does not change or weaken the independent proof-bound Case SQL boundary. Actual production identities,
public/inherited/column permissions, ownership and role memberships need governed catalog acceptance.

CIMD publication is a privileged validated-data seam, not a replacement for PR 2 fetching/parsing/SSRF
checks. The operator adapter supplies the combined metadata/public-key digest and method mask
(1 public, 2 private_key_jwt). The Java boundary enforces bounded HTTPS/no-query/no-fragment identities;
PR 4 must compose the complete PR 1/2 validated snapshot, exact identity and freshness policy.
Only CIMD is represented now; DCR creation and MANUAL conversion are absent. Every publication advances
revision, even for identical bytes, conservatively invalidating old approval eligibility. No published
identity or digest can establish approval or user consent.

## Transactions, lifecycle and races

All public procedures require a fresh autocommit lease. Tenant operations take the existing shared
capability then exclusive tenant application locks, and hold current actor membership through commit.
Publication uses a transaction-owned registry lock. Publication rows are held while checking their
revision during request/approval. Locks, unique indexes, exact RowVer checks and immutable ownership
apply across connections/processes; there is no positive authority cache.

A member may create a 24-hour PENDING request; this is not an OAuth intent and does not prolong one.
An active same-tenant administrator may APPROVE a nonempty subset of requested scopes for 1–90 days,
or DENY it. Approval rechecks global and tenant opt-in, current application revision/disable state,
request expiry and expected RowVer. Approval does not enable either policy. A ceiling can subsequently
only REDUCE; REVOKE closes it permanently. Denial/revocation history is never reopened or deleted.
Re-requesting creates a new approval ID and, if approved, a new suspended integration. Expired pending
requests must be denied and expired approvals revoked before replacement; no automatic renewal restores
consent. Disabled/revised publications cannot approve old requests. Approval expiry/global revision/live
user-policy eligibility must be checked again by PR 4 at every authorization/exchange/refresh/use seam.

Ceiling reduction permanently revokes affected grants, their sessions and credentials; revocation
revokes the whole same-tenant integration lineage. Pending integration consent intents are consumed.
Every update qualifies tenant and integration/grant joins explicitly. Later scope increases are rejected;
old revoked authority cannot reappear through re-request. Repeated decisions with old RowVer and decisions
on terminal records fail closed. On response loss, callers must reload/restart through future authenticated
management surfaces; this foundation never infers successful approval from a transport failure.

## Audit compatibility

The SQL transaction appends to existing `EntityActionAuditLog` before commit. A scoped additive allowlist
extension adds `AI_APPLICATION_APPROVAL`, preserving every recognized previous token and unrelated check;
ambiguous/unknown grammar fails the whole installation. CREATED records a request; UPDATED records each
administrator transition. Allowlisted metadata contains scope mask and closed `AI_APPROVAL_STATE`
(PENDING/APPROVED/DENIED/REVOKED). The existing Java event/reader vocabulary accepts those states and
rejects arbitrary strings. New suspended integration creation and later ceiling/revoke updates also append
the existing AI_INTEGRATION event. Descendant revocations are covered by that authoritative parent mutation,
as with the existing integration lifecycle seam.

Publication appends existing `GlobalControlPlaneAuditLog` as `AI_EXTERNAL_APPLICATION`, actual SQL operator,
numeric entity and correlation ID. It needs no new global audit table or tenant actor. Both global and tenant
zero-row/failed audit insert abort the business transaction. JDBC uses `AiSql` to drain completion and close
the lease before returning; existing completion/close-failure regression tests remain authoritative.

No URLs, names, metadata/key documents, digest, RowVer, session proof, callback, token, SQL or exception text
enters audit metadata. No PHI read occurs. There is deliberately no global registry inventory or tenant-admin
list/read API: sensitive administrative lists and their closed administrative-read discriminator belong to
PR 6. Pure input validation has no event; this PR performs no anonymous registration or fetch telemetry.

## Migration and compatibility review boundary

`universal-pr3.sql` is a one-shot transactional candidate. Pre-existing objects/roles abort; it never
reinstalls historical SQL or updates a historical receipt. Its own independent catalog receipt and read-only
`universal-pr3-verify.sql` cover the new objects, shared gate definitions, indexes/FKs/triggers, RLS,
permissions/column permissions, role owners, inherited role authority and audit vocabulary. Verify with full
catalog visibility. A matching verification is read-only; drift is never repaired by refreshing a receipt.
A second installation is rejected, rather than silently applying an incompatible revision.

**Production installation is intentionally blocked when `AiCaseDataDeployment` exists**, including when
that immutable table is empty. The guard isolates compatibility review from the historical confinement
contracts. Do not remove it as an operational workaround. The existing V1/V2 installers, dispatch/startup
verifiers, source receipts, binding files/table/trigger and issuer/resource/time-zone tuple are unchanged.
The historical foundation receipt still verifies after this extension in the synthetic fixture. That is
not evidence of full production V2 permission-graph or native Azure compatibility.

Before lifting that guard in a separately reviewed change, prove the new dbo-owned modules/roles and
static chains against the complete confinement dependency/effective-authority graph and both catalog
identities. Determine whether a versioned verification successor is necessary. If necessary, add it
separately with its own installation/verification and source receipt; do not edit/reissue old receipts,
expand bootstrap authority, or change the immutable binding. This PR supplies no waiver or successor
that pretends those checks ran. Existing production contracts remain authoritative.

## Exact production prerequisites and verification order

These are prerequisites for a future approved deployment, **not authorization to run SQL now**.

1. Resolve the compatibility guard above through explicit review and disposable authoritative-schema,
   SQL Server and native Azure acceptance. Confirm current installed versions with existing read-only
   verifiers. Do not replay the seven predecessor installers, completed Stage B/Phase 1R migrations,
   completed identity provisioning or archived investigations.
2. Preserve the accepted production identity verification, ordinary-client smoke and function provenance
   at their recorded scopes. The [readiness ledger](../../production-mcp-integration-readiness.md) still
   owns native Azure catalog/read and mutation/fault gates, V2 deployment, artifact/ingress parity,
   operator/legal/tenant approvals and feature OFF requirements. This extension closes none of them.
3. Resolve the independently governed [immutable binding prerequisites](../../production-mcp-deployment-binding-readiness.md),
   including actual running JVM zone evidence, without changing any existing binding value. Registry issuer
   and resource must match that approved binding exactly; configuration must not invent a new tuple.
4. Review a backup/restore and containment plan, deployment window, schema/audit-constraint locks and
   history retention/capacity. Validate recognized audit allowlist grammar and current tenant-qualified
   Users/integration keys, dbo/sec ownership and enabled strict policy on the actual target catalog.
5. Approve separately managed publisher and tenant control-plane identities. Use encrypted, certificate-
   validated independent pools; require no schema ownership, fixed admin/data roles, impersonation,
   grant options, broad database/schema permissions, public application grants or bootstrap expansion.
   Empty role creation does not provision credentials or establish least-privilege acceptance.
6. Only after those gates and explicit deployment approval, apply the reviewed additive successor then
   reviewed registry migration as dbo in a fresh session with both keys NULL, SQLCMD stop-on-error enabled.
   Run cumulative historical verification **and** independent registry verification before composing code.
   Require zero findings, exact effective EXECUTEs, all new table denials, three strict tenant predicates,
   trusted tenant-qualified FKs, enabled immutable triggers, unchanged binding/receipt values and zero
   unexpected new approvals/grants/credentials. Never use startup to install schema.
7. Run disposable two-tenant approval/deny/revoke/ceiling/race/expiry/audit-fault tests with real restricted
   logins, then manual-client and ordinary-client regression checks. No fault injections or tenant fixture
   writes belong in production. Verify only governed read-only production evidence. Deploying this code
   alone advertises and activates nothing; PR 4 must separately implement/test exact wire-ID binding,
   live approval/revision checks, original WEB session and explicit consent. DCR remains PR 5.

Rollback is containment: keep features OFF, withdraw only newly reviewed EXECUTEs/membership, drain any
future dedicated pools, retain registry revisions, approvals, revocations, audits and receipts. No destructive
down migration, broad runtime fallback, consent resurrection or receipt replacement is provided.

See [test inventory, exact commands and results](../../testing/universal-mcp-pr3-verification.md).
