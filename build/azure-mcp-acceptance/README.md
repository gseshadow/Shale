# Supervised local SQL acceptance

This tool prepares and supervises the **remaining 33 of 51 SQL stages**. It has no
Azure management, deployment, app-start, firewall or enablement commands. Production
`Shale` is excluded. Keep `shale-mcp-acceptance` stopped and all AI/MCP controls OFF.
Offline test results are not Azure SQL or runtime acceptance.

## Prepare without changing the existing checkpoint

Use PowerShell 7, JDK 21 and the independently reviewed Microsoft **ODBC SQLCMD**.
Use a private terminal without transcripts, SQLCMD/application environment overrides,
shared output or input logging. Restrict the existing packet directory to the operator.
Never enter passwords in command arguments; SQLCMD prompts for each connection.
Retain the existing DPAPI credential store; this installer neither reads nor changes it.

From the reviewed checkout, retain the original rendered packet and the independent
18/51 evidence (through `15b-foundation-users`), failed 15c record and SSMS zero counts.
Do not rerun completed stages, edit rendered SQL, regenerate BCrypt values, or refill
secret inputs. Use the same unchanged authoritative fixture and existing private packet:

```powershell
$Packet = Join-Path (Get-Location) 'work/azure-mcp-acceptance'
$Fixture = Read-Host 'Unchanged authoritative fixture path'
.\build\azure-mcp-acceptance\Prepare-Packet.ps1 -PacketDirectory $Packet -AuthoritativeFixture $Fixture -Continuation
```

`-Continuation` requires every existing input. It compares a fresh offline fixture export
against the existing export and refuses differences; it never replaces existing inputs
or evidence. It creates a new rendered directory and reconciliation script. Record its
exact printed path as `$Rendered`; never automatically choose the newest directory.
Independently compare completed stage hashes to retained evidence. They must match.
All remaining stages have new SET/source-completion instrumentation and need new,
independent stage hash review. Private source SQL/transactions/permissions are unchanged.

```powershell
.\build\azure-mcp-acceptance\Test-Readiness.ps1 -RenderedDirectory $Rendered -AuthoritativeFixture $Fixture
# To check only the immediate next stage:
.\build\azure-mcp-acceptance\Test-Readiness.ps1 -RenderedDirectory $Rendered -AuthoritativeFixture $Fixture -Stage 15c-seed
```

Readiness is offline and does not alter inputs. Default `All` reports blockers for every
remaining stage and fails if any is blocked. Expected initial blockers include missing
worker/binding evidence and unapproved/unfilled Stage 28 capability inputs. It validates
fixture fingerprint, local tools, ordered manifest, every file hash and destination fence,
source dependencies, seed approval/four distinct BCrypt shapes, binding approvals/zones/
samples and capability approval/12 distinct secret inputs, without printing their values.
Source checks cannot establish live roles, permissions, stopped-app state or JVM evidence.
A stage-specific PASS permits a later approval prompt, never execution by itself.

## Supervise the remainder from one terminal

**Future operator action only:** run this only after separately approving continuation,
reviewing this change, the new Stage 15c hash, and the read-only reconciliation hash.
Nothing in this PR authorizes the current failed Stage 15c to run.

```powershell
.\build\azure-mcp-acceptance\Start-SupervisedAcceptance.ps1
```

It prompts for the exact rendering, original fixture path and existing acceptance owner.
It shows client/stage hashes and the fixed target, asks for independent hashes and exact
approval phrases, then executes one approved stage through `Invoke-Stage.ps1`. No SQL is
printed. Each SQLCMD connection still prompts privately for its principal's password.
The initial read-only approval covers the identical reconciliation script for this session;
each stage, each contained-principal boundary and continuation require separate approval.
Any different answer stops. `CONTINUE` only permits the next preview.

