# Issue 1969: remaining-stage review

Reviewed against `codex/latest` `b3ab7c0c` (#1968). No live SQL, Azure changes,
private operator inputs, BCrypt values, DPAPI store or acceptance evidence was accessed.
The reported 18/51 checkpoint is retained, not rediscovered by executing prior stages.
Operator commands: [supervised installer guide](../../build/azure-mcp-acceptance/README.md).

## Remaining stage audit

The manifest still contains the same ordered 51 names and sources, including 15a/15b/15c.
All remaining sources and their include closure were reviewed for session requirements,
version ordering, output behavior, roles/permissions, secret/approval inputs and identities.
No historical installer, receipt, module, source fingerprint, role, transaction or guard changed.

| Stage(s) | Finding and disposition |
| --- | --- |
| 15c-seed | Demonstrable Msg 1934 cause: new ODBC SQLCMD connection defaults QUOTED_IDENTIFIER OFF. Seed DML reaches indexed/filtered/computed paths. Render all seven SETs plus GO before unchanged seed source, within approved bytes. Seed still requires empty tables and four independently supplied BCrypt values; transaction/rollback/count checks unchanged. Require new hash approval and independent rollback evidence before any later execution. |
| 16/17/18 binding | Expanded includes execute V2 XML catalog verification on fresh connections. Same complete SET preamble now precedes each source. Unchanged input/target/28-instant validator and immutable single-insert transaction remain authoritative. Missing worker JVM/timezone evidence is a hard STOP before 16; no placeholder zone or app startup. 17 still has separate execution approval. Require exact BindingStatus and zero ConfinementCatalogDriftFindingCount. |
| 19 PR3 preparation | Authority inventory precedes the mutating supplement. XML exists in the catalog chain/dynamic module paths; full session preamble required. This is NOT a read-only stage. Existing binding, neutral dbo, OFF capability and predecessor receipts are prerequisites. Confinement findings must be zero; inventory counts are informational. |
| 20 PR3 | Preserve install → application verify → confinement verify. XML/session preamble before all three unchanged sources. Required named application, confinement and registry finding counts must be present and zero. No catalog re-sealing as recovery. |
| 21 proof / 22 OAuth | XML receipt/catalog paths require the same session setup. Their own assertions throw on drift; source-completion records prove every source returned through its final target/OFF fence. Historical proof/OAuth versions are checked here, before successor installs. |
| 23 reviewer / 24 onboarding | Create exact metadata/accessor and original-WEB-session role contracts after PR3/proof/OAuth. No role fallback or broad grants added. Session preamble covers DDL/indexed paths consistently; existing source assertions remain authoritative. |
| 25 C1 / 26 C2 | C1 XML and successor catalog/module paths need complete session state. Preserve successor order and historical receipt comparisons. C2 creates AiDcrEndpointV3 after C1; do not substitute AiDcrRegistrarV2. Final verification uses DCR_V3 resources, not frozen predecessor checks against successor metadata. |
| 28 contained users | Twelve role-specific contained identities only after roles exist. Require private provisioning approval, 12 unique sufficiently long SQL literals, and independence from the five retained foundation secrets. SQL still rejects existing users/missing roles; no adoption, regrant, server login or secret generation. |
| 30 final owner | Stage-0 platform inventory, DCR_V3 registry/authorization catalogs, then OFF/synthetic/17-user final assertions. XML preamble before each source. Seven specific platform finding counts plus registry finding count must be zero; final preparation status is required. |
| 31 verifier / 32 reviewer | XML catalog batches run under exact independent mcpacc_verifier / mcpacc_reviewer logins, with the unchanged five-second query cap. The reviewer retains its receipt-accessor EXECUTE contract. Required Case/registry findings in 31 must be zero; 32 succeeds only through unchanged catalog assertions and its completion record. No dbo fallback. |
| 33–49 identity checks (17 stages) | Authenticate separately as each exact contained principal. Preserve own-user/public/sole-role token, contained-authentication/SID and permission-denial checks. Require exact identity status and completion. These are authentication/token checks, not full enabled runtime acceptance. |

All remaining rendered sources receive the same seven SET/GO preamble, avoiding reliance on
client defaults or a previous process. Source-completion records are added after each original
final guard. All previously completed rendered stages are unchanged. Offline golden hashes for
all 18 default/synthetic stages were generated with the #1968 renderer and are regression tested;
these are not hashes of operator secrets, real fixture exports or actual accepted private packets.
The operator must independently compare their completed rendered hashes during continuation.

## Supervision and recovery architecture

`Test-Readiness.ps1` is read-only/offline. `Start-SupervisedAcceptance.ps1` owns a foreground
prompt/state machine and calls the existing `Invoke-Stage.ps1`, which retains fixed destination,
SQL authentication, TLS validation, SHA-256 approval, `-x -X1 -b -V 16`, login/query timeouts and
independent-principal checks. Supervised output adds only formatting flags for strict parsing.
No generic SQL path/target/argument escape hatch or credential API is introduced.

Each source must emit its exact ordered completion record; required statuses and named finding
results are checked. Every observed finding column must contain numeric zero. Raw stdout/stderr
is captured in memory and never persisted or printed; only allowlisted result/exit summaries are
recorded. Failure/timeout/incomplete results leave a pending/failed stage, with no automatic retry.
The existing manual wrapper's default preview and explicit execution behavior remain supported.

The separate hash-approved reconciliation script checks target/owner/OFF fences, five contained
foundation identities, object/module/receipt/principal/permission/RLS/index/column metadata and
binding tuple digests, plus tenant/user/Case counts. It does not emit definitions, credentials,
password hashes or row contents. Fresh connections bracket every stage and reconcile resume.
The ledger is append-only and hash chained; an exclusive local lock prevents concurrent runners.
It is not a signed audit trail or a database concurrency lock. Exclude other schema/control/data
maintenance and keep the app stopped throughout the approved window.

Unknown commit outcome requires independent stage-specific read-only verification, explicit
committed/rolled-back classification and exact live snapshot agreement. A rollback must match
pre-execution state; seed disposition also checks the expected counts. Recovery only records the
decision and exits. This human review is intentional: a generic metadata digest cannot prove a
transaction committed correctly or rolled back all data effects. Partial/unknown remains blocked.

Audit compatibility: this local operational workflow adds no application/API mutation or PHI read.
Counts and catalog reads are operational verification; existing governed seed/identity/binding/DDL
behavior is unchanged. Operator approval/stage records stay private, outside application tables.
No new ad-hoc tenant/PHI audit row, audit schema or audit allowlist is introduced. Binding audit
vocabulary remains the separate architecture gap documented in the existing binding readiness
packet. Existing source-owned transactional audits remain authoritative.

## Remaining risks / unverified behavior

- No Azure SQL compilation/execution or Windows ODBC interactive-password integration was run.
  Native errors/driver prompt differences, permission visibility, S0 duration/lock limits and the
  historical restricted catalog failure remain possible. They cause STOP; do not increase timeouts,
  grant broader permissions, disable TLS checks or switch SQLCMD variants as an automatic repair.
- Offline shape/source checks cannot prove live roles, credentials, fixture equivalence, effective
  permissions, worker configuration, all data contents or true independent approval. Keep retained
  independent 18-stage evidence and review each exact hash. Never approve an unknown transaction
  solely because counts or a digest match.
- Worker JVM/timezone evidence is still absent in the supplied checkpoint. The installer cannot
  cross Stage 16 until separately authorized evidence has been reviewed; it cannot produce that
  evidence while the app remains stopped. No such authorization is inferred here.
- SQL preparation is not complete native/runtime/stock-client acceptance or activation approval.
  Shared B1/server resource risks and existing read-only/owner capacity windows remain unchanged.

## Test impact and local verification

Before editing, reviewed `test_packet.py` and `test_phase1r_evidence.py` plus repository-wide
references to Invoke-Stage, Prepare-Packet, 15c-seed and the previous three-stage SET contract.
Renderer changes update the existing session-scope assertions; wrapper argument/hash/identity
refusal tests remain in force. `test_supervised.py` covers readiness blockers, all 18 completed
synthetic hashes, all 33 remaining supervised transitions, exact contained users, approvals,
wrong target/hash, nonzero findings, malformed results, process failure/timeout, interrupted
commit/rollback recovery, no replay/retry, secret suppression, ledger corruption/tampering and
linked paths. The reconciliation SQL remains unexecuted; its native semantics remain unverified.

Verification commands (workspace-local PowerShell 7.4/JDK 21/Maven 3.9.11; session proxy/CA for Maven):

```text
python -m unittest discover -s build/azure-mcp-acceptance -p 'test_*.py' -v
python build/test-selection/select_tests.py --base b3ab7c0c --head HEAD --format json
mvn test
git diff --check
```

The SQL packet documentation selects the existing security-data Maven area and the binding/external
Python checks. Acceptance-installer build paths also produce an informational full-suite suggestion;
this is not an additional completion gate. Direct offline acceptance tests cover the changed executable contracts; the
critical reactor protects existing application/default-OFF behavior. No application production
code, historical SQL installer, module resource, manifest order or receipt was modified.

Initial implementation results: **55 offline acceptance tests PASS**; **160 selector-affected Maven tests PASS**; the two selector Python checks pass **11 tests**;
**283 critical-reactor tests PASS**, zero failures,
errors or skips. PowerShell AST parsing and Git whitespace checks PASS. The initial Maven
download failure was resolved using the session proxy and existing CA trust, without repository
build changes. No native SQL or Windows ODBC interactive integration result is claimed.


## Final pre-merge review of PR #1970

**Verdict: keep draft until local Windows console/ODBC checks pass.** This review runs on
Linux; it cannot certify Windows hidden input or cancellation behavior. The guide provides
`Test-WindowsConsoleCapture.ps1` (pwsh fixture) and `Test-WindowsOdbcPrompt.ps1` (the actual
reviewed ODBC client against an owned, inert loopback listener). Neither uses Azure, a SQL
server/query, real credentials, the private acceptance packet or Stage 15c. Record terminal,
PowerShell version, approved client hash and observed hidden-input/Ctrl+C behavior locally.

Findings corrected in the same PR:

- PowerShell pipeline capture could hide the native prompt and native error preferences could
  throw before collecting LASTEXITCODE. A .NET Process transport now pins the application,
  inherits console stdin, drains stdout/stderr concurrently and obtains Process.ExitCode directly.
  No password is handled by PowerShell, placed in argv/environment or saved to a file. Unsupported
  hosts/redirected stdin are refused. The replacement prompt appears only after an actual English
  Password: prefix is observed while the child runs; no premature password request on startup
  failure. Unconfirmed prompts cannot produce a successful supervised stage.
- Safe diagnostics retain the known stage ID, native exit code or UNKNOWN, at most 32 distinct
  integer SQL error numbers from standard Msg/Level/State headers, and a closed failure category.
  Messages, SQL, bodies, principals mentioned in errors, hashes and private result values are not
  copied. A failed postcheck records execution exit and verification exit separately and leaves
  the stage unresolved. Read-only failures before any stage are printed as safe diagnostics;
  they never create a false completed-stage record.
- A missing final ledger newline is now treated as interruption/corruption before any connection.
  Multiple numeric rows under a single finding-count header fail closed. No failure/timeout,
  interrupted client, zero-exit SQL error, malformed output or uncertain commit can advance/replay.
- HashData/ToHexString/OperatingSystem.IsWindows were newer than the declared PowerShell 7.0
  baseline. Compatible SHA256.ComputeHash and RuntimeInformation APIs now preserve identical
  existing ledger digests. Existing rows without new diagnostic fields remain readable.

The renderer, manifest, seed source, reconciliation SQL, destination/owner guards, SQLCMD security
flags, query/login budgets and Stage 16 evidence gate are unchanged by this review. Tests retain
all 18 completed synthetic-stage hashes and the existing 15c seven-SET correction. Existing private
inputs, actual completed-stage evidence, credentials and DPAPI storage were not read or modified.

Test impact: existing packet/approval/identity/SQLCMD tests and the supervised state-machine tests
were reviewed before changes. New cases cover safe errors with exit 16 or exit 0, postcheck failure,
old-ledger compatibility, missing newline, unconfirmed prompts and duplicate findings. Native-process
checks launch only local pwsh fixtures: inherited stdin, literal argv, concurrent full pipes, startup
failure, bounded safe parsing and a real no-newline prompt/hidden input under a Linux pseudo-terminal.
The SQLCMD process seam remains mocked in installation simulations, including all 33 transitions.

Cancellation cleanup is best effort in finally; force-kill, power loss or cleanup failure can leave
an uncertain client/server outcome. The pending ledger is authoritative for STOP, never rollback.
Exclude surviving clients/sessions and perform independently approved read-only verification before
recording a recovery decision. Neither an exit code nor matching counts alone establishes a commit.
No native Azure SQL execution, Stage 15c retry, app startup/enablement, production change or merge ran.

Final review validation: **68 offline acceptance tests PASS** and **283 critical-reactor tests
PASS**, zero failures/errors/skips. The strengthened pseudo-terminal echo/timing check also
passed directly after its assertion update. PowerShell AST parsing and Git whitespace checks
PASS. The selector run with `--base 48fa619d --head HEAD` selects no Maven feature area and
requires the critical reactor; its unknown-build-path full-suite recommendation is informational.
SQL opt-ins were removed from the Maven environment. The two Windows-specific manual probes
and Windows Ctrl+C observation remain **NOT RUN**, and are the stated merge blocker.


## Reported Windows probe failure: client identity investigation

Operator evidence: Windows 11 / PowerShell 7.7.0 console fixture PASS, synthetic exit 17,
SQL error 50001. The second probe did not show PASSWORD INPUT READY and returned its old
generic inconclusive result. The supplied executable hash exactly matches `sqlcmd.exe`
inside Microsoft's [Go SQLCMD v1.10.0 Windows amd64 release asset](https://github.com/microsoft/go-sqlcmd/releases/download/v1.10.0/sqlcmd-windows-amd64.zip):
`D9DBD1A8BD26213747B246DEBA1E13663A7E1DEF530CD961D7582B41A5E27852`.
The public asset was downloaded and hashed in scratch storage; no user executable/private
file was read, modified or executed. This is a byte-identical Go client identification,
not an inference from `C:\Program Files\SqlCmd` or the operator's ODBC label.

The [v1.10.0 connection implementation](https://github.com/microsoft/go-sqlcmd/blob/v1.10.0/pkg/sqlcmd/sqlcmd.go)
calls `promptPassword` before `sql.OpenDB(...).Conn(...)`. Its console uses
[liner v1.2.2 PasswordPrompt](https://github.com/peterh/liner/blob/v1.2.2/line.go), which
rejects redirected output (`ErrNotTerminalOutput`) before printing the prompt; unsupported
terminal/width checks can also reject it. This explains why this Go binary can exit with
no captured prompt before any TCP connection. The old probe did not retain exit/contact
metadata, so no exact historical error message or native exit code is invented. Its result
does not demonstrate that an ODBC client failed because of the inert listener.

Corrections:

- Reject the known Go hash before approval/process/listener; identify other clients using
  `-X1 -?` only, with a five-second watchdog, closed family/numeric-version output and no
  raw help output. Go, unknown clients and unreviewed tool major versions are refused before
  login. No Go compatibility mode, auth fallback or weakened SQLCMD/TLS flag is introduced.
- Record actual process start/exit, OS error number, timeout, prompt/input-ready state,
  elapsed time and output lengths. Distinguish start failure, capture failure, interruption,
  watchdog expiry and output overflow without exception text. The internal watchdog defaults
  to disabled for installer calls, so all existing acceptance budgets remain unchanged.
- The owned loopback listener remains non-TDS and unauthenticating. Log whether it was reached;
  distinguish missing prompt/process exit, before/after-prompt timeout, transport failure and
  harness start/accept/cleanup failures. Require prompt, input-ready, loopback contact, nonzero
  exit and explicit hidden-input confirmation. Neither generic failure nor mere TCP contact
  establishes a password test PASS. A deferred-prompt client cannot be certified by an inert
  listener; do not fabricate a login exchange to make the test pass.

Tests cover public-release hash identification, ODBC/Go/unknown help banners, a diagnostic
matrix, real native timeout/start/pipe/prompt behavior, and the actual probe orchestration
with only the platform gate and process seam substituted in an isolated test host. The
orchestration tests bind their own loopback listener, send zero TDS bytes, assert help once /
login at most once, and verify wrong clients never reach login. They do not start SQLCMD,
connect to a SQL server, access real credentials or use the acceptance packet.

Audit compatibility is unchanged: these are local operational metadata/probe records, no
PHI/domain mutation, application audit row, schema, permission or production change. Renderer,
manifest, seed source, reconciliation SQL, Stage 16 gate, SHA approvals and SQLCMD security
controls remain intact. A genuine ODBC Windows probe still needs local verification; PR #1970
remains draft. No acceptance stage, Azure SQL or production operation ran in this investigation.

Latest probe correction verification: **78 offline acceptance tests PASS**, **283 critical-reactor
tests PASS** (zero failures/errors/skips), PowerShell AST parsing and Git whitespace PASS. Selector
`--base 6a5426b1 --head HEAD` selects no Maven feature area and requires the critical reactor; its
full-suite suggestion remains informational. SQL opt-ins were removed. The operator's Windows
console PASS is retained as reported evidence; a genuine ODBC 17/18 Windows probe is still pending.
