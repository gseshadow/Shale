# Azure setup timeout and Windows checkout follow-up — 2026-10-30

The subsequent [existing-worktree checkout investigation](native-azure-existing-worktree-checkout.md)
supersedes the fresh-prefix-only checkout conclusion below. The owner confirmed deletion of the
failed resource group. Current retry readiness remains BLOCKED; native gates stay OPEN.

**BLOCKED.** The separate bounded owner datasource, live timeout guard, sanitized phase diagnostics
and exact-byte checkout protection are implemented and focused checks pass. Mandatory Maven
verification/package remain blocked by the unavailable Spring Boot 4.1.1 BOM. The historical
five-second live read timeout is not reproduced or causally explained. This is not operator retry
readiness or native Azure acceptance. Continue draft #1929; do not merge.

## Operator evidence and exact failure location

The owner reports a newly created disposable Azure SQL Database, verified authoritative candidate,
no production access and 13/13 passing offline prerequisite tests after restoring the committed LF
representation of `phase3b8-confinement.sql`. The first live setup then failed in approximately
5.4 seconds with SQLServerException / SocketTimeoutException `Read timed out`. No final
`work/azure-native/setup-sequence.txt` was produced and the read-only selector was not run.

At PR head `23f96ed69d6548dd3073318cd7be0c9923070dbc`, setup source line 34 is exactly:

```java
execute(c,Files.readString(Path.of("../docs/sql/ai-integration/azure-sql-stage0-verify.sql")));
```

The stack's line 66 delegates to `AiCaseDataLocalSqlServerTest.sql`, whose JDBC query timeout is
20 seconds. This is the **initial Stage 0 batch**, after connection/edition/owner/empty-object
checks and before authoritative DDL import, prerequisites, Phase1R, V2 installation, users or
cumulative verification. Stage 0 includes multiple catalog/visibility queries; the stack does not
identify which internal query waited. No SQL wait/session/network evidence was supplied to resolve
that question. No final sequence file is expected on failure because it is written only at success.
Its absence never authorizes fixture reuse.

The owner is deleting the failed resource. Cleanup is **owner-reported in progress, not confirmed**.
No resource names, credentials or version output were supplied. No task Azure/production connection
or mutation occurred. Both native read-only and disposable mutation/fault gates remain **OPEN**.

## Executed driver proof and design change

The repository pins Microsoft JDBC `12.6.1.jre11`. `AiCaseDataAzureSqlDataSourceTest` executes that
driver's actual URL parser and `mergeURLAndSuppliedProperties` methods with synthetic credentials:

1. `setURL(...)`, followed by `setSocketTimeout(5000)`, produces effective socket timeout 5000.
2. A later `setSocketTimeout(120000)` produces getter **and merged property** value 120000.

Thus the original override works in the pinned driver's offline construction/merge path. Do not
claim the setter was ignored, or infer the owner's connected socket/driver state from elapsed
time. Actual Azure socket behavior remains unobserved by this task. Test-only reflection examines
synthetic properties only; production code does not gain reflection or metadata privileges.

Owner setup now creates a new explicit datasource rather than modifying a read-only identity
source. Its independent configuration is bounded: login 15 seconds, socket read 120000 ms,
default statement query 20 seconds, cancellation acknowledgement 5 seconds, overall JUnit
300 seconds. The existing shared executor still applies its 20-second statement bound. These
limits have different purposes; a 120-second socket limit does not extend statement execution
to 120 seconds. TLS validation, explicit disposable database and SQL credentials outside the
URL remain required. No production target, alternate transport/authentication or retry is added.

Immediately after obtaining the owner connection and before every setup phase,
`Connection.getNetworkTimeout()` must equal 120000. A mismatch refuses work rather than quietly
changing a live connection or continuing with five seconds. The driver implements this JDBC
getter through its TDS channel's socket network timeout. Configured bounds and actual connected
timeout/driver version are logged without credentials, URLs, SIDs or SQL text.

Named phase BEGIN/PASS/FAIL markers and elapsed milliseconds distinguish owner preflight, initial
Stage 0, schema import, prerequisite migrations, Phase1R/V2 prerequisite, V2 install, contained
users, deployment binding, cumulative verification, final Stage 0 and OFF checks. A focused test
verifies timeout mismatch prevents phase work and failure markers do not echo exception content.

Read-only identities retain **3-second login / 5000-ms socket** behavior. Production Java,
ten-second attestation budget, authority checks and audit transactions are unchanged.

## Windows exact-byte scope

