# Read-only Azure catalog component diagnostic

## Console credential and marker-boundary correction — 2026-11-04

The owner-authorized disposable attempt stopped at the Windows `Get-Credential` dialog before
resource creation. Owner-confirmed `Test-Path work/azure-native/resource-scope.json` returned
`False`: no scope was created and no cleanup is required. Both validation markers had already
been legitimately consumed. Never restore, reuse or relabel them. This correction executes no
Azure, grants no new attempt, and leaves both native gates OPEN.

The packet uses the previously validated console-only `Get-Credential` override with
`Read-Host -AsSecureString`. No password may be echoed, saved to disk, placed in process arguments,
Maven properties, SQLCMD variables, logs or Git. Section 2 completes login/context selection,
available-region input, secure credentials, strict single-IPv4 validation and read-only generated
resource-group collision checks before consuming either marker. It checks both marker hashes again,
consumes each exactly once, writes the non-secret recovery scope and immediately begins creation.
Cancellation/refusal before this boundary leaves both markers intact and no scope record.
The existing launcher `try/finally`, scope-before-mutation recovery and exact cleanup remain intact.

Live sections 2/3/5 SHA-256 (LF-normalized fence bodies joined by one LF):
`32f1a91f1b1e7389af9b1513a11484e509ba299f23fb202eda5270bd3f6bce12`.
The executable baseline stays `5b88402940fc9950228eb5320bed9b37fc64e359`.
All Azure creation commands, TLS/one-IP firewall, sections 3/5, contained-secret generation,
600-second V2 ceiling, SQL/catalog expressions, receipts/hashes, grants/roles, authority boundaries
and production paths remain unchanged. Full native attestation section 4 remains excluded;
all five sanitized diagnostic categories remain unchanged. Only a fresh disposable scope is allowed.
Audit compatibility is unchanged: no application/PHI mutation or audit migration is introduced.

Test impact reviewed before editing: the five `test_catalog_components.py` guards and
`test_runbook_credentials.ps1`; only the obsolete whole-section-2 hash and early-consumption
expectations require updating. Creation-command, credential-generation and cleanup hashes remain
pinned. The new `test_runbook_launcher.ps1` executes the actual section 2 fence against offline
console/Azure mocks and synthetic temporary evidence. Eighteen scenarios cover login/context
cancellation/null results, region cancellation/empty/unavailable/preflight failures, secure credential
cancellation/empty input, IPv4 cancellation/invalid/IPv6/shorthand/CIDR/zero, name collision and
successful preflight. Every preflight mock requires intact markers and no scope. File-write and
first-mutation mocks enforce `reader,live,scope,mutation`, with one deletion per marker; the first
mutation throws an offline sentinel so no Azure SDK call occurs. Unmocked Azure commands refuse.

Local Python launcher guards: **5 passed**. Static comparison confirms the protected blocks above.
Native Windows PowerShell execution remains **unverified** here (no PowerShell installed);
section 1 now requires the new launcher test as well as the unchanged credential/fence test in the
operator's exact Windows edition/host. No Maven or SQL code changed in this packet delta.
Git transport is unavailable in this environment; source and the narrow commit use connected
GitHub Git-data tools, preserving the full parent tree.

### Fresh Windows evidence after consumption

This protocol supersedes the prior instruction to reuse existing reader evidence: both prior
markers are consumed. Fresh evidence requires a fresh run, never copying/relabeling an old file.
Use the same Windows host/PowerShell edition that will validate the final packet, with JDK 21,
Maven, Git and Python, at `C:\Eclipse\Workspace\shale-parent` in a fresh private shell without
`SHALE_AZURE_*` variables. Set `$ReviewedPacketHead` to the full final commit from this handoff.
Keep the private schema local. Do not execute the live launcher or sections 2–5.

First, regenerate only reader-delta evidence at the unchanged executable baseline:

