# Phase 13B installed-Windows feasibility checklist

**Current status (2026-09-30):** installed-Windows execution remains outstanding. The validation runner available
for this pass is Linux (`Linux 6.18.44 x86_64`); it exposes neither `powershell.exe` nor `cmd.exe`, no installed
Shale workstation, `%ProgramData%`, Windows accounts, Task Scheduler, session lock, UAC, MSI, or Windows ACLs.
Consequently this report does not turn source inspection or deterministic unit contracts into installed-Windows
evidence. Phase 13B remains `IN PROGRESS` and production activation remains off.

**Full repository `mvn test`: PASS.** This result was supplied after successful execution outside the previously
restricted runner. Automated Java/Maven verification is therefore PASS and is no longer a roadmap blocker. The
Linux documentation runner did not independently reproduce it; that limitation does not downgrade the supplied
result and must not be confused with the still-missing installed-Windows evidence.

Use a per-user MSI installation owned by the Windows user running Shale. Never provide a Windows password, Shale
password, JWT/JTI, or session secret to a task/helper.

1. Inspect Task Scheduler and prove no Phase 13B task was silently registered by install, upgrade, startup, or preference change.
2. With preference disabled, invoke the evaluation harness and confirm `DEFER_PREFERENCE` and no Phase 12 file.
3. Enable the preference through Phase 13A; confirm the evaluator rereads the provider (do not edit its file).
4. With no newer exact Production target package, confirm `DEFER_NO_UPDATE` or `DEFER_UNAVAILABLE` and no attempt.
5. With foreground activity less than 30 minutes ago, confirm `DEFER_ACTIVE_USER`.
6. With any active safe-work lease, save/mutation in flight, dirty/uncertain editor, or visible foreground window, confirm deferral.
7. At 02:00, inside the window, and immediately before 04:00 local time, exercise eligibility; at 04:00 confirm deferral.
8. Repeat across Windows DST spring-forward/fall-back and after a timezone change; confirm local calendar evaluation and bounded counters.
9. Lock the session and confirm lock alone never overrides activity, work, shutdown, policy, package, or lock checks.
10. Do not test logged-out execution as supported: it is explicitly unsupported by this foundation.
11. Disconnect networking; confirm authority/package unavailability defers without a Phase 12 attempt and retries no more than four times at 30-minute spacing.
12. Confirm a real terminal updater failure permits no second handoff in the same local window; a new local date/time-zone window resets counters.
13. Before activation, add and validate one shared cross-process lock against manual versus unattended, two evaluators, and another Shale process; only one handoff may win.
14. Before activation, replace force termination with cooperative shutdown; dirty/blocked shutdown must defer without an unseen prompt.
15. At the eventual handoff seam, confirm exactly one Phase 12 attempt begins and successful startup reconciliation remains unchanged.
16. Confirm no UAC prompt and that the helper runs as the per-user install owner, never SYSTEM. Treat either failure as a blocker.
17. Confirm package SHA-256/ZIP traversal checks remain identical to manual mode; separately verify release Authenticode posture because runtime code does not validate it.
18. Confirm installer reboot-required behavior is recorded without automatically rebooting. (The current ZIP updater does not invoke MSI.)
19. Disable the Phase 13A preference and confirm every future execution-time evaluation defers even if scheduler state is stale.
20. If a task/helper is added later, upgrade twice and prove stable identity/path with no duplicate task; uninstall and prove task/helper/state cleanup.

## Validation-run checklist results — 2026-09-30

`NOT TESTED` below means **not executable on the non-Windows validation runner**, not that the acceptance
criterion has been waived. Every item that requires an installed Windows workstation remains open.

