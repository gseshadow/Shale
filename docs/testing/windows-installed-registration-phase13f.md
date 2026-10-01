# Phase 13F installed-Windows registration acceptance

**Status on 2026-10-01:** Phase 13F remains IN PROGRESS. Platform-neutral repository verification is PASS, but
installed-Windows acceptance is NOT YET RUN. The first real Windows packaging attempt reached the post-mutation
recompilation of generated `main.wxs` and failed with `CNDL0150` because the custom Candle invocation omitted
jpackage's generated `Jp*` preprocessor definitions. The first attempted fix scanned every textual
`$(var.Jp...)` reference in `main.wxs` and required a logged definition. A second real Windows build proved that check
overly strict: jpackage's own successful Candle invocation does not define optional variables referenced inside
conditional WiX branches, including `JpAboutURL`, `JpHelpURL`, and `JpUpdateURL`.

The third attempted fix replayed the visible definitions, but first its parser assumed the
wrong jpackage verbose-log serialization. JDK 21 on Windows actually emits `Command [PID: ...]:` followed by an
indented plain `candle.exe` command line, not the ProcessBuilder-style argument list the parser expected. The parser
was corrected and recovered all 11 visible definitions, including values containing spaces.

The fourth Windows finding proved that exact command-line replay is still insufficient: generated `main.wxs` also
requires `JpProductLanguage`, `JpInstallerVersion`, `JpCompressedMsi`, `JpInstallScope`, and upgrade/downgrade
detection variables supplied by jpackage's internal WiX environment, not its visible Candle command, generated
source, or stub `overrides.wxi`. The final correction therefore does not recreate that private environment.

The fifth Windows finding confirmed Option B but exposed an over-broad extraction check. `jimage` extracted the one
real JDK resource at `jdk.jpackage/jdk/jpackage/internal/resources/main.wxs`, while the batch `for /r` invocation
without a wildcard synthesized a `main.wxs` candidate once at every directory level. The six reported paths were:

* `main.wxs`
* `jdk.jpackage/main.wxs`
* `jdk.jpackage/jdk/main.wxs`
* `jdk.jpackage/jdk/jpackage/main.wxs`
* `jdk.jpackage/jdk/jpackage/internal/main.wxs`
* `jdk.jpackage/jdk/jpackage/internal/resources/main.wxs`

Only the last path is an actual extracted resource. It is authoritative because it is in the `jdk.jpackage` module's
Windows resource package beside jpackage's WiX resources (including `overrides.wxi`,
`InstallDirNotEmptyDlg.wxs`, and `MsiInstallerStrings_*.wxl`), rather than merely sharing the filename. Resource
selection now enumerates real files and requires exactly one normalized identity equal to
`jdk.jpackage/jdk/jpackage/internal/resources/main.wxs`. Zero or duplicate exact matches fail closed and print every
discovered `main.wxs` resource path. The selected file is copied byte-for-byte before the existing registration
mutation. Only customized `main.wxs` is supplied because jpackage obtains its other resources normally; the build
does not fork or copy the full JDK resource set.

The sixth Windows finding confirmed that resource injection itself works: jpackage reported that it loaded the
custom `main.wxs`. Its original Candle invocation then failed with `CNDL0387` because Phase 13F had added
`InstallPrivileges="elevated"` beside jpackage's `InstallScope="perUser"`. WiX 3.14 treats those package declarations
as incompatible and recommends using `InstallScope` alone. The selected architecture is native MSI Option A with a
strict owner constraint: retain per-user scope, omit `InstallPrivileges`, and require install, repair, upgrade, and
uninstall to be launched through UAC by the same split-token administrator who owns the installation. In an elevated
transaction the deferred `Impersonate="no"` actions run in the privileged installer-service context. In an
unelevated transaction their protected HKLM work must fail and roll back; there is no best-effort registration.

This does not claim that `Impersonate="no"` independently grants privilege, nor that jpackage's per-user MSI can
preserve one standard user's identity through an over-the-shoulder credential prompt. Explicitly running `msiexec`
under secondary administrator credentials changes the per-user client: `UserSID`, `LocalAppDataFolder`, install root,
Windows Installer ownership, and later maintenance belong to that administrator. That path is unsupported. Same-user
administrator consent retains the original SID/profile, and the action data is formatted before deferral so SYSTEM
is never inferred as owner. Windows installed acceptance must prove these statements for every lifecycle operation.

Option B (a separately signed elevated helper/bootstrapper) was not selected because coordinating its independent
UAC process with MSI rollback, repair, upgrade, and Add/Remove Programs uninstall would add a second transactional
owner and is not a narrow correction. It becomes necessary only if standard-user ownership plus secondary-credential
elevation is made a requirement. Option C was rejected because user-writable authority defeats Phase 13F. Option D
was rejected because it would change deployed per-user LocalAppData ownership, multi-user separation, updater paths,
upgrade/uninstall registration, and session/version assumptions merely to satisfy Candle.

