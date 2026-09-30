# Phase 13F installed-Windows registration acceptance

**Status on 2026-09-30:** Phase 13F implementation is COMPLETE and the full repository `mvn test` verification is
PASS. Installed-Windows acceptance is NOT YET RUN, so overall Phase 13F remains IN PROGRESS. The available host is
Ubuntu Linux and has no PowerShell, Windows command environment, Wine, Windows VM manager, MSI artifact, remote
Windows runner, or release signing credential. Do not report any installed row as PASS until its command is executed
on an installed Windows machine and evidence is retained. Loose classes, exploded directories, source inspection,
and platform-neutral tests are not substitutes for MSI lifecycle validation.

## Current acceptance evidence

| Item | Result | Evidence / remaining action |
| --- | --- | --- |
| Windows build/version tested | NOT RUN | Repository version is `1.0.128`, but no Windows build was produced or tested on this host. |
| MSI tested | NOT RUN | No MSI artifact is present; the supported Windows release pipeline was not executable on Linux. |
| Signing mode | NOT RUN | No MSI was built, so it is not classified as either a signed release artifact or an unsigned developer artifact. |
| Fresh-install registration | NOT RUN | Requires a normal installation of the generated MSI on Windows. |
| Authoritative registration path | NOT RUN | The contract path is `HKLM\SOFTWARE\Shale\Installations\<installation UUID>`; no actual key was inspected. |
| Installation UUID | NOT RUN | No installed registration exists on this host. |
| Owner SID and owner-derived roots | NOT RUN | No genuine Windows installation owner or ProfileList authority is available. |
| Authoritative-record ACL and ordinary-user mutation resistance | NOT RUN | The actual child key and its effective ACL must be inspected on Windows; parent/source ACL assumptions are insufficient. |
| Production reader classification | NOT RUN | `VALID` has not been observed against an installed registration. |
| Installed-version metadata | NOT RUN | No installed `app\shale-installed-version.properties` payload exists to compare with the packaged runtime or parse strictly. |
| Updater, Phase 12 attempt, Phase 13B lock, and evidence-log paths | NOT RUN | No owner registration was available from which to resolve and compare these paths. |
| Runtime startup and Phase 13C availability | NOT RUN | No installed Shale application was launched. |
| Upgrade UUID preservation | NOT RUN | Requires two actual MSI versions and the normal upgrade path. |
| Repair and replacement-UUID fallback | NOT RUN | Requires controlled Windows Installer repair fixtures. |
| Exact uninstall cleanup | NOT RUN | Requires an installed record plus a separate exact fixture/installation to prove noninterference. |
| Stale registration | NOT RUN | Requires an exact elevated disposable registry fixture and cleanup. |
| Duplicate UUID | NOT RUN | Requires controlled conflicting claimants and cleanup. |
| Owner/path mismatch | NOT RUN | Requires a controlled Windows owner/path fixture. |
| Reparse/junction rejection | NOT RUN | This host cannot create or safely validate the required Windows reparse fixture. |
| Genuine multi-user lifecycle | NOT RUN | No installed Windows machine with two suitable accounts is available. |
| Authenticode: `Shale.exe` | NOT RUN | No installed artifact or production signing credentials are available. |
| Authenticode: `ShaleUpdater.exe` | NOT RUN | No installed artifact or production signing credentials are available. |
| Authenticode: MSI | NOT RUN | No MSI artifact or production signing credentials are available. |
| Phase 13A / 13B / 13C installed regressions | NOT RUN | Their installed paths and behavior cannot be exercised on Linux; repository verification is PASS. |
| Logged-out automatic updating | UNSUPPORTED | Unchanged: registration does not activate Task Scheduler, a service, SYSTEM updating, or any logged-out executor. |
| Defects and fixes | NONE OBSERVED | Installed acceptance did not execute, so this is not evidence that installed defects are absent; no production change was made. |

The exact next work is continuation of **Phase 13F installed-Windows acceptance completion only** on a suitable
Windows machine. No subsequent phase is recommended or authorized until these lifecycle results are known.

Run the privacy-bounded helper from an elevated PowerShell prompt after each lifecycle operation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\scripts\validate-installed-windows.ps1
```

For signed acceptance, require `Valid`, approved publisher, and timestamp:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\scripts\validate-installed-windows.ps1 -MsiPath .\dist\Shale-<version>.msi -ExpectedPublisher '<approved subject fragment>'
```

## Sequence

1. **Fresh install:** confirm one `VALID` record, current SID, equal canonical `%LOCALAPPDATA%\Shale` roots,
   protected non-inherited ACL, schema 1 runtime-matching metadata, and derived updater/attempt/lock/log paths.
   Confirm an unrelated standard user cannot write the key.
2. **Upgrade:** record UUID, install next build, and confirm UUID/SID/roots unchanged, no duplicate, and semantic
   version metadata changed. DisplayVersion is not evidence.
3. **Repair:** remove only the record while retaining protected InstallerState, repair, and confirm the same UUID.
   Corrupt protected state must fail; removing state and record uses the documented replacement-UUID fallback.
4. **Rollback:** force create/update failure and confirm prior registration remains; force uninstall cleanup failure
   and confirm uninstall fails/restores rather than reporting false success.
5. **Uninstall:** confirm only this user's exact UUID record/state disappear.
6. **Signing:** validate installed `Shale.exe`, `ShaleUpdater.exe`, and MSI. Unsigned developer output is `NotSigned`,
   not PASS.
7. **Two users:** confirm distinct UUID/SID/root records and A's upgrade/uninstall leaves B unchanged. NOT RUN.
8. **Stale/reparse:** use controlled elevated fixtures and expect `STALE` and `REPARSE_UNSAFE`; clean exact fixtures.
   NOT RUN.

The helper prints no token, Shale identity, tenant, credential, fingerprint, session data, or PHI and creates no
Phase 12 attempt. Paths must equal normal owner paths independent of the validator environment. Registration alone
never makes an unsigned executable trustworthy.
