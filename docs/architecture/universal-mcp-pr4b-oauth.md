# PR 4B: confined CIMD OAuth lifecycle candidate

Stack: #1949 -> #1950 -> this draft. CIMD remains OFF and unadvertised.
This candidate adds no production pool, identity membership, activation or deployment.

## Pre-edit review and test impact

Reviewed `codex-prompt-rules`, database schema, tenancy/RLS, system overview, web API
migration/readiness, the universal onboarding plan, PR 3 registry review, PR 4 security
dependency and PR 4A proof design. Inspected the actual legacy authorization, exchange,
credential, revocation, browser, assertion and CIMD retrieval implementations and tests.

| Contract | Existing tests inspected | Required successor evidence |
| --- | --- | --- |
| Original WEB, explicit consent, exact external ID/S256/callback | OAuthAuthorizationFlowTest, OAuthBrowserFilterTest, ShaleOAuthHumanTest | Confined new transaction namespace; no legacy registration activation |
| Single-use exchange, rotation, revocation | AiOAuthExchangeContractTest, AiOAuthCredentialContractTest, AiOAuthRevocationContractTest | Real two-tenant JDBC, replay and failed-audit rollback |
| Publication/approval and proof | AiCimdProofLocalSqlServerTest and V1, registry/confinement tests | Existing PR 4A family/credential namespace; current revision/approval checked at each mutation |
| Private-key method/key binding | PinnedClientAssertionTest, CimdMetadataTest/CimdRetrievalTest | Exact external iss/sub, remote kid mapping, selected method with no downgrade |
| Historical receipts and manual clients | Original V1/V2 verifiers, registry supplement, PR 4A verifier | No historical source/receipt/binding writes; independent new seal |

## Boundary and audit review

A dedicated metadata publisher supplies only PR 2 validated public snapshots matching an
already governed application publication. It cannot publish a new registry revision,
enable an application, approve a tenant or issue credentials. Revalidation of identical
bytes renews only bounded freshness. Changed metadata requires a new registry publication
and fresh tenant approval. The accepted detection delay is at most five minutes.

New browser and issuer identities must remain separate from legacy runtime/bootstrap and
Case identities. All new procedures are same-owner static chains; only digest-owner
resolution executes as owner, with NULL caller context. Tenant procedures require fresh
leases and derive actor/tenant from initialized authenticated Shale authority. Session
context is not independent SQL authentication. Production composition requires a new
full identity/catalog attestor and dedicated pools; the old attestor cannot authorize them.

Grant creation, family issuance, rotation and family revocation use existing
EntityActionAuditLog entities on their business SQL transaction. Audit failure rolls back
code consumption, JTI reservation, credentials and family state. Public metadata cache
refresh and pending consent without a grant carry no PHI or domain authority and add no
audit event. No URL, callback, state, key, assertion, digest or credential enters audit
metadata. No historical audit vocabulary or receipt is modified.

Implementation and exact executed verification are recorded below before handoff.

## Implemented candidate

The additive installer creates seven tables, twenty-five modules and five initially empty
roles. The metadata publisher resolves an unchanged, exact digest/method publication and
stores bounded validated public callbacks/keys/freshness. It never advances a registry
revision. Browser, issuer, resolver and neutral-revocation roles are separate. No existing
role receives an EXECUTE. New tenant tables retain the exact existing FILTER and both
BLOCK predicates. Only the three digest-to-owner resolvers execute as owner.

The browser namespace retains the original WEB session, nonce, external identity,
application revision/digest, approval identity/RowVer, internal dormant anchor, selected
method/key, callback, requested scopes and explicit decision. Spring consent nonces retain
the existing bounded base64url/padding contract; credentials remain 256-bit unpadded
CSPRNG values. SQL stores code/credential/JTI digests. S256 and callback checks use the
existing policies, including public numeric loopback callbacks; exchange binds the actual
callback exactly. The maintained browser adapter now accepts a persistence contract;
manual persistence remains its installed implementation. CIMD projection preserves the
full wire identity and SQL-clipped consent deadline.

