# Web V2 Phase 2C: My Shale shared presentation review

Implemented for review on `codex/web-v2-phase-2c-my-shale` from live `origin/codex/latest`,
`7e7972d6ebb0f1fcc5ae61c1d2eead4854aad39e`. Fetch explicitly updated the remote-tracking ref;
`git merge-base --is-ancestor` verified merged Phase 2B PR #1840. Phase 2 remains
**IN PROGRESS; acceptance OPEN**. This milestone publishes a review PR; no merge or deployment.

## Exact scope and ownership

My Shale is the first operational screen using shared V2 presentation throughout both Light and Dark.
Its heading, welcome text, sections, assigned-case/task cards, metadata, status labels, Complete actions
and loading/empty/error feedback compose existing `ui/primitives.tsx`. The obsolete “read-only summary”
welcome now describes assigned work, opening records and completing an open task. No new dashboard
capability, data field, lookup, route, filter, count, or service request was added.

| Owner | Change / compatibility boundary |
| --- | --- |
| `App.tsx`: `AppShell` | `/my-shale` receives a neutral route wrapper; all other routes keep `legacy-route-content`. Shell remains the only operational theme owner. No route/auth/session changes. |
| `App.tsx`: `MyShalePage`, `MyCasesSection`, `MyTasksSection` | Explicit `.shale-presentation` working plane, existing PageHeader/SectionRegion/Feedback/cards/metadata/pills. No dashboard paint sheet or duplicate card vocabulary. |
| `ui/presentation.css` | Extracted foundation geometry/type/component paint references; `.shale-foundation` or `.shale-presentation` opt-in. Reusable working plane and intrinsic card columns; scoped resets prevent beta element defaults leaking into opted-in cards. |
| `ui/tokens.css` | Unchanged single Light/Dark palette, type, spacing, radius and elevation ownership. A screen inherits the selected shell theme rather than installing its own palette/default. |
| `ui/buttons.css` | Existing semantic button rules additionally accept `.shale-presentation`. Shell utilities and foundation retain their existing selectors/paint. |
| `ui/foundation.css`, preview | Imports the extracted shared presentation. `foundation.html`, synthetic preview and Vite entry are retained; preview remains API/storage independent. |
| `MyTasksList` | Default remains legacy for `/tasks`; only My Shale opts into Secondary/Small Complete, busy semantics and completion status. Native card navigation stays separate from the action. |

A.2: solid canvas/working plane/section/card/status surfaces, 14px cards, 10px controls, shared
4/8/12/16/24 spacing and 24/18/15/14/12px type hierarchy. Complete is a supporting Secondary action,
with a 32px visual plane inside a minimum 44px native target. Cards use the single shared restrained
shadow. There are no card/page gradients, screen colors or per-theme geometry copies. Existing shell
selected navigation retains its allowed gradient. Case summary responses contain no stored status-color
field; preserve their existing labels/info tone rather than invent a lookup or infer authoritative colors.

Sections stack at every width. Lists choose columns from their own available space with a bounded 20rem
minimum: one column at 320/360/768, two at 1280 in the reviewed shell. Metadata wraps label/value pairs;
long/unbroken titles, names, references and status labels wrap without hiding facts or squeezing actions.
There is one document scroll and no fixed dashboard footer/viewport clipping.

## Interaction, data and audit preservation

Existing assigned-case/task calls, authenticated token arguments, missing-value fallbacks, formatting,
IDs, navigation callbacks and server-completed-task merge are retained. Case/card background pointer
activation and the named native title button still open the existing detail URLs. Complete remains an
independent native button: Enter/Space do not also open the card. Pending disables the existing task
action and labels it Completing; no optimistic completion, automatic mutation retry or read reload.
Completed tasks retain their server-supplied completion date and offer no Complete action.

My Shale now displays a completion error separately from the initial task-read error. This keeps loaded
cards/navigation visible after a failed Complete instead of replacing the task list with an error. The
same existing API error is shown in shared alert feedback; no success is inferred and no request is
replayed. The separate Tasks route retains its prior action and error behavior. Existing single-task
pending bookkeeping and unrelated concurrency/session lifecycles were not redesigned.

