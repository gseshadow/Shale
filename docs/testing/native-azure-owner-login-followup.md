# Disposable owner login investigation — 2026-11-02

**Post-merge status — 2026-11-04:** #1929 merged at
`1f12091aef3275721c1e35d6ba00a2ecb80c5173`. The
[final diagnostic closeout](native-azure-diagnostic-closeout.md) stops further Azure diagnosis;
earlier retry, pending-validation and merge-hold instructions below are historical.
Native acceptance remains OPEN. See the [ordinary dormant release gate](../dormant-server-release-compatibility.md);
no SQL, Azure, deployment or activation is authorized by this status correction.

PR #1929 merged into codex/latest on 2026-11-04 (historical draft status superseded). No Azure or production access occurred in this
investigation. Native read-only and mutation/fault gates remain OPEN. The second owner
fixture was deleted; it must never be reused. Maven verification of this follow-up remains
blocked as recorded below; this is not authorization for another live attempt.

## Authoritative second-attempt evidence

The owner used `ccf59e0fda6efc2e703b6e06454afc5619d5bc64`, created a fresh disposable
Azure SQL database with a new nonce and exact-workstation firewall rule, then ran
`AiCaseDataAzureSqlDisposableSetupTest.provisionEmptyOwnedAzureFixture`.
Surefire reports 1 test, 0 failures, 1 error, 0 skips, approximately 15.46 seconds.
`SQLServerException: Read timed out`, rooted in `SocketTimeoutException`, occurs in
`sendLogon` / `logon` / `connectInternal` / `SQLServerDataSource.getConnection` at setup
line 33, `try(var c=owner.getConnection())`.

No connection was returned. The failure is OWNER_CONNECT, before the connected guard,
OWNER_CONNECT PASS/bounds line, OWNER_PREFLIGHT, STAGE0_BEFORE, import, migrations or
Phase1R/V2. No `work/azure-native/setup-sequence.txt` was created. This is different from
the original Stage 0 failure; neither failure establishes the other's cause. The owner
then used the reviewed cleanup and confirmed `cleanup=CONFIRMED`,
`productionAccess=false`, `productionChange=false`. These are owner observations, not
agent Azure operations. No target names, secrets, schema, SIDs or tokens are recorded here.

## Exact JDBC 12.6.1 behavior

