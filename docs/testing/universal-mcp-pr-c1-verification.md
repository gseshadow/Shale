# PR C1 verification — authoritative disposable SQL passed

Base: #1957 `f18574784d2bf8064c7df09cefb4386a310f5b52`.
Final SQL run: November 12, 2026, completed 18:27:15 UTC.

The four authored C1 authoritative SQL scenarios executed and passed: **4 tests,
0 failures, 0 errors, 0 skips**. This closes the missing-fixture/local SQL blocker.
No production service or SQL endpoint was contacted. Native Azure acceptance and
stock MCP-client interoperability are not claimed. C2 has not begun; there is no
DCR HTTP endpoint, production advertisement, deployment, activation or merge.

The [C1 contract](../architecture/universal-mcp-pr-c1.md) lists the exact four
predecessor catalog changes, new V2 modules and reused components. The sanitized
[result artifact](evidence/universal-mcp-pr-c1/authoritative-sql-results.json)
records the engine, fixture hash, individual scenario times, assertions and cleanup.
Raw connection properties, credentials, tokens and private SQL are excluded.

## Fixture and disposable environment

The reattached `authoritative-candidate.sql` was verified unchanged against SHA-256
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
The test enforces this hash before its first SQL owner connection and uses the
existing authoritative importer, nonce databases/logins, restricted JDBC adapters,
RLS and cleanup. No schema reconstruction or mock was substituted.

SQL Server **16.0.4315.2**, Developer Edition (64-bit), ran in a task-owned local
container bound to `127.0.0.1:15433`. Image:
`mcr.microsoft.com/mssql/server:2022-latest`, registry digest
`sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`.
JDBC used `encrypt=true`, `trustServerCertificate=false`, hostname verification
for localhost and a task-local truststore for the disposable server certificate.
The certificate is test TLS setup, not product signing infrastructure.

The controls defaulted OFF after installation, as asserted in each scenario.
Only nonce fixture controls were enabled for the tests. Production controls
remain OFF. Post-run master queries found **0 fixture databases and 0 fixture
logins**. The task-owned SQL container and its private credentials/TLS material
were removed after recording results.

## Executed SQL scenarios

| Authored test method | Result | Seconds |
| --- | --- | ---: |
| `dcrNeutralCreationTwoTenantConsentAndCredentialSeparation` | Passed | 47.646 |
| `dcrSuccessorRejectsDriftWithoutChangingHistoricalReceipts` | Passed | 34.699 |
| `dcrRegistrationAuditFailureRollsBackAllApplicationRecords` | Passed | 32.518 |
| `dcrTenantRevocationAndApplicationRevisionInvalidateOnlyTheirAuthority` | Passed | 54.403 |

The first scenario asserts unchanged authority-row counts after neutral DCR
creation with product authority OFF; separate tenant 7/8 approvals and explicit
consents; token issuance; actual restricted Case reads for each tenant and
cross-tenant denial. It verifies approved-metadata retention, unused expiry and
SQL error 229 for direct tenant-table access. DCR credentials fail manual and
CIMD-only paths, while existing CIMD and manual Case reads still succeed.

The revocation scenario executes refresh, tenant-specific approval revocation
with other-tenant continuity, known/unknown credential revocation and application
disablement/revision invalidation. Revoked access and refresh fail; manual access
continues. Compatibility checks execute unchanged V1 rejection, explicit V2
acceptance, injected catalog-column drift refusal and acceptance after restoration.
Historical receipts and immutable deployment binding values compare equal. The
audit scenario injects a failing global-audit trigger, confirms application and
metadata insertion rollback, removes the fault and verifies the successor again.

Exact Maven selection (only the four `dcr*` methods, excluding inherited tests):

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-data -am -Dtest=AiDcrApplicationLocalSqlServerTest#dcr* \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

This used task-local Maven 3.9.11 and Temurin JDK 21.0.13+9, with
`SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, `SHALE_CASE_VERIFY_SCHEMA`,
`SHALE_CASE_VERIFY_SCHEMA_SHA256`, loopback `SHALE_AI_SQL_PORT` and private
fixture-owned password/truststore variables. No private values are recorded.
JUnit class elapsed time: **170.199 seconds**; Maven reactor: **2:58**.

## Demonstrated C1 failures corrected

The initial four-test run reached real SQL and failed in shared C1 installation;
subsequent runs selected only compatibility until that setup passed. Three C1
successor-generation defects were corrected:

1. SQL Server persists an altered procedure's definition with `CREATE PROCEDURE`.
   Locator pins had incorrectly hashed the deployment `ALTER` spelling. Pins now
   match persisted source; installation still uses ALTER, preserving IDs/grants.
   A disposable engine probe confirmed the behavior.
2. An empty computed-column XML projection returns NULL, erasing the new catalog
   digest. Only that optional projection now contributes an empty string; both
   sealing and verification explicitly reject missing catalog material. A second
   disposable engine probe confirmed the NULL behavior.
3. Historical locator projection also rewrote the trigger query, where alias
   `o` does not exist. Projection now applies only to the procedure-object query;
   the original trigger query is retained.

Module and missing-receipt diagnostics identify the failed static pin. No old
verifier was bypassed or failure suppressed. No historical receipt was regenerated.
Only the new C1 installer, successor resources, generator and focused generation
checks changed. The four authored SQL scenarios and product authorization paths
were not weakened or modified to obtain acceptance.

## Focused verification and historical preservation

After the fixes, **6 C1 generation/preservation tests passed**, with no failures,
errors or skips:

```text
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover \
  -s build/sql-confinement -p test_external_v2.py -v
git diff --check
```

SHA-256 comparison to the base confirmed all **273 historical baseline artifacts**
under `docs/sql`, `shale-data/src/main/resources/ai`, `build/sql-confinement` and
`build/deployment-binding` unchanged. New C1 files remain additive to that base.
Runtime equality checks separately confirm receipt and binding preservation.

Earlier C1 evidence remains: focused Java 75 passed, final relevant rerun 27
passed, affected selection 231 passed, critical reactor 236 passed, selector
unit tests 32 passed, and selected generation checks 18 passed / 1 private-evidence
skip. Those are earlier runs, not rerun for this SQL-only correction. No broad
historical SQL or Java campaign was repeated. The final authoritative four-test
run above contains no skip and supersedes the earlier missing-fixture gate.

## Remaining requirements

There is no remaining demonstrated C1 local authoritative SQL failure. Native
Azure SQL acceptance remains unexecuted before a deployment decision. Any future
installation requires review of the explicit V2 contract, source pins, role
assignments and dormant composition; this evidence authorizes no deployment.

C2 remains separate: RFC 7591 HTTP registration, distributed endpoint abuse/rate
limits, capacity/retention policy, discovery gating and stock MCP-client browser
interoperability still need implementation/acceptance. No unmodified client or
HTTP flow was exercised by these C1 SQL tests. Production MCP/OAuth/CIMD/DCR
controls remain OFF; the PR stays draft.