First launch imports 18 completed stages only after retained independent evidence is
confirmed and a fresh guarded owner connection verifies five contained foundation users,
OFF controls and zero tenant/user/Case counts. It never executes stages 00–15b. Every
stage has a fresh pre/post read-only catalog/count snapshot, exact source-completion
markers and required status/named zero-finding results. Nonzero exit/finding, missing or
ambiguous output, drift or interruption stops immediately. Raw output is discarded.

The private packet's `supervised.jsonl` is append-only, hash chained, flushed to disk and
protected against concurrent runner sessions with `supervised.lock`. It contains only
stage/hash, safe outcome/count summary, exit code, UTC time and metadata/count snapshot.
Do not edit/delete/truncate it. Hash chaining detects corruption, not a malicious operator
with write access; retain private backups and existing independent evidence.
Relaunching verifies ledger order, completed hashes and live state before advancing.
New renderings under the **same packet** reuse this ledger and must preserve completed
stage hashes. Never copy a ledger to a different database/packet to adopt its state.

**Pre-Stage-16 hard STOP:** actual test-worker JVM/timezone proof, approved SQL mapping,
all 28 samples and independent binding evidence are mandatory. The runner cannot obtain
these by starting/deploying the app. Keep this gate blocked until a separately authorized
evidence window has finished and the app is stopped again. Then approve only the necessary
private binding inputs, re-render with `-Continuation`, and relaunch against that exact new
rendering. The same ledger resumes at Stage 16, without replaying seed. Stage 28 likewise
requires independently approved existing/new capability inputs, without altering foundation
users. Stages 31–49 authenticate as their exact distinct contained principals, never owner.

## Failure and interrupted-stage recovery

There is **no automatic retry**. A pending/failed ledger record blocks execution even if
SQLCMD returned zero. Preserve it. Independently verify the stage's complete effects,
receipts, control state, permissions/identity and committed/rolled-back outcome using only
separately approved **read-only** checks. The metadata snapshot alone is insufficient to
classify a transaction. Use the correct versioned verifier; never re-seal or rerun DDL.
Partial/unknown outcomes remain blocked for separate investigation.

The existing wrapper can run the hash-approved rendered `reconcile.sql` for a reviewer:

```powershell
.\build\azure-mcp-acceptance\Invoke-Stage.ps1 -RenderedDirectory $Rendered -Stage 15c-seed -OperatorUser $Owner -ReadOnlyCheck
# Preview only. A later separately approved read-only execution also requires -Execute,
# -ApprovedSHA256 and the existing exact execution acknowledgement.
```

Reconcile using the original failed rendering before changing any inputs/source. Preserve it.
After independent review, relaunch with `-Recovery VerifiedCommitted` or
`-Recovery VerifiedRolledBack`. Both require fresh live reconciliation, explicit independent
review confirmation and the exact independently verified `ACCEPTANCE_SNAPSHOT` record.
Rollback also requires equality to the retained pre-execution snapshot; seed counts must
match the claimed disposition. Recovery appends a decision and **exits without execution**.
A subsequent normal launch requires new approval before any retry of a verified rollback;
a verified commit advances without replay. After recording a verified rollback, a newly rendered
correction may use a new independently approved hash while retaining the entire failed history. A corrupt/truncated ledger requires separate
review and preservation, never deleting it to unblock the runner.

Manual `Invoke-Stage.ps1` remains available with its existing security controls. Do not
mix manual stage execution with a supervised ledger without independent reconciliation.
At 51/51, only SQL preparation is complete. Restricted native/runtime/stock-client acceptance
and any app lifecycle or feature activation remain separate, unapproved work.

## Offline checks

```powershell
python -m unittest discover -s build/azure-mcp-acceptance -p 'test_*.py' -v
```

Tests use new disposable synthetic packets and a function that intercepts **every** SQLCMD
call; they never open the operator's packet or connect to Azure. See
[stage audit and limitations](../../docs/testing/azure-mcp-supervised-acceptance-review.md).