| # | Result | Evidence/limitation |
|---:|---|---|
| 1 | NOT TESTED | No Windows Task Scheduler or installed MSI is present. Source/package inspection still finds no Phase 13B registration code. |
| 2 | NOT TESTED | No installed Settings preference or Windows evaluation harness can be exercised. The pure test contract expects `DEFER_PREFERENCE` and zero handoffs. |
| 3 | NOT TESTED | No installed Settings UI, restart, Shale-user switch, or `%ProgramData%` is available. Provider reread is covered by the existing deterministic contract only. |
| 4 | NOT TESTED | No installed authenticated policy/manifest runtime is available. Pure eligibility distinguishes no update and unavailable authority. |
| 5 | NOT TESTED | No JavaFX Windows foreground-input session is available. Deterministic tests cover 1,799, 1,800, and 1,801 seconds, missing evidence, and future skew. |
| 6 | NOT TESTED | Representative installed editors could not be opened. Static coverage findings are recorded below and are not runtime evidence. |
| 7 | NOT TESTED | No installed runtime can be clock-driven here. The shared pure resolver tests define 01:59:59 closed, 02:00 open, 03:59:59 open, and 04:00 closed. |
| 8 | NOT TESTED | Windows DST/timezone behavior could not be observed. Deterministic `ZonedDateTime` contracts cover spring-forward, fall-back, and zone identity changes. |
| 9 | NOT TESTED | The host cannot lock a Windows interactive session or observe Windows foreground behavior. |
| 10 | PASS | Code and architecture consistently state that logged-out execution is unsupported; no task, helper, service, public policy endpoint, or credential storage exists. |
| 11 | NOT TESTED | No installed network/policy runtime is available. The pure design defers unavailable authority before handoff and bounds evaluations to four. |
| 12 | NOT TESTED | No real updater handoff is safe in this environment. The retry contract limits handoffs to one per local-date/zone window. |
| 13 | FAIL | Source inspection confirms that no shared OS cross-process update lock exists. Eligibility can be forced to defer through its supplied lock input, but no installed lock owner supplies that fact. This remains an activation blocker. |
| 14 | FAIL | Windows updater source still executes `taskkill /IM Shale.exe /F`; no inspect-only global readiness operation or prompt-free cooperative close exists. Unattended callers must continue to supply shutdown unavailable. This remains an activation blocker. |
| 15 | NOT TESTED | No real handoff was attempted. Existing unit boundaries place Phase 12 creation at handoff rather than eligibility, but installed reconciliation was not rerun. |
| 16 | NOT TESTED | UAC, install-owner identity, and writable installed files require Windows runtime evidence. No claim of prompt-free execution is made. |
| 17 | NOT TESTED | Installed package behavior and Authenticode posture cannot be exercised. Source inspection confirms configured SHA-256 checking, ZIP traversal rejection, and no runtime Authenticode verification. |
| 18 | NOT TESTED | The in-app source path consumes ZIP rather than MSI and contains no reboot call; Windows installer reboot-required behavior was not observable. |
| 19 | NOT TESTED | No installed preference can be changed. The provider-first pure contract still fails closed for every state other than explicit `ENABLED`. |
| 20 | NOT TESTED | Phase 13B installs no task/helper/state, so cleanup is presently inapplicable; a future activated implementation must execute this acceptance test. |

## Installed-system facts

There is no Windows installation on the validation runner. Therefore the actual installation directory, per-user
versus per-machine registration, installed updater path and its upgrade stability, owning-user write access,
updater/MSI UAC behavior, ZIP selection at runtime, concurrent desktop processes, and second-Windows-account
behavior remain **unverified**. The source/package expectation remains a per-user `jpackage` MSI, an updater at
`app/updater/ShaleUpdater.exe` with a legacy alternate lookup, and ZIP-based in-app updates; these expectations are
not substituted for installed evidence.

Likewise, neither `%ProgramData%\Shale` nor `automatic-update-preference.properties` exists on this host. Owner,
inherited ACEs, ordinary-user read/write, cross-account access, MSI directory creation/modification, and UAC are
all unverified. Source inspection confirms that the current MSI does not provision a stronger ACL; validation must
retain the distinction between machine-scoped semantics and whatever read/write permissions Windows actually grants.

## Exact installed-Windows evidence commands

