# Case Updates workspace acceptance and deployment

## Deployment requirements

This is a desktop Java/core/data change. No new SQL migration, REST endpoint, Azure
Function, PubSub envelope, API server deployment, or duplicate storage is required.
Existing installations must already have the documented `CaseUpdates` lifecycle
columns and `RowVersion`, and the normal tenant-stamped runtime/RLS configuration.
This task does not execute production SQL, deploy an API, publish a desktop update,
or change authentication/permission-retirement work.

An owner-approved desktop release can package the changed desktop modules through
the normal release workflow after acceptance. No automatic release is part of this PR.

## Test impact and automated evidence

The pre-edit inventory covered Case Update DAO query tests, CaseServiceAdapter tests,
CaseUpdatesNarrativeEditorTest, medical-record safeguard tests, shared enhanced/rich
editor tests, Case section navigation/lifecycle, and Case Overview update-rail design
contracts. Existing constructors/API readers remain compatible. Obsolete exact
whitespace/CSS and scrollbar-arithmetic assertions in the touched navigation suite
were replaced with current resource tokens and keyboard/section behavior, following
`architecture/codex-prompt-rules.md`.

New `CaseUpdatesPagingJdbcTest` executes the production adapter/gateway/DAO and checks
filter combinations, literal Unicode/wildcard search, inclusive dates, all sort modes,
case/tenant bindings, deterministic seek cursors/lookahead, unavailable actors/cases,
admin-only deleted access, metadata-only authors, creator/version mutation predicates,
and atomic PHI audits including rollback. These are JDBC doubles, not proof of live
SQL Server execution plans, database collation, or deployed RLS policy effectiveness.

`CaseUpdatesWorkspaceTest` covers filter/reset combinations, bounded page navigation,
late response rejection, session/navigation invalidation, distinct states, semantic
controls in both themes, and committed-save synchronization of tab and rail.
`CaseUpdateEditorTest` exercises actual dialog buttons/close protection, blank input,
single in-flight Save, create/edit initialization, retained drafts after failure,
Keep Editing/Discard, and cancellation without persistence. Shared editor compatibility
and existing navigation tests remain part of focused verification.

## Manual desktop acceptance checklist

Run with ordinary and administrator users against a disposable non-production case
with more than 100 updates, duplicate timestamps, multiple authors, long/formatted
notes, and a few soft-deleted rows. Use two desktop instances for concurrency/live tests.

- [ ] Open Case > Case Updates directly and via the rail's View All Updates; restore
      `CASE_UPDATES` through back navigation. Verify only the selected center root is
      active. Return to sibling tabs and confirm the existing right rail/composer.
- [ ] Verify newest-first default; oldest and recently edited sorts; stable ID order
      for ties; Previous/Next on first, intermediate, last, and empty pages.
- [ ] Search text, Unicode, literal `%`, `_`, `[`, and no-match terms. Combine creation
      dates, inclusive through date, author, lifecycle, and each sort. Clear all filters.
      Invalid/reversed dates must not run a query; rapid typing must not show an old page.
- [ ] Ordinary users see only active notes and cannot edit another author's note.
      Administrator Active/Deleted/All includes labelled read-only deleted notes.
      Test unavailable historical authors and case/tenant boundaries under runtime RLS.
- [ ] Create in the tab, edit through the rail, then reverse the entry points. Verify
      both surfaces refresh without reopening the case and preserve current filters.
      Check the existing medical-record keyword prompt only after a committed create.
- [ ] View/copy a long full update. Verify the card preview remains compact and that
      author/created/edited metadata and available actions are correct.
- [ ] Resize the shared create/edit/expanded composer near 900 × 650, on a smaller
      screen and at desktop scaling. Confirm wrapping, readable multiline text, and
      persistent Save/Cancel (Apply/Cancel for isolated form expansion).
- [ ] Modify text/formatting then Cancel, Escape, and X. Keep Editing retains the
      draft; Discard saves nothing. Ordinary Enter inserts a line; Ctrl+Enter saves.
      Repeated Save while busy performs one write.
- [ ] Simulate save/load failures. Editor draft remains available; refresh retry
      recovers; a committed save followed by refresh failure is not reported as a
      failed save. Inspect PHI audit and parent last-updated behavior in test data.
- [ ] Edit the same update in two instances. The later stale save must retain its
      draft and report conflict; it must not overwrite the first committed change.
      Remote creates/edits and reconnect refresh both surfaces without changing open drafts.
- [ ] Switch case/session while reads are pending; verify no late case/tenant content.
- [ ] Review Light and Dark at wide/narrow widths: wrapping filter rows, cards, selectors,
      author picker, full viewer, editor, focus/disabled states, and readable state text.

Installed Windows/macOS, live SQL/RLS, real PubSub delivery, DPI, and physical
multi-monitor acceptance remain manual; local synthetic JavaFX/JDBC checks do not
claim those environments were exercised.
