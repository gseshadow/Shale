# Windows packaging and in-app update

The installed Shale application uses the existing per-user `jpackage` layout and locates `ShaleUpdater.exe` first at
`app\updater\ShaleUpdater.exe`, with the established legacy fallback. In-app application replacement uses the
manifest-selected Windows ZIP overlay; Phase 13C does not introduce unattended MSI installation.

Automatic activation is session-only. Shale must already be running and authenticated through the 02:00–04:00 local
window. The coordinator does not install a scheduled task or service, wake a computer, operate after logout, request
SYSTEM access, bypass UAC, or reboot Windows. A non-writable installation path is treated as unsupported because the
automatic path may not display or conceal an elevation prompt.

The desktop acquires the shared per-user update execution lock before Phase 12 attempt creation and launches the
updater with `--lockHandoff true --invocationMode UNATTENDED`. The updater independently acquires that same lock.
`UNATTENDED` never invokes `taskkill /F`; manual updates retain `MANUAL` behavior. Startup reconciliation in Shale,
not process launch or ZIP application, decides completion.

Current installed acceptance evidence records both `Shale.exe` and `ShaleUpdater.exe` as NotSigned. Phase 13C does
not add or claim Authenticode signing. Signing and runtime signature verification remain release-hardening debt.

Phase 13D does not register a scheduled task, service, SYSTEM helper, install-owner credential, or installation
registration. Logged-out/closed-app updating is explicitly `UNSUPPORTED`: the per-user install cannot yet be paired
safely with a credentialless owner principal, authoritative policy, trustworthy closed-app version metadata,
owner-correct attempt/log/lock paths, signed executables, and deterministic MSI cleanup. Phase 13C remains the only
automatic mechanism. See `docs/testing/windows-logged-out-update-phase13d.md`.

## Phase 13E signing and installed-version metadata

Local builds remain unsigned unless `SHALE_WINDOWS_SIGNING_REQUIRED=true`. Production signing additionally requires
`SHALE_SIGNTOOL_PATH`, `SHALE_SIGN_TIMESTAMP_URL`, `SHALE_SIGN_EXPECTED_SUBJECT`, and either
`SHALE_SIGN_CERT_THUMBPRINT` or `SHALE_SIGN_CERT_PATH` (with optional secret `SHALE_SIGN_CERT_PASSWORD`). Secrets and
certificates are supplied externally and are never committed or printed. `sign-windows-artifact.ps1` signs with
SHA-256 plus RFC3161 timestamping, verifies signature, timestamp, and expected publisher, and fails closed. It covers
`Shale.exe`, `ShaleUpdater.exe`, and the final MSI. Executables are signed before ZIP construction; the MSI is signed
before publication; manifest SHA-256 therefore covers final signed ZIP bytes. Developer mode explicitly says the
artifact remains unsigned and is not production-ready.

The payload contains `app/shale-installed-version.properties` with schema 1, canonical version, and `PRODUCTION`
channel. The updater atomically replaces it only after payload application succeeds and before `INSTALL_APPLIED`.
Add/Remove Programs `DisplayVersion` is not authority. This owner-writable file is lifecycle evidence, not sufficient
trust for privileged execution; protected registration, canonical/reparse-safe paths, and Authenticode are also
required.

## Standard-user MSI registration remediation — 2026-10-21

This section supersedes the Phase 13F elevated-install requirement. The historical acceptance of
1.0.128/1.0.129 was for an administrator-owned installation; it did not prove a standard employee could install.
The 1.0.135/1.0.136 failures expose the design conflict: `--win-per-user-install` produces a limited-privilege MSI,
but deferred `Impersonate="no"` actions attempted protected HKLM writes. That flag does not grant elevation.
The obsolete writer also applied machine ACLs, kept UUID lookup/snapshot state, and implemented custom rollback.
Its decoded source contains those operations, not an identified download or persistence payload. This review does
not classify Defender's `Trojan:Win32/Commando.A!ml` detection as a false positive.

### Scope, discovery, ownership, and lifecycle

The MSI remains per-user under `%LOCALAPPDATA%\Shale`. Normal install, repair, upgrade between corrected builds,
and uninstall require no administrator terminal. The jpackage template retains its native preprocessor instructions,
ProductCode/UpgradeCode/version, and limited compiled scope. A fixed-GUID x64 component selected by the existing
Feature writes only these values at `HKCU\SOFTWARE\Shale\Desktop`:

| Value | Content |
| --- | --- |
| schemaVersion | DWORD 2 |
| scope | perUser |
| ownerSid | MSI UserSID |
| installRoot | MSI INSTALLDIR |
| supportRoot | MSI LocalAppDataFolder + Shale |
| upgradeCode | MSI UpgradeCode, stable product-family identity |

The component's `installRoot` value is its HKCU key path. Native WriteRegistryValues/RemoveRegistryValues and MSI
transaction rollback own its lifecycle; it is neither permanent nor never-overwrite. The component GUID and key
remain stable across corrected MSI versions. Uninstall removes only MSI-owned values, not the whole Shale key or
user state. Separate Windows accounts have separate hives and per-user MSI product registration. Side-by-side
same-account versions of the same product family remain unsupported.

