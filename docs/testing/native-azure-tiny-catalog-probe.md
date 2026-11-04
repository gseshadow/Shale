# Tiny synthetic Azure catalog probe

Prepared from Windows-validated `fceac929e1f9cfcf6626f26d602c1f5bd15b1ce5`.
**No Azure executed or authorized by preparation.** This is an independent diagnostic;
never invoke the full-fixture launcher or regenerate/reuse its markers for this probe.
The owner's completed Windows validation of that baseline remains historical evidence.

## Independent review pins

Canonical manifest SHA-256 (UTF-8, CRLF normalized to LF):
`3e36a4b760b7d4ec32962c8ace87b9fccca21344025fb06de6898d72c8f1bd7a`.

The complete source/test pins are in
[`tiny_catalog_probe.pins.json`](../../build/azure-native/tiny_catalog_probe.pins.json).
The launcher verifies the supplied manifest digest, every manifest source/test pin and the
binary JDBC digest before any Azure command. These pins are independent of full-fixture
receipts, source heads, candidate schema hashes and validation markers.

- JDK 21 (`JAVA_HOME` with `bin/java.exe` and `bin/javac.exe`); compile with `--release 21`.
- Microsoft JDBC `12.6.1.jre11`, binary SHA-256
  `3b1a70145dbaff98daa70022791e15becfb2b9534cc9e8cfaa1bdba6a3edeb8e`.
- Unchanged test-only 19-component SQL resource SHA-256
  `f2b8aee3a420f9655ec047eaaa66e4299d3aee064476644c6132fe41f00a3dbf`.
- Restricted socket timeout **5000 ms**; statement query timeout **20 seconds**.
  Adaptive buffering stays at the pinned driver's default. Connection retries are disabled.
  Owner synthetic DDL uses the existing owner-like 120000-ms socket bound and 20-second
  statements; it never executes a catalog diagnostic to warm the relevant path.

## Offline/native Windows validation

Run from this branch's repository root with JDK 21 selected. These checks do not load Az,
load a JDBC driver, use live credentials, create validation markers or contact Azure:

```powershell
python -m unittest discover -s build/azure-native -p test_tiny_catalog_probe.py
if ($LASTEXITCODE -ne 0) { throw 'Tiny probe offline Java/pin check failed' }
$PowerShellExe = (Get-Process -Id $PID).Path
& $PowerShellExe -NoProfile -File build/azure-native/test_tiny_catalog_probe.ps1
if ($LASTEXITCODE -ne 0) { throw 'Tiny probe offline launcher check failed' }
```

Expected: three Python tests pass; PowerShell prints
`TINY_CATALOG_LAUNCHER PASS scenarios=21 azureExecuted=false`.
Run the PowerShell command separately in native Windows PowerShell 5.1 and PowerShell 7
when both are supported on the operator host. Linux PowerShell is supplemental only.
The Python harness compiles/runs the actual Java probe against JDBC proxies, covering
fixed execution order, exactly one submission per check, no owner catalog execution,
all classifier categories, untyped communication failures, suppressed/chained failures,
fixed output, query bounds and properties. The PowerShell test executes the actual launcher
with a closed Azure mock inventory, including noisy streams, partial creation, output
injection, cleanup failure/absence checks, authorization and pin refusal.

## Scope and execution, only after separate explicit authorization

Authorize one fresh nonce database on a **dedicated nonproduction Azure SQL test server**.
The server must already exist, have tags `Purpose=ShaleCatalogProbe` and
`Environment=Nonproduction`, and have operator-approved connectivity for this workstation.
The probe neither creates servers/firewalls nor changes existing server permissions.
Tags are a scope fence; the operator must independently confirm that the selected server
and subscription are disposable test infrastructure. Never relabel a production server.
An existing full application database is never selected or adopted.

The launcher requires a matching already-authenticated Az subscription context and an
owner credential for this dedicated server. It creates only a fresh `catalogprobe_<nonce>`
S0 database, generates a new independent contained-user password in process memory, and
compiles/runs the standalone Java program. The role has exactly the six V2 authorities:
database VIEW DEFINITION, SELECT on sys.sql_expression_dependencies, SELECT on the tiny
AiCaseDataDeployment table and SELECT on the three tiny reference tables. The deployment
SELECT remains present for authority parity even though the catalog batch does not use it.
There are no broader grants, permission fallbacks or role escalation.

Inside this new database, synthetic objects match the tiny local authority test: two tiny
owned tables, a computed column/check/default/index, one FK, one view/dependency, one
trigger, one schema-bound predicate/function/policy, the reference tables with one row
each, and empty role definitions needed by the unchanged catalog expressions. This is
synthetic diagnostic DDL, **not** schema import, migrations, Phase1R, V2 installation,
binding, receipts or application fixture construction. No PHI or business data is used.
Audit compatibility: no application mutation or sensitive application read occurs; adding
application audit infrastructure would defeat this isolated synthetic scope. No audit
schema or application audit behavior changes.

After explicit authorization and successful native Windows offline checks, supply local
scope inputs and a locally available, pinned JDBC jar. Do not paste credentials into
commands, files or chat. For example, in the authorized Windows operator session:

