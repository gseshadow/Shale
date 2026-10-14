# Hosted web operator handoff — 2026-10-12

**Current reconciliation:** user-reported publication to Shale-web-beta / https://app.myshale.com is
recorded below. Earlier preparation/publication-blocked statements describe their original tasks;
this task performs no deployment. Overall security/operational acceptance remains OPEN.
Phase 2 acceptance OPEN; Phase 3 IN PROGRESS; Phase 4 first read slice INCOMPLETE.
Appearance remains provisional; ordinary web delivery is independent of AI/MCP.
This replaces obsolete 5P-4 instructions to create a new host and publish a “read-only beta.”

## Later operator reports — October 12, 2026

The [R4 acceptance ledger](../shale-web/docs/r4-desktop-acceptance.md#october-12-hostedmobile-and-windows-validation-reconciliation)
owns precise attribution and exclusions. User reported Windows committed-routing test (four subtests),
`npm ci` with zero vulnerabilities, typecheck/build, index.html/foundation.html/hashed assets and identical
copied staticwebapp.config.json. Source/tool versions were not supplied; Codex did not rerun these checks.
[#1863](https://github.com/gseshadow/Shale/pull/1863) is now merged/closed; its original blocked checks
remain historical, not retroactively passed. No current PR/review status was changed.

User identified resource **Shale-web-beta**, origin **https://app.myshale.com**, main tracking and existing
workflow deployment on main pushes, and reported merging codex/latest to main and successful publication.
The published interface/Case workspace looked as tested; direct Overview reload worked. Missing
`/assets/missing.js` and frontend `/api/health` were unavailable without successful SPA application fallback.
Successful Overview, POST search-page and GET assigned-page had inspected `Cache-Control: no-store`.
Bounded physical iPhone/Firefox sign-in/search/Overview/Back, portrait/landscape/on-screen keyboard and
Dark navigation/readability/tap acceptance is recorded separately in R4; versions unspecified, no VoiceOver.

These reports supersede the earlier *unknown resource/origin/linked branch/publication* entries for those
facts only. Subscription/resource group/environment ID, exact deployment/source/build/API artifact,
compiled API origin, tool versions, complete CORS, response/MIME/cache/error matrix, logging privacy and
rollback/asset-retention window remain unknown. No universal no-store, tenant/session/audit-fault acceptance,
read-only pilot restriction or phase completion follows. Existing legacy writes/foundation output remain.
Next security work is the [runnable disposable local harness](testing/case-read-local-security-verification.md);
the remaining hosted checks below still apply to their unreported scope. Do not repeat already reported passes.

**October 14 prerequisite update:** the supplied authoritative offline schema and user-run catalog result
confirm missing Users/auditLog/Statuses RLS; local SQL integration remains NOT RUN. Follow the
[remediation assessment](testing/case-read-local-security-verification.md#rls-blocker-assessment--2026-10-14)
for pre-tenant auth/remembered-bootstrap compatibility, targeted coverage and separately labelled candidate
verification before invoking that harness. Keep the original export/harness unchanged. Missing predicates
are a confirmed coverage gap, not proof of an API leak. All accepted reports and remaining host/security
gates above remain; this assessment authorizes no configuration, live connection, fault or deployment.

## Evidence and retained acceptance

Live `codex/latest`: **`33ddd228c789c81b7d7c05cd1d77c232b1d47ff6`**, retrieved through the
connected GitHub app because the workspace Git proxy was unreachable. Imported source objects were
hash-checked; four unavailable review PNG blobs were excluded locally, with source tree entries retained.
Separate branch: `codex/hosted-web-operator-handoff`. GitHub merged metadata and compare merge-base
checks verify these source ancestors, not uploaded binary identity:

| Prerequisite | Merged PR / commit |
| --- | --- |
| R1 exact paging | [#1855](https://github.com/gseshadow/Shale/pull/1855), `fe029cb2d157a3e4f1f86130f3872d731dd2a991` |
| R2 minimized reads / required audit | [#1856](https://github.com/gseshadow/Shale/pull/1856), `862b8671da698da5469e8d28220bca04bad98944` |
| R3 workspace / limited Overview | [#1857](https://github.com/gseshadow/Shale/pull/1857), `e7b5135c99e6f326b077cfbae550f5cc93bf5636` |
| Ordinary-build explicit bindings | [#1858](https://github.com/gseshadow/Shale/pull/1858), `d0d43655765b74e0c1e83ecefe59451307e7b749` |
| Authenticated theme migration | [#1859](https://github.com/gseshadow/Shale/pull/1859), `0572ea7c42c414edc5c9992fdfa1edf1cdf63ac8` |
| R4 desktop acceptance record | [#1860](https://github.com/gseshadow/Shale/pull/1860), `33ddd228c789c81b7d7c05cd1d77c232b1d47ff6` |

[R4](../shale-web/docs/r4-desktop-acceptance.md) retains the user-reported Windows Firefox/localhost →
Azure API search/Overview, Back/Assigned/paging, sign-in return, unavailable-case clearing, persisted
audit/reread, Dark, keyboard, actual 200%/400% zoom and limited Narrator passes. Do not repeat accepted
desktop checks. No application requests, Azure/SQL inspection, build, deployment or fault injection ran here.

Authorities: [roadmap](architecture/shale-web-v2-architecture-roadmap.md),
[first-read contract](architecture/shale-web-v2-first-read-contract.md),
[R3](../shale-web/docs/r3-review.md), [theme review](../shale-web/docs/theme-review.md),
[App Service/session rollout](azure-app-service-deployment.md),
[API readiness](web-api-azure-readiness.md) and [local smoke guide](web-api-local-smoke-test.md).

## Existing host: source configuration versus actual deployment

| Item | Verified source / authorized read-only GitHub evidence | Actual deployed setting |
| --- | --- | --- |
| Resource/hostname/environment | Existing [SWA workflow](../.github/workflows/azure-static-web-apps-proud-water-049dc3010.yml); `proud-water-049dc3010` is a workflow/secret identifier | **USER-REPORTED** Shale-web-beta / https://app.myshale.com. Subscription/resource group/environment ID remain UNKNOWN. |
| Branch/triggers | Push to `main`; PR opened/synchronize/reopened/closed targeting `main`. Upload on push/nonclosed PR; close on closed PR. No workflow_dispatch | **USER-REPORTED** resource tracks main, codex/latest merged to main and successful publication; exact deployed source/artifact UNKNOWN. |
| Locations | `app_location: ./shale-web`; `output_location: dist`; blank `api_location` | Effective host/build settings **UNKNOWN** |
| Builder | `Azure/static-web-apps-deploy@v1`; no explicit install command, Node pin, app_build_command or API-origin environment in YAML. [package](../shale-web/package.json) build is `npm run typecheck && vite build` | Actual install/Oryx/build/tool versions and compiled API origin **UNKNOWN** |
| Historical upload | [main run 37659978509](https://github.com/gseshadow/Shale/actions/runs/37659978509), October 7, SHA `6aef93a0b0f9940911af3a7b3f635570a224cc76`: Build and Deploy Job succeeded | Historical job success does not identify current served bytes |
| Historical PR run | [37659979153](https://github.com/gseshadow/Shale/actions/runs/37659979153), `codex/latest` SHA `c2c77830a1ce7d3994b1ba46a899854015ea1eaf`: Close Pull Request Job succeeded, build skipped | Not an uploaded `codex/latest` build |
| API | Source/runbooks name `https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net`; R4 reports #1858 JAR deployment and successful Overview | Current JAR source/hash/settings/health **UNKNOWN** |

Only GitHub metadata and job-step summaries were read; no job logs/secrets/tokens. No Azure identity
is available here. Do not guess a generated hostname, presume existing configuration is correct or create
a replacement host. This PR targets `codex/latest` and does not trigger the source workflow above.

## Exact local build/artifact procedure (can prepare now)

Use a clean disposable checkout of the reviewed source SHA, unchanged
[package-lock](../shale-web/package-lock.json), and an approved **recorded exact Node/npm pair**
satisfying its engines. Versions are UNKNOWN until the builder is identified; dependencies are locked,
Node/npm are not. Use `npm ci`, never an upgrade/install that rewrites the lock. Require clean
`git status --short` and no local `.env`, `.env.local`, `.env.production` or
`.env.production.local` overrides. `.env.example` is guidance, not automatically loaded.

**`VITE_SHALE_API_BASE_URL` is build-time.** [apiBaseUrl](../shale-web/src/api.ts) trims the value,
uses the documented Azure default when absent/blank and strips trailing slashes; it does not validate
an HTTPS origin. Operator must supply verified HTTPS scheme/host/port without user-info/path/query/fragment
or `/api`. A Java App Service/static-host runtime setting does not change compiled JS: rebuild required.
Every `VITE_*` value is public browser configuration; credentials must never enter it or the artifact.

Run from repository root. These commands only prepare local files; abort on any failure.
The interactive API origin must come from operator evidence. Record it even when using the source default.

### Bash

```bash
set -e
mkdir -p work/hosted-web
git rev-parse HEAD > work/hosted-web/source-commit.txt
sha256sum shale-web/package-lock.json > work/hosted-web/lockfile.sha256
node --version > work/hosted-web/node-version.txt
npm --version > work/hosted-web/npm-version.txt
read -r -p 'Verified HTTPS API origin: ' SHALE_BUILD_API_ORIGIN
test -n "$SHALE_BUILD_API_ORIGIN"
printf '%s\n' "$SHALE_BUILD_API_ORIGIN" > work/hosted-web/api-origin.txt
npm ci --prefix shale-web
VITE_SHALE_API_BASE_URL="$SHALE_BUILD_API_ORIGIN" npm run build --prefix shale-web
(cd shale-web/dist && find . -type f -print0 | sort -z | xargs -0 sha256sum) > work/hosted-web/files.sha256
tar -czf work/hosted-web/browser-assets.tar.gz -C shale-web/dist .
sha256sum work/hosted-web/browser-assets.tar.gz > work/hosted-web/browser-assets.sha256
```

### Windows PowerShell (paste each block separately)

```powershell
New-Item -ItemType Directory -Force work/hosted-web | Out-Null
```

```powershell
git rev-parse HEAD | Set-Content work/hosted-web/source-commit.txt
```

```powershell
Get-FileHash shale-web/package-lock.json -Algorithm SHA256 | Format-List | Out-File work/hosted-web/lockfile.sha256
```

```powershell
node --version | Set-Content work/hosted-web/node-version.txt
```

```powershell
npm --version | Set-Content work/hosted-web/npm-version.txt
```

```powershell
$env:VITE_SHALE_API_BASE_URL = Read-Host 'Verified HTTPS API origin'
if ([string]::IsNullOrWhiteSpace($env:VITE_SHALE_API_BASE_URL)) { throw 'API origin required' }
$env:VITE_SHALE_API_BASE_URL | Set-Content work/hosted-web/api-origin.txt
```

```powershell
npm ci --prefix shale-web
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' }
```

```powershell
npm run build --prefix shale-web
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
```

```powershell
Get-ChildItem shale-web/dist -Recurse -File | Sort-Object FullName | Get-FileHash -Algorithm SHA256 | Export-Csv work/hosted-web/files.csv -NoTypeInformation
```

```powershell
Compress-Archive -Path shale-web/dist/* -DestinationPath work/hosted-web/browser-assets.zip
```

```powershell
Get-FileHash work/hosted-web/browser-assets.zip -Algorithm SHA256 | Format-List | Out-File work/hosted-web/browser-assets.sha256
```

Archive **contents** go at the static host root, including `index.html`, `assets/` and SWA config.
Retain archive SHA-256, per-file inventory/hashes, source SHA, lockfile hash, Node/npm versions, API origin,
command and eventual deployment ID outside `dist`. Verify extracted/uploaded bytes and the origin in
emitted JS without dumping bundles. Toolchains/packaging timestamps can differ: this procedure is
reproducible from recorded inputs, not a promise of byte-identical rebuilds or existing artifact identity.

These are **static browser HTML/JS/CSS**, not the Java API. The executable Spring Boot JAR is built
separately with `mvn -pl shale-server -am clean package -DskipTests`; record its exact versioned
filename/source/SHA-256. Never upload `.jar.original` or a JAR to SWA. No API rebuild/upload occurred;
the existing API must satisfy accepted wire/binding/session/schema/audit prerequisites.

### Asset publication inspection

[Vite](../shale-web/vite.config.ts) explicitly builds **index.html and foundation.html**.
The [foundation preview](../shale-web/src/preview/FoundationPreview.tsx) and synthetic examples are
compiled into a separate public entry (with shared chunks as applicable). Recommend keeping it for
isolated review and excluding it from publication through a separate reviewed build/composition change;
no such switch exists. The only `public/` file is SWA config, copied into output.
Review scripts/screenshots/intercept fixtures under `shale-web/docs/` and test files are outside the
production entry graph and not configured as published assets. Confirm actual `dist` inventory before
upload; publish only reviewed output, never repository/docs/.env/fixture/evidence folders.
No build or actual artifact inventory/hash was established by this documentation inspection.

## Hosted routing, cache, CORS, privacy and scope prerequisites

1. **Publication scope:** the reviewed `/case-workspace` and `/case-workspace/:caseId` limited read-only
   Overview coexist with **legacy read/write routes**. [Registry](../shale-web/src/app/routeRegistry.ts)
   and [clients](../shale-web/src/api.ts) retain My Shale task completion; Case creation/core/assignment/
   status/updates/tasks; Contact/Organization creation/editing; task editing; Team and Settings.
   The entire bundle is not a read-only pilot. Roadmap §8.2 authorization/audit/transaction gaps remain.
   A narrower pilot needs actual reviewed composition/access enforcement, including direct links and
   server authority. Hidden navigation or host login alone cannot restrict domain writes; no config
   switch is invented here.
2. **Routing boundary:** merged [#1862](https://github.com/gseshadow/Shale/pull/1862) adds exclusions
   to [SWA config](../shale-web/public/staticwebapp.config.json) while retaining the `/index.html`
   rewrite. Build/artifact and SWA runtime acceptance remain open; see the validation follow-up below.
   Require HTTPS and reload/direct entry for workspace/Overview and legacy `/cases/:caseId`,
   `/tasks/:taskId`, `/contacts/:contactId`, `/organizations/:organizationId`, `/team/:userId`,
   plus login/safe return/history. Real JS/CSS must retain MIME types; missing `/assets/...` must fail,
   not return 200 HTML. Exclude `/api` and `/api/**` from SPA fallback: absent frontend API paths fail;
   any actual API proxy must preserve JSON/error status. Source fetches the separate absolute API,
   with blank api_location; do not invent a proxy. The merged exclusions still need emitted-artifact
   inspection and actual response/MIME verification before this boundary is accepted.
   Selected browser parsing/byte/schema checks reject HTML, malformed/oversized data and safe service/audit
   failures; no successful empty state, stale Overview or list-row detail fallback may mask failure.
3. **Cache:** recommend revalidated entry HTML/config and immutable content-hashed public assets only
   with an agreed retention window. Actual host/CDN headers and prior-asset retention are UNKNOWN.
   Check old tabs/imports after later authorized publication; retain prior chunks or safe reload
   respecting unsaved work. No offline sensitive cache is approved.
   [Selected successes](../shale-server/src/main/java/com/shale/server/controller/MinimizedCaseReadController.java)
   and [errors](../shale-server/src/main/java/com/shale/server/controller/MinimizedCaseReadErrors.java)
   emit `Cache-Control: no-store`; selected browser requests use no-store too. Applicable proxies/CDNs
   must preserve it and never cache bearer/private responses. AuthController/legacy routes do not
   establish universal no-store in source: login/me/logout, rejection/error and legacy sensitive
   responses require explicit end-to-end evidence and scoped remediation where absent. Static public/
   immutable caching must never apply to authenticated data.
4. **Exact CORS:** reported frontend origin = **https://app.myshale.com**. Effective full CORS list,
   preflight and platform/error behavior remain unverified; successful sign-in/read is bounded evidence.
   Add that exact approved HTTPS origin to the existing effective list, preserving localhost and other
   authorized consumers. No wildcard/guessed hostname. [CORS owner](../shale-server/src/main/java/com/shale/server/config/ShaleServerWebConfiguration.java)
   chooses first nonblank primary env, primary Java property, alias env, alias Java property:
   `SHALE_ALLOWED_CORS_ORIGINS` then `SHALE_SERVER_ALLOWED_CORS_ORIGINS`. Primary shadows alias.
   Mapping is /api/**; methods GET/POST/PUT/PATCH/DELETE/OPTIONS; all header names; cookie credentials
   false; preflight maxAge 3600. Inspect any Azure platform CORS layer too. After separately authorized
   setting change/restart, verify OPTIONS Authorization/Content-Type and exact Allow-Origin on success
   and errors. Each approved preview/custom origin needs its own entry; never replace the whole list
   with only the new host or localhost.
5. **Privacy:** body-only POST search avoids URL query text, not body capture. SearchRequest.toString
   is redacted; selected errors log operation/category/exception class; generic throwable diagnostics
   sanitize messages. These do not establish App Service/proxy/WAF/APM/browser-telemetry privacy.
   Obtain sanitized settings/evidence for each actual layer: body/header capture disabled or excluded;
   Authorization/bearers/passwords/search bodies/sensitive responses excluded; no raw payload/exception
   attachments; bounded retention/restricted access. Legacy GET search can put text in URLs: inspect
   query/referrer suppression for exposed legacy consumers. Keep INFO defaults; no DEBUG/TRACE or
   payload logging to diagnose failures. Record setting names/pass-fail/safe operation/status/error
   category/times/build IDs only. Never export secrets, tokens, connection strings, names/search text,
   raw HAR or sensitive response bodies.

## Historical ordered preparation procedure (before the later reported publication)

1. **Prepare now:** resolve host facts below, decide full-bundle versus separately implemented narrower
   scope, prepare the reviewed local artifact/manifest unuploaded, and arrange rollback retention.
   Resolve routing/asset and sensitive-response cache gaps in scoped implementation PRs.
2. **Prerequisite evidence:** identify compatible API JAR/source/hash, #1858 explicit bindings, three
   /api/v2/cases contracts, durable WEB sessions/cutoff and existing schema/grants/RLS/audit/viewer
   requirements in first-read §11. Collect remaining R4 two-tenant/non-dbo/pooled-context isolation,
   server-owned successful audit context, **isolated** INSERT/commit/lost-ack uncertainty, live
   expiry/revocation/reload/login/logout/storage checks, hosted privacy/security and physical-device/
   remaining browser/AT evidence. Retain SQL scan/plan/collation/paging uncertainty and inherited
   Maven advisories. Only current API/database exists: no production fault tests, fixture/grant/TTL
   changes or disabling audit. Disposable local SQL/API fault-test feasibility is separate preparation.
3. **Publication is blocked:** obtain separate authorization after prerequisite acceptance. Hosted
   smoke evidence necessarily follows a separately authorized publication; this handoff cannot
   pre-certify it or close R4. For manual delivery use the **confirmed existing host/environment's**
   authorized upload channel and upload archive contents at root, checking hashes. Resource/channel/
   operator are UNKNOWN, so no executable Azure creation/deployment command is supplied.
   For workflow delivery first separately review the existing main path, exact source SHA, builder
   environment/API origin and artifact identity. Current workflow does not upload the prepared local
   archive or guarantee a codex/latest build. This PR does not authorize main merge, YAML changes,
   workflow reruns or deployment.
4. **After authorized publication:** record host/deployment/source/archive/API identities. Check TLS/
   HTTP redirect, root/deep-link reload/return in both themes, real/missing assets, safe API JSON/status
   (400/401/403/404/503), exact preflight and no-store through applicable layers, compiled API destination
   and sanitized logging settings. Use local synthetic fixtures for failure scenarios; never induce
   faults on the current API/database. Overview checks need authorized access and required audit;
   committed audit is not proof of human viewing. These host-specific checks add evidence without
   repeating accepted desktop results. Unverified live/device/AT gates remain open.
5. **Rollback:** retain last known-good exact archive/file hashes/chunks, source/lock/tools/API-origin
   records and deployment ID for the agreed retention/API compatibility window (**UNKNOWN**).
   Restore those bytes via the same confirmed channel, recheck entry/assets/origin compatibility, and
   keep old/new API contracts for old tabs and rollback. Do not assume rebuilding a commit reproduces
   its artifact or SWA automatically retains/restores it. Preserve all audit history, R1/security/session
   contracts and SQL protections. API rollback separately obeys durable-session drain/rotation rules
   in the App Service guide; no automatic database rollback.

## Documentation validation

Documentation-only diff: this handoff, web README routing and App Service corrections. No production,
test, workflow, config, dependency/version or SQL changes; roadmap §12 now routes to the concrete next
prerequisite. Test-impact search found no affected runtime contract.

```bash
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --plan-output /workspace/work/hosted-web-selection.json
python3 /workspace/work/check_hosted_docs.py
git diff --check
git diff origin/codex/latest HEAD --check
```

Executed: selector PASS, no full-suite escalation or modified tests; scratch documentation checker PASS
for four Markdown files and 142 relative links/anchors, fences/statuses/whitespace and docs-only scope;
both diff checks PASS. The broad shale-web/** mapping selects AuthControllerTest solely for README
prose and prints generic mvn test. Neither runtime command is relevant to this documentation-only
change; no Maven/npm/browser runtime suite or new build/audit ran. Historical advisory failures remain
attributed to R4 and are not relabelled passing.

## Exact host/operator facts still needed

- Existing SWA subscription/resource group/environment ID and any additional approved preview origins;
  authorized upload channel/operator (no secrets). Resource/origin/main tracking/publication now have
  user-reported evidence above; exact deployment identity remains unknown.
- Active deployment/source/artifact hashes, exact Node/npm/install/build inputs and compiled API origin;
  API executable JAR source/hash and compatible endpoint/session/schema evidence.
- Effective CORS list/precedence/platform CORS; applicable proxies/CDNs, fallback/MIME/cache settings,
  prior assets and agreed rollback/API compatibility window.
- Sanitized access/error/WAF/APM/body/header/query/referrer settings/evidence; full-bundle versus narrower
  scope decision and owners/dispositions for all remaining R4 gates.

## Historical next prompt — static routing boundary prerequisite (now merged)

```text
Repository: gseshadow/Shale
PR base: codex/latest
Task: implement the static host routing boundary before hosted publication.

Fetch live origin/codex/latest. Read AGENTS.md and architecture/codex-prompt-rules.md completely;
follow routing. Read docs/shale-web-deployment.md, Web V2 roadmap/first-read contract and R4 record.
Create a separate task branch. Inspect actual SWA config, Vite entries and existing relevant tests.

Implement a minimal reviewed staticwebapp.config.json boundary: retain index.html navigation fallback
for workspace/Overview and all legacy deep links, but exclude /api, /api/** and emitted static asset/
file paths so missing API/assets cannot return successful SPA HTML. Verify root/nested/API/missing
asset behavior with deterministic local synthetic checks against the chosen SWA semantics. If a local
host emulator is unavailable, mark runtime behavior unverified and give exact operator checks.
Keep the API on its existing absolute origin; do not add a proxy or fabricate a deployed host.

Do not remove legacy routes/writes or claim a read-only pilot, universal no-store, hosted acceptance
or Phase 2/3/4 completion. Foundation publication composition and narrower pilot are separate decisions.
No dependencies/versions, Azure resources, workflow changes, deployments, live API/SQL calls, mutations,
fault injection or merge. Retain accepted desktop results and all remaining R4 gates. Maintain relevant
tests, run selector and relevant checks, validate docs/diff, commit/push and open a PR to codex/latest.
Return routing diff, validation limits and concrete operator checks; no generic reassessment.
```

## Routing boundary implementation (historical unpublished checkpoint, October 12, 2026)

The separate `codex/hosted-swa-routing-boundary` change narrows only `shale-web/public/staticwebapp.config.json` navigation fallback. The Vite output has two HTML entries (`index.html`, `foundation.html`), hashed `assets/` chunks, and a copied SWA config. The configured exclusions are exact `/api`, prefix `/api/*`, emitted `/assets/*`, and a bounded extension set for HTML, scripts, styles, source maps, manifests, images, fonts and other conventional static files. An extensionless browser route (including the supported workspace, Overview, login and legacy deep links) remains eligible for `/index.html`. Real files are served normally with the platform's content type; a missing excluded file must be 404 rather than successful HTML. This does not add host authentication, proxying, API URL rewriting, headers, caching or a publication policy. API calls continue to use the independently compiled absolute HTTPS API origin. Microsoft documents SWA navigationFallback.exclude directory and multi-extension glob semantics: https://learn.microsoft.com/en-us/azure/static-web-apps/configuration#fallback-routes .

**Verification boundary:** repository/config checks and a deterministic synthetic URL matrix cannot prove SWA runtime responses or MIME types. No local SWA runtime or Azure host was exercised in this task. Before separately authorized publication, build with the reviewed lockfile and verify `dist/index.html`, `dist/foundation.html`, `dist/assets/*` and `dist/staticwebapp.config.json`. After that separate publication, record host, source, build and asset hashes. For `/`, `/login`, `/case-workspace`, `/case-workspace/6502`, `/cases/6502`, `/tasks/1`, `/contacts/1`, `/organizations/1`, `/team/1`, and `/settings`, request a direct reload: expected 200 HTML index (then appropriate application behavior with authorized credentials). Request actual emitted JS/CSS/assets: expect 200 and correct JavaScript/CSS/image MIME, never HTML; request nonexistent `/assets/missing.js`, `/assets/nested/missing.css`, `/missing.js`, `/nested/missing.png`, `/api`, `/api/`, and `/api/health`: expect not-found, never 200 index HTML. Inspect `/foundation.html` as a separate existing file; this is not authorization to publish it. Confirm browser requests still target the approved compiled absolute API origin and never the SWA-relative `/api`. Repeat against the exact published host/environment, not just Vite preview.

**Rollback:** retain the previous exact static archive and hashes, source/lock/tooling/API-origin and deployment identity; restore the last known-good assets and SWA config using only the separately authorized existing-host publication channel. Validate old/new JS chunk compatibility and direct navigation after rollback. Rolling back this routing boundary does not roll back API/SQL or change session/audit history. Prior rollback retention and all R4 isolation, required-audit failure/uncertain commit, live session, CORS, no-store/privacy and device/AT gates remain open. Foundation composition and full-bundle legacy writes remain separate decisions.

## Routing validation follow-up — October 12, 2026

**Historical #1863 checkpoint: PARTIALLY VERIFIED.** Later user-reported Windows build/artifact and
bounded hosted passes are recorded above; unreported route tests/statuses/MIME/identity remain OPEN.
Live connector access
confirmed `codex/latest` at #1862's merge commit `0a0bffc49ff7c8694ea0b3f85e4cd2ae3805ce00`,
tree `c864aad09cf2dc52c368e18df5b4eb58953be1d6`. `git fetch origin codex/latest` failed because
the inherited managed proxy on port 8080 was unreachable. Supported GitHub connector reads supplied
the exact source blobs; each imported blob, complete tracked tree and reconstructed signed merge
commit matched its Git SHA. A separate `codex/validate-static-routing-1862` worktree preserves the
original checkout. Its 427 historical PNG/JPG evidence files were not materialized (67 MB including
missing sources); their original tree entries remain intact. This is a source-complete sparse checkout,
not a new screenshot review or proof of historical uploaded binaries. No network settings were changed.

Reviewed the committed routing test/config, two Vite HTML inputs, route registry and its existing tests,
deployment/roadmap/first-read/R4 authorities and repository test-selection rules. No routing/test defect
was established by available checks, so this follow-up changes evidence only. All declared root/login,
workspace/Overview and legacy extensionless route patterns remain outside the exclusions by source
inspection. `/assets/*` covers Vite's configured asset directory in the documented directory-filter
model; **actual emitted paths and copied config cannot be confirmed without the production build**.
No dist files existed after the failed build; no artifact inventory/hash or compiled API origin is claimed.

| Executed command/check | Result |
| --- | --- |
| `node --test shale-web/tests/static-routing.test.mjs` | PASS, exit 0; default runner reported one passing file. |
| `node --test --test-isolation=none --test-reporter=tap shale-web/tests/static-routing.test.mjs` | PASS, all four committed subtests visible; no standalone equivalent used. |
| `node --version`; `npm --version` | `v24.19.0`; `11.9.0`. |
| `sha256sum shale-web/package-lock.json` | `6017c5dec9bc9c5b8f1e39d609d6ae255cd5bd898926aa969cb8d142d255dd9d`; lockfile unchanged. |
| `npm ci --prefix shale-web --fetch-retries=0 --fetch-timeout=15000` | BLOCKED, exit 1: `EPERM` connecting to inherited proxy `172.31.0.29:8080` while fetching locked `xmlchars-2.2.0.tgz` from registry.npmjs.org. |
| `npm run typecheck --prefix shale-web` | BLOCKED, missing `tsc`; installation did not complete. |
| `npm run build --prefix shale-web` | BLOCKED at typecheck, missing `tsc`; Vite did not emit dist. |
| `npm test --prefix shale-web -- src/app/routeRegistry.test.ts` | BLOCKED, missing `vitest`; protects the supported deep-link inventory and recognition. |
| `python -m unittest build/test-selection/test_select_tests.py` | PASS, 24 selector tests. |
| Selector with `--path` for #1862's four connector-verified changed files | PASS; selects server `AuthControllerTest` plus critical `mvn test`, no full-suite escalation. Broad web-consumer mapping is compatibility coverage, not SWA runtime proof. |
| `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`; `mvn test` | BLOCKED, `mvn: command not found`; no new Maven pass or advisory disposition. |
| `command -v swa`; `npm ls -g --depth=0`; installed-tool search under `/workspace` and `/opt/codex` | No supported existing SWA runtime found; global npm listing also failed on absent `/home/agent/.local/lib`. No emulator dependency installed. |
| `python /workspace/work/check_routing_docs.py` | PASS: two edited Markdown files, 134 relative links/anchors, balanced fences, retained phase/gate markers, whitespace and evidence-only scope. Scratch checker uses tracked tree entries for sparse historical evidence paths. |
| `python build/test-selection/select_tests.py --base 0a0bffc49ff7c8694ea0b3f85e4cd2ae3805ce00 --head HEAD --format markdown` | Final evidence-only change selects no affected area or full-suite escalation; generic critical command remains blocked by missing Maven. |
| `git diff --check`; `git diff 0a0bffc49ff7c8694ea0b3f85e4cd2ae3805ce00 HEAD --check` | PASS for the evidence-only diff. |

The merged-change selector command was:

```bash
python build/test-selection/select_tests.py \
  --path docs/shale-web-deployment.md \
  --path docs/architecture/shale-web-v2-architecture-roadmap.md \
  --path shale-web/public/staticwebapp.config.json \
  --path shale-web/tests/static-routing.test.mjs --format markdown \
  --plan-output /workspace/work/routing-1862-selection.json
```

[Microsoft configuration documentation](https://learn.microsoft.com/en-us/azure/static-web-apps/configuration#fallback-routes)
was checked directly: it documents fallback exclusions, directory filters and brace-delimited extension
filters, with existing files served and excluded missing files returning 404 in its examples. Its wildcard
section distinguishes `/calendar/*` from `/calendar`, supporting the separate exact `/api` exclusion.
This supports the chosen syntax/intended boundary; it does **not** execute our root extension filter,
nested wildcard paths, API namespace handling or actual content types. The custom matcher assumes suffix
matching across nested paths. Actual HTTP status, nested wildcard semantics and MIME handling remain
**OPEN**, including both existing HTML entries, real JS/CSS/assets, missing root/nested assets and
`/api`, `/api/`, `/api/health`. Vite preview cannot substitute for SWA acceptance. Microsoft also describes
the supported [local SWA CLI](https://learn.microsoft.com/en-us/azure/static-web-apps/local-development).

**Historical next prerequisite at #1863 (later Windows/host reports reconciled above):** restore the managed proxy's supported registry access, then repeat
lockfile installation, typecheck, route-registry tests and production build on this exact source with
recorded tools/API origin; inspect every emitted path and compare copied config byte-for-byte. Run the
selector's selected/critical checks in an existing Maven-capable environment. For HTTP verification,
use an already available supported SWA runtime against that dist without live API calls; if unavailable,
retain OPEN and use the response/MIME matrix above only after separately authorized publication to the
verified existing host. This task authorizes neither runtime installation nor Azure changes/deployment.
All R4 accepted desktop observations, inherited Maven advisories and unresolved isolation/audit/commit
uncertainty/session/host/privacy/device/AT gates remain retained. Phase 2 acceptance OPEN; Phase 3 IN
PROGRESS; Phase 4 INCOMPLETE. No cache/API proxy/foundation/workflow/dependency/version changes.
