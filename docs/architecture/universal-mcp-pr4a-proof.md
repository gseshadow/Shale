# PR 4A: versioned CIMD credential and Case proof boundary

Design baseline: `38281e8e` (`codex/latest`), stacked on draft #1949 (`a70bbc6f`).
This candidate is SQL-only and default OFF. No browser/OAuth issuer, metadata advertisement,
DCR, runtime pool, production installation or activation is included.

## Design reviewed before implementation

A CIMD family uses a separate immutable binding and separate credential table. Reuse the existing
`AiDelegatedSession` only as a tenant/grant/session lifecycle and audit anchor, with its historical
`TransactionId` **NULL**. Its new versioned family binding supplies the original WEB, consent and
consumed-code/S256 receipt. No CIMD code or credential is inserted into the legacy transaction or
credential table. The integration also remains legacy-SUSPENDED at Revision=1: new SQL does not activate
it. The new live loader requires that initial dormant anchor, so ordinary administrative modification
invalidates the new path, and the old OAuth client loader/consent cannot create an alternative family. Existing legacy proof locators require their historical authorization transaction
join and therefore cannot admit this family, independently of Java or token-string validation.

New versioned Case modules derive owner only from the new ACCESS digest, use a separate invocation
ledger, and reuse the reviewed Case operations, quotas, audits and final handoff algorithm. The
SQL live-authority loader additionally holds and checks the current application, full external ID,
publication revision/digest, current same-tenant approval identity/RowVer/scope/expiry, and exact family
binding. It runs at admission, each Case phase and final disclosure; invocation IDs never confer
identity. Publication changes block both tenants; revocation in one tenant leaves the other intact.

Original modules, roles, receipts, trigger/policy definitions and immutable binding values remain
unchanged. Add new namespaced modules/roles/receipts only. Existing V1/V2 and #1948 verification must
still pass. New source/catalog verification cannot refresh any receipt or substitute for original
verification. No new data role may call a legacy Case root or issuer; no existing role gains new
EXECUTEs. SQL Server and native Azure identity acceptance remain distinct.

Code issuance and refresh are deliberately not exposed by this foundation: a future versioned OAuth
exchange must create the family receipt/session/credentials atomically after original WEB consent,
S256/code verification, external client authentication and replay protection. Test fixtures seed this
receipt as owner only; that is not an issuer implementation. No secret arrives via Java-only eligibility.

## Pre-edit test-impact inventory

Reviewed `AiApplicationConfinementLocalSqlServerTest`/V2 and #1949's diagnostic, original Case
proof/lifecycle/SQL migration and attestation tests, `AiApplicationLocalSqlServerTest`, code/credential/
revocation contract tests, registry generation tests, V1/V2 module/catalog generation and SQL receipt
queries. New tests will inherit the hash-gated authoritative fixture and actual restricted identities;
new SQL uses verified existing Users/grant/session/registration keys and existing audit vocabulary.

| Contract | Test impact |
| --- | --- |
| New credential namespace and NULL legacy transaction | Old bootstrap and Case roots reject new proof; existing manual credentials still read Cases. |
| Versioned live loader and final disclosure | Current proof succeeds on own Cases; metadata revision, disablement, expired/revoked approval and scope changes deny admission and previously admitted final handoff. |
| Durable ownership / RLS | Two tenants share one exact external ID, but cannot substitute proof/receipt/invocation or update ownership. FILTER and INSERT/UPDATE BLOCK remain strict. |
| Revocation, lock order and audit transaction | Actual restricted lifecycle caller, concurrent decisions/handoff, stale RowVer and injected audit failure preserve atomicity and isolation. |
| New schema/modules/permissions/receipts | One-shot install, old receipts/binding unchanged, source/catalog drift and excess privilege fail closed; generation reproduces exactly. |

## Audit compatibility

The existing `AiDelegatedSession` anchor lets revocation use the existing `AI_DELEGATED_SESSION`
entity-action event with its real numeric session entity ID. Case reads retain existing PHI/access/
resource audits and quotas, on the Case-owning SQL transaction. No new audit enum or historical
allowlist/table change is needed. Proof admission and data-free handoff do not add a PHI event.
Every business audit failure must roll back its mutation/read receipt. No metadata URL, external
ID, publication digest, credential/code hash, assertion, callback, state or key is added to audits.
Installation/verification catalog facts contain no PHI and need no application audit event.

Implementation and disposable results are recorded below after verification; this design does not
claim production readiness or a complete OAuth lifecycle.

## Concrete SQL candidate

`universal-pr4a-proof.sql` creates only new versioned objects:

- `AiCimdFamilyV1`: immutable full external-ID/application revision/digest/approval RowVer binding,
  the original WEB session and consumed code/S256 receipt, internal client and original selected key.
  Tenant/session/grant/integration/user composite ownership references the existing session anchor.
  The existing WEB table exposes only a globally unique SessionId key; a FK plus the insertion trigger
  and live loader independently enforce its same-tenant/user WEB relationship without altering that table.
- `AiCimdCredentialV1`: new hash-only ACCESS/REFRESH namespace, bounded expiry, immutable owner/scope/
  generation and monotonic consume/revoke. No credentials are copied to `AiOAuthCredential`.
- `AiCimdInvocationV1`: a separate immutable proof/invocation/correlation ledger referencing the new
  credential ID and the existing tenant-qualified session, grant and committed access-audit event.
- `AiCimdProofControlV1`: a singleton installed with Enabled=0; neither new role can read/write/enable it.
- `AiCimdProofReceiptV1`: an immutable singleton source/catalog/before receipt. #1948 freezes the entire
  existing `Shale.*` extended-property set, so adding an extended property would correctly fail its
  original check. This candidate neither changes that snapshot nor chooses an excluded property prefix.
  Its separate receipt table is verified in addition to the unchanged historical check. A test adds a
  `Shale.PR4a.Unreviewed` property and requires #1948 to reject it, then removes only that test property.

