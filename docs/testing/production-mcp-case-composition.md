# Production MCP Case composition candidate

Base: `b990a09212b9e5872858005e9385d4a2b0539c9f`, merged #1922/#1923.
Status: **BLOCKED / UNVERIFIED IMPLEMENTATION CANDIDATE**. This record does not close production acceptance.

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
| Serving bounds, final handoff, clients, TLS | Existing #1922/#1923 serving/client/TLS suites; fresh production-profile proof still required |

The mocked production context test proves wiring only. It does not prove SQL confinement, actual
production-like credentials/TLS, client interoperability or authoritative acceptance.

## Execution limits

The exact signed base commit/tree and source blobs were recovered through the GitHub connector
because the executor's shell proxy was unavailable. All source and binary blobs were verified against their original Git object IDs.
The original checkout is preserved.

The focused Maven command fails before compilation because the required
`spring-boot-dependencies:4.1.1` POM is absent and outbound Maven access is denied.
Offline compilation fails for the same absent dependency. Docker socket access is denied and
the private hash-gated authoritative schema is not mounted. No SQL/client/expiry/TLS production-like
run can be claimed. Existing #1923 results retain their original test-profile/local scope only.

**Required before this candidate is accepted:** resolve the local dependency/runtime/fixture setup,
compile and run the added policy/context/readiness tests; then execute actual production-profile,
PRODUCTION-identity authoritative composition, all #1922 attacks, final-gate expiry, both clients
(including missing/deleted/revoked/expired/scope-reduction cases), local verified TLS/proxy and
ordinary regressions; run the affected suites and critical reactor once. Skips are not passes.
The production-specific serving/TLS harness work is not represented as finished by mocked wiring.

Available checks: selector Python **27 PASS** after the test inventory update; isolated compilation
and execution of the actual policy/properties against cached Spring 6 APIs **131 assertions PASS**;
JDK 21 isolated compilation of `AiCaseDataAttestation`/`AiSql`/`UtcJdbcTimestamp` PASS. These are
supplemental checks, not a Boot 4 build or SQL proof. Focused, affected and critical Maven attempts
all stop at dependency resolution before any Java test runs; their passing-test count is zero.
The selector chooses cases/security-data/server and includes the new composition test in both
affected and critical inventories. Diff/defaults/SQL-history/relative-link static checks PASS.

Commands executed:

```
mvn -o -B -pl shale-server -am -DskipTests compile
mvn -B -pl shale-server -am -Dtest=McpCaseCompositionTest,McpSearchTest,McpOverviewTest,McpFilterIsolationTest,OAuthConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base b990a09212b9e5872858005e9385d4a2b0539c9f --head HEAD --format json --output /workspace/work/production-selection.json
# The emitted selected_maven_batches argv was attempted with mvn -o -B; dependency failure.
mvn -o -B test
python3 -m unittest discover -s build/test-selection -p 'test_*.py'
git diff b990a09212b9e5872858005e9385d4a2b0539c9f HEAD --check
```

## Audit compatibility and remaining gates

Read-only readiness/attestation has no Case read or application mutation and adds no PHI/entity
audit event. Existing Overview PHI and integration parent/resource audit behavior is unchanged.
No audit schema or migration is added. Runtime permission and actual principal review remain required.

Tenant/legal retention sign-off, SDK 2.2.0-M1 production approval, security/operator/tenant-owner
pilot authorization, actual Azure SQL platform compatibility, deployed catalog/Stage A/B/owner/
collation/JAR/caller parity, public ingress, monitoring/capacity/restart/failover and R4/live gates
remain OPEN. No production/Azure SQL, Azure change, deployment, credential issuance, real tenant/
integration enablement or installer/version publication is performed. Packaged defaults remain OFF.
