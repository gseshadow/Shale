# Authenticated Light/Dark presentation review

Branch `codex/authenticated-theme-consistency`, fetched live base
`d0d43655765b74e0c1e83ecefe59451307e7b749`. Original `/workspace/Shale` work branch and files are
preserved in place; implementation is in a separate worktree. The earlier Case-only prompt is superseded.
No earlier Case-only local branch/worktree or open matching theme PR was found during status inspection.
No merge or deployment. Appearance is provisional, not final aesthetic approval.

**Current acceptance update (2026-10-12):** [R4 desktop record](r4-desktop-acceptance.md) records
the user's report that pulling merged #1859 and restarting the local frontend corrected Dark on all
pages, plus the reported workspace keyboard, actual Firefox zoom and limited Narrator passes.
These are user-reported localhost → Azure API observations; the matrix below remains synthetic.

## Ownership and scope

Styles load in App.tsx as beta styles → shared tokens/buttons → shell/authenticated bridge → shared
presentation; native discard-dialog CSS is loaded by its component. AppShell formerly selected My Shale
by a literal pathname check and otherwise installed `legacy-route-content`: root beta variables, fixed
`#172033` foreground, fixed Light native `color-scheme`, and a pale outer frame. R3's internally themed
workspace/Overview were still placed inside that forced-Light frame. Beta styles also owned hard-coded
white card gradients, translucent-white detail headers, pale editors/status choices, and generic errors.
Typed beta input selectors outranked the shared `:where(input,select,textarea)` rule.

AppShell now opts every existing authenticated outlet into `shale-route-content shale-presentation`,
with `data-route` from the existing registry matcher (including case-insensitive/trailing-slash routes).
There is no theme-specific routing or new route. The shell remains the single session theme owner;
My Shale and R3 keep their existing shared components. Authenticated compatibility variables map retained
beta class names to established Shale tokens. Scoped detail/section/editor rules replace fixed Light
paint; shared presentation owns card/text/field/button/feedback state paint. Primary gradients remain
restricted to shared actions/navigation. Selected status cards use semantic selection/focus while stored
colors remain narrow accents. Color swatches/status rails use the existing validated indicatorPaint with
neutral fallback; dynamic colors never paint a whole card or selector shell.

The native select and option styles inherit shell color-scheme and semantic input/text tokens. Native
modal dialogs remain descendants of the themed editor when promoted to the top layer. No React portals
or additional dialog/editor libraries exist in the inspected operational surfaces. OS-native picker
internals remain browser/platform-owned; no popup replacement was added.

No request, search/filter/page, navigation, mutation, validation, RowVer/value update, dirty/pending,
session generation, safe-return, or audit behavior changes. Settings currently has profile/tenant and
read-only administrative panels; no Settings editing capability is invented. Unavailable destinations
remain unavailable. The existing beta Light CSS remains for unauthenticated/compatibility contexts;
no global inversion, global theme recolor, !important patch or duplicated Dark stylesheet was introduced.

## Test impact

| Changed production | Existing contracts inspected and maintained |
| --- | --- |
| App.tsx wrapper, remaining native Case actions and data-color presentation | App.test all-route/theme/no-refetch/authoritative actions; existing My Shale-only assertions updated; routeRegistry, returnPath, shell and preview suites retained. |
| shell/authenticated.css | Actual-App synthetic full-page route/editor/state evidence; shared primitives and shell tests retain behavior and semantics. |
| ui/presentation.css, ContactValueInput.tsx feedback class | Shared primitive fields/actions/feedback and ContactValueInput tests; actual-App CSS cascade/native-control/dialog review. |

Contact and Organization draft tests retain retain/set/clear, opening timestamp/RowVer, failed/pending
save, dirty/unload, native-modal focus and security bypass. Case workspace/client tests retain audited
Overview request ownership, paging/search/Back memory, rejection and no replay/refetch. All session,
credential, login/startup/logout/return tests remain meaningful. Theme-ownership assertions test the
actual route heading/editor's ancestor and continuity through theme changes, rather than mirroring CSS
values. Browser evidence complements jsdom, which does not evaluate the actual CSS cascade.

## Synthetic route/editor matrix

[Fixture interception](theme-evidence/fixtures.cjs) fulfills or aborts every API call locally, including
synthetic mutation errors/pending responses. It never forwards an API request. No real directory/case data,
live credentials, backend mutations or live acceptance captures were used.

