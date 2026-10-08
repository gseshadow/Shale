# Web V2 Phase 2D: shell and My Shale acceptance review

Review branch `codex/web-v2-phase-2d-acceptance`, based on live `origin/codex/latest`
`fc45437e6519be7e905b1ace90e418cbd49276cc`, fetched explicitly on 2026-10-08.
PR #1841 is merged; `git merge-base --is-ancestor 1b9ad0af65f1ccf04f3a29285126701c88a05cbe origin/codex/latest`
passed, and its My Shale presentation/review/evidence are present. No other PR was merged or used as base.
**Phase 2 IN PROGRESS; acceptance OPEN.** Review publication only; no merge/deployment.

## Findings and targeted fix

Pending Complete previously changed only the disabled button label/`aria-busy`; there was no live-region
update. Success inserted a pre-populated `role=status` span, rather than updating an established region.
`MyTasksList` now mounts one initially empty, polite, atomic status region for My Shale. It updates to
“Completing [task]…” and then “Completed [task].” only after the returned task has `completedAt`.
Failure clears pending status and preserves the existing alert, loaded work, and available actions.
Already-completed facts remain ordinary readable text, avoiding initial per-card success announcements.
This improves the announcement contract; **actual spoken announcements remain unverified**.

No other concrete production defect was established. One script focus check initially ran after pointer
navigation; it now restores keyboard modality before checking `:focus-visible`. Another script compared
an entire URL after the skip link added `#foundation-main`; it now checks pathname. Neither was an app defect.
No cosmetic implementation change was manufactured from those harness failures.

Existing calls, task response merge, fallbacks, IDs, native card navigation, independent Complete actions,
authentication/session handling and single-task pending bookkeeping are unchanged. No optimistic success,
read reload or automatic mutation retry was added. The shared list's `/tasks` presentation/feedback is
unchanged; no other screen is redesigned. `foundation.html`, preview and build entry are retained.

Audit compatibility: no new sensitive read/domain/admin mutation, audit seam or migration. Existing service/
DAO authorization, tenant isolation, validation, concurrency and audit owners remain authoritative. Status,
theme and focus presentation intentionally add no audit rows. Roadmap §8.2 sensitive-read/task transaction,
actor and audit gaps remain deferred. No backend/SQL/API/auth/session/transport, deployment, versions,
application dependencies, native projects or MCP/AI activation changes.

## Executed passes

Read AGENTS/prompt rules completely and followed UI/web routing: development rules, design system/A.2,
theme infrastructure, system overview, Web V2 roadmap, Phase 2A/2B/2C reviews, web README, migration Step 2/3,
and local test-selection guidance. Inspected App shell/My Shale/list handlers, primitive consumers, shell/
presentation/button/token CSS, all five existing web suites and Phase 2B/2C browser/zoom/contrast scripts
before editing. Repository-wide test-impact search mapped the only changed production symbol,
`MyTasksList`, to App completion/pending/failure/missing-values/legacy-isolation tests. Extended those existing
behavioral assertions for stable status identity, task context, pending/success/failure and legacy opt-out.
No geometry regression gate or GitHub workflow added.

Commands from repository root (Java 21/Maven via `source /workspace/.tools/shale-env.sh` in this environment):

- `npm ci --prefix shale-web --cache /workspace/.cache/npm`: existing lockfile restored.
- `npm test --prefix shale-web -- src/App.test.tsx src/shell/ResponsiveShell.test.tsx src/ui/primitives.test.tsx src/preview/FoundationPreview.test.tsx`: **60 passed**.
- `npm test --prefix shale-web`: **68 passed**, five files.
- `npm run typecheck --prefix shale-web`; `npm run build --prefix shale-web`: passed; both HTML entries built.
- `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`:
  [selection](phase-2d-evidence/test-selection.md); server/AuthController selected because browser consumers
  map to bearer/API compatibility, no all-suite escalation.
- `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`: **10 passed**.
- `mvn test`: critical reactor **116 passed**, zero failures/errors/skips.
- `git diff --check`: passed. Final changed-file/test review confirms only App/list feedback and its tests
  affect operational code; preview, shell, API and unrelated task-detail handlers are unchanged.

### Browser evidence (synthetic only)

Runtime **Chromium 151.0.7922.173**, Playwright **1.64.0**, axe wrapper **4.13.0**, axe via external `work/browser-tools`.
Advisory scripts/packages/fixtures remain outside operational imports and application dependencies.
All `/api/` requests are intercepted; this is not live auth/backend/tenant/audit acceptance.

