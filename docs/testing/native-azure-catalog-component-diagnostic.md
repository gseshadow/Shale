# Read-only Azure catalog component diagnostic

## Before-first phase packet refresh — 2026-11-04

The owner reports complete native Windows validation at executable baseline
`bec7ecbac86a2026bc16dac7a2e8963d7467dd4e`: 15 Java tests, zero failures/errors/skips;
five Python catalog guards; `RUNBOOK_DIAGNOSTICS PASS cases=204 azureExecuted=false`;
`RUNBOOK_CREDENTIALS PASS rounds=2 identities=5 partialFailureCleanup=true`;
`RUNBOOK_LAUNCHER PASS scenarios=18 azureExecuted=false`; final
`BEFORE_FIRST_PHASE_WINDOWS_VALIDATION PASS`. This supersedes the pending Windows status below.

Both packet executable pins now select that exact commit. Only the runbook, this evidence
document and the existing five Python guards change. All Java, SQL/catalog expressions and order,
receipts/hashes, grants/roles/authority boundaries, datasource configuration, ordinary 20-second
query timeout, 600-second V2 timeout and production paths are unchanged. Sections 2/3/4/5 executable
fence bodies are exact, including resource/firewall behavior, console-only credentials, marker
consumption and cleanup/finally. Live sections 2/3/5 digest remains
`3b3a5eef6faff948959ca81714e93c5e35716cb39588f94061b3b07d89f5e03a`.

### Current minimal Windows packet validation

On the same native Windows host that produced the reported PASS, preserve the original
`shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml`
from that validation. Use a clean checkout of the exact final packet `sourceHead` from this
handoff, in a fresh private PowerShell session without `SHALE_AZURE_*` variables. Keep the private
candidate local. Neither validation marker nor an unresolved resource-scope record may exist;
retain prior evidence separately, never overwrite, restore or relabel earlier/consumed markers.

Set `$ReviewedPacketHead` to the full final packet SHA from the handoff and execute **only the
complete first PowerShell fence in runbook section 1**. It verifies exact source/pins, permitted
packet-only changes and clean state; checks the preserved suite name, 15 cases, zero failures/
errors/skips and no failing/skipped child elements; and hashes the original report. It creates
fresh test-only reader/phase evidence with:

- `codeHead=sourceHead=bec7ecbac86a2026bc16dac7a2e8963d7467dd4e`
- same `hostName`, `result=PASS`, `scope=CATALOG_READER_DELTA_ONLY`
- `tests=15`, `failures=errors=skipped=0`, `launcherGuards=5`, `reportSha256`
- `validationMarker=BEFORE_FIRST_PHASE_WINDOWS_VALIDATION PASS`, `azureAuthorized=false`

This records the owner's completed validation; it does not run Maven or relabel an old marker.
If the original report is unavailable, stop and recover only focused phase-validation evidence.
Do not rerun unrelated Phase1R/V2/setup/timeout/datasource or broad Maven tests.

The final packet fence verifies pinned bytes/receipts, runs the five Python guards, then runs the
three unchanged PowerShell scripts in the current host's exact PowerShell edition. It requires
zero exit status and exact single-line output for the 204-case diagnostic parser, two-round/
five-identity credential fence and 18-scenario launcher fence. It verifies the private candidate
against its unchanged digest and rechecks source/report/reader-evidence integrity.

Only after all checks pass does it generate the fresh local live marker
`work/azure-native/catalog-diagnostic-validation.json` with:

- `sourceHead=$ReviewedPacketHead` (exact final packet SHA from this handoff)
- `codeHead=bec7ecbac86a2026bc16dac7a2e8963d7467dd4e`
- same `hostName`, `result=PASS`, `scope=CATALOG_COMPONENT_PACKET_ONLY`
- `readerValidationSha256`, `launcherGuards=5`, `diagnosticCases=204`
- `credentialRounds=2`, `credentialIdentities=5`, `launcherScenarios=18`
- boolean `azureAuthorized=false`

