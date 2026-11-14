# Local SQL acceptance: fast-track or strict supervision

This tool prepares and supervises the **remaining 33 of 51 SQL stages**. It has no
Azure management, deployment, app-start, firewall or enablement commands. Production
`Shale` is excluded. Keep `shale-mcp-acceptance` stopped and all AI/MCP controls OFF.
Offline test results are not Azure SQL or runtime acceptance.

## Prepare without changing the existing checkpoint

Use an interactive Windows PowerShell 7 console (7.4 recommended), JDK 21 and the independently reviewed Microsoft **ODBC SQLCMD**.
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

## Fast-track: one run approval

For the retained **18/51 checkpoint only**, after the preparation above, run:

```powershell
.\build\azure-mcp-acceptance\Start-FastTrackAcceptance.ps1
```

Supply the exact existing rendering directory, unchanged fixture path, reviewed **absolute
ODBC 17/18 executable path**, and existing acceptance owner username when prompted. These
can instead be provided with `-RenderedDirectory`, `-AuthoritativeFixture`,
`-SqlcmdExecutable` and `-OperatorUser`. Fast-track does not use PATH to choose SQLCMD.
Run the SQL-free Windows probes below with that same executable first.

Before any SQL, fast-track checks **all** remaining prerequisites, client family, hashes,
source/target fences, existing secret inputs and the mandatory **pre-Stage-16 JVM/timezone
evidence**. It cannot manufacture evidence or bypass missing binding approvals. If the
retained checkpoint still lacks this evidence, the run stops offline before seed. Complete
only separately authorized evidence collection first; keep the application stopped afterward.
No replacement password, BCrypt value, private input or fixture is generated.

Review the displayed target/client and type **FAST_TRACK_SHALE_MCP_ACCEPTANCE** once to
approve all remaining SQL transitions using the retained independent 18-stage evidence.
Hashes are checked automatically; no hash retyping, per-stage phrases or pre/post snapshot
connections are required. The destination is hard-coded to **shale.database.windows.net /
ShaleMcpAcceptance**, with the existing database/server/edition/collation/owner/OFF guards
repeated in SQL. The first connection also refuses nonempty seed tables or missing foundation
identities before Stage 15c. Completed stages 00–15b are never sent to SQLCMD.

The 14 consecutive owner stages share one fail-fast connection; the two restricted catalogs
and 17 identity checks each retain their exact separate login. Expect **20 native password
prompts** for a full remaining run, using the existing credentials. These are authentication,
not repeated approvals. Wait for **PASSWORD INPUT READY** on every connection. Passwords
stay in SQLCMD's inherited console input; they are never passed through argv/environment,
read from the credential store, or captured by PowerShell. The reviewed ODBC executable must
support this hidden-input path; an unconfirmed prompt cannot establish a successful group.

Original rendered SQL is reused in order with public begin/done controls. Existing SQL
transactions, assertions, timeout budgets, TLS validation and SQLCMD security switches remain.
Fast-track strengthens `-V` to **11** so even severity 11–15 SQL errors stop SQLCMD; strict
mode retains its existing flags. Do not use a packet/checkout path containing commas; ODBC's
multiple-input argument cannot safely represent those paths. Excessively long paths fail offline.

Progress and a concise `report.json` appear under a new private `fast-track-<id>` directory.
The report lists completed stages, failing stage, native exit/SQL error numbers, the attempted
group and next action. Raw SQL output, messages, credentials, password hashes and private
rows are discarded. The packet's hash-chained `fast-track.jsonl` and shared lock prevent replay
and concurrent runners. Keep these files and the independent original checkpoint evidence.
Relaunch can advance only after fully verified groups; it never replays a completed stage.

**Any failed, interrupted or uncertain group blocks relaunch.** SQL errors stop the native
batch immediately. Output validation occurs when that connection returns: if capture or
parsing fails, additional stages in the attempted group may already have committed. Independently
reconcile the **entire attempted group**, surviving clients/sessions and transactional effects;
matching counts or a zero exit alone do not prove a commit. There is no automatic retry,
automatic ledger repair or fast-track recovery override. Do not delete/edit a journal or switch
modes to bypass uncertainty. Existing strict-mode progress beyond its baseline blocks adoption;
strict mode likewise refuses a packet containing fast-track history. Use independently reviewed
recovery work before further execution.

