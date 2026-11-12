# Universal MCP PR A — authorization and credential integration

Baseline: `38281e8e087ffa645add9b5bd8846d03ce359ce6` (`codex/latest`).
Development direction approved by the owner on November 11, 2026. CIMD stays OFF.
This is a review candidate, not deployment or activation authorization.

## Security contract identified before implementation

The existing `phase3b7-sql-confinement-contract.md` requires a distinct procedure-only
AI Case identity, bearer-equivalent digest proof at every SQL phase, strict tenant
predicates/RLS, quotas, transactional PHI/access audits, and data-free single-use final
handoff. Its startup/rebuild/rotation section requires actual identity and full catalog
verification. `AiCaseDataAttestation` additionally states "before readiness, each request
and any pool rebuild". `McpAiCaseData` implements this in its constructor and in
`authenticate` through `ready(deadline)`. Its reader does not reattest every lease.
The merged registry supplement runs with that existing attestation and must remain.

PR A does not relocate or remove any of those existing checks. Historical SQL modules,
verifiers, packaged resources, receipts, deployment bindings and the manual HTTP
composition are unchanged. The existing startup and admission behavior is retained.

The unmerged #1950/#1951 proposals require a complete future runtime attestor but do not
provide a serving runtime. #1952's nine pools and exhaustive per-lease attestation,
#1953's certificate-signed catalog gate and #1954's suggested catalog fence are not
adopted contracts. No such infrastructure is incorporated here.

## Proposed new CIMD composition boundary — for review

Reuse the separate proof/credential namespace and OAuth persistence adapter from
#1950/#1951. New credentials must never be admitted by legacy SQL roots. The original
integration remains SUSPENDED and the delegated session's legacy TransactionId remains
NULL. Versioned Case procedures derive authority from the new proof namespace and
reuse frozen Case algorithms, quotas, audits and handoff. Java routing will select a
fixed namespace, never rewrite SQL through a test-only datasource.

Keep complete additive SQL installation/source/permission verification. Use independently
governed metadata visibility for startup and admission verification rather than
introducing server signing authority. New connection providers are supplied by
the composition root; PR A must not construct a nine-pool runtime or grant fallback.
An incomplete/unverified composition is not eligible for HTTP activation.

`AiCimdCaseData` is an explicit dormant library composition, with no Spring bean,
endpoint, feature switch or pool construction. It takes the unchanged predecessor
attestor and deadline-bound Case/reviewer providers from trusted application wiring.
Startup and each admission run the predecessor checks and complete additive proof/OAuth
checks once. Read, handoff and cleanup use the authenticated request's fixed namespace
without catalog reinspection. A detected integrity failure requires reconstruction;
an invalid/revoked token does not disable another tenant's valid credentials.

Existing and #1950/#1951 catalog fingerprints include grants on protected objects.
Direct SELECT grants on binding/receipt tables would violate those contracts.
`universal-pr-a-reviewer.sql` therefore adds only a new metadata role and
`AiCimdReadAuthorizationReceiptsV1`: a parameterless owner-executed SELECT over the
immutable binding and two immutable verification receipts. It cannot read Cases, users,
credential/code digests or audit payloads, mutate anything, or run arbitrary SQL. Its
exact source/owner/context is checked before execution. A statement-owned INSERT EXEC
transaction is permitted for these immutable metadata reads; it supplies no business
authority. Live business operations retain their original transaction rules.

The new reviewer role has exactly VIEW DEFINITION, SELECT on the system dependency
view, and EXECUTE on that accessor. It receives no table SELECT and is never the Case
identity. On boxed SQL its separately governed metadata login needs the same VIEW ANY
DEFINITION visibility already required of the historical reviewer; SQL Server 2022
also reports its implied SECURITY/PERFORMANCE DEFINITION permissions. There is no new
server certificate, impersonation grant, signing login, old-role grant or old receipt.
The installer creates no login/user/membership and rolls back on a late extra permission.
This is the minimal permission accommodation for the immutable predecessor contract,
subject to PR review.

The packaged catalog reuses the complete #1950/#1951 additive verification sections.
Only binding/receipt reads are substituted with the same immutable values returned by
the fixed accessor. Historical verification runs unchanged first. This avoids rerunning
embedded predecessor checks and operational inventories as a privileged owner. Every
module/source/permission/RLS/dependency/fingerprint comparison is retained; there is no
positive authorization cache or catalog-change fence.