Run the following in an **ordinary, non-elevated PowerShell** session while the normally installed Shale build is
running. It is discovery-only: it neither changes ACLs nor starts an update. Save the transcript and redact only
user-identifying path segments if necessary; do not redact whether a path is under `LOCALAPPDATA`, `ProgramFiles`,
or another root.

```powershell
$ErrorActionPreference = 'Continue'
$transcript = Join-Path $env:TEMP 'shale-phase13b-windows-evidence.txt'
Start-Transcript -Path $transcript -Force

"Timestamp: $(Get-Date -Format o)"
"Windows identity: $([Security.Principal.WindowsIdentity]::GetCurrent().Name)"
"Username: $env:USERNAME"
"LOCALAPPDATA: $env:LOCALAPPDATA"
"ProgramFiles: $env:ProgramFiles"
"ProgramFiles(x86): ${env:ProgramFiles(x86)}"
Get-Command Shale.exe, ShaleUpdater.exe -ErrorAction SilentlyContinue |
    Format-List Name, Source, Path, Version

$shaleProcesses = @(Get-Process -Name Shale -ErrorAction SilentlyContinue)
$shaleProcesses | Select-Object Id, ProcessName, Path, StartTime | Format-List
$shaleExe = $shaleProcesses | Select-Object -First 1 -ExpandProperty Path
if (-not $shaleExe) {
    $shaleExe = Get-ChildItem $env:LOCALAPPDATA, $env:ProgramFiles, ${env:ProgramFiles(x86)} `
        -Filter Shale.exe -File -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if ($shaleExe) {
    $installDir = Split-Path $shaleExe -Parent
    $updaterCandidates = @(
        (Join-Path $installDir 'app\updater\ShaleUpdater.exe'),
        (Join-Path $installDir 'updater\ShaleUpdater.exe')
    )
    "Shale executable: $shaleExe"
    "Install directory: $installDir"
    "Under LOCALAPPDATA: $($shaleExe.StartsWith($env:LOCALAPPDATA, [StringComparison]::OrdinalIgnoreCase))"
    "Under ProgramFiles: $($shaleExe.StartsWith($env:ProgramFiles, [StringComparison]::OrdinalIgnoreCase))"
    Get-Item $shaleExe, $installDir -ErrorAction Continue |
        Select-Object FullName, Attributes, CreationTimeUtc, LastWriteTimeUtc | Format-List
    Get-Acl $shaleExe, $installDir -ErrorAction Continue |
        Format-List Path, Owner, AreAccessRulesProtected, AccessToString
    foreach ($candidate in $updaterCandidates) {
        "Updater candidate: $candidate; Exists: $(Test-Path -LiteralPath $candidate -PathType Leaf)"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            Get-Item $candidate | Select-Object FullName, VersionInfo, LastWriteTimeUtc | Format-List
            Get-Acl $candidate | Format-List Path, Owner, AreAccessRulesProtected, AccessToString
        }
    }
    $probe = Join-Path $installDir ('.shale-write-probe-' + [Guid]::NewGuid().ToString('N'))
    try {
        [IO.File]::WriteAllText($probe, 'probe')
        'Current-user install-directory write: TRUE'
    } catch {
        "Current-user install-directory write: FALSE ($($_.Exception.GetType().Name))"
    } finally {
        Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
    }
} else {
    'Shale executable: NOT FOUND; launch the installed application and rerun.'
}

$preferenceDir = Join-Path $env:ProgramData 'Shale'
$preferenceFile = Join-Path $preferenceDir 'automatic-update-preference.properties'
"ProgramData directory exists: $(Test-Path -LiteralPath $preferenceDir -PathType Container)"
"Preference file exists: $(Test-Path -LiteralPath $preferenceFile -PathType Leaf)"
Get-Item $preferenceDir, $preferenceFile -ErrorAction Continue |
    Select-Object FullName, Attributes, CreationTimeUtc, LastWriteTimeUtc | Format-List
Get-Acl $preferenceDir, $preferenceFile -ErrorAction Continue |
    Format-List Path, Owner, AreAccessRulesProtected, AccessToString