Audit compatibility: no new sensitive read, domain/admin mutation, authoritative audit integration or
migration. Existing authorization, principal/tenancy, validation, concurrency and audit owners remain
unchanged. Theme/navigation/focus/presentation intentionally create no audit events. Roadmap section 8.2
sensitive-read and task transaction/actor/audit gaps remain deferred; retaining the existing Complete
endpoint is not a claim those gaps are closed. No backend, SQL, API contract, auth/session/transport,
deployment/version/dependency, native project or MCP/AI activation change.

## Test-impact review and executed checks

Read AGENTS/prompt rules completely; followed UI/web routing through development rules, canonical
design system including A.2, theme infrastructure, system overview, Web V2 roadmap, Phase 2A/2B reviews,
web README and migration Step 2/3. Before editing, inspected My Shale, both assigned sections, all shared
primitive consumers (including `/tasks`, settings/result/detail cards and preview), shell/theme and CSS
ownership, and existing App/primitives/preview/shell/ContactValueInput suites. Repository-wide symbol/test
search found App and shared/preview suites affected; ContactValueInput and native components are unchanged.

`App.test.tsx` adds six focused contracts: named sections/theme without refetch, legacy Tasks opt-out,
pending/server-confirmed completion and unchanged other task, failure with retained work/no auto retry,
independent loading/empty/read-error sections, and missing/completed facts. Existing card/action, all
routes, login/logout, shell/history/focus, indicator contrast, preview isolation and validation tests pass.
The documented login return-to defect remains unchanged and explicitly tested as existing Phase 3 work.
No rendered-pixel regression gate or GitHub testing workflow was introduced.

Executed successfully from repository root:

- `npm ci --prefix shale-web --cache /workspace/.cache/npm-phase2c`: restored existing lockfile;
  no dependency/version edits. Initial test attempt found the uninstalled Vitest executable.
- `npm test --prefix shale-web -- src/App.test.tsx src/ui/primitives.test.tsx src/preview/FoundationPreview.test.tsx`:
  57 focused tests passed; then `npm test --prefix shale-web`: **5 files, 68 tests passed**.
- `npm run typecheck --prefix shale-web` and `npm run build --prefix shale-web`: passed;
  operational and foundation HTML entries retained.
- `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`:
  server/AuthController selected; no all-suite escalation. Web consumer ownership maps to bearer/API
  compatibility; no unrelated domain areas selected. The same command with `--run` also passed,
  executing the selected check and critical reactor below. [Selector output](phase-2c-evidence/test-selection.md).
