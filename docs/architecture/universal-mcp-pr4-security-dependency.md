# Universal MCP PR 4: OAuth binding security dependency

Baseline: `38281e8e087ffa645add9b5bd8846d03ce359ce6` (`codex/latest`), including merged
#1945–#1948. Review date: November 10, 2026 (America/Denver).

**PR 4 is incomplete. Do not enable or advertise CIMD.** This review records the boundary that
must be implemented and verified before connecting the retrieval/registry foundation to OAuth.
It contains no production implementation, SQL installer, identity provisioning or activation.
The attached authoritative candidate is available and its SHA-256 was verified as
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f` before disposable SQL use.
Missing fixture input is therefore not the remaining dependency.

## Security dependency

A registry application revision and a tenant approval are not part of the existing credential proof.
Activating the suspended integration created by PR 3 and translating the external URL into its
opaque internal client ID would let the existing OAuth procedures issue a legacy family. That family
has no durable application/approval revision binding. Neither legacy proof admission nor final Case
handoff checks current application metadata or approval expiry. A valid legacy family cannot safely
stand in for the new external application authority.

This is an integration dependency, not a claim that the intentionally disconnected PR 3 implementation
issues credentials or that existing manual clients should depend on an application approval. Keep
approval-created integrations suspended until a complete versioned path exists.

| Existing boundary | Observed contract | Consequence for PR 4 |
| --- | --- | --- |
| `AiApplicationRegistryService.publishCimd` / `AiPublishApplicationV1` | Validated digest is caller supplied; every publish advances revision, including identical bytes. No read/resolve or conditional unchanged-publication path. | Compose secure retrieval with an exact, bounded registry resolver. Reusing an unchanged publication must not advance revision on every authorization or resurrect an operator-disabled application. Concurrent publication must use SQL concurrency control. |
| `AiTenantApplicationService` | Authenticated-context request/decision; approval creates a suspended integration with no redirect/key/consent/credential. | Add confined runtime resolution/activation with current same-tenant approval, scope ceiling, policy, expiry and exact publication revision. An approval never consumes user consent. |
| `AiOAuthTransactionStore`, `DurableOAuthAuthorization`, `TenantBoundOAuthClientRepository` | Legacy opaque ID, registration version, original WEB session; at most five redirects. | Add a versioned immutable wire-ID/application/approval/method/key binding. Preserve legacy validation and browser login; do not merely broaden a regex. |
| `AiOAuthExchangeStore`, `AiOAuthCredentialStore`, code/refresh resolvers | Digest-derived owner plus the legacy opaque client and credential lineage. | Bootstrap must prove the full external ID and durable owner lineage without a caller tenant. Issuance, replay reservation and audit must still commit together. |
| `PinnedClientAssertion` | UUID local key ID and exact legacy client as `iss`/`sub`. | Remote metadata `kid` needs an explicit mapping to its approved internal key revision. Claims must retain the external ID; never rewrite a signed assertion or downgrade a confidential-only profile. |
| `ResolveAiRevocationV1` / `OAuthRevocation` | Unknown credentials may use the globally unique manual registration creator as owner. | A shared CIMD identity has no unique tenant creator. Add tenant-neutral authentication/replay handling for unknown credentials and empty RFC 7009 success; known credentials remain digest-owner-bound. |
| `AiDataLocateAccessOwnerV1`, `AiDataLoadAuthorityV1` and confined Case handoff | Existing session/transaction/registration client equality, proof, live legacy authority, quotas, audits and deadline checks. | New application/approval checks must reach admission and final disclosure at the SQL proof boundary. Java lookup alone cannot close a database authority path. |
| `AiRegistryIdentityAttestation`, registry confinement supplement V1 | Publisher has one exact EXECUTE; tenant identity has two; exact role tokens, terminal audit graph and immutable historical receipts. | Do not add methods/membership to those roles or replace the pinned helper. A new capability needs separately versioned authority/verification with independent restricted identities and the unchanged V1 supplement still passing. |

The needed security dependency is a reviewed additive OAuth/credential/Case proof extension that closes
this whole path. No such extension exists at this baseline. Changing one Java adapter or adding an
approval check only at authorization would leave a working bypass through retained credentials.
Historical verifiers, receipts and immutable binding values cannot be edited to admit that shortcut.

## Disposable counterexample and acceptance boundary

`AiApplicationConfinementLocalSqlServerTest.legacyFamilyCannotServeAsCimdRevisionBinding`, also
selected through its V2 subclass, models the unsafe shortcut explicitly and exclusively as owner-only
disposable test setup:

1. Install unchanged predecessors, confinement, the #1948 prerequisite and PR 3; retain original
   manual-client fixtures, receipts, binding, restricted identities and source checks.
2. Publish one exact CIMD identity. Two tenants independently request and approve it. Assert both
   integrations are suspended before introducing the deliberately unsafe adapter fixture.
3. As fixture owner only, activate those two integrations and add a test callback. Run the existing
   WEB-session-bound intent/explicit approval/code/S256/issuance path against their internal IDs.
   This creates ordinary legacy families, not real CIMD OAuth support.
4. Republish the application, advancing its revision. Assert both tenant approvals are stale.
5. Run actual publisher/tenant identity and Case attestation. Exercise legacy proof admission and
   actual Case overview disclosure independently in each tenant.

This diagnostic asks whether current proof enforcement already binds the application revision. It is
not a positive CIMD acceptance test, does not validate the shortcut for use, and must be replaced or
extended with denial assertions on the new versioned path when that path is implemented. Existing
manual family behavior remains a separate regression contract. Its result is recorded below only
after actual execution; mocked JDBC checks are not SQL evidence.

## Required implementation contract for the successor

The following are design requirements for implementation review, not installed objects or finalized
SQL names. They stay within PR 4; DCR and management/UI work remain PR 5–7.

- Retain an exact immutable application UUID, issuer/resource, full external ID, approval ID/revision,
  tenant/integration, publication digest/revision and selected method/key revision through intent,
  code and family. A digest-index hit must also compare the entire ID and byte length. No shortened
  wire ID or tenant parameter is exposed to clients.
- Keep new external families outside any legacy-only proof path that omits those checks. Merely
  creating additional wrappers while an older executable resolver can still admit the same family
  is insufficient. Review old-path reachability as well as new-path correctness.
- Add bounded, separately permissioned registry and authenticated tenant projections. Neutral public
  metadata reads cannot enumerate tenants or users; tenant reads derive both context keys from live
  WEB authority. Publication is not approval. A changed publication invalidates old eligibility;
  disablement, revocation and expired approvals cannot be silently upgraded or restored.
- Preserve original browser login, current WEB session/JTI, five-minute consent nonce, separate explicit
  consent, 60-second hashed single-use code, exact callback/state/resource/issuer and mandatory S256.
  No partial success, automatic consent, client-selected tenant or alternate password flow.
- Recheck application revision, approval state/expiry, scope ceiling, integration state, policy, user,
  WEB lineage and selected method/key at completion, exchange, refresh, access admission and final
  disclosure. Checks must lock the authoritative rows with the mutation/audit or proof transaction.
  Clip authority to all applicable deadlines; preserve rotation and family reuse revocation.
- Support legacy singular/plural metadata profiles without a confidential-to-public fallback. Fetch
  metadata and JWKS exclusively through PR 2's bounded DNS/TLS/SSRF/cache boundary. Treat cache
  freshness and transaction revision as distinct controls. Record the accepted bounded detection delay.
- Preserve assertion endpoint audiences, exact external `iss`/`sub`, RS256 restrictions and atomic JTI
  replay protection across replicas. Unknown-token revocation still needs tenant-neutral client
  authentication and replay reservation without selecting an arbitrary tenant.
- A new verifier must inspect all added roles, actual member tokens and effective permissions, entry
  points, signatures/execution context, table-expression/policy/trigger/FK edges and terminal audits.
  Run historical verification and #1948's unchanged supplement as well. Never replace an existing
  receipt, reseal an old helper, widen bootstrap or modify the deployment binding to make this pass.
- New runtime composition remains default OFF and refuses incomplete configuration or verification.
  Advertisement additionally requires the complete durable path and independent profile evidence.
  A feature switch or a secure fetcher alone is not readiness.

## Audit compatibility

This PR changes test code and documentation only. It performs no application sensitive read or domain/
admin mutation, so it adds no audit event, schema or allowlist. Disposable probes reuse existing real
transactional grant, credential and disclosure audits; no raw credential, callback, metadata body,
client state, assertion, key material, exception or SQL text belongs in their audit metadata.

The implementation must preserve existing `GlobalControlPlaneAuditLog` publication and
`EntityActionAuditLog` approval/integration/grant/session mutation ownership, plus existing
integration-access auditing. Every authoritative mutation and its allowlisted event must use the same
SQL connection and transaction and roll back on failed audit. No UI-authored, asynchronous or
post-commit audit is acceptable. A tenant-neutral assertion replay store is a security mutation whose
transaction, cleanup bounds and audit compatibility require explicit review; it is not a tenant event
with a fabricated actor. No new audit schema is designed or claimed by this dependency record.

## Pre-edit test-impact inventory and changes

Before changing tests, inspected current registry/approval, transaction, code, credential, revocation,
client mapping, metadata, assertion, browser and confinement tests, including their SQL field lists,
immutable ownership triggers, source/permission receipts and dispatch semantics.

| Changed file / contract | Coverage and reason |
| --- | --- |
| `AiCaseDataAttestationTest` | Baseline SQL inspection overflowed the Java regex stack on the large #1948 dynamic batch. Possessive repetition preserves matching while avoiding recursive backtracking. New large escaped-string LF/CRLF coverage verifies that unconditional server-catalog SQL after dispatch remains visible. All existing Azure routing/forbidden-catalog assertions remain. No production verifier changed. |
| `AiApplicationConfinementLocalSqlServerTest` (inherited by V2) | New explicit-opt-in authoritative SQL dependency probe with two approvals, real existing code issuance, stale publication, actual identities/attestation and tenant-confined Case access. All owner-only fault setup remains within nonce-owned databases. |
| Onboarding plan and this record | Link the PR 4 dependency without marking implementation complete or changing later PR scopes. |

Existing `AiApplicationLocalSqlServerTest` additionally covers real SQL two-tenant request/decision,
revision/expiry/disablement, deny/revoke/ceiling, RLS FILTER/BLOCK, RowVer races, audit rollback and
manual registration preservation. Existing OAuth/CIMD tests protect SSRF/TLS, method negotiation,
manual client mapping, browser controls, S256, resource/issuer and assertion validation. These
baseline checks are not new CIMD lifecycle acceptance.

## Verification results

The counterexample is **confirmed on both V1 and V2**: advancing a publication digest/revision made
both approvals stale, but the deliberately legacy-issued families still authenticated and disclosed
each tenant's own Case. Registry identity and Case attestation passed. This is evidence against the
shortcut, not a regression in disconnected PR 3 or authorization to use it. Both probes assert all
receipt and immutable binding values remain unchanged.

The only initial failure was the baseline `AiCaseDataAttestationTest` recursive-regex
`StackOverflowError`; the final focused run passes with the test-only fix above. No SQL or production
assertion was weakened. Maven 3.9.11 and Temurin Java 21.0.12.1 were used. Final runs are sequential.

| Check | Result |
| --- | --- |
| Focused CIMD retrieval/parser, manual OAuth/browser/assertion and data/confinement contracts | 74 PASS; zero failures/errors/skips. |
| Actual SQL registry scenarios and authoritative confinement compatibility/dependency probes | 12 PASS; zero failures/errors/skips: nine registry scenarios, one V2 compatibility scenario, one new V1 and one new V2 two-tenant counterexample. |
| Selector affected core/data/server suite | 154 PASS; zero reported failures/errors/skips. SQL-opt-in classes execute zero tests in this non-opt-in run; only the separate 12-scenario run is SQL evidence. |
| Critical seven-module reactor | 245 PASS; all seven modules successful; zero failures/errors/skips. |
| Unchanged registry supplement generation | 3 PASS. |
| Immutable binding packet / test selector | 5 / 32 PASS. |
| Whitespace, changed-document links and historical/production changed-path checks | PASS; no production source, SQL, packaged AI resource or confinement generator changes. |

Counts overlap and must not be added as unique cases. Cases/security-data are the selector's affected
areas because these tests protect Case proof admission, database authority and Azure platform routing.
Their core/data/server dependencies are relevant. The selector does not recommend a full-suite
escalation; no historical all-tests, UI visual, actual browser or stock-client run was performed.
No test result establishes a complete CIMD lifecycle, production/native Azure acceptance or tenant
rollout readiness.

Disposable SQL used the pinned SQL Server 2022 image
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`,
a dedicated container and loopback port 14333. The fixture used generated localhost TLS material,
`encrypt=true`, `trustServerCertificate=false` and a private PKCS12 truststore; system trust and
production were untouched. Only nonce-owned synthetic databases/logins were created. A final
certificate-validated JDBC query independently confirmed zero user databases and zero nonce logins.
The dedicated container and generated credential/key/certificate/truststore were removed. The attached
candidate's SHA-256 remained identical after SQL use; it was never modified or committed.

