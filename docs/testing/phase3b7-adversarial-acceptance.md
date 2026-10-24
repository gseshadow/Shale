# Phase 3B.7 adversarial/local acceptance — 2026-10-24

**3B.7 BLOCKED — authoritative Case artifact unavailable. Phase 3B IN PROGRESS.**

This is an acceptance attempt against `codex/latest` commit
`90580f11dcfd3371cac3b7f4bb9dd4819ec5e181` (merged #1917), not technical readiness
approval for controlled deployment or live acceptance. Phases 3B.1–3B.6 retain their
recorded source/local synthetic completion scopes. No production behavior, dependency,
SQL migration, tool, field, authentication rule or activation configuration changes here;
only acceptance tests and documentation/evidence are added.

## Source and authoritative artifact gate

The original `/workspace/Shale` checkout was clean and preserved. CLI fetch failed
once because its configured HTTP proxy was unreachable; a bounded HTTPS diagnostic
confirmed the same failure. The GitHub connector retrieved the current branch,
commit, tree and missing text blobs. All 325 retrieved text blobs match their Git
hashes; root tree `856b031cab8c6c0a375b1f96acf089a4dde93d89` and the reconstructed
signed commit match GitHub. Twelve unrelated PNGs remain unchanged in the index/tree
and were omitted from checkout. Worktree: `/workspace/work/phase3b7`; branch:
`codex/phase-3b7-adversarial-acceptance`. This is connector retrieval, not a successful
CLI fetch. No stale local base supplied acceptance source.

| Artifact requirement | Observation |
| --- | --- |
| Previously approved source | User-supplied schema-only SSMS export `shale-schema-source.sql`; 728,862 bytes; UTF-16LE. Provenance is the existing [Case-read runbook](case-read-local-security-verification.md#offline-artifact-and-evidence-classification) and its October 14 assessment. |
| Immutable original-byte SHA-256 | `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda` — historical recorded hash, **not recomputed in this execution**. |
| Current accessible path | Unavailable. Hidden-inclusive filename searches of readable `/workspace` and `/tmp`, repository trees, and available library/shared/scratch inputs found no source, derived/candidate artifact, DACPAC or BACPAC. No attached artifact was supplied. This does not assert absence from an inaccessible operator archive. |
| Approved equivalent | None located with repository-governed provenance/approval. |
| Derived authoritative hash | Unavailable; no derivation performed. |
| Candidate authoritative hash | Unavailable; no candidate constructed. A migration hash is not a candidate-schema hash. |
| Approved for this execution | Historical source is the identified approved input; bytes unavailable, so identity and suitability cannot be freshly verified. No alternative was approved or substituted. |

`synthetic-case-read.sql` remains explicitly synthetic. It is not promoted to an
authoritative schema; no production assumptions were used to reconstruct one.
No production data or PHI was requested or used.

The exact remaining authoritative gap is isolated derivation/candidate construction
under the existing runbook, preserving real constraints, computed definitions,
`dbo.ToBitNV`, collations, ownership, FK/type/function dependencies and source bytes;
then actual Case/current-status/Users joins, global/tenant Status behavior, tenant A/B/
NULL and foreign-context denial, active/not-deleted filtering, runtime principal and
audit INSERT authority with no dbo bypass. Actual permission DENY, audit atomicity,
lost commit acknowledgment, context reuse and full transport acceptance must be
repeated on that approved candidate. Synthetic SQL cannot close any of these gates.

## Execution and evidence classes

Local Maven 3.9.11/JDK21 stopped before tests: offline Boot 4.1.1 BOM absent. One
bounded online retry attempted to obtain the missing dependency and failed with
`Operation not permitted`. Neither attempt is a runtime pass. No further local
reactor/dependency retry was made.

The task explicitly authorizes GitHub Actions when local Maven/networking is
unavailable. That exception permits a temporary branch-only workflow despite the
standing no-Actions-testing policy in `architecture/codex-prompt-rules.md`; the
standing policy is unchanged. [Hosted run 40132997548](https://github.com/gseshadow/Shale/actions/runs/40132997548)
tests `e67a4e867208bfbbe6f6c1bfa8bdf7402cbe781c`, whose application, test, client,
configuration and SQL blobs are identical to the inspected base. Its sole addition
is [temporary execution scaffolding](https://github.com/gseshadow/Shale/blob/e67a4e867208bfbbe6f6c1bfa8bdf7402cbe781c/.github/workflows/mcp-adversarial-acceptance.yml).
The final PR removes that workflow; both auxiliary branch-only workflows were also
removed after their completed runs. No standing CI or deployment workflow is added.

Evidence labels below are deliberately separate:

- **Static:** source/schema/configuration inspection; no runtime inference.
- **Unit/injected:** executable parser, contract, JDBC/encoder/fault doubles.
- **Synthetic SQL:** actual SQL Server transactions, locks, separate restricted
  identities and replacement JVMs over explicitly synthetic prerequisites/Case data.
- **Synthetic protocol/client:** maintained OAuth servlet flow with synthetic WEB
  identity store; serving loopback MCP/production ACCESS/Case workers and official
  independent clients. Loopback HTTP is not TLS/proxy acceptance.
- **Authoritative:** unavailable for the Case candidate. Prior implementation
  receipts are historical evidence at their recorded scopes, not fresh 3B.7 passes.

The main hosted run **PASSED** on Ubuntu 24.04 / Temurin 21.0.12.1 / Maven 3.10.0,
with a disposable loopback SQL Server 2022 Developer container, fresh databases and
restricted synthetic identities. [Artifact 12576053523](https://github.com/gseshadow/Shale/actions/runs/40132997548/artifacts/12576053523)
retains all 51 partition receipts, focused reports, SQL method/command inventory,
client results, conformance raw checks, npm lockfile, environment and SQL image ID.
GitHub-reported ZIP SHA-256:
`b6335c9e70613c4ed6f7408fe19fb2c82154a2f938ca2c373005ee519cb8788d`;
retention 14 days. The exact container was removed at job end.

| Executed group (overlapping) | Result |
| --- | --- |
| Production compilation | PASS |
| SDK/transport/schema/disclosure/startup smoke | 94 PASS: 44 data + 50 server; zero failures/errors/skips |
| Focused OAuth/ACCESS/shared Case/REST/WEB/DESKTOP server regressions | 257 PASS: 91 data + 166 server; zero failures/errors/skips |
| Broad acceptance partitions | 451 PASS: 283 non-SQL tests plus all 168 SQL invocations across 99 annotated methods, in 51 bounded partitions; zero failures/errors/skips/omissions |
| One critical reactor | 207 PASS, all seven modules BUILD SUCCESS; zero failures/errors/skips; no reactor retry |
| Final selector coverage | All 22 selected security-data classes accounted across main focused/partition/critical receipts and the separate nine-test closeout. The two added SQL methods pass separately. |
| TypeScript 2.4.0 | 6 no-handler transport records, 14 search records, 22 Overview records PASS |
| Python 2.3.0 | Same 6 / 14 / 22 records PASS through independent implementation |
| Full synthetic SQL inventory including new tests | 101 annotated methods / 182 invocations PASS across main + supplement; no authoritative Case claim |
| Selector Python / hosted docs / whitespace | 27 PASS; existing owning-doc paths/fences and base-to-tested-head diff checks PASS |

The critical source is the unchanged production baseline at the named main-run
commit. The supplemental run compiles and executes the final additive fixture blob;
existing methods/helpers and every production blob remain unchanged. No extra
reactor is claimed for test-only additions or final documentation/workflow cleanup.

Conformance raw results: suite **0.2.0-alpha.12**, protocol **2026-07-28**, existing
**no-handler** server-stateless/tools-list fixture, twice (focused and partition run).
Per pass: **23 SUCCESS, 4 FAILURE / Not testable, 5 SKIPPED, 2 INFO**. The four
unavailable diagnostics require test_missing_capability (two checks),
test_streaming_elicitation and test_logging_tool. Server-stateless exits **1**;
tools-list exits **0**. The JSON receipt preserves every raw check ID/status/error.
These failures are not passes; there is **no full finished two-tool conformance claim**.
The scope/Origin/OFF scenarios from the no-handler clients also do not become a
complete matrix against the final two-tool server.

Local static JSON Schema checks: **64 PASS**, explicitly separate from Java
semantic validation, SQL and transport. JSON Schema accepts integral float `1.0`;
the Overview adapter independently rejects floating tokens.

Local independent Python checks: selector **27 PASS**; Case launcher **4 PASS**;
Phase 1R launcher **3 PASS**; auth launcher **35 PASS / 8 SKIP** (43 total).
The eight native Windows/PowerShell/ODBC skips remain unexecuted, not passes.

## Acceptance coverage ledger

A passing existing test family is supplemental evidence for the named layer. It
does not mean every requested vector ran through both independent clients, serving
TLS and the approved Case candidate. The full-stack requirements remain OPEN where
that evidence is absent. The workflow and retained method/partition inventory give
the exact executed methods; groups overlap and must not be added as unique totals.

| Requested area | Evidence / remaining acceptance limit |
| --- | --- |
| Non-bypass SQL identities | Existing foundation/ACCESS/disclosure suites use actual distinct setup, runtime, bootstrap, operator and verifier logins. Procedure/table denials, no sysadmin/db_owner/ShaleAdmins/impersonation and typed audit authority are tested. OAuth servlet fixture combines synthetic operator/runtime authority for setup; it is not sole least-privilege proof. Authoritative Case runtime permissions remain BLOCKED. |
| OAuth → MCP → Case | Existing server fixtures obtain fresh code through maintained authorization/self-consent/S256 filters, exchange for ACCESS/REFRESH, then use ACCESS against mounted MCP and production validator/shared Case worker/quota/audit/handoff. Human durable WEB store is synthetic; OAuth is in-process servlet execution, MCP is serving loopback HTTP. No direct mint or internal worker alone is claimed as end-to-end acceptance. Fully serving TLS flow remains OPEN. |
| ACCESS adversarial | Parser/transport, ACCESS SQL owner/state matrices and mounted OAuth flow cover live ACCESS, wrong kinds, opaque noise, WEB/remembered credentials, malformed/duplicate/oversized headers, expiry, issuer/resource/owner lineage and revoked families. Complete identical-vector authentication matrix through each finished two-tool client remains OPEN. A valid B credential authenticates B only; it is not expected to become A. |
| Confidential assertions | `PinnedClientAssertionTest`, OAuth policy/HTTP and SQL replay tests cover pinned RS256, wrong/revoked/expired/future/weak keys, claims/audience/time, HS/PS/EC, remote jku/x5u/JWK and shared JTI races/replacement. No Basic/shared-secret/remote-key fallback. Full serving complete-stack matrix remains OPEN. |
| Code/refresh | Existing SQL/maintained endpoint regressions exercise replay, PKCE/redirect/client/resource/issuer binding, code expiry/authority changes, rotation, consumed reuse, lost-response retry, concurrent refresh and revoke/refresh. Committed reuse durably revokes the whole family including a concurrent winner. |
| Tenant/user crossover | Existing two-tenant/multi-user SQL fixtures test immutable ownership, foreign grants/sessions/context/audit/quota and ordinary service access. No tool has caller tenant/user/grant/session arguments. Authoritative Case crossover and exhaustive per-client matrix remain OPEN. |
| Scope/catalog | Unit SDK tests exercise search-only, overview-only, both and neither with both handlers. Live SQL reductions/revocations and no-resurrection are tested separately. Independent Overview fixture enables both handlers and checks search/overview/both catalogs; it does not execute the entire search matrix on that same server. Full live reduction/neither matrix per client remains OPEN. |
| Kill switches | Global/tenant/integration/grant/session/user/tenant lifecycle changes are exercised by SQL state/race matrices, including post-commit Overview gates. No positive auth cache; re-enable does not resurrect revoked descendants. Each switch through full transport at every requested race point remains OPEN. |
| Revocation boundary | Synthetic SQL tests cover precommit/final gate rejection, post-commit-before-handoff rejection, and authorized handoff first. This authorizes one immutable internal buffer; bytes already handed to transport cannot be recalled. Full-stack race repetition remains OPEN. |
| Database-UTC expiry race | Existing ACCESS and disclosure SQL tests use real lock waits crossing injected session deadlines and reject after wait; a timeout is not proof. Those internal SQL tests are supplemental; OAuth → MCP → Case lock-wait expiry evidence required by this phase remains OPEN. |
| Search adversarial | Existing semantic/SDK/JDBC/client tests cover success/empty/page 0/page 9/probe/window, absent/null/malformed page/query, minimum Unicode letters/digits, UTF-16 limits, extra arguments, literal escaping and closed errors. Full requested punctuation/depth/type matrix through each two-tool client remains OPEN. No browse-all/total/export/continuation added. |
| Overview adversarial | Independent clients cover direct/unassigned success, missing/foreign/deleted `CASE_UNAVAILABLE`, zero/negative/int32 overflow/string/fraction/null/missing/extra arguments, oversized source, PHI failure and quota. Unit test rejects integral floating token `1.0` too. Full authoritative Case matrix remains BLOCKED. |
| Eight-field output/source validation | Shared DTO/SQL/encoder and wire schemas retain exact required nulls, UTF-16 bounds, Unicode/escaping and stored local timestamp. Overlength values fail without truncation. Existing `MinimizedCaseReadJdbcTest.projectionTransfersOnlyApprovedFieldsWithoutChildHydrationAndPreservesRelationships` tests noncanonical source color becoming neutral null under the approved ordinary Case contract; the encoder rejects invalid non-null color. Neutral null conforms to the emitted schema; this is normalization, not a strict stored-value rejection claim. Authoritative source-width/timestamp/color acceptance remains OPEN. |
| Prompt/instruction injection | Source dispatch has no interpreter, LLM, callback or tool recursion and logs no payload. Existing audit privacy uses prompt/query/credential sentinels; 13 new real synthetic SQL vectors now include all three exact instruction strings through search and Overview. DTOs preserve them unchanged, only the two requested operations encode data, and persisted integration audits exclude them. Complete transport/client repetition remains **OPEN**, no LLM-safety claim. |
| SQL injection / wildcard | Inspected `CaseSummaryDao.selectMinimized` prepares fixed SQL and binds literal input through existing `escapeLike`; no adapter SQL. Existing JDBC tests verify binding/escaping. New real synthetic SQL tests cover literal single/double quotes, percent, underscore, brackets, semicolon, double dash, SQL-looking text and Unicode punctuation: each returns only its exact same-tenant match, excluding a same-tenant decoy and identical foreign row. Approved candidate/both-client repetition: **OPEN**. |
| Quota/concurrency | Full existing synthetic SQL suite exercises durable 200/user, 500/integration, 1000/tenant rows; 10/user, 20/integration, 60/tenant per 60 seconds; 1/user, 2/integration, 5/tenant leases; race winner, replacement JVM, new grants/tokens and no-refund semantics. Transport/process ceiling 5 has separate executed tests. Full client/replica combined stress matrix remains OPEN. |
| Replacement process | Real replacement JVM tests observe quota, code/JTI/refresh replay, revocation and scope reduction. They are not a multi-replica serving deployment or SQL Server restart. Full serving process-replacement acceptance remains OPEN. |
| MCP protocol | Existing SDK/servlet tests cover exact 2026-07-28, old/future versions, legacy initialize rejection, GET/other methods, malformed/empty/batch/oversized/deep JSON, giant IDs, duplicates, metadata/header conflicts, unknown methods/tools, media/Accept, hostile Origin/proxy headers. No downgrade/session/GET-SSE. Exhaustive finished-server per-client matrix remains OPEN. |
| Origin/proxy/TLS | Exact Origin accepted, wrong Origin 403, absent Origin permitted for nonbrowser callers; configured issuer/resource unaffected by Host/Forwarded/X-Forwarded spoofing in servlet tests. Production config requires canonical HTTPS identity. **True serving TLS/trusted reverse-proxy execution OPEN**; HTTP loopback does not close it. |
| Response sizes | Existing tests enforce search 32768/65536 and Overview 8192/32768, including duplicate text/escaping/SDK metadata and faults before handoff. Some boundaries use deterministic encoder/envelope fault injection; exhaustive real source-data just-below/exact/above cases remain OPEN. No partial Case output accepted. |
| Success audit atomicity | Search parent + 0–10 RETURNED children; Overview one original PHI append + parent + RETURNED child, same read transaction. Parent/child/PHI/commit/close faults covered by JDBC injections and synthetic SQL rollback/suppression checks. Authoritative permission DENY and full transport fault matrix remain OPEN. |
| Failure audit/outage | Existing tests cover DENIED/INVALID_INPUT/UNAVAILABLE/THROTTLED/CANCELLED after rollback in separate short transaction; outage withholds results, emits fixed `INTEGRATION_ACCESS_AUDIT_OUTAGE`, no sensitive spool/reread. Full-stack exhaustive matrix remains OPEN. |
| Audit privacy/review | Existing SQL tests inspect every persisted parent/child column for query/Case/credential/prompt/JSON/SQL/URL/email sentinels; typed Case IDs remain expected. Same-tenant active admin review, foreign isolation/nonadmin denial and ≤100-parent bounds are tested. No generic dump path. Authoritative principal/grant acceptance remains BLOCKED. |
| Lost commit acknowledgment | Existing doubles and actual-commit JDBC wrapper preserve committed history, return no protected output, avoid automatic replay/duplicate ALLOWED_READ and never claim delivery. It is an accurately labelled injected lost acknowledgment, not an observed network outage. Full transport/candidate repetition remains OPEN. |
| Pool/async | Existing real synthetic single-SPID A1 → A2 → B1 → A1 plus setup/close/cancellation and concurrent explicit-context tests cover two-key overwrite and no integration SQL key/ThreadLocal authority. Full Case candidate and finished-server async/rejected-task/timeout matrix remain OPEN. |
| REST/WEB/DESKTOP isolation | Existing maintained server auth/controller regressions cover password/me/refresh/logout/durable/remembered desktop and reject live ACCESS through ordinary REST resolver; WEB cannot authenticate MCP. Installed/native client acceptance is not inferred. |
| OAuth regressions | Existing metadata/authorization/consent/code/token/refresh/reuse/revoke/client-auth suites selected; no dependency or provider/authority change. |
| Two independent clients | Exact pinned TypeScript `@modelcontextprotocol/client 2.4.0`, Python `mcp 2.3.0`, finite Streamable HTTP, asserted 2026-07-28. Modern `server/discover` replaces initialize. Existing per-tool/no-handler scenarios are recorded below; neither client completed every main matrix item against one finished two-tool/TLS server. **Full independent-client acceptance OPEN.** |
| Conformance | Pinned `@modelcontextprotocol/conformance 0.2.0-alpha.12`, spec 2026-07-28, existing server-stateless/tools-list **no-handler** fixture. Raw results remain raw; finished two-tool full conformance **NOT TESTABLE with the existing fixture / OPEN**. No new diagnostic tools or capabilities added to satisfy a suite. |
| Performance sanity | Five fresh samples each of real ACCESS validation and search/Overview including quota/audit/final gate are recorded below at internal synthetic-service scope. Serving tools/list and separately isolated audit/quota transaction timings remain **OPEN**. No endpoint SLA or full load-test claim. |
| Retention/legal | Proposed seven-year integration-audit retention from §10.5. **tenant/legal retention sign-off: OPEN**; no actual approval located, no purge implementation. |
| Deployment/config/defaults | Reviewed below; no SQL/deployment/enablement performed. |

Cross-identity negatives mean a B credential/reference cannot acquire A authority
or mutate A's immutable invocation tuple. A separately live authorized B ACCESS
bearer authenticates B under the established bearer model; possession alone cannot
identify the human holding it. Stolen valid ACCESS replay until expiry/revocation
remains the documented residual risk, not a proven sender-identity guarantee. This
phase adds no sender-constrained credential feature or alternate authority input.

## Additional focused checks and performance samples

[Supplement run 40133653040](https://github.com/gseshadow/Shale/actions/runs/40133653040)
tests `1b79bd92271525d98e834935108f90a8a21b5b06`: **14 PASS**, zero failures/errors/skips
(13 literal/instruction-text vectors plus one performance test). The retained
`AiSecurityLocalSqlServerTest.java` blob is
`666f9aca92d0373b912d05c2a2427287f8e23f3d`; existing test/helper bodies are unchanged.
Desktop enrollment/lifecycle, remembered recovery/diagnostics and login source
regressions separately pass **21 tests** (2 UI + 19 desktop), no failures/errors/skips.
These are source tests, not installed Windows acceptance.

[Supplement artifact 12576730147](https://github.com/gseshadow/Shale/actions/runs/40133653040/artifacts/12576730147)
ZIP SHA-256: `a81deb0e108001c2d1ece1d2bb7584fe88a2e13a2d01662a2a6fb9eb43e521d9`.
It retains raw nanosecond samples, logs, SQL image ID and Surefire XML for 14 days.
[Selector closeout run 40133772348](https://github.com/gseshadow/Shale/actions/runs/40133772348)
adds **9 PASS** in AuthCredentialLookupTest, PreTenantAuthenticationModuleContractTest
and RuntimeSessionServiceTimingTest, the selected classes absent from the broad
main focused batch/default critical catalog. [Artifact 12575709800](https://github.com/gseshadow/Shale/actions/runs/40133772348/artifacts/12575709800)
ZIP SHA-256: `a26a84a86c36cecc6574e56af7a798336f750226974facbd2efc77af0df10e59`.
No full reactor was used to debug or rerun these independent focused checks.

Performance environment: GitHub-hosted Ubuntu 24.04, Temurin 21.0.12.1, Maven 3.10.0,
disposable loopback SQL Server 2022 Developer; fixed tiny synthetic data and distinct
runtime/bootstrap SQL logins. Five sequential samples per operation, no excluded
warm-up sample. [Raw samples](phase3b7-performance.json) use System.nanoTime around
actual synchronous production service calls, excluding fixture creation.

| Operation / scope | n | Min ms | Median ms | Max ms |
| --- | --- | --- | --- | --- |
| Opaque ACCESS validation, confined bootstrap + runtime live gate | 5 | 13.224 | 15.683 | 19.365 |
| Search shared service, quota/read/audit/encoding/final gate | 5 | 26.480 | 30.642 | 143.088 |
| Overview shared service, quota/read/PHI/access audit/encoding/final gate | 5 | 37.938 | 39.049 | 136.488 |

These samples are a sanity check, not MCP network endpoint latency or production
SLA/load acceptance. Serving tools/list and isolated audit/quota transaction timings
are unmeasured. Concurrency correctness is tested separately. No raw Case or
credential values enter the performance receipt.

## SDK review

Live official GitHub release metadata was checked on 2026-10-24. Pinned
`io.modelcontextprotocol.sdk:mcp-json-jackson3:2.2.0-M1` / `mcp-core:2.2.0-M1`
remain prerelease. The release list returns `v2.1.0` as the newest stable 2.x
release; its complete untruncated tagged tree has no `io/modelcontextprotocol/modern`
source. No newer stable exact-modern-server candidate was located; no upgrade was made.

The [milestone release](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v2.2.0-M1)
declares the modern server, no modern client/tasks/SEP-2243 custom headers and an
upstream 37-scenario result. That score is not Shale conformance. Relevant open
issues include [#1269](https://github.com/modelcontextprotocol/java-sdk/issues/1269)
(`x-mcp-header` schema validation) and
[#1198](https://github.com/modelcontextprotocol/java-sdk/issues/1198)
(client capability enforcement before handler side effects). Shale's fixed tools
declare no such custom headers or client-callback capability and reject Mcp-Param
headers. That limits applicability; it does not approve the prerelease for production
or establish that the upstream issue inventory is exhaustive. Current Shale SDK/parser/
servlet/client regression receipts establish only the exercised compatibility scope.

## Deployment prerequisites and configuration review

The [SQL README](../sql/ai-integration/README.md) and
[future deployment runbook](../azure-app-service-deployment.md#future-ai-oauth-deployment-sequence--through-phase-3b2d)
agree on this accumulated manual migration order:

1. `phase3b1.sql`
2. `phase3b2b.sql`
3. `phase3b2c1.sql`
4. `phase3b2c2.sql`
5. `revocation.sql`
6. `phase3b2d.sql`
7. `phase3b3.sql`
8. Cumulative `verify.sql` and effective non-bypass permission/catalog checks.

All paths are under `docs/sql/ai-integration/`. These are accumulated candidate
files, **not a claim that all seven are unapplied on any real database**. Deployed
receipt inventory is unavailable. Operators must identify and apply only unapplied
migrations in that order; an older installer must not repair a newer catalog.
Existing auth Stage A/B, compatible fleet and Phase 1R prerequisites remain separately
governed. Phases 3B.4–3B.7 add no migration. No production SQL was executed.

Before any separately authorized later live stage, operators must supply:

| Configuration/authority | Required current model |
| --- | --- |
| Canonical origin | `shale.ai.oauth.public-origin`, one canonical production HTTPS origin, no path/trailing slash/aliases; derived issuer `/oauth/mcp` and resource `/mcp`. Never request Host/Forwarded values. |
| OAuth switches | Explicit composition/authorization/exchange enablement; separate from AI capability/MCP. Missing origin with composition ON fails startup. |
| SQL identities | Existing tenant runtime plus separately configured `.oauth.bootstrap.jdbc-url`, `.username`, `.password` restricted to confined resolvers; separately governed setup/operator/review authority. No shared auth-pool expansion. Do not record secrets in docs. |
| MCP/Origin | Explicit `shale.ai.enabled`, `shale.ai.mcp.enabled`; exact `.allowed-origins` (≤8), production HTTPS, no wildcard/null aliases. Missing Origin follows nonbrowser contract and grants no authority. |
| Registration/client | Audited same-tenant admin registration; exact redirects (≤5), PUBLIC none or CONFIDENTIAL pinned RSA RS256 public keys, validity/key-rotation inventory and secure client private-key custody. No shared secret/Basic/JWKS fallback. |
| Tenant/user | Restricted global capability, explicit active tenant policy, current user self-consent/grant/session; no automatic tenant/integration seed or administrator consent-as-user. |
| Handlers | Explicit search and Overview switches. Current handlers additionally require `test` profile and TEST identity and refuse prod/azure even if forced ON. A later production activation design/review is still required; do not deploy a test profile to bypass it. |
| TLS/proxy | Approved serving HTTPS/TLS termination and proxy/header/logging policy; exact identity independent of forwarding; real TLS/proxy test evidence remains OPEN. |

Packaged `application.properties` explicitly sets global AI, MCP transport, search,
Overview and all three OAuth switches to false. Its SHA-256 is
`253df5409a8d2888a367853b059c198237381fa29be9c84fdbb638320066f799`.
SQL foundation creates only global Enabled=0; missing tenant policy is OFF and no
registration/grant is seeded. Default constructors further deny. Static exact-field/
resource checks passed; hosted configuration/startup/SQL tests supply their separate
executed proofs. An ordinary package cannot expose Case data through accidental
single-flag activation, and current production profiles cannot activate either handler.

## Disposition and next work

No material implementation defect was established by the executed checks; no
production fix or existing regression expectation change is included. The additive
13-vector parameterized test and five-sample performance test reuse the existing
disposable fixture, production Case worker/encoder and restricted runtime. Their
pre-edit impact inventory covered all existing disclosure/privacy/quota/ACCESS tests,
fixture helpers, Case projection/escaping, encoder and selector security-data routing.
No constructor, DTO, production SQL/binding or existing test/helper behavior changes. This is not proof of
absence of defects. In particular neutral status-color normalization is disclosed
above rather than reported as a strict invalid-source rejection test.

Resume **local acceptance only** after recovering the governed approved schema-only
artifact (or already explicitly approved equivalent), verifying its immutable bytes,
and constructing the isolated candidate under the existing runbook. Close the full
serving OAuth/two-tool/two-client, injection, exact response-boundary, lock-wait expiry,
TLS/proxy, conformance applicability and performance gaps recorded above. Preserve
raw diagnostic failures/skips and resolve any real defect with a narrow regression.
Tenant/legal sign-off and prerelease production approval remain separate OPEN gates.
Do not recommend real enablement while this phase is blocked.

Exactly `search_cases` and `get_case_overview` exist; no broader Case fields, tools,
resources/prompts, mutations or other domains were added. No real tenant/integration
was enabled. No production/Azure SQL, deployment, installer or version publication.

## Reproduction and final checks

Full main-run argv and bounded partition inventory are retained in the frozen
workflow and evidence artifact; the [structured receipt](phase3b7-verification.json)
retains client records, raw conformance checks and 94 source hashes. Representative
exact commands (SQL credentials privately supplied only to the disposable fixture):

```bash
mvn -B -ntp -U -pl shale-server -am -Dmaven.test.skip=true install
mvn -B -ntp -pl shale-data -am '-Dtest=AiSecurityLocalSqlServerTest#phase3b7*' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -pl shale-data -am -Dtest=AuthCredentialLookupTest,PreTenantAuthenticationModuleContractTest,RuntimeSessionServiceTimingTest -Dsurefire.failIfNoSpecifiedTests=false test
xvfb-run -a mvn -B -ntp -pl shale-desktop -am -Dtest=DesktopSessionEnrollmentClientTest,DesktopSessionEnrollmentLifecycleTest,DesktopRememberedRecoveryTest,DesktopRememberedSignInDiagnosticsTest,LoginRememberedSignInContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format json --plan-output /workspace/work/final-selection.json
xvfb-run -a mvn -B -ntp test
python3 -m unittest discover -s build/test-selection -p test_select_tests.py
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/phase1r-verification -p 'test_*.py'
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 /workspace/work/check_phase3b7_contracts.py
git diff --check origin/codex/latest HEAD
```

Final local checks also parse evidence/contract JSON, review changed-file/test
mapping, verify Markdown fences and relative paths, and confirm unchanged production/
SQL/configuration/dependency blobs. The initial scratch link checker misread a SQL
catalog example as a Markdown link; excluding code examples fixed that checker,
without changing repository content. The ordinary local commit builder rejected
the known omitted PNG blobs; Git write-tree --missing-ok/commit-tree preserved their
original tree entries. Connector publication retains all original binary entries.
No unavailable check is converted into a pass.