- `/workspace/.tools/bin/mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **10 passed**, after sourcing `/workspace/.tools/shale-env.sh` for Java 21/Maven.
- `/workspace/.tools/bin/mvn test`: critical reactor **116 passed**, zero failures/errors/skips.
- `git diff --check`: passed. Changed production files mapped back to App, primitive and preview tests;
  old read-only dashboard text and dashboard-only wrappers have no remaining operational consumer.

## Browser evidence and exact limits

Chromium **151.0.7922.173**, Playwright and axe used external advisory tooling under `work/`; packages
are not application dependencies. All API requests were intercepted with synthetic records outside
operational code. [Browser script](phase-2c-evidence/browser-review.cjs) and
[observations](phase-2c-evidence/browser-observations.json) cover both themes at 320/360/768/1280:
long/unbroken names, missing values, initial loading, successful empty, read errors, already completed
tasks, pending Complete, failure and authoritative completion. **56 captures**; document width equals
viewport width, no measured text clipping or horizontal page overflow, dashboard buttons >=44×44px,
visible 3px focus, Tab/action separation, native Enter/Space and background-pointer navigation pass.
No page errors. Other-route legacy task action paint remains identical in both shell themes; detail
navigation returns to legacy content. Preview reflows in both themes at all widths with zero API requests.

**40 axe WCAG 2 A/AA + 2.1 A/AA scans reported zero violations** across normal/failure/loading/empty/read
error states. [Accessibility observations](phase-2c-evidence/accessibility-observations.json) retain
incomplete color checks for pseudo-element action planes and selected-navigation gradients; these were
not counted as automated passes. [Contrast script](phase-2c-evidence/contrast-review.cjs) and
[50 measured token pairs](phase-2c-evidence/contrast-observations.json) complement those gaps:
text >=4.5:1 and focus/control boundaries >=3:1, including Secondary normal/hover and navigation gradient
endpoints. Disabled pending opacity remains the shared disabled treatment. The Chromium accessibility
tree exposes named regions, landmarks and actions; this is not screen-reader or blanket WCAG acceptance.

**Actual browser zoom exercised:** initial Ctrl+Equal shortcuts had no effect, and Settings was unavailable
in an isolated context. A disposable **persistent Chromium context** then exposed native Settings >
Appearance > Page zoom. With physical viewport fixed at 1280×900, native 200% produced DPR 2 / CSS
640×450; 400% produced DPR 4 / CSS 320×225. Both themes reflowed with scrollWidth matching CSS width,
visible card focus, keyboard Complete and failure retaining cards. This is browser page zoom, not CSS
zoom, viewport resizing, device/text-size emulation. Full captures export the zoomed layout at CSS
resolution through CDP; export scaling does not alter native browser zoom.
[Zoom script](phase-2c-evidence/zoom-review.cjs) / [exact observations](phase-2c-evidence/zoom-observations.json).

| Width | My Shale Light / Dark | Completion failure Light / Dark |
| --- | --- | --- |
| 320 | [Light](phase-2c-evidence/normal-320-light.png) / [Dark](phase-2c-evidence/normal-320-dark.png) | [Light](phase-2c-evidence/failure-320-light.png) / [Dark](phase-2c-evidence/failure-320-dark.png) |
| 360 | [Light](phase-2c-evidence/normal-360-light.png) / [Dark](phase-2c-evidence/normal-360-dark.png) | [Light](phase-2c-evidence/failure-360-light.png) / [Dark](phase-2c-evidence/failure-360-dark.png) |
| 768 | [Light](phase-2c-evidence/normal-768-light.png) / [Dark](phase-2c-evidence/normal-768-dark.png) | [Light](phase-2c-evidence/failure-768-light.png) / [Dark](phase-2c-evidence/failure-768-dark.png) |
| 1280 | [Light](phase-2c-evidence/normal-1280-light.png) / [Dark](phase-2c-evidence/normal-1280-dark.png) | [Light](phase-2c-evidence/failure-1280-light.png) / [Dark](phase-2c-evidence/failure-1280-dark.png) |
| Native 200% | [Light](phase-2c-evidence/zoom-200-light.png) / [Dark](phase-2c-evidence/zoom-200-dark.png) | CSS viewport 640×450 |
| Native 400% | [Light](phase-2c-evidence/zoom-400-light.png) / [Dark](phase-2c-evidence/zoom-400-dark.png) | CSS viewport 320×225 |

[All 60 screenshots, including loading/empty/read errors/pending/completed](phase-2c-evidence/screenshots.md).
Reproduce with dev server port 5173 and independently available Playwright/axe/Chromium; set
`PLAYWRIGHT_MODULE`, `AXE_MODULE`, `BROWSER_EXECUTABLE_PATH`, `REVIEW_ORIGIN`, and for zoom a disposable
`ZOOM_PROFILE` if needed. Run both browser scripts and `node shale-web/docs/phase-2c-evidence/contrast-review.cjs`
from the repository root. Do not use a profile with a real operational bearer.

**Still unverified:** physical devices/safe-area/virtual keyboard, screen readers, Firefox/WebKit,
real-host routing, live authentication/backend/tenant/audit workflows. Concise manual acceptance:
use an approved test tenant on target phones/tablets, check orientation/navigation and reachability;
use NVDA/VoiceOver to read My Cases/My Tasks, missing values and loading/error/completion feedback;
Tab/Shift+Tab to every card and Complete, activate each independently, and confirm pending/failure
announcements. Repeat 200%/400% native zoom and long-name/failure cases in supported browser engines.
Only claim live service/security acceptance after separately exercising those workflows.

## Rollback and next bounded milestone

Frontend-only rollback: revert this milestone's commits or rebuild base
`7e7972d6ebb0f1fcc5ae61c1d2eead4854aad39e` with the same API-origin setting. This restores Phase 2B
shell and beta My Shale presentation. No server/SQL/session/deployment rollback is required. Keep
foundation.html/preview and prior evidence until accepted composition replaces them in a later task.

Next bounded **Phase 2D**: obtain screen-reader/physical-device acceptance of the shell and My Shale,
resolve only findings in those adopted surfaces, and decide any next single-screen adoption separately.
Wider screen/dialog/menu migration remains bounded to its own consumers. Phase 2 stays IN PROGRESS;
Phase 3 remains NOT STARTED, including the existing login return-path investigation. No merge/deploy.