Redeem owns one SQL transaction: current authority, exact code/client/issuer/resource/
callback/S256, client method/key, JTI reservation, consume, original grant, session,
immutable PR 4A family receipt, initial ACCESS/REFRESH rows and audits. The session retains
its internal client ID and NULL legacy transaction. No registration is activated and no
new credential enters a legacy table. Refresh rechecks current application/approval,
ceiling, policy, user, WEB, grant, session and key; rotates generation atomically; and
commits whole-family revocation before returning replay denial. Access expires within
five minutes; refresh idle authority within eight hours; all authority is clipped by the
original consent/session and current approval/WEB/key deadlines. Nothing extends consent.

RS256 assertions retain the full external `iss`/`sub` and exact endpoint audience. The
remote `kid` maps to an approved publication and tenant-local pinned key. The supported
SQL key profile uses exponent 65537. Confidential-only publication approvals produce a
confidential anchor; mixed-method publications currently produce a public anchor. A
request requiring private-key authentication cannot downgrade that selection. Every JTI
also reserves a global application/kid/digest key, across tenants, replicas, endpoint
audiences and publication revisions. Expired receipts are cleaned in bounded batches;
live global reservations have an application capacity bound. Tenant-local replay history
remains separately RLS-confined.

Known-token revocation is digest-owner-bound and audits session/family invalidation.
Unknown-token revocation authenticates current public metadata and reserves assertions in
the same global replay transaction without choosing a tenant. Its empty success grants
no domain authority. This short-lived replay ledger is a security receipt, like the
existing assertion ledger, not a fabricated tenant audit event. All meaningful grant,
key and session mutations retain the existing audit schema/vocabulary.

The new independent receipt records new source/catalog facts and an immutable before-hash
of all historical Shale receipts, the binding and PR 4A receipt. Binary hashes are rendered
as hexadecimal before XML serialization. The unchanged cumulative, registry supplement
and PR 4A verifiers run before/after installation and before its final commit. New checks
cover exact module bytes/context, explicit role permissions, RLS, catalog fingerprints,
member/inherited authority, signatures, ownership and SQL-platform login facts. Installation
is one-shot, unscoped dbo, schema-locked and global capability OFF; the new control is OFF.
No historical receipt or binding is resealed.

## Runtime integration boundary and remaining gaps

`CimdOAuthLifecycle` and `AiCimdOAuthStore` are explicit, unregistered composition seams.
There is no new Spring bean, property, endpoint, advertisement, login, membership or pool.
The existing deployed/manual security chain continues using its original store and
providers. The new browser/issuer/resolver/metadata/neutral factories must not reuse that
runtime or bootstrap identity. A complete attestor must include both historical and new
verifiers, effective object/column permissions, endpoint identity separation, cross-database
probes and native Azure acceptance. A restricted catalog execution contract remains open.
That prerequisite is isolated here; no Java eligibility fallback or broadened SQL role is
provided to bypass it.

The next phase must install separately governed, dedicated pools and the new full attestor,
route the maintained AS providers between manual and CIMD namespaces without translation,
and execute an actual WEB/browser + SQL + HTTP lifecycle through a disposable deployment.
The current browser tests are adapter contracts, not Chromium or stock-client acceptance.
The new metadata/lifecycle composition also needs that real HTTPS/SQL combined test; PR 2's
fetcher tests alone do not establish its combined behavior. The candidate deliberately
fails new authority when no-store metadata has no positive remaining freshness.

Known and unknown revocation authenticate the current exact publication digest and public
method/key profile independently of WEB, grant or approval liveness. Known credentials
retain their immutable digest-derived owner. Client authentication, global replay
reservation, family mutation and any required audit commit atomically. A publication
change between signature verification and SQL rejects the stale digest. Inactive known
credentials and repeated/unknown tokens return empty success without granting authority.

DCR, Settings, production/native Azure activation, stock-client URL-only acceptance,
artifact/ingress/binding/operator/legal/tenant gates and automatic merge remain outside this
candidate. Passing local tests cannot authorize installation or feature activation.

## Verification record

Authoritative private fixture, verified before and after SQL execution:

- Absolute local path: `/workspace/attachments/86798399-e48c-4839-a4ca-096931b813b1/authoritative-candidate.sql`.
- SHA-256: `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
- Fixture was neither modified nor committed. All imported schema and rows lived only in
  nonce-owned disposable databases with distinct restricted SQL logins.

SQL Server 2022 image:
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`.
The dedicated container exposed loopback port 14333 only. Generated localhost TLS material
and a private PKCS12 truststore used `encrypt=true;trustServerCertificate=false`. No system
trust or production SQL/configuration changed. JDK 21.0.12.1 / Maven 3.9.11.

