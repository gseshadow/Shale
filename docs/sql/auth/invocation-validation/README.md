# Synthetic Azure SQL invocation validation

**Executable experiment; production activation REFUSED.** This package creates only synthetic
procedures and diagnostic database-scoped XE sessions in an independently approved, dedicated,
empty Azure SQL Database. It never connects to Shale, copies/backups of Shale, or production identities.
It provisions no database, server, identity, permission, credential, Storage resource or scheduler.
Do not run it until the approvals below exist. This PR authorizes no Azure execution.

The three probes stand for the three legacy modules; they deliberately do **not** reproduce their
credential/accounting bodies or use their names. This is a source-payload experiment, not another
cache sampler. The raw start/end session is test instrumentation, **not a future live candidate**.
Even a passing experiment does not approve, configure or activate monitoring in Shale.

## Decision and evidence

At 2026-10-15 22:29:04 UTC the operator reports #1875's sampler succeeded against Shale (exit 0):
all three legacy procedures had `CachedPlanCount=0`, NULL execution counts/times and
`INCOMPLETE_CACHE_OBSERVATION`. These are **unavailable cache observations**, not zero usage.
Query Store AUTO also had no retained statements. No complete observation/drain record follows.
These are attributed operator results; this task did not connect to Azure or inspect original captures.

The owning [cutover runbook](../../../testing/auth-module-permission-cutover.md) and #1874 remain
authoritative. Preserve retirement/rollback, unchanged preflight and original captures, Stage A/B,
installed desktop compatibility and every RLS prerequisite. Live-update negotiate authorization is a
separate open concern; this package does not resolve or change it. No application/domain mutation,
PHI read, application audit event or audit schema change is introduced. Approval, security-metadata
reads, synthetic DDL and evidence retention belong in private operator change control.

## Why this experiment

Official Microsoft sources read October 15, 2026:

