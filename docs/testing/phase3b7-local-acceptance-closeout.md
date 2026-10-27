# Phase 3B.7 recovered-input local acceptance

Date: 2026-10-26 (America/Denver). PR: [#1923](https://github.com/gseshadow/Shale/pull/1923).
Branch: `codex/phase3b7-local-acceptance`; base: `0eed0e4a709e4d858065893b5889a603feb8d91a`.

**3B.7 COMPLETE (local/adversarial acceptance).**
**Phase 3B COMPLETE at pre-deployment/local acceptance scope.**
All ten Category A gates PASS. Eight Category B gates remain explicitly OPEN before real pilot.

This receipt supersedes current-status statements in the earlier ledger; their failed and blocked
execution evidence remains historical. The reattached 728,862-byte source is hash-verified before
decoding: `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`.
The reviewed preparer reproduces derived hash
`40a665caefb3203a6c364d0caac3fadcc1a58d24a66a994f48e058b73ae6f243` and candidate hash
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
Runtime confinement and expiry serving remain **FIXED at #1922's recorded scope**; the recovered-input
regressions do not reopen those findings. Uploaded SQL is data, not an instruction source.

Only local tests, client harnesses and documentation change. Production code, SQL migrations,
dependencies and packaged OFF defaults do not change. Actual SQL uses a task-owned loopback SQL Server
container, the authoritative candidate, synthetic rows and distinct restricted AI/catalog identities.
Owner credentials provision fixtures and inject faults; protected requests use the procedure-only AI
login and live attestation. No source or derived private schema, bearer, proof, private key or password
is committed. No production/Azure SQL, JAR upload, real tenant, deployed global/MCP/handler enablement, live
credentials, installer or version publication occurs.

## Gate classifications

The complete 18-row A/B inventory, with per-item rationale and evidence, is recorded in
`recoveredInputAcceptance.gateClassifications` in [the verification JSON](phase3b7-verification.json).
Category A consists of source identity, local TLS/OAuth composition, identical clients, response
boundaries, dedicated pool/async, durable quota/replacement JVM, audit/privacy/admin review, applicable
conformance, bounded performance and regressions. Category B remains OPEN before real pilot.

| Remaining ledger item | Category/status | Rationale and evidence |
| --- | --- | --- |
| tls-proxy-and-serving-oauth: isolated serving TLS/proxy and local OAuth composition | A — PASS | Roadmap §10.11 explicitly requires serving server/TLS/proxy in 3B.7. Header-only servlet tests do not close real TLS, canonical origin, hostile Host/Forwarded/X-Forwarded/Origin and HTTPS enforcement. The finished confined server requires the private candidate. Eight real certificate/hostname-verified Caddy TLS cases, including protected Overview; maintained local OAuth issuance. |
| tls-proxy-and-serving-oauth: actual public deployment and real human/tenant flow | B — OPEN | Real ingress certificates, proxy trust/configuration and identity integration are environment/operator acceptance; no live tenant is permitted in this task. |
| complete-identical-client-corpus | A — PASS | Both finished clients must execute the same complete authentication, catalog, search, Overview and live-authority matrix. #1922's narrower two-client expiry proof remains FIXED but does not close this corpus. Same 66-vector corpus through both official clients: 132 PASS records. |
| response-boundaries | A — PASS | Deterministic source/encoding/full-envelope below/exact/above limits, Unicode expansion, nulls and valid/invalid source widths protect disclosure; unit injection alone does not close finished serving acceptance. Search 32/64 KiB; Overview 8/32 KiB. Real source structured and exact search wire boundaries; Overview mathematical/source bound plus injected measurement seam. |
| pool-async | A — PASS | New dedicated AI pool must pass concurrent A/B, before/during-SQL cancellation, timeout, rejected task, reuse after failure, close/cleanup faults and interruption without proof/tenant/actor/invocation leakage. Legacy shared-pool evidence is supplemental. Concurrent A/B dedicated Hikari pool and fourteen serving faults with per-fault SQL/history/handoff checks. |
| replicas-quota-restart: bounded replicas/replacement JVM and replay | A — PASS | Durable row/rate/concurrency accounting, replacement process, new ACCESS/grant and consumed/released invocation replay are local security requirements. Synthetic legacy quota tests cannot establish the entire new protected serving path. Actual replacement JVMs, new grant/ACCESS, two service instances, aggregate row/rate/concurrency ceilings and replay denial. |
| replicas-quota-restart: fleet stress, database failover/restart operations | B — OPEN | Production-scale load, operational SQL restart/failover and installed fleet topology are deployment acceptance. Replacement JVM accounting is retained in A; this split does not waive durability. |
| audit-faults-and-review | A — PASS | Finished protected path must suppress every requested parent/child/PHI/quota/commit/lost-ACK/close/outage fault, preserve actual commits, avoid duplicate ALLOWED_READ/reread, inspect persisted privacy, and enforce authoritative admin review/page <=100/history IDs. Actual serving append/commit/close/outage faults, persisted privacy inspection and authoritative admin review. |
| finished-conformance | A — PASS | Run applicable current suite on actual two-tool server; preserve SUCCESS/FAILURE/SKIP/NOT TESTABLE counts. Missing diagnostic tools are unsupported scenarios, never invented tools or passing scenarios. Current suite executed; all applicable checks pass; raw unsupported/diagnostic failures individually retained and assessed. |
| performance: bounded local sanity and available representative plans | A — PASS | Detect obvious regressions in ACCESS, list, search, Overview, quota/audit and handoff. Collect protected query plans when available; no SLA claim or requirement for full production load. Five finished HTTP samples per operation; ten actual SQL intervals per transaction phase; two sanitized cached plans. |
| performance: production SLA/capacity | B — OPEN | Requires deployed topology, volumes and operator thresholds; bounded local samples cannot certify it. |
| source-vs-deployed-parity: local source identity/candidate/catalog | A — PASS | Historical hash gate passed. Fresh authoritative runs require the same immutable bytes and reviewed derivation/catalog; receipt hashes alone cannot recreate SQL. Original/derived/candidate hashes reproduce; 25 authoritative SQL regressions and both original clients pass. |
| source-vs-deployed-parity: historical provenance/deployed owners/collation/catalog | B — OPEN | Hash identity does not establish historical ownership/collation or current deployed catalog. Must verify during governed deployment, without inventing provenance. |
| deployed-auth-and-runtime | B — OPEN | Production/Azure SQL, Stage A/B permissions, installed API/JAR/caller compatibility, old-caller drain, fleet/native Windows parity and monitoring require operator/deployment evidence. Ordinary local WEB/DESKTOP/REST/OAuth regression checks remain A. |
| retention-legal | B — OPEN | **tenant/legal retention sign-off: OPEN**. Roadmap §10.12 requires it before real-data enablement; no approval is invented for synthetic local testing. |
| sdk-production-approval | B — OPEN | Prerelease approval concerns production suitability; any concrete technical defect would still block A. Recheck official releases without changing dependencies during acceptance. |
| deployment-live-acceptance | B — OPEN | R4/live configuration, real tenant/integration enablement, operator/security/tenant-owner safe-pilot approval and separately authorized configuration changes are deployment gates. §10.11's owner authorization sentence remains a pilot gate; it does not authorize enablement here. |
| required regressions, including unfinished all-opt-in coverage | A — PASS | Focused, ordinary, affected, mandatory SQL subsets, critical once, selector Python and static checks must be reported honestly. Partition the prior timed-out legacy SQL suite into bounded complete groups; never treat that timeout as a pass. Focused/ordinary/desktop/affected/critical and Python checks complete; all prior legacy methods partitioned into 13 passing bounded groups. |

Roadmap §10.11 explicitly requires local serving TLS/proxy, so it remains A and is exercised with
real TLS. Its security/tenant-owner safe-pilot authorization clause is B: technical local acceptance
does not grant permission to enable a tenant. Public ingress and real human identity integration are
also B; maintained OAuth servlet composition and synthetic WEB identity are the local issuance scope.
Roadmap §10.12 requires legal retention approval before real-data enablement, not synthetic testing.
Unsupported diagnostic tools are explicitly assessed; no genuine local security requirement is waived.

## Identical finished-client matrix

Official TypeScript `@modelcontextprotocol/client 2.4.0` and Python `mcp 2.3.0` negotiate
`2026-07-28` against the actual finished two-tool HTTP server. Both consume the same committed
[66-vector corpus](../../build/mcp-transport/acceptance-corpus.json): **66/66 PASS each; 132 records**.
The Java parent independently checks actual SQL/audit effects after every serving fault.

| Group | Vectors per client | Coverage |
| --- | ---: | --- |
| Authentication | 13 | Valid/expired/revoked ACCESS, random opaque, refresh, authorization code, WEB, duplicate/malformed/oversized bearer; wrong resource/issuer/client. |
| Catalog | 5 | Search only, Overview only, both, neither, live scope reduction. |
| Search | 13 | Valid, zero, pages 0/9 with hasMore/windowExhausted, blank/short/wildcard-only, literal wildcard-looking text, oversized query, bad page, extra property, Unicode, overlength source. |
| Overview | 11 | Same tenant, unassigned, missing, foreign, deleted; zero/negative/string/overflow/null IDs and extra property. |
| Live authority | 7 | Tenant off; integration/grant/session revoke; user disable; credential expiry/revoke. |
| Serving faults | 14 | Parent/child/PHI/quota append; commit disconnect; lost ACK; logical close; failure-audit outage; before/during-SQL cancellation; timeout; task rejection; cleanup outage; interruption. |
| Reuse/privacy | 3 | Success after faults; assertion/SQL/metadata sentinels; actual ACCESS submitted as query sentinel. |

Both clients reject protected output on every fault. Python's anyio client can report intentional
response interruption as an `ExceptionGroup` during context exit; the harness accepts fault-path
transport closure but recursively rejects assertion failures. Java still requires exactly one protected
RPC, zero handoff and exact committed/rolled-back audit deltas. Earlier harness failures are recorded as
failed attempts, including fixture constraints, logical-versus-physical close injection and Python
shutdown handling; they are not passing evidence or production fixes.

## Response boundaries

Search structured bytes **32,767 / 32,768 / 32,769** use valid authoritative fields with deterministic
escaping: below/exact return complete buffers; above returns none. That seam measures structured output
independently of the full envelope. Separate actual HTTP search requests produce complete SDK responses
of **65,535 / 65,536** bytes; the **65,537**-byte candidate is replaced by a 331-byte closed
`PAYLOAD_TOO_LARGE` response without structured content. Their structured sizes are 30,400/30,400/30,401.
All three actual committed READs remain durable, including the withheld result; no automatic reread.

Overview uses authoritative widths: CaseNumber 100, accepted CaseName 255, Status 50, PracticeArea 150,
and two user displays of at most 101 UTF-16 units each. The all-max-width six-byte-escaping fixture is
4,872 structured bytes. A conservative bound reserves another 64 bytes for maximum scalar IDs/date
text; duplicate escaped text, fixed envelope and even the entire 8,192-byte request budget as echoed ID
remain below 32 KiB. Thus valid-source structured 8,191/8,192/8,193 and full 32,767/32,768/32,769 are
unreachable. They are not claimed as actual wire tests. An injected full-envelope measurement seam
with actual protected SQL separately verifies below/exact/above 32 KiB (and search 64 KiB).
Null/unassigned fields and Unicode are exercised; a 256-unit CaseName is rejected before encoding.
No truncation, partial protected output, success-history erasure or size-failure retry is permitted.

## Pool, async and durable quotas

The dedicated Hikari AI pool serves simultaneous tenant A/B requests, followed by foreign-ID failure
and successful B/A reuse. Tenant/actor/proof/invocation/audit ownership remains separate and pooled
context is cleared. Finished-server fault injection decorates logical AI pool leases, after live
attestation. Actual SQL WAITFOR is positively observed before statement cancellation or disconnect;
timeouts execute against the real wait. Before-SQL interruption, rejected-task propagation, post-COMMIT
interruption, close and bounded cleanup outages are injected explicitly, not described as natural
infrastructure failures.

Actual replacement JVMs and independent service instances observe persisted accounting. New ACCESS,
grant and integration registration do not reset tenant/user totals. User/integration/tenant row ceilings
are 200/500/1000; call ceilings are 10/20/60; active concurrency ceilings are 1/2/5. Owner-seeded valid
historical/active reservations reach aggregate ceilings without a production load test; the subsequent
denial uses genuine proof-bound protected procedures. Real calls, fresh grants and a held private output
buffer additionally exercise competing service instances. Consumed/released invocation read and handoff
replay fails; committed charges are not refunded by failures or replacement processes.

## Audit faults, privacy and review

Parent, child and PHI append failures roll back every success fact. Quota admission faults remain closed
(existing SQL vocabulary maps the injected admission fault to FORBIDDEN). Disconnect during a positively
observed SQL wait rolls back the protected transaction. Lost commit ACK, logical close failure,
post-COMMIT interruption and combined lost-ACK/cleanup outage retain exactly one actual ALLOWED_READ and
PHI append, with zero protected handoff. Failure-audit outage remains closed; committed history survives.
Every fault attempts the protected read once and never automatically rereads.

Every persisted integration parent/resource column and value is inspected. No query, Case name/number,
bearer, ProofDigest, OAuth assertion, JSON-RPC ID, exception text, SQL text or arbitrary metadata appears.
Actual issued credentials are checked as well as sentinels. Existing typed historical Case resource IDs
remain intentional. An active same-tenant admin can review them after Case deletion; non-admins are denied,
foreign tenant review sees none and page 101 is rejected. Review remains bounded to <=100 parents; no
generic dump endpoint is added. All audits use the existing schema; no audit migration is required.

## Finished-server conformance

Pinned suite: **`@modelcontextprotocol/conformance 0.2.0-alpha.12`**, modern spec **2026-07-28**.
Stable CLI tag remains 0.1.16. Authentication injection covers fetch and raw Node HTTP requests and never
adds diagnostic tools. Raw nonzero suite exits remain nonzero; the execution harness's success means the
suite finished and evidence was inspected, not that every raw scenario passed.

| Selection | SUCCESS | FAILURE | SKIP | WARNING | INFO | Assessed NOT TESTABLE | Assessed not applicable |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Complete frozen revision: 50 scenarios | 95 | 73 | 5 | 4 | 1 | 27 | 46 |
| Active profile | 23 | 18 | 0 | 0 | 1 | 7 | 11 |
| Modern server-stateless | 21 | 6 | 3 | 0 | 0 | 4 | 2 |
| Modern HTTP-header-validation | 14 | 0 | 0 | 0 | 0 | 0 | 0 |
| Modern caching | 4 | 3 | 1 | 0 | 0 | 0 | 3 |

The frozen invocation uses `--requirements 2026-07-28 --timeout 60000` and finishes all 50 scenarios.
The other rows overlap it and are not additive. Its additional diagnostic input-request/JSON-schema/
custom-header probes need fixtures or annotations the two tools do not provide. Its optional tasks
extension checks assume task/long_job/failing_job fixtures and advertised task handlers; neither exists.
The official SDK release explicitly lists tasks as unsupported. These are unadvertised optional features,
not a discovered defect in the required Case tools. Five raw skips and four warnings remain explicit.

NOT TESTABLE/not-applicable are assessments of raw failures, not extra passing tests. The JSON preserves
each failed check ID, original error, classification and rationale. Active diagnostic-tool calls cannot
run without fixed test tools; unsupported prompts/resources/completion methods account for ten more.
The remaining Origin probe expects localhost HTTP Origin acceptance, which conflicts with the explicit
canonical HTTPS allowlist (403 is correct). Modern missing-capability/progressive-stream/logging probes
need absent diagnostic tools; their internal 600/800-ms streaming aborts also precede the roughly one-second
live attestation. Subscription ACK/tag cases are inapplicable because no subscription-delivered capability
is advertised; the suite itself skips three neighboring subscription checks. Caching failures concern
unadvertised prompts/resources/templates; applicable tools-cache checks pass. No full conformance claim.
The first frozen attempt with an eight-second scenario budget remains historical and incomplete; this complete 60-second-budget rerun supersedes it without rewriting its raw failures.

## TLS, performance and plans

Task-owned **Caddy 2.11.7**, a real loopback reverse proxy and ephemeral certificate execute TLS with
certificate and hostname verification enabled. Canonical public identity is stable under hostile Host,
Forwarded and X-Forwarded headers. Exact Origin is accepted; hostile Origin gets 403; HTTP redirects to
canonical HTTPS. A protected Overview also traverses TLS. These are actual sockets/certificates, not
headers pretending to be TLS. Public certificates, trusted deployment proxy topology and real identity
integration remain B. The proxy is stopped and private key erased in finally.

Five samples each measure ACCESS/discovery, tools/list, search and Overview through the actual server.
Ten SQL BEGIN/COMMIT samples each measure quota admission, protected read/audit and final handoff.
These bounded synthetic observations detect obvious regression; they certify no SLA or fleet capacity.
Live catalog attestation accounts for roughly one second per HTTP request. Actual cached protected search
and Overview query plans are captured; statement text, parameter values and constants are stripped and
bearer/digest absence checked. No RPC parameter/sql_text capture is enabled. The JSON records samples,
plan hashes and representative operators; sanitized plan artifacts are kept separately from private SQL.

## Verification and reproducibility

| Final check | PASS | Skips | Failures/errors |
| --- | ---: | ---: | ---: |
| final-client-ts | 1 | 0 | 0/0 |
| final-client-python-v2 | 1 | 0 | 0/0 |
| final-authoritative-data | 25 | 0 | 0/0 |
| final-original-clients | 2 | 0 | 0/0 |
| final-protocol | 2 | 0 | 0/0 |
| modern-profile | 1 | 0 | 0/0 |
| final-frozen-conformance | 1 | 0 | 0/0 |
| final-focused | 90 | 0 | 0/0 |
| final-ordinary | 55 | 0 | 0/0 |
| final-desktop-and-http | 58 | 0 | 0/0 |
| final-affected-1 | 129 | 6 | 0/0 |
| final-critical | 215 | 0 | 0/0 |

Counts overlap and are not additive unique coverage. Standard selector opt-ins are skipped because
its combined duration exceeds a bounded batch; they pass in the explicit authoritative/client groups
above. Native Windows launcher skips are deployment/fleet scope. Selector Python: 27 PASS; Case launcher:
4 PASS; Phase1R launcher: 3 PASS; auth launcher: 35 PASS, 8 native-platform skips. Critical reactor ran
once after the recovered-input changes, across all seven modules. Docs/static and diff checks PASS.

Exact Maven argv, counts, durations, client records, fault read/wait counters, conformance classifications,
TLS outcomes and performance samples are in `recoveredInputAcceptance` in the verification JSON.
The previous all-opt-in timeout is never a pass: all 101 legacy SQL methods were instead selected in 13
independently bounded groups, producing 182 passing invocations without skips. This is supplemental
shared-runtime coverage; the authoritative new boundary is verified separately.

Prepare the private candidate using `build/sql-confinement/prepare-authoritative-fixture.py` and the
approved source. Use JDK 21/Maven, the documented LOCAL_DISPOSABLE_AI loopback opt-in, exact candidate
hash, distinct fixture logins, `npm install --prefix build/mcp-transport --package-lock=false`, and a
test-owned `work/mcp-clients` venv with `mcp==2.3.0`. Real TLS checks additionally require the test-owned
Caddy executable at `work/phase3b7/caddy` and openssl. Never substitute production URLs or principals.
Select the two client parameterizations independently with `-Dshale.acceptance.client=ts` / `python`.
Run individual acceptance methods as bounded Maven groups; the combined class exceeds a single short
whole-suite budget. Skipped opt-ins in the standard selector are not acceptance evidence.

## Remaining live/deployment gates and recommended sequence

All are OPEN; none is executed by this task:

1. Obtain security/tenant-owner/operator safe-pilot authorization, tenant/legal retention sign-off and
   explicit SDK prerelease production approval. Java SDK remains **2.2.0-M1**, prerelease; stable 2.1.0
   lacks the modern-server API. Recommend a dedicated compatibility task when a supported stable modern
   server becomes available; do not change it during acceptance.
2. Review the production identity/composition and configuration path in a separate change (current
   production/Azure enablement remains rejected); inventory redirects/keys, source provenance,
   deployed owners/collation/catalog and old callers. Approve the additive SQL/Stage A/B rollout and
   independently attest effective non-bypass permissions. Do not infer deployment parity from local hashes.
3. Deploy only through the governed release process after that authorization; verify installed JAR/API,
   compatible callers, old-caller drain, fleet/native Windows parity and real public TLS/proxy/Origin/
   HTTPS/edge logging controls while capabilities remain OFF.
4. Rehearse monitoring, quota/revoke/disable propagation, bounded capacity, restart/failover and containment;
   retain durable history. Complete R4/live operator acceptance with deployed query plans and thresholds.
5. Obtain a separate explicit authorization for real human OAuth, live credentials and the first tenant/
   integration/handler/transport enablement. Stage the pilot under monitored controls and retain rollback
   as OFF/revoke/drain; do not erase history or automatically roll back SQL.

This sequence is a recommendation for later work, not deployment permission or a claim of legal approval.
