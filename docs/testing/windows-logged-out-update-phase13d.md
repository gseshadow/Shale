# Phase 13D Windows logged-out automatic-update feasibility

**Decision:** `UNSUPPORTED` for the current per-user installation architecture.

**Verification status:** design and repository contracts are complete; no installed-Windows logged-out execution
was performed. This runbook records evidence required by a separately authorized successor phase. It is not a setup
guide and must not be used to register a production task or service.

## Why no prototype was registered

The application belongs to one Windows user under `C:\Users\<install-owner>\AppData\Local\Shale\`, while consent is
one machine-scoped value under `%ProgramData%\Shale`. Consent does not identify an installation. A logged-out process
also lacks Shale's memory-only bearer used by Phase 13C to read authoritative central policy. The public package
manifest is package authority, not a permitted replacement for policy. Both installed executables are currently
`NotSigned`, and the MSI has no proven task/service cleanup lifecycle. These independent gaps make a background
prototype unsafe even though ZIP SHA-256 and traversal validation remain useful package-integrity controls.

`LoggedOutAutomaticUpdateSupport.current()` therefore returns only `UNSUPPORTED`. It creates no process, task,
service, credential, attempt, log, API request, or preference mutation.

## Principal and scheduler options

| Option | Credential/session behavior | Per-user install and network consequences | Phase 13D result |
| --- | --- | --- | --- |
| Install owner, **run only when logged on** | Interactive token; no stored password | Correct profile/network identity, but not logged-out execution | Rejected for 13D; Phase 13C is the safer in-session mechanism |
| Install owner, **run whether logged on or not**, password logon | Task Scheduler stores OS-managed credentials and creates a non-interactive logon | Could target the correct profile and normally use network credentials, but violates the requirement not to request/store the Windows password | Rejected |
| Install owner with S4U/passwordless task logon | No password is stored; S4U is a non-interactive token | Microsoft documents no access to network resources or encrypted files; loaded-profile/AppData, TLS trust, and package access still need Windows proof | Rejected for the current network-dependent design |
| `SYSTEM` task | No user password; machine service identity | Can traverse profiles but is not the install owner; `%LOCALAPPDATA%` resolves to SYSTEM, replacement may alter ownership/ACLs, certificate behavior differs, and one task cannot safely infer which users' installs to update | Rejected |
| Privileged Windows Service | Service account/SYSTEM; survives logout | Adds a privileged persistent attack surface, service install/update/self-protection, explicit registration, impersonation/ACL rules, and deterministic uninstall requirements | Deferred; major new architecture |
| Installer-owned helper | Depends on its registered principal | Could eventually provide explicit owner/install metadata, but the current per-user MSI creates no signed protected helper or registration and cannot prove upgrade/uninstall cleanup | Deferred |

Relevant semantics must be revalidated against Microsoft's Task Scheduler documentation in successor work: password
logon (`TASK_LOGON_PASSWORD`) stores credentials; S4U (`TASK_LOGON_S4U`) stores no password but has no network or
encrypted-file access; interactive-token tasks require an interactive session; service-account tasks use that
service identity. The primary references are Microsoft's
[`TASK_LOGON_TYPE`](https://learn.microsoft.com/windows/win32/api/taskschd/ne-taskschd-task_logon_type) and
[`Principal.LogonType`](https://learn.microsoft.com/windows/win32/taskschd/principal-logontype) documentation.
Phase 13D does not claim these source findings as installed acceptance evidence.

## Target discovery, version, and multi-user consequences

Uninstall registry data or a known `LocalAppData` convention can be discovery hints, never sufficient authority.
`DisplayVersion` is stale on observed installations and must not decide eligibility. A future solution needs a
separate, bounded installer-created registration containing only stable install identity, explicit owner SID, an
absolute normalized install root, updater path, attempt/log roots, and a trustworthy local runtime-version source.
It must not modify or overload the Phase 13A preference file.

A machine can contain independent User A and User B installs while sharing one enabled workstation preference. One
machine task enumerating profiles would cross ownership boundaries, amplify a writable-binary attack, and make
attempt attribution and cleanup ambiguous. Consent means only that unattended evaluation is allowed on the
workstation; it does not select either install. Phase 13D selects no installation and updates none.

The future closed-app version source should be signed packaged metadata, a signed executable version resource, or
installer-maintained release metadata protected with the installation. It must not launch JavaFX, use uninstall
`DisplayVersion`, or trust user-editable version data without an authenticity design.

## Policy, package, preference, and execution boundary

The current policy response is global/channel-scoped release metadata: channel, revision, latest/recommended/minimum
references, required deadline, access mode, publication time, and server time. It contains no tenant, user, bearer,
session, or PHI fields. A narrowly scoped, rate-limited public Production-policy read might be feasible, but Phase
13D does **not** add it: endpoint exposure, cache/staleness rules, abuse controls, deployment ownership, and
identical semantics to authenticated Phase 11 authority require a dedicated server security decision. The static
manifest alone remains insufficient. With no authenticated user and no approved public authority, execution is
`UNSUPPORTED` rather than manifest-only.

If a successor closes that gap, execution must still follow this order:

1. resolve one registered install and its owner explicitly (never from the executor's `%LOCALAPPDATA%`);
2. reread `WorkstationUpdatePreferenceProvider`; only valid `ENABLED` continues;
3. obtain fresh authoritative Production policy; offline/unavailable exits with no attempt;
4. read trustworthy packaged runtime version and reuse the existing manifest/downloader, SHA-256, and archive checks;
5. detect Shale for the target install across interactive, locked, and disconnected sessions; if running, defer to
   Phase 13C without dialog automation, cross-session messaging, or force termination;
6. acquire the existing per-owner OS-backed `UpdateExecutionLock` at the owner's explicit support path;
7. only at actual updater handoff create Phase 12 state in that same owner's explicit attempt directory;
8. launch non-interactively with stable result codes and no UAC prompt, UI, reboot, or rapid retry.

Preference disabled/missing/corrupt/unavailable, policy unavailable, no update, app running, lock busy, and package
unavailable are eligibility exits and create no Phase 12 attempt. Package-validation failure or a later execution
failure is recorded only after real handoff. Completion remains a later normal Shale startup reading the same
per-owner store. Logs likewise belong in a protected, explicitly resolved owner/application location—not SYSTEM's
profile and not a globally writable directory.

## Supported and unsupported Windows states

* **Supported automatic mechanism:** Phase 13C only—Shale running in an authenticated install-owner desktop session,
  including a locked or disconnected session only insofar as the process remains alive and all existing Phase 13C
  gates independently pass. Lock/disconnect is never itself evidence of idleness or safety.
* **Phase 13D supported logged-out scenarios:** none.
* **Unsupported:** Shale closed with no authenticated Shale session; owner logged out; no interactive user; an install
  owned by another user; multiple discovered installs; SYSTEM/service execution; elevation/UAC required;
  authoritative policy unavailable; unsigned background binary trust; or any state requiring a prompt, wake,
  force-kill, cross-session UI, password/JWT storage, or automatic reboot.
* With multiple logged-in users, any running target Shale instance causes deferral. A future helper must not infer
  that a disconnected RDP or locked session is logged out.

One daily/overnight **evaluation** with no wake-from-sleep and no tight retry loop is the preferred future trigger.
The shared lock remains final authority alongside Phase 13C. Enabled consent cannot activate rollout: a separate
safe-default-off capability flag and installed acceptance are required.

## Threat review and lifecycle blockers

An ordinary user can write much of a per-user install, so a higher-privilege task/service executing an unsigned,
replaceable helper or updater can become a privilege-escalation path. SYSTEM broadens impact across every profile.
SHA-256 from the HTTPS manifest detects package corruption/substitution only to the trust afforded that source; it
does not authenticate the installed launcher/helper as Authenticode would. Stale/replayed policy must not authorize
an install, and malicious package hosting must not override central target/channel authority.

Before any prototype, require Authenticode signing/verification for every scheduled executable, protected task and
registration ACLs, explicit owner/install registration, canonical-path and reparse-point defenses, authoritative
policy access, owner-correct attempt/log/lock paths, target-specific running-process detection, non-elevated ZIP
replacement proof, and deterministic stable identity `\Shale\Automatic Update`. Registration/update must be
idempotent with no per-version duplicates; preference-off execution must still no-op; upgrades must update in place;
and the per-user MSI must remove the task/registration on uninstall. Inability to prove cleanup remains a blocker.

No broad telemetry or new audit row is appropriate. Evaluation is local operational work; Phase 12 remains the
bounded attempt record. There is no SQL, API, schema, production task, service, helper, signing, macOS, reboot, or
force-kill change in Phase 13D.

## Installed-Windows successor validation procedure

Run only after a later phase supplies a signed, feature-flag-disabled prototype and approved test package. Record
Windows/build versions, task XML, sanitized paths, exit code, owner/SID, ACLs, and timestamps. Never put a password,
bearer, user email, tenant, machine UUID, or PHI in the transcript.

```powershell
Get-ScheduledTask -TaskPath '\Shale\' -TaskName 'Automatic Update' |
  Select-Object -ExpandProperty Principal | Format-List *
