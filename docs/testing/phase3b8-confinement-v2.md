# Confinement V2 acceptance — 2026-10-28

**BLOCKED.** The successor is implemented for review; deployment preparation readiness is not established.
Required production-profile ordinary-authentication smoke times out on POST /api/auth/login with AI
composition OFF, before enabling V2 readiness. McpProductionReadinessLocalSqlServerTest fails in
ProductionOrdinarySmoke.check/call. No timeout, authentication check or assertion was weakened.

## Version and security scope

Base a06a05cf7a9c0b758fc7a0478c5541c9dfdb1489; implementation 1eee0106.
[Design and implementation map](../architecture/phase3b8-confinement-v2.md).
AiCaseDataV2 has independent catalog/source receipts, installer, standalone and packaged verifier.
Production selects shale.ai.data.contract-version=V2 explicitly. No fallback follows V2 failure.
V1 installers, verifier, manifest and historical hashes remain byte-identical. Its original test
assertions run with default V1; V2 inherits those assertions with explicit version/fixture hooks.

The six RPCs and all 13 confined module bodies remain identical to V1. V2 installs their exact
UTF-16LE bytes from hex, preserving module identities even with CRLF installer text. Case columns
use active logical positions; six prerequisite modules accept only two individually reviewed exact
hash variants each. Metadata, module settings, parameters, dependency and policy identity/ownership
checks remain independent. Literals and module text are never broadly normalized.

## Evidence and hashes

Private candidate SHA-256 4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f
and receipt are verified. Eight exact production Base64 components verify their hashes/counts.
Production-style SQL reconstruction reproduces EB39DDBFA59DF338DCAA969E440C7D254B1CB27ACE1211524375D786B2006AB1
before Phase1R and D4AA4B9D029044A69BA0FE10A171DD6B5447374EC3124CBFB6EA6E743E49F0CC after
exactly its three filters. V1 still refuses that representation. Actual historical Phase1R
preflight/apply/postcheck writes its receipt, then V2 prerequisite/install/standalone/cumulative passes.

- V2 Case/RLS catalog: 6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a.
- V2 source receipt: 60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891.
- Installer file SHA-256: 7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8.
- Packaged server JAR: 4159a660406e0d841ae6f37a90319ecb9b0df2166823ff54b526ce86e339c14f.

Installed V2 catalog receipts additionally include destination IDs and grants: they are local
fingerprints, not portable production review values. No attachment or complete private schema/module
body is committed. Hash-only reviewed variants and synthetic fixture transformations are committed.

## Verification

Full per-group counts, failed attempts and final results are in
[the verification JSON](phase3b8-confinement-v2-verification.json). Counts overlap and are not additive.
The local runs use the hash-gated private candidate, loopback SQL Server 2022, nonce-owned databases
and distinct SQL logins. The real external-table fault uses separately owned matching PolyBase.
The initial PolyBase GENERIC-source refusal was resolved by restarting its local server after enabling
PolyBase; both V1 and V2 actual external-table refusal tests then pass.

Focused V2/V1/Phase1R/composition: 28 executed, zero failures/errors/skips. Authoritative Case/RLS:
1 executed, zero failures/errors/skips. Ordinary V1 and V2 data suites: 27 each, zero skips/failures.
Real external table: 1 each, zero skips/failures. Python selector: 27; Phase1R: 3; Case launcher: 4;
V2 contracts/private projection: 4. Package build passes. V2 HTTP authority-fault matrix, TLS/proxy, response boundaries and both independent clients pass.
The online critical reactor passes 228 tests with zero skips/failures/errors. Full per-group results are
reported individually in JSON; no failed or unexecuted check establishes readiness.

## Remaining gates and deployment impact

The ordinary HTTP authentication timeout must be diagnosed and all mandatory acceptance must pass
before this PR can receive the requested readiness classification. An uncached critical-reactor
artifact caused an initial offline dependency failure; its online retry is reported separately.
Native Azure contained identities/read-only live attestation and an authorized disposable Azure
mutation/fault suite remain OPEN. No native Azure fixture or acceptance credentials were provisioned.
Local SQL Server, real local TLS and routing tests cannot close those native gates.

The future sequence is documented in the [deployment preflight](../production-mcp-deployment-preflight.md).
The seven production prerequisites are already installed and MUST NOT be rerun. Phase1R and V2 are
future separately governed changes. No production connection/change, identity provisioning, server
JAR deployment, AI/MCP/OAuth activation or real integration/grant/session occurred. Do not merge or deploy
this blocked candidate. Audit transactions and vocabulary remain unchanged.

