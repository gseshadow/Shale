# R4 desktop acceptance record — 2026-10-12

**Phase 2 acceptance OPEN; Phase 3 IN PROGRESS; Phase 4 first read slice INCOMPLETE.**
Desktop, hosted and bounded physical-device acceptance below are recorded; overall acceptance remains open.
Appearance remains provisional; dedicated aesthetic refinement follows functional delivery.
Ordinary web delivery remains independent of AI/MCP.

## October 12 hosted/mobile and Windows validation reconciliation

**USER-REPORTED; not Codex reruns.** These later reports supplement the earlier desktop/audit/Firefox
zoom/Narrator observations and historical synthetic evidence below. They do not identify exact source,
deployed commit/artifact, Node/npm versions, phone model, iOS or Firefox versions.

| Report | Recorded pass and attribution |
| --- | --- |
| Windows local | User ran the committed static-routing test: all four subtests passed. `npm ci` succeeded and reported zero vulnerabilities; web typecheck and production build passed. Output included index.html, foundation.html and hashed assets. Binary public-versus-dist staticwebapp.config.json comparison found no differences. No route-registry suite, Maven or every #1863 check is inferred. |
| Hosted publication | User identified **Shale-web-beta**, **https://app.myshale.com**, main tracking with existing workflow deployment on main pushes, and reported merging codex/latest into main and successful publication. Published interface looked as tested, including Case workspace. This task did not merge or publish. |
| Hosted routing | Overview direct reload worked. `/assets/missing.js` and frontend `/api/health` were unavailable rather than successful SPA application fallback. Exact statuses/MIME and the rest of the root/nested/legacy/asset matrix were not supplied. |
| Selected hosted successes | User inspected `Cache-Control: no-store` on successful Overview, POST search-page and GET assigned-page responses. This covers those inspected responses, not every response/error/auth/legacy/proxy path or universal no-store. |
| Physical iPhone / Firefox | Hosted sign-in, search, Overview and Back worked. Requested portrait/landscape and on-screen-keyboard checks looked good; Dark-mode navigation/readability/tap flow worked as intended. Exact device/software versions unspecified. No VoiceOver, all-editor, all-device or comprehensive browser acceptance claimed. |