Final SQL acceptance comprises **15 executions: all twelve V2 lifecycle scenarios below,
plus V1 repetitions of issuance/rotation/legacy exclusion, private-key/global-replay/audit
rollback, and inactive-known-token revocation**. These are real restricted JDBC calls,
not simulated SQL results. Each fixture obtains manual credentials through the existing
real WEB/S256/consent/code/credential sequence. Positive CIMD credentials are issued by
the new procedures; no owner-seeded family receipt is used to claim lifecycle success.
Only negative historical-expiration setup is owner-seeded, in the disposable database.

1. Exact two-tenant external identity, padded framework nonce, explicit consent, initial
   issuance, own-Case proof, rotation, code replay, NULL legacy transaction, legacy code/
   refresh/Case exclusion and ordinary manual-client continuity.
2. Refresh reuse commits revocation of the entire family, rejects both generations and
   replacement refresh, and leaves the other tenant and manual client functional.
3. Either credential revokes its family; a foreign tenant cannot mutate it; other tenant
   and manual access remain functional.
4. Changed publication rejects pending approval, outstanding code, retained refresh and
   access proof; manual access remains independent.
5. Tenant approval revocation rejects that tenant's outstanding code and refresh/access,
   while the other tenant can still rotate and use its own proof.
6. Changed external ID case, wrong S256 verifier, callback, tenant and replacement WEB
   session fail without consuming the rightful code, which subsequently exchanges.
7. Audit faults roll back initial exchange, rotation, token revocation and refresh-replay
   revocation. The original or rotated credential remains usable after failed mutation.
8. Confidential-only publication binds a remote non-UUID `kid` to a tenant-local key,
   rejects public fallback, rolls back global/tenant JTI reservations on failed issuance
   audit, rejects assertion replay across tenants and known/unknown revocation, then
   rotates and revokes with fresh proof. Other tenant/manual proof remains independent.
9. Expired code and refresh history cannot resurrect authority or poison the live family;
   its legitimate refresh still rotates successfully.
10. Late extra-EXECUTE fault after receipt creation rolls back all new tables/roles/receipt.
    Real identities reject direct old/new tables and legacy/private issuer roots. Actual
    authorization-table RLS FILTER hides foreign/unscoped rows; exact FILTER/BLOCK catalog
    predicates are independently verified. External binding and receipt mutation fail.
11. OFF control rejects exchange/refresh; revoked original WEB prevents retained refresh;
    unrelated tenant/manual authority remains functional.
12. Inactive known credentials still revoke all credential rows with current public client
    authentication. Stale publication digest fails; repeated and unknown revocation succeeds
    without selecting a tenant creator, and the other tenant/manual proof is unaffected.

The final 15-execution batch passed 14 and encountered one JDBC read timeout during initial
client loading in the expiration scenario, before its assertions. That exact scenario
passed unchanged in a targeted rerun. After aligning the new verifier with the established
Azure-safe guarded dynamic encoding, **two additional SQL rechecks passed**: namespace/
manual continuity and late-fault/permission/receipt rollback. The schema/module/permission
source hash remained exactly
`1a97ff3087f8a0f6a56d214ee34b41c8f1c819180c71c843aa91ef9341831528`.
Counts overlap; they are not seventeen distinct security scenarios.

| Final check | Result |
| --- | --- |
| Browser adapter contracts | 4 PASS: external identity through intent/explicit consent/code and clipped deadline; missing/foreign human authority; exact ID/no normalization or manual fallback; unknown client type cannot downgrade to public. |
| RS256 client authentication contracts | 4 PASS: external claims/remote kid mapping; strict method choice; wrong ID/sub/audience/kid rejection; assertion/key expiration. |
| Existing Case attestation contracts after platform-encoding correction | 13 PASS, including unchanged Azure catalog-exclusion assertions. |
| Selector affected security-data/server suite | 214 PASS; zero failures/errors/skips. SQL-opt-in classes run zero tests here and are not SQL acceptance. |
| Critical seven-module reactor | 253 PASS; all seven modules successful; zero failures/errors/skips. |
| New CIMD OAuth generation / unchanged PR 4A generation / registry generation | 4 / 3 / 3 PASS. |
| Immutable binding packet / selector unit tests | 5 / 32 PASS. |
| Diff whitespace, historical SQL/resource changed-path checks and fixture digest | PASS. |