At 51/51 only SQL preparation is complete. Application startup, MCP activation, firewall
changes and runtime acceptance remain separate. Fast-track contains no such operations.

## Strict mode: supervise the remainder from one terminal

**Future operator action only:** run this only after separately approving continuation,
reviewing this change, the new Stage 15c hash, and the read-only reconciliation hash.
Nothing in this PR authorizes the current failed Stage 15c to run.

```powershell
.\build\azure-mcp-acceptance\Start-SupervisedAcceptance.ps1
```

It prompts for the exact rendering, original fixture path and existing acceptance owner.
It shows client/stage hashes and the fixed target, asks for independent hashes and exact
approval phrases, then executes one approved stage through `Invoke-Stage.ps1`. No SQL is
printed. Each SQLCMD connection reads its principal's password through the inherited native console.
The wrapper prints PASSWORD INPUT READY only after observing the native English `Password:`
prompt in captured output while the child is running. Until then, do not type a password.
Native stdout/stderr are drained concurrently in memory, including prompts without a newline. Do not pipe stdin, use ISE/editor/remote hosts, supply
`-P`, or set SQLCMDPASSWORD; unsupported/redirected-input hosts are refused.
The initial read-only approval covers the identical reconciliation script for this session;
each stage, each contained-principal boundary and continuation require separate approval.
Only the stage identifier, native exit code (or UNKNOWN), bounded numeric SQL error identifiers
and a closed failure category are shown on failure. SQL error messages/bodies and raw output
are never logged. A missing final ledger newline is treated as an interruption, not repaired.
Any different answer stops. `CONTINUE` only permits the next preview.

First launch imports 18 completed stages only after retained independent evidence is
confirmed and a fresh guarded owner connection verifies five contained foundation users,
OFF controls and zero tenant/user/Case counts. It never executes stages 00–15b. Every
stage has a fresh pre/post read-only catalog/count snapshot, exact source-completion
markers and required status/named zero-finding results. Nonzero exit/finding, missing or
ambiguous output, drift or interruption stops immediately. Raw output is discarded.

The private packet's `supervised.jsonl` is append-only, hash chained, flushed to disk and
protected against concurrent runner sessions with `supervised.lock`. It contains only
stage/hash, safe outcome/count summary, exit code, numeric SQL error identifiers, closed
failure category, UTC time and metadata/count snapshot. A failed postcheck also records its
separate verification exit code: execution exit 0 never establishes commit success.
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

Tests use new disposable synthetic packets and intercept the native SQLCMD process seam;
separate native-process tests run only local pwsh fixtures. No test starts SQLCMD; they never open the operator's packet or connect to Azure. See
[stage audit and limitations](../../docs/testing/azure-mcp-supervised-acceptance-review.md).

## Required local Windows review before merge

This Linux review cannot certify Windows console/ODBC hidden input or Ctrl+C cleanup.
The strict installer review identified these **SQL-free** checks, which must pass in the actual Windows terminal
with the intended PowerShell and independently reviewed ODBC executable:

```powershell
.\build\azure-mcp-acceptance\Test-WindowsConsoleCapture.ps1
.\build\azure-mcp-acceptance\Test-WindowsOdbcPrompt.ps1 -SqlcmdExecutable (Read-Host 'Reviewed ODBC SQLCMD .exe path')
```

Both ask for the **public word OFFLINE**, never a real password or existing credential.
Wait for PASSWORD INPUT READY before typing. If it never appears, press Ctrl+C and
record the check as inconclusive; never type a credential at the returned PowerShell prompt.
The first launches only a local pwsh fixture. The second owns an ephemeral loopback TCP
listener which speaks no TDS and never authenticates: no SQL server, query, Azure endpoint
or private packet is used. An ODBC login/prelogin failure is expected; a native password
prompt must be observed in captured output, the input must remain invisible, and the
operator must explicitly confirm that observation. Only version/hash/pass/exit metadata
is printed; raw ODBC errors and entered characters are discarded.

Also run the console fixture again and press Ctrl+C while it awaits input; confirm the
terminal returns and the fixture child exits. The transport attempts child-tree cleanup
in `finally`. Force-kill, power loss or failed cleanup cannot guarantee client termination
or rollback; independently exclude surviving SQLCMD/SQL sessions before classifying a
pending outcome. Never use Stage 15c or production as a prompt/cancellation test.
These checks do not waive later separately authorized native SQL acceptance or the Stage
16 worker/JVM/timezone gate. No live SQL execution is authorized by this guide/review.