Live inspection found [#1863](https://github.com/gseshadow/Shale/pull/1863) **merged/closed** on October 12;
current task base is `70599324cf843b2dd4f51dfb44e6094502354e25`. Its historical Codex Node/selector passes
and proxy/missing-tool blockers remain attributed to that task. The later Windows build/artifact reports
add acceptance without rewriting those executed outcomes, closing unreported checks, changing PR/review
status, or creating a conflicting open-PR ledger. No issue/PR closure or review mutation occurred here.

Remaining host gates: exact source/build/API identities and compiled destination; complete routing/MIME,
CORS/preflight/error policy; selected failures/auth/legacy end-to-end cache handling; entry/asset cache and
compatibility/rollback retention; search-body/bearer/query/referrer logging privacy at each actual layer.
Remaining device/AT gates include VoiceOver, tablet/other devices/engines and untested editors/errors/keyboard/
safe-area/announcement flows. Reports establish none of tenant isolation, session expiry/revocation,
required audit failure/commit uncertainty or logging privacy. Appearance stays provisional.

The [local SQL harness/runbook](../../docs/testing/case-read-local-security-verification.md) now implements
the isolated verification preparation recommended below. It requires authoritative schema-only definitions,
synthetic fixtures and a marked local disposable database; fault controls are test tooling only. SQL
execution is **NOT RUN** without that approved offline baseline, and local passes cannot accept deployed Azure.
The remaining security gates below persist; Phases 2/3/4 are not complete.

## Evidence and implemented baseline

### October 14 Case-read RLS blocker assessment

The user-supplied authoritative schema-only SSMS export and user-run Shale catalog result confirm
no security predicates for Users/auditLog/Statuses. This is a confirmed coverage gap, not an observed
API leak. The [owning runbook assessment](../../docs/testing/case-read-local-security-verification.md#rls-blocker-assessment--2026-10-14)
reviews explicit application predicates, auth/session/bootstrap/desktop/audit paths and unknown grants;
it recommends bounded pre-tenant auth compatibility before targeted strict/overlay FILTER attachment.
The original snapshot and #1865 harness stay unchanged. **No local SQL integration run occurred.**
All user-reported desktop/audit/Windows/hosted/iPhone passes above remain accepted within their stated
scope. No new security/SQL/fault/deployment acceptance follows from this documentation assessment.
Phase 2 OPEN; Phase 3 IN PROGRESS; Phase 4 INCOMPLETE; appearance provisional. Follow the prerequisite
implementation prompt in the runbook before candidate-schema execution; deployed and other gates remain.

**USER-REPORTED LIVE OBSERVATIONS**, October 12, 2026: Windows desktop, Firefox,
localhost browser frontend calling the deployed Azure API. Codex did not rerun these checks.
Browser version, exact test times, artifact hashes and screenshots were not supplied. No audit
actor verification or broader assistive-technology coverage is inferred. No names, emails,
search text, tokens or raw Case responses are retained here.

Live base `0572ea7c42c414edc5c9992fdfa1edf1cdf63ac8` was retrieved through the connected GitHub
app (workspace Git proxy unreachable); task branch `codex/r4-desktop-acceptance` starts there.
GitHub merged-PR metadata and compare merge-base checks establish the following source ancestry,
not the identity of uploaded binaries:

| Implemented prerequisite | Merged PR / merge commit |
| --- | --- |
| R1 exact legacy SQL paging | [#1855](https://github.com/gseshadow/Shale/pull/1855), `fe029cb2` |
| R2 minimized tenant-wide reads / required Overview audit | [#1856](https://github.com/gseshadow/Shale/pull/1856), `862b8671` |
| R3 bounded Case workspace / limited read-only Overview | [#1857](https://github.com/gseshadow/Shale/pull/1857), `e7b5135c` |
| Ordinary-build binding correction | [#1858](https://github.com/gseshadow/Shale/pull/1858), `d0d43655`; explicit `caseId`, `page`, `size` bindings retained |
| Authenticated Light/Dark migration | [#1859](https://github.com/gseshadow/Shale/pull/1859), `0572ea7c`; shared authenticated theme bridge retained |

The [first-read contract](../../docs/architecture/shale-web-v2-first-read-contract.md),
[R3 review](r3-review.md) and [theme review](theme-review.md) own implementation and earlier validation.
R4 changes documentation only; no production code, tests, dependencies, SQL, configuration, versions,
deployment or live fault injection. Presentation/navigation add no audit events or mutations;
the existing server-owned required Overview audit and summary-read exemption remain unchanged.

## User-reported acceptance passed

| Area | Reported observation / bounded outcome |
| --- | --- |
| Deployment and correction | API JAR uploaded and Azure restarted. Health returned OK; all three minimized Case endpoints appeared in Swagger. Initial login timed out; a deliberate second attempt succeeded. Overview initially returned HTTP 400 / `invalid_case_request`; after deploying #1858's corrected API JAR, Overview loaded successfully. Pulling #1859 and restarting the local frontend corrected Dark presentation. The timeout cause and first attempt's server issuance outcome remain unknown. |
| Discovery and navigation | Tenant-wide case-name search and limited Overview passed. Back restored search/results; Assigned loaded. Next/Previous paging passed. Searching directly for a Case seen beyond the first displayed page succeeded. |
| Sign-in return / unavailable Case | Logout → direct Overview URL → sign-in → requested Overview passed. Missing-case response displayed no stale details and retained sign-in. A valid-ID 404 is unavailable-case feedback evidence, not proof of cross-tenant or deleted-case isolation. Logout/re-login does not prove timed expiry or remote revocation. |
| Persisted audit | Overview entries persisted in `dbo.AuditLog` for Case 6502. Explicit **Read Overview again** produced a new entry. Observed audit timestamp **15:25** corresponded to Denver **09:25**; this records the user's observation only, without a reviewed system-wide timestamp contract. Actor/context/event sanitization and viewer verification remain separate; successful persistence does not prove fail-closed fault behavior. |
| Dark presentation | User reported all pages look correct in Dark after #1859. This is the reported desktop presentation pass, not final aesthetic approval or acceptance of every route/editor state in every environment. |
| Keyboard | Case workspace Tab/Shift+Tab/Enter flow passed, including visible focus and return focus on the opened result. |
| Actual Firefox zoom | Actual 200%/400% zoom passed for the requested search/results/Overview/navigation flow. |
| Windows Narrator | Announced the tested search-result status and identified the Overview heading/controls. No broader speech, error-state, browser/device or AT acceptance is claimed. |

## Synthetic evidence (separate, historical)

R1/R2 JDBC, mocked HTTP/OpenAPI and transaction doubles; R3 actual-App fixtures and Chromium browser/
zoom checks; and #1859's intercepted route/editor/theme matrix remain **synthetic**, with exact results
in their owning reviews. R4 did not rerun Java, browser, npm audit or runtime suites. Those artifacts
are not upgraded to deployed SQL/RLS, audit-fault, session, hosted frontend, device or speech evidence.

Known inherited Maven advisories remain: R1/R2's **12 data test failures** (downstream modules not run),
**three ApiExceptionHandlerTest failures**, and #1858's ordinary-build legacy application-release
OpenAPI parameter failure reproduced on its unchanged base
([contract §13](../../docs/architecture/shale-web-v2-first-read-contract.md#13-minimized-overview-ordinary-build-400-correction--2026-10-12)).
Prior focused/critical passes remain attributed to their tasks; the full Maven suite is not passing.
Advisory failures need tracked disposition, not an unrelated repair or a new all-tests run for R4.

## Remaining verification and release/deployment gates

| Gate / blocker | Evidence still required |
| --- | --- |
| Tenant isolation | Authorized two-tenant **non-dbo** RLS enforcement and explicit root/join/strict-versus-overlay predicates; active/removed actors and assigned/unassigned/closed/deleted cases; search/page/hasMore/direct lookup and audit visibility. Alternate tenant/actor on pooled connections and test null/mismatched context. One missing-case result and dbo catalog inspection cannot close this gate. |
| Required audit / commit uncertainty | Verify successful rows' server-owned tenant/actor/Case/event/sanitized metadata and viewer meaning. In isolation, required INSERT failure, commit failure and lost commit acknowledgement must withhold all Case data, return safe failure and permit only deliberate Retry. A rollback attempt cannot prove an uncertain commit was undone; retain committed history and reconcile uncertainty without automatic replay. |
| Live web session | Timed expiry and explicit re-login on the next request; authorized remote revocation and rejection of the old bearer after confirmed logout; reload/startup verification uncertainty, login/late-response/issuance uncertainty, storage failure and truthful remote-logout outcomes. Retain current TTLs, no refresh and no replay; historical JavaFX session closeouts do not accept these browser paths. |
| Hosted frontend / search-body logging privacy | HTTPS origin/resource, successful publication, Overview reload, two fallback exclusions and three successful no-store responses now have user-reported evidence above. Exact source/build/API identity/compiled origin, complete CORS/routing/MIME/safe JSON/error/auth/legacy cache matrix, privacy layers, compatible assets and rollback evidence remain open. |
| Devices / browser / AT | Bounded iPhone Firefox orientation/keyboard/Dark tap flow now has user-reported acceptance above. Physical tablet/other devices, remaining browser engines and screen-reader combinations, explicit safe-area coverage, and untested keyboard/announcement/error/editor states in both themes remain open. Earlier Windows Firefox/Narrator passes cover their reported flow only. |

These evidence gaps block overall acceptance and release/deployment readiness; they do not erase the
recorded desktop passes. Representative live SQL plans/scanned-work, collation and concurrent-paging
behavior remain unverified under the R2 checklist. Known inherited advisory failures are carried forward
for disposition, without treating the optional full suite as a newly imposed deployment gate.
No deployment is authorized by this record. Use the [web deployment](../../docs/shale-web-deployment.md)
and [App Service/session rollout](../../docs/azure-app-service-deployment.md) runbooks.

**Historical minimal isolated fault-test recommendation (superseded by the runnable local harness above):** there is only the current Shale API/database; no separate
test target exists. Plan a disposable local SQL Server database and loopback-only API using the relevant
existing schema/RLS/audit/session dependencies, synthetic two-tenant fixtures and non-dbo runtime identity,
with no production connections or credentials. First establish schema/fixture feasibility and an approved
boundary; a second Azure environment is not mandatory. A scoped connection/transaction harness can then
exercise denied INSERT, failure before commit and commit-then-lost-acknowledgement against that disposable
target, inspecting durable outcome separately from response withholding. Doubles alone stay synthetic;
this does not close deployed RLS/session/host gates. This is a recommendation, not executed testing or
authorization to alter production grants, disable audit, create production fixtures, change TTLs or
perform destructive fault tests.

**Later improvements:** dedicated aesthetic refinement after functional delivery; broader parity,
automatic refresh coordination, generic cache/forms work and optional native/PWA/AI/MCP remain separately
scoped roadmap work. Required accessibility, privacy, isolation and audit evidence are not deferred polish.

## Historical R4 documentation validation

Changed-file review contains six Markdown files only and changes no production/test contract.

```bash
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --plan-output /workspace/work/r4-selection.json
python3 /workspace/work/check_r4_docs.py
git diff --check
git diff origin/codex/latest HEAD --check
```

Executed: selector PASS, no full-suite escalation or modified tests; scratch documentation checker PASS
for six files, 211 relative links/anchors, fences, whitespace and phase/evidence/gate markers; diff checks
PASS. The broad `shale-web/**` map selects server/AuthControllerTest solely for three review Markdown
paths and prints generic `mvn test`; neither runtime command is relevant to recording user evidence.
No Maven/npm/browser runtime suite ran, per the documentation-only scope. The inherited theme bundle
link was reconciled: the claimed archive is absent from this base, so its invalid local link was removed.

## Next bounded prompt

Use the [local SQL runbook](../../docs/testing/case-read-local-security-verification.md) and
[roadmap §12](../../docs/architecture/shale-web-v2-architecture-roadmap.md#12-next-bounded-prompt--local-sql-execution-and-remaining-acceptance)
for the [pre-tenant auth prerequisite prompt](../../docs/testing/case-read-local-security-verification.md#next-implementation-prompt)
before the targeted RLS migration and candidate-schema harness execution. The supplied authoritative
export confirms the gap and must stay unchanged; no local SQL result is inferred. Record source/schema/
tool identities and distinguish real SQL from injected commit acknowledgement in later verification.
Preserve deployed RLS/performance/session/host/privacy/device/AT gates. No merge or deployment.