Schema 2 is user-writable discovery data, **never privileged execution authority**. It does not replace, import,
or relabel a legacy protected schema-1 UUID. The legacy HKLM reader and `ShaleRegistrationDiagnostic.exe` remain
read-only compatibility diagnostics and never fall back to HKCU as trusted authority. Current launch/update code
uses `DesktopInstallLocator` and `app\updater\ShaleUpdater.exe` (plus its existing legacy fallback), not either
registry record. Manual and authenticated session-only automatic ZIP updates, owner lock/attempt paths, machine
UUID, preferences, and remembered sign-in storage are unchanged. ZIP updates do not update MSI ProductVersion or
registration; installed-version metadata remains the ZIP version evidence. Logged-out updates stay UNSUPPORTED.

### Legacy migration gate

MSIs 1.0.128 through 1.0.137 contain their own encoded elevated actions. A new MSI cannot rewrite the cached old
package: automatic RemoveExistingProducts would execute that old code, including commit cleanup. A native
UpgradeVersion/LaunchCondition therefore stops a detected legacy related product before removal, even if the new
MSI was started elevated. The migration is explicit:

1. Close Shale and preserve settings, machine identity, remembered sign-in, and update-attempt state through the
   organization's normal backup process. Record the legacy ProductCode/installation UUID and exact registered paths.
2. Have the **same Windows owner**, if a split-token administrator, uninstall the old MSI with administrator consent.
   A standard employee must involve IT; do not run the new per-user installer using another administrator's account.
   If the old uninstaller is blocked by Defender, stop and use an organization/Microsoft-supported remediation
   procedure. Never disable protection, add exclusions, or blindly delete cached MSI/registry state.
3. Verify only the intended old product is gone. Install the corrected MSI from the employee's ordinary session.
   Confirm user state survives and HKCU registration is correct. Do not claim legacy UUID continuity into schema 2.

Existing HKLM Installations/InstallerState values are never written or removed by the corrected installer. Any
stale record remains for inspection; it cannot authorize an executor because no logged-out executor exists.
Legacy removal/cleanup is an explicitly privileged operator task, separate from normal per-user installation.
Supported corrected-build upgrades preserve the HKCU component identity and owner paths. Historical EXE and
older-than-1.0.128 migrations require Windows validation; they are not accepted merely from source inspection.

### Execution and payload security

Both encoded installer implementations were removed, including the stray `DELETE windows_msi_registration.py` copy.
No registration/install/rollback/uninstall script, `iex`, decoded Base64 execution, execution-policy bypass,
or interpolated PowerShell command remains in the MSI. Registry values are MSI data, never evaluated commands.
The old single-quoted PowerShell interpolation of paths also allowed apostrophes to escape source literals;
removing the interpreter removes that injection boundary. No security setting or global ACL is weakened.
Defender-enabled acceptance remains required; transparent native registration alone cannot guarantee antivirus approval.

`windows_runtime_input.py` now stages only the exact desktop JAR, runtime dependency JARs in `lib`, the packaged
updater with its runtime/legal notices, version metadata, and explicit root license/notice files. Windows native
DLL and toast marker are then added deliberately. Both desktop ZIP/app-image and MSI use this curated stage rather
than Maven `target`. Native toast resources are staged before the ZIP image as well as the MSI. Updater staging
already selected its shaded JAR; its runtime is preserved. Validation rejects known development directories and
source/test/Javadoc JARs in staged trees, app images, generated WiX, and decompiled final MSI installed paths,
including short|long MSI names. The final File table, not opaque Dark extraction filenames, defines installed paths.

The build still supplies one customized original JDK 21 `main.wxs` to jpackage, recompiles only `bundle.wxf` for
shortcut identity, compares preliminary/final product identity, and validates the final payload before finalization.
No ICE03 suppression, package identity replacement, broad registry ACL, or extra privileged helper is introduced.
Build contracts are deterministic; byte-for-byte MSI reproducibility has not been established.

### Signing and release boundary

Existing external-certificate Authenticode support remains optional for development and required when
`SHALE_WINDOWS_SIGNING_REQUIRED=true`. Release scripts no longer force ExecutionPolicy Bypass. The signing script
runs as a visible `-File` invocation subject to the build machine's normal policy; provision approved scripts/policy
on that machine if required. Secrets remain external. In addition to app-image launchers, the exact fresh jpackage
Shale, diagnostic, and updater executable sources consumed by final Light are signed with literal argument arrays
before relinking; this closes the former gap where the ZIP launcher was signed but MSI regenerated an unsigned one.
The final MSI is signed afterward, before finalization. No signing credentials are needed for local development.

Use `Get-AuthenticodeSignature` and `signtool verify /pa /all /v` on the final MSI and installed executables; production
acceptance requires Valid status, expected publisher, and timestamp. Unsigned development artifacts may show unknown
publisher/SmartScreen prompts. Signing authenticates the publisher; it does not guarantee reputation or clear Defender
findings. Production signing and Defender acceptance have not run in Codex Cloud.

Build a **new version above 1.0.137**, produce a new MSI and matching desktop ZIP, and complete the
[current acceptance matrix](testing/windows-installed-registration-phase13f.md#standard-user-remediation-acceptance--2026-10-21).
Do not republish an existing immutable release or use the ZIP alone to repair MSI registration. This installer-only
delta requires desktop distribution, no API/server deployment, no SQL migration, and no audit schema change.