| Source | Design consequence |
| --- | --- |
| [Extended Events in Azure SQL](https://learn.microsoft.com/en-us/azure/azure-sql/database/xevent-db-diff-from-svr) | Azure SQL Database uses `ON DATABASE`; sessions cannot collect another database's events. Event files require separately authorized Azure Storage. No event files or Storage credentials here. |
| [Event column metadata](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-object-columns-transact-sql) | Inspect data, readonly and customizable fields, descriptions, defaults and capabilities on the **actual build before choosing/creating sessions**. No ACTION clause does not remove intrinsic data. |
| [CREATE EVENT SESSION](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-event-session-transact-sql) | Exact predicates; explicitly disable statement collection and causality. `ALLOW_SINGLE_EVENT_LOSS` avoids blocking workload; it provides no completeness guarantee. Never substitute `NO_EVENT_LOSS`. |
| [Targets](https://learn.microsoft.com/en-us/sql/relational-databases/extended-events/targets-for-extended-events-in-sql-server) | Ring buffer overwrites old events, loses contents on stop and can truncate XML at 4 MB. Limit target to 1024 KB. `event_counter` is synchronous but aggregation cannot sanitize an unsafe event source; histogram collisions are unsuitable for exact per-module negative evidence. |
| [Session health](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-database-sessions-azure-sql-database) and [target health](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/sys-dm-xe-database-session-targets-azure-sql-database) | Record start identity, session drops/oversize, buffer sizes/activity and target failures as well as complete raw target data. Missing/unsupported health is UNKNOWN, not healthy. |

Before selecting this mechanism, local SQL Server 2022 metadata was inspected: both events expose
`source_database_id`, `object_id`, line/offsets, module type/name, intrinsic `statement`, and a boolean
customizable `collect_statement` defaulting to false; module_end also exposes duration and row_count.
Local raw events with `collect_statement=0` still contained an **empty statement field**. The verifier
accepts that disabled empty field (or its absence), rejects any nonempty value, unexpected field,
action, causality attribute, nested value or synthetic sentinel anywhere in the serialized target.
This local schema is a hypothesis for Azure, never Azure evidence. The Azure Discover/Setup boundary
forces independent metadata review and an exact saved-file SHA256 before creating any session.
Unknown fields, options or semantics require rejection/scoped review, not an expanded allowlist to pass.

No retained SQL text, parameters, email, credentials, password hashes, tokens or application data may
enter a live candidate. Synthetic inputs deliberately contain distinguishable `IV_FORBIDDEN_` sentinels;
the diagnostic database contains no real values. Inspect **all raw JSON RawTarget strings/XML**, including
intrinsic values, type/package nodes, headers and target attributes. Exporting fewer columns, redaction,
masking a viewer or switching to a counter cannot repair capture of unsafe source data.

## Minimum resource facts and approvals (not supplied)

Use `facts.template.json` as a private form; every blank must come from actual approved records:

- Exact subscription/resource group/server/database resource ID, logical SQL endpoint, expected SQL
  `SERVERPROPERTY('ServerName')`, database name/ID, region, service objective/pool and engine/build.
  This package accepts commercial Azure SQL Database endpoints only, not Managed Instance/Fabric.
- Independent provenance that the dedicated empty database was created for synthetic validation,
  has never held Shale/application data and is not a copy/restore/backup. A safe-looking name is not
  provenance. The package also rejects names containing Shale/system-database names and any visible
  user objects or existing database telemetry. Do not rename a copy to bypass the guard.
- Named resource/security owner approval of this exact source revision and experiment: synthetic DDL,
  raw payload inspection, three bounded in-memory sessions, one hour maximum and 1000 stress calls.
  Confirm the service objective can tolerate these bounds and obtain the owner's actual cost approval;
  this task supplies no pricing, resource name, approval ID or approval record.
- An existing, separately approved **synthetic-only** identity resolving to database user `dbo`, with
  metadata/VIEW DATABASE STATE and database XE create/alter/drop authority. The dbo requirement prevents
  filtered catalogs masquerading as an empty database. No grants are supplied. Never reuse production
  credentials. If authority or connectivity is missing, stop for independent disposition.
- Approved operator workstation/network/firewall route and existing authentication method, operator
  staffing/exclusive metadata window, private evidence directory with restricted ACLs, reviewer and
  retention destination/decision. No firewall/identity/permission changes are performed here.
- Actual references for experiment, provenance, identity, metadata review, exclusive window and
  retention; fill the corresponding JSON fields. Set `DedicatedEmptySynthetic` to
  `APPROVED_NO_SHALE_DATA_OR_CREDENTIALS` **only after that actual decision**. Acknowledgements do not prove it.

Cross-database/colliding-object-ID and restart/failover trials need additional approved synthetic targets
and fault operations. They are deliberately not performed by this package; record these gaps as untested.
No future live-monitor approval is bundled with any of these experiment approvals.

## Windows operator sequence

Prerequisites: reviewed checkout, Windows PowerShell 5.1 with .NET System.Data.SqlClient supporting
`AccessToken`/async commands, and existing Python 3.8+. No installation is performed by these scripts.
Native Windows/.NET execution has not been certified in this task. Use a fresh private terminal; do not
turn on transcripts, query echo, connection-string logging or shell tracing. Keep state/facts/evidence
outside Git and under exclusively controlled non-reparse-point folders (including trusted ancestors).

1. Privately complete the form from real records and review all SQL/PowerShell/Python. Set local paths
   below to approved paths; these are PowerShell variables, not fabricated resource values. Authenticate
   using an approved **synthetic-only** SQL credential prompt, or supply an already approved Entra access
   token in memory using `-AccessToken $syntheticAccessToken` instead of `-Credential`. Do not fetch a token
   or change authentication/permissions merely to run this package. Do not store secrets in the facts file.

   ```powershell
   $ErrorActionPreference = 'Stop'
   $package = (Resolve-Path 'docs/sql/auth/invocation-validation').Path
   # Set $factsPath, $statePath and $evidenceParent to approved private paths.
   # $statePath must not exist; $evidenceParent must already exist with private ACLs.
   $syntheticCredential = Get-Credential -Message 'Approved dedicated synthetic SQL identity only'
   & "$package\harness.ps1" -Phase Discover -FactsPath $factsPath -StatePath $statePath `
       -EvidenceParent $evidenceParent -Credential $syntheticCredential
   if ($LASTEXITCODE -ne 0) { throw 'Stop; discovery failed' }
   $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
   ```

   Every connection pins encrypted certificate-verified endpoint/database/database ID/dbo identity,
   Azure engine edition 5 and the discovered version (Stop permits version drift solely to disable owned sessions and records the new version). There is no fallback identity, database or local
   SQL mode. Discovery reads metadata only and refuses nonempty databases/existing telemetry.

2. Review `identity.json` and **every** `metadata.json` row privately: package/event availability,
   intrinsic/readonly/customizable fields, types, defaults, descriptions, controls and predicate fields.
   Check ring-buffer options, maximum sizes and permissions on this tier/build. Require configurable
   boolean `collect_statement` and event `source_database_id`/`object_id`; review module_start and
   module_end independently. Abort if absence of sensitive data cannot be tested safely or unexpected
   fields are not understood. Never select rpc/batch/statement/sql_text/error_reported events as a fallback.

   ```powershell
   $metadata = Join-Path $state.Directory 'metadata.json'
   # Only after the independent metadata review recorded in the actual change record:
   $reviewedMetadataSHA256 = (Get-FileHash -LiteralPath $metadata -Algorithm SHA256).Hash
   & "$package\harness.ps1" -Phase Setup -StatePath $statePath -Credential $syntheticCredential `
       -ReviewedMetadataSHA256 $reviewedMetadataSHA256
   if ($LASTEXITCODE -ne 0) { throw 'Stop; setup refused or failed' }
   ```

   Setup rechecks metadata bytes and target, refuses existing task schema/sessions, creates seven synthetic
   procedures under two random task schemas, then three **stopped** diagnostic sessions. No production
   definitions/permissions are used. Review all three `*-config.json` files (complete configuration XML,
   including predicates, fields, actions, targets and options); fingerprints protect later lifecycle steps.
   Serialize catalog changes throughout. Lost state/partial setup requires manual exact-ownership review;
   never recreate ownership by matching names/prefixes or silently adopt an existing resource.

3. Run the bounded workload once. The script starts/collects/stops each session; there is no unattended
   multi-day collection or startup activation. Normal workload: each module gets direct, nested wrapper,
   dynamic, two overlapping asynchronous calls (overlap must also be proved by the raw timestamps), a thrown failure and a cancelled WAITFOR call. It also
   attempts parameter-conversion failure before entry (expects 8114), a same-schema unrelated procedure,
   a near-name decoy and an identically named module in another task schema. Concurrency uses distinct guarded connections and a one-second synthetic wait.
   Cancellation requires raw module-entry acknowledgement before Cancel; no fixed sleep substitutes for
   entry proof. Expected per module: seven starts; six or seven ends, with the cancelled-end outcome
   reported explicitly. Any different outcome fails and needs investigation; do not adjust expected counts.

   ```powershell
   & "$package\harness.ps1" -Phase Workload -StatePath $statePath -Credential $syntheticCredential
   if ($LASTEXITCODE -ne 0) { throw 'Keep all evidence; run guarded Stop, then review failure' }
   python "$package\assertions.py" --state $statePath
   if ($LASTEXITCODE -ne 0) { throw 'Privacy/coverage not established; no live activation' }
   ```

   The stress session has 1024 KB session buffers, 1024 KB target memory and an eight-event limit; 1000
   probe calls should exceed the target. The assertions must **reject** that interval as incomplete even
   with zero session drop counters. Actual session loss under these bounds is measured, not promised;
   zero drops is not proof for a future rate/concurrency/tier. A third session uses a deliberately wrong
   source-database ID: six source events from three calls must all be excluded. It tests the database
   predicate without opening another database; it is not a cross-database-context trial.

   Collection reads the entire nvarchar(max) target through SqlDataReader and serializes it losslessly
   as JSON; there is no SQLCMD display-width truncation. Before/after/final and cancel-entry snapshots,
   exact session starts, UTC boundaries, buffer/target health and hashes are retained. Ring-buffer output
   is saved **before** stop destroys it. Missing target, malformed/truncated raw data, changed module IDs,
   failed target buffers, session resets, stale collection or mismatched counts are UNKNOWN/failure.
   Stop verification is an empty live-target result, never inferred from successful polling.

4. Always use Stop after an interrupted/failed run. It verifies catalog ownership/configuration,
   captures any still-running task target and stops only the exact recorded sessions. Repeated Stop
   preserves previous snapshots with new unique names. It does not drop objects or evidence.

   ```powershell
   & "$package\harness.ps1" -Phase Stop -StatePath $statePath -Credential $syntheticCredential
   if ($LASTEXITCODE -ne 0) { throw 'Ownership/connection unavailable; retain resources for exact manual review' }
   ```

   Workload also attempts final raw capture and guarded stop in nested finally blocks. Connection loss,
   host termination or catalog drift can prevent this; do not claim automatic cleanup guarantees.
   STARTUP_STATE is OFF. No monitor survives intentionally; the separate one-hour guard refuses further
   setup/workload after expiry but is **not a server-side timer**. Stop remains the operator's responsibility.

5. Inspect every raw payload and assertion output, record each gap/outcome and have the owner decide
   retention. Independently copy and hash-verify the exact `Files` inventory plus the state/facts and
   source revision into the approved private retention destination. Keep originals. Only after the copy
   and actual retention decision, create this additional local manifest from those real values:

   ```powershell
   $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
   # Set $verifiedCopyDirectory and $retentionApprovalReference from the actual completed retention step.
   $manifest = Join-Path $state.Directory 'retained-manifest.json'
   if (Test-Path -LiteralPath $manifest) { throw 'Preserve existing manifest; review, do not overwrite' }
   @{Token=$state.Token;Files=$state.Files;VerifiedCopyDirectory=$verifiedCopyDirectory;
     RetentionApprovalReference=$retentionApprovalReference} | ConvertTo-Json -Depth 20 |
       Set-Content -LiteralPath $manifest -Encoding UTF8
   $retainedHash = (Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash
   & "$package\harness.ps1" -Phase Cleanup -StatePath $statePath -Credential $syntheticCredential `
       -RetainedEvidenceSHA256 $retainedHash
   if ($LASTEXITCODE -ne 0) { throw 'Stop; exact cleanup refused or partially completed; review state' }
   ```

   Cleanup verifies the exact ownership token, full synthetic object/ACL catalog fingerprint, exact
   session IDs/configurations, stopped state and all retained-copy file hashes. It drops only the three
   recorded sessions and seven explicitly named task procedures/two schemas. No wildcard/prefix matching,
   database deletion, Storage/credential deletion or local evidence deletion occurs. Unexpected objects,
   dependencies or drift refuse cleanup. Session drops and object cleanup are separate operations; a
   partial failure needs exact manual review, not blind replay. Preserve all raw evidence and the original
   #1874 captures regardless of experiment outcome. Finally close the terminal/dispose in-memory secrets.

## Acceptance limits and practical next steps

An exit 0 from assertions means only that the bounded synthetic trials match their explicit contracts,
including rejecting the deliberately lossy interval. It **always prints production_activation REFUSED**.
The full source payload, not its exported view, must satisfy privacy. Failed privacy, missing metadata,
unknown loss or unresolved coverage means **no live activation** and no weakening of retirement gates.

Module entry is an observation of **entered modules**, not all attempted invocations. The pre-entry
conversion trial should produce no module events; permission failures, missing modules, compile failures
and connection loss before entry also remain outside an entry counter. Cancelled exit coverage can vary.
Do not label module_end a complete attempted-call ledger. Wrong-database predicate and decoy tests do not
establish cross-database execution-context/replica coverage. Restart/failover, scaling, read-only replicas,
engine drift, long-running/recursive/recompiled calls, sustained production load, collection outages,
watermarks/durable rollover, operator availability and fleet/job/dormant-client coverage remain untested.
In-memory targets cannot supply a durable multi-day negative record. Zero drop counters alone cannot
establish either complete source coverage or retention. Retest/review build drift in a new synthetic run.

If Azure emits forbidden data or lacks controls/health, record the demonstrated build/schema/payload
limitation privately; do not claim Azure universally cannot support privacy-safe events. Alternatives:
existing independently approved adequate telemetry; explicit caller migration and configuration/job/fleet
inventory plus drain evidence; separately reviewed application instrumentation emitting only allowlisted
module identity/counts at a trusted seam (with deployed coverage proved); or a separately reviewed,
privacy-verified exact-module **entry-only** design with three event_counter sessions and durable interval
health/continuity records. No live candidate or Storage setup is supplied here. Aggregation, a different
viewer or a shorter observation window cannot cure an unsafe source or missing callers. Cache counters
remain positive evidence only; Query Store AUTO absence remains insufficient. If coverage of required
attempts/consumers cannot be established, the #1874 retirement gate stays BLOCKED.

## Verification in this task

See the new dated section in the owning runbook for exact commands/results and the local/Azure boundary.
Offline assertions have adversarial payload/loss/ownership regression tests in
`build/auth-verification/test_invocation_validation.py`. Local SQL Server uses `ON SERVER` and different
catalog/health support; it cannot certify the shipped Azure `ON DATABASE` commands, service-tier authority,
Azure failed-buffer counter, native Windows .NET behavior or installed desktop compatibility.
