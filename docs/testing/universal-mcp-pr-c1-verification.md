# PR C1 verification — candidate, real SQL unverified

Base: #1957 `f18574784d2bf8064c7df09cefb4386a310f5b52`.
Date: November 12, 2026 (America/Denver).

**This implementation is not accepted as complete.** The private authoritative
fixture is absent. No real SQL installation, registration, issuance, Case call,
revocation, RLS or transactional audit pass is claimed. No production service or
SQL endpoint was contacted. No DCR HTTP endpoint, production advertisement,
deployment, activation or merge is included.

The [C1 contract](../architecture/universal-mcp-pr-c1.md) lists the exact four
predecessor catalog changes, new V2 modules, unchanged components and audit model.
Historical SQL files, packaged predecessor SQL resources, receipts and deployment
binding values are preserved; the candidate installer changes only the reviewed
source constraint/locator definitions in a future explicitly reviewed database.

## Executed focused verification

- Java reactor compilation passed.
- Focused Java tests passed: **75 tests** (20 data, 55 server), zero failures,
  errors or skips. Coverage includes explicit V2 selection, adapter delegation,
  stored DCR metadata/no CIMD fallback, public and RS256 confidential registration,
  exact assertion identity, original WEB/consent/code binding for both namespaces,
  manual mapping, metadata advertisement, request policies, Case namespace cleanup,
  and unchanged CIMD metadata parsing and secure retrieval tests.
- C1 generation/preservation tests: **5 passed**, zero failures/errors/skips.
- Existing/C1 SQL-generation selection `test_*v[12].py`: 19 reported, **18
  passed, 1 skipped** for absent private production/candidate evidence. The skip
  is not SQL acceptance. This includes the prior registry/proof/OAuth preservation
  checks and finite V2 catalog representation tests.
- Selector unit tests: **32 passed**.

Commands (the `mvn` shorthand uses task-local Apache Maven 3.9.11, Temurin JDK
21.0.13+9, the inherited proxy in `work/maven-settings.xml`, task-local repository
`work/m2`, and JVM trust `/etc/ssl/certs/java/cacerts`):

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-server -am -DskipTests compile
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-server -am \
  -Dtest=AiDcrApplicationStoreTest,AiCaseDataAccessTest,AiCaseDataAttestationTest,DcrApplicationMetadataTest,CimdBrowserBindingTest,CimdClientAuthenticationTest,OAuthClientMappingTest,OAuthMetadataTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,CimdMetadataTest,CimdRetrievalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python -m unittest discover -s build/sql-confinement -p test_external_v2.py -v
python -m unittest discover -s build/sql-confinement -p 'test_*v[12].py' -v
python -m unittest discover -s build/test-selection -p test_select_tests.py
```

Initial focused failures found two issues, corrected before the passing run:
generated platform-specific checks needed the existing guarded dynamic-batch
packaging; and DCR redirect validation needed an explicit dot-segment check
because URI normalization leaves a leading `/../` segment unchanged. The latter
is scoped to DCR registration/stored metadata; existing CIMD/manual redirect
policy is unchanged. A SQL-harness setup ordering issue was also corrected so
the ancestor's opt-in gate still runs before any SQL connection.

## Required disposable SQL acceptance remains blocked

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-data -am -Dtest=AiDcrApplicationLocalSqlServerTest#dcr* \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The command reached the explicit authoritative-fixture opt-in gate and reported
**zero executed SQL tests**. A green Maven exit from that gate is not acceptance.
The earlier setup-order attempt failed for missing fixture TLS configuration
before any connection; it is not treated as a SQL product failure or success.

The four authored scenarios cover:

1. Neutral registration while product authority is OFF; unchanged authority-row
   counts; independent tenant A/B approvals and explicit consent; issuance;
   real restricted Case reads/cross-tenant denial; rejection by CIMD-only/manual
   paths; existing CIMD and manual Case continuity.
2. Refresh, tenant-specific approval revocation, other-tenant continuity,
   application disablement/revision invalidation and manual continuity.
3. Actual V1 rejection, explicit successor acceptance, catalog drift refusal,
   historical receipt and binding equality.
4. Registration audit-failure rollback of application/revision/metadata.

The fixture must be the unchanged `authoritative-candidate.sql`, verified against
SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
The C1 fixture enforces this exact hash before the first SQL owner connection.
Use the established disposable SQL Server/TLS fixture and nonce database/login
lifecycle. Required variables are `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`,
`SHALE_CASE_VERIFY_SCHEMA`, `SHALE_CASE_VERIFY_SCHEMA_SHA256`, loopback
`SHALE_AI_SQL_PORT`, fixture-owned `SHALE_AI_SQL_PASSWORD` and
`SHALE_AI_SQL_TRUSTSTORE`. Do not reconstruct the schema, substitute a mock, use
production, or count an opt-in skip as an executed scenario.

No sanitized SQL success artifact is supplied because no real SQL scenario ran.
The successor's T-SQL execution, fingerprint projection and restricted grants
remain unverified until these scenarios pass. Native Azure acceptance is also
unexecuted. Do not deploy or activate this candidate on the strength of Java or
generation tests.

## Final repository checks

Affected-suite, critical-reactor and final preservation results are recorded
after execution. No pending check is counted as passing.
