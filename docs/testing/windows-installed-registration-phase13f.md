# Phase 13F installed-Windows registration acceptance

**Status on 2026-09-30:** Phase 13F remains IN PROGRESS. Platform-neutral repository verification is PASS, but
installed-Windows acceptance is NOT YET RUN. The first real Windows packaging attempt reached the post-mutation
recompilation of generated `main.wxs` and failed with `CNDL0150` because the custom Candle invocation omitted
jpackage's generated `Jp*` preprocessor definitions. The first attempted fix scanned every textual
`$(var.Jp...)` reference in `main.wxs` and required a logged definition. A second real Windows build proved that check
overly strict: jpackage's own successful Candle invocation does not define optional variables referenced inside
conditional WiX branches, including `JpAboutURL`, `JpHelpURL`, and `JpUpdateURL`.

The exact-definition replay design was correct, but a third Windows finding showed that its parser assumed the
wrong jpackage verbose-log serialization. JDK 21 on Windows actually emits `Command [PID: ...]:` followed by an
indented plain `candle.exe` command line, not the ProcessBuilder-style argument list the parser expected. The parser
now treats that observed format as first-class while retaining compatibility with the older representation.

The corrected packaging boundary treats the original successful generated-`main.wxs` Candle command in
`jpackage-verbose.log` as authoritative. It recovers every and only `-dJp...` argument from that invocation, validates
the core jpackage identity/configuration definitions, and replays the values unchanged through a quoted Candle
response file. It does not scan raw WiX references as a mandatory-variable list. The corrected Windows build has not
yet been rerun, so neither final MSI production nor installed acceptance is claimed. The available Codex host is
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
| Packaging defect and fix | THIRD FIX IMPLEMENTED; DIRECT WINDOWS HELPER RERUN REQUIRED | Exact definition replay was correct, but the parser expected a ProcessBuilder-style list while the actual JDK 21 Windows log uses `Command [PID: ...]:` followed by an indented plain command line. The parser now supports that observed form, selects exactly one generated-`main.wxs` Candle command, and preserves spaced definition values. Run the direct helper check below before another full packaging run. |

The exact next work is continuation of **Phase 13F only** on a suitable Windows machine. From the repository root,
first run only:

```bat
python build\scripts\windows_jpackage_wix_definitions.py prepare build\staging\windows-msi\jpackage-verbose.log build\staging\windows-msi\jpackage-main-definitions-test.rsp
type build\staging\windows-msi\jpackage-main-definitions-test.rsp
```

The helper must report recovered definitions and create the response file. Only after that direct check succeeds,
run `build-shale-release.bat`; it must produce and validate the final MSI without uploading a manifest or artifacts.
Installed lifecycle acceptance follows separately. Logged-out automatic update support remains **UNSUPPORTED**.

After installing the Phase 13F MSI, run the privacy-bounded, read-only helper from an elevated PowerShell prompt
and paste its complete concise report into the acceptance evidence:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\scripts\validate-installed-windows.ps1
```

For signed acceptance, require `Valid`, approved publisher, and timestamp:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\scripts\validate-installed-windows.ps1 -MsiPath .\dist\Shale-<version>.msi -ExpectedPublisher '<approved subject fragment>'
```

This helper covers only non-destructive fresh-install observations. Upgrade, repair, rollback, uninstall,
multi-user, stale-registration, duplicate-UUID, and reparse-fixture lifecycle tests remain separate manual steps;
the helper does not create or mutate those fixtures. Running it and pasting its report does not mark Phase 13F
complete.

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