Reviewed the [v12.6.1 source](https://github.com/microsoft/mssql-jdbc/tree/v12.6.1/src/main/java/com/microsoft/sqlserver/jdbc)
and executed the cached `mssql-jdbc-12.6.1.jre11.jar`, SHA-256
`3b1a70145dbaff98daa70022791e15becfb2b9534cc9e8cfaa1bdba6a3edeb8e`.

1. [SQLServerDataSource.getConnectionInternal](https://github.com/microsoft/mssql-jdbc/blob/v12.6.1/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerDataSource.java#L1502)
   parses the URL and calls the driver's `mergeURLAndSuppliedProperties`; setter properties
   take precedence. It passes the merged properties to `SQLServerConnection.connect`.
   Offline introspection verifies owner user/password/database, TLS, login, socket, query
   and cancel properties with synthetic settings only. It never inspects live secrets.
2. [SQLServerConnection.login](https://github.com/microsoft/mssql-jdbc/blob/v12.6.1/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerConnection.java#L3269)
   computes `timerExpire = timerStart + loginTimeoutSeconds * 1000L`. TCP connection
   selection has its own login-derived intervals (including TNIR/failover behavior).
3. [TDSChannel.open](https://github.com/microsoft/mssql-jdbc/blob/v12.6.1/src/main/java/com/microsoft/sqlserver/jdbc/IOBuffer.java#L729)
   initializes the underlying TCP socket's `SO_TIMEOUT` with:

   ```java
   int socketTimeout = con.getSocketTimeoutMilliseconds();
   if (!con.isConnected()) {
       socketTimeout = Math.min(con.timerRemaining(con.timerExpire), socketTimeout);
   }
   tcpSocket.setSoTimeout(socketTimeout);
   ```

   Thus, with finite socketTimeout=120000 and loginTimeout=15, initial socket reads are
   bounded by the remaining login time at channel open, at most about 15000 ms. This
   underlying read timeout also applies through the TLS/TDS streams during handshake and
   login-response processing. `timerRemaining` clamps an expired remainder to 1 ms.
   This is a per-read socket setting calculated at channel open, not a recomputed absolute
   deadline for every byte/read. DNS, retries and multiple reads mean total wall time is
   not promised to equal exactly loginTimeout. In this source, socketTimeout=0 also makes
   the minimum zero (unbounded socket read); this harness always uses a positive bound.
4. [TDSChannel.read](https://github.com/microsoft/mssql-jdbc/blob/v12.6.1/src/main/java/com/microsoft/sqlserver/jdbc/IOBuffer.java#L2160)
   converts a `SocketTimeoutException` into a driver termination/SQL exception. The
   logon command is an `UninterruptableTDSCommand`; the statement query/cancellation
   settings do not govern this initial `sendLogon` wait.
5. The driver does not restore configured socketTimeout when login completes and state
   becomes OPENED. `Connection.getNetworkTimeout()` reads the TCP socket's actual
   `SO_TIMEOUT`; `setNetworkTimeout(null, millis)` changes it synchronously in this driver.
   Therefore a getter/merged property of 120000 never proved that a returned socket had
   that guard. The previous immediate equality check could refuse a successful login.

**Yes: setLoginTimeout(15) can produce this exact exception mechanism, including during
sendLogon.** The approximately 15.46-second runtime is consistent with that budget. The
120-second connected guard was never reached or observed. The evidence does not identify
why the server/network stopped supplying login bytes or establish that 30 seconds will
succeed. It proves neither TLS validation failure nor firewall failure, and does not
justify changing either security control. The original Stage 0 timeout remains unresolved.

## Design changes and acceptance limits

The owner harness now uses **30 seconds** for login. This matches
[the driver's default since 11.2](https://learn.microsoft.com/en-us/sql/connect/jdbc/understand-timeouts)
(`SQLServerDriverIntProperty.LOGIN_TIMEOUT` in 12.6.1 is 30) and
[Azure SQL guidance recommending at least 30 seconds](https://learn.microsoft.com/en-us/azure/azure-sql/database/troubleshoot-common-errors-issues#steps-to-fix-common-connection-issues).
There was no measured S0 readiness distribution or other evidence supporting the old
15-second owner setting. The runbook provisions S0, not auto-paused serverless, so no
serverless resume assumption justifies a 90/120-second login budget here.

Successful control-plane creation is not a data-plane login probe.
[Firewall changes can take up to five minutes](https://learn.microsoft.com/en-us/azure/azure-sql/database/firewall-configure),
and [Azure SQL uses gateway/proxy or redirect routing](https://learn.microsoft.com/en-us/azure/azure-sql/database/connectivity-architecture).
For an outside-Azure workstation the default policy is Proxy; a TCP 1433 check alone
cannot prove TLS/database authentication, nor diagnose all explicitly configured Redirect
paths. Allow propagation before the probe; do not change firewall scope, connection policy,
DNS or TLS as a timeout workaround. A longer login budget does not fix those problems.

| Bound | Current owner design | Meaning |
| --- | --- | --- |
| Login / establishment | 30 seconds | Driver connection/login budget; initial finite socket reads are clamped as above |
| Connected socket read | 120000 ms | Explicitly set and verified immediately after getConnection returns, before metadata/SQL; verified again before every setup phase |
| Statement query | 20 seconds | Existing SQL execution limit, independently retained |
| Cancel acknowledgement | 5 seconds | Driver wait for query-cancellation acknowledgement |
| Total JUnit | Setup 300 seconds; separate probe 180 seconds | Overall test limit; thread interruption is not a substitute for socket/query bounds |

No new application retry loop is introduced. One getConnection invocation can still use
the pinned driver's existing internal connection/TLS retry behavior. A failed setup is
never replayed, including an OWNER_CONNECT failure. The guard setter is confined to the
owner test harness, fails closed on mismatch, and closes failed established connections.
Read-only identity 3-second login / 5000-ms socket, ten-second production attestation,
TLS validation, exact-workstation firewall, credential separation, read-only authority,
confinement SQL, receipts, Phase1R and V2 semantics are unchanged.

OWNER_CONNECT now emits BEGIN and PASS/FAIL with elapsed milliseconds and all four configured
bounds. PASS records the verified connected timeout and a sanitized driver version line.
FAIL adds exception/root classes, allowlisted SQL-state shape or `unavailable`, and numeric
SQL code. The replacement exception contains no raw message, cause or suppressed chain,
so Surefire cannot reprint connection secrets. JDBC's JUL warnings can independently
contain targets: owner commands load the harness-only quiet JUL file, and the harness
checks before datasource construction and refuses network access if any registered driver logger permits output. Logback alone was
insufficient. No production logging is changed.

The [operator packet](native-azure-readonly-operator-runbook.md) now requires a separate,
explicitly opted-in owner login/catalog probe after creation/propagation and before the
mutating opt-in. It shares the same owner configuration, exact acknowledgement, independent
credential checks and connected guard. Only edition, current database/dbo and empty custom
object/principal counts are read. No schema parser/import, Stage 0, object creation/adoption,
migration, user, binding, Phase1R/V2 work or acceptance receipt is involved. A successful
probe closes its connection; setup opens a new connection and repeats all checks. A failed
probe stops the packet and requires cleanup. A failed mutating setup always discards the
fixture regardless of probe success. No native acceptance is inferred from a probe.

## Test impact and verification

Before edits, searched all OWNER_* bounds, ownerSource, requireOwnerNetworkTimeout, phase,
OWNER_CONNECT and existing timeout expectations. Reviewed all datasource/safety tests,
the native read-only/setup classes, authoritative V2/Phase1R fixture, attestation, V1/V2
migration and schema-safety contracts, selector security-data/cases ownership, operator
packet, acceptance/history and database/tenancy/AI architecture. No production Java or SQL
contract changed. Audit compatibility: only data-free diagnostic and catalog-probe metadata;
no application data or business mutation, no new audit event/schema. Existing audit seams
remain unchanged.

Focused offline results and required Maven/selector results are recorded after execution
below. In-memory driver tests never call a real Socket.connect, perform TLS, or contact any
service. Their injected read exception occurs at prelogin, not sendLogon: applicability to
sendLogon comes from the reviewed shared underlying socket/TDS source path. The synthetic
OPENED transition is introspection, not a successful login simulation or native evidence.

| Verification executed in this follow-up | Result |
| --- | --- |
| `python work/run-owner-offline.py`: JDK 21 `javac -proc:none` with current core/data/test source paths, cached external dependencies (no cached Shale JARs/stubs), then JUnit Platform 1.10.3 / Jupiter 5.10.3 | 36 discovered / **33 passed** / 0 failed / 3 assumption-aborted native tests (setup, probe, read-only); no Azure connection |
| Focused classes | Datasource diagnostics/guards, real-driver in-memory timeout tests, target/owner safety, attestation, V1/V2 migration contracts and `CaseReadVerificationSafetyTest` |
| `python build/sql-confinement/checkout_bytes.py` | 25 pinned files, clean index, original V1/V2 receipts PASS |
| `python -m unittest discover -s build/sql-confinement -p test_checkout.py` | 11/11 PASS |
| `PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/test-selection -p test_select_tests.py` | 31/31 PASS |
| Selector against prior PR head (command below) | PASS; cases/security-data, core/data/server, all five changed Azure classes including probe/driver tests, one affected batch |
| Focused Maven, online (command below); earlier offline datasource baseline | No tests executed: Spring Boot 4.1.1 BOM absent; online download reports `Operation not permitted` |
| Selector's `selected_maven_batches[0]`, via `mvn -B -ntp -o` | No tests executed: same missing BOM |
| `mvn -B -ntp -o test` critical reactor | No tests executed: same missing BOM |
| `git diff ccf59e0fda6efc2e703b6e06454afc5619d5bc64 --check`; protected production Java/resources/SQL/confinement-input comparison; changed-document relative links | PASS; no protected changes |
| Exact marked runbook credential-generation block comparison with prior head | Identical; PASS. PowerShell parsing/execution of the updated packet is unexecuted: no PowerShell host; Windows host validation pending |

The selector reports informational full-suite escalation for the new harness JUL properties file
as an unknown production path. No mapping exception was added. The optional historical full
suite was not run; it cannot replace the blocked focused/affected/critical Maven checks. The
scratch direct-JUnit launcher is under `work/`, not a build/dependency-policy change or a substitute
for Maven verification. Compilation initially exposed a test-double checked-exception signature
and a scratch classpath's older SLF4J selection; both were corrected, and the final direct run
above compiles real current transitive sources with no project-JAR substitution.

```sh
mvn -B -ntp -pl shale-data -am \
  -Dtest=AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAzureSqlDriverTimeoutTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py \
  --base ccf59e0fda6efc2e703b6e06454afc5619d5bc64 --head HEAD \
  --format markdown --output work/owner-selection.md --plan-output work/owner-selection.json
```

**Verification remains incomplete:** rerun the focused Maven command, generated affected batch
and critical reactor with working dependency access, and validate the updated packet on the
owner's Windows PowerShell host before classifying another operator attempt ready. No native
probe, setup or read-only gate ran in this task. No application audit integration/migration was
needed; existing audit schemas and transactions are unchanged.