The seventh Windows finding exposed a validation-layer error rather than another jpackage integration failure. The
authoritative raw JDK template intentionally expresses `InstallScope="$(var.JpInstallScope)"`; jpackage resolves that
variable during its original preprocessing/Candle invocation after loading the resource directory. Phase 13F had
incorrectly required literal `perUser` while mutating that unresolved template, so resource preparation stopped
before jpackage ran. Template validation now requires the exact jpackage-owned expression, rejects missing or literal
scope and every `InstallPrivileges` value, and preserves the expression byte-for-byte as an XML attribute value.
After jpackage, generated `main.wxs` remains TEMPLATE-validated because Candle does not rewrite source. Preliminary
and final Dark output separately validate the compiled MSI representation described in the ninth finding below and
require the complete registration action sequence. Identity
comparison remains independently required before publication. This layering does not define or replay
`JpInstallScope`, compile `main.wxs` a second time, or weaken the final MSI contract.

The build extracts the authoritative `main.wxs` resource from the selected JDK 21 module image, adds only the Phase
13F actions while preserving WiX preprocessor instructions, and passes it through jpackage's supported
`--resource-dir` input. jpackage's original Candle invocation compiles it with all native defaults and remains the
ProductCode, UpgradeCode, version, and package-identity authority. There is no second `main.wxs` compile. The later
link still replaces only the independently modified shortcut fragment, and a fail-closed comparison of Dark output
from the preliminary and final MSI requires ProductCode, UpgradeCode, and version to remain identical. Final Dark
validation still requires all registration actions before artifact finalization.

An eighth Windows run proved the original jpackage compile and all original Candle inputs succeeded, original
`light.exe` produced the preliminary MSI, and Option B resource injection is operational. It then exposed another
validation-layer error: Candle consumes but does not rewrite `config/main.wxs`, so that source still contains
`InstallScope="$(var.JpInstallScope)"` and cannot satisfy the resolved FINAL contract. The pipeline now applies only
TEMPLATE validation to that source, Dark-decompiles the preliminary MSI and applies FINAL validation there, applies
FINAL validation again to the Dark-decompiled relinked MSI, and then compares preliminary/final ProductCode,
UpgradeCode, and Version before artifact finalization.

That first successful preliminary link also reported ICE03 overflows for all five registration action-data setters.
Their former Targets were 17,642 (install), 17,644 (uninstall), 17,650 (rollback install), 17,652 (rollback
uninstall), and 17,641 (commit) characters because each repeated the 17,320-character encoded PowerShell program.
The MSI `CustomAction.Target` schema limit is 255 characters. The program now occupies one private MSI Property and
each setter uses a 254-character formatted Target containing its reference, compact mode, owner SID, `INSTALLDIR`,
and exact `LocalAppDataFolder\Shale` support root. Validation fails above 255 or when any field is missing. No ICE03
suppression was added. Installation acceptance remains unauthorized until a Windows rerun proves the warnings are
absent; Phase 13F remains IN PROGRESS.

The ninth Windows run proved the customized jpackage template compiles and links successfully, the preliminary MSI
is produced, and the five ICE03 Target overflows are gone. WiX 3.14 `dark.exe` also successfully decompiled that MSI.
The resulting `Package` did not reconstruct the source authoring abstraction `InstallScope="perUser"`; it emitted
`InstallPrivileges="limited"` and no `InstallScope`, `ALLUSERS`, or `MSIINSTALLPERUSER`. FINAL validation was therefore
corrected to validate this actual MSI/Dark representation: `InstallPrivileges="limited"` is required, `InstallScope`
is optional but may not be explicitly machine-scoped, and clear machine-wide or contradictory property signals fail
closed. TEMPLATE validation intentionally remains different: it requires the exact
`InstallScope="$(var.JpInstallScope)"` expression and forbids every explicit `InstallPrivileges` value. The source
template, original jpackage compile, Option B resource-injection architecture, registration actions, and privilege
architecture are unchanged. Phase 13F remains **IN PROGRESS** pending the corrected Windows rerun and installed
lifecycle acceptance.

The corrected ninth-finding build has not yet been rerun through the final relink on Windows, so neither final MSI
production nor installed acceptance is claimed. The available Codex host is
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
| Packaging defect and fix | PRELIMINARY MSI/DARK PROVED; NINTH-FINDING CORRECTION REQUIRES WINDOWS RERUN | Original jpackage Candle and light pass and produce a preliminary MSI without the Phase 13F ICE03 overflows. Dark represents its per-user package as `InstallPrivileges="limited"` without `InstallScope`; FINAL validation now checks that MSI representation for both preliminary and final output. Identity comparison and installed acceptance remain required. |

The exact next work is continuation of **Phase 13F only** on a suitable Windows machine. From the repository root,
run:

```bat
build\scripts\build-shale-release.bat
```

For installed lifecycle testing, launch the produced MSI from an elevated PowerShell opened with **Run as
administrator** by the same Windows account (consent prompt, not secondary credentials), for example:

```powershell
Start-Process "$env:SystemRoot\System32\msiexec.exe" -Verb RunAs -Wait -ArgumentList '/i', (Resolve-Path '.\dist\Shale-<version>.msi')
```

Use corresponding elevated same-account `msiexec /fa` and `/x` operations for repair and uninstall. Also run one
ordinary unelevated negative test and require transaction failure with no authoritative partial record.

The run must show jpackage resource preparation, successful preliminary jpackage, original-compile registration
validation, final link, identity/registration validation, and final MSI creation with no undefined `Jp*` variable.
It must not upload a manifest or artifacts.
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