Expected final output: `CATALOG_DIAGNOSTIC_WINDOWS_VALIDATION PASS azureAuthorized=false`.
The launcher rechecks these fields and the exact packet head; marker-consumption ordering is
unchanged. Neither marker grants Azure permission. Do not execute the live launcher without
separate explicit owner authorization. The sole purpose of a future disposable attempt is to
distinguish the verifier's zero-component failure phase: `STATEMENT_CREATE`, `QUERY_TIMEOUT_SETUP`,
`BATCH_EXECUTE` or `BEFORE_FIRST_RESULT`. Section 4 remains excluded; both native gates stay OPEN.

Pre-edit review: existing packet pins/evidence protocol, five Python guards and the unchanged
PowerShell diagnostic/credential/launcher fences; repository prompt rules and prior relevant
tenancy/RLS, schema/audit, confinement V2 and Azure attestation reviews carry forward.
Metadata/evidence only: no PHI/entity mutation, audit event or migration. Local Python guards:
five passed. Protected-section, full-tree and narrow whitespace checks pass. The owner's native
phase-delta PASS is preserved as reported evidence; Windows execution of this final refreshed
packet remains pending on the owner host (PowerShell is unavailable here). No Maven or database
rehearsal was repeated. Git-data tools preserve the full parent tree. This task executes no Azure,
creates no validation marker and grants no Azure authorization.


## Historical before-first operation diagnostic — 2026-11-04

The latest separately authorized disposable attempt passed setup, V2 installation and cumulative
verification, failed at `VERIFIER_COMPONENT_READ; component=0:before_first_component`, and cleaned
up with `AZURE_NATIVE_CLEANUP CONFIRMED`. Verifier target/preflight had previously passed.
This is evidence only; no permission/visibility change, fixture adoption or new Azure attempt.

The five stages and existing component ordinal/name semantics are unchanged. Only a zero-component
read failure adds one fixed `AzureCatalogComponents.ReadPhase` value:

- `STATEMENT_CREATE`: `connection.createStatement()`
- `QUERY_TIMEOUT_SETUP`: the unchanged `statement.setQueryTimeout(20)`
- `BATCH_EXECUTE`: the existing `statement.execute(sql())` call, including resource evaluation
- `BEFORE_FIRST_RESULT`: execute returned, but traversal failed/exhausted before the first fixed
  component slot; this includes `getUpdateCount()`, `getMoreResults()` and before-first cleanup

The phase is assigned before its operation and captured outside JDBC resource scopes, discarding
driver/SQL/target/credential/SID/token text, causes and suppressed close failures. Nonzero-component
messages have no phase suffix. The single batch execution, SQL and finite traversal budget remain
unchanged. No error text or JDBC evidence can supply a phase, ordinal or component name.

Section 3 now constructs complete candidate failure messages from the exact five stages, fixed
ordinal/name pairs and four phases. It preserves exact matches, including legacy stage-only and
zero-component messages. Phases are accepted only with component zero on owner/verifier read stages.
Unknown values, wrong ordinal/name pairs, extra text/fields, malformed XML, missing evidence or
multiple messages fall back to a constructed sanitized `REPORT_WRITE` message. Both report parsing
and the final catch use the same allowlist; arbitrary exception text is never returned or echoed.

Only this diagnostic parser changes section 3 executable content. Sections 2, 4 and 5, console-only
credentials, marker consumption, resource/one-IP firewall commands and cleanup/finally are exact.
The new live sections 2/3/5 digest is
`3b3a5eef6faff948959ca81714e93c5e35716cb39588f94061b3b07d89f5e03a`.
The digest guard and Python expectation are updated together. Executable pins remain at the prior
validated baseline `8d6bfa11b31a0334e16cc99c81cac3f35bfada48`; their permitted-delta fences
intentionally reject this Java change. Earlier packet-validation/marker protocols below are
historical until this delta passes Windows and a separate packet refresh is reviewed.
Do not create/relabel markers, execute the live launcher or treat this edit as Azure authorization.

### Required offline Windows validation

Use the exact handoff commit in a clean native Windows checkout with JDK 21/Maven/Python, in a
fresh private PowerShell session without `SHALE_AZURE_*` variables. Run:

