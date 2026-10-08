# Web dependency security maintenance — 2026-10-08

Base: fetched live `origin/codex/latest`, `2c5d2e17afb3e32005589a09e6b17658279e3af7`.
Separate task branch: `codex/shale-web-security-dependencies`. This is dependency maintenance;
Web V2 phase/acceptance statuses and its deferred login return-path issue remain unchanged.

## Updates and authoritative verification

GitHub's live advisory API (`https://api.github.com/advisories/<GHSA>`) and npm's version-specific
registry records were checked on 2026-10-08. The advisory links below include upstream fixes/releases.
Selected versions are the first compatible releases covering all six findings, rather than the latest
available patches. Published npm dependency metadata and integrity hashes were resolved with npm in
an isolated scratch project and applied only to the five existing lock entries. `npm ci` verified the result.

| Package / installed chain | Before → after | Advisory / first compatible patch |
| --- | --- | --- |
| `vite@8.0.16 → postcss` | `8.5.15 → 8.5.23` | [GHSA-r28c-9q8g-f849](https://github.com/advisories/GHSA-r28c-9q8g-f849): 8.5.18; [GHSA-fxqj-rqcc-2cmp](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp): 8.5.23 |
| `vite → postcss → nanoid` | `3.3.13 → 3.3.18` | [GHSA-28wg-ghj8-5hjv](https://github.com/advisories/GHSA-28wg-ghj8-5hjv): 3.3.16; [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8): 3.3.18 |
| `vite → postcss → source-map-js`; `jsdom@30.1.2 → css-tree@3.2.1 → source-map-js` | `1.2.1 → 1.2.2` | [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q): 1.2.2 |
| `react-router-dom → react-router` | both `7.18.0 → 7.18.2` | [GHSA-qwww-vcr4-c8h2](https://github.com/advisories/GHSA-qwww-vcr4-c8h2): Router 7.18.2 (8.x patch is 8.3.0) |

`@vitejs/plugin-react`, Vitest and its mocker also consume Vite through peer ranges. jsdom's
`@asamuzakjp/dom-selector`, `@bramus/specificity` and `@csstools/css-syntax-patches-for-csstree`
also consume the same css-tree/source-map-js chain. The source-map-js install is deduplicated.
[Before chains](security-dependency-evidence/chains-before.txt) and
[after chains](security-dependency-evidence/chains-after.txt) capture
`npm ls --prefix shale-web nanoid postcss react-router react-router-dom source-map-js --all`.

Vite's existing `postcss:^8.5.15` accepts 8.5.23; that release's `nanoid:^3.3.16` and
`source-map-js:^1.2.1` accept the selected patches. css-tree's `source-map-js:^1.2.1` also accepts 1.2.2.
The manifest minimum becomes `react-router-dom:^7.18.2`; its exact Router dependency is 7.18.2.
No new direct transitive dependencies, overrides, major upgrades or `--force` are needed.
An inspected scoped `npm update` proposed later patches (3.3.20/8.5.29/7.18.4); the final lock
uses the smaller first-patched releases and leaves every other installed package unchanged.

## Applicability to Shale

* **Browser runtime:** App and preview use `ReactDOM.createRoot`, `BrowserRouter`, ordinary
  routes/links/navigation hooks; tests also use `MemoryRouter`. Searches of source/config/manifests
  found no unstable RSC APIs, RSC plugin, server-component handler or React Router server action endpoint.
  The Router advisory explicitly affects only unstable RSC APIs, so its CSRF action-execution path is
  not applicable to this composition. Router is still patched because it is shipped browser code.
  A Vite build plugin collected the actual emitted chunk modules:
  [module inventory](security-dependency-evidence/browser-bundle-modules.json) includes Router and
  excludes Nano ID, PostCSS and source-map-js. Those three are Node tooling here, despite npm's
  production classification of Vite and its dependencies. API responses are not processed as CSS/maps.
* **Development server:** Vite runs CSS transforms in Node. PostCSS's sourceMappingURL auto-loading
  could disclose accessible `.map` contents from malicious CSS; providing `from` alone was insufficient
  for the traversal advisory. Vite's inspected `runPostCSS` supplies `from`/`to`, reducing applicability
  of the separate no-`from` bypass, but is not a substitute for patching. Shale has no user-uploaded CSS
  processor; exposure would require attacker-influenced project CSS/dependencies/maps. The existing
  dev script binds all interfaces; no server security setting is relaxed or changed by this update.
* **Build/test tooling:** PostCSS/source-map-js run during CSS transformation and map consumption;
  malformed indexed maps with extreme section offsets can block the synchronous Node event loop.
  Vitest uses Vite, and jsdom consumes css-tree/source-map-js. Untrusted repository/package/map inputs
  are the relevant boundary, not ordinary authenticated browser navigation. PostCSS imports
  `nanoid/non-secure`, but the inspected consumer calls `nanoid(6)` with a fixed positive size, not an
  attacker-controlled negative size or zero-sized custom generator. No application Nano ID consumers
  were found. Both Nano ID defects are nonetheless removed from the installed toolchain.

These conclusions concern the inspected source and locally built assets, not deployed assets or
live backend acceptance. No sensitive read, domain/admin mutation or audit seam is introduced;
existing authorization, tenant isolation and authoritative audit enforcement remain untouched.

## Audit captures and npm compatibility

| Exact command | Before (exit 1) | After (exit 0) |
| --- | --- | --- |
| `npm audit --prefix shale-web --json` | [14: 11 high, 3 moderate](security-dependency-evidence/audit-before.json) | [0 findings](security-dependency-evidence/audit-after.json) |
| `npm audit --prefix shale-web --omit=dev --json` | [7: 5 high, 2 moderate](security-dependency-evidence/audit-production-before.json) | [0 findings](security-dependency-evidence/audit-production-after.json) |

Counts include inherited parent findings; they are not counts of distinct advisories or proof of
browser exploitability. Both post-update commands establish a clean audit for this captured registry
snapshot. **Remaining npm audit findings: none.** Future advisories may change that result.

Inspected package.json, lockfile v3, Vite config and npm configuration. No project `.npmrc`, Node
version file, packageManager field or top-level engines declaration exists. Registry is
`https://registry.npmjs.org/`; omit is empty, `legacy-peer-deps=false`, `engine-strict=false`.
No configuration, dependency classification, existing `latest` specification, npm version requirement
or application version is changed. All changed packages' engine declarations are identical before/after.
Validation used Node **24.19.0**, npm **11.9.0**, satisfying the existing toolchain (including jsdom's
`^22.22.2 || ^24.15.0 || >=26.0.0`). No repository-root package-lock.json was created.

## Test-impact review and validation

Before editing, reviewed AGENTS/prompt rules completely, development/design/system guidance, Web V2
roadmap, web migration/readiness/deployment guides, web README, Phase 2D review and local selection guide.
Reviewed all five existing web suites and relevant App/session/routes/shell/preview/primitives consumers.
Dependency behavior is covered by existing contracts; no application symbols or expected behaviors change,
so no test assertions were rewritten or weakened.

Commands passed from repository root:

* `npm ci --prefix shale-web --cache /workspace/.cache/npm` — updated lock installed, 99 packages.
  The cache path is an environment-only writable-cache choice; the initial default-cache attempt failed
  because `/home/agent/.npm` was unwritable, and succeeded on retry without dependency/config changes.
* `npm test --prefix shale-web` — **68 tests**, five files, passed.
* `npm run typecheck --prefix shale-web` — passed.
* `npm run build --prefix shale-web` — passed; index.html and foundation.html both built.
* `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown`
  — server area/AuthControllerTest, no full-suite escalation. Browser consumers map to bearer/API
  compatibility; the selected auth suite and critical reactor below cover that retained boundary.
* `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  — **10 passed**.
* `mvn test` — **116 passed**, zero failures/errors/skips; Java 21 via the environment's shale-env.sh.
* Both audit commands above and installed-chain capture — passed after remediation.
* `git diff --check` — passed; only the Router range and five package entries change the dependency tree.

Existing Phase 2B/2D synthetic browser scripts were copied under `work/security` with only their output
directories redirected, preserving historical evidence. Ran with Vite on loopback, independently installed
Playwright 1.64.0/axe wrapper 4.13.0 and Chromium 151.0.7922.173. Phase 2B produced 16 captures and passed
authenticated direct routes/query/hash, navigation/history/focus, logout, other-route smoke and preview
isolation. Phase 2D produced 56 state captures plus eight shell observation records and 56 zero-violation
axe scans; card/background/Enter/Space navigation remains independent of Complete, pending/failure/success
remain authoritative, and preview makes zero API requests across 320/360/768/1280 in both themes.
Both report zero page errors. All API responses were synthetic interceptions.

This rerun does not close physical-device, screen-reader, other-engine, real-host or live-backend
acceptance. The Phase 2B harness's headless zoom shortcuts did not change zoom; no new zoom acceptance is
claimed. Web V2 statuses and its known login return-path gap are unchanged.

## Rollback

Revert this maintenance commit's package.json/package-lock.json changes (or restore those two files
from base `2c5d2e17`), run `npm ci --prefix shale-web`, then rerun web tests/typecheck/build and both audits.
That restores the vulnerable versions, so rollback is temporary and requires renewed remediation.
No SQL, backend, authentication/session protocol or deployment rollback is needed. No merge/deploy performed.
