# Windows operator portability follow-up — 2026-11-02

**Post-merge status — 2026-11-04:** #1929 merged at
`1f12091aef3275721c1e35d6ba00a2ecb80c5173`. The
[final diagnostic closeout](native-azure-diagnostic-closeout.md) stops further Azure diagnosis;
earlier retry, pending-validation and merge-hold instructions below are historical.
Native acceptance remains OPEN. See the [ordinary dormant release gate](../dormant-server-release-compatibility.md);
no SQL, Azure, deployment or activation is authorized by this status correction.

PR #1929 merged into codex/latest on 2026-11-04 (historical draft status superseded). **Owner validation of the new launcher/RNG fixes is
pending; both native Azure gates remain OPEN.** After owner validation, the next action is
a fresh disposable Azure SQL retry with a new nonce. This task accessed neither Azure nor
production and created no cloud resource.

## Owner evidence retained

The owner reports real Windows non-Azure validation complete: pinned refresh 25/25,
`PINNED_INDEX clean=true`, V1 and V2 receipt PASS, focused Azure/V1/V2 contracts,
Phase1R local SQL, critical Maven reactor, package verification, and the selector-generated
affected Maven batch all passed. The owner's plan reported no full-suite escalation.
The affected batch succeeded when invoked as `& mvn.cmd @($Plan.selected_maven_args)`;
`select_tests.py --run` itself failed before Maven started. These observations supersede
the earlier Windows/stat-cache and Maven-environment blockers, without claiming live Azure
acceptance or rerunning the owner's local SQL gates in this task.

## Changes and test impact

Reviewed `AGENTS.md`, `architecture/codex-prompt-rules.md`, the Azure attestation design,
the acceptance/operator packet, the change-aware selection guide, both structured command
runners and all existing selector tests before editing. Repository searches found the
incompatible static RNG overload only in the native operator packet; the existing local
recovery script already uses an instance RNG.

| Contract | Change and validation |
| --- | --- |
| Selector launcher | Reuse `run_selection.resolve_maven`: Windows prefers `mvn.cmd`, other platforms prefer `mvn`, with the existing fallback. Affected and critical execution retain original argument arrays and `shell=False`. Missing executables fail with exit code 2 and an actionable diagnostic. |
| Command length | Existing generated batches and 7,000-character limit are unchanged. Windows execution additionally checks the serialized argv including resolved path/quoting before starting any plan commands. |
| Launcher regressions | Existing structured-argument test updated; platform preference/fallback, missing executable, Windows path with spaces, quoted-length rejection, and real PATH launcher/argument recording covered. The real recorder uses `mvn.cmd` on Windows and `mvn` on POSIX. |
| Runbook credentials | Exact instance `Create()` / `GetBytes(byte[])` API; independent 48-byte draws for all five identities. Per-identity `finally` clears byte buffers and secret references, RNG disposal occurs in `finally`, and generation failure removes all partial credential pairs. Owner environment/opt-in removal also occurs in `finally`. |
| Offline PowerShell check | `build/azure-native/test_runbook_credentials.ps1` parses all PowerShell fences and executes the exact marked credential block twice. It checks shape, separation across identities/runs, absence of output, released local references, and partial-failure environment cleanup. It refuses inherited Azure environment values, clears all test credentials, and never invokes Azure/JDBC. |

The selector's mapping, policy, test manifest and batch-generation functions are unchanged.
An AST comparison confirms only `run_commands` and `main` changed. The infrastructure edits
trigger the existing informational full-suite escalation; this is distinct from the owner's
previous affected plan, which reported none. No mapping exception was added for the launcher bug.

No confinement SQL, receipt, authoritative candidate, Phase1R implementation, datasource
boundary, timeout, production configuration or native gate semantics changed. Audit review:
launcher execution and offline process-memory credential generation add no application data
access or business mutation; existing audit integration/schema remain unchanged.

## Focused task validation

Commands run from the repository root with JDK 21 and Maven 3.9.11. Logs and temporary tooling
remain private under `work/`; they are not committed. Counts overlap and are not additive.

| Command / check | Result |
| --- | --- |
| `python3 -m unittest discover -s build/test-selection -p 'test_*.py'` | PASS 31/31, zero skips. |
| New Windows resolution/length regressions against the previous selector implementation | Both fail as expected before the fix; pass with the fix. |
| `pwsh -NoProfile -File build/azure-native/test_runbook_credentials.ps1` on Linux PowerShell 7.4.13 / .NET 8 | PASS: two rounds, five identities, partial-generation cleanup, no secret output or persistence. Workspace XDG directories were used because the host's default PowerShell cache/data directories are read-only. |
| `python3 build/sql-confinement/checkout_bytes.py` | PASS 25/25, clean index, original V1/V2 receipts. |
| `python3 -m unittest discover -s build/sql-confinement -p test_checkout.py` | PASS 11/11. |
| `mvn -B -ntp -pl shale-data -am -Dtest=AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | BUILD SUCCESS: 18 tests, zero failures/errors/skips. |
| `MAVEN_ARGS='-B -ntp' python3 build/test-selection/select_tests.py --base e129f2733bf7175425299a12ae97efdae3f23d22 --head HEAD --plan-output work/pr-plan.json --run` (before this follow-up commit) | Affected batch BUILD SUCCESS: 148 tests, 146 passed, two native Azure opt-in skips; zero failures/errors. The supplemental legacy `AiSecurityLocalSqlServerTest` reports zero executed tests without its opt-in; no SQL acceptance inferred. Critical result recorded below. |
| Critical `mvn test` executed by the same corrected `--run` | BUILD SUCCESS across all seven reactor modules: 228 tests, zero failures/errors/skips. |
| Incremental portability plan (all follow-up paths), diff checks, protected-file comparison, documentation fences/relative links | PASS. Existing infrastructure escalation remains informational; the historical `-Pall-tests` profile was not run. |

## Windows owner check before the new Azure attempt

In a fresh credential-free Windows PowerShell process, from the reviewed checkout, run:

```powershell
powershell.exe -NoProfile -File build/azure-native/test_runbook_credentials.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline credential check failed' }
python -m unittest discover -s build/test-selection -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Selector launcher regression failed' }
python build/test-selection/select_tests.py --base e129f2733bf7175425299a12ae97efdae3f23d22 --head HEAD --run
if ($LASTEXITCODE -ne 0) { throw 'Affected/critical selector execution failed' }
```

Use `pwsh` instead of `powershell.exe` if that is the actual operator host. The exact-block check
must pass on that host: Linux PowerShell and simulated Windows launcher tests do not establish
real Windows execution. No new Windows or live Azure result is claimed here.

The runbook's instance API exists in Windows PowerShell 5.1/.NET Framework 4.8; it does not
require the unsupported static `GetBytes(int)` overload. Generated strings/environment/JDBC
copies cannot be reliably zeroed in managed memory: keep the session private, follow the
runbook's process-environment cleanup, then close the session. Credentials never become
printed output, files or command-line values. Proceed to the runbook's fresh nonce-owned
disposable Azure workflow only after owner validation; do not reuse the failed fixture.