```powershell
mvn -B -ntp -pl shale-data -am '-Dtest=AzureCatalogComponentsTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
if ($LASTEXITCODE -ne 0) { throw 'Focused diagnostic tests failed' }
python -m unittest discover -s build/azure-native -p test_catalog_components.py
if ($LASTEXITCODE -ne 0) { throw 'Five launcher guards failed' }
$PowerShellExe = (Get-Process -Id $PID).Path
foreach ($Test in @('test_runbook_diagnostics.ps1','test_runbook_credentials.ps1','test_runbook_launcher.ps1')) {
    & $PowerShellExe -NoProfile -File (Join-Path 'build/azure-native' $Test)
    if ($LASTEXITCODE -ne 0) { throw 'Offline PowerShell diagnostic/fence tests failed' }
}
```

Require fresh `AzureCatalogComponentsTest` Surefire XML: 15 tests, zero failures/errors/skips;
five Python passes; all three PowerShell PASS markers. The expanded Java phase test injects all
four before-first phases for both identities, including non-result exhaustion, update-count/
advancement failure and close failures carrying SQL/driver/credential/SID/token canaries. Existing
19-slot, malformed/reordered evidence, five-stage and no-cause/suppression assertions remain.
The new PowerShell test executes the exact runbook parser and report-failure/catch boundary with
synthetic private XML: all stages, every ordinal/name, both read identities, every phase, legacy
messages, invalid combinations, canaries, multiple messages and missing/malformed reports.
The existing credential/launcher fences remain unchanged. No database or private candidate is
needed; do not repeat setup, V2, Phase1R, timeout/datasource or broad Maven rehearsals for this delta.

Pre-edit review carried forward repository prompt rules, tenancy/RLS, relevant schema/audit,
confinement V2 and Azure attestation contracts; inspected all reader/component/stage references,
the five Python guards, runbook failure routing and both existing PowerShell fence scripts.
No production path, SQL/catalog expression/order, receipt/hash, grant/role/authority boundary,
datasource configuration, query timeout or V2 timeout changes. Metadata-only diagnostics need no
PHI/entity audit event or migration.

Local supplemental JDK 21 compilation with the real dependency closure and cached JUnit 5.10.3:
15 passed, zero failures/errors/skips. Five Python launcher guards pass. Protected-section,
full-tree delta and narrow whitespace checks pass. Focused configured Maven is blocked before
tests by the uncached Spring Boot BOM 4.1.1 and unavailable dependency transport. Native Windows
Maven and PowerShell execution of the new parser test remain unverified here (no PowerShell).
Commit/push uses connected GitHub Git-data tools. No Azure was executed or authorized.


## Historical localization packet refresh — 2026-11-04

Owner-reported native Windows validation is complete at executable baseline
`8d6bfa11b31a0334e16cc99c81cac3f35bfada48`: `AzureCatalogComponentsTest` 15 tests,
zero failures/errors/skips; five Python launcher guards; final
`COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS`. This supersedes the pending Windows status
in the historical localization notes below. No unrelated validation needs repeating.

The packet pins (`$ExecutableHead` / `$ValidatedHead`) now select that exact commit.
The final packet `sourceHead` is the exact new commit reported in the handoff; it is deliberately
separate from executable `codeHead`. Only the runbook, this evidence document and five existing
Python guards change. Java, production paths, setup/SQL, all 19 catalog expressions, V2 receipt/hash,
grants/roles/authority boundaries, datasource configuration and 20-/600-second timeouts are unchanged.
Sections 2, 3 and 5 executable fence bodies remain byte-identical, as do resource creation, the
console-only credential flow, marker-consumption ordering, one-IP firewall, cleanup/finally and
section 4 exclusion. Live digest remains
`32f1a91f1b1e7389af9b1513a11484e509ba299f23fb202eda5270bd3f6bce12`.

### Current minimal Windows packet validation

On the same native Windows host that produced the reported localization PASS, preserve the
original `shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml`
from that validation. Do not edit/copy/relabel a report from another head. In a fresh private shell
without `SHALE_AZURE_*` variables, use a clean checkout of the exact final packet from this handoff.
Retain the private candidate locally. Neither validation marker nor an unresolved resource-scope
record may already exist; retain prior evidence separately, never restore consumed markers.

