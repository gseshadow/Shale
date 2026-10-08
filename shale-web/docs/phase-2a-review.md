# Web V2 Phase 2A foundation review

Implemented for review; V2 acceptance and the rest of Phase 2 remain open. Base: live
`origin/codex/latest` at `eda5045153d6a4dc175884bbb889021e04df83d2`, including merged PR #1838.
No deployment was performed.

## Open the isolated preview

```bash
npm ci --prefix shale-web
npm run dev --prefix shale-web
```

Open <http://localhost:5173/foundation.html>. No credentials or API environment setting are required.
Use **Preview theme** for Light/Dark. Expand **Navigation** on compact screens; every destination,
including My Tasks and unavailable Calendar/Reports/Search, is labelled. Destination links change the
synthetic review heading and active navigation, retaining the component gallery; they do not open beta
business screens. Example cards toggle local selection; **Check example** demonstrates validation and
focuses the invalid field. Nothing saves, downloads, or reports a fake persistence success.

The production build includes both HTML entries. For a local built review:

```bash
npm run build --prefix shale-web
npm run preview --prefix shale-web -- --host 127.0.0.1
```

Open the printed local origin followed by `/foundation.html`. `/` and all existing business URLs still
mount the beta. Vite's additional HTML build input is build composition only; hosting/deployment
configuration, versions and dependencies are unchanged.

## Ownership and deliberate adoption

| Owner | Contract |
| --- | --- |
| `src/ui/primitives.tsx` | Adapted beta PageHeader, ToolbarActions, EntityCard/List, MetadataGrid/Row, StatusPill, Loading/Empty states; native Button with Primary/Secondary/Ghost/Danger/Navigation roles, link NavigationButton, SectionRegion, Field and Feedback. No API/domain/session imports or business rules. |
| `src/ui/foundation.css` | Single opt-in token vocabulary, both theme paints, shared geometry/type/density and component states. All selectors require `.shale-foundation`. |
| `src/ui/indicatorPaint.ts` | Validates desktop 6/8-digit hex and `0x` forms; picks the greater black/white contrast against actual opaque fill. Invalid/missing values use theme-neutral paint. Stored alpha is composited against an explicit white backing, consistently in both themes. Badge labels always use theme text. |
| `src/shell/navigation.ts` | Label/path/availability registry shared by beta navigation and the new shell. Availability is presentation, never authorization. Beta retains its available destinations and My Shale task grouping. |
| `src/shell/ResponsiveShell.tsx` / `shell.css` | One labelled navigation tree, skip link/main landmark, compact native disclosure, medium 10rem rail, wide 15rem rail; caller supplies content, links, identity and utilities. No data loading or account authority. |
| `src/preview/` / `foundation.html` | Dedicated synthetic composition importing foundation styles, separate from beta entry/auth startup. Theme, selection and text live only in component memory. |

Beta now imports the extracted primitives with its existing classes. Compatibility ActionButton and
SecondaryButton delegate to the one Button implementation. Beta CSS remains a legacy skin until
explicit screen adoption, not a second new V2 vocabulary. The only beta style addition resets the native
card activation button to the existing title appearance and supplies its focus ring. Whole-card pointer
activation remains; keyboard activation is now a named native button; nested Complete/Edit actions do
not bubble into navigation. Related ContactValueInput and its server validation/RETAIN/SET/CLEAR,
uniqueness-error and stale-response behavior are unchanged.

New paint follows canonical `architecture/design-system.md` A.2, `theme-infrastructure.md`, and current
Light/Dark resources. Solid navy chrome, pale cool-blue light canvas, theme content/section/card/input
surfaces, text and semantic feedback pairs map to those roles. We deliberately adapt primary endpoints
(darker blue/purple), muted text and control borders for browser normal-text/control contrast rather
than inheriting insufficient small-text contrast. Gradients occur only on Primary action planes and
selected global navigation. Cards, inputs, regions, page canvas and feedback are solid. Primary is
unique in its local form action area; disabled Danger demonstrates no mutation.

Page/section/body/metadata type uses 24/18/14/12px rem roles; editing inputs use 16px to avoid phone
focus auto-zoom. The 4/8/12/16/24 spacing rhythm and comfortable/compact/dense variables own geometry.
Cards use 14px radius and one restrained shadow; controls use 10px radius. Buttons retain 40/32px visual
planes inside at least 44px native hit areas. Long labels can grow; density never reduces their target.

## Responsive and accessibility evidence

Content breakpoints are `<48rem`, `48rem–<75rem`, `>=75rem`. Compact deliberately uses an inline,
labelled disclosure instead of the roadmap's proposed fixed bottom bar/drawer. It keeps full labels and
one normal document scroll, requires no focus trap, and cannot obscure forms when the keyboard opens.
Escape closes expanded compact navigation and returns focus to its summary. Changing destination
closes it and focuses main content. Medium/wide use the same expanded tree. Selected nav has a border,
underline and `aria-current`; selected cards have text/checkmark and native `aria-pressed`.

Safe-area padding, 100dvh with fallback, viewport-fit and interactive-widget resize hints are provided.
There are no sticky form footers or viewport-height content clips. Physical keyboard/safe-area behavior
still needs device acceptance; CSS hints are not proof of mobile OS behavior.