if (Test-Path -LiteralPath $preferenceDir) { & icacls.exe $preferenceDir }
foreach ($path in @($preferenceDir, $preferenceFile)) {
    if (Test-Path -LiteralPath $path) {
        $acl = Get-Acl $path
        $me = [Security.Principal.WindowsIdentity]::GetCurrent()
        $rules = $acl.GetAccessRules($true, $true, [Security.Principal.NTAccount])
        "ACL rules for current token on ${path}:"
        $rules | Where-Object {
            $me.Groups.Translate([Security.Principal.NTAccount]).Value -contains $_.IdentityReference.Value -or
            $_.IdentityReference.Value -eq $me.Name
        } | Format-Table IdentityReference, AccessControlType, FileSystemRights, IsInherited -AutoSize
    }
}

Get-ScheduledTask -TaskPath '\Shale\' -ErrorAction SilentlyContinue |
    Select-Object TaskPath, TaskName, State | Format-Table -AutoSize
Get-AuthenticodeSignature $shaleExe -ErrorAction Continue |
    Select-Object Path, Status, StatusMessage, SignerCertificate | Format-List
foreach ($candidate in $updaterCandidates) {
    if (Test-Path -LiteralPath $candidate) {
        Get-AuthenticodeSignature $candidate |
            Select-Object Path, Status, StatusMessage, SignerCertificate | Format-List
    }
}
Stop-Transcript
"Evidence transcript: $transcript"
```

The temporary install-directory probe tests effective ordinary-user write access and removes itself; it does not
change permissions. If even that file operation is unacceptable, omit only the `$probe` block and record effective
write access as `NOT TESTED`, not PASS.

### Required manual observation record

Use the installed UI without starting an unattended updater. Record exact observed results, timestamps, Shale
version, Windows version, and whether each prompt appeared.

1. In Settings, disable the Phase 13A preference, exit normally, restart, and record the displayed value. Enable it
   as a Shale administrator, restart, and record it again. Switch to another Shale user and record the same setting.
   If a second existing Windows account is readily available, repeat there; do not create one for this test.
2. For New Intake, Contact edit, Case edit, Organization edit, Task edit, and Calendar/Event edit, separately record
   whether the aggregate Phase 11B lease/Phase 13B active-work input changes on open and clears on close/cancel.
   Make a harmless unsaved change and initiate a representative save where practical; absent instrumentation is
   `UNKNOWN / DEFER`, never evidence of safety.
3. For each workflow, attempt ordinary application Exit in four conditions: no editor, clean open editor, dirty
   editor, and save in flight. Record exits-without-prompt, prompt, block, loss, and wait behavior. Cancel prompts;
   do not discard work merely to finish this validation.
4. Interact with the foreground Shale window and capture the diagnostic/harness activity timestamp; verify an age
   below 30 minutes and `foregroundVisible=true` each defer. Use the existing injected-clock harness for boundaries.
5. Lock Windows with `rundll32.exe user32.dll,LockWorkStation`, then unlock and capture activity/foreground inputs.
   A lock must not make missing readiness safe. Do not attempt logged-out execution.
6. With no update to apply, invoke only enough of the existing **manual** update action to observe whether launching
   `ShaleUpdater.exe` requests UAC. If a legitimate test ZIP is already available, separately record UAC while it is
   applied. Record MSI UAC only during an otherwise-required install/upgrade; do not install solely for this test.
7. Temporarily disconnect the normal network using the workstation's approved method, evaluate once through the
   diagnostic harness, and record unavailable policy/package, deferral, absence of a Phase 12 attempt, and bounded
   retry state. Restore networking immediately. Do not use a real handoff.
8. Inspect the installed manifest/package configuration and updater log for SHA-256 use and archive rejection.
   Capture `Get-AuthenticodeSignature` output separately because current runtime code does not validate it.
9. Confirm in Task Manager that no restart is initiated. If an otherwise-required MSI returns 1641 or 3010, record
   that result and confirm Shale does not reboot automatically.

Do **not** launch `ShaleUpdater.exe` against a real package merely to test process collision: its current Windows
startup calls `WindowsPlatformSupport.terminateRunningApp()`, which immediately executes unconditional
`taskkill /IM Shale.exe /F`, waits for that command, and has no normal-exit wait or manual/unattended distinction.
The caller leaves Windows shutdown control to the updater. Until a safe Windows-tested lock seam exists, record
cross-process locking as FAIL and do not run overlapping updater processes.

## Active-work coverage inspection

This table records **static implementation inspection only** because no installed JavaFX Windows session was
available. “Local” means the editor has relevant state internally; Phase 13B does not currently receive that state.
No row is safe for an unattended decision today because the production runtime has no global no-prompt shutdown
readiness and must conservatively provide `cooperativeShutdownAvailable=false`.

| Workflow | Active-work observable | Dirty-state observable | Save-in-flight observable | Safe for unattended decision |
|---|---|---|---|---|
| New Intake | Yes, Phase 11B lease for modal lifetime | Local close policy only; not exported | Local `saving` state only; not exported | No — defer through unavailable cooperative shutdown |
| Case edit | Not reliably exposed to the Phase 13B contract | Some editors track changes/prompts locally; no aggregate | Some dialogs track submission locally; no aggregate | No — uncertainty must defer |
| Contact edit | Yes, Phase 11B lease for aggregate editor lifetime | Local editor/close behavior; not exported | Local operation state; not exported globally | No — defer through unavailable cooperative shutdown |
| Organization edit | Not reliably exposed to the Phase 13B contract | Local dialog dirty/confirmation behavior; not exported | Local save behavior; not exported globally | No — uncertainty must defer |
| Task edit | Not reliably exposed to the Phase 13B contract | Local task model/dialog state; not exported | Local in-flight counter; not exported globally | No — uncertainty must defer |
| Calendar/event edit | Not reliably exposed to the Phase 13B contract | Local dirty flags where implemented; no aggregate | Local submitting/saving flags where implemented; no aggregate | No — uncertainty must defer |

New Organization also has a Phase 11B modal lease, but that does not make Organization edit observable. The
important feasibility conclusion is conservative: the existing lease count is useful positive evidence, while an
absent lease is not proof that every substantive editor is clean.

## Installed evidence summary

Only the Maven row is newly established by supplied execution evidence. Runtime-only rows remain `NOT TESTED`;
static blockers remain `FAIL`. This distinction prevents Linux/source inspection from becoming Windows PASS.

| Scenario | Result | Evidence / limitation |
|---|---|---|
| Maven full suite | PASS | Full repository `mvn test` passed outside the previously restricted runner. |
| Installation scope/path | NOT TESTED | No installed Windows Shale instance is available; executable path, owner, effective write, and genuine per-user scope await the PowerShell transcript. |
| Updater path | NOT TESTED | Installed primary/legacy candidate and cross-version stability require the real installation and an upgrade observation. |
| ProgramData ACL | NOT TESTED | `%ProgramData%\Shale` owner, inheritance, ACEs, and effective current-user access require Windows. |
| Phase 13A persistence | NOT TESTED | Disable/enable plus restart sequence has not run on the installed application. |
| Second Shale-user behavior | NOT TESTED | Shale-user switch has not been observed on the installed application. |
| Cross-Windows-user behavior | NOT TESTED | Test only with a readily available second Windows account. |
| UAC | NOT TESTED | Updater launch and installed-owner privilege behavior require Windows observation. |
| ZIP/MSI execution | NOT TESTED | ZIP application and any otherwise-required MSI path have not been observed; do not install solely for validation. |
| Recent activity | NOT TESTED | No installed JavaFX Windows session; deterministic contracts are not runtime evidence. |
| Foreground app | NOT TESTED | No installed foreground-window observation; supplied `foregroundVisible=true` defers in the pure evaluator. |
| New Intake active work | NOT TESTED | Static lease presence is not substituted for installed lifecycle/eligibility observation. |
| Case edit | FAIL | Static inspection finds no reliable aggregate readiness signal; installed behavior remains to be observed and safety is UNKNOWN / DEFER. |
| Contact edit | NOT TESTED | Static lease presence is not substituted for installed lifecycle/eligibility observation. |
| Organization edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Task edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Calendar edit | FAIL | Static inspection finds no reliable aggregate readiness signal; safety is UNKNOWN / DEFER. |
| Dirty-state coverage | FAIL | Editor-local state is not a reliable global inspect-only readiness answer. |
| Save-in-flight coverage | FAIL | Save-in-flight cannot currently be proven globally; unattended handoff must defer unless stronger readiness is introduced. |
| Cooperative shutdown | FAIL | No aggregate inspect-only prompt/loss readiness exists; Windows updater force-stops Shale. |
| Cross-process update lock | FAIL | No `FileLock`, `FileChannel.tryLock`, named mutex, or equivalent protects desktop handoff/updater application. |
| Phase 12 ordering | PASS | Deterministic contract places the attempt at actual handoff; every eligibility deferral, including unavailable shutdown/lock, produces no attempt. No installed handoff was run. |
| Offline behavior | NOT TESTED | Installed authority/package loss and bounded retry observation remain outstanding. |
| Retry/window contract | PASS | Deterministic contracts retain 02:00 inclusive/04:00 exclusive, four evaluations, 30-minute nominal spacing, one handoff per local-date/zone window, and date/zone reset. |
| Package integrity | NOT TESTED | Installed package has not run; source has configured SHA-256 and traversal checks but no runtime Authenticode validation. |
| Windows lock screen | NOT TESTED | No Windows interactive session is available. |
| Logged-out behavior | DEFER/UNSUPPORTED | Phase 13B in-process architecture does not support logged-out execution. |
| Reboot | NOT TESTED | No installed update ran; source has no automatic reboot and ZIP has no MSI reboot-required result. |
| Production scheduler activation | DEFER/UNSUPPORTED | No timer, recurring executor, task, service, daemon, helper, or startup registration is activated. |

## Shutdown, locking, integrity, and activation verdict

Ordinary Exit routes to JavaFX `Platform.exit()` and the normal lifecycle, but there is no callable inspect-only
operation answering whether all windows can close without prompts or data loss. Manual Windows update launches the
updater while Shale remains running; the updater later waits for `taskkill` and uses `/F` by executable image name.
It does not distinguish manual from unattended invocation. Because no unattended production caller exists and the
eligibility service requires callers to assert cooperative shutdown and lock availability, no invocation-mode or
lock implementation was added without installed-Windows evidence. Future unattended work must not reuse this
force-stop path.

No existing cross-process mutex/file lock was found at desktop launch, manual handoff, updater startup, or install
application. Multiple ordinary Shale processes are not prevented by an application-wide single-instance contract.
The current Phase 13B foundation remains safe only while production wiring is absent and any prospective caller
reports shutdown/lock capability unavailable. Lock collision therefore cannot yet be validated, and a lock must be
acquired before a future real handoff/Phase 12 attempt begins; the updater must independently enforce it without a
desktop/updater ownership deadlock.

The updater downloads the platform ZIP, verifies SHA-256 when configured, and rejects archive traversal during
extraction. It does not validate Authenticode or MSI signatures at runtime. The in-app updater does not execute the
MSI and contains no automatic reboot behavior. No Task Scheduler task, service, startup timer, detached unattended
helper, SYSTEM execution, stored credential, anonymous policy endpoint, macOS implementation, SQL/API change, or
automatic reboot was added in this validation pass.

**Verdict: Phase 13B remains IN PROGRESS.** Concrete remaining blockers are: (1) execute this checklist on an
installed per-user Windows workstation, including preference persistence/ACL/UAC/session-lock evidence; (2) provide
and validate a conservative aggregate cooperative-shutdown readiness contract or keep unattended handoff
unsupported; and (3) provide and validate one per-install-owner OS-backed cross-process update lock or keep
eligibility deferred. Production scheduler activation remains disabled.
