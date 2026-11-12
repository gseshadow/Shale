# Universal MCP PR A — verification

Baseline: `38281e8e087ffa645add9b5bd8846d03ce359ce6` (`codex/latest`).
Selected proof/OAuth sources: #1950 `ea4be190` and #1951 `40439960`.
The [architecture and trust boundary](../architecture/universal-mcp-pr-a.md) identify the
unchanged startup/admission contract, exact reuse, audit ownership and new metadata accessor.

## Environment and private input

Maven 3.9.11, Microsoft OpenJDK 21.0.9; all tools/dependencies were kept under `work/`.
Disposable SQL Server 2022 image:
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`.
Loopback port 15433, certificate-validated encrypted JDBC, separate restricted logins,
nonce-owned databases, synthetic users/Cases/credentials only. No production connection.

The prior attachment path was missing. The reattached authoritative input is:
`/workspace/attachments/8f9cee9b-4e28-4608-af20-77e7af188279/authoritative-candidate.sql`.
SHA-256 verified before use:
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
The unchanged restricted DDL importer and hash guard were used. No substitute schema,
fixture reconstruction or fixture content is committed.

## Results

* Final focused Java contracts: **112 PASS**, zero failures/errors/skips. Covers fixed
  namespace read/handoff/cleanup, existing Case disclosure/permit faults, original
  confinement inspection, OAuth persistence/mapping, external browser adapter, assertion
  signatures, metadata, manual composition and default-OFF Boot behavior.
* First selected authoritative SQL batch: **14 PASS**, zero failures/errors/skips.
  Includes complete V1/V2 composition, additive drift refusal/reconstruction, post-read
  revocation, metadata-role denials/OFF behavior, late-install fault rollback, stale
  publication/approval, wrong bindings, refresh/assertion replay and required-audit faults.
* Final SQL batch: **7 PASS**, zero failures/errors/skips. Explicit selections cover strict RLS/immutable ownership,
  legacy namespace exclusion, inactive-known-token revocation, code/refresh expiry,
  revoked WEB authority and the final composition revision.
  The two batches contain 21 executions of 19 distinct scenarios; repeated final
  composition/OFF cases verify the final source and per-disclosure PHI audit assertions.
* Seven proof/OAuth generation contracts pass; all three generators' `--check` pass.
* Selector's 32 Python tests pass. Selection identifies cases/security-data/server with
  no selection error. Informational escalation comes from the two test-inventory files.
  The owner explicitly requested targeted testing; no broad affected/critical/all-tests
  or historical SQL campaign was run. Do not read these results as those suites passing.

An earlier focused run passed 82 tests; counts overlap and are not additive. SQL opt-in
classes executing zero tests in ordinary Java batches are not SQL acceptance.

## Complete disclosure evidence

`completeOAuthAndAuditedDisclosureWithinOriginalDeadline` uses real SQL browser consent,
single-use code/S256 exchange and rotating refresh, not owner-seeded CIMD families.
The actual `AiCimdCaseData` composition runs unchanged predecessor attestation plus its
complete additive checks at startup/admission. Its production namespace routes all Case
phases; there is no test-only SQL-name rewriting. The original eight-second monotonic
budget starts before admission and is retained through encoding and final handoff.

Recorded passing complete requests include 3,309 ms on V2 and 2,626–2,902 ms on V1.
These are local synthetic samples, not representative-load percentiles or an SLA.
Positive assertions require actual Case bytes, the correct tenant/user, committed
READ/RETURNED resources and PHI audit, consumed handoff and released/nonrefunded quota.
The final positive checks count a new PHI audit per disclosure, including rotated tokens.
Revocation after read withholds output and leaves handoff unconsumed while preserving B.

The new reviewer receives exactly three database permissions: metadata visibility,
system dependency SELECT and EXECUTE on one source-checked immutable-metadata accessor.
It cannot directly SELECT the binding, receipts, Case or credential tables. Business
identities cannot call the accessor. A late extra Case SELECT in the installer rolls
back both new objects; the corrected installation then passes complete disclosure.
No historical role/table grants or receipt values are changed to make verification pass.

Development failures were corrected before the passing run: missing packaged declarations,
a SQL predicate parenthesis, SQL Server 2022's implied metadata permissions, and the
statement transaction introduced by INSERT EXEC for immutable metadata reads. The
initial direct-receipt-grant approach was rejected because historical verification
correctly refused the permission drift. No business proof, audit, expiry, deadline,
historical fingerprint or required positive assertion was relaxed.

## Reproduction

Use the private fixture above and the existing explicitly opted-in disposable harness.
Set `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, `SHALE_AI_SQL_PORT`,
`SHALE_AI_SQL_PASSWORD`, `SHALE_AI_SQL_TRUSTSTORE`, `SHALE_CASE_VERIFY_SCHEMA` and
`SHALE_CASE_VERIFY_SCHEMA_SHA256`. Use a trusted synthetic server certificate; never
disable TLS validation. Do not print credential values. Local Maven proxy/JDK wrappers
are omitted below; filters list the methods actually executed (an unmatched obsolete
RLS selector in the first invocation contributed no tests or acceptance).

