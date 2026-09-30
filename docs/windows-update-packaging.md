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