```powershell
$ErrorActionPreference = 'Stop'
$ReaderHead = '5b88402940fc9950228eb5320bed9b37fc64e359'
if (@(Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*').Count -ne 0) { throw 'Fresh process required' }
if ($env:OS -cne 'Windows_NT') { throw 'Native Windows host required' }
if ($ReviewedPacketHead -cnotmatch '^[a-f0-9]{40}$') { throw 'Set exact final reviewed packet SHA first' }
$ReaderPath = 'work/azure-native/catalog-reader-windows-validation.json'
if ((Test-Path -LiteralPath $ReaderPath) -or
    (Test-Path work/azure-native/catalog-diagnostic-validation.json) -or
    (Test-Path work/azure-native/resource-scope.json)) { throw 'Existing marker/scope forbidden; never overwrite or restore consumed evidence' }
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean tracked source required' }
git checkout --detach $ReaderHead
if ($LASTEXITCODE -ne 0) { throw 'Exact reader checkout failed' }
mvn -B -ntp -pl shale-data -am '-Dtest=AzureCatalogComponentsTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
if ($LASTEXITCODE -ne 0) { throw 'Reader validation failed; STOP' }
[xml]$ReaderReport = Get-Content -LiteralPath 'shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml' -Raw
if ([int]$ReaderReport.testsuite.tests -ne 12 -or
    [int]$ReaderReport.testsuite.failures -ne 0 -or [int]$ReaderReport.testsuite.errors -ne 0 -or
    [int]$ReaderReport.testsuite.skipped -ne 0) { throw 'Require fresh 12 tests, zero failures/errors/skips' }
python -m unittest discover -s build/azure-native -p test_catalog_components.py
if ($LASTEXITCODE -ne 0) { throw 'Require five baseline launcher guards; STOP' }
$ReaderSource = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $ReaderSource -cne $ReaderHead) { throw 'Reader head changed; STOP' }
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean reader source required' }
New-Item -ItemType Directory -Force work/azure-native | Out-Null
@{ codeHead=$ReaderHead; sourceHead=$ReaderHead; hostName=[Environment]::MachineName;
   result='PASS'; scope='CATALOG_READER_DELTA_ONLY'; tests=12; failures=0; errors=0;
   skipped=0; launcherGuards=5; azureAuthorized=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $ReaderPath
git checkout --detach $ReviewedPacketHead
if ($LASTEXITCODE -ne 0) { throw 'Exact final packet checkout failed; STOP' }
```

Then execute **only the complete first PowerShell fence in runbook section 1**, in this same
private session with `$ReviewedPacketHead` still set to the exact final SHA. It requires clean final
source; checks permitted packet-only changes, 25 pinned files/index and both receipts; runs five
updated Python guards, the unchanged `test_runbook_credentials.ps1`, and new
`test_runbook_launcher.ps1` (18 scenarios) using the same PowerShell executable; and checks the
private authoritative-candidate bytes against the unchanged supplied SHA-256. It hash-binds the
new reader marker and writes a fresh live marker with `codeHead` equal to the reader baseline,
`sourceHead` equal to the final packet SHA, same host, packet-only scope and
`azureAuthorized=false`. The reader marker remains baseline-only (`sourceHead=codeHead=ReaderHead`).

Those are the minimal Windows checks: one focused Maven class (12/0/0/0), five baseline Python
guards, then the final packet's section 1 fence. No full Maven suite, Azure setup, V2 installation,
Phase1R rehearsal, unchanged timeout/datasource suite or full native attestation section 4 is needed.
A failure stops the sequence; never paste downstream commands manually. Passing creates fresh
test evidence only and does not authorize Azure. This editing task creates no validation markers.


## Reviewed packet refresh — 2026-11-04

Historical: superseded by the console/consumption correction and fresh-evidence protocol above.

