# Phase 13F installed-Windows registration acceptance

**Status on 2026-09-30:** NOT RUN. Platform-neutral contracts were tested on Linux; no installed Windows/WiX host or
release signing credential was available. Do not report these rows as PASS until commands are executed and evidence
retained.

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
