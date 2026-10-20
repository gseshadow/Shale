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
      Include local-day boundaries across a daylight-saving transition.
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

## Local verification record — 2026-10-20

Java 21 and Maven 3.9.11; JavaFX ran on an Xorg dummy display (`DISPLAY=:99`)
with a writable native-library cache. Focused feature tests, 25 neighboring Case View
checks, static FXML/CSS validation, and Git diff checks passed. The final generated
affected suite passed **122 tests**, and the full default critical reactor passed
**116 tests**, with zero failures, errors, or skips. Full-suite escalation was not
selected. Synthetic Light/Dark workspace previews using the production update cards
were inspected. Installed desktop/live SQL/PubSub acceptance remains the checklist above.

Cases/UI checks cover the shared roots, rail, dialogs and data boundary. The selector
also chose Contact/Organization/Task adapters, reports/FXML and server smoke checks
because the shared core DTO/service port has direct consumers in those areas. No
authentication or permission-retirement implementation was changed. Neighboring test
maintenance also corrected pre-existing assumptions about selected occurrence identity
and the shared date-row/Parties-section presentation owner, verified against the base.

Exact verification commands (after selecting Java/Maven in the runner):

```bash
python3 build/test-selection/validate_ui_resources.py
python3 build/test-selection/select_tests.py --base 14d8d813a369a8170e664a0df690b6043902185e --head HEAD --format markdown --plan-output work/selected-plan.json
DISPLAY=:99 mvn -q -pl shale-ui -am -Djavafx.cachedir=/workspace/Shale/work/javafx -Dshale.preview.dir=/workspace/Shale/work/previews -Dtest=CaseUpdateQueryTest,CaseUpdatesPagingJdbcTest,CaseDaoCaseUpdatesQueryTest,CaseServiceAdapterTest,CaseUpdatesNarrativeEditorTest,CaseControllerMedicalRecordsSafeguardSourceTest,CaseSectionNavigationSelectionTest,CaseMaterialsPhase4UiContractTest,CaseOverviewPhase6SupportingComponentsTest,EnhancedTextAreaTest,ExpandedTextEditTest,RichTextExpandedEditorTest,AppDialogsPhase8JTest,CaseUpdatesWorkspaceTest,CaseUpdateEditorTest -Dsurefire.failIfNoSpecifiedTests=false test
DISPLAY=:99 mvn -q -pl shale-ui -am -Djavafx.cachedir=/workspace/Shale/work/javafx -Dtest=CaseCalendarTabSourceTest,CaseRequestsLifecycleRegressionTest,CaseOverviewPhase2ContractTest,CaseOverviewPhase2CDesignContractTest,CaseOverviewPhase2DDesignContractTest,CaseDatesPhase2CArchitectureTest,MedicalRecordsRequestedCaseUpdateSafeguardTest -Dsurefire.failIfNoSpecifiedTests=false test
DISPLAY=:99 mvn -q -pl shale-ui -am -Djavafx.cachedir=/workspace/Shale/work/javafx -Dtest=CaseUpdatesWorkspaceTest -Dsurefire.failIfNoSpecifiedTests=false test
DISPLAY=:99 mvn -Djavafx.cachedir=/workspace/Shale/work/javafx -pl shale-core,shale-data,shale-server,shale-ui -am -Dtest=com.shale.core.dto.CaseUpdateQueryTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.service.adapter.CaseServiceAdapterTest,com.shale.data.service.adapter.CaseUpdatesPagingJdbcTest,com.shale.data.service.adapter.OrganizationServiceAdapterTest,com.shale.data.service.adapter.TaskServiceAdapterTest,com.shale.server.controller.AuthControllerTest,com.shale.ui.component.CaseUpdateEditorTest,com.shale.ui.component.EnhancedTextAreaTest,com.shale.ui.component.RichTextExpandedEditorTest,com.shale.ui.controller.CaseControllerMedicalRecordsSafeguardSourceTest,com.shale.ui.controller.CaseMaterialsPhase4UiContractTest,com.shale.ui.controller.CaseOverviewPhase2CDesignContractTest,com.shale.ui.controller.CaseOverviewPhase2ContractTest,com.shale.ui.controller.CaseOverviewPhase2DDesignContractTest,com.shale.ui.controller.CaseOverviewPhase6SupportingComponentsTest,com.shale.ui.controller.CaseSectionNavigationSelectionTest,com.shale.ui.controller.CaseUpdatesNarrativeEditorTest,com.shale.ui.controller.CaseUpdatesWorkspaceTest,com.shale.ui.controller.OrganizationFxmlLoadTest,com.shale.ui.controller.ReportsControllerLifecycleTest,com.shale.ui.controller.SettingsFxmlLoadTest,com.shale.ui.util.SemanticControlCssContractTest -Dsurefire.failIfNoSpecifiedTests=false test
DISPLAY=:99 mvn -Djavafx.cachedir=/workspace/Shale/work/javafx test
git diff --check
```