Export-ScheduledTask -TaskPath '\Shale\' -TaskName 'Automatic Update'

# Resolve these from protected install registration, not the executor environment.
$installRoot = '<registered absolute install-owner path>'
$supportRoot = '<registered absolute install-owner support path>'
Get-Acl $installRoot, "$installRoot\app\updater\ShaleUpdater.exe", $supportRoot | Format-List
Get-AuthenticodeSignature "$installRoot\Shale.exe", "$installRoot\app\updater\ShaleUpdater.exe"

# Presence must produce APP_RUNNING/defer, never taskkill.
Get-CimInstance Win32_Process -Filter "Name='Shale.exe'" |
  Select-Object ProcessId, ExecutablePath, SessionId

Get-Item "$supportRoot\updates\update-execution.lock" -ErrorAction SilentlyContinue
Get-ChildItem "$supportRoot\update-attempts" -ErrorAction SilentlyContinue
```

Test separately: current owner logged on; locked; disconnected RDP; owner logged out; no interactive users; another
user logged in; two owners installed; offline; preference disabled/missing/corrupt; policy unavailable; app running;
lock held by manual update and Phase 13C; invalid SHA-256/archive; insufficient access/UAC; upgrade registration;
uninstall cleanup; and later startup reconciliation. Validate network access using only approved policy/manifest
test requests. Validate owner profile and attempt/log paths explicitly; the task's `%LOCALAPPDATA%` is never proof.

Expected Phase 13D result for every logged-out case today is `UNSUPPORTED`, with no process, attempt, prompt, task
mutation, retry loop, or replacement. Do not mark any row PASS until executed on an installed Windows machine.

## Phase 13E installed-Windows prerequisite validation (NOT RUN)

This remains an installed-Windows acceptance procedure; no row was executed on the Linux development host.

```powershell
$installRoot = "$env:LOCALAPPDATA\Shale"
Get-AuthenticodeSignature "$installRoot\Shale.exe" |
  Select-Object Status, StatusMessage, @{n='Publisher';e={$_.SignerCertificate.Subject}}, @{n='Timestamp';e={$_.TimeStamperCertificate.Subject}}
Get-AuthenticodeSignature "$installRoot\app\updater\ShaleUpdater.exe" |
  Select-Object Status, StatusMessage, @{n='Publisher';e={$_.SignerCertificate.Subject}}, @{n='Timestamp';e={$_.TimeStamperCertificate.Subject}}
Get-AuthenticodeSignature '.\Shale-<version>.msi'

# Registration persistence is intentionally not installed yet. After an installer architecture phase supplies it,
# validate exact HKLM/ProgramData location, SID, roots, explicit ACL, two-owner isolation, repair restoration,
# upgrade identity preservation, stale-root rejection, and exact-record uninstall cleanup.
Get-Content "$installRoot\app\shale-installed-version.properties"
# Verify initial install, updater replacement, runtime match, mismatch diagnostics, and stale DisplayVersion ignored.
```

A signing-required release expects `Valid`, approved publisher, and timestamp certificate. Unsigned developer builds
remain `NotSigned` and must not be described as production. Do not mark registration/lifecycle rows PASS until the
installer-owned mechanism exists and the procedure is run.
