# Production MCP Case composition verification

PR: [#1924](https://github.com/gseshadow/Shale/pull/1924), branch
`codex/production-mcp-case-composition`.

Status: **READY FOR GOVERNED DEPLOYMENT — mandatory local acceptance passed.**
Deployment, activation and remaining Category B approvals are not authorized by this result.

## Baseline and immutable schema

Resumption started from published `e396af4026efd135c8201a3cf0648a2a993163c8`; the preceding verification
update published `3adcd603db047bf1c2509a942672a06d506033de`. At attachment resumption, the local
branch matched that published commit/tree (`3bdd451b915ab10412c0914a24f4dc5094979511`) before edits.
Fresh fetch confirmed merged base `b990a09212b9e5872858005e9385d4a2b0539c9f` and unchanged PR head.
The original checkout and unrelated work are preserved. No second PR was created.

The previously missing attachment was supplied on October 27, 2026. Its **original bytes were hashed
before decoding or derivation**, matched exactly, and remain unchanged, private and uncommitted.
The existing reviewed `build/sql-confinement/prepare-authoritative-fixture.py` reproduced both
reviewed receipts before any SQL execution:

| Artifact | Bytes / SHA-256 |
| --- | --- |
| `shale-schema-source.sql` | 728,862 bytes; `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda` |
| Reviewed derived schema | `40a665caefb3203a6c364d0caac3fadcc1a58d24a66a994f48e058b73ae6f243` |
| Reviewed three-FILTER candidate | `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f` |

Both task-owned SQL containers were removed after exact-label ownership checks at completion.
The original attachment and private scratch inputs remain uncommitted.

No schema reconstruction or substitute Case model was used. The candidate contains 96 tables,
five functions, three schemas, 208 indexes and 1,142 ALTER TABLE batches. Authoritative tests use
nonce-owned databases/logins on task-owned SQL Server Developer containers pinned to
`mcr.microsoft.com/mssql/server@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
Only synthetic data was seeded. Production-mode fixture connections require encrypted JDBC with
certificate validation and a trusted task-owned certificate for `localhost`; the production Case
pool also uses the normal protected datasource constructors. No production datasource was contacted.

## Verification-discovered defect and fix

The first actual Maven build exposed a test compilation error: Spring 7's `MockEnvironment.setProperty`
returns void and cannot be chained. That test was corrected. Maven 3.9.11, JDK 21.0.12.1 and Boot 4.1.1
then compiled the candidate and ran actual tests; isolated javac is not used as acceptance evidence.
The original network failure was a command socket permission issue; the concrete permission fix
resolved dependency acquisition. Repository policy disables GitHub Actions testing, which was not used.

A later live adversarial test found a production attestation defect: `GRANT SELECT ON dbo.Cases TO public`
gave the catalog verifier real Case visibility while readiness still returned `ready`. The AI principal's
own role DENY continued to reject its direct Case read, so inspecting only the AI token was insufficient.
The failing assertion was **expected `not-ready`, actual `ready`**.

`AiCaseDataAttestation` now inspects the verifier's inherited public grants in production mode. It rejects
application reads/EXECUTEs, schema/database authority and grant options outside the metadata allowlist.
Engine-owned system metadata/context facilities remain allowed; hidden negative-ID compatibility
catalog views are recognized only as system SELECTs. Unknown application grants fail closed. The exact
verifier token, its own direct grants, server grants and ownership checks remain in force. No SQL
migration, grant installation, receipt, historical module, role definition or dependency changed.

The fixed full Boot test proves actual excess verifier visibility during the injected fault, fresh
`not-ready`, and fresh `ready` after removal. A separate real serving test proves a valid ACCESS gets
401 while that drift exists, no protected output, then successful discovery after restoration.
The legacy-authority sentinel remains uncalled when the confined boundary is unavailable.

## Executed evidence

Exact argv, source hashes, per-class counts, schema receipts, client vector results and resolved failed
attempts are recorded in [the verification JSON](production-mcp-case-composition-verification.json).
Counts overlap; they must not be added as unique coverage. A class with zero tests under a disabled SQL
opt-in is **NOT RUN**, even if Surefire reports zero skips.

The final source is code commit `7225815414b82a12b3a79345efd4e1c1d09001f8`. All counts below
were executed after the inherited-permission fix; pre-fix passes are not used to close its acceptance.

| Executed group | Passed | Failures/errors | Skipped |
| --- | ---: | --- | ---: |
| Authoritative SQL confinement/parity/attacks, including inherited verifier grant regression | 27 | 0/0 | 0 |
| Production-profile readiness/ordinary Boot, OAuth lifecycle, both expiry clients, TLS and full client corpus wrappers | 19 | 0/0 | 0 |
| Isolated TEST-profile OAuth/legacy-refusal and both confined clients | 19 | 0/0 | 0 |
| Actual Spring composition matrix, production OFF Boot and focused configuration | 29 | 0/0 | 0 |
| Case security/transport/disclosure contracts | 90 | 0/0 | 0 |
| Ordinary JDBC/auth/Case REST/session regressions | 55 | 0/0 | 0 |
| Desktop/HTTP/session regressions | 58 | 0/0 | 0 |
| OAuth policy/assertion/metadata regressions | 20 | 0/0 | 0 |
| Affected cases/security-data/server suite | 185 | 0/0 | 9 |
| Critical reactor, once after final source fix, all seven modules | 226 | 0/0 | 0 |
| Selector Python | 27 | 0/0 | 0 |

The 46-test authoritative reactor contains the first two rows. Its 19 production cases include
12 durable OAuth cases, one full Boot readiness/ordinary case, two official-client expiry cases,
and four finished-serving cases (two client matrices, TLS, inherited-authority denial).
TypeScript and Python each passed **66/66 vectors**. TLS/proxy passed **20/20 checks**; the 0 status
entries in its receipt mean refused TLS handshakes, not HTTP success. Ordinary full Boot/SQL smoke
passed **23/23 HTTP checks with AI OFF and 23/23 after readiness alone**.

The affected suite's nine skips are eight opt-in Phase3B.7 cases plus one production readiness case;
its normal selector invocation deliberately supplies no SQL opt-in. No skip or zero-test SQL suite is
counted as passing SQL evidence. Mandatory production cases were explicitly executed in the separate
zero-skip authoritative run. Unselected historical frozen/modern conformance/performance cases are
not represented as fresh evidence; existing Phase3B.7 closeout retains its original scope.

## What the production proofs exercise

- Full `ShaleServerApplication` Boot startup under both `azure` and `prod`, PRODUCTION identity,
  all eight controls OFF and no AI settings: ordinary real beans and routes remain available;
  no AI pool/boundary, handler, MCP servlet, OAuth issuance/bootstrap or legacy authenticator.
- Actual Spring registration for all 32 control masks under each production profile, plus isolated
  `test`/TEST composition. Production transport, search and Overview use the same policy. Mixed
  profiles, invalid/noncanonical/HTTP identities and incomplete dedicated settings fail their guards.
- Full Boot readiness under `azure`/PRODUCTION with only Case composition enabled; SQL global and
  tenant capability OFF; normal independent AI, verifier and bootstrap datasources; immutable
  database/issuer/resource/timezone binding; live SID/token/authority attestation. MCP, handlers and
  OAuth issuance remain absent. Readiness is fresh, closed and data-free.
- Actual restricted AI login denials: Cases/control/credential SELECT, forged tenant/user context,
  direct PHI INSERT, all 42 legacy EXECUTEs, impersonation, DDL, policy/role changes and an actual
  cross-database read against a separate owned marker database. A successful statement followed by
  rollback is never counted as denial.
- Verifier token exactly own SQL user + public + `AiCaseDataCatalogVerifier`; approved metadata-only
  server permissions; SID different from AI/auth/runtime/bootstrap; no ordinary Case/PHI/credential/
  control read or application EXECUTE. Wrong database/binding, reused SIDs, excess direct grants and
  inherited public Case grants remove readiness. Ordinary auth/runtime may share a SID; bootstrap
  and verifier may not reuse that SID or each other's SID.
- Real production-profile confined serving, both unchanged tools, no `mcpAccessAuthenticator` under
  production profiles. Deliberate boundary unavailability returns `not-ready`/401 with no disclosure
  or call to the injected legacy fallback sentinel.
- Both official clients: catalog and exact two-tool contract; search success/zero/paging/literal/
  Unicode/invalid input; same-tenant/unassigned/foreign/missing/deleted Overview; expired/revoked/
  reduced/live authority; all maintained audit/commit/close/cancellation/timeout/privacy vectors.
  Client versions are TypeScript `@modelcontextprotocol/client` 2.4.0 and Python `mcp` 2.3.0.
- Both official clients' final-handoff race uses the existing deterministic SQL lock drill, crosses
  DB-UTC expiry while blocked, samples DB UTC after required locks and denies protected output.
- Real task-owned Caddy 2.11.7 TLS proxy: trusted certificate and canonical hostname; wrong hostname
  and untrusted certificate rejected; canonical issuer/resource metadata despite hostile Host,
  Forwarded and X-Forwarded headers; exact Origin and near-match denials; permitted nonbrowser
  missing Origin; protected Overview; HTTP redirects even for credential-bearing MCP/OAuth requests.
  This proves local proxy behavior, not deployed Azure ingress parity.
- Twelve production-identity durable OAuth tests: S256 authorization code, public client none,
  confidential pinned RS256 private_key_jwt, exact redirect/issuer/resource, single-use code/JTI,
  refresh rotation/reuse family revocation, revocation and unsupported-grant/ordinary-bearer refusal.
- Real Boot/SQL ordinary smoke with all AI OFF, then readiness alone: 23 HTTP checks in each state
  for WEB login/identity/refresh/logout, remembered DESKTOP rotation/replay/logout, Case REST search/
  Overview and closed foreign/missing/deleted results. Existing ordinary/client/session regressions
  run separately. These are protocol/service regressions, not an interactive browser/JavaFX GUI run.

## Reproduction and scope

Use the original hash-gated attachment and the reviewed preparer; point `SHALE_CASE_VERIFY_SCHEMA`
and `SHALE_CASE_VERIFY_SCHEMA_SHA256` at the verified candidate above. Set the explicit
`SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, task-owned loopback SQL port and synthetic owner password
through a private environment/secure prompt. Never substitute a production URL/login. Install the
repository's Node dependencies and `mcp==2.3.0` in the task-owned `work/mcp-clients` venv; place the
reviewed Caddy executable at `work/phase3b7/caddy`.

Production acceptance additionally requires a locally trusted SQL TLS certificate with DNS SAN
`localhost` (and the existing ordinary-pool certificate name `*.database.windows.net`), its root in the test JVM's truststore, and `-Dshale.acceptance.production=true` on the
recorded Maven commands. That switch changes only opt-in test fixtures to profile `azure`, identity
PRODUCTION and canonical `https://shale.example`; it is not an application activation setting.
Production-mode fixture JDBC uses `trustServerCertificate=false`. The original TEST-mode SQL fixtures
retain their explicitly local setup behavior and are not used as production TLS proof. Ephemeral proxy
certificates are trusted explicitly by the test trust manager with hostname checks enabled; negative
trust and hostname checks must fail. There is no TLS trust-all client or forwarding-header authority.

The shared 66-vector client corpus runs on the private loopback serving listener; the separate real
TLS/proxy suite proves canonical TLS serving with a protected Overview. The production readiness Boot
proof uses the actual property-based pool factory, while the serving fixture supplies real confined
SQL sources to the same boundary and transport. No mocked SQL attestation is counted as live proof.

## Original blocker and changed boundary

The base's `McpTransportConfiguration.mcpAiCaseData`, `McpSearchConfiguration` and
`McpOverviewConfiguration` each required profile `test`, OAuth identity `TEST`, and absence of
`prod`/`azure`. The new shared `McpCaseCompositionPolicy` preserves isolated test composition and
adds an explicit production SQL composition control. No profile enables it automatically.

Production handler registration requires all of:

```
(azure OR prod) AND NOT(test OR dev OR local)
AND OAuth identity PRODUCTION AND canonical HTTPS origin
AND shale.ai.mcp.case-data-composition-enabled
AND shale.ai.enabled AND shale.ai.mcp.enabled
AND the individual handler switch
AND a constructed, successfully live-attested McpAiCaseData
```

Readiness requires only the production identity/profile/origin, the new composition switch and
the complete dedicated SQL configuration/attestation. OAuth composition, authorization, exchange,
global application capability, transport, both handlers, SQL global and tenant capability can all
remain OFF. Configuration presence cannot enable any of them.

Production transport requires the same production identity/profile/composition and application
gate even when no handlers are selected. It authenticates only through `McpAiCaseData`.
The legacy `DeadlineAccess` authenticator is excluded from `azure`/`prod`; the production security
chain explicitly refuses a missing confined boundary. No production runtime fallback exists.

## Readiness observation

`McpCaseReadiness` is an internal bean, not an HTTP endpoint. Initialization emits exactly
`AI_CASE_SQL_READINESS disabled|ready|not-ready`. Deployment operators can inspect this bounded
startup observation after separately authorizing readiness configuration and a restart.
Internal `state()` calls perform fresh bounded attestation and return only those three strings.
No catalog details, usernames, database names, credentials, hashes, proofs or PHI are returned.
This is not an ongoing background health monitor. Every protected request still independently
reattests; a previous readiness result never authorizes work. Ordinary `/api/health` is unchanged.

Missing configuration or initial attestation failure prevents boundary construction/startup and
therefore handler registration. A subsequent failed internal readiness check returns `not-ready`.
All-OFF deployments never create the AI pool or require AI credentials/origin.
Explicit invalid origins and mixed production/development profiles remain refusals.

## SQL identity separation

No SQL migration changes. The exact six proof-bound procedures, 42 legacy denials, historical
receipts, RLS, source/catalog hashes, proof holder, quotas, audits and final gate are retained.
The production attestation additionally compares actual verifier and bootstrap SIDs against
ordinary auth/runtime identities. The AI identity remains distinct from all of them. The current
global operator provider uses ordinary runtime, so that comparison also excludes its SID; a future
independent operator datasource must be explicitly added to the governed identity inventory.
No new role membership or permission is installed by startup.

All required AI, verifier, bootstrap and expected-database properties are resolved before allocating
the independent AI pool. SQL TLS rules remain unchanged: production URLs cannot override encryption,
certificate validation, SQL authentication identity, MARS or timeout protections.

## Test impact inventory

Pre-edit searches covered all callers of `mcpAiCaseData`, `mcpSearchHandler`, `mcpOverviewHandler`,
`McpAiCaseData` and `AiCaseDataAttestation`, handler switches and isolated-profile assertions.

| Contract | Existing and added test owners |
| --- | --- |
| Profile/identity/controls, both handlers agree | New `McpCaseCompositionTest`; updated negative guards in `McpSearchTest` and `McpOverviewTest` |
| OFF security chains and ordinary routing | `McpFilterIsolationTest`, new production OFF/context checks |
| Readiness without issuance/transport/handlers | New internal readiness and production context checks |
| Missing datasource settings and no pool allocation | New missing-property matrix; existing `DataSourcesAiCaseDataTest` |
| Actual SID separation and drift | New disposable `AiCaseDataLocalSqlServerTest` method; existing authoritative denial/parity/expiry tests |
| OAuth and ordinary callers | `OAuthConfigurationTest`, `OAuthAuthorizationFlowTest`, existing WEB/DESKTOP/session suites |
| Serving bounds, final handoff, clients, TLS | Existing #1922/#1923 serving/client/TLS suites; production-profile proofs described above |

Mocked wiring remains supplemental; the real SQL/Boot/serving proofs above supply live evidence.

## Audit compatibility and gates remaining OPEN

Readiness/attestation reads only identity/catalog/reference state and adds no PHI/entity audit event.
Existing Overview PHI, integration parent/resource audit, quota and final-handoff behavior is unchanged;
no audit schema or migration is added. Fault-injected synthetic Case visibility is counted, never exported.

Tenant/legal retention sign-off, **SDK 2.2.0-M1 production approval**, and security/operator/tenant-owner
pilot authorization remain OPEN. Actual Azure SQL/platform/catalog/Stage A/B/owner/collation/JAR/caller
parity, deployed TLS/proxy, monitoring/capacity/restart/failover and remaining Category B/R4/live gates
remain OPEN. Local acceptance does not approve deployment, activation or production principal provisioning.

No production/Azure SQL, Azure configuration change, JAR deployment, production credential issuance,
real tenant/integration enablement, production DNS/proxy/certificate change or installer/version publication was
performed. All packaged AI/OAuth/MCP/readiness/handler activation defaults remain OFF.