Authorized database administrators and deployment operators are trusted to preserve
reviewed modules, grants, RLS, audit dependencies and release artifacts. They must drain
serving instances before security-affecting SQL changes and verify before restoring
readiness. Unexpected drift discovered by existing checks withdraws readiness.
There is no claim of continuous protection against unrestricted privileged SQL mutation
between checks. Fresh business proof is not a catalog-change fence. The #1954 column-
grant counterexample remains valid and must not be relabeled as a passing integrity
test. This explicitly rejects the stronger per-lease draft model without weakening
the existing startup/admission contract.

## Minimal implementation and reuse

Retain the additive #1950 proof schema and #1951 OAuth schema where the frozen historical
contracts prevent safe reuse of legacy tables/procedures. Their generated catalog
checks are installation/readiness tools, not a new per-lease framework. Keep readable
templates and generators so the independently versioned changes are reviewable.
Do not add new pools, signatures, grants on legacy roles, or capability advertisement.

Reuse the maintained browser adapter through `AiOAuthBrowserStore`, keeping the default
manual adapter. Preserve exact external identity, publication/approval revision and
ceiling, original WEB session, explicit consent, S256, code consume, client method/key,
refresh rotation/replay, known/unknown revocation and cross-replica assertion replay.
Live SQL checks continue at consent, exchange, refresh, admission, read and handoff.

## Pre-edit test-impact inventory

* `AiOAuthTransactionStore`, `DurableOAuthAuthorization`, `AiOAuthBrowserStore`:
  OAuthAuthorizationFlowTest, OAuthBrowserFilterTest, ShaleOAuthHumanTest,
  PinnedClientAssertionTest, AiSecurityContractTest and CimdBrowserBindingTest.
* Fixed namespace routing in `AiCaseDataAccess`/`AiCaseDataRequest`: AiCaseDataAccessTest,
  IntegrationCaseReadTest, AiCaseDataLocalSqlServerTest, selected CIMD proof/OAuth SQL
  tests. Legacy constructor/call sites must retain their original procedure names.
* New bindings and SQL lifecycle: AiCimdProofLocalSqlServerTest,
  AiCimdOAuthLocalSqlServerTest and their V1 variants; generation checks; registry and
  historical receipt/binding regression checks. Select methods rather than inherited
  broad historical SQL campaigns.
* Verification placement: McpAiCaseData and AiCaseDataAttestation remain unchanged;
  the SQL-inspection StackOverflow regression fix from #1949 is retained without
  weakening its Azure dispatch assertions.
* `AiCimdCaseData` and the metadata accessor: selected methods of
  AiCimdAuthorizationLocalSqlServerTest and its V1 variant cover complete deadline-safe
  disclosure, post-read revocation, additive drift, OFF controls, forbidden metadata/
  business reads and atomic installer rollback. Existing Case admission code is unchanged.

## Audit compatibility

Existing EntityActionAuditLog owns grant, integration and delegated-session mutations;
GlobalControlPlaneAuditLog owns application publication; existing PHI audit plus
IntegrationAccessAuditLog/Resource own sensitive Case reads. Audits commit on the same
SQL connection/transaction; faults roll back authoritative mutations or read receipts.
No new audit schema/vocabulary is needed. No token/digest/assertion, callback, state,
metadata body or PHI payload is logged as metadata. Data-free proof/catalog checks and
public metadata fetching create no fabricated tenant audit event. A neutral assertion
replay ledger is not a tenant-admin event. No administrative inventory UI is in PR A.

## Acceptance and limitations

Completion requires real restricted-login SQL OAuth consent/code/S256 issuance,
refresh/revocation and actual audited Case bytes inside the original request deadline,
including admission verification, read, encoding and handoff. Preserve two-tenant
isolation, stale-publication/approval denial at read and final handoff, legacy namespace
exclusion, manual continuity and failed-audit rollback. No renewed budget after
admission, unit-only acceptance, owner-seeded family, or skipped SQL test counts as
completion. Native Azure and stock-client HTTP/browser acceptance remain separate.

The private hash-gated authoritative schema is required for real Case acceptance:
SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
It was absent from the initial workspace and prior attachment path. The owner reattached
the unchanged file; its hash was verified before use at
`/workspace/attachments/8f9cee9b-4e28-4608-af20-77e7af188279/authoritative-candidate.sql`.
Do not commit, reconstruct or substitute the fixture. Results are recorded separately
in `docs/testing/universal-mcp-pr-a-verification.md`.
