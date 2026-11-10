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
verifiers also run after new DDL before sealing. The complete independent verifier, including the
explicit new-role permission allowlist, executes again after the new receipt is inserted and before
COMMIT; a late injected permission fault must roll back all new DDL and the new receipt together. No old table/role permission or binding is changed.

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

The family-revocation administrative seam relies on authenticated tenant/user connection initialization,
as existing tenant administration does; mutable SQL session context is not independent authentication.
The Case data role has no lifecycle permission and derives its owner from credential proof regardless
of caller context. Runtime identity/pool acceptance must retain this separation.

## Disposable verification and evidence limits

Authoritative private fixture SHA-256, verified before SQL use and unchanged afterward:
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
It was used only through the existing disposable schema-import safety harness and was never edited
or committed. SQL Server 2022 image:
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`.
The dedicated container used loopback port 14333, generated localhost TLS material, certificate-
validated JDBC (`encrypt=true`, `trustServerCertificate=false`) and a private PKCS12 truststore.
No system trust or production resource was changed. JDK 21.0.12.1 / Maven 3.9.11.

The primary suite passed **20 real SQL scenarios**, ten each against unchanged authoritative V1 and
V2. Both use actual restricted mapped logins and real JDBC, not simulated SQL results. The test-only
DataSource adapter changes procedure names to the new namespace; it delegates every connection,
statement and result to the real driver. Fixtures seed the exchange receipt as owner; they do not
claim browser consent/code/refresh implementation. Each fresh fixture also obtains ordinary manual
credentials through the existing real WEB-session/S256/code/issuance sequence.

The ten scenarios on each baseline cover:

1. Two independent tenant families and own-Case reads, foreign Case rejection, separate new versus old
   proof namespaces, REFRESH rejection as Case proof and actual manual-client Case continuity.
2. Publication change after committed SQL read but before final handoff: no data escapes; both tenants'
   old access proofs subsequently fail admission, while manual access remains functional.
3. Disabled application denies both tenants and preserves unrelated manual access.
4. Competing family revocations have one RowVer winner; A cannot revoke B and B remains functional.
5. Failed entity-action audit rolls back session and new-credential revocation; failed required Case
   audit prevents a committed read receipt or serialized result. The intentional fault produces only
   the existing fixed `INTEGRATION_ACCESS_AUDIT_OUTAGE` diagnostic during bounded cleanup.
6. Approval revocation after read prevents final disclosure and blocks A while B remains usable.
7. OFF control, expired approval and reduced ceiling fail closed; unrelated manual grants remain usable.
8. Actual new identities deny direct old/new tables and legacy Case roots. Legacy code and refresh
   resolvers cannot consume the new digests. The legacy client loader and consent also reject the
   still-SUSPENDED integration, preventing creation of an alternative legacy family.
9. Immutable receipt, one-shot install and source/member/extra-EXECUTE drift rejection. #1948 still
   rejects an added `Shale.*` property; no historical snapshot is bypassed or resealed.
10. Actual FILTER and AFTER INSERT BLOCK isolation on new tables, immutable external identity and
    legacy-transaction exclusion, nontruncating RowVer input and manual continuity.

The proof schema/module/permission source hash is
`6ad3d47b5cfdd4e6e9c49b1ef8abf3b1cd18529cafca7d9fd57240bbf0191941`.
After adding the final independent permission allowlist and complete pre-COMMIT seal verification,
the proof bodies and schema retained that exact source hash. **Four additional real SQL checks passed**:
namespace/manual continuity and receipt/installation drift on each V1/V2 baseline. These final receipt
checks also inject a late permission fault after receipt creation and require rollback of every new
table/role/receipt, with all historical receipt and immutable binding values unchanged. Counts overlap;
they are not 24 distinct security scenarios.

Development runs found generator EXEC-expression/batch-scope errors and a catalog-collation conflict.
They were corrected in the new generator; no historical SQL or security assertion was weakened.
Development runs are not acceptance evidence. Only the successful batches above are reported as SQL
acceptance. New explicit permission comparisons use binary collation on both operands, preserving
exact allowlist semantics across the fixture's database/catalog collation difference.

Final sequential checks: **47 focused contracts**, **154 affected core/data/server tests**
and **245 critical tests across all seven reactor modules** passed, with zero failures/errors/skips.
The new generation checks passed 3; unchanged registry generation 3, binding packet 5 and selector 32
also passed. Counts overlap. SQL-opt-in classes executing zero tests outside opt-in are not SQL
acceptance; only the dedicated real SQL batches above supply that evidence.

After the final SQL batch, an independent certificate-validated JDBC cleanup query confirmed **zero
user databases and zero nonce logins**. The dedicated container and generated credential/key/certificate/
truststore were removed. The attached fixture hash stayed unchanged. Historical SQL, packaged AI
resources, module/source manifests and immutable binding inputs remain byte-identical to the baseline.
No production Java source changed. Git whitespace, document links/fences, public generation and final
selector consistency checks passed.
The selector chooses security-data and its core/data/server dependencies because the new SQL changes
credential authority, Case proof, audits, RLS and catalog verification. It recommends no full-suite
escalation. No full historical, visual, browser, stock-client or native Azure suite is claimed.

Reproduction requires the dedicated disposable loopback setup described in
[the existing SQL fixture record](../testing/universal-mcp-pr3-verification.md#disposable-sql-setup-and-commands).
Private environment only: `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, `SHALE_AI_SQL_PORT=14333`,
`SHALE_AI_SQL_PASSWORD` (generated secret), `SHALE_AI_SQL_TRUSTSTORE` (private PKCS12, synthetic password
`synthetic-trust`), `SHALE_CASE_VERIFY_SCHEMA` (the attached candidate path) and
`SHALE_CASE_VERIFY_SCHEMA_SHA256` (the exact digest above). Never put a credential in command arguments
or logs. From the repository root, with the supported JDK and Maven on PATH:

```sh
python3 build/sql-confinement/generate-cimd-v1.py --check
python3 -m unittest discover -s build/sql-confinement -p test_cimd_v1.py
# Only with the exact private disposable environment above:
python3 - <<'PYSQL'
from pathlib import Path
import re, subprocess
source=Path('shale-data/src/test/java/com/shale/data/security/ai/AiCimdProofLocalSqlServerTest.java').read_text()
methods='+'.join(re.findall(r'@Test void (\w+)\(',source))
classes=['AiCimdProofLocalSqlServerTest','AiCimdProofV1LocalSqlServerTest']
selection=','.join(f'{name}#{methods}' for name in classes)
subprocess.run(['mvn','-B','-pl','shale-data','-am',f'-Dtest={selection}',
                '-Dsurefire.failIfNoSpecifiedTests=false','test'],check=True)
PYSQL
# Final seal/namespace rechecks, under the same disposable opt-in:
mvn -B -pl shale-data -am \
  '-Dtest=AiCimdProofLocalSqlServerTest#namespaceAndTwoTenantCaseProofPreserveManualClients+independentReceiptRejectsDriftWithoutRelaxingHistoricalSnapshot,AiCimdProofV1LocalSqlServerTest#namespaceAndTwoTenantCaseProofPreserveManualClients+independentReceiptRejectsDriftWithoutRelaxingHistoricalSnapshot' \
  -Dsurefire.failIfNoSpecifiedTests=false test
# The following runs are outside disposable SQL opt-in:
mvn -B -pl shale-server -am \
  -Dtest=AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,AiApplicationContractTest,AiOAuthCredentialContractTest,AiOAuthExchangeContractTest,AiOAuthRevocationContractTest,OAuthClientMappingTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,PinnedClientAssertionTest,OAuthBrowserFilterTest,OAuthMetadataTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base a70bbc6f --head HEAD \
  --format markdown --plan-output work/pr4a/selection.json --output work/pr4a/selection.md
python3 - <<'PYSELECT'
import json, os, subprocess
env=os.environ.copy()
for key in list(env):
    if key.startswith(('SHALE_AI_SQL_','SHALE_CASE_VERIFY_')): env.pop(key)
for args in json.load(open('work/pr4a/selection.json'))['selected_maven_batches']:
    subprocess.run(['mvn','-B',*args],env=env,check=True)
subprocess.run(['mvn','-B','test'],env=env,check=True)
PYSELECT
python3 -m unittest discover -s build/sql-confinement -p test_registry_v1.py
python3 -m unittest discover -s build/deployment-binding -p test_packet.py
python3 -m unittest build/test-selection/test_select_tests.py
git diff --check a70bbc6f HEAD
```

The local executable used was `/workspace/.tools/bin/mvn`. No native Azure or production connection,
fixture writes, settings, credentials, permissions, SQL deployment, activation or automatic merge.