| Main routes (all in both themes at 320/360/768/1280) | Reachable existing surfaces |
| --- | --- |
| My Shale `/my-shale` | Assigned case/task cards, Complete pending/failure/disabled preserved by existing tests and prior synthetic evidence. |
| My Tasks `/tasks`, Task Detail `/tasks/12` | Task list, completed/open metadata, Task editor and native Priority/Assigned selects. |
| Cases `/cases`, Case Detail `/cases/1` | New case, core/date/summary editor, assignment, status radio selection/history, Add task, Add update, related Contacts. |
| Case workspace `/case-workspace`, Overview `/case-workspace/1` | Assigned/search controls, results, limited Overview, reread/Back/error/empty/loading. |
| Contacts `/contacts`, Contact Detail `/contacts/7` | New/Edit Contact, phone/email feedback/extensions, checkbox and discard dialog. |
| Organizations `/organizations`, Organization Detail `/organizations/7` | New/Edit Organization, phone/fax/email/extensions, related Case cards and discard dialog. |
| Team `/team`, Member `/team/1` | Directory filter, profile, badges, invalid stored-color fallback. No editor currently exists. |
| Settings `/settings` | Current profile, tenant info, admin Status/Practice Area cards, non-admin unavailable panel. No Settings mutation form currently exists. |

Main-route fixtures additionally cover loading, empty/unavailable and safe-error states at 360 in both
themes; forms cover intercepted pending/disabled and server validation states. Focus is exercised with
native keyboard input, not CSS focus injection. Full-page screenshots include shell, frame and page ends.
The executed evidence and retained full-page review set are linked below. No real device, real screen-reader speech, other-engine or real-host acceptance is implied.

## Local retest

From the repository root, with Node matching the lockfile and JDK 21/Maven available:

```bash
npm ci --prefix shale-web
npm test --prefix shale-web -- src/App.test.tsx src/app/routeRegistry.test.ts src/ui/primitives.test.tsx
npm test --prefix shale-web -- src/contactDraftApp.test.tsx src/organizationDraftApp.test.tsx src/caseWorkspaceApp.test.tsx src/ContactValueInput.test.tsx
npm test --prefix shale-web
npm run typecheck --prefix shale-web
npm run build --prefix shale-web
npm audit --prefix shale-web
npm audit --prefix shale-web --omit=dev
python3 -m unittest discover -s build/test-selection -p 'test_*.py'
python3 build/test-selection/select_tests.py --base d0d43655765b74e0c1e83ecefe59451307e7b749 --head HEAD --format markdown --plan-output /tmp/shale-theme-selection.json
python3 build/test-selection/run_selection.py /tmp/shale-theme-selection.json --command affected
mvn test
git diff --check d0d43655765b74e0c1e83ecefe59451307e7b749...HEAD
```