[Browser observations](phase-2d-evidence/chromium/browser-observations.json) record both themes at
**320, 360, 768, 1280 CSS px**, keyboard targets/outlines, shell history/skip/active destination/route focus,
and normal, pending, failure, completed, loading, empty and read-error geometry. Native Enter/Space/Escape
navigation disclosure, Tab reachability, visible 3px focus, card background/Enter/Space navigation and
independent Complete pass. Long/unbroken names and missing values wrap; targets >=44×44px; one main;
no horizontal page overflow or measured text clipping. Preview passes both themes/all widths with zero API
requests, and legacy Tasks retains its existing paint. No page errors. **56 state screenshots**, plus
four native zoom screenshots; shell observations are eight additional records, not screenshots.

[56 axe scans](phase-2d-evidence/chromium/accessibility-observations.json), including pending and completed,
report zero WCAG 2 A/AA + 2.1 A/AA violations. Incomplete pseudo-element/gradient contrast checks are recorded,
not counted as passes. [50 measured shared-token text/focus/control pairs](phase-2d-evidence/contrast-observations.json)
pass >=4.5:1 text / >=3:1 boundaries. Main accessibility snapshot exposes named sections, lists and actions.
Loading uses status, read/completion failures use alerts; the new pending/success region is persistent,
polite and atomic. DOM/live-region assertions and axe do **not** establish screen-reader acceptance.

[Native zoom observations](phase-2d-evidence/zoom-observations.json): actual Chromium Settings > Appearance >
Page zoom in a disposable persistent profile; fixed 1280×900 physical viewport. **200%** gives DPR 2,
CSS 640×450; **400%** gives DPR 4, CSS 320×225. Both themes pass reflow, visible focus, keyboard Complete and
failure retaining cards. No viewport/CSS zoom/font emulation counted. Screenshot export scaling changes
only PNG output, not native zoom. [Screenshot index](phase-2d-evidence/screenshots.md).

Reproduce with the Vite server on port 5173 and independent Playwright/axe packages:

```bash
npm run dev --prefix shale-web -- --host 127.0.0.1
# In another terminal, set absolute paths to independently installed tooling:
export PLAYWRIGHT_MODULE="$PWD/work/browser-tools/node_modules/playwright"
export AXE_MODULE="$PWD/work/browser-tools/node_modules/@axe-core/playwright"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/work/browsers"
REVIEW_ENGINE=chromium BROWSER_EXECUTABLE_PATH=/usr/bin/chromium node shale-web/docs/phase-2d-evidence/browser-review.cjs
ZOOM_PROFILE="$PWD/work/zoom-profile" BROWSER_EXECUTABLE_PATH=/usr/bin/chromium node shale-web/docs/phase-2d-evidence/zoom-review.cjs
node shale-web/docs/phase-2d-evidence/contrast-review.cjs
```

## Unavailable checks and user-reported results

- **Firefox:** downloaded Playwright Firefox 157.0 (build 1555), but launch failed with “Could not find profile
  folder.” One targeted retry moving TMPDIR under `work/` failed identically. No Firefox matrix or zoom pass.
- **WebKit:** downloaded Playwright WebKit 27.2 (build 2370), but host validation blocks launch: missing
  `libgtk-4`, `libgraphene-1.0`, `libharfbuzz-icu`, `libmanette-0.2`, `libhyphen`, `libGLESv2`.
  No WebKit matrix/zoom pass; packaged WebKit is not physical Safari/iOS evidence.
- No real screen reader, desktop display/Xvfb, or physical phone/tablet is available. No device emulation
  is counted as physical acceptance. Real-host routing and live authenticated/security workflows were not run.
- **User-reported results:** none supplied for Phase 2D. Historical Phase 2C passes are distinct from reruns here.

[Short operator checklist with exact launch steps and result fields](phase-2d-operator-checklist.md)
owns all remaining checks. No repeated unsupported launch/zoom retries are required.

## Rollback and next bounded milestone

Revert the Phase 2D operational/test changes or rebuild base `fc45437e6519be7e905b1ace90e418cbd49276cc`
with the same API origin. No backend/session/SQL rollback; keep foundation/preview and prior evidence.
Next bounded **Phase 2E acceptance closure**: execute the outstanding screen-reader, physical-device and
Firefox/WebKit checks for shell/My Shale; fix only reproduced findings there, without adopting another
screen. Phase 2 stays IN PROGRESS until required acceptance is recorded. Wider screen/dialog/menu adoption
needs a separate selected consumer. Phase 3 remains NOT STARTED: begin with a focused investigation of the
existing login return-path defect (protected detail → login → My Shale), safe pathname/search/hash restoration
and its routing/session tests; it remains deferred here. No merge or deployment.
