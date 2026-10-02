# Phase 13F installed-Windows registration acceptance

**Status on 2026-10-01: COMPLETE.** Platform-neutral repository verification and required installed-Windows
acceptance are PASS. The final fresh-install run found the authoritative 64-bit HKLM record, no duplicate UUID,
schema 1, current owner SID, canonical install/support roots, the protected ACL, production-reader `VALID` with exact
fact matching, schema-1 `1.0.129` / `PRODUCTION` metadata, and the updater executable. Earlier lifecycle runs proved
repair and `1.0.128` to `1.0.129` major-upgrade UUID preservation plus exact uninstall cleanup. Optional destructive
fixtures and genuine two-owner testing remain hardening evidence, not required closure gates. Production Authenticode
is **NOT RUN** because these developer artifacts were intentionally unsigned; it remains a separate deployment
prerequisite and no unsigned artifact is production-ready.

The first real Windows packaging attempt reached the post-mutation
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
suppression was added. At that point, installation acceptance remained unauthorized until a Windows rerun proved the warnings absent.

The ninth Windows run proved the customized jpackage template compiles and links successfully, the preliminary MSI
is produced, and the five ICE03 Target overflows are gone. WiX 3.14 `dark.exe` also successfully decompiled that MSI.
The resulting `Package` did not reconstruct the source authoring abstraction `InstallScope="perUser"`; it emitted
`InstallPrivileges="limited"` and no `InstallScope`, `ALLUSERS`, or `MSIINSTALLPERUSER`. FINAL validation was therefore
corrected to validate this actual MSI/Dark representation: `InstallPrivileges="limited"` is required, `InstallScope`
is optional but may not be explicitly machine-scoped, and clear machine-wide or contradictory property signals fail
closed. TEMPLATE validation intentionally remains different: it requires the exact
`InstallScope="$(var.JpInstallScope)"` expression and forbids every explicit `InstallPrivileges` value. The source
template, original jpackage compile, Option B resource-injection architecture, registration actions, and privilege
architecture were unchanged. At that point Phase 13F remained **IN PROGRESS** pending the corrected Windows rerun and installed
lifecycle acceptance.

The next Windows run passed the clean reactor, app-image, generated-WiX, preliminary-MSI, final-link, registration,
and identity checks, but exposed a payload-validation representation mismatch in the final Dark output. The MSI
File table preserved long installed JAR names as Dark `LongName` values while `Name` contained their generated 8.3
aliases; extracted `Source` paths are payload locations, not installed filenames. The validator had indexed only
`Name` (falling back to the authored source basename), so the diagnostic launcher's exact
`shale-desktop-1.0.129.jar` classpath entry could not match its one MSI File row. Compiled validation now derives
the installed name from MSI/Dark `LongName`, `SourceName`, or `Name` semantics (including `short|long` FileName
serialization), never from a Dark extraction path. It still requires exactly one classpath mapping, opens every
packaged JAR to prove exactly one desktop-owned diagnostic class, rejects the legacy core class, and proves the
production reader remains in exactly one effective `shale-core` JAR. Generated source and app-image validation
remained unchanged and strict. At that point Phase 13F remained **IN PROGRESS** pending the Windows rebuild and installed reader
acceptance; no Phase 13G work has begun.

The corrected build was subsequently produced and used for the lifecycle evidence recorded below. The current
Codex host is Ubuntu Linux and cannot rerun the corrected ACL/reader helper against that installation. Do not report
either remaining row as PASS until its command is executed on an installed Windows machine and evidence is retained.
Loose classes, exploded directories, source inspection, and platform-neutral tests are not substitutes for that
installed validation.

## Current acceptance evidence

| Item | Result | Evidence / remaining action |
| --- | --- | --- |
| Windows build/version tested | PASS | Installed lifecycle evidence covers `1.0.128` and `1.0.129`. |
| MSI tested | PASS | Fresh install, repair, major upgrade, and exact uninstall were executed with the generated MSIs. |
| Production signing | NOT RUN | Developer artifacts were intentionally unsigned. Expected-publisher/timestamp Authenticode remains a separate deployment prerequisite; this is not production-ready evidence. |
| Fresh-install registration | PASS | Fresh install created the authoritative HKLM registration. |
| Authoritative registration path | PASS | The installed record was `HKLM\SOFTWARE\Shale\Installations\ca2bb9b9-9576-400d-a637-0ad645c52bea`. |
| Installation UUID | PASS | `ca2bb9b9-9576-400d-a637-0ad645c52bea` was retained through repair and major upgrade. |
| Owner SID and owner-derived roots | PASS | Installed validation matched the registered SID and both canonical owner-derived roots. |
| Authoritative-record ACL and ordinary-user mutation resistance | PASS | The exact 64-bit child is protected with no inherited ACEs; SYSTEM and Administrators have FullControl; the owner has ReadKey only; no unrelated allow-write/control ACE was observed. |
| Production reader classification | PASS | The installed desktop-owned diagnostic invoked the core production reader, returned `VALID`, and exactly matched the authoritative UUID, SID, install root, and support root. |
| Installed-version metadata | PASS | Installed schema 1 metadata reported version `1.0.129` and the expected production channel. |
| Updater, Phase 12 attempt, Phase 13B lock, and evidence-log paths | PASS | Registered owner paths were resolved and the installed updater exists at the expected path. |
| Runtime startup and Phase 13C availability | NOT RUN | No installed Shale application was launched. |
| Upgrade UUID preservation | PASS | `1.0.128` to `1.0.129` major upgrade preserved UUID `ca2bb9b9-9576-400d-a637-0ad645c52bea`. |
| Repair and replacement-UUID fallback | PARTIAL | Ordinary repair preserved UUID `ca2bb9b9-9576-400d-a637-0ad645c52bea`; destructive replacement-UUID and corrupt-state fixtures remain NOT RUN. |
| Exact uninstall cleanup | PASS | Exact uninstall removed the authoritative HKLM registration. Separate noninterference fixtures remain part of multi-user/security acceptance. |
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

Phase 13F is closed. The following commands remain the reproducible packaging/acceptance procedure for future signed releases. From the repository root, run:

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
Installed lifecycle acceptance is PASS for Phase 13F. Logged-out automatic update support remains **UNSUPPORTED** and was separately reevaluated in Phase 13G.

After installing the Phase 13F MSI, run the privacy-bounded, read-only helper from an elevated PowerShell prompt
and paste its complete concise report into the acceptance evidence:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\scripts\validate-installed-windows.ps1
```

The helper directly opens `HKEY_LOCAL_MACHINE` through the 64-bit .NET registry view for ACL inspection; it does
not depend on PowerShell provider `PSPath` ACL behavior or localized account names. It also starts only the installed
`ShaleRegistrationDiagnostic.exe` alternate jpackage launcher with the selected installation UUID. The launcher uses
the actual generated application classpath and calls the narrow diagnostic entry point backed by the production
reader in `shale-core`. The entry point itself is uniquely owned by `shale-desktop`; updater JAR layout is unchanged.
It creates no Start Menu or desktop shortcut. The diagnostic accepts no arbitrary command, performs no
write/heal operation, and returns only classification, schema, UUID, owner SID, install root, and support root. A
missing launcher, invocation failure, non-`VALID` classification, or fact mismatch is a specific `FAIL`.

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