## Post-merge login investigation — 2026-10-28

PR #1927 was merged as 5e84669fe5f287a05d472609e29ae6ebb9b6521f with the
ordinary production-profile login blocker unresolved. Its original evidence above and
verification JSON remain historical evidence; the merge did not establish production
deployment readiness. This follow-up starts from that current `codex/latest` commit.

**BLOCKED: reproduction prerequisites are absent from the new environment.**
The owned Docker daemon is reachable but has zero containers/images. No reviewed private
schema is attached or present in the supplied workspace input directories, and
`SHALE_CASE_VERIFY_SCHEMA`, the disposable SQL endpoint/owner configuration and the
local SQL TLS fixture have not been supplied. The existing harness requires the exact
private candidate SHA-256
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
Alternatively, its existing read-only derivation requires the reviewed source export
SHA-256 `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`.
A substitute schema cannot establish the requested authoritative acceptance.

### Static findings, not root-cause evidence

- `ProductionOrdinarySmoke.check()` first sends a wrong-password login expecting 401,
  then a correct-password login expecting 200. Each HTTP request already has a ten-second
  timeout; the HTTP client has a five-second connect timeout. The enclosing test retains
  `@Timeout(180)`. A historical stack in the client does not locate the server stall.
- `AuthController.login` calls `AuthServiceAdapter.authenticate`, then
  `AuthServiceImpl.login`: auth-pool acquisition, the bounded
  `dbo.LookupAuthenticationCredentialV1` RPC (five-second query timeout), result validation
  and Java password verification. Only successful authentication reaches
  `ServerAuthSessionService.issue` and response construction. No runtime timing or
  session/lock evidence is available to identify which seam actually stalled.
- `OAuthAuthorizationFlowTest` uses an `AnnotationConfigWebApplicationContext`,
  fixture sessions and direct JDBC runtime connections. Its passing OAuth authorization
  flow is not evidence that the full Boot ordinary login path succeeds.
- `McpTransportConfiguration.mcpAiCaseData` is conditional on Case composition or
  enabled disclosure composition. The ordinary OFF matrix has neither condition.
  `McpAiCaseData` constructs the dedicated pool/verifier only when that bean is created;
  `DataSources.aiCaseData()` is lazy. The existing OFF composition test checks bean
  absence and route isolation, but cannot replace the required real SQL Boot smoke.
- The merged V2 implementation commit `1eee0106` did not change `AuthController`,
  `AuthServiceImpl` or the ordinary `DataSources` pool factory. Its readiness-test changes
  choose the installer and explicit contract version. This does not prove either a
  pre-existing fixture defect or a V2 regression. Ordinary pools enforce TLS with
  `hostNameInCertificate=*.database.windows.net`; the documented local fixture must
  support that name as well as `localhost`. A successful dedicated-identity localhost
  probe alone would not prove the ordinary pool's TLS connection succeeds.

### Outcome and resumption

Root cause: **unidentified**. Exact fix: **none claimed or applied**. Before/after:
#1927's reported login timeout remains unresolved; the fresh environment cannot reach
that request without the authoritative fixture. No SQL blocking/session evidence, Java
request-stack evidence or measured request timing was collected. Phases 1–5 are incomplete.
Timeouts, login smoke, assertions, authentication, RLS and production behavior are unchanged.

Provide the hash-gated private fixture and restore an owned disposable SQL/TLS harness
using the existing documented contract, then collect bounded request/server/JDBC timing
and SQL-session/thread evidence before selecting any fix. Run every mandatory local
suite against that fixture with zero unexpected skips. Current follow-up commands/counts
and unexecuted gates are recorded separately in
[the follow-up verification record](phase3b8-login-timeout-followup.json).

Native Azure identity/read-only acceptance and disposable Azure mutation/fault acceptance
remain separate **OPEN** deployment gates. No production connection, migration, identity
provisioning, JAR deployment or capability activation occurred. The seven installed
Phase 3B production migrations must not be rerun.

Follow-up verification: focused non-SQL Java checks 63/0 failures/0 errors/0 skips;
critical reactor 228/0/0/0; Phase1R Python runner 3/0/0/0; selector Python 27/0/0/0.
Readiness invocation discovered 1 test and skipped 1 (no runtime reproduction). V2 Python
discovered 4 and skipped the private-input check (3 executed). Local server packaging
passed with tests explicitly skipped in that packaging invocation; it adds no SQL evidence.
Mandatory SQL suites listed in the follow-up JSON remain unexecuted. No counts here
replace the original #1927 counts, and counts across groups overlap.
