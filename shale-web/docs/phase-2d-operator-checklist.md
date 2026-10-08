# Phase 2D operator acceptance checklist (OPEN)

Record actual browser/version, OS/device, assistive technology, outcome and findings below.
Automated scans/tree inspection are not screen-reader acceptance; emulation is not physical-device acceptance.
All check rows are **NOT RUN** until an operator records results. Use synthetic records or an approved test tenant.

## Desktop launch: Firefox/WebKit and real screen reader

On a desktop with a display, from repository root:

```bash
npm ci --prefix shale-web
npm install --prefix work/browser-tools --no-save playwright @axe-core/playwright
export PLAYWRIGHT_MODULE="$PWD/work/browser-tools/node_modules/playwright"
export AXE_MODULE="$PWD/work/browser-tools/node_modules/@axe-core/playwright"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/work/browsers"
node work/browser-tools/node_modules/playwright/cli.js install chromium firefox webkit
npm run dev --prefix shale-web -- --host 127.0.0.1
```

In another terminal export the same module/browser paths. For the automated cross-engine matrix:

```bash
REVIEW_ENGINE=firefox node shale-web/docs/phase-2d-evidence/browser-review.cjs
REVIEW_ENGINE=webkit node shale-web/docs/phase-2d-evidence/browser-review.cjs
```

If host libraries are unavailable, record the launch error; use a supported workstation. Do not label a failed
launch a pass. For interactive synthetic review with a real screen reader:

```bash
REVIEW_ENGINE=firefox node shale-web/docs/phase-2d-evidence/operator-review.cjs
# Or REVIEW_ENGINE=chromium / webkit; optional BROWSER_EXECUTABLE_PATH for Chromium.
```

This headed harness was syntax-checked here but could not be executed without a display. It opens
`http://127.0.0.1:5173/my-shale`, intercepts API calls and never falls through to the live service.
Select Light/Dark in the page. Terminal commands `normal`, `loading`, `empty`, `errors` reload the fixture;
`reads` resolves held loading. Click Complete, inspect pending, then type `success` or `failure` to release
that request. `normal` restores open tasks; `quit` closes. Fixtures include long/unbroken names and missing facts.

1. Enable **NVDA** on Windows (launch NVDA, then focus Firefox/Chrome), or **VoiceOver** on macOS
   (Cmd+F5, then focus the browser; Safari keyboard navigation enabled in Settings > Advanced).
   Navigate landmarks/headings/lists and Tab/Shift+Tab. Expect one main, named Primary navigation,
   My Cases/My Tasks, named card controls, ordinary completed facts and meaningful missing-value text.
2. At 320/360/768/1280 CSS widths in both themes: compact Navigation opens with Enter/Space, Escape closes
   and returns focus; all available destinations remain reachable, unavailable labels do not act as links.
   Current destination has text/underline/border plus current-page semantics. Skip to content focuses main;
   case/task navigation and Back/Forward focus the new route and select the right destination.
   Expect visible focus, >=44px targets and independent card versus Complete activation.
3. Trigger loading → empty, read errors, pending → success, then pending → failure. Expect loading/empty status,
   errors announced as alerts, “Completing [task]…” before resolution, “Completed [task].” only after success,
   failure without success and with retained cards/actions. Listen for missing/duplicate/interrupted messages;
   record timing and focus after the Complete button disappears. Do not infer speech from DOM attributes.
4. Use **browser menu Page zoom** (Firefox menu percentage control, Chrome menu Zoom) to select actual
   **200% and 400%**, retaining the physical window size; verify the menu percentage. In Safari use
   View > Zoom In / Website Settings > Page Zoom where supported; record any maximum that prevents 400%.
   Expect readable wrapping, no horizontal page overflow, reachable navigation/actions and full vertical
   scrolling in both themes. Resizing, CSS zoom, OS magnification and text-only zoom do not close this gate.

## Physical device and real-host launch

On a phone/tablet open the existing owner-approved test-host URL plus `/my-shale`, sign in with the approved
test identity and record the host/build/tenant designation without credentials. No deployment is requested here.
If reviewing a locally running client against an approved test API instead:

```bash
VITE_SHALE_API_BASE_URL=https://APPROVED-TEST-API-ORIGIN npm run dev --prefix shale-web -- --host 0.0.0.0
```

Replace the placeholder with the existing authorized API origin; open `http://DEVELOPER-LAN-IP:5173/my-shale`
on the device on the same network. This requires already-approved API CORS/network access; do not change
server/deployment settings for this checklist. If no such environment exists, keep the check blocked.
`/foundation.html` is credential-free for supplemental safe-area/form-keyboard inspection, but cannot prove
operational completion. Do not complete real production work to obtain synthetic success/failure evidence.

5. On actual iOS Safari/Android Chrome, test both themes in portrait/landscape, navigation, targets, scrolling,
   cutouts/safe areas and browser bars. Enable VoiceOver (iOS Settings > Accessibility > VoiceOver) or TalkBack
   (Android Settings > Accessibility > TalkBack), traverse and independently activate cards/Complete.
   My Shale has no text-entry field: virtual-keyboard coverage uses the retained foundation Example name field
   as a supplemental check, not an operational My Shale claim. Expect focused field/errors/actions reachable;
   orientation keeps chosen theme and content; no clipped/covered controls. Record device dimensions/scale.
6. On the existing approved host refresh `/my-shale`, follow case/task links, then Back/Forward; verify approved
   test data and authoritative completion/failure without retries. Authentication/backend/tenant/audit acceptance
   requires the separately authorized workflow checks; synthetic passes do not close those gates. The known
   signed-out detail return-path defect is deferred Phase 3 work, not a Phase 2D change request.

| Check | Browser/version, OS/device, screen reader | Host/build / dimensions / zoom | Outcome | Findings / evidence / operator/date |
| --- | --- | --- | --- | --- |
| Firefox matrix | | | NOT RUN | |
| WebKit matrix | | | NOT RUN | |
| Real screen-reader semantics and all announcements | | | NOT RUN | |
| Firefox native 200% / 400%, both themes | | | NOT RUN | |
| Safari/WebKit native zoom, both themes (record supported limits) | | | NOT RUN | |
| Physical iOS/Android, orientation/safe areas/touch/AT | | | NOT RUN | |
| Supplemental physical virtual keyboard in foundation | | | NOT RUN | |
| Existing approved real host / live workflow checks | | | NOT RUN | |

A failing/unsupported check remains open with exact findings; do not mark Phase 2 complete from partial results.
