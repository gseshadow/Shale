# Phase 13B installed-Windows feasibility checklist

**Current status (2026-09-30):** installed-Windows execution remains outstanding. The validation runner available
for this pass is Linux (`Linux 6.18.44 x86_64`); it exposes neither `powershell.exe` nor `cmd.exe`, no installed
Shale workstation, `%ProgramData%`, Windows accounts, Task Scheduler, session lock, UAC, MSI, or Windows ACLs.
Consequently this report does not turn source inspection or deterministic unit contracts into installed-Windows
evidence. Phase 13B remains `IN PROGRESS` and production activation remains off.

The supplied repository baseline records a passing full `mvn test`. A validation-run attempt to execute the
focused Phase 11A/11B/12/13A/13B and updater regressions in this container was blocked before project construction
when Maven Central returned HTTP 403 for the Spring Boot dependency BOM. Automated Java/Maven verification is
therefore no longer the roadmap blocker, but this container did not independently reproduce that reported pass.

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

`NOT APPLICABLE` below means **not executable on the non-Windows validation runner**, not that the acceptance
criterion has been waived. Every item that requires an installed Windows workstation remains open.

| # | Result | Evidence/limitation |
|---:|---|---|
| 1 | NOT APPLICABLE | No Windows Task Scheduler or installed MSI is present. Source/package inspection still finds no Phase 13B registration code. |
| 2 | NOT APPLICABLE | No installed Settings preference or Windows evaluation harness can be exercised. The pure test contract expects `DEFER_PREFERENCE` and zero handoffs. |
| 3 | NOT APPLICABLE | No installed Settings UI, restart, Shale-user switch, or `%ProgramData%` is available. Provider reread is covered by the existing deterministic contract only. |
| 4 | NOT APPLICABLE | No installed authenticated policy/manifest runtime is available. Pure eligibility distinguishes no update and unavailable authority. |
| 5 | NOT APPLICABLE | No JavaFX Windows foreground-input session is available. Deterministic tests cover 1,799, 1,800, and 1,801 seconds, missing evidence, and future skew. |
| 6 | NOT APPLICABLE | Representative installed editors could not be opened. Static coverage findings are recorded below and are not runtime evidence. |
| 7 | NOT APPLICABLE | No installed runtime can be clock-driven here. The shared pure resolver tests define 01:59:59 closed, 02:00 open, 03:59:59 open, and 04:00 closed. |
| 8 | NOT APPLICABLE | Windows DST/timezone behavior could not be observed. Deterministic `ZonedDateTime` contracts cover spring-forward, fall-back, and zone identity changes. |
| 9 | NOT APPLICABLE | The host cannot lock a Windows interactive session or observe Windows foreground behavior. |
| 10 | PASS | Code and architecture consistently state that logged-out execution is unsupported; no task, helper, service, public policy endpoint, or credential storage exists. |
| 11 | NOT APPLICABLE | No installed network/policy runtime is available. The pure design defers unavailable authority before handoff and bounds evaluations to four. |
| 12 | NOT APPLICABLE | No real updater handoff is safe in this environment. The retry contract limits handoffs to one per local-date/zone window. |
| 13 | FAIL | Source inspection confirms that no shared OS cross-process update lock exists. Eligibility can be forced to defer through its supplied lock input, but no installed lock owner supplies that fact. This remains an activation blocker. |
| 14 | FAIL | Windows updater source still executes `taskkill /IM Shale.exe /F`; no inspect-only global readiness operation or prompt-free cooperative close exists. Unattended callers must continue to supply shutdown unavailable. This remains an activation blocker. |
| 15 | NOT APPLICABLE | No real handoff was attempted. Existing unit boundaries place Phase 12 creation at handoff rather than eligibility, but installed reconciliation was not rerun. |
| 16 | NOT APPLICABLE | UAC, install-owner identity, and writable installed files require Windows runtime evidence. No claim of prompt-free execution is made. |
| 17 | NOT APPLICABLE | Installed package behavior and Authenticode posture cannot be exercised. Source inspection confirms configured SHA-256 checking, ZIP traversal rejection, and no runtime Authenticode verification. |
| 18 | NOT APPLICABLE | The in-app source path consumes ZIP rather than MSI and contains no reboot call; Windows installer reboot-required behavior was not observable. |
| 19 | NOT APPLICABLE | No installed preference can be changed. The provider-first pure contract still fails closed for every state other than explicit `ENABLED`. |
| 20 | NOT APPLICABLE | Phase 13B installs no task/helper/state, so cleanup is presently inapplicable; a future activated implementation must execute this acceptance test. |

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

## Evidence summary

| Scenario | Result | Evidence/limitation |
|---|---|---|
| Preference enabled/disabled | NOT APPLICABLE | Windows Settings/restart/user-switch sequence unavailable; pure provider-first behavior exists but is not installed evidence. |
| ProgramData ACL | NOT APPLICABLE | `%ProgramData%`, its owner/ACEs, and a second Windows account are unavailable. Stronger MSI ACL provisioning is absent in source. |
| Update path privilege/UAC | NOT APPLICABLE | No installed EXE/MSI or UAC desktop. ZIP is the source-selected in-app path, but prompt-free execution remains unproven. |
| Human activity | NOT APPLICABLE | No Windows JavaFX foreground session; instant-based threshold boundaries exist in deterministic tests. |
| Foreground state | NOT APPLICABLE | Installed foreground detection could not run; the evaluator conservatively defers when supplied `foregroundVisible=true`. |
| Active workflow | FAIL | Static inspection found incomplete aggregate visibility across the required workflows. Current safe result remains defer. |
| Dirty state | FAIL | Dirty state is local to individual editors and has no reliable global readiness view. Current safe result remains defer. |
| Save in flight | FAIL | Save/submission state is local and incomplete as a global signal. Current safe result remains defer. |
| Cooperative shutdown readiness | FAIL | Ordinary `Platform.exit()` has no inspect-only global readiness answer; updater force-kills by image name. Unattended close remains unsupported. |
| Cross-process update lock | FAIL | No shared file/mutex/process lock exists at desktop handoff or updater startup. Eligibility must be supplied lock-unavailable. |
| Offline | NOT APPLICABLE | Installed network loss not exercised; unavailable authority is a pre-handoff deferral in the pure contract. |
| Package verification | NOT APPLICABLE | Installed package not exercised; source has optional configured SHA-256 plus ZIP traversal checks and no Authenticode validation. |
| Locked workstation | NOT APPLICABLE | Windows session lock unavailable. Locked-session support is not claimed. |
| Logged-out workstation | PASS | Unsupported by Phase 13B in-process architecture. No code claims otherwise. |
| Reboot | NOT APPLICABLE | No installer was run. Source contains no automatic reboot and the current ZIP path cannot report MSI reboot-required state. |
| Uninstall/task cleanup applicability | PASS | No scheduler, task, helper, service, or Phase 13B operational state is installed, so there is currently nothing new to remove. |

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