```powershell
# $SubscriptionId, $ResourceGroupName, $ServerName and $JdbcJar are local scope inputs.
$OwnerCredential = Get-Credential
try {
    & ./build/azure-native/invoke_tiny_catalog_probe.ps1 `
        -ExpectedDigest '3e36a4b760b7d4ec32962c8ace87b9fccca21344025fb06de6898d72c8f1bd7a' `
        -JdbcJar $JdbcJar -JavaHome $env:JAVA_HOME `
        -SubscriptionId $SubscriptionId -ResourceGroupName $ResourceGroupName `
        -ServerName $ServerName -OwnerCredential $OwnerCredential `
        -AuthorizeTinySyntheticProbe
    if ($LASTEXITCODE -ne 0) { throw 'Tiny probe failed; review sanitized evidence and cleanup status' }
} finally {
    $OwnerCredential = $null
}
```

Preparation is not authorization. No live invocation was used to develop this packet.
The launcher can also verify pins locally with `-ValidateOnly -ExpectedDigest <above>
-JdbcJar <local jar>`; that mode makes no Azure calls and is not a validation marker.

## Fixed checks, evidence and limits

After owner DDL/seeding/grants, before any owner execution of a relevant catalog path,
run these once each in this fixed order, each over a fresh contained-user connection:

1. `DATABASE`: connected database row.
2. `SECURITY`: enabled security policies/predicates.
3. `PRINCIPALS`: database permissions/principals for the probe verifier role.
4. `DEPENDENCIES`: synthetic view dependency.
5. `COMPONENT_1`: exact resource preamble and first metadata expression/emitter.
6. `BATCH_19`: exact unchanged small synthetic 19-component batch.

Earlier check failures do not trigger resubmission, owner comparison, permission changes,
timeout changes or different SQL. Each remaining predetermined check is still submitted
once. A setup failure stops before checks and still enters database cleanup.

Only tab-separated `checkID`, `PASS`/fixed category, elapsed milliseconds and
`connectionClosed=true/false` leave the probe. No result hashes, lengths, data, SQL,
identifiers, targets, credentials, driver text, SQLState, error codes, causes or suppressed
exceptions are emitted. JDBC JUL logging and native stderr are suppressed; the launcher
validates the entire output before forwarding any line. Failures exit the launcher nonzero.

The classifier maps known engine permission/binding codes and JDBC timeout states/types.
A typed SocketTimeoutException in a bounded cause walk maps to SOCKET_TIMEOUT; an untyped
08S01 remains OTHER_SANITIZED because resets/transport loss can share that state. Causes
are inspected for type only and are never rendered. Query cancellation maps to QUERY_TIMEOUT.
Timing alone does not prove a socket timeout. Each elapsed value includes connection setup
and complete result traversal, not a separate first-response stopwatch. A failure during
execute is retained without a wrapper that discards its category.

For each check, connectionClosed is sampled immediately after execution/failure and before
explicit disposal. It is true if opening failed and no connection was obtained. Every
obtained connection is closed before the next check; close/state failures become
OTHER_SANITIZED. A check reporting false still has its connection disposed afterward.
Lifecycle failures use only SETUP/LAUNCHER/CLEANUP IDs with the same fixed evidence fields;
those lifecycle rows use true after JDBC disposal or when no JDBC connection was opened.

Finally, the launcher clears its own credential environment, removes its temporary classes,
and drops only its exact generated database (including its contained user/role/objects).
It marks possible ownership before database creation, so even an ambiguous creation failure
enters cleanup. It confirms that exact database is absent by a fresh management-plane read.
CLEANUP PASS is the absence confirmation; CLEANUP OTHER_SANITIZED means absence was not
confirmed and requires operator resolution in the already-authorized scope. Do not retry
the probe or infer cleanup success from a process exit or suppressed driver exception.

A SOCKET_TIMEOUT near five seconds with a closed connection supports preemption under this
tiny Azure scope. PASS establishes only this synthetic scope's authority/timing. It cannot
reconstruct the prior full schema's cold-cache behavior or establish a production SQL fix.
No owner warm-cache comparison or artificial WAITFOR is included. Provisioning/deletion
latency is Azure-dependent; there is no application fixture build or Maven live invocation.

## Preparation verification

Executed locally on Linux: three new Python/JDK checks, the 21-scenario mocked PowerShell
launcher test (PowerShell 7.4.13), five unchanged Python catalog guards, and all three
unchanged runbook fences (208 diagnostics, two credential rounds/five identities, 18 launcher
scenarios). No Azure ran. Native Windows validation of these new files remains unexecuted.

The repository test selector selects no Maven area for these standalone tooling paths and
suggests the critical default only. The attempted offline critical Maven reactor could not
resolve uncached `org.springframework.boot:spring-boot-dependencies:4.1.1`; it did not reach
tests. Thus broader reactor verification remains blocked. No application source or Maven
configuration was changed to resolve that unrelated environment limitation.

Production SQL, the V2 installer/grants, receipts/hashes, datasource configuration, existing
full-fixture launcher and production deployment state remain unchanged. The next live probe
can be authorized independently using this manifest after its native Windows checks; no
full-fixture markers need regeneration and none are accepted by this launcher.