Set `$ReviewedPacketHead` to the full final packet SHA, then execute **only the complete first
PowerShell fence in runbook section 1**, in that same session. It verifies exact source ancestry,
permitted packet-only changes and clean state. It checks the preserved Surefire suite name,
15 test cases, zero failures/errors/skips and absence of failing/skipped child elements, and hashes
the original report. It creates fresh test-only reader/localization evidence with:

- `codeHead=sourceHead=8d6bfa11b31a0334e16cc99c81cac3f35bfada48`
- same `hostName`, `result=PASS`, `scope=CATALOG_READER_DELTA_ONLY`
- `tests=15`, `failures=errors=skipped=0`, `launcherGuards=5`
- `validationMarker=COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS`, `reportSha256`
- boolean `azureAuthorized=false`

This newly recorded evidence describes the owner's completed localization validation, not a new
Maven run. If the original report is unavailable, stop: only focused localization evidence must
be recovered, never a broad Maven/setup/Phase1R/V2/timeout rehearsal. No old marker is relabelled.

The fence then verifies pinned bytes/receipts, runs exactly five Python launcher guards and the
existing PowerShell credential and mocked launcher fences in the same Windows PowerShell edition,
checks the private authoritative candidate against its unchanged digest, and rechecks source and
evidence integrity. Only after every check passes does it generate
`work/azure-native/catalog-diagnostic-validation.json`, with:

- `sourceHead=$ReviewedPacketHead` (exact final packet SHA from this handoff)
- `codeHead=8d6bfa11b31a0334e16cc99c81cac3f35bfada48`
- same `hostName`, `result=PASS`, `scope=CATALOG_COMPONENT_PACKET_ONLY`
- `readerValidationSha256` binding the fresh test-only evidence, `launcherGuards=5`
- boolean `azureAuthorized=false`

Expected final output: `CATALOG_DIAGNOSTIC_WINDOWS_VALIDATION PASS azureAuthorized=false`.
No Maven, selector/broad suite, setup, Phase1R, V2 installation, timeout test, Azure command or
live launcher runs in section 1. A guard failure stops the sequence; never paste downstream
commands manually. This editing task creates no validation marker and grants no Azure authority.

The existing live launcher remains separately authorized work. Its preserved section 3 does not
gain a new output parser: inspect only the sanitized component-localization failure in the private
Surefire evidence after a separately authorized attempt; do not forward raw XML/driver output.
The sole purpose of that future disposable diagnostic is to identify the fixed verifier ordinal/name
causing `VERIFIER_COMPONENT_READ`. Do not change permissions or visibility before it is known.

Pre-edit test impact: the existing five Python guards protect packet pins/evidence, credential bytes,
resource commands, marker ordering, cleanup, section 4 exclusion and diagnostic output. Only the
obsolete baseline/count expectations are updated; existing PowerShell fence scripts are unchanged.
Architecture review carries forward the prompt rules, tenancy/RLS, schema/audit, confinement V2 and
Azure SQL attestation contracts. Metadata/evidence only: no PHI/entity mutation or audit migration.
Local Python guards: five passed. Static protected-section and full-tree delta checks pass.
Native Windows execution of this refreshed packet's existing PowerShell fences remains pending
on the owner host (PowerShell is unavailable here); the supplied localization PASS is preserved
as owner-reported evidence. Git transport remains unavailable; commit/push uses connected GitHub
Git-data tools. No Azure was executed or authorized.

## Historical localization implementation — 2026-11-04

The latest owner-authorized disposable diagnostic completed owner setup, V2 installation
(~36.9 seconds), cumulative verification and cleanup (`AZURE_NATIVE_CLEANUP CONFIRMED`).
No `catalog-components.tsv` was created. The sanitized failure identified
`stage=VERIFIER_COMPONENT_READ`. This is evidence only; no component difference, permission
fix, acceptance, fixture adoption or further Azure attempt is authorized.