There was **no actual Chromium, combined browser/HTTP/SQL or stock-client run**. The four
browser tests exercise the maintained authorization adapter with explicit persistence
fixtures, not the real SQL store or a live browser. The signature and SQL layers are tested
separately; no combined remote-client OAuth acceptance is claimed. Existing WEB/manual
contracts also pass in the affected/critical suites.

Development runs found setup-method ordering, a JDBC TLS connection retaining its short
login network timeout, binary-to-XML fingerprint serialization, and the new verifier's
platform encoding. Only new harness/generator code was corrected. Fixture-owner DDL uses
an explicit 120-second JDBC network/query timeout; restricted business procedure calls
retain their five-second query deadline. No historical verifier assertion or SQL receipt
was relaxed. An independent certificate-validated JDBC query after the final SQL rechecks
confirmed **zero user databases and zero nonce logins**. The dedicated container and
its generated credentials/key/certificate/truststore were removed after verification.

Exact final commands from the repository root (`mvn` uses the workspace JDK/Maven setup):

```sh
# Only with the dedicated disposable TLS fixture and the private environment described above.
mvn -B -pl shale-server -am \
  '-Dtest=AiCimdOAuthLocalSqlServerTest#lifecycle*,AiCimdOAuthV1LocalSqlServerTest#lifecycleTwoTenantIssuanceRotationAndLegacyExclusion+lifecyclePrivateKeyBindingAndAssertionReplayAreAtomic+lifecycleInactiveKnownCredentialsStillRevokeWithCurrentClientProof,CimdBrowserBindingTest,CimdClientAuthenticationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Targeted unchanged-source expiration rerun plus browser/signature contracts.
mvn -B -pl shale-server -am \
  '-Dtest=AiCimdOAuthLocalSqlServerTest#lifecycleExpiredCodeAndRefreshDoNotResurrectAuthority,CimdBrowserBindingTest,CimdClientAuthenticationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Final platform/seal rechecks; these do not expand the scenario campaign.
mvn -B -pl shale-server -am \
  '-Dtest=AiCimdOAuthLocalSqlServerTest#lifecycleTwoTenantIssuanceRotationAndLegacyExclusion+lifecycleInstallerLateFaultAndRuntimePrivilegesFailClosed,AiCaseDataAttestationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/sql-confinement/generate-cimd-oauth-v1.py --check
python3 -m unittest discover -s build/sql-confinement -p 'test_cimd*.py'
python3 -m unittest discover -s build/sql-confinement -p test_registry_v1.py
python3 -m unittest discover -s build/deployment-binding -p test_packet.py
python3 -m unittest build/test-selection/test_select_tests.py
python3 build/test-selection/select_tests.py --base ea4be190 --head HEAD \
  --format markdown --plan-output work/pr4b/selection.json --output work/pr4b/selection.md
# Run outside SQL opt-in; never accidentally run inherited historical SQL campaigns.
python3 - <<'PYSELECT'
import json, os, subprocess
env = os.environ.copy()
for name in list(env):
    if name.startswith(('SHALE_AI_SQL_', 'SHALE_CASE_VERIFY_')):
        env.pop(name)
for args in json.load(open('work/pr4b/selection.json'))['selected_maven_batches']:
    subprocess.run(['mvn', '-B', *args], env=env, check=True)
subprocess.run(['mvn', '-B', 'test'], env=env, check=True)
PYSELECT
git diff --check ea4be190 HEAD
```

Private SQL environment uses the existing `LOCAL_DISPOSABLE_AI` opt-in, generated disposable
password, port 14333, private truststore, attached fixture path and its verified digest.
No secret values belong in this document or shell history. The selector's informational
full-suite suggestion arises solely from registering the two new focused security test
classes. No historical all-tests or unrelated campaign was run, honoring the requested
scope. Cases are exercised through the unchanged PR 4A proof; the selected server and
security-data areas cover the changed OAuth adapter, persistence, audit and SQL contracts.

**Readiness:** ready for review and development of the subsequent runtime integration
phase. It is not a live CIMD implementation or activation-ready system. Dedicated pools,
full restricted identity/catalog attestation, versioned Case-root composition, maintained
HTTP provider routing, combined HTTPS/browser/SQL tests and separately governed activation/
audit plus native Azure and existing deployment gates remain required. No production
installation, activation, DCR, Settings, third-party-client modification or merge occurred.
