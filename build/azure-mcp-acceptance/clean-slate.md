# Clean-slate FAST TRACK (operator only)

This opt-in path supersedes continuation/recovery **for this authorized disposable
reset only**. It never reads or edits a historical journal to decide what to run.
Production `Shale`, Azure server configuration, the shared plan, application
startup/deployment and MCP/AI activation remain outside its scope.

From the reviewed checkout on an interactive PowerShell 7.4+ console, run **one command**:

```powershell
.\build\azure-mcp-acceptance\Start-CleanSlateAcceptance.ps1
```

Have Azure CLI already signed in to the existing approved subscription/route,
Python 3 and JDK 21 on PATH. Keep the acceptance App Service stopped. Use a private
console without transcripts/input logging. The command asks for the existing
private packet directory, unchanged authoritative fixture path, and the existing
**acceptance database owner credential** through `Get-Credential`. Passwords never
enter command arguments, SQLCMD environment variables or reports. Valid reviewed contained-user credentials are reused from the private inputs, held
in memory, and supplied through `SqlCredential`. Missing contained-user secrets
are requested securely once per missing identity before approval and written only
to the new protected input copies. Existing secrets and DPAPI stores are unchanged.
Invalid reviewed fixture/seed/timezone inputs stop before reset.

Review the displayed fixed destination and new private run path. Type
`RESET_AND_REBUILD_SHALE_MCP_ACCEPTANCE` once. This authorizes deletion of all
acceptance schema/data/users and rebuilding all 51 stages, including the existing
private provisioning, reviewed synthetic seed and binding inputs, and normalization
**only after** the rebuilt catalog matches the authoritative baseline. There are
no per-stage password prompts or approvals. No `-Force`, unattended approval,
configurable SQL destination or recovery override exists.

## What happens

The entry point validates fixture/source fingerprints, all 51 rendered stage
hashes, reviewed seed/binding approvals, seed values, identity credentials, timezone evidence
shape and tools before approval. It checks the exact subscription/resource IDs,
server/database/edition/collation, dbo/CONTROL and OFF SQL controls. Existing
worker/JVM/timezone evidence is reused; no deployment, worker configuration,
origin or binding input is changed. Invalid live binding/timezone checks still
fail closed. This does not re-observe a stopped worker.

A protected `work/clean-slate-<guid>` directory holds copies of the reviewed
inputs, rendered packets, private catalog comparison, a **new** hash-chained
journal and safe report. Original inputs, journals, unresolved failed attempts
and reports stay byte-for-byte intact. The source packet's existing supervisor
lock and a new clean-slate lock prevent concurrent local runners. Use an exclusive
maintenance window: local locks cannot prevent another machine/operator from
starting an app or connecting to the database. Historical evidence is inventoried
and checked again before reset and after the run.

The reset is **in place**, preserving Azure SQL database identity/configuration,
owner and Azure resource settings. A repeated SQL identity fence precedes a single
DDL transaction with `XACT_ABORT ON`, exclusive installer lock and rollback/rethrow.
Only acceptance object families are supported. Unknown/unsupported features
(DDL triggers, temporal/external/memory-optimized tables, user-defined types,
assemblies, credentials, external sources, keys/certificates, XML collections)
are refused before drops. A dependency cycle rolls back the reset. No fallback
connects to `master`, drops/recreates a database, or changes the shared server.
Stage 00 independently requires the resulting database to be empty.

At the Stage 09 boundary, a fresh read-only catalog capture is compared with the
hash-gated authoritative fixture plus reviewed Stages 02–08. A mismatch stops
before normalization. Only a passing comparison supplies new physical-ID-sensitive
Phase1R fingerprints, in the **new copy** of `phase1r.json`. Re-rendering may change
only Stages 10–12; any other changed stage bytes stop the run. The single whole-run
approval authorizes this baseline-verified operation; the existing standalone
comparison tool continues to set `normalizationApproved:false` and never grants
approval itself. Old database-incarnation hashes are not treated as new evidence.

Every source gets a new, unpooled encrypted SqlClient connection pinned to
`tcp:shale.database.windows.net,1433` / `ShaleMcpAcceptance`, with seven required SET
options, neutral session checks and bounded results. Batches retain their SQL
bytes; GO inside strings/comments is not a separator. Every reader row and every
`NextResult` is consumed. Any error stops subsequent batches/sources/stages. An
open transaction fails the source and is rolled back before physical disconnect.
Successful sources must end in autocommit. No session context or impersonation
can carry into another source. Owner/OFF and all installer security checks remain.

## Stage 21 finding and correction

The exact historical failed module/property **cannot be reconstructed**. All 16
embedded module definitions match their expected hashes offline. The previous
all-missing output can describe post-rollback absence; a missing console rollback
marker does not prove rollback failed. Neither that output nor a source success
message proves a durable install. No live SQL implementation defect has been
established, and this change does not alter module definitions, hashes, ownership,
execution contexts, RLS, permission assertions or rollback guarantees.

The corrected execution path eliminates SQLCMD exit/console capture ambiguity:
source results cannot mask a later driver error, and an uncommitted source cannot
succeed. Existing pre-THROW, phase-tagged diagnostics are retained. Stage 21 becomes
complete only when both the installer and the **independent verifier on a new
connection after installer disconnect** succeed. If objects disappeared, the
verifier fails and 21 remains pending. Reports contain only safe error numbers,
allowlisted assertion categories and bounded module-name/property flags with
transaction observation context, never definitions, credentials or application
rows. This fixes the runner's false-success acceptance path; it is not a claim
that a still-unobserved genuine SQL assertion will magically pass after reset.

## Completion and failure

Progress prints `Completed N/51`; only a successful stage advances the confirmed
prefix. `report.json` must say `Status: complete`, `Completed: 51`, app `Stopped`
and activation `OFF`. The app is checked stopped before reset, before every stage
and at completion; SQL OFF fences remain throughout. Nothing starts or enables
it. 51/51 means the **51 SQL preparation/catalog/contained-user stages**, not live
MCP/application acceptance.

On any real SQL error, the command stops without retry. Preserve the new run and
its pending journal record. `Operation` identifies preflight categories; SQL
failures include safe error numbers/assertions/module flags. No historical journal
is modified and no pending stage is silently replayed. After correcting a blocker,
the same command creates another new run and requires one new destructive approval;
it does not resume/reconcile an old failed journal. Do not automatically reset in
an error handler. Reset DDL is atomic, but the whole 51-stage build is intentionally
not one transaction: previously committed new-run stages remain if a later stage
fails. An interrupted reset has unknown outcome until independently checked; a
missing client marker is not evidence of failed rollback.

Audit compatibility: this authorized disposable administrative reset deletes the
database's application audit data together with fixture data. Existing operator
failure evidence is retained externally. The private hash-chained run journal
records approval/reset/stage outcomes and hashes, not PHI, secrets or SQL payloads.
No production domain mutation or new application audit schema is introduced.

## Offline verification

```text
python -m unittest discover -s build/azure-mcp-acceptance -p test_clean_slate.py -v
python -m unittest discover -s build/azure-mcp-acceptance -p test_*.py
python build/test-selection/select_tests.py --base codex/latest --head HEAD
mvn test
```

Tests use synthetic inputs/readers/connections and mocked Azure responses. They
never execute SQL/reset or connect to Azure. Live reset/Stage 21 execution is
reserved for the explicitly approving operator, so Azure behavior remains unrun
by Codex. No automatic merge or GitHub Actions testing is added.