New `.gitattributes` rules pin LF only for `docs/sql/ai-integration/*.sql`, paired AI packaged SQL
verifiers and confinement manifests, and the SQL/hash/reviewed-variant catalog inputs under
`build/sql-confinement`. All reviewed committed files in that scope already use LF; no installer,
module, manifest, prerequisite, verifier, receipt or historical V1 blob is rewritten.

The real Git regression creates an isolated local repository from committed blobs, stages the
attributes, and runs `git -c core.autocrlf=true checkout-index`. All 25 protected files remain
byte-identical, while an unrelated control text file becomes CRLF, proving the conversion is
actually exercised. Both V1 and V2 source receipts are recomputed over raw checked-out bytes
with only the embedded digest field zeroed; no newline/module normalization is performed.
V2 retains `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`, never the
owner-observed CRLF hash `561d548e8c49c17688c4f8d64c6f5f606cd70a477db2fb7cf67b104305faabda`.

The [operator runbook](native-azure-readonly-operator-runbook.md) verifies current attributes and
refreshes only clean pinned files through Git, describes each setup timeout/marker, and requires
deletion/replacement with a new nonce and independent secrets after any failed setup. The owner
must not manually retry/adopt the failed database, including after an early Stage 0 failure.

## Test-impact inventory and verification

Before editing, reviewed the V2/contained-identity architecture, native runbook/acceptance,
datasource safety and construction, attestation tests, V1/V2 migration contracts, generation/hash
inputs, Phase1R fixture/runner and selector ownership. Only test harness/build metadata/docs change.
Audit compatibility: phase/bounds metadata is data-free; no application business mutation/read,
new audit event, audit vocabulary or schema is introduced. Existing PHI/integration audits remain.

| Verification | Actual result |
| --- | --- |
| JDK 21 `javac -proc:none`, real transitive sources/cached dependencies; no stubs | PASS |
| Cached JUnit Platform 1.10.3 / Jupiter 5.10.3: `AiCaseDataAzureSqlAcceptanceSafetyTest`, `AiCaseDataAzureSqlDataSourceTest`, `AiCaseDataAttestationTest`, V1/V2 migration contracts, schema safety, both opt-in Azure classes | 26 discovered, 24 passed, 0 failures, 2 assumption aborts (native setup/read-only unexecuted) |
| `python3 -m unittest discover -s build/sql-confinement -p 'test_*.py'`, with private candidate/catalog inputs | 5/5 PASS, including Windows-style Git checkout and four existing V2 projection contracts |
| `python3 -m unittest discover -s build/phase1r-verification -p 'test_*.py'` | 3/3 PASS; offline evidence-protocol checks, not Phase1R SQL execution |
| `python3 -m unittest discover -s build/test-selection -p 'test_*.py'` | 27/27 PASS |
| Maven focused required safety/datasource/attestation/V1/V2 selectors | No tests executed: Spring Boot 4.1.1 BOM absent; online resolution reports `Operation not permitted` |
| Maven `Phase1RLocalSqlServerTest` | Unexecuted: same dependency resolution blocker; no local SQL acceptance claimed |
| Maven affected selection and critical reactor | Required; same dependency blocker, not credited from focused JUnit checks |
| `python3 build/test-selection/select_tests.py --base 23f96ed69d6548dd3073318cd7be0c9923070dbc --head HEAD` | PASS; cases/security-data, all four changed Azure test classes selected; emitted affected Maven batch stops on the missing BOM |
| `git diff 23f96ed69d6548dd3073318cd7be0c9923070dbc --check` and exact SQL/resource diff | PASS; no SQL/resource blob changes or private artifact additions |
| `mvn -B -ntp -o -pl shale-data -am -DskipTests package` | Blocked before build by same missing BOM; no production/server JAR built/uploaded/deployed |

Exact Maven focused command:

```sh
mvn -B -ntp -o -pl shale-data -am \
  -Dtest=AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Phase1R uses `mvn -B -ntp -o -pl shale-server -am -Dtest=Phase1RLocalSqlServerTest
-Dsurefire.failIfNoSpecifiedTests=false test`; critical uses `mvn -B -ntp -o test`.
These dependency failures do not establish valid local completion. All mandatory checks must pass
before changing classification to `OPERATOR RETRY READY`. A fresh owner run must then establish
actual native setup/read-only evidence separately. Preserve #1928's accepted local results and
`UNREPRODUCED HISTORICAL TRANSIENT` disposition. Do not merge #1929 or change production.