Chromium/Playwright inspection used only synthetic records. Eight full-page captures at 320/360/768/1280
CSS pixels cover both themes and validation/focus states. All visible native controls/links/summary
measured at least 44px in both dimensions; document scroll width equalled viewport width; preview made
zero `/api/` requests and had zero page errors. Enter/Space activate cards; Enter expands navigation;
Escape and return focus, validation focus, all ten links and medium/wide reachability were checked.
This is review evidence, not a rendered-pixel regression gate.

| Composition | Light | Dark |
| --- | --- | --- |
| 320px reflow | [Capture](phase-2a-evidence/320-light.png) | [Capture](phase-2a-evidence/320-dark.png) |
| Compact 360px | [Capture](phase-2a-evidence/360-light.png) | [Capture](phase-2a-evidence/360-dark.png) |
| Medium 768px | [Capture](phase-2a-evidence/768-light.png) | [Capture](phase-2a-evidence/768-dark.png) |
| Wide 1280px | [Capture](phase-2a-evidence/1280-light.png) | [Capture](phase-2a-evidence/1280-dark.png) |
| 320px / 200% text-size emulation | [Capture](phase-2a-evidence/320-text200-light.png) | [Capture](phase-2a-evidence/320-text200-dark.png) |

[Browser observations](phase-2a-evidence/browser-observations.json),
[contrast measurements](phase-2a-evidence/contrast-observations.json), and
[text reflow limits](phase-2a-evidence/text-reflow-observations.json) record exact evidence.
[Preserved beta capture](phase-2a-evidence/beta-1280-synthetic.png) uses intercepted synthetic API fixtures,
not real server/tenant data. Its legacy skin is intentionally different from the opt-in V2 foundation.

22 text pairs and 5 essential focus/input boundary pairs per theme pass 4.5:1/3:1 respectively,
including normal/hover/pressed gradient endpoints. Dynamic fill foreground tests include alpha/red/light/
dark examples. Light shell focus uses on-dark outlines on navy; Primary planes have a contrasting border.
No blanket WCAG conformance claim is made.

**Unverified:** physical phones/tablets, software keyboards and safe-area cutouts, iOS Safari/Android,
Firefox/WebKit, screen readers, real-host direct-link handling, actual browser UI 200%/400% zoom.
200% root-font resizing at 320px is text-size emulation, not browser UI zoom; both themes reflow without
page overflow. The available headless Chromium shell did not expose browser settings or load the local
test zoom extension; native browser zoom must be checked during acceptance.

## Test impact, audit review, and checks

Before editing, reviewed the sole existing web suite `ContactValueInput.test.tsx` completely and searched
the repository for all extracted symbols and their consumers/tests. Desktop similarly named factories
are independent; their production code is unchanged. Existing tests remain intact. New tests cover
primitives' native semantics, labelled errors, selection/nested actions and readable fills; all ten preview
destinations, Escape/return focus, local validation, and no API/storage writes; every existing beta
list/detail URL, login with no bearer, and authoritative Complete versus card navigation.

Audit compatibility: the foundation adds **no sensitive read, domain mutation, administrative mutation,
API endpoint, SQL or audit schema change**. Preview theme/selection/validation are synthetic local UI
interactions and intentionally create no audit events. Beta APIs, mutation handlers and their authoritative
service/DAO audit paths are preserved; tests mock those endpoints rather than perform real mutations.
No UI card/button creates audit authority. Future domain slices still need their own audit review.

Executed successfully:

- `npm test --prefix shale-web`: 4 files, 52 tests passed, including unchanged ContactValueInput suite.
- `npm run typecheck --prefix shale-web`: passed.
- `npm run build --prefix shale-web`: passed, independent beta/foundation HTML and CSS assets.
- `python build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`:
  web paths map to server; AuthControllerTest selected, no full-suite escalation.
- `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  10 passed. Relevant because repository ownership maps browser consumers to bearer/API compatibility.
- `mvn test`: critical reactor passed, 116 tests, zero failures/errors/skips.
- `git diff --check`: passed.

No new GitHub workflow, gate, package, or native/PWA tooling was added. The first dependency attempt
found missing test packages; restoring the existing lockfile with `npm ci` resolved it. Initial navigation
name failures were corrected in production with explicit unavailable link labels; final suites pass.

## Temporary entry removal and next narrow milestone

Keep `/` beta composition and base commit `eda5045153d6a4dc175884bbb889021e04df83d2` recoverable until V2
acceptance. This task does not switch the beta to the new shell or migrate all screen paint. Reviewers
can continue using its existing business routes and handlers while reviewing the isolated foundation.

Next Phase 2B: accept/adjust this vocabulary and navigation with actual browser zoom, screen-reader and
physical-device checks, then deliberately adopt ResponsiveShell around existing route outlets and one
small presentation slice. Keep business/session/transport unchanged. Remaining Phase 2 includes needed
focus-managed dialog/menu primitives, deliberate screen adoption and responsive/contrast acceptance.
Phase 3 session/transport and subsequent business slices are separate tasks.

After accepted feature composition replaces beta, remove `foundation.html`, `src/preview/` and its Vite
input together; retain `ui/`, `shell/`, focused tests and this evidence. Remove beta CSS adapters only
after their final consumer is migrated. Retain the known-good beta build/commit for the owner-approved
rollback window; no permanent fork of every feature is introduced.
