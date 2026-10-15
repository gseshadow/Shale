# Authentication module permission cutover — 2026-10-15

**Latest assessment:** see [synthetic Azure invocation validation](#synthetic-azure-invocation-validation-follow-up--2026-10-15). Production activation, retirement and installation remain blocked; cache sampling cannot certify nonuse.

## Synthetic Azure invocation validation follow-up — 2026-10-15

**Production monitoring, retirement and installation remain BLOCKED.** The separately isolated
[executable operator package](../sql/auth/invocation-validation/README.md) tests raw module-entry/exit
payload privacy and bounded coverage in a separately approved, empty synthetic Azure SQL Database.
It supplies discovery, guarded setup, synthetic workload, full raw collection, offline assertions,
stop and exact-owner cleanup. It provisions nothing and has not connected to Azure. No live candidate
is supplied; synthetic diagnostic start/end sessions must never be activated against Shale or its copies.

Operator evidence at **2026-10-15 22:29:04 UTC**: the #1875 sampler ran against Shale, exit 0;
each of `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure`, `dbo.sp_login_mark_success` had
`CachedPlanCount=0`, NULL execution counts/times and `INCOMPLETE_CACHE_OBSERVATION`.
Record these as **unavailable cache evidence, not zero usage**. Query Store AUTO also retained no
statements for them. These attributed reports do not establish nonuse; no original output was supplied
or regenerated in this task. Preserve the original captures and #1874 retirement/rollback, unchanged
preflight, Stage A/B, desktop compatibility and RLS prerequisites. The live-update tenant/user
negotiate authorization concern remains separate and OPEN.

Live base fetched: `be55d7187cddc2bc7bf2fbfbbfd00a19c4b614e4`; GitHub reports #1875 merged at
2026-10-15 22:21:20 UTC, and local ancestry confirms its merge in the base. Task branch:
`codex/azure-synthetic-invocation-validation-20261015`; a separate worktree preserves the prior checkout.
Reviewed completely: AGENTS.md, architecture/codex-prompt-rules.md and this runbook; database-schema
and tenancy-and-rls routing and the existing auth/module/lifecycle/permission fixture and launcher tests
were inspected before edits. No application symbol, auth behavior, permission, preflight, historical
capture, retirement/rollback, Stage A/B or RLS contract changes. Audit integration needs no application
schema/event: synthetic DDL/security metadata and retention use private operator change control.

Microsoft documentation and local supported metadata were inspected before choosing this bounded
experiment. Local module_start/module_end expose intrinsic statement plus configurable collect_statement;
setting it to zero emitted an empty statement field locally. That is **not Azure payload verification**.
The package requires actual Azure metadata review before setup, rejects full-payload privacy violations,
checks exact module/database predicates, counts and health, and deliberately demonstrates/rejects bounded
target loss. Cross-database context, restart/failover and durable operational coverage remain explicit gaps.
Even successful synthetic assertions always refuse production activation and cannot close #1874's drain gate.
The package lists minimum actual resource facts, provenance, identity, experiment/review and retention
approvals; none is invented or granted here. If privacy/coverage cannot be established, use the documented
practical alternatives or keep retirement blocked without weakening any gate.

Verification results and exact local commands will be recorded below before review.

## Post-#1874 evidence and bounded usage observation — 2026-10-15

**Retirement and installation remain BLOCKED. Observation is not permission-change or installation
approval.** This task fetched live `codex/latest` at `572b96756c1e2a6573be5b1887ccbb70cdcfe8a1`;
GitHub reports #1874 merged, and local Git ancestry independently confirms its merge is in the base.
Work branch: `codex/legacy-login-observation-20261015`. Original captures, unchanged preflight,
#1874 exact retirement/rollback safeguards, installed-client compatibility, Stage A/B gates and every
RLS prerequisite remain authoritative. No live retirement, Stage A installation, Stage B restriction,
RLS change or deployment has occurred according to the operator; none was performed in this task.
The historical records below remain attributed to their original revisions.

### Evidence ledger and limits

| Provenance | October 15 evidence | What it establishes / does not establish |
| --- | --- | --- |
| Operator report after #1874 | Native Windows disposable SQL harness: credential_identity, remembered_rotation, runtime_isolation PASS; exit 0 | Supersedes the pending native Windows retest for the operator's reported post-#1874 run. Exact checkout/tool/artifact hashes were not supplied. Codex did not execute that Windows run; installed desktop acceptance and actual-login mapping remain open. |
| Operator consumer inventory | Desktop 1.0.136; app.myshale.com through Shale API; Azure live updates; no other consumers known | Working inventory only. Does not establish exhaustive fleet, job, integration, dormant/offline-client or dynamic/wrapper coverage. |
| Operator Azure Function inventory | Function App `negotiate` has HTTP functions `negotiate` and `live-case-updated`; settings list has Web PubSub, Storage and monitoring, with no observed SQL settings/connection strings | Supports a messaging-only hypothesis for the observed component. Settings-list absence is not proof that every code/configuration/identity path lacks SQL access. |
| Operator artifact provenance | Both source files and DLL supplied previously; DLL downloaded with Azure **Download app content**, app settings excluded | Attachments are absent from this session. They were not inspected or rehashed here. Preserve them privately; no binaries or private captures in Git. |
| Prior assistant static inspection, relayed in this task | DLL startup/function methods consistent with supplied source, Web PubSub operations, no SQL calls or legacy-login procedure references found | Prior static evidence only; neither independent inspection in this task nor runtime/exhaustive nonuse proof. |
| Operator artifact/deployment identifiers | DLL SHA-256 `19acf16bd78f754d147ebbb7ad5050eb057fa5692a72815238e7209b713361e1`; active deployment displayed `845a735`, February 25, 2026, VS Code push / Run-From-Zip | Digest identifies the reported artifact, not a digest independently computed here. Deployment ID is **not established as a source Git commit**; no assumed source/deployed parity. |
| Operator Query Store report | READ_WRITE, AUTO, retention setting 30 days; each exact legacy procedure has zero captured statements, zero retained executions, NULL first/last execution | No retained usage found by that query. AUTO can filter queries; retention configuration is not a complete 30-day observation record. Cannot certify nonuse. |
| Existing automated tests | #1874 records local synthetic SQL, focused, selected and critical passes | Regression evidence for the captured bodies and safeguards, not deployed usage or installed-fleet acceptance. New checks for this task are recorded separately below. |
| Independently verified here | Live GitHub merge metadata, fetched Git base/ancestry, tracked source/runbook and current official Microsoft documentation | No Azure connection, attachment inspection, deployed runtime verification or new operator approval is implied. |

**Separate live-update authorization finding — OPEN:** the prior source review reports that `negotiate`
derives tenant/user from request parameters and grants join/send group roles without Shale-session
validation in the reviewed method. That is a tenant authorization concern, separate from SQL legacy
procedure use. Platform authentication/other upstream controls and full deployed behavior are not
established here. Track independent owner review of authenticated tenant/user derivation, group-role
scope and negative cross-tenant/session tests. Do not modify or deploy that component in this task;
absence of observed SQL references does not resolve its authorization concern.

### Smallest supported method and concrete limitation

Prefer an **already approved, adequate** observation record if an owner can supply one with exact
module coverage, UTC boundaries, loss/gap/health evidence and retention covering the window. None is
established by the supplied Query Store AUTO result. Microsoft’s [Query Store best practices](https://learn.microsoft.com/en-us/sql/relational-databases/performance/best-practice-with-the-query-store) describe AUTO filtering and automatic cleanup/state transitions.
Do not change its capture mode, query raw SQL text,
or enable broad SQL Audit/RPC/batch/statement tracing to compensate.

The smallest supported additional read is the existing
[`sys.dm_exec_procedure_stats`](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-exec-procedure-stats-transact-sql)
cache counters, filtered by **current database ID and exactly the three object IDs**, never name patterns:
`dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure`, `dbo.sp_login_mark_success`.
The separate [sample candidate](../sql/auth/permission-cutover/sample-legacy-login-usage.sql) reads
only module identity, UTC sample/cache/last-execution times and counts; it creates no persistent object,
event, target, audit or database setting. It uses the existing approved dbo metadata connection,
not auth/runtime impersonation or new grants. Microsoft documents service-tier-specific DMV authority:
Basic/S0/S1 and elastic pools require server/Entra admin or `##MS_ServerStateReader##`; other Azure SQL
Database service objectives permit VIEW DATABASE STATE or that server role. If the existing identity
cannot read it, stop; this plan does not authorize granting additional authority. Dbo database context
alone does not establish that service-tier authority. Wrong database, missing/hidden modules, non-UTC
engine, ambient transaction or a DMV error must stop the sample.

**This is a positive-evidence fallback, not a sufficient retirement observation system.** Microsoft
states counters cover completed executions of cached plans; rows disappear on eviction. Aborted,
in-flight, recompiled/evicted-between-samples and pre-entry failures can be missed. A retained last
execution in the approved UTC window is evidence to investigate use and block retirement. Earlier
retained executions are historical context. Counts are cumulative per cached plan: do not sum successive
snapshots or claim their difference is an exact call count. Multiple plans are aggregated; changes in
plan count/earliest cache time/counters can reveal some resets, never establish continuity. A missing
row yields NULL retained count/times, not zero calls. Every row says `INCOMPLETE_CACHE_OBSERVATION`,
even when collection succeeds. A quiet series **cannot close #1874's drain/nonuse gate**.

UTC is supported by Microsoft's [GETDATE documentation](https://learn.microsoft.com/en-us/sql/t-sql/functions/getdate-transact-sql):
Azure SQL Database follows UTC; Managed Instance is an exception. The sampler refuses a nonzero engine
offset rather than silently labeling local SQL Server timestamps UTC. Its sampling boundary follows
materialization; the view is not an atomic invocation ledger. Catalog changes, object-ID reuse or
recreated modules invalidate mapping: serialize module metadata during sampling and review the original
captured-definition baseline separately before/after, without replacing original captures.

### Why no activatable event-session candidate is supplied

Microsoft's [Azure SQL Extended Events documentation](https://learn.microsoft.com/en-us/azure/azure-sql/database/xevent-db-diff-from-svr)
confirms database-scoped `ON DATABASE` sessions, common event metadata DMVs, in-memory targets, and
`event_file` persistence to Azure Storage with separate credential/RBAC requirements. That is platform
support, **not verification of a particular Azure build's complete event payload**. This session has
neither a version-specific Azure event schema nor raw synthetic event payloads establishing that a
module-entry/exit event excludes every forbidden field. No event configuration can be labeled privacy
verified here. The shipped sampler emits no event payload at all; it does not create a new event pipeline. This is an evidence limitation, not a claim that Azure SQL cannot support safe events.
The permitted bounded fallback above therefore stays insufficient for negative proof.

A separately approved follow-up can first inspect available `sqlserver.module_start`/`module_end`
metadata through [`sys.dm_xe_object_columns`](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-object-columns-transact-sql)
(readonly/data/customizable fields, defaults and configurable controls), without creating a session.
Then, on a disposable Azure SQL database with synthetic modules/arguments, review **entire raw event
payloads before activation in Shale**: every intrinsic data field, action, causal/activity metadata and
target serialization, not just displayed/exported columns. Verify SQL text/statement fields are absent
or disabled in the emitted payload itself, not masked afterward. No SQL text, arguments, email,
credentials, hashes, tokens or application data may enter the event pipeline even if the chosen target
stores only counts. Reject unexpected fields and unsupported options. Do not infer that no ACTION
clause means no intrinsic statement field. Raw validation uses synthetic sentinels only, never real
legacy calls against Azure. Review version/build drift before later activation.

If that succeeds, review a distinct exact-object-predicate module-entry counter candidate for each
procedure, with no actions and only necessary identity/timing/count fields. Three filtered
`event_counter` sessions could avoid histogram collisions; per-session ownership, monitoring overhead,
UTC collection boundaries and restart continuity still need validation. A single histogram is smaller
but Microsoft's [target documentation](https://learn.microsoft.com/en-us/sql/relational-databases/extended-events/targets-for-extended-events-in-sql-server)
warns collisions can misattribute procedure counts and zero buckets. Aggregating targets **do not**
make an unsafe source payload private. A ring buffer stores raw events, loses old events and may
truncate its XML at 4 MB. It is not a durable multi-day record. Event files add Storage access,
retention/rollover, credentials and cleanup scope; none is approved or created here. Do not use
NO_EVENT_LOSS as a blanket completeness guarantee or block application work to make observation perfect.

Any later session candidate must supply tested exact Windows create/start/status/collect/stop/drop
commands, unique task names and saved identity/configuration guards; refuse preexisting sessions and
unexpected configuration. It must not adopt/drop existing telemetry, shared credentials or storage.
**There are deliberately no Azure XE activation/drop commands in this revision:** safe event payloads
and a complete observation design are not verified. Approval of this PR authorizes no such activation.

### Coverage, health, window and decision

| Risk | Required treatment |
| --- | --- |
| Missed/aborted/pre-entry calls | Cache counters only report finished executions. Module-entry events would still miss permission/compile failures before entry; exit-only events can miss aborted calls. Neither proves attempted-call absence. |
| Nested/dynamic invocation | Filter the target module identity, not caller RPC text. Wrappers/dynamic SQL that reach it may contribute counters; calls that fail before entry do not. Validate nested, same-owner wrapper and dynamic paths in any later event candidate; inventory remains mandatory. |
| Event loss/aggregation | Future event candidates must record session/target state, loss counters and collection watermarks before/after each interval. Counter aggregation and histogram collisions need explicit testing. Unknown loss is incomplete; no dropped-event counter alone proves health. |
| Restart/failover/cache eviction | Cache state can vanish; event targets/session counters can reset and stopped sessions stop capturing. Preserve prior snapshots, mark a gap and start a new interval. STARTUP_STATE alone cannot recover downtime or in-memory data. |
| Retention/capture gaps | Query Store 30-day setting is not 30 days of AUTO coverage. Local snapshots exist only after successful collection. Future files need bounded rollover/lifecycle and retrieval verification. Missing files, inaccessible storage, partial exports or stopped capture invalidate the affected interval. |
| Monitoring health | Record expected/actual UTC collections, operator availability, target/database identity and known maintenance privately. Nonzero SQLCMD exit, timeout, missing/invalid three-row output, stale status, clock reversal, changed object IDs or cache discontinuities are gaps/UNKNOWN. Successful polling is collection health only, never complete call coverage. |

**Window proposal, not an approved schedule:** first obtain actual activity dates/frequency for desktop
1.0.136 (password login and checked/unchecked remembered startup/restart), Shale API/web authentication,
and live-update negotiation/reconnect. Review already approved records/configuration; do not generate
legacy Azure calls or collect user identities/payloads for this inventory. Obtain owners' scheduled-job
inventory, including disabled/monthly/quarterly jobs, integrations, maintenance scripts, stored wrappers,
older/offline desktops and effective connection targets. Intervals, timezones, next due runs, idle-client
coverage and fleet completeness are currently **UNKNOWN**; no job schedule or complete fleet is asserted.

After inventory, propose **14 consecutive days** as an initial operational review window to include two
weekly activity cycles *if owners confirm those cycles actually occur*. Start only when the known
consumers will be active; include their normal workdays, representative restarts and the longest
inventoried job interval plus a completed due run. If a monthly/quarterly job exceeds that bound, extend
to its actual due cycle or keep that consumer unresolved for its own approved disposition; do not mark
it drained. Dormant/offline clients require separate compatibility/drain evidence. Missing activity or
health gaps extend/restart the affected interval under owner review. Fourteen days and five-minute
sampling below are proposals, not evidence of actual schedules or a guarantee that calls are captured.

At the endpoint, submit per-procedure use/unknown findings, exact UTC intervals, gaps, telemetry health,
consumer activity and inventory coverage to the security owner. Positive use blocks retirement pending
caller migration/disposition; no observed use with this fallback remains **UNKNOWN / insufficient**.
Do not substitute acceptance of cache sampling for #1874's independently approved inventory, caller drain,
installed 1.0.136 compatibility, actual principal mapping, exclusive change window, baseline/rollback
identities, unchanged Stage A preflight, Stage B fleet/job gates or RLS prerequisites.

### Exact Windows fallback lifecycle — only after separate read-only collection approval

No commands below were run against Azure. Approve the fixed sampler revision, existing connection
identity/target, 14-day maximum initial collection, proposed five-minute cadence, operator staffing,
private destination and retention first. The connection array is the existing approved mechanism;
no secrets/URLs are supplied here. Do not add SQL/query echo or command-line passwords. This is a
manual per-sample procedure: no unattended scheduler or background Azure job is installed. A staffed
collector may invoke the collect block at the approved cadence; missed rounds remain recorded gaps.
Use a fresh PowerShell session and keep its exact path/token/file list for guarded local cleanup.

```powershell
# START: local bookkeeping only. $privateObservationParent must already be an approved private folder.
$ErrorActionPreference = 'Stop'
$observationToken = [guid]::NewGuid().ToString('N')
$privateObservationParent = (Resolve-Path -LiteralPath $privateObservationParent).Path
$observationDir = Join-Path $privateObservationParent "shale-legacy-observation-$observationToken"
if (Test-Path -LiteralPath $observationDir) { throw 'Refuse existing observation directory' }
New-Item -ItemType Directory -Path $observationDir | Out-Null
$markerPath = Join-Path $observationDir 'owner.txt'
$startPath = Join-Path $observationDir 'start-utc.txt'
$stopPath = Join-Path $observationDir 'stop-utc.txt'
Set-Content -LiteralPath $markerPath -Value $observationToken
Set-Content -LiteralPath $startPath -Value ([DateTime]::UtcNow.ToString('o'))
$ownedObservationFiles = @($markerPath, $startPath)
$observationStopped = $false
$observationStartUtc = [DateTime]::Parse((Get-Content -LiteralPath $startPath)).ToUniversalTime()

# STATUS: local status only, NOT Azure capture health or zero-use evidence.
Get-Content -LiteralPath $startPath
Get-Item -LiteralPath $ownedObservationFiles | Select-Object Name, Length, LastWriteTimeUtc
if (Test-Path -LiteralPath $stopPath) { Get-Content -LiteralPath $stopPath }

# COLLECT: one separately approved read-only DB connection; repeat only within approved bounds.
if ([DateTime]::UtcNow -gt $observationStartUtc.AddDays(14) -or
    $ownedObservationFiles.Count -ge 4035) { throw 'Initial collection bound reached; stop for review' }
if ($observationStopped -or (Test-Path -LiteralPath $stopPath)) { throw 'Collection stopped' }
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
$samplePath = Join-Path $observationDir ("sample-" + [guid]::NewGuid().ToString('N') + '.txt')
$ownedObservationFiles += $samplePath # retain even failed output; never overwrite prior samples
sqlcmd @approvedSqlcmdOptions -b -V 16 -l 30 -t 30 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/sample-legacy-login-usage.sql -v ExpectedDatabase=Shale -o $samplePath
if ($LASTEXITCODE -ne 0) { throw 'Collection gap: retain failed output privately; no zero-use inference' }
Get-Content -LiteralPath $samplePath
# Operator validates exactly three named rows, fixed Coverage value, UTC values and counts.
# Record missing/late rounds and resets in private change control. Never rewrite original samples.

# STOP: after last collection/review, local marker only; no server telemetry exists to stop.
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
if (Test-Path -LiteralPath $stopPath) { throw 'Already stopped; preserve original boundary' }
Set-Content -LiteralPath $stopPath -Value ([DateTime]::UtcNow.ToString('o'))
$ownedObservationFiles += $stopPath
$observationStopped = $true
```

SQLCMD closes each connection, removing its table variables. Nothing to stop/drop in Azure; **do not
clear the plan cache**, remove Query Store data, drop any event session or alter audit/Storage settings.
Preserve snapshots privately through security-owner review. Local deletion requires a separate recorded
retention decision and evidence retained in its approved destination; never delete the original captures.
The cleanup below validates the complete directory before deleting an exact in-memory file list; it
refuses unexpected files/subdirectories/reparse points, changed token, missing stop marker or lost state.
If the terminal/file list is lost, stop for manual ownership review, never reconstruct ownership by glob.

```powershell
# CLEANUP: only after review/retention approval; set this from that actual decision, never by default.
if ($observationCleanupApproval -cne 'APPROVED_EVIDENCE_RETAINED') { throw 'Retention decision required' }
if (-not $observationStopped -or -not (Test-Path -LiteralPath $stopPath)) { throw 'Stop first' }
$dir = Get-Item -LiteralPath $observationDir
if ($dir.Name -cne "shale-legacy-observation-$observationToken" -or
    ($dir.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe directory identity' }
if ((Get-Content -LiteralPath $markerPath -Raw).Trim() -cne $observationToken) { throw 'Owner mismatch' }
$children = @(Get-ChildItem -LiteralPath $observationDir -Force)
if ($children.Count -ne $ownedObservationFiles.Count) { throw 'Unexpected directory contents' }
foreach ($child in $children) {
    if ($child.PSIsContainer -or ($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        $ownedObservationFiles -cnotcontains $child.FullName) { throw 'Not task-owned; stop' }
}
foreach ($file in $ownedObservationFiles) { Remove-Item -LiteralPath $file }
Remove-Item -LiteralPath $observationDir # empty directory only; no recursive/wildcard deletion
```

Keep this private directory exclusive to the operator during validation/deletion (including trusted
ancestors); the guard is not protection against a hostile concurrent filesystem writer. Partial deletion
failure stops and requires review. This does not authorize deletion of any server telemetry or shared file.

Audit compatibility: the fallback reads bounded performance/security metadata, not application rows or
sensitive business views, and mutates no domain/security catalog. No application audit event/schema is
added. Existing PHI/entity-action/session-security audit transactions stay unchanged; collection approvals,
health/gaps and retention belong in private operator change control without account data or SQL payloads.

### Verification of this observation revision

Pre-edit test-impact review searched all three procedure names, permission-cutover scripts, the complete
`AuthPermissionCutoverFixture`, existing auth/module/lifecycle and launcher tests, and selector ownership.
No application symbol, DTO, auth behavior or existing test expectation changes. Changed-file review maps
the new read-only SQL to five privacy/scope/NULL/approval/cleanup contract checks; the owning runbook adds
operator instructions only. Existing SQL/Java fixtures and every preflight/retirement/Stage A/B/RLS file
are unchanged. The selector chooses security-data's auth, session and neighboring audit/transaction smoke
contracts for security tooling; no full-suite escalation. Audit integration requires no schema change.

| Check executed in this task | Result and boundary |
| --- | --- |
| New observation Python contracts | 5 PASS: exact object/database filtering, minimum source fields/no mutation or text/handles, uncached NULL/fixed incomplete coverage, event-payload approval boundary, exact nonrecursive cleanup guards. Static checks, not Windows/Azure runtime verification. |
| Entire auth launcher Python suite | 22 PASS, one native-Windows skip (23 tests run). |
| Focused existing auth/module/lifecycle/support Maven tests | 15 PASS; no failures/errors/skips. |
| Committed-base selector affected suite / critical reactor | 54 PASS / 116 PASS; no failures/errors/skips. |
| Disposable SQL Server 2022 sampler through real SQLCMD | PASS: missing modules, wrong database and ambient transaction refuse; uncached counters/times remain NULL; exact three rows exclude same-schema near-name and other-schema decoys; direct/nested/dynamic synthetic calls produce retained counts; full returned output excludes synthetic argument sentinel; ALTER of one synthetic module evicts its cached row and returns unknown, not zero-use. |
| Local cleanup | Exact generated container ID plus unique task label verified before removal; no ports published, no existing resource touched. Windows cleanup received static guard review only. |
| Documentation links/fences, source scope, whitespace | PASS; only this runbook, the new sampler and its Python contracts change. No private captures/binaries committed. |

Local SQL used the repository-recorded image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, fresh random
container/database and synthetic no-op procedures/argument sentinels. These reproduce neither deployed
Azure usage nor installed desktop acceptance. Local validation used a UTC SQL Server engine; the shipped sampler has no event-session scope at all. No Azure `ON DATABASE` session,
Azure service-tier permission, Azure event payload or failover behavior was tested.

Exact repository-root commands (`work/` contains uncommitted scratch orchestration and public documentation
copies, never operator captures; `/workspace/.tools/bin/mvn` is the existing configured wrapper):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_observation.py' -v
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base 572b96756c1e2a6573be5b1887ccbb70cdcfe8a1 --head HEAD --format markdown --plan-output work/legacy-observation/selection.json --run
# --run executes its selected Maven command followed by mvn test.
python3 work/legacy-observation/local_validate.py
git diff --check 572b96756c1e2a6573be5b1887ccbb70cdcfe8a1 HEAD
```

The initial offline focused Maven run lacked ICU/libphonenumber dependencies; the configured-proxy retry
passed without build changes. Sandboxed Git/proxy and Docker-socket access initially failed; permitted
shell execution resolved those access constraints. They were not counted as test passes. Official
Microsoft pages linked above were fetched and read on October 15, 2026; they establish the documented
capabilities/limitations, not this deployment's runtime configuration.

**Unverified boundaries:** PowerShell/SQLCMD commands were not executed on native Windows in this task;
Windows cleanup is statically reviewed, not runtime certified. Azure-specific event availability/payloads,
DMV service-tier authority, actual target mapping, failover/restart coverage and installed 1.0.136 acceptance
remain NOT RUN. This bounded alternative cannot certify Azure nonuse; retirement/installation stay BLOCKED.


## Captured legacy login behavior and separate retirement candidate — 2026-10-15

**Current decision: retirement candidate ready for review; installation remains BLOCKED.**
The complete private `legacy-login.txt` capture collected **2026-10-15 20:30:25.8430398 UTC**
was reviewed, including definitions, SET flags, parameter metadata, ownership, dependencies,
principal/role context and exact ACL states/grantors. It supplements the original prior-state
capture; neither raw capture nor unrelated principal/permission inventory is committed.
Live `codex/latest` was fetched at `9a812953028b8f58876172ecc39d3d9b502cdd85`; GitHub metadata
and Git ancestry verify merged #1873. Separate branch: `codex/legacy-login-retirement-20261015`.
Earlier unknown-definition statements below describe the evidence available to those earlier revisions.
This section supersedes them for these three captured procedures only.

| Captured fact | Consequence |
| --- | --- |
| All three accept only `@email nvarchar(320)`, compare `LOWER(email)` without tenant qualification, and accept `is_deleted IS NULL` | Duplicate emails can affect multiple tenants; removed status is not checked. |
| get_hash checks for any future lock first, then unordered `TOP (1) password_hash` | One matching account can suppress all results; ambiguity is hidden. No password verification occurs. |
| mark_failure updates every eligible match, increments the existing value, and uses AuthConfig id 1 with NULL/missing defaults 5/15 | Expired locks do not reset the counter; threshold failures assign a new lock deadline. NULL counters stay NULL. No lockout policy change is proposed. |
| mark_success updates every eligible match to zero failures, NULL lock and current last_login | A caller can clear locks and stamp successful-login accounting without presenting credentials or authenticated identity. |
| CALLER context, NULL explicit object owner, effective dbo ownership, no captured signatures/triggers/inbound dependencies; outbound Users and AuthConfig only | Same-owner ownership chaining skips table permission checks. CALLER and direct Users DML DENYs do not confine these capabilities. Static metadata is not an external/dynamic caller inventory. |
| Auth has exactly three object EXECUTE G/GRANT rows, grantor dbo | A narrow exact-state REVOKE is possible only after independently approved caller retirement. W/D/R or other grantors are rejected rather than rewritten. |
| Runtime has database EXECUTE plus role dbo-schema EXECUTE | REVOKE on auth does not retire runtime access; table read fences do not block the hash procedure or accounting writes. |

The test resources contain **only the three reviewed procedure bodies**, normalized to LF in Git and
restored to captured CRLF before SQL creation. Their SQL Server SHA-256 hashes must equal:

```text
get_hash     0x1B0D6F1117829EA94326E04EF955243EC7BF8110692C550876F1ADBA1C9D7453
mark_failure 0x08AF95D876F0DBA465DE97AB5A7371544604B64B916B464722DA5D040F374112
mark_success 0xA76F60DAEA06E046D46A7CAB99770C819449474AA8A214DD27C13F5EB24C0C6E
```

### Exact permission delta and approval boundaries

`retire-legacy-login.sql` is a **separate pre-Stage-A operator candidate**, never included by an
installer or preflight. Its one transaction removes the three captured auth object EXECUTE grants
**AS dbo** and adds exactly six object EXECUTE DENYs **AS dbo**, one for each procedure on each of
`shale_runtime` and `shale_runtime_role`. This explicit runtime scope is required to defeat the reported
inherited EXECUTE and future role members; broad database/schema grants remain unchanged. No module,
user data, AuthConfig value, session context, remembered credential, RLS predicate, role or membership
is edited. This does not establish least privilege: runtime's direct Users access, broader schema
CRUD/EXECUTE and audit permissions remain independent findings.

Before mutation, an operator must retain and reference all of the following privately:

1. Security-owner approval specifically for retiring **all three auth and runtime capabilities** and
   the reopened-risk rollback, distinct from Stage A/B approval. Establish actual installed desktop,
   server, job and connection identities, original-login/server-token authority and exact artifact hashes.
2. A complete caller inventory covering installed 1.0.136/older clients, server versions, jobs,
   integrations, stored wrappers, dynamic SQL, synonyms and external consumers. Repository source absence
   and empty dependency metadata do not establish absence of use. Any required caller must first be
   migrated through separately reviewed compatible work; this candidate does not create a substitute.
3. A bounded observation/drain record for the inventoried consumers with a documented start/end,
   longest scheduled-job interval and connection/session drain, owners and zero remaining invocations.
   Do not invoke these procedures against Azure to create evidence. Use already-approved private
   operational/configuration/telemetry records; a missing observation source remains a blocker requiring
   a separately approved telemetry collection plan, not guessed zero usage.
4. Compatibility evidence tying unchanged password/bcrypt and remembered flows to the installed
   artifacts and actual configured principals, including 1.0.136's direct Users read and old remembered
   resolver. This can justify retiring unused grants while retaining old-client reads; it cannot justify
   disabling a deployed/external consumer. If accounting/lockout callers are active, stop for their own
   migration/disposition rather than silently bypassing or redesigning their policy.
5. Exclusive serialized operator control of security/module metadata during baseline collection,
   apply and rollback. No concurrent DDL/permission editing; the scripts do not impose a global DDL lock.

Acknowledgements and nonempty evidence references record these decisions; they do not prove them.
**Those records have not been supplied. No live retirement or installation is approved by this PR.**

`legacy-retirement-baseline.sql` is a bounded read-only catalog/hash action on the existing approved dbo
metadata connection. It pins the captured bodies/SET flags/parameters/owners, rejects signatures,
Users/AuthConfig triggers, inbound static callers and matching synonyms, and verifies the four outbound
dependencies. It outputs a single database-specific catalog digest. Retain it independently only after
reviewing the associated current private catalog evidence; never calculate an expected hash inside the
mutation invocation or replace an original failed baseline with current state merely to pass a guard.
It does not replace the original prior-state capture. Its digest covers exact permission/grantor/state,
principal SID/owner, membership, schema/object ownership, module hashes/SET/execution flags, parameters/column/type metadata,
signatures, dependencies, synonyms, triggers and security policies/predicates. Application rows and
server/login authority are intentionally outside this metadata digest. Changed/unsupported evidence stops.

Apply refuses drift before editing and compares the complete expected ACL delta plus unchanged other
metadata before commit. Auth authority diagnostics remain unchanged and must pass after revocation;
runtime effective checks must show no escalation and no legacy EXECUTE. A same-owner wrapper can bypass
nested procedure DENY, so known inbound callers are rejected and external/dynamic paths remain an inventory
gate. Later/new wrappers or authority changes require renewed review; the DENYs are not a sandbox for
arbitrary dbo modules. Run full unchanged Stage A preflight separately after retirement, with its original
identity/security-owner gates. A successful retirement does not authorize installation.

### Windows operator commands and the remaining bounded action

**Next action now:** have the security owner complete the five-item evidence review above and record a
per-procedure, per-auth/runtime-consumer retire-or-migrate decision. If the records cannot establish no
remaining dependent callers, stop here; provide the missing consumer/telemetry plan for separate approval.
No Azure call, permission change or installation is part of this Codex task.

Once separately authorized, from the reviewed repository root use the existing approved connection
array and private output paths. SQLCMD variables accept trusted fixed literals, safe change-control IDs
and emitted hex digests only; never interpolate quotes, SQL, credentials, URLs or private contents.
Do not put invented evidence references in these variables. Use one SQLCMD connection per script and
stop on every error; SQLCMD exits/closes the connection so an uncommitted transaction rolls back.

```powershell
# Read-only; retain the output privately. No approval of mutation is implied.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql -v ExpectedDatabase=Shale -o $privateLegacyBaselineOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: captured legacy baseline is incompatible or incomplete' }
# Set $legacyBeforeHash from the independently reviewed output, never an inline current-state query.
# Fill these safe IDs only from approved private change-control records.
$legacyApproval = @('ExpectedDatabase=Shale', 'ReviewedIdentityMapping=CONFIRMED', 'LegacyRetirementApproval=APPROVED', 'CallerInventoryApproved=APPROVED', 'LegacyCallersDrained=CONFIRMED', 'RuntimeLegacyDisposition=DENY_THREE_MODULES', 'ExclusiveChangeWindow=CONFIRMED', "CallerInventoryEvidence=$inventoryRecordId", "CallerDrainEvidence=$drainRecordId", "CompatibilityEvidence=$compatibilityRecordId")
$legacyApply = $legacyApproval + @("LegacyBeforeHash=$legacyBeforeHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/retire-legacy-login.sql -v @legacyApply -o $privateLegacyRetirementOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: retirement failed; verify unchanged catalogs before any next action' }
# Independently save AfterHash as $legacyAfterHash from that successful output.
# Recheck the actual configured auth/runtime login tokens and effective permissions, without invoking legacy modules.
# Existing Stage A approval remains separate; @review is the previously evidenced original acknowledgement set.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightRetestOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation remains blocked' }
```

Afterward, actual auth must have zero effective EXECUTE on each legacy procedure and retain direct Users
SELECT/old resolver EXECUTE. Actual runtime must have zero EXECUTE on all three and retain approved
noncredential projections/password-management writes. Review original-login/server-token and dynamic
wrapper authority separately; database-user impersonation tests do not establish those facts.
No legacy procedure execution against Azure is requested, including as a postcheck.

### Stage B disposition and exact rollback

The three retired auth grants **stay absent** and all six runtime DENYs **stay present through Stage A,
Stage B and rollback of either stage**. The existing scripts already preserve unrelated permissions; no
Stage B allowlist or guard change is needed. Stage B's full compatible fleet/job drain, caller inventory,
module identities and RLS prerequisites remain independently mandatory. The legacy retirement's smaller
caller drain is not a substitute for the Stage B old-client read drain. Neither a Users SELECT DENY nor
runtime hash-column DENY would prevent these procedures' internal access; this is why the explicit
EXECUTE retirement is required before considering Stage B complete for this profile.

`rollback-legacy-login.sql` supports only the captured original **G/GRANT AS dbo** profile. It requires
separate rollback approval, compatibility/reopened-capability review, the original independently saved
BeforeHash and AfterHash, and the same inventory/drain/exclusive-window evidence. It rejects extra target
ACLs, changed grantors/states/SIDs/metadata and missing hashes. It removes exactly six new DENYs AS dbo,
replays exactly the three original grants AS dbo without GRANT OPTION, then requires the **entire original
catalog digest** before commit. No CASCADE, grantor substitution, broad grant replay or module edits.
Unsupported original W/D/R/other-grantor states fail closed rather than using this rollback.

Rollback order: while Stage A/B exist the legacy rollback intentionally refuses; drain/roll back their
consumers and use their own guarded rollbacks first. If any later unrelated catalog change remains,
AfterHash will still differ: stop for separately reviewed reconciliation, never overwrite saved hashes.
Review the reopened hash disclosure, unauthenticated accounting and inherited runtime risks before
restoring access. Failed rollback closes the connection and rolls back its complete transaction.

```powershell
$legacyRollback = $legacyApproval + @('LegacyRollbackApproval=APPROVED', 'RollbackCompatibilityReviewed=CONFIRMED', "LegacyBeforeHash=$legacyBeforeHash", "LegacyAfterHash=$legacyAfterHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-legacy-login.sql -v @legacyRollback -o $privateLegacyRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: exact legacy rollback refused; do not replay grants manually' }
```

Audit compatibility: this is a security-catalog change, not an application business-data mutation or
sensitive application read. Existing PHI/entity-action/session-security transactions and schemas remain
unchanged; use private operator change control for approval, source hashes, evidence and catalog digests.
Do not place raw metadata, bodies, SQL, credentials or account data in application audit rows.

### Verification of this candidate

Pre-edit test-impact inventory: existing auth lookup/module/lifecycle, remembered store/migration,
User creation/removal/projection and `AuthVerificationSupportTest`, the full permission fixture, local SQL
harness, launcher and security-data selector ownership were inspected. No runtime auth symbols or DTOs
change. The expanded fixture runs captured behavior first, then the actual retirement/rollback scripts,
then the existing unchanged Stage A/B and password/remembered/isolation scenarios. Retired modules remain
present through that roundtrip, preventing accidental test-only removal of the capability blocker.

All accounts, AuthConfig values, table shapes and calls in the fixture are **synthetic**. Only the three
hash-verified module bodies reproduce the capture. Coverage includes duplicate emails across tenants,
NULL/deleted/removed eligibility, threshold/expiry/default/NULL accounting, unauthenticated success,
ownership-chain read/update despite direct DML DENY, runtime inherited EXECUTE after object REVOKE,
future role-member denial, caller/dependency/approval/hash/ACL drift refusal, rollback of all nine ACL
changes on postcheck failure, and exact original G/W/D/R/other-grantor preservation outside the target delta.
Native Windows execution and installed 1.0.136 acceptance of this revision remain NOT RUN.



Linux results for this revision (Maven 3.9.11 / JDK 21; SQL Server 2022 pinned to the digest
already recorded below, task-owned container bound only to 127.0.0.1:14333):

| Check | Result |
| --- | --- |
| Focused auth/module/lifecycle/store/User tests | 38 PASS; no failures/errors/skips. |
| Committed-base repository selector / affected security-data suite | 54 PASS; no full-suite escalation. Auth, tenant/session and neighboring audit/transaction contracts are relevant to permission tooling. |
| Critical reactor `mvn test` | 116 PASS; no failures/errors/skips. |
| Final disposable SQL fixture / public quiet launcher | All three PASS lines; exit 0. Captured-body behavior, retirement/rollback and existing Stage A/B roundtrip execute before the current password/remembered/isolation scenarios. |
| Real SQLCMD `-b -V 16` | PASS: missing drain 58343/exit 16; post-mutation authority failure 58347/exit 16 with complete rollback; drift 58344/exit 16; wrong final rollback identity 58346/exit 16 with no reopened grants; exact original digest restored. |
| Python auth / selector / unchanged Case launcher | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL execution. |
| Captured-body UTF-16 hashes, SQL includes/aliases, runbook links/fences, scope and Git checks | PASS. Original preflight, Stage A/B, RLS, runtime authentication and remembered-credential code unchanged. |

Commands executed from repository root (`mvn` uses the existing configured JDK/proxy/CA wrapper;
`work/` helpers provide private synthetic credentials and exact nonce-owned local resources only):

```bash
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 work/legacy-login/local_sql.py test
python3 work/legacy-login/sqlcmd_roundtrip.py
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base 9a812953028b8f58876172ecc39d3d9b502cdd85 --head HEAD --format markdown --plan-output work/legacy-login/selection.json --run
# The selector runs its printed affected Maven command, followed by mvn test.
python3 work/legacy-login/local_sql.py launcher
git diff --check 9a812953028b8f58876172ecc39d3d9b502cdd85 HEAD
```

The first new SQL attempts exposed empty-catalog XML NULL serialization and synthetic grant-option
cleanup requirements. Empty catalog serializations now have explicit canonical empty roots, and an
independently enforced non-NULL digest check; effective-permission UNKNOWN handling is unchanged.
A definition-drift injection was made explicit rather than relying on CREATE/ALTER spelling. Private
SQLCMD setup also needed captured SET flags and exact UTF-16 definition transport. Final runs above
passed after those corrections; failed attempts are not acceptance. Private diagnostic helpers were
removed from test sources. The final documented Windows launcher procedure remains available below;
**native Windows execution, installed 1.0.136 acceptance and actual-login checks remain NOT RUN**.
No Azure connection, live permission change, installation, release, RLS change, merge or deployment
occurred. Only task-owned disposable SQL resources were removed. Caller/drain/compatibility evidence,
independent owner approval and the existing Stage A/B/RLS/security gates remain open.

**Historical #1873 blocker:** the operator identified three `sp_login_*` EXECUTE grants after #1872.
The [current assessment and one bounded dbo query](#identified-1872-blocker-three-legacy-login-executes--2026-10-15)
keep installation blocked pending deployed definitions/authority and accounting/caller evidence.

**Review candidate; deployment BLOCKED.** Base `7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0`
was fetched from live `codex/latest`; merged #1868, #1869 and #1870 were verified with GitHub metadata
and Git ancestry. Separate branch: `codex/auth-module-permission-cutover`.
The [Case security runbook](case-read-local-security-verification.md) retains the original schema evidence,
Case verification procedure and historical execution records. This document owns the permission candidate.
No Azure connection, live grant/migration, client release, configuration change, merge or deployment is
part of this task. No three-table FILTER or other predicate is added/removed. Original schema export,
Case harness and accepted R4 reports remain unchanged; no substitute export is generated.

## Attributed operator evidence

Reported by the user in the October 15 task, not executed by Codex against Windows/Azure:

```text
PASS credential_identity
PASS remembered_rotation
PASS runtime_isolation
Exit code 0
```

This closes the requested **native Windows synthetic auth SQL retest** after #1870, including its
reported successful exit. Tool versions, JVM timezone and exact Windows source/artifact hashes were not
supplied; none is invented. It does not establish this new permission candidate's Windows execution,
installed application compatibility, complete production-schema parity or security acceptance.

User-run Azure Shale metadata/effective-permission evidence:

| Observation | Consequence |
| --- | --- |
| Database collation `SQL_Latin1_General_CP1_CI_AS` | Case-insensitive identifiers resolve AuditLog/auditLog; exact email comparisons retain this collation. No collation/index/data rewrite. |
| `shale_app`: no db_owner/ShaleAdmins; Users SELECT, old resolver EXECUTE, database CONNECT | Current capability supports older password/remembered consumers. Catalog names do not establish actual server/desktop configuration mapping. |
| `shale_runtime`: no db_owner/ShaleAdmins; member `shale_runtime_role` | Both principal and role need an explicit module fence. |
| Runtime and role: dbo schema CRUD; runtime database EXECUTE, role dbo schema EXECUTE | Installing dbo owner-executed procedures alone exposes them to runtime. An object REVOKE does not remove inherited EXECUTE. |
| Runtime effective Users.password_hash SELECT/UPDATE and auditLog SELECT/INSERT/UPDATE/DELETE | Hash disclosure and mutable audit history are real permission findings. Read FILTERs alone would not repair either. |
| V1/V2 absent; old resolver caller-executed, dbo-owned | New-name installation can preserve all old object definitions/ACLs and their grantors/states. |
| Users/auditLog/Statuses have no RLS predicates | Confirmed gap persists; this task must not attach the three FILTERs. |

**Actual deployed server and desktop auth/runtime credential configuration is still UNCONFIRMED.**
No independently approved security-owner acceptance of dbo module authority/internal hash access is implied.

## 58307 investigation after #1871 — 2026-10-15

**Operator-reported evidence, not a Codex Azure/Windows execution:** the expanded native Windows
fixture produced all three PASS lines and exit 0; prior-state capture succeeded with exit 0 and is
retained privately. Azure preflight stopped at 58307. On the actual `shale_app` connection, database
CONTROL, ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, dbo schema ALTER/EXECUTE, dbo user IMPERSONATE and
database EXECUTE all returned 0. Auth has Users SELECT, old resolver EXECUTE, CONNECT and an explicit
database VIEW DEFINITION DENY, without db_owner/ShaleAdmins. **Stage A has not been installed; employees
still use desktop 1.0.136.** Preserve that captured state and the original schema; neither is regenerated
or committed here. The expanded Windows report supersedes the pending #1871 fixture retest for that
reported revision only; this diagnostic revision has not been run natively on Windows by Codex. These
observations do not enumerate effective column permissions or every token path.

**Disposable result:** the reported auth grants **including VIEW DEFINITION DENY** pass preflight and
the existing install/postcheck/restrict/rollback fixture on SQL Server 2022. DENY alone does not reproduce
58307. No Azure root cause or extra live permission is established; no live permission change is proposed.
The new output names the decision inputs so the operator can distinguish an actual extra capability from
UNKNOWN evidence. This is a diagnostic/tooling change, not security acceptance or cutover authorization.

Every auth condition formerly combined in the 58307 OR list now contributes to one shared diagnostic
relation and shared fail-closed decision. It checks:

| ConditionName | Authority tested / reported securable |
| --- | --- |
| role / fixed-role | db_owner, ShaleAdmins, every fixed database role in the effective token except public |
| database / database-permission | ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, CONTROL, EXECUTE; every fn_my_permissions database capability outside CONNECT and the two existing column-encryption metadata exceptions |
| dbo-schema / dbo-user / impersonate-user | dbo ALTER/EXECUTE, dbo IMPERSONATE and other impersonatable database users; roles are not USER securables |
| ownership | schema and explicit object owners matched against the complete effective token |
| grant-option | every W-state permission matched to the token, including class/major/minor IDs, entity/column where applicable, grantee and original grantor |
| column | every dbo-inventoried column UPDATE, and every non-Users column SELECT |
| object | ALTER/CONTROL/INSERT/UPDATE/DELETE, non-Users SELECT and EXECUTE outside the three existing resolver/V1/V2 exceptions; includes callable elevated/signed/encrypted modules without printing bodies |

Token output includes public and nested roles. Ownership, grant-option, principal, object and column
inventories are captured under dbo before impersonation; filtered caller catalogs are not proof of absence.
Database VIEW DEFINITION's raw effective value and matching token ACLs are context, not a requirement to
grant metadata visibility. Effective 1 is GRANTED, 0 is NOT_GRANTED, SQL NULL is UNKNOWN. Any GRANTED guard
input or UNKNOWN stops with 58307. No unknown is coerced to a safe zero or mislabeled a grant. The database
user test still does not replace actual-login/server-token review. Serialize metadata changes during probes.

Output is metadata only: no application query, hash, SID, secret or module body. It contains at most 200
finding details and 200 token rows, grouped finding counts, total counts and explicit truncation flags.
Truncation leaves the investigation incomplete; retain private output and narrow the affected condition in
a separately reviewed read-only follow-up before drawing conclusions. No broad permissions should be
removed to make the count disappear. Dbo-known absence of the optional ShaleAdmins role is distinguished
from a NULL membership result for an existing role.

### Next bounded operator query and exact retest commands

From repository root, on the **existing approved dbo metadata connection**, run this read-only diagnostic
before proposing permission changes. It does not require new security-approval acknowledgements and does
not include capture, installation or restriction scripts:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/diagnose-auth.sql -v ExpectedDatabase=Shale -o $privateAuthDiagnosticOutput
$authDiagnosticExit = $LASTEXITCODE
# A nonzero exit with 58307 is expected when GRANTED/UNKNOWN findings exist. Stop and review them.
if ($authDiagnosticExit -ne 0) { throw 'Stop: review bounded auth diagnostics; do not change permissions or install' }
```

`diagnose-auth.sql` is the bounded read-only query artifact: only SET, temporary catalog snapshots,
EXECUTE AS/REVERT and metadata/effective-permission queries. It works before Stage A. It reports the same
auth inputs as preflight and restores dbo before reporting/throwing. Preserve the prior-state capture as-is;
do not rerun capture merely to replace the original failure evidence. Share only approved condition,
securable, permission, outcome and truncation metadata, never retained private captures/module definitions.
If all inputs are zero, compare the exact script revision, selected database and connection identity with
the failed run; do not assume that permission state or original-login authority is unchanged.

After reviewing the diagnostic, retest only the read-only preflight with the **previously evidenced**
review acknowledgements (do not assert unapproved decisions):

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightRetestOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight still blocked' }
```

No Stage A/B, release or permission-change command is authorized by this investigation. The native Windows
synthetic retest uses the launcher commands in the Windows section below; use a dedicated local disposable SQL
instance, never Azure. Installer, restriction and rollback paths include the same preflight. Existing
module postchecks already reject NULL EXECUTE with fail-closed COALESCE; read postchecks reject NULL forbidden
column access and NULL required writes. The expanded fixture exercises these paths under the metadata DENY.

## Caller review and bounded decision

### Identified #1872 blocker: three legacy login EXECUTEs — 2026-10-15

**Operator-reported Azure result:** `shale_app` has database VIEW DEFINITION DENY; the effective token
contains `public` and `shale_app`. The diagnostic reports 3299 NOT_GRANTED, 3 GRANTED and zero UNKNOWN.
The three findings are object EXECUTE on `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure` and
`dbo.sp_login_mark_success`; SQLCMD exits 16 / error 58307. This identifies the guard inputs, not their
definitions, grantors, execution authority or safety. It supersedes the earlier unresolved-condition
statement above. **Stage A remains NOT installed, desktop 1.0.136 remains in use, installation BLOCKED.**
Codex has made no Azure connection or live change.

Fetched live `codex/latest` at `cfc62956b209265d7c56829b5e8e1af3773c99c1` (merged #1872), with complete
available Git history; separate task branch `codex/1872-legacy-auth-evidence`. The initial sandboxed
Git fetch could not reach its proxy. A GitHub API fetch verified the complete base tree and signed commit;
the subsequent approved shell fetch/unshallow confirmed that same live base and supplied history.

| Evidence reviewed | Finding and limit |
| --- | --- |
| Current tracked repository and available history, exact searches for all three module names | No definitions or callers found before this investigation. The schema reference names `failed_attempts`, `lock_until` and `last_login`, but supplies no accounting algorithm or module authority. No original schema export is available in this task workspace; none was regenerated or inferred. |
| Release commit `c2c77830` (`Release Shale 1.0.136`), `shale-data/.../auth/AuthServiceImpl.java` | Direct parameterized `SELECT TOP 1` from Users by email with `is_deleted=0`; `password_hash AS pw_hash`; bcrypt verification in Java. No lockout check, failure increment, success reset or last-login update in this path. Its retry counter is connection retry only. |
| Released `DesktopUiAuthService.login`, shared auth adapter and server auth controller | Desktop delegates to the shared auth service, then stages the separate server credential exchange. No `sp_login_*` call is present. This is source evidence; installed binary hashes, actual configured identities and external clients/jobs remain unverified. |
| Current `AuthServiceImpl` and `LookupAuthenticationCredentialV1` | New bounded credential lookup plus Java bcrypt; no new lockout/accounting algorithm. Neither current nor released source proves that deployed modules/triggers/external consumers are unused. |
| Ownership/execution/permission authority of the three Azure procedures | **Unknown.** dbo schema/name does not establish explicit object owner, CALLER versus OWNER/named execution, signatures, chaining, parameter semantics or grantor/state. Even CALLER modules can reach same-owner data through ownership chaining. |

**Decision:** these are unreviewed executable capabilities. Evidence does not yet support classifying
them as necessary narrowly scoped legacy capabilities, obsolete grants, or unsafe implementations.
Do not whitelist by name, revoke them, edit their bodies, or relax 58307. No production permission or
authentication correction is justified yet. Preserve deployed lockout/failure/success behavior by making
no change to it. Repository absence is not permission-removal evidence.

#### One bounded read-only deployed-evidence query

On the existing approved **dbo metadata connection**, from repository root, the operator can run:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/inspect-legacy-login.sql -v ExpectedDatabase=Shale -o $privateLegacyLoginEvidence
if ($LASTEXITCODE -ne 0) { throw 'Stop: legacy module evidence incomplete; installation remains blocked' }
```

This single script performs only catalog reads, session SET and temporary metadata tables; it does not
impersonate, invoke modules, query application rows, alter permissions, capture a replacement prior state,
or install anything. Full Unicode definitions and SHA-256 hashes are private review evidence. Parameter
types/directions/lengths are returned, never parameter default values. Module owners (explicit, schema,
effective), CALLER/OWNER/named execution, SET flags, signer identities/thumbprints without keys/signature
bytes, direct inbound/outbound dependency metadata, related owners/triggers, complete database-principal
and nested-role context, and exact applicable permission class/major/minor IDs, G/W/D/R states,
grantors and grantees are included. ACLs cover database, related schemas/objects/columns, USER/type
authority and all explicit grants to auth/runtime, module owners/execution identities/signers and their
nested roles, including authority beyond direct dependencies. Fixed-role and dbo authority is not
limited to explicit ACL rows. Server/login authority still needs the existing actual-connection review.

Bounds are explicit: three bodies of at most 1 MiB each, 200 dependency records/related objects/signatures/
triggers, 6303 parameter rows, 1000 principals/membership rows each and 5000 relevant permission rows.
Over-limit evidence stops with 58330 rather than truncating. Missing/encrypted/non-T-SQL modules report
their status then stop with 58331. SQLCMD must retain complete `-y 0 -Y 0 -u` output. Serialize catalog
changes during collection. An exit 0 means collection succeeded, **not preflight approval**. Definitions
may contain sensitive embedded literals; review/redact privately before sharing. No application rows,
credential values from application storage, SIDs, key material or parameter defaults are returned.

#### Evidence/action required before any correction

1. Review those deployed bodies/parameters/SET flags, explicit/effective owners, execution identities,
   signatures and exact ACL/grantors. Trace every dependency, synonym, dynamic SQL, trigger and external
   caller; catalog dependencies are not a complete dynamic/external call graph. Obtain separately bounded
   private evidence for unresolved/transitive bodies or trigger behavior rather than assuming absence.
2. Establish installed 1.0.136 artifact/configuration mapping and jobs/older consumers. Verify how credentials
   are selected and how failed attempts, threshold, duration, expiry, deleted/removed users, successful login,
   last-login updates and concurrent attempts are accounted for. No live authentication execution is requested
   by this metadata step. Obtain approved behavior evidence; do not invent a lockout policy from names.
3. Classify each module. If necessary and safe, a subsequent correction must pin independently reviewed
   deployed definitions/SET semantics, parameters, owners, execution/signature authority, exact ACL states/
   grantors and permitted caller/role closure; reject drift, broad or grant-option authority and unexpected
   dependencies. Reproduce the **actual reviewed behavior** in disposable SQL, including lockout boundaries,
   concurrent failure/success accounting and released-client compatibility, before permitting a capability.
   If obsolete or unsafe, obtain a separately reviewed consumer migration/drain and exact-state permission/
   module correction with rollback. No blanket REVOKE or guessed GRANT replay is acceptable.
4. Keep the original private prior-state capture and original schema export unchanged. Supplement them with
   this timestamped evidence; never overwrite them. Any later authorized change requires its own exact
   delta, independent baseline hashes/signature and grantor/state constraints, drift refusal, failure atomicity
   and exact rollback. Recheck deployed metadata and actual auth/runtime connections before Stage A.

Stage B still requires complete caller inventory and compatible installed-fleet/job drain, including these
three modules if used. A Stage B table SELECT fence alone must not be claimed to block a same-owner procedure's
internal read or update. Any admitted legacy path requires an explicit Stage B disposition and regression tests.
The current candidate remains blocked with these grants; none of its staged scripts, prior-state capture,
schema export or RLS gates has been changed. The existing rollback commands do not authorize modifying these
three deployed procedures. **This revision's rollback is reverting its query/test/documentation commit;
there is no database delta to undo.**

#### Test impact and verification boundary

Pre-edit review covered `AuthCredentialLookupTest`, `AuthUserLifecycleSecurityTest`,
`PreTenantAuthenticationModuleContractTest`, `AuthPermissionCutoverFixture`,
`PreTenantAuthenticationLocalSqlServerTest`, `SqlRememberCredentialStoreTest`, remembered migration,
User creation/removal/projection tests, the auth launcher tests and the selector's security-data inventory.
Only the fixture, new metadata script and this runbook change. Shared login, bcrypt, account lifecycle,
password-management SQL, historical installers, capture and RLS definitions are unchanged.

The disposable fixture creates all three exact names with explicitly **synthetic** parameterized bodies,
grants each EXECUTE as dbo, and calls them under the metadata-denied auth principal. It requires diagnostic,
preflight and installer to reject all three at 58307 before module installation; harmless-looking names/bodies
are never adopted. It exercises the new dbo collector, full definitions/hash/parameters/owners/ACL grantors,
missing-module rejection, execution/definition drift, and certificate signer authority beyond dependencies.
It then removes only its synthetic probes and compares exact original permissions/memberships/predicates
before the existing staged roundtrip and credential/remembered/isolation checks. These probes reproduce the
grant blocker and collector behavior; **Azure module behavior/lockout reproduction remains NOT RUN until
the deployed definitions and behavioral evidence arrive.** Synthetic counts vary with fixture schema and
are not asserted equal to Azure's 3299 negative checks. Native Windows execution of this revision remains
NOT RUN. No Case SQL or Azure execution is part of this revision.

Audit compatibility: private security metadata collection adds no business mutation, application sensitive
view or audit schema. Existing application/security audit transactions remain authoritative. Retain approved
metadata privately in operator change control; do not log bodies, credentials or SQL into application audits.

Linux verification for this revision uses the same SQL Server 2022 digest recorded below, a new task-owned
container bound only to `127.0.0.1:14333`, generated synthetic credentials, and random nonce/SID-guarded
databases/logins. No operator-retained resource or original export was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/User tests | 38 PASS. |
| Selector affected security-data/server suite | 54 PASS; no full-suite escalation. These preserve auth/lifecycle and neighboring transaction/audit/session contracts selected for SQL security tooling. |
| Critical `mvn test` | 116 PASS. |
| Expanded disposable SQL fixture and final public quiet launcher | All three scenario PASS lines, exit 0. Exact three GRANTED / zero UNKNOWN; all three entry points reject 58307. Missing/encrypted bodies reject 58331; oversized evidence rejects 58330 before output. Original ACLs/memberships/predicates and staged rollback checks pass. |
| Real SQLCMD `-b -V 16` | Three exact EXECUTEs report GRANTED and exit 16 / 58307; collector exits 0; a missing module exits 16 / 58331. Fresh independently nonce-guarded synthetic database removed. |
| Python auth / selector / unchanged Case launcher tests | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| Changed-file/test review, SQL metadata/include/scope/alias, runbook links/fences, Git whitespace | PASS; only the collector, fixture and owning runbook changed. |

Commands executed from repository root (`mvn` uses the existing `/workspace/.tools/bin/mvn` proxy/CA/JDK
wrapper; `work/` helpers are private local orchestration, not application configuration):

```bash
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 work/local_sql.py test
python3 work/sqlcmd_probe.py
python3 work/local_sql.py launcher
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base cfc62956b209265d7c56829b5e8e1af3773c99c1 --head HEAD --format markdown --plan-output work/committed-selection.json --run
git diff --check cfc62956b209265d7c56829b5e8e1af3773c99c1 HEAD
```

The public Windows launcher procedure below reproduces the committed Java fixture; the local helpers
only supply private synthetic credentials and test real SQLCMD. An initial offline Maven dependency miss
(`libphonenumber`) was resolved through the configured proxy; no build change was needed. Passing local
checks validates this evidence tooling and continued blocking, **not the deployed procedures or their
lockout behavior**. Installation remains BLOCKED; security acceptance OPEN.

Repository-wide credential-field/SQL/call-site search at this base found:

- `AuthServiceImpl.login` uses V1 through `DataSources.auth()` for shared desktop/server password checks;
  BCrypt remains in Java. No inline SELECT fallback. Server desktop exchange repeats this verification.
- `SqlRememberCredentialStore.lookup` uses V2 on auth, then principal-initialized runtime performs the
  serializable owner/session/JTI revalidation and rotation. Creation/deletion also use runtime.
- `UserDao` directory/detail/management projections list noncredential fields explicitly. User creation
  inserts `password_hash`/`password_alg`; administrative reset updates both and revokes sessions on the
  same connection. These writes must remain available. No blanket runtime Users SELECT/UPDATE denial.
- Minimized/shared Case projections, User directory/Team/detail, selectors and session/profile readers
  consume explicit User identity/display/eligibility fields. Their original projections are preserved.
- Older installed desktops can still execute the former direct auth SELECT. External jobs, custom
  modules, installed source versions and actual configuration cannot be inventoried from this checkout.

Stage A adds object EXECUTE to `shale_app` and object DENY EXECUTE to **both** `shale_runtime` and
`shale_runtime_role` for V1/V2. One outer transaction encloses the unchanged #1868 installer, its nested
transaction, permissions and effective postchecks. Newly privileged modules are never committed with
an exposure window. Do not run the historical module installer alone in this permission profile.
No existing broad grant is revoked or rewritten. Explicit module ACL shape and exact expected permission/member deltas are checked before commit; original module definitions/owners are preserved too. No role gains dbo/ShaleAdmins/ALTER/CONTROL/impersonation.

Stage B is separately gated on compatible fleet/job drain and approved caller inventory. It creates
new deny-only roles, requiring their names to be absent:

| New role / members | Exact restriction |
| --- | --- |
| `ShaleAuthTableFenceV1` / shale_app | DENY Users SELECT, remembered-table SELECT and old resolver EXECUTE. New V1/V2 remain executable through their confined owner scope. |
| `ShaleRuntimeHashFenceV1` / shale_runtime and shale_runtime_role | DENY SELECT on Users.password_hash only. Other User projections, INSERT and password_hash/password_alg UPDATE remain. |

SQL Server column GRANT can override table DENY. Stage B snapshots all User/remembered column names as
dbo, checks every column as auth and aborts/rolls back if any remains readable. It never silently revokes
that column grant. Runtime's column DENY is tested against a direct column GRANT as well as inherited
schema CRUD. This is not a restriction on every legacy credential-related column or a repository-wide
least-privilege rewrite. Remaining credential fields and alternate elevated modules require inventory.
Stage A intentionally retains old auth SELECT/resolver compatibility; Stage B intentionally ends it.
Do not run Stage B merely because the synthetic auth test passed.

### Verification of this diagnostic revision

Fetched live base `bfca3e3346aa1b4abeb357788484d655b9087a7e` (merged #1871); isolated branch
`codex/diagnose-1871-auth-preflight`. Codex executed Linux/local disposable checks only, using the same
SQL Server 2022 image digest recorded below, a newly created task-labelled container bound to
127.0.0.1:14333, random nonce/SID-guarded fixture databases/logins and private synthetic credentials.
No retained operator resource, prior capture, original schema or Azure connection was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/migration/User tests | 38 PASS, zero failures/errors/skips. |
| Committed-base selector affected security-data/server suite | 54 PASS, one explicit opt-in SQL skip; modified fixture compiled. No full-suite escalation. |
| Critical `mvn test` | 116 PASS, zero failures/errors/skips. |
| Expanded disposable Java SQL and final quiet launcher | All three PASS lines, exit 0; reported CONNECT/Users SELECT/old resolver EXECUTE/VIEW DEFINITION DENY retained through staged roundtrip and exact ACL/member/predicate comparisons. |
| Negative diagnostic probes | Database/schema EXECUTE, fixed/ShaleAdmins membership, column UPDATE/non-Users SELECT, public/nested inheritance, W state, schema ownership, user impersonation and elevated module EXECUTE each reject 58307 and name a GRANTED condition. Unsupported permission probe returns real SQL NULL, reports UNKNOWN and fails the shared decision. |
| Real SQLCMD `-b -V 16` standalone diagnostic | Baseline exit 0 with metadata DENY shown; injected Users.email UPDATE reports GRANTED and exits 16 on 58307. Disposable injection removed. |
| Python auth / selector / unchanged Case launcher | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| SQL include, privacy/scope, alias, changed-file and Git checks | PASS. No capture/historical SQL/schema/RLS/Case-tooling changes; new diagnostics have no permission mutations, module definitions, hashes or application SELECTs. |

The first offline Maven attempts lacked cached dependencies (libphonenumber and later jna-platform);
configured-proxy retries passed without build-file changes. Early fixture development exposed a W-state
test cleanup requirement, a result-decoder collision with grouped condition counts and the fact that
missing securables under denied metadata can return 0 rather than NULL. The final UNKNOWN test uses an
unsupported permission to obtain a real NULL; no missing object is asserted to be denied by that test.
Dbo inventories, explicit prerequisites and serialized metadata changes remain essential. These failed
attempts are not counted as acceptance. Existing SQL postchecks passed under the DENY without guard removal.

Exact local commands (the private `work/` helpers supply only task-local credentials and expand the
committed SQL; reproduce the Java coverage using the documented public quiet launcher):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base bfca3e3346aa1b4abeb357788484d655b9087a7e --head HEAD --format markdown --plan-output work/auth-58307/selection.json
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn test
python3 work/auth-58307/run_fixture.py
python3 work/auth-58307/check_sqlcmd.py
python3 work/auth-58307/run_launcher.py
git diff --check bfca3e3346aa1b4abeb357788484d655b9087a7e HEAD
```

Audit compatibility: diagnostics read only security metadata and add no application/business mutation or
sensitive application view, so no application audit event or schema migration is introduced. Retain
approved diagnostic evidence in private operator change control. **The Azure 58307 cause remains
unresolved until the bounded diagnostic is run there by the operator. Deployment BLOCKED; security
acceptance OPEN; desktop 1.0.136 compatibility and the original prior state remain preserved.**

## Exact operator preflight and staged next steps

These are **future separately authorized operator actions**, not instructions to connect during this task.
Use a reviewed checkout and an existing approved dbo-capable migration connection. Never place passwords,
JDBC URLs, tokens or hashes of application credentials in command arguments/output. `sqlcmd` authentication
uses the operator's existing approved mechanism; do not invent a server/login or reuse local credentials.
Run from repository root, one connection per script, SQLCMD mode, stop on first error (`-b -V 16`).
An SSMS runner that continues GO batches after failure is unsuitable. Retain complete private output with
no display truncation; definitions/SIDs/ACL metadata are change-control evidence, not public chat output.

1. **Resolve configuration first.** Privately inspect effective server and each desktop configuration's
   URL/user precedence (including aliases/system properties/packaged sources), without sharing values.
   On each already-approved actual auth/runtime connection run the SELECT-only identity/token/effective
   catalog handoff in the Case runbook. Establish same intended database and distinct actual principals
   matching `shale_app`/`shale_runtime`; verify original/effective login and nested/public/fixed/server
   authority. Repeat for jobs and supported older builds. Different mappings block these fixed-identity
   scripts; do not substitute guessed names. Record exact compatible server/MSI/source hashes and fleet.
2. **Security owner reviews** the bounded dbo modules, internal exact-email hash capability and offline
   hash-attack risk, duplicate-email rejection (including inactive/NULL-owner duplicates), module owners,
   signing, dependencies and plans/scanned work. Review complete existing module definitions, runtime
   escalation/impersonation/server authority and any elevated wrapper/dynamic SQL paths. Preflight rejects
   executable elevated, signed or encrypted user modules outside V1/V2; static/deferred caller wrappers
   are rejected by module postchecks because same-owner chaining can skip a nested procedure DENY; unexpected authority needs separate disposition.
3. **Metadata/effective preflight.** Only after steps 1–2 are evidenced, run:

   ```powershell
   sqlcmd <approved connection options> -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v ExpectedDatabase="Shale" ReviewedIdentityMapping="CONFIRMED" SecurityOwnerApproval="APPROVED" -o <private-preflight-output>
   if ($LASTEXITCODE -ne 0) { throw 'Stop: auth cutover preflight failed' }
   ```

   The angle-bracket fields are operator-selected local paths/approved connection options, not literal
   arguments. Preflight changes only session execution context/SET options and temporary metadata tables; no permanent setting or grant changes. It requires dbo metadata visibility, no ambient
   transaction, expected database/collation/owners/principals/old resolver and zero three-table predicates.
   It tests actual database-user effective permissions; server/login-token review in step 1 is still
   required, particularly for sysadmin/CONTROL SERVER not represented by database-user impersonation.
4. **Retain prior evidence before authorization.** Export the full SELECT-only catalog handoff and
   exact old resolver definition/hash/owner, principal SIDs/role closure and every applicable permission
   row including class/major/minor IDs, grantor, grantee and G/W/D/R state. Preserve original grantor
   principals. Run `capture.sql` on the approved metadata connection and save its complete output, then
   close it (its #temp tables are session-local). Capture new-name absence separately. Store source,
   script hashes and exact client rollback artifacts privately. Missing/truncated metadata blocks work.
5. **Stage A, later authorization:** run `install.sql` with the same `-v` values and stop flags, saving
   complete output. It captures all prior database permissions/memberships/predicates/known module
   definitions before mutation, then asserts every original permission/grantor/state and membership
   remains and predicates are identical. Save the two emitted `DefinitionHash` values independently
   for Stage B/rollback. A preexisting module name stops; there is no overwrite/no-op/adoption path.
   On error the runner must close its connection so the open transaction rolls back; never continue.
6. **Actual-connection postchecks, before clients:** as actual runtime, verify both new module EXECUTE
   effective permissions are 0 and invalid-input calls receive permission error 229; as actual auth,
   verify both are 1 and run approved authentication smoke checks without collecting hashes/results.
   Verify caller identity/context restoration on success/failure. Reinspect owners/definition hashes,
   exact ACLs/grantors, original old resolver, memberships and all predicates. Database-user impersonation
   is useful but does not replace original-login checks on real connections. Stage A retains direct auth
   Users SELECT and old resolver EXECUTE; record this transitional exposure explicitly.
7. **Compatible releases, later authorization:** modules/permissions precede server and desktop artifacts.
   Test password login (A/B users), second credential exchange, enrollment, /me, issuance/refresh/logout,
   checked/unchecked remembered login, Windows restart/ordinary close, rotation/replay/outage Retry,
   revocation/disabled-account rejection, DPAPI unavailable behavior, user switching and pooled context.
   Verify User directory/Team/detail/assignments/history and creation/reset/revocation. Retain the exact
   deployed artifact hashes and minimum compatible fleet/drain evidence. No release is made by this PR.
8. **Stage B, only after full inventory/drain and separate authorization:** run `restrict-reads.sql` with
   the same values plus `CompatibleFleetDrained="CONFIRMED" CallerInventoryApproved="APPROVED"`
   `LookupDefinitionHash="<saved Stage A hex>" RememberDefinitionHash="<saved Stage A hex>"`.
   Save complete prior evidence and emitted `FenceSid` values. Failures roll back both roles/memberships.
   Rerun actual-connection checks: auth direct User/remembered reads and old resolver denied; V1/V2 work;
   runtime both modules and hash SELECT denied; original noncredential projections and credential writes
   work; runtime audit INSERT remains. Confirm no prior permission/grantor/state/member/predicate changed.
9. **Separate next decisions:** keep targeted three-FILTER/candidate-schema work deferred until installed
   compatibility, actual permission checks and owner approval pass. Later prepare original/derived/candidate
   hashes/manifests from the unchanged export and run the unchanged Case harness. No synthetic auth pass
   substitutes for Case SQL or deployed RLS/grant/performance/security acceptance.

Use `module-postcheck.sql` and, after Stage B, `fence-postcheck.sql` plus `read-postcheck.sql` as repeatable metadata/effective checks
on the approved dbo connection. Their checks are supplemented by actual calls above; empty filtered rows
are not permission-denial evidence. A repeat mutation script fails closed; inspect/reconcile rather than
replaying it. Serialize operator changes during capture/install/rollback; no concurrent permission editing.

### Operator command sequence (after each preceding approval/evidence gate)

Use the existing approved connection arguments as a PowerShell array `$approvedSqlcmdOptions`; no
connection or secret values are provided by this PR. Define private output paths and use the saved
hashes/SIDs from the prior successful stage. The explicit `-y 0 -Y 0 -u` flags preserve complete Unicode
catalog/definition output. Do not add `-W` or `-h` with `-y 0`; SQLCMD rejects those combinations.
The acknowledgement values are recorded decisions, not ways to bypass missing evidence.

```powershell
$review = @('ExpectedDatabase=Shale', 'ReviewedIdentityMapping=CONFIRMED', 'SecurityOwnerApproval=APPROVED')
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight failed' }
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/capture.sql -o $privatePriorCatalogOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: prior capture failed' }
# Stage A requires its own authorization after reviewing the capture above.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/install.sql -v @review -o $privateInstallOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation failed' }
# Save $lookupDefinitionHash / $rememberDefinitionHash from that successful output.
# Release acceptance and complete old-client/job drain occur BEFORE Stage B authorization.
$readCutover = @review + @('CompatibleFleetDrained=CONFIRMED', 'CallerInventoryApproved=APPROVED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/restrict-reads.sql -v @readCutover -o $privateReadCutoverOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read restriction failed' }
# Save $authFenceSid / $runtimeFenceSid from that successful output.
```

Rollback commands, only after the compatibility/risk decisions below:

```powershell
$readRollback = @review + @('RollbackCompatibilityReviewed=CONFIRMED', "AuthFenceSid=$authFenceSid", "RuntimeFenceSid=$runtimeFenceSid")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-reads.sql -v @readRollback -o $privateReadRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read rollback failed' }
$installRollback = @review + @('NewModuleConsumersDrained=CONFIRMED', 'RollbackCompatibilityReviewed=CONFIRMED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-install.sql -v @installRollback -o $privateInstallRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation rollback failed' }
```

Use only independently saved values, never query current state to invent a missing expected identity.
The scripts fail closed on missing/empty values. SQLCMD variables are trusted operator inputs: retain the
literal database/principal profile and use emitted hex hash/SID values, never arbitrary text/SQL.

For the candidate's **new Windows synthetic tests**, use the existing dedicated local SQL instance and
fresh guarded terminal described in the Case runbook, then:

```powershell
python -m unittest discover -s build/auth-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Auth launcher tests failed' }
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333 --maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'
if ($LASTEXITCODE -ne 0) { throw 'Candidate SQL remains unverified; retain fixed failure/detail pairs' }
```

All three PASS lines plus exit 0 now include the permission fixture under setup, followed by the existing
credential, remembered-rotation and pooled isolation checks. Earlier user PASS results certify their
original run only. Leave retained earlier resources/container intact; new test runs create fresh random
resources and use the existing exact nonce/SID cleanup guards. Do not weaken environment guards.

## Exact rollback

Keep additive modules while any consumer depends on them. Rollback never disables TenantFilter, edits shared
functions, restores a broad historical grant script, rewrites data, deletes audit history or rotates secrets.
Durable-session signing/cutoff/max-TTL constraints in the App Service guide remain independent requirements.

- **Failed batch:** stop/close the single SQLCMD connection. Its outstanding transaction rolls back. Confirm
  new-name absence or unchanged prior stage and compare the saved complete catalog snapshots.
- **Stage B rollback:** review which installed artifacts will depend on restored direct access and document
  the reopened hash-read risk. Run `rollback-reads.sql` with preflight variables, separately saved
  `AuthFenceSid` and `RuntimeFenceSid`, and `RollbackCompatibilityReviewed="CONFIRMED"`. It validates exact
  SIDs, dbo role owners, three exact membership rows, four DENY rows and original dbo grantor, no role
  nesting/extra grants or grantor dependencies, then removes only those newly created memberships/roles.
  Existing grants (including other grantors/W/column R exceptions) were never rewritten and remain exact.
  Compare all prior permission/member/predicate rows to the saved Stage A baseline before client rollback.
- **Stage A rollback:** after Stage B rollback and every V1/V2 consumer drained/rolled back to known compatible
  artifacts, run `rollback-install.sql` with preflight values, both saved Stage A definition hashes,
  `NewModuleConsumersDrained="CONFIRMED"` and `RollbackCompatibilityReviewed="CONFIRMED"`.
  It rejects changed definitions/owners/ACL/grantors, signatures and static dependencies. It removes only
  the two originally absent procedures and their six newly created ACL rows. Old resolver and every old
  permission/grantor/state/member remain. Dynamic/external dependency drain is an operator requirement.
  Verify restored older password/remembered flows and exact baseline catalogs. Unknown drift means stop
  for exact-state review; no CASCADE, broad REVOKE, grantor substitution or guessed GRANT replay.

If another separately authorized change has attached a Users FILTER, these scripts stop at preflight.
Coordinate that change's own compatibility/rollback disposition; never disable the entire policy.

## Audit compatibility and explicitly retained backlog

This permission change adds no business-data view or application mutation and no new audit schema/event.
Existing PHI/entity-action/session-security transactions remain authoritative; migration catalog evidence
belongs in operator change control, without credentials, emails, hashes, SQL payloads or raw logs in app audit.

**Audit permission remediation remains OPEN:** reported runtime auditLog SELECT/INSERT/UPDATE/DELETE means
history is **not database-enforced append-only**. This candidate preserves those permissions, including
INSERT needed by required Overview audit. A separate reviewed scope must inventory PHI/legacy writers and
administrative tools, preserve historical viewer behavior, establish active-admin authority below the UI,
reduce audit UPDATE/DELETE and ordinary SELECT compatibly, decide strict write BLOCKs and global Status
immutability, and verify failure/rollback. Audit-list SQL/bound-value stderr logging remains an independent
privacy finding. Shared desktop credentials/mutable SESSION_CONTEXT and broader schema CRUD/EXECUTE remain
trust-boundary debt; do not call this a full least-privilege/security acceptance.

## Verification record

Executed on Linux, Python 3.12.14, Maven 3.9.11, Temurin JDK 21.0.12.1 and the existing Microsoft
JDBC 12.6.1.jre11. `mvn` below denotes `/workspace/.tools/bin/mvn` with its existing proxy/CA settings;
no repository build/dependency configuration changed. SQL Server 2022 uses task-owned image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, published only to
127.0.0.1:14333. A fresh random database/logins and original nonce/login SIDs guard the Java fixture;
separate SQLCMD databases use an independently generated exact nonce. No Azure destination/credentials,
original schema input, Case SQL execution or operator-owned retained resource is used.

Pre-edit inventory covered existing auth/module/lifecycle, remembered migration/store/UTC diagnostics,
User create/reset/removal/projections, runtime, server exchange and desktop lifecycle tests. No existing
production caller or DTO expectation changes. The new helper executes the actual candidate scripts within
the existing opt-in harness; the unchanged quiet launcher still exposes only three PASS scenario lines.

| Check | Final result / boundary |
| --- | --- |
| Focused auth/module/store/User projection/password tests | 38 PASS, one explicit opt-in SQL skip. |
| Additional existing server/desktop/UI auth/session compatibility | 47 PASS. Synthetic regression evidence, not installed-client acceptance. |
| Committed base-to-head selector / affected suite | 54 PASS, one explicit opt-in SQL skip. Security-data plus compiled modified helper/harness; no full-suite escalation. |
| Critical reactor `mvn -B test` | 116 PASS, zero failures/errors/skips. |
| Auth launcher Python | 17 PASS, one explicit native-Windows skip. |
| Unchanged Case launcher / selector Python | 4 PASS / 24 PASS; no Case SQL. |
| Final disposable Java SQL / real quiet launcher | All three scenarios PASS; exact cleanup, exit 0. New broad-grant/cutover/rollback checks execute under setup before application scenarios. |
| Real SQLCMD `-b -V 16` | PASS: error-stop atomic installation failure, install/restrict/rollback roundtrip, exact catalog equality and nonce cleanup. |
| Documentation/scope/Git checks | PASS: relative links/fences, SQL includes, no bare RowCount aliases, exact unchanged Case tooling/original auth SQL/R4 ledger and whitespace. |

The fixture reproduces runtime direct database EXECUTE and both principal/role dbo CRUD plus role schema
EXECUTE. It proves installer-only and REVOKE-only exposure, both final module denials (229), public and
future-role-member inheritance, and same-owner wrapper bypass followed by explicit dependency rejection.
It tests missing configuration/fleet acknowledgements, installation postcheck failure with zero committed
modules, Stage B write-capability failure with neither role retained, column-GRANT behavior, exact original
G/W/D/R/non-dbo-grantor preservation, wrong SID/hash and changed-ACL rollback refusal, and complete staged
roundtrip. Auth direct User/credential reads and old resolver fail after Stage B; new modules/password and
remembered rotation work. Runtime noncredential projection and actual parameter-bound password updates
remain usable; broad audit SELECT/UPDATE/DELETE are deliberately still available. Every rejected stage
asserts dbo caller restoration and no open transaction. Synthetic Users RLS is added only after these
checks, preserving the existing Case/source-schema boundary.

Initial offline runs missed existing dependencies, resolved through the configured proxy without POM
changes. An overlapping Maven attempt produced a transient compile failure and was discarded; counted
final runs are sequential. The first critical attempt was blocked when an existing desktop test opened
a loopback HTTP socket; the unchanged suite passed with approved local socket access. Early candidate
SQL runs exposed an unsupported permission-name check, catalog/state collation comparison and THROW/
REVERT transaction behavior; these were corrected and rerun rather than weakening guards. Initial private
SQLCMD setup had file-access/header-flag errors; final real wrapper execution passed with complete output.
No failing attempt is counted as acceptance. Existing historical all-tests advisories remain unresolved;
no new all-tests run was required or claimed.

Exact commands executed (private helpers are task scratch tools, not application configuration; dedicated
local SQL credentials were supplied only through private prompts/child environment):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest,PreTenantAuthenticationLocalSqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dtest=AuthServiceAdapterTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SessionControllerTest,ServerAuthSessionLogoutTest,DesktopSessionEnrollmentLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopRuntimeSessionLifecycleTest,LoginRememberedSignInContractTest,AuthenticatedSessionLifecycleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 --head HEAD --format markdown --plan-output work/auth-cutover/selection.json
mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B test
python3 work/auth-cutover/run_sql.py
python3 work/auth-cutover/run_sqlcmd.py
python3 work/auth-cutover/run_launcher.py
python3 work/auth-cutover/check_docs.py
git diff --check 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 HEAD
```

The public Windows launcher procedure above reproduces the committed opt-in Java fixture; private helpers
only supply task-local credentials/capture fixed output or exercise real SQLCMD with fresh synthetic setup.
Passing disposable SQL does not close actual configured identity mapping, independent security-owner
approval, installed fleet/drain, deployed parity/permissions, Case SQL, broader audit/admin remediation
or other R4/security/host/device gates. **Deployment remains BLOCKED; security acceptance OPEN.**
