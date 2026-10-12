# R4 desktop acceptance record — 2026-10-12

**Phase 2 acceptance OPEN; Phase 3 IN PROGRESS; Phase 4 first read slice INCOMPLETE.**
Desktop acceptance below is recorded; overall release/deployment acceptance remains open.
Appearance remains provisional; dedicated aesthetic refinement follows functional delivery.
Ordinary web delivery remains independent of AI/MCP.

## Evidence and implemented baseline

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
| Hosted frontend / search-body logging privacy | Actual static host and source/build/API identity remain unverified. Establish approved HTTPS origin, compiled API origin, exact CORS, deep-link reload/fallback for workspace/Overview and legacy routes, correct API JSON failures, no-store through API/proxies, and exclusion/redaction of search bodies/bearers from access/error/tracing/APM logs. Confirm compatible assets and rollback procedure. Localhost navigation is not hosted acceptance. |
| Devices / browser / AT | Physical phone/tablet keyboard traversal, virtual-keyboard and safe-area checks, remaining browser engines and screen-reader combinations, and untested keyboard/announcement/error/editor states in both themes. Windows Firefox/Narrator passes above cover only the reported flow. |

These evidence gaps block overall acceptance and release/deployment readiness; they do not erase the
recorded desktop passes. Representative live SQL plans/scanned-work, collation and concurrent-paging
behavior remain unverified under the R2 checklist. Known inherited advisory failures are carried forward
for disposition, without treating the optional full suite as a newly imposed deployment gate.
No deployment is authorized by this record. Use the [web deployment](../../docs/shale-web-deployment.md)
and [App Service/session rollout](../../docs/azure-app-service-deployment.md) runbooks.

**Minimal isolated fault-test preparation:** there is only the current Shale API/database; no separate
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

## Documentation validation

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

Use [roadmap §12](../../docs/architecture/shale-web-v2-architecture-roadmap.md#12-next-bounded-prompt--hosted-web-delivery-preparation)
to prepare a documentation-only hosted-web readiness handoff: reconcile the existing Static Web Apps
workflow/fallback with the actual host, API origin, CORS, no-store and logging-privacy prerequisites.
Inspect only authorized read-only configuration; record unknowns and an operator verification/rollback
checklist. Do not create resources, change configuration, merge or deploy.