Start Vite in another terminal:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
```

Use an already available Playwright installation (no application dependency changes):

```bash
PLAYWRIGHT_MODULE=/absolute/path/to/playwright node shale-web/docs/theme-evidence/browser-review.cjs
PLAYWRIGHT_MODULE=/absolute/path/to/playwright node shale-web/docs/theme-evidence/zoom-review.cjs
node shale-web/docs/theme-evidence/contrast-review.cjs
PLAYWRIGHT_MODULE=/absolute/path/to/playwright node shale-web/docs/theme-evidence/paint-review.cjs
PLAYWRIGHT_MODULE=/absolute/path/to/playwright node shale-web/docs/theme-evidence/controls-review.cjs
```

`REVIEW_ORIGIN` selects a local Vite origin; `BROWSER_EXECUTABLE_PATH` selects Chromium;
`EVIDENCE_DIR` selects output captures. Zoom uses a disposable profile selected by `ZOOM_PROFILE`.
Inspect the full images, not only interiors/thumbnails. For manual review without API access, use the
isolated `foundation.html` preview; operational synthetic review requires the interception harness.
Any separately authorized live retest must use approved nonsensitive fixtures and the existing R2/session/
CORS/audit prerequisites, never a guessed default API readiness or real-data screenshots.

## Executed results

- Focused App/route/shared primitives: **123 passed**. Final modified Contact/Organization/Case workspace
  tests: **136 passed** (including both themes' modal ownership), plus ContactValueInput coverage in the
  full suite. Final full web: **642 passed / 18 files**. Final feedback/Contact/Organization focused run: **127 passed**. Typecheck and production build passed; full/production npm audits report zero vulnerabilities.
- Selector unit tests: **24 passed**. Commit-based change selector maps frontend consumers to server
  bearer compatibility: **AuthControllerTest 10 passed**, no full-suite escalation. Subsequent critical
  `mvn test`: **116 passed**, zero failures/errors/skips. The selected bearer area is relevant because
  authenticated route composition must retain its established identity/authorization boundary.
- `python3 build/test-selection/validate_ui_resources.py` and `git diff --check` passed. No Java resources
  changed; the resource validator is a supplemental repository check. The optional historical full Maven
  suite was not rerun; prior R1/R2 advisories recorded in [R3 review](r3-review.md) remain inherited,
  including the 12 data failures and three ApiExceptionHandlerTest failures. They are not new passes.
- Chromium **151.0.7922.173**: **200** full-page route/editor captures (14 main routes + 11 editors × four
  widths × both themes), **84** main loading/empty/error captures, **44** editor pending/disabled/validation
  captures, **four** discard-dialog captures. No horizontal page overflow or page errors. Native keyboard
  focus/selected status and dialog Escape/Keep focus passed. Controls review adds **12** captures for
  completion pending/failure, non-admin Settings and native selects. API interception never forwards reads
  or writes; populated runs and zoom make zero mutations, state runs use intercepted synthetic failures.
- Native Chromium Settings zoom: **100** scenarios (all 25 routes/editors × 200%/400% × both themes),
  fixed physical 1280×900 viewport, DPR 2/4 and CSS widths 640/320. No page overflow; editor focus remains
  visible. CDP exports full pages at CSS resolution without changing the native zoom under review.
- **1,610** actual solid text/ancestor-background contrast checks across 50 route/editor/theme scenarios
  pass, with no non-action card/header/editor gradients. **84** shared semantic text/focus/input/validation
  boundary pairs pass. Action pseudo-planes/gradient endpoints are checked in the token audit rather than
  falsely treated as transparent backgrounds by the computed-text review. Disabled text is exempt from
  contrast thresholds but is retained in visual state captures. Dynamic indicator foreground/fallback
  behavior remains protected by existing shared primitive tests and the populated fixtures.
- Native selects/options inherit selected color-scheme and matching input/text paint. Native keyboard
  selection works without a mutation. Headless full-page screenshots capture the surrounding field/focus,
  not OS popup internals. No portals exist; both native top-layer dialogs retain themed DOM ownership.
- Untouched fetched-base synthetic captures reproduce the defect: Dark shell, **Light** native scheme,
  `rgb(238,243,248)` outer frame and white card gradient. This establishes a before/after against the same
  fixture rather than relying only on the user's original report.

[Retained screenshot index](theme-evidence/screenshots.md), [route/editor ownership observations](theme-evidence/captures/matrix-observations.json),
[state observations](theme-evidence/captures/observations.json), [native zoom](theme-evidence/zoom/observations.json),
[computed contrast](theme-evidence/paint-observations.json), [token contrast](theme-evidence/contrast-observations.json),
[native controls](theme-evidence/controls/observations.json) and [untouched-base evidence](theme-evidence/before/observations.json)
are review artifacts. The original theme review reported a full **448-image** synthetic bundle; that
archive is absent from the R4 live base, so no repository download link is claimed. The repository
retains a smaller full-page set to avoid storing every repeated state/zoom image. Scripts regenerate the
complete matrix and observation filenames locally. A full run produces 332 capture records; a state-only
resume produces 132, with the separate 200-record matrix unchanged.

**Not performed by the theme implementation task:** physical phone/tablet virtual keyboard/safe-area
acceptance, real screen-reader speech, Firefox/WebKit/other-engine rendering and hosted acceptance.
Later user-reported Windows Firefox/Narrator and keyboard passes are scoped in R4; they do not expand
this synthetic matrix or accept every theme/editor/device/AT state. Remaining coverage stays open;
appearance remains provisional. No phase is closed by these synthetic checks or the bounded reports.

## Audit, R4 reports and rollback

Existing schema/audit integration stays unchanged. Presentation/theme/staging mechanics intentionally
add no audits, new sensitive reads or domain/admin mutations. Required server Overview audit and explicit
reread ownership remain; cancellation is not claimed to undo a committed audit. Existing mutation owners
and broader roadmap §8.2 audit gaps remain untouched; no migration or audit enhancement is claimed.

The [R4 record](r4-desktop-acceptance.md) consolidates the earlier functional/audit reports with the
later corrected Dark and workspace keyboard/focus, actual Firefox zoom and limited Narrator report.
The earlier theme defect and unreported-keyboard status are superseded for that tested desktop flow.
Remaining release/deployment gates are retained there. Phase 2 acceptance OPEN, Phase 3 IN PROGRESS,
Phase 4 INCOMPLETE with desktop acceptance recorded. Next work prepares hosted delivery, not a repeat
of completed desktop checks or an implicit deployment.

Rollback reverts this PR's frontend presentation/tests/documentation only, or rebuilds base `d0d43655`
with the same API-origin setting to recover the previous assets (including the known theme defect).
No backend, SQL, schema, auth, dependency, audit-history or deployment rollback is required. Preserve
R1/R2/R3 and the ordinary-build Overview fix, session/security contracts and all persisted audits.