Exact test/check commands from the repository root (the local Maven executable was
`/workspace/.tools/bin/mvn`; use normal `mvn` with equivalent JDK setup):

```sh
mvn -B -pl shale-server -am \
  -Dtest=CimdClientIdentifierTest,CimdMetadataTest,CimdRetrievalTest,AiApplicationContractTest,AiOAuthExchangeContractTest,AiOAuthCredentialContractTest,AiOAuthRevocationContractTest,OAuthClientMappingTest,OAuthMetadataTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,OAuthBrowserFilterTest,PinnedClientAssertionTest,OAuthConfigurationTest,AiCaseDataAttestationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Only with the dedicated disposable loopback fixture and private environment below:
mvn -B -pl shale-data -am \
  '-Dtest=AiApplicationLocalSqlServerTest,AiApplicationConfinementV2LocalSqlServerTest#preparedCompatibilityInstallsWithoutRefreshingAnyHistoricalReceipt+legacyFamilyCannotServeAsCimdRevisionBinding,AiApplicationConfinementLocalSqlServerTest#legacyFamilyCannotServeAsCimdRevisionBinding' \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 38281e8e --head HEAD \
  --format markdown --plan-output work/pr4/selection.json --output work/pr4/selection.md
# Run outside SQL opt-in; execute each selected_maven_batches argument array with mvn.
python3 - <<'PYSELECT'
import json, os, subprocess
env = os.environ.copy()
for name in list(env):
    if name.startswith(('SHALE_AI_SQL_', 'SHALE_CASE_VERIFY_')):
        env.pop(name)
for args in json.load(open('work/pr4/selection.json'))['selected_maven_batches']:
    subprocess.run(['mvn', '-B', *args], env=env, check=True)
subprocess.run(['mvn', '-B', 'test'], env=env, check=True)
PYSELECT
python3 -m unittest discover -s build/sql-confinement -p test_registry_v1.py
python3 -m unittest discover -s build/deployment-binding -p test_packet.py
python3 -m unittest build/test-selection/test_select_tests.py
git diff --check 38281e8e HEAD
git diff --name-only 38281e8e HEAD -- docs/sql shale-data/src/main/resources/ai \
  shale-core/src/main shale-data/src/main shale-server/src/main build/sql-confinement
```