This diagnostic delta preserves all five stages. Only `OWNER_COMPONENT_READ` and
`VERIFIER_COMPONENT_READ` additionally report `component=<ordinal>:<fixed name>`.
Names come exclusively from `AzureCatalogComponents.NAMES`, never JDBC evidence.
Before a result slot is identifiable (including execute and leading traversal failure), the
label is `0:before_first_component`. Once a result is exposed, its expected ordinal is
tracked before obtaining/reading/closing it. While advancing after a valid result, the label
is the next expected ordinal, capped at 19 for terminal checks and statement cleanup.
Thus an advancement failure at `13:dependencies` identifies the expected traversal slot,
not proof that the driver was executing that expression: JDBC may prefetch. Malformed or
reordered metadata cannot supply the label and still fails closed. SQL is executed once;
its expressions, ordering, traversal budget and resource closure remain unchanged.

Failure sanitization occurs outside JDBC resource scopes, then again at the public comparison
boundary. Driver/SQL/target/username/password/SID/token text, causes and suppressed close
failures are discarded. The offline matrix injects failures for both identities at every
component's result acquisition, next, ordinal/name/length reads, NULL check and close, and
advancement to slots 2–19. Each primary failure is followed by a failing connection close.
Separate checks cover execute/leading failures, terminal statement close, malformed evidence,
all five stages, stale-report deletion and private-data canaries in rendered stack traces.

Pre-edit review: repository prompt rules, tenancy/RLS, schema/audit, confinement V2 and Azure SQL
attestation contracts; existing reader/stage/sanitization tests and five launcher guards.
Audit compatibility: metadata-only diagnostics; no PHI/entity mutation, audit event or migration.
No setup/executable SQL, 19 catalog expressions, V2 receipt/hash, grant, role, authority boundary,
owner/verifier datasource, ordinary 20-second query or 600-second V2 timeout changes.
Resource/one-IP firewall commands, cleanup and section 4 exclusion are unchanged.

The runbook is unchanged. Its live sections 2/3/5 digest remains
`32f1a91f1b1e7389af9b1513a11484e509ba299f23fb202eda5270bd3f6bce12`.
Its historical executable/marker fences intentionally reject this Java delta; do not update
pins, restore consumed markers or execute the launcher. A subsequent packet review and Azure
authorization are separate work. The older Windows/marker protocols below are historical
and do not authorize marker creation or a live run for this delta.

### Required Windows tests for this delta

At the exact handoff commit, in a clean JDK 21/Maven Windows checkout and fresh shell without
`SHALE_AZURE_*` variables, run only these offline delta checks (no private candidate needed):

```powershell
mvn -B -ntp -pl shale-data -am '-Dtest=AzureCatalogComponentsTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
python -m unittest discover -s build/azure-native -p test_catalog_components.py
```

Require fresh `TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml`: 15 tests,
zero failures/errors/skips; require five Python passes. Do not run the separately opted-in
`AiCaseDataAzureSqlCatalogComponentTest`, setup, V2 installation or live launcher.
Do not create/relabel any validation marker from these commands.

Local supplemental verification: real dependency closure compiled with JDK 21 and cached
JUnit 5.10.3; all 15 tests pass, zero failures/errors/skips. Five Python guards pass.
The selector maps the three Java files to cases/security-data (data/server plus dependencies).
Focused configured Maven, the selected affected batch and critical reactor were attempted
offline; all stop before tests because Spring Boot BOM 4.1.1 is uncached and dependency
transport is unavailable. Native Windows/Maven verification remains pending; this delta is
unverified at that required scope. Git transport is unavailable; the narrow commit uses
connected GitHub Git-data tools with the full parent tree preserved. No Azure was executed.

## Historical console credential and marker-boundary correction — 2026-11-04

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

### Superseded reader regeneration protocol

The former 12-test baseline regeneration sequence is retired. Use only the current localization
protocol above and runbook section 1: verify the preserved 15-test report at the validated
`8d6bfa11b31a0334e16cc99c81cac3f35bfada48` baseline and create fresh evidence; no Maven rerun
is required for final packet validation. The historical sections below are evidence, not commands
to execute or authority to create markers or run Azure.

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

