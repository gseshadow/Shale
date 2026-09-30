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
