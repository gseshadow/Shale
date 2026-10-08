# Web V2 Phase 2B authenticated shell adoption review

Implemented for review from current `origin/codex/latest` at
`6951305201c1d18b9712cb4a2a7c5200269abb5b` (merged Phase 2A PR #1839), on
`codex/web-v2-phase-2b`. Phase 2 remains **IN PROGRESS; acceptance OPEN**.
No merge or deployment was performed.

**Phase 2C follow-up:** [My Shale shared presentation review](phase-2c-review.md) records its deliberate
Light/Dark adoption, isolated other-route styling, native Chromium 200%/400% zoom evidence and remaining
acceptance gaps. Statements below about retained beta route paint describe the Phase 2B checkpoint;
My Shale is now the first adopted operational screen. Phase 2 remains IN PROGRESS.

## Exact scope and composition

`AppShell`, already nested beneath `ProtectedRoute`, now composes `ResponsiveShell` around the unchanged
`Outlet`. `AppRoutes`, `ProtectedRoute`, `LoginPage`, API transport, session storage, service calls and
all route/mutation handlers are unchanged. The twelve authenticated list/detail URLs and root/login/
unknown-route redirects remain declared in the existing React Router `Routes`. No new operational URL,
router, dependency, version, backend, SQL, API contract, authentication protocol, deployment configuration,
native project or MCP/AI activation was added.

The shell uses `src/shell/navigation.ts` directly, removing the beta's secondary navigation projection.
My Tasks is now a labelled `/tasks` destination; `/tasks/:taskId` selects it. Entity detail paths select
their parent destination through the existing segment-aware matcher. Calendar, Reports and Search remain
visible **Unavailable** text, without operational links that would silently redirect to My Shale.
The explicit preview `linkTo` adapter still links all ten synthetic destinations within `foundation.html`.
Availability is presentation only; the existing authenticated boundary and server remain authoritative.

The shell receives pathname for active state and React Router's location key for focus, including new
history entries at the same pathname. Route changes close compact navigation and focus the single main
landmark without forcing scroll. The native skip link focuses main. Compact disclosure uses native
Enter/Space/Tab and Escape/summary return focus. A resize that hides a focused navigation link returns
focus to summary; resizing preserves focus and memory drafts in route inputs. Signed-in name/email and
Logout remain available in compact, medium and wide layouts.

Light is the initial shell theme. **Theme (this session)** changes only shell presentation in memory;
it survives route navigation while the shell stays mounted and resets after logout/reload. It neither
loads nor saves desktop `appearance.theme`, performs API calls nor changes bearer storage.
**Operational route content deliberately keeps its existing Light beta skin in both shell themes.**
This is shell adoption, not completed Dark screen migration or broad screen redesign.

## Style ownership and necessary presentation adjustments

- `ui/tokens.css` and `ui/buttons.css` extract the existing Phase 2A declarations unchanged in meaning;
  `foundation.css` imports them for the isolated gallery. Operational composition imports only tokens,
  shell styles and shell utility buttons, so foundation card/field/type styles do not migrate every route.
  Button selectors opt in the preview or operational shell utilities, preserving route action styling.
- `shell/authenticated.css` bridges the existing route skin into the new working plane. It removes mobile
  detail negative margins/old viewport minimums and sticky detail headers tied to the removed inner scroll,
  preserves a 44px Back target, and wraps detail headings and long status labels.
- Card columns use their own available space (`auto-fit`, bounded 20rem minimum), rather than viewport
  width that ignores the rail and nested sections. Card headers wrap so status pills cannot be squeezed
  into unreadable vertical fragments. Medium detail sections remain one column beside the 10rem rail.
- `styles.css` removes document/root overflow clipping so the in-flow shell has one document scroll.
  Login markup and its paint remain intact; short viewports can scroll rather than clip controls.
- `index.html` gains the same viewport safe-area/keyboard resize hints as the preview. These are hints,
  not physical-device acceptance. Shell focus outlines are explicit even without the gallery stylesheet.

Breakpoints remain `<48rem`, `48rem–<75rem`, `>=75rem`; rails remain 10rem and 15rem. No fixed footer,
drawer, focus trap, duplicated device-specific screen, fake persistence, or operational synthetic data
was introduced. `foundation.html`, `src/preview`, its Vite input and all Phase 2A evidence are retained.

## Test impact and audit compatibility

Before editing, reviewed `AGENTS.md`, prompt/development rules, design system (including A.2 and semantic
controls), theme infrastructure, system overview, Web V2 roadmap, Phase 2A review, web README, migration
Step 2/3 and applicable readiness/deployment/session sections. Inspected route composition, auth startup/
login/logout, API bearer functions, route presentation, registry, shell, primitive/preview ownership and
all four existing web suites. Repository-wide symbol searches identified `App.test.tsx` and
`FoundationPreview.test.tsx` as shell/navigation consumers; primitives and ContactValueInput remain covered
by their existing suites. No test expects the removed beta navigation projection afterward.

`App.test.tsx` now covers authenticated direct routes, registry destinations/availability, browser history,
active state, compact Escape/skip focus, local theme isolation, delayed session verification, existing
verification-failure/login contracts, immediate local logout while remote logout is pending, and unchanged
Complete versus card navigation. New `ResponsiveShell.test.tsx` covers resize focus/draft preservation and
same-path history-entry focus. Existing preview reachability/no API/no persistence/keyboard and primitive/
ContactValueInput validation, stale-response, duplicate-error and RETAIN/SET/CLEAR coverage remain intact.
CSS/resource changes receive browser review rather than permanent rendered-pixel/geometry test gates.

Audit compatibility: this composition creates no sensitive read, domain/admin mutation or new audit seam.
Existing route/service/DAO transaction, tenant, actor, validation, concurrency and audit paths are unchanged.
Navigation, resize, focus and memory-only theme selection intentionally create no audit rows. Browser
fixtures intercept requests outside the application; they do not establish backend authorization or audit
acceptance. Known roadmap sensitive-read/task audit gaps remain deferred to their own bounded work.

## Completed verification and evidence

- `npm ci --prefix shale-web --cache /workspace/.cache/npm-phase2b`: restored the existing lockfile.
  The first install failed because the default npm cache was unavailable; no dependency change was needed.
- `npm test --prefix shale-web`: **5 files, 61 tests passed**.
- `npm run typecheck --prefix shale-web`: passed.
- `npm run build --prefix shale-web`: passed, retaining both operational and foundation HTML entries.
- `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`:
  selected server/AuthController compatibility; no all-suite escalation. Browser consumer ownership maps
  to server auth, so this is relevant even though no server production file changed.
- `/workspace/.tools/bin/mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **10 passed**. The bundled executable is the environment's Maven installation.
- `/workspace/.tools/bin/mvn test`: critical reactor **116 passed**, zero failures/errors/skips.
- `git diff --check`: passed. No GitHub test workflow or gate added.

Headless Chromium/Playwright used intercepted synthetic identity, assigned work, long names/unbroken text,
case detail and existing empty/error facilities. No live auth/backend acceptance was exercised or claimed.
[Browser observations](phase-2b-evidence/browser-observations.json) record version, matrix, shell control
sizes, landmarks, expected synthetic unavailable responses, and zoom attempts. No page errors occurred.
My Shale, case detail and its staged Edit details/Cancel fit both shell themes at all four widths without
page overflow. Visible shell controls/links/summary measured at least 44×44px. Browser back/forward,
main focus, direct URL with query/hash, skip link, native compact keyboard and local logout/token removal
passed. Other retained list/detail routes received 360px empty/error/synthetic detail smoke review.
The retained preview also passed both themes at all four widths with zero API requests.

| CSS width | My Shale Light / Dark | Case detail Light / Dark |
| --- | --- | --- |
| 320 | [Light](phase-2b-evidence/320-light.png) / [Dark](phase-2b-evidence/320-dark.png) | [Light](phase-2b-evidence/detail-320-light.png) / [Dark](phase-2b-evidence/detail-320-dark.png) |
| 360 | [Light](phase-2b-evidence/360-light.png) / [Dark](phase-2b-evidence/360-dark.png) | [Light](phase-2b-evidence/detail-360-light.png) / [Dark](phase-2b-evidence/detail-360-dark.png) |
| 768 | [Light](phase-2b-evidence/768-light.png) / [Dark](phase-2b-evidence/768-dark.png) | [Light](phase-2b-evidence/detail-768-light.png) / [Dark](phase-2b-evidence/detail-768-dark.png) |
| 1280 | [Light](phase-2b-evidence/1280-light.png) / [Dark](phase-2b-evidence/1280-dark.png) | [Light](phase-2b-evidence/detail-1280-light.png) / [Dark](phase-2b-evidence/detail-1280-dark.png) |

[Reproducible browser review script](phase-2b-evidence/browser-review.cjs) runs from the repository root
with the dev server on port 5173, an independently installed Playwright module (`PLAYWRIGHT_MODULE` can
select its absolute path), and Chromium (`BROWSER_EXECUTABLE_PATH` override). It installs no application
dependencies, intercepts every `/api/` request, and writes only synthetic evidence. It is an advisory
review utility, not a new release gate. Chromium's accessibility tree exposed banner, labelled Primary
navigation and main landmarks; this does not establish screen-reader or full WCAG acceptance.

## Remaining acceptance gaps and next bounded milestone

**Actual browser UI zoom unavailable:** attempted Chromium Ctrl+Equal twice; devicePixelRatio and CSS
viewport stayed unchanged. Attempting `chrome://settings/appearance` closed that headless target. Neither
attempt established 200%/400% browser zoom acceptance. No text-size emulation is represented as zoom.
Physical devices, virtual keyboards/cutouts, assistive screen readers, Firefox/WebKit, real-host direct-link
refresh, live authenticated API workflows and backend/tenant/audit acceptance remain unverified.
The retained beta screen skin has not received full V2 Dark/contrast/responsive acceptance.

**Existing login return-to defect, preserved:** a focused test starting at `/contacts/7` observed the
protected route capture that path in login state, then successful sign-in landed on `/my-shale`.
The same failed detail-return assertion reproduced with the untouched base `App.tsx`; shell code was
not involved. This is automated fixture evidence, not a live browser/server login result. Existing
return-to search/hash validation/restoration, startup-failure token clearing, uncoordinated refresh and
remote logout-status handling are still Phase 3 concerns. Authenticated deep links/back/forward pass;
full signed-out deep-link round-trip acceptance remains open. No auth fix was folded into shell adoption.

Next bounded **Phase 2C**: obtain native browser zoom, screen-reader and physical-device shell evidence;
then deliberately adopt shared presentation on one selected screen (for example My Shale), including
both theme contrast/reflow checks, while retaining service/session/mutation behavior. Keep broader
screen redesign and focus-managed dialog/menu work bounded to their own consumers. Separately start
Phase 3 with a focused routing/session return-to investigation; do not infer its completion here.

## Review and rollback

Run the normal dev/build commands and review operational URLs with an approved test identity, or run
the interception script for synthetic evidence. The gallery remains at `/foundation.html`, with no API
or storage access even if an operational bearer exists. Synthetic fixtures never enter operational source.

Rollback is frontend-only: revert the Phase 2B commit(s) or rebuild known-good base
`6951305201c1d18b9712cb4a2a7c5200269abb5b` with the same API-origin build setting. This restores the beta
shell, Phase 2A preview and original screen composition. No server, SQL, session-signing/revocation,
CORS or deployment change needs rollback. Any later redeployment is separate operator-authorized work;
this milestone publishes a review PR only. Retain the preview until accepted feature composition replaces
it; remove its HTML/entry/Vite input together only in that later task.