### Windows 11 / PowerShell 7.7.0 follow-up

The operator-reported console fixture PASS is accepted. The supplied executable hash
`D9DBD1A8BD26213747B246DEBA1E13663A7E1DEF530CD961D7582B41A5E27852`
exactly matches Microsoft's published **Go SQLCMD v1.10.0 Windows amd64** binary. Its
installation path does not identify the client family. Select the independently reviewed
Microsoft **ODBC 17/18 SQLCMD** executable for the second probe and the installer.
Do not adapt flags, pass a password through argv/environment, or use a credential fallback
just to make Go SQLCMD work. The corrected probe rejects this known Go hash before any
process/listener starts; other binaries must pass a bounded help-only identity check first.

The probe now prints only client family/numeric version/hash and closed diagnostic fields:
process started/exited, native exit or UNKNOWN, timeout, prompt observed/input-ready,
loopback contact, elapsed milliseconds, output character counts, OS error number and
transport/harness category. It never prints help text, raw errors, SQL or entered input.

| Status | Meaning / next action |
| --- | --- |
| `WRONG_CLIENT_GO_SQLCMD` / `ODBC_CLIENT_NOT_CONFIRMED` | Select the actual reviewed ODBC client. No login probe/listener ran. |
| `HELP_COMPLETED` | Expected help-only exit; no password prompt or server connection is expected in this phase. |
| `MISSING_PROMPT_PROCESS_EXIT` | No captured prompt before exit. `loopbackAccepted=False` means the owned listener was not reached; True shows contact without a completed prompt path. Neither is proof of ODBC hidden-input compatibility. |
| `TIMEOUT_BEFORE_PROMPT` / `TIMEOUT_AFTER_PROMPT` | The probe watchdog expired. Child cleanup is attempted; no automatic retry. |
| `PROCESS_INTERRUPTED` | Native interrupt exit or cancellation; not an expected prelogin test result. |
| `PROCESS_START_FAILURE` / `TRANSPORT_FAILURE` | Inspect the safe transport/OS code; no raw exception is needed. |
| `HARNESS_FAILURE` | Listener start, accept, transport-call or cleanup failed; this is a test-harness failure, not a client compatibility verdict. |
| `PROCESS_EXIT_BEFORE_LOOPBACK` | Prompt path occurred, but the client exited before contacting the owned listener. |
| `PROMPT_OBSERVED_AFTER_PROCESS_EXIT` / `UNEXPECTED_SUCCESS` | Inconclusive or inconsistent; never type at the returned PowerShell prompt or treat this as PASS. |
| `EXPECTED_PRELOGIN_FAILURE` | Prompt/input-ready observed, loopback reached and native exit nonzero; explicit hidden-input confirmation is still required before PASS. |

The listener deliberately sends **no TDS/TLS/login response** and cannot authenticate.
It is not a SQL server emulator: a client that needs a prelogin exchange before prompting
cannot be certified by this test. Do not add a fake login acknowledgement or weaken TLS to
force a prompt. Keep the PR blocked if the genuine ODBC probe remains inconclusive.
Help has a five-second watchdog; the login probe has a 45-second watchdog and a local-only
five-second login timeout. These probe limits do not change installer SQLCMD budgets or
retry/recovery policy. No real credentials, private packet or Azure endpoint is involved.

Before any later separately authorized installer run, ensure `Get-Command sqlcmd -CommandType
Application` resolves the same approved ODBC executable/hash used by the probe. A successful
explicit-path probe does not change PATH or replace the existing Go installation.

### Fast-track Windows limitation

A full simulated fast-track run does not certify ODBC's multiple `-i file1,file2` execution,
space-containing Windows paths, hidden password input, or cancellation on the operator's
Windows/driver version. The probes above cover the console path only. Native SQL execution
was not performed for this change. Do not use acceptance Stage 15c as a compatibility probe.

Reconciliation now emits one bounded `PRINT` record rather than a variable-width result
column; its reader accepts SQLCMD BOM, surrounding spaces and CRLF, canonicalizes the digest,
and still rejects missing, duplicate, malformed, truncated or overflowing records. This fixes
those output-shape failures without accepting arbitrary substrings. The reported live output
was unavailable, so the exact historical parsing failure and native correction remain unverified.
