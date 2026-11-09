# Phase 4 OAuth bootstrap datasource hardening verification

Baseline: `b3d6f86c`, merged PR #1942, target `codex/latest`. The two Phase 4 document paths
requested for review did not exist at baseline; this record and the companion runbook are new.
Reviewed the existing production MCP readiness/preflight and Case composition evidence instead.

## Root cause and impact

`OAuthCodeBootstrapConfiguration` passed its JDBC URL directly to `SQLServerDataSource` without
validating security overrides or explicitly setting TLS. Enabled exchange could therefore use
`encrypt=false`, `trustServerCertificate=true`, a substituted hostname/trust implementation, or
URL-embedded authentication/credentials while Case readiness used a different construction path.
A readiness pass did not establish the exchange source's effective policy. Invalid URLs and blank
settings were deferred to connection time. Bootstrap-only composition also missed Case startup's
SQL driver log protection. This could expose credentials to an unverified server or unsafe transport.
No exploit, production insecure configuration, or credential exposure is asserted.

## Implementation and test-impact review

- Shared `AiJdbcUrl` replaces duplicated AI Case/readiness URL guards and is used by OAuth bootstrap.
  A destination-property allowlist closes malformed URL and unknown driver security-option gaps in
  the former regex. AI Case, catalog verifier and bootstrap use the same rule.
- Bootstrap explicitly forces encryption/certificate validation and retains driver server-name
  verification, dedicated credentials, existing timeouts and the exchange-enabled conditional.
- Readiness rejects all unsafe dedicated URLs before pool creation. Both enabled paths install the
  existing SQL log protection before configuring driver sources. Connection failure chains are withheld.
- Existing pool/composition tests were inspected and extended. The opt-in OAuth SQL fixture now
  uses a destination-only localhost URL; bootstrap requires a trusted certificate in every identity.
- New tests cover valid URLs and actual Microsoft driver property merging; malformed destinations,
  duplicate/unknown/embedded security options; missing/blank fields; startup failure before source
  construction; disabled/absent switches; suppressed driver logs; sanitized connection exceptions.
  Existing `azure` and `prod` all-OFF Boot regressions verify dormant startup and closed routes.
- Test selection owns the validator/pool security paths and includes the new security tests in the
  critical inventory. No GitHub test workflow was added or invoked.

Production-to-test mapping: `AiJdbcUrl` / `DataSources` -> `AiJdbcUrlTest`,
`DataSourcesAiCaseDataTest`; `McpAiCaseData` -> `McpCaseCompositionTest`;
`OAuthCodeBootstrapConfiguration` / SQL log protection -> `OAuthCodeBootstrapConfigurationTest`,
`OAuthConfigurationTest`, `McpProductionOffBootTest`, and the inspected opt-in
`OAuthAuthorizationFlowTest`.

## Local verification

Final results: **PASS**. Zero failures/errors/skips in the executed Maven tests.

| Check | Result |
| --- | --- |
| Focused datasource/OAuth/composition/OFF Boot | 27 tests PASS |
| Selector affected security-data/server/Case dependencies | 197 tests PASS |
| Critical reactor, all modules | 237 tests PASS |
| Selector Python unit tests | 32 PASS |
| Deployment-binding packet checks selected by security-data | 5 PASS |
| `git diff --check b3d6f86c HEAD` | PASS |

Exact commands, from the repository root (the existing `/workspace/.tools/bin/mvn` launcher
selects Maven 3.9.11, JDK 21, workspace cache and the configured proxy/trust settings):

```bash
/workspace/.tools/bin/mvn -B -pl shale-server -am -Dtest=AiJdbcUrlTest,DataSourcesAiCaseDataTest,OAuthCodeBootstrapConfigurationTest,OAuthConfigurationTest,McpCaseCompositionTest,McpProductionOffBootTest -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base b3d6f86c --head HEAD --output work/selection.json
PATH=/workspace/.tools/bin:$PATH python build/test-selection/run_selection.py work/selection.json --command affected
python -m unittest discover -s build/test-selection -p test_select_tests.py
python -m unittest discover -s build/deployment-binding -p test_packet.py
/workspace/.tools/bin/mvn -B test
git diff --check b3d6f86c HEAD
```

The selector's areas cover shared connection configuration, OAuth/exchange/filter isolation,
Case readiness, tenant/RLS/audit and ordinary authentication regressions. The selector flagged the
inventory/mapping edits for informational full-suite escalation; `-Pall-tests` was not run. The
required focused, selected affected and critical checks passed. Opt-in SQL classes execute no SQL
without their explicit disposable-fixture environment. No live SQL, certificate handshake, native
Azure acceptance or browser/client consent campaign was executed; those are not claimed as passes.
Maven 3.9.11 and JDK 21 use the existing workspace launcher and dependency cache. Initial development
runs exposed a missing-property test fixture error and an unavailable data-module Mockito dependency;
the fixture and test were corrected without adding a dependency or weakening the security contract.

No production SQL/Azure operation, credential change, migration, binding change, OAuth/MCP activation,
deployment or browser consent implementation occurred. No audit schema/events were added: startup
configuration validation has no trusted business actor or domain mutation. Existing transactional
exchange/revocation/disclosure audits are unchanged. Retained live TLS/native Azure and production
acceptance gaps and future operator review are documented in the companion runbook.

## Browser authorization and consent verification

