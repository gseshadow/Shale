# Phase 13C installed-Windows activation validation

**Status:** NOT RUN — required before Phase 13C can be marked complete.

Use a normally installed Phase 13C build in an ordinary, non-elevated Windows desktop session. Use the development
injected-clock/test seam in an automated or diagnostic build; do not ship a user-visible “run automatic updater now”
control and prefer a dry-run handoff seam over applying a real package. Record build/version, Windows version, local
zone, timestamps, and sanitized scheduler decisions.

1. Disabled preference: enter a simulated eligible window and confirm `DEFER_PREFERENCE`, no attempt, no process.
2. Enabled plus recent foreground input: confirm active-user deferral.
3. Enabled plus each representative open mutation and a registered save: confirm readiness deferral.
4. Enabled, idle, background, fresh eligible policy/package, writable ZIP installation, and `READY`: confirm both
   evaluation and immediate recheck reach the dry-run handoff seam.
5. Inspect the generated process command and confirm exact `--invocationMode UNATTENDED`.
6. Hold the shared execution lock and confirm no Phase 12 attempt or updater process; release it and confirm one
   process can cross the seam.
7. Confirm the unattended path executes no `taskkill /F`, prompt automation, UAC bypass, discard, auto-save, or reboot.
8. With automatic preference disabled, scheduler stopped, and outside the window, separately confirm manual Update
   still uses `MANUAL` and behaves as before.
9. Log out/switch user and confirm the scheduler thread/callback stops; run a captured old callback and confirm it is
   inert. Repeat shell initialization and confirm only one scheduler exists.
10. Simulate suspend/delay: resume at 03:00 evaluates normally; resume at 09:00 schedules the next local window and
    launches nothing. Confirm the computer is never woken by Shale.
11. Disconnect approved networking briefly and confirm policy/manifest unavailable defers, creates no attempt, and
    remains within the four-evaluation budget. Restore networking immediately.
12. Confirm scheduler status/log/state contains no user/tenant identity, JWT/JTI, activity history, case data, PHI,
    or location, and that Phase 12 startup reconciliation—not the scheduler—records completion.

Also capture `Get-AuthenticodeSignature` for both executables. Expected current result is `NotSigned`; record it as
known hardening debt, not a Phase 13C signing pass. Do not test logged-out execution, Task Scheduler, a service,
SYSTEM, stored credentials, wake timers, macOS scheduling, MSI automation, or reboot behavior beyond confirming none
was introduced.

Phase 13D subsequently assessed those logged-out/background choices and selected `UNSUPPORTED`; it did not change
this checklist or the Phase 13C scheduler. See `windows-logged-out-update-phase13d.md`.