**AZURE NOT AUTHORIZED. Packet prepared for owner review only.**
The owner reports native Windows reader-delta PASS at
`5b88402940fc9950228eb5320bed9b37fc64e359`. This exact commit is now both
`$ExecutableHead` and `$ValidatedHead`, and is the eventual live marker's `codeHead`.
The reader Java and all SQL/security/runtime inputs remain unchanged in this packet refresh.
The historical preparation and validation sections below record earlier work; the current
[operator runbook section 1](native-azure-readonly-operator-runbook.md#1-validate-the-diagnostic-only-delta-on-windows)
supersedes their broader validation commands and pending-reader status.

The existing `work/azure-native/catalog-reader-windows-validation.json` must prove:
both heads equal the exact reader baseline above, the same Windows `hostName`, `result=PASS`,
`scope=CATALOG_READER_DELTA_ONLY`, 12 tests, zero failures/errors/skips, five launcher guards,
and boolean `azureAuthorized=false`. Missing fields, wrong types/counts, host or heads refuse.
Do not manufacture this evidence or rerun unrelated Maven/Azure/Phase1R/V2 rehearsals.

On the exact final packet commit reported in the handoff, set `$ReviewedPacketHead` to that
full SHA and execute **only the first PowerShell fence in runbook section 1**. It checks the
reader marker, the exact packet head, permitted file changes, clean tracked state, pinned bytes/
receipts, five updated launcher guards, and same-host PowerShell fences/credential generation.
It verifies the private candidate digest locally. It writes a new
`work/azure-native/catalog-diagnostic-validation.json` with `sourceHead` equal to that exact
final reviewed commit, `codeHead` equal to the reader baseline, `scope=CATALOG_COMPONENT_PACKET_ONLY`,
same host, `result=PASS`, five guards, the original reader marker's SHA-256, and
`azureAuthorized=false`. It refuses an existing live marker or unresolved scope record.
There is no new Maven command and no database contact in this sequence.

For a future separately authorized attempt, the launcher revalidates and consumes **both**
markers before resource creation. Never relabel, restore or reuse consumed/earlier markers.
A guard failure stops the entire sequence; no manual continuation. Packet validation does
not grant Azure permission, and the live launcher requires a separate explicit owner opt-in.
This refresh does not execute the live launcher, create a live marker, or authorize Azure.

Live sections 2, 3 and 5 SHA-256 (LF-normalized fence bodies joined by one LF):
`fcae9c725c01f9f5cc36fbd1a5847e6a0e0502d4e9c9aed6eab10d073ca613fb`.
Creation/one-IP firewall, cleanup and credential-generation fence bytes remain exact. Section 3
retains the same probe/setup; its only command changes follow setup in the component diagnostic.
Full native attestation section 4 remains excluded. Setup and the 600-second V2 install ceiling
are unchanged. Scope is persisted before mutation; `finally` always invokes exact owned-scope
cleanup, including partial creation. No production access/credentials, retry or fixture adoption.

Diagnostic Maven streams are discarded rather than forwarded. A failure exposes only one fixed
category: `OWNER_TARGET_PREFLIGHT`, `VERIFIER_TARGET_PREFLIGHT`, `OWNER_COMPONENT_READ`,
`VERIFIER_COMPONENT_READ`, or `REPORT_WRITE`. Only exact allowlisted reader messages may select
the first four; missing/malformed evidence, invocation/report failure or an incomplete/non-equal
comparison falls back to `REPORT_WRITE`. No XML, driver messages, SQL, connection targets,
credentials, SIDs, tokens, causes or suppressed exceptions are printed. Raw local Surefire
artifacts remain private. A complete success prints the approved TSV header and exactly 19 rows,
once: ordinal, fixed name, owner/verifier byte lengths, owner/verifier SHA256 and equality.
Every row and equality is checked before any table output. No raw report or partial table is echoed.

Review/test impact: only the three current packet/evidence documents and the existing five
Python launcher guards change. The guards now protect the baseline pins, exact packet identity,
reader proof, dual-marker consumption, authorization fence, diagnostic stream suppression,
fixed categories and complete approved table output, while retaining the original byte checks.
`python -m unittest discover -s build/azure-native -p test_catalog_components.py`: **5 passed**.
Static protected-block comparison and whitespace checks pass. Native Windows PowerShell packet
checks cannot execute in this Linux environment and remain required by section 1; the owner's
passed reader-delta evidence is reused, not claimed as a new packet validation here.

Protected-state review: installer SQL, all catalog expressions, receipts, expected hashes,
grants/roles, authority boundaries, production paths and all timeouts have no tree changes.
Architecture review covered `architecture/codex-prompt-rules.md`, tenancy/RLS, relevant schema/audit
sections, confinement V2 and Azure SQL attestation contracts. Audit compatibility is unchanged:
metadata-only diagnostics add no PHI/entity mutation or audit migration.

## Harness investigation — 2026-11-04

Historical status as of the reader commit; superseded by the packet refresh above.

**BLOCKED pending Windows validation of the revised reader. No further Azure run is authorized.**
The owner reports that the fresh component attempt failed before a complete comparison and
`AZURE_NATIVE_CLEANUP CONFIRMED`. The fixture was destroyed; this is diagnostic evidence only,
not a component difference, receipt drift, acceptance, or permission to retry/adopt.

Investigation baseline: PR #1929 head `56709de2584b9ebcd2cc43e67e80914b4085beef`.
The original reader exits immediately on `execute=false` / update count `-1`; the original valid
mock always returns `true`. The regression now models four leading no-result states plus one
before each SELECT. It reproduces the gap under the proposed sequence, but does **not** establish
the Azure cause: JDBC normally defines false/-1 as exhaustion, and inspection of the pinned
12.6.1.jre11 driver's `SQLServerStatement$1NextResult.onDone` shows that NOCOUNT DML completions
and intermediate non-DML completions are skipped internally. The old failure has no stage evidence.

The reader tolerates 23 no-result advances per gap (four preamble statements plus 19 SETs in the
unchanged resource), resetting after each valid result. It executes SQL once and always advances
with CLOSE_CURRENT_RESULT, with at most 479 advances. Exhaustion without all 19 results still
fails finitely. The allowance also applies after result 19 to detect trailing extra results or
update counts. Zero/positive update counts, missing/extra/duplicated/reordered results, invalid
names, NULL lengths and malformed lengths/digests still fail; result/statement closure is retained.

Other paths shared the same generic failure: datasource construction/open, target validation,
component reads, connection cleanup and report I/O. The failure now adds only one fixed `stage`:
`OWNER_TARGET_PREFLIGHT`, `VERIFIER_TARGET_PREFLIGHT`, `OWNER_COMPONENT_READ`,
`VERIFIER_COMPONENT_READ`, or `REPORT_WRITE`. Connection cleanup belongs to its identity's
target/preflight category; primary read failures retain their category even if cleanup also fails.
Thrown failures contain no driver messages, SQL text, targets, credentials, SIDs, tokens, causes
or suppressed exceptions. Failed comparisons attempt to remove partial/stale TSV output.

Pre-edit test impact: `AzureCatalogComponentsTest` covers traversal, exact expression identity,
comparison and sanitization; the existing five Python launcher guards protect creation, cleanup,
credential bytes and ordering. Existing focused safety/datasource/driver/V2-install/attestation/
migration/Phase1R tests protect the surrounding contracts. The selector maps this Java delta to
cases/security-data (core/data/server). No production source, SQL/resource, receipt, hash, grant,
authority boundary, datasource setting, ordinary 20-second limit or 600-second V2 timeout changes.
Catalog/reference metadata reads introduce no PHI/entity audit event or audit migration.

The validation/live pins below remain historical and unchanged. They intentionally refuse this
executable delta. Do not bypass fences, manufacture/reuse a validation marker or launch Azure.
The owner's delta-only validation instruction supersedes the earlier broad Windows checklist:
run only the focused diagnostic class and five launcher guards described below. No Azure setup,
V2 installation, Phase1R rehearsal or unchanged timeout/datasource suite needs repeating for this
delta. Configured Maven and native Windows results remain required; cached-JUnit checks alone
cannot authorize a live run. Updating/reviewing a launcher baseline is separate work.

Current local verification (Linux; no Azure connection):

- JDK 21 `javac` plus cached JUnit 5.10.3 launcher, using the nine focused classes listed in
  the historical Windows command: 48 executed/passed, zero failures/errors/skips. After adding
  the combined primary-read/close failure assertion, the changed `AzureCatalogComponentsTest`
  class was recompiled and rerun: 10/10 passed. This is supplemental, not Maven/Surefire validation.
- The new leading/interleaved-state method run against the original reader fails with the
  sanitized incomplete-metadata refusal; against the revised reader it passes.
- `python -m unittest discover -s build/azure-native -p test_catalog_components.py`: 5 passed,
  including a rerun after the runbook prose changes.
- `python -m unittest discover -s build/sql-confinement -p test_checkout.py`: 11 passed.
- `python build/sql-confinement/checkout_bytes.py`: 25 pinned files/index and both receipts PASS.
- `python build/test-selection/select_tests.py --base 56709de2584b9ebcd2cc43e67e80914b4085beef --head HEAD`
  checks the unchanged HEAD; explicit `--path` selection of the three uncommitted Java files
  selects cases/security-data and core/data/server. Its affected Maven batch and critical
  `mvn test` were attempted with the configured cache in offline mode; both fail before test
  execution because Spring Boot BOM 4.1.1 is uncached. The focused configured Maven command also
  fails resolving that BOM through the unavailable dependency network path.
- `git diff --check`: PASS. Native Windows/PowerShell and required configured Maven suites remain
  unverified. No Azure resource, fixture reuse, production access or acceptance is claimed.

### Delta-only Windows validation and marker protocol

Final delta review added separate leading-only/interleaved-only cases and explicit private-evidence
canaries. JDK 21 compilation and the cached JUnit rerun pass all 12 diagnostic tests with zero
failures/errors/skips. Both separate traversal regressions fail against the original reader.
The five Python guards and 25 pinned-byte/index/receipt checks pass again. Configured Windows
Maven remains pending; these supplemental checks do not repeat the passed setup/V2/Phase1R work.

Use a clean checkout of the new reader commit reported in the handoff, with JDK 21/Maven in a
fresh Windows shell containing no `SHALE_AZURE_*` variables. The only Maven test selector needed
for this delta is:

```powershell
mvn -B -ntp -pl shale-data -am '-Dtest=AzureCatalogComponentsTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
```

Require a fresh `shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml`:
12 executed, zero failures/errors/skips. The leading-only and interleaved-only tests are separate;
the combined-state test covers missing/extra/reordered results and initial/trailing zero/positive
update counts. The category test checks all five exact allowlisted messages, synthetic SQL,
JDBC target, username/password, SID, token and driver-message canaries, null cause and no suppressed
exceptions, including a primary read failure followed by a failing close. The existing exact SQL
expression comparison test remains part of this same class.

Also run `python -m unittest discover -s build/azure-native -p test_catalog_components.py`:
five passes, protecting the unchanged runbook command blocks, credentials, cleanup and ordering.
Checkout code, resource bytes, SQL expressions, installer, receipts, grants, production source,
V2/Phase1R helpers and timeouts have no diff, so their already-passed Windows/SQL rehearsals carry
forward. No Azure opt-in or private candidate is needed for these two offline commands.

After both pass, record test-only evidence at
`work/azure-native/catalog-reader-windows-validation.json`, with `codeHead` and `sourceHead`
both equal to the full new reader commit SHA, `hostName` equal to the validating Windows host,
`result=PASS`, `scope=CATALOG_READER_DELTA_ONLY`, `tests=12`, `failures=0`, `errors=0`, `skipped=0`,
`launcherGuards=5` and `azureAuthorized=false`. Do not write the live-consumed
`catalog-diagnostic-validation.json` from this delta-only test sequence.

For a subsequent separately reviewed packet, its executable baseline (`$ExecutableHead`,
`$ValidatedHead` and eventual live marker `codeHead`) must be this new reader commit SHA,
never the historical `5867337...` or `56709de...` baseline. Its live marker `sourceHead` must be
the exact final reviewed packet commit, on the same `hostName`, with passing same-source packet
checks. Do not relabel the test-only marker as a live marker. Updating pins, consuming a marker
or launching remains separate, unapproved work. Passing Windows tests alone authorizes no Azure run.

## Historical preparation and validation

Prepared 2026-11-02 against reviewed packet `7a70ea76db26351632b647863a4d814182bc472b`.
Diagnostic code baseline: `5867337464e2caf0eabdf5c8694061ab43d3c8a7`.
Windows/Maven validation and the next fresh Azure attempt remain **PENDING**.
No Azure resource was created and no production connection was opened for this change.

The owner reports successful execution of the unchanged V2 installer under its 600-second
ceiling. Static comparison established all 97 owned names and all 19 component constructions
agree. `CATALOG_RECEIPT (SQL 0)` localizes failure to execution of the packaged catalog stage;
it does not prove that the stored receipt differs from the recomputed catalog hash.

## Implementation and result

- `shale-data/src/test/resources/ai/confinement-v2-catalog-components.sql`: the same owned-name
  declaration and 19 expressions in their original order. Each component is assigned to a
  local `nvarchar(max)` variable and only its ordinal/name, `DATALENGTH` and SHA-256 are selected.
  Local table-variable initialization writes no persistent database object or application row.
- `shale-data/src/test/java/com/shale/data/security/ai/AzureCatalogComponents.java`: bounded JDBC
  result draining, strict 19-result validation and length/hash comparison. NULL, missing, extra,
  duplicated or reordered evidence fails closed. Empty components remain valid hashed empty strings.
- `shale-data/src/test/java/com/shale/data/security/ai/AiCaseDataAzureSqlCatalogComponentTest.java`:
  separate `SHALE_AZURE_CATALOG_COMPONENT_OPT_IN=AZURE_SQL_DATABASE_CATALOG_COMPONENTS` gate.
  Reuses `AiCaseDataAzureSqlDisposableSetupTest.ownerSource` / `ownerConnect` and
  `AiCaseDataAzureSqlDatabaseReadOnlyTest.source("VERIFIER")`, without alternate credentials or
  connection configuration. Requires the existing disposable/independent-credential checks,
  matching live Azure logical-server/database targets, dbo owner, configured verifier, complete
  VIEW DEFINITION and absence of the mutating setup opt-in. SQL/close/configuration failures
  are sanitized without cause/suppressed exception chains.
- `shale-data/src/test/java/com/shale/data/security/ai/AzureCatalogComponentsTest.java`:
  twelve offline expression, metadata, difference, JDBC-closure and error-sanitization tests.
- `build/azure-native/test_catalog_components.py`: five offline launcher safeguard tests.
- [Operator runbook](native-azure-readonly-operator-runbook.md): existing fresh-resource creation,
  owner probe and setup, followed by the diagnostic before owner-secret cleanup. The historical
  full native attestation section is not selected. Setup Java, SQL and timeouts remain untouched.

The only diagnostic artifact is private local `work/azure-native/catalog-components.tsv`.
It has a header and exactly 19 rows: ordinal, fixed name, owner/verifier byte lengths,
owner/verifier SHA-256 and equality. No raw catalog/module text, schema SQL, target names,
credentials, SIDs, tokens or application/production rows are emitted. No receipt comparison is
performed. A difference preserves this metadata and fails with the exact fixed ordinal/name list;
stop and clean up. Nineteen equal rows record a complete context match; clean up and investigate
`confinement-v2-catalog.sql` execution behavior in separate work. Equality does not close either
native gate, prove receipt equality, or authorize another attempt or production deployment.

## Historical packet validation

The earlier broad Windows checklist and live-section digest are superseded by the reviewed
packet refresh above and current runbook section 1. Do not run earlier validation/live fences,
reuse their markers or repeat unrelated rehearsals for this documentation/launcher refresh.

## Review and verification evidence

Reviewed `architecture/codex-prompt-rules.md`, the tenancy/RLS contract, relevant AI schema/audit
sections of `architecture/database-schema.md`, `docs/architecture/phase3b8-confinement-v2.md`,
`docs/architecture/azure-sql-case-attestation.md`, the setup/DataSource/owner helper implementations,
the packaged verifier, installer receipt builders and operator runbook before editing.

Pre-edit test impact: existing Azure acceptance safety and DataSource/driver-timeout tests protect
target, independent identities, TLS, owner timeouts and sanitized failures. `AzureOwnerV2InstallTest`
protects the unchanged 600-second ceiling and setup allowance. Existing attestation, V1/V2 migration
and Phase1R contracts protect the resource/receipt/authority boundaries. Added tests address only the
new component collector and its launcher placement. No production symbol or resource was edited.

Local Linux verification:

- JDK 21 compilation of the new helper/live/offline tests and existing focused classes succeeded.
- Cached JUnit 5.10.3 runner: 45 executed, 45 passed, zero failures/errors/skips. This is supplemental
  verification, not the configured Maven/Surefire or Windows gate. After a final assertion-only
  sanitization adjustment, all seven changed diagnostic tests were recompiled and passed again.
- `python -m unittest discover -s build/sql-confinement -p test_checkout.py`: 11 passed.
- `python -m unittest discover -s build/azure-native -p test_catalog_components.py`: five passed.
- `python build/sql-confinement/checkout_bytes.py`: all 25 files/index and both source receipts PASS.
- `git diff --check`: PASS. Protected installer/catalog/manifest/timeout/setup Java inputs match
  the reviewed remote blobs exactly.
- Repository selector identifies cases/security-data (core/data/server). Focused Maven, affected
  suite and critical reactor cannot execute here: uncached Spring Boot dependency BOM 4.1.1 cannot
  be retrieved through this environment's unavailable network path. Windows PowerShell execution
  is also unavailable. Required Windows/Maven/PowerShell verification remains unexecuted; do not
  claim this packet validated or launch before section 1 passes on the owner host.

Audit compatibility: metadata and the three existing Unicode reference tables are the only reads.
There is no tenant/PHI read, business/admin mutation, new authority or audit schema. No application
audit row is appropriate for this diagnostic. Installer, catalog construction, module/source/catalog
receipts, grants, roles, hashes, credentials and authority boundaries are unchanged.