```sh
mvn -pl shale-server -am -Dtest=CimdBrowserBindingTest,CimdClientAuthenticationTest,OAuthBrowserFilterTest,ShaleOAuthHumanTest,PinnedClientAssertionTest,AiSecurityContractTest,AiCaseDataAccessTest,IntegrationCaseReadTest,AiCaseDataAttestationTest,OAuthMetadataTest,OAuthClientMappingTest,McpCaseCompositionTest,McpProductionOffBootTest,AiOAuthExchangeContractTest,AiOAuthCredentialContractTest,AiOAuthRevocationContractTest -Dsurefire.failIfNoSpecifiedTests=false test

# SQL batch A: method-only selections avoid inherited historical campaigns.
mvn -pl shale-data -am '-Dtest=AiCimdAuthorizationLocalSqlServerTest#completeOAuthAndAuditedDisclosureWithinOriginalDeadline+revocationAfterReadWithholdsDisclosureAndPreservesOtherTenant+admissionRejectsAdditiveDriftWithoutChangingHistoricalVerification+metadataAccessorAndOffControlsCannotGrantCaseOrCredentialAuthority+reviewerInstallerRollsBackLatePrivilegeFault,AiCimdAuthorizationV1LocalSqlServerTest#completeOAuthAndAuditedDisclosureWithinOriginalDeadline,AiCimdOAuthLocalSqlServerTest#lifecycleRefreshReplayCommitsWholeFamilyRevocation+lifecycleChangedPublicationBlocksPendingCodeExchangeAndRefresh+lifecycleApprovalRevocationBlocksCodeAndRefreshInOnlyItsTenant+lifecycleWrongBindingsAndReplacementWebFailWithoutConsumingCode+lifecycleAuditFailureRollsBackExchangeRotationReplayAndRevocation+lifecyclePrivateKeyBindingAndAssertionReplayAreAtomic,AiCimdProofLocalSqlServerTest#publicationChangeDeniesBothTenantsAtAdmissionAndFinalDisclosure+failedAuditRollsBackRevocationAndCaseReadReceipt' -Dsurefire.failIfNoSpecifiedTests=false test

# SQL batch B: final revision and explicit RLS/expiry/inactive-revocation coverage.
mvn -pl shale-data -am '-Dtest=AiCimdAuthorizationLocalSqlServerTest#completeOAuthAndAuditedDisclosureWithinOriginalDeadline+metadataAccessorAndOffControlsCannotGrantCaseOrCredentialAuthority,AiCimdProofLocalSqlServerTest#strictRlsAndOwnerImmutabilityProtectNewNamespace+namespaceAndTwoTenantCaseProofPreserveManualClients,AiCimdOAuthLocalSqlServerTest#lifecycleInactiveKnownCredentialsStillRevokeWithCurrentClientProof+lifecycleExpiredCodeAndRefreshDoNotResurrectAuthority+lifecycleOffAndExpiredWebDenyIssuanceAndRefresh' -Dsurefire.failIfNoSpecifiedTests=false test

python3 build/sql-confinement/generate-cimd-v1.py --check
python3 build/sql-confinement/generate-cimd-oauth-v1.py --check
python3 build/sql-confinement/generate-cimd-authorization-catalog.py --check
python3 -m unittest discover -s build/sql-confinement -p 'test_cimd*.py'
python3 -m unittest discover -s build/test-selection -p 'test_*.py'
python3 build/test-selection/select_tests.py --base 38281e8e --head HEAD --plan-output work/pr-a-selection.json
git diff --check 38281e8e
```

The first Maven command runs without SQL opt-in variables. SQL batches require them.
Selector commands were also run against the complete changed-path inventory before commit.

## Artifact integrity, cleanup and limits

All 53 pre-existing files under SQL/AI resources/confinement-build/deployment-binding
trees are unchanged. No #1952–#1954 runtime, certificate gate, system baseline or binding
changes are included. Authoritative tests compare historical receipts and every immutable
binding field after each scenario; manual clients retain their original path.

Final fixture SHA-256 matches the before hash. Independent certificate-validated TLS
checks found **zero disposable user databases, zero fixture/probe logins and zero CIMD
signing certificates**. The dedicated SQL container and generated password, TLS private
key/certificate and truststore were removed. The user attachment is retained unchanged.

CIMD is OFF/unadvertised/unwired from HTTP. No DCR, Settings, stock-client connection,
real Chromium/HTTPS/browser-to-SQL flow, native Azure acceptance or production readiness
is claimed. Browser and signature tests are adapter/crypto contracts; SQL tests are
real JDBC lifecycle evidence. PR B owns the stock-client vertical connection. Native
Azure and existing production/deployment/activation gates remain separate and open.