## Multi-author and delete follow-up acceptance

This follow-up uses the same desktop deployment requirements above: **no new SQL migration or
API deployment**. Existing `IsDeleted`/`DeletedAt`/`DeletedByUserId`/`RowVersion` and PHI AuditLog
remain prerequisites. No SQL, API, authentication, permission-retirement or release configuration
is changed. The obsolete unused unversioned DAO delete now throws; desktop callers must use
the new versioned command. Deleted records remain administrator-only and read-only.

Manual Windows checklist (non-production case, ordinary author, unrelated editor/user, tenant admin,
and two desktop instances):

- [ ] Open Change and select two or more authors by clicking different parts of their full-width
      checkbox rows. Both stay checked; clicking a selected row deselects only that author.
- [ ] Search so some checked authors are hidden, clear search and verify the full checked set.
      Apply, reopen Change, verify all applied selections; modify then Cancel/Escape/X and verify
      no filter/query changes. Check one-name/count/All authors summary, Remove and Clear filters.
- [ ] On a later page, apply multiple authors and verify page one, OR author semantics, AND search,
      creation dates, every sort and administrator Active/Deleted/All. Verify Next/Previous and
      displayed page counts after filtering or removing the last result on a page.
- [ ] Tab through rows; Space toggles only the focused row without applying the dialog. Check
      accessible name/checked state with a screen reader, hover/focus distinctions, Light/Dark,
      scrollable long author lists, small screens and Windows scaling; search/footer stay reachable.
- [ ] Verify Delete in both workspace and quick rail for the original author and current same-tenant
      administrator. An unrelated user or editor cannot see Delete or acquire authority through editing.
      A deleted update has View only. No restoration action is introduced.
- [ ] Cancel/Delete confirmation: confirm author/time and removal-from-normal-history explanation.
      Cancel, Escape and X make no changes; Enter must not default to destructive confirmation.
- [ ] Confirm deletion; verify authoritative removal from active history and counts, unchanged filters,
      page-one reset, and sidebar/workspace synchronization. Reopen the Case and inspect admin deleted
      history: content, author/creation/edit metadata remain; deletion user/time and PHI audit are present.
- [ ] Edit/delete the same update from another instance before confirming; stale deletion must fail
      without overwriting. Repeated deletion, missing records and crafted tenant/case/user requests
      must not expose another tenant's content. Revoke an administrator's role before deleting and
      verify the DAO uses current membership.
- [ ] Interrupt persistence/audit in disposable test infrastructure: failure leaves the update active,
      no optimistic removal; refresh/retry recovers. Successful deletion emits only the existing
      tenant/case invalidation; second instance/reconnect reloads with current filters retained.

The automated follow-up inventory maps `CaseUpdateQuery` to its immutable-set/version tests;
CaseDao/service/gateway to `CaseUpdatesPagingJdbcTest` (including existing binding combinations);
shared picker changes to the existing `UserSelectionFieldTest`/`UserSelectorSourceContractTest` plus
`UserSelectorMultipleSelectionTest`; workspace, paging and staging to `CaseUpdatesWorkspaceTest`;
shared card visibility, real confirmation/cancel, persistence and rail/tab refresh to
`CaseUpdateDeletionTest`. Narrative/editor, Case navigation, adapter and semantic CSS consumers
remain focused/selector regressions. Native Windows, screen-reader, deployed SQL/RLS and physical
PubSub delivery remain manual checks; JDBC doubles do not execute SQL Server.