Baseline `4eef59b13c3a744996a0e66f9b02739f27ce677c` includes merged #1943.
The [browser runbook](../production-mcp-phase4-acceptance-runbook.md#phase-4-browser-authorization-and-consent--november-9-2026)
records design, deployment dependencies, acceptance, rollback and Hermes gaps. Earlier datasource
hardening evidence above retains its original scope.

### Test-impact inventory and review

Pre-edit searches covered `McpOAuthSecurityConfiguration`, `OAuthHumanFilter`,
`DurableOAuthAuthorization`, `ShaleOAuthHuman`, `OAuthAuthorizationRequestPolicy`, their callers and
existing OAuth flow/filter/configuration/metadata/bootstrap/mapping/revocation/exchange tests.
UI inspection covered existing login/startup/session-rejection/return-path/registry/App tests and
session-generation request guards. SQL adapter/procedure and AiOAuth contract tests were inspected;
no SQL or token-contract implementation changed. Existing expectations were retained except the
intentional new protected route and static-host security headers.

Production-to-test mapping:

| Changed seam | Relevant verification |
| --- | --- |
| OAuthBrowserFilter / security composition | New origin/config/preflight/navigation/JSON-redirect tests; real mounted SQL flow; existing filter isolation, OFF prod/azure Boot, metadata, client, token and ordinary auth regressions |
| Durable consent display / OAuthHumanFilter | Existing SQL lifecycle plus authoritative user/tenant/deadline display, foreign tenant/replacement session rejection, explicit decision, expired intent, consumed-state replay and injected audit failure |
| OAuthConsent / App / registry / history | Component tests for no automatic approval, explicit approval/denial, double clicks, substituted callback/state, invalid scope/identity/deadline, expiry, response loss, stale session and unmounted continuation; existing startup/login/safe-return/logout tests |
| Static document / host config | Node routing and security-header assertions; built artifacts; desktop/mobile headless screenshot inspection |
| Critical/area inventory | Selector unit tests; affected server/dependency suite; local critical reactor |

The SQL fixture is a task-owned loopback SQL Server 2022 Developer container, synthetic database and
separate runtime/bootstrap principals. A task-owned localhost certificate is trusted explicitly in
the test JVM; the enabled OAuth bootstrap still validates hostname/certificate. No production identity,
SQL/Azure connection, binding or activation is used. Fixture setup/drop is not a production migration.
Historical expired-intent rows are inserted by the fixture owner with valid table constraints and
security triggers left enabled. Audit-fault injection is confined to that synthetic database.

### Executed checks

Final result: **PASS at local source/synthetic scope**. The critical reactor passes **242 tests**
across all modules, zero failures/errors/skips. The direct OAuth SQL flow runs **22 tests**, including the new browser cases, with zero failures/errors/skips. The affected
server suite runs **74 tests**, zero failures/errors/skips, with the disposable SQL opt-in active.
The separate SDK compatibility checks run **3 tests**, including the pinned `2026-07-28` revision.
Web component/session/router tests run **654 tests across 19 files**, all passing. Static routing and
hosting headers run **4 Node tests**, all passing. Selector Python tests run **32**, all passing.
TypeScript checking and Vite production build pass.

A headless Chromium run uses the actual React app and existing credential login, with synthetic API
responses: signed-out deep link -> login -> account/permission consent -> one explicit approval ->
exact callback. It verifies no approval on load, fragment removal, original consent nonce, one decision,
WEB Authorization only to the API, and no page errors. Desktop 1100x850 and mobile 390x844 screenshots
were inspected. This is browser/UI evidence with a mocked API; the real SQL mounted-filter checks are
separate. It is not an actual Hermes, production ingress, Azure, device/assistive-technology or fully
combined browser-plus-SQL acceptance campaign.

Reproduction commands (the existing workspace Maven launcher selects JDK 21/Maven 3.9.11):

```bash
# Set only the documented LOCAL_DISPOSABLE_AI opt-in, loopback port and synthetic owner password.
# The task-local truststore includes the generated localhost SQL certificate and normal trust roots.
/workspace/.tools/bin/mvn -B -pl shale-server -am \
  -Djavax.net.ssl.trustStore=/workspace/Shale/work/phase4-sql/cacerts \
  -Dtest=OAuthAuthorizationFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn -B -pl shale-server -am \
  -Dtest=OAuthBrowserFilterTest,OAuthFilterIsolationTest,ShaleOAuthHumanTest,OAuthConfigurationTest,McpSdkCompatibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base 4eef59b1 --head HEAD --output work/phase4-selection.json
# The task-local mvn shim adds the same trustStore property; the disposable opt-in remains set.
PATH=/workspace/Shale/work/phase4-bin:$PATH python build/test-selection/run_selection.py work/phase4-selection.json --command affected
python -m unittest discover -s build/test-selection -p test_select_tests.py
/workspace/.tools/bin/mvn -B test
npm --prefix shale-web test -- src --maxWorkers=2
node --test shale-web/tests/static-routing.test.mjs
npm --prefix shale-web run build
git diff --check 4eef59b1 HEAD
```

The selector maps this change to server/OAuth, ordinary authentication and dormant composition;
`-am` also compiles core/data dependencies. Inventory/mapping edits trigger informational full-suite
escalation; `-Pall-tests` is not required for this focused increment and was not run. No GitHub testing
workflow was added or executed. The first broad `npm test` invocation collected the existing standalone
Node static-routing file under Vitest/jsdom and failed with `URL must be of scheme file`; its 653 component
tests passed. Running the component suite under `src` and the static test with its documented Node
runner resolves that runner mismatch without changing unrelated test infrastructure. A first SQL replay
assertion accidentally reused a mock request, duplicating Authorization and correctly getting 401;
the fixture now creates a fresh replay request and verifies the intended 400 consumed-intent result.

No real Hermes version or production browser/client acceptance is claimed. Existing native Azure,
V2/binding/readiness and operator/tenant/legal activation gates remain open at their established scope.
