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

Commands and final results will be recorded after the focused, selector-affected and critical runs.
Maven 3.9.11 and JDK 21 use the existing workspace launcher and dependency cache. Initial development
runs exposed a missing-property test fixture error and an unavailable data-module Mockito dependency;
the fixture and test were corrected without adding a dependency or weakening the security contract.

No production SQL/Azure operation, credential change, migration, binding change, OAuth/MCP activation,
deployment or browser consent implementation occurred. No audit schema/events were added: startup
configuration validation has no trusted business actor or domain mutation. Existing transactional
exchange/revocation/disclosure audits are unchanged. Retained live TLS/native Azure and production
acceptance gaps and future operator review are documented in the companion runbook.
