# Phase 13B installed-Windows feasibility checklist

**Current status:** not executed. Phase 13B does not register or launch an unattended scheduler. Items that require
an actual handoff remain acceptance criteria for a later, explicitly activated Windows prototype; they are not
claims about the current build.

Use a per-user MSI installation owned by the Windows user running Shale. Never provide a Windows password, Shale
password, JWT/JTI, or session secret to a task/helper.

1. Inspect Task Scheduler and prove no Phase 13B task was silently registered by install, upgrade, startup, or preference change.
2. With preference disabled, invoke the evaluation harness and confirm `DEFER_PREFERENCE` and no Phase 12 file.
3. Enable the preference through Phase 13A; confirm the evaluator rereads the provider (do not edit its file).
4. With no newer exact Production target package, confirm `DEFER_NO_UPDATE` or `DEFER_UNAVAILABLE` and no attempt.
5. With foreground activity less than 30 minutes ago, confirm `DEFER_ACTIVE_USER`.
6. With any active safe-work lease, save/mutation in flight, dirty/uncertain editor, or visible foreground window, confirm deferral.
7. At 02:00, inside the window, and immediately before 04:00 local time, exercise eligibility; at 04:00 confirm deferral.
8. Repeat across Windows DST spring-forward/fall-back and after a timezone change; confirm local calendar evaluation and bounded counters.
9. Lock the session and confirm lock alone never overrides activity, work, shutdown, policy, package, or lock checks.
10. Do not test logged-out execution as supported: it is explicitly unsupported by this foundation.
11. Disconnect networking; confirm authority/package unavailability defers without a Phase 12 attempt and retries no more than four times at 30-minute spacing.
12. Confirm a real terminal updater failure permits no second handoff in the same local window; a new local date/time-zone window resets counters.
13. Before activation, add and validate one shared cross-process lock against manual versus unattended, two evaluators, and another Shale process; only one handoff may win.
14. Before activation, replace force termination with cooperative shutdown; dirty/blocked shutdown must defer without an unseen prompt.
15. At the eventual handoff seam, confirm exactly one Phase 12 attempt begins and successful startup reconciliation remains unchanged.
16. Confirm no UAC prompt and that the helper runs as the per-user install owner, never SYSTEM. Treat either failure as a blocker.
17. Confirm package SHA-256/ZIP traversal checks remain identical to manual mode; separately verify release Authenticode posture because runtime code does not validate it.
18. Confirm installer reboot-required behavior is recorded without automatically rebooting. (The current ZIP updater does not invoke MSI.)
19. Disable the Phase 13A preference and confirm every future execution-time evaluation defers even if scheduler state is stale.
20. If a task/helper is added later, upgrade twice and prove stable identity/path with no duplicate task; uninstall and prove task/helper/state cleanup.