Private fixture environment: `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`,
`SHALE_AI_SQL_PORT=14333`, `SHALE_AI_SQL_PASSWORD` (generated disposable secret, never logged),
`SHALE_AI_SQL_TRUSTSTORE` (generated PKCS12 path), `SHALE_CASE_VERIFY_SCHEMA` (attached candidate path),
and `SHALE_CASE_VERIFY_SCHEMA_SHA256` (the verified digest above). The expected synthetic truststore
password is `synthetic-trust`, as required by the unchanged fixture. Setup is restricted to that
dedicated local container; these commands do not authorize production or native Azure setup.

## Remaining interoperability and rollout dependencies

No stock client has been run against a CIMD-enabled Shale flow. ChatGPT remote-key/plural-method,
Claude native localhost/random-port, Hermes ten-callback and MCP protocol/version compatibility remain
at the evidence levels recorded in the [onboarding plan](universal-mcp-tenant-onboarding.md#interoperability-evidence).
Do not patch vendor clients or claim URL-only support from the existing provisioned-ID SQL fixture.
DCR remains PR 5, Settings/admin APIs and UI remain PR 6, stock combined acceptance remains PR 7.

Before tenant rollout: implement and review the additive proof/verification successor; complete real
SQL wire-ID/code/refresh/assertion/revocation/restart/race/audit-fault/two-tenant tests; verify actual
browser and independent CIMD profiles; retain all native Azure, identity separation, immutable binding,
JVM time-zone, artifact/ingress, operations, legal/operator/tenant gates in the existing readiness
records. Those gates are not authorization to reopen a stopped production investigation. Nothing in
this PR deploys SQL, provisions production identities, activates MCP/OAuth, changes a binding or merges.
