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

## Phase 13F elevated registration integration

The existing jpackage/WiX 3.14 stack is retained. The build extracts JDK 21's authoritative
`jdk.jpackage/jdk/jpackage/internal/resources/main.wxs` resource, lists every discovered `main.wxs`, and fails closed
unless that exact Windows MSI resource identity occurs once. Unrelated same-named resources do not affect selection.
The build
uses `windows_msi_registration.py` to add the registration actions without discarding its preprocessor instructions,
and supplies it to the original jpackage build with `--resource-dir`. jpackage therefore compiles it once with its
native implicit/default `Jp*` environment and remains authoritative for ProductCode, UpgradeCode, and version; the
build does not recompile `main.wxs` or reproduce private defaults. Dark output from the preliminary and final MSI is
compared fail-closed for those three identity fields. The package retains `InstallScope="perUser"` and deliberately
has no `InstallPrivileges` attribute: WiX 3.14 rejects the contradictory `perUser`/`elevated` pair. Installation,
repair, upgrade, and uninstall must instead start `msiexec` with UAC elevation by the same split-token Windows
administrator who owns the per-user installation. Deferred non-impersonating `WixQuietExec64` actions then run in
the elevated Windows Installer service context; an unelevated run fails and rolls back rather than silently omitting
registration. MSI `UserSID`, `INSTALLDIR`, and `LocalAppDataFolder` are formatted before deferral and carried as
action data. Consent elevation preserves that user's SID and profile; over-the-shoulder credentials select the
credentialed account and are unsupported. The writer
cross-checks SID/profile/path, rejects visible reparse ancestors, writes only
`HKLM\SOFTWARE\Shale\Installations\<UUID>`, and applies protected explicit ACLs. It never uses the elevated account's
environment as owner authority.

These checks are deliberately layered. Before jpackage, the raw JDK template must retain
`InstallScope="$(var.JpInstallScope)"` and omit `InstallPrivileges`; that stage proves jpackage still owns scope
resolution. After jpackage, generated/decompiled MSI source must contain resolved `InstallScope="perUser"`, still
omit `InstallPrivileges`, and retain every registration lifecycle action and sequence row.

Protected installer state retains the random UUID across upgrade and repair. Major-upgrade removal does not delete
the record; exact uninstall does. Paired rollback actions restore prior state. If both record and protected state are
destroyed, repair creates a documented replacement UUID because the original cannot be safely invented. The payload
remains per-user. Developer MSIs may be unsigned; signing-required builds still fail before publication, and
registration never makes an unsigned updater trustworthy.

Automated repository verification for Phase 13F is complete: the full `mvn test` suite is PASS. Installed-Windows
acceptance remains NOT RUN on the available Linux host, which has no Windows/WiX installation environment or MSI
artifact. Accordingly, no fresh-install, ACL, runtime, upgrade, repair, uninstall, security-fixture, multi-user, or
Authenticode result is inferred from the packaging contracts.