Public SQL permissions are confined to two newly created, initially empty roles:

| Role | Exact application EXECUTE roots |
| --- | --- |
| `AiCimdCaseRuntimeV1` | `AiCimdDataAuthenticateV1`, `AiCimdDataSearchCasesV1`, `AiCimdDataGetCaseOverviewV1`, `AiCimdDataRecordCaseFailureV1`, `AiCimdDataFinishCaseQuotaV1`, `AiCimdDataAuthorizeCaseHandoffV1` |
| `AiCimdLifecycleV1` | `AiCimdRevokeFamilyV1` only; current same-tenant subject/admin and exact RowVer required. It cannot revoke a manual family. |

No logins/users/memberships are installed. New roles receive direct-access DENYs on new tables and
no permission on old tables or legacy entry points. New private helpers are not caller-executable;
only the digest-to-owner locator executes as owner, over the new immutable namespace. Public roots
execute as caller, derive tenant/user from proof, restore original context, reject ambient/implicit
transactions and retain the original bounded transactions, quotas, expiry samples, committed PHI/
access/resource audit and data-free single-use handoff. SQL exception text is not a public contract.

`generate-cimd-v1.py` deterministically derives versioned copies of the frozen V1 Case algorithms
(the V2 contract retains those bodies) and changes only the namespaced credential/receipt/invocation
joins plus the additional live checks. The public schema and authority templates make the additions
reviewable without decoding generated UTF-16LE module literals. Pure normalization, Case projections,
quotas and existing audit schemas remain shared. The generator never writes historical files or
consumes the private fixture. Its `--check` mode verifies all generated output without writes.

The live loader uses the existing capability/tenant lock prefix. It then holds the application row,
current tenant approval and immutable family binding before validating the original registration,
grant, session, user, WEB/key and credential lineage. Publication holds its application update to
commit and has no tenant lock dependency; approval mutations already serialize on the tenant lock.
Expiry is sampled after lock acquisition, and the earlier current approval deadline further clips
the existing minimum authority deadline. A prior invocation cannot extend that deadline or substitute
another family's proof. The same loader runs again at final handoff after housekeeping/authority locks.

The standalone verifier runs unchanged historical and #1948 verification in separate dynamic batch
scopes, then checks exact new module bytes/owners/settings/execution contexts, independent new source/
catalog/before receipt, columns/constraints/indexes/FKs, exact RLS, trigger/table-expression closure,
signatures, new role/member grants/inheritance/ownership and SQL platform-specific login facts. No
verification path refreshes a receipt. Installation is a one-shot unscoped dbo transaction under the
existing schema lock, with global capability OFF; existing or partial new objects abort. The old
verifiers also run after new DDL before sealing. No old table/role permission or binding is changed.

## Explicit remaining boundaries

This is an independently reviewable SQL proof candidate, not an activated CIMD implementation.
The following work remains outside this bounded PR:

1. Versioned authorization/code/exchange/refresh and client-assertion procedures must create the family
   receipt, NULL-legacy-transaction session anchor and new credentials atomically, with the existing
   explicit WEB consent, exact external identity/resource/issuer, S256 and assertion replay protections.
   No callable issuer, code resolver, refresh rotation or automatic activation is supplied here. Existing
   manual issuance/rotation remains unchanged. A stored S256 receipt alone is not PKCE verification.
2. Connect secure metadata retrieval to exact conditional publication resolution and tenant approval,
   method/key lifecycle and bounded freshness. Preserve legacy-SUSPENDED integration anchors; do not
   activate them as a shortcut. No fetch or Java eligibility code is added here.
3. Compose the new Case roots with an independent pool and a new full runtime identity attestor that
   includes both old and new verifiers, effective object/column permissions, real endpoint identity
   separation and cross-database access probes. The new standalone catalog verifier currently requires
   the owner/full-review boundary; do not grant old catalog roles extra reads merely to execute it.
   An activation-ready restricted catalog execution contract is unresolved. Old Case attestation by
   itself does not attest the new role. No pool or readiness fallback is implemented.
4. Close native Azure contained-session/catalog and disposable mutation/fault acceptance, source/
   artifact/ingress parity, immutable binding/JVM zone and existing operator/legal/tenant gates through
   their established separately authorized processes. Keep every capability OFF. No installation or
   production action is authorized by a local passing test.

No Settings, DCR, browser flow, stock client, production deployment or automatic merge is included.

## Migration dependencies and review sequence

The draft is stacked on #1949 rather than duplicating its unmerged dependency record and SQL-inspection
regression fix. Its SQL baseline is still latest `codex/latest` (`38281e8e`). Review this PR's diff
against #1949's branch; retarget to `codex/latest` only after that dependency is resolved.

This is not a deployment instruction. A future separately authorized installation requires the accepted
seven AI predecessors, exact V1 or V2 confinement, immutable binding already present, the sealed #1948
registry/confinement supplement and PR 3 registry. Do not replay accepted production installers or copy
a disposable receipt/binding. Require fresh unscoped dbo, complete metadata visibility, the existing
exclusive schema lock and global capability OFF. Run both predecessor verification scopes unchanged,
then the candidate, then its independent verifier and compare the exact historical receipt/binding
before-record. Any missing, changed or partial state aborts. No new pool/role membership/feature may
be activated based on the old attestor alone.

Containment keeps the new proof control and application switches OFF and drains any future new pools.
Retain family, credential, invocation and audit history. No destructive down migration, receipt refresh,
manual-client conversion, legacy-registration activation or fallback to an older proof is supplied.
The new control has no application setter; a future governed activation/audit contract is still required.
