# PR B — actual disposable client connection evidence

Stacked on draft #1955 (`a81f0ecf`). No production SQL, activation or deployment.
CIMD is default-OFF and production/azure activation is refused in this increment.

## Actual client and registration

Unmodified NousResearch **Hermes Agent**, commit
`74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5`, reported version
`git.74dde23` (`base_version=unknown`, clean git checkout), Python 3.14.7,
MCP Python SDK **2.3.0**. Installed using its own frozen lock and `mcp` extra.
Registration method: **CIMD**, exact built-in external identifier:

`https://nousresearch.github.io/hermes-agent/docs/oauth/client-metadata.json`

No DCR, static OAuth ID, manually selected metadata URL, callback, scope or protocol
configuration was supplied. Empty task-owned Hermes profiles were used independently
for tenants A and B. CLI command:

```text
hermes mcp add shale --url https://shale.test/mcp --auth oauth --connect-timeout 300
```

`--auth oauth` is the client's generic authentication choice; 300 seconds is its
interactive connection window. Shale metadata, SQL, Case and HTTP deadlines are unchanged.
The CLI itself completed OAuth, auto-negotiated the modern protocol and listed both tools.
A thin driver then invoked Hermes' unchanged registration and tool-handler functions
with ordinary tool arguments, using its own stored credentials. No client code was patched.
This deterministic driver avoids needing an LLM/API key to issue a tool call.

## Deployment and trust limits

Separate disposable identity `https://shale.test/mcp`, loopback TLS frontend, real Boot
HTTP server, existing React login/consent page, and nonce SQL Server database.
The production product URL remains `https://api.myshale.com/mcp`; its literal host and
production ingress were not contacted or activated. Existing production/test identity
separation is preserved. Disposable leaf/CA trust is explicit; no TLS verification is disabled.

Authoritative private fixture:
`/workspace/attachments/8f9cee9b-4e28-4608-af20-77e7af188279/authoritative-candidate.sql`,
SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
It is neither regenerated nor included in this PR. Runtime JDBC uses verified TLS.

The governed environment cannot complete the default direct DNS/TCP fetch inside the
existing metadata budget. Test-only resolver/connector seams use fresh actual A/AAAA
answers, unchanged all-answer public classification, numeric proxy CONNECT and original
vendor-host TLS. Metadata is fetched live, not substituted. Production defaults are
unchanged. **Direct-egress deployment acceptance remains a blocker/gap.** This run proves
the integrated client flow under the explicitly recorded disposable routing fixture.

## HTTP/browser/SQL results

Both tenants completed: MCP URL → 401 resource challenge → protected-resource metadata
→ AS metadata → stock browser opener → existing SQL password login and durable WEB
session → fresh tenant pending request → explicit administrator approval → separate
explicit user consent → code/PKCE exchange → modern discovery → tools/list → tool calls.

The original relative consent URI was pinned to the configured external issuer, rather
than accepting internal/forwarded origins. A legacy initialize is rejected with the
standard unsupported-method signal, allowing Hermes' built-in auto negotiation to retry
`server/discover`. Legacy tool/handshake support is not added; modern header/body equality
and every existing authorization gate stay enforced.

Sanitized evidence is under [`evidence/universal-mcp-pr-b`](evidence/universal-mcp-pr-b).
Only method/path/status/timings, synthetic result facts, browser steps and SQL counts are
retained. State/code URLs, credentials, private schema and TLS keys are excluded.

Final isolated timings/counts and HTTP security results are recorded in those artifacts.
Returned disclosure is checked against the committed READ/ALLOWED_READ audit, matching
RETURNED resources, consumed handoff, released quota and PHI Overview counts. Historical
receipts and immutable deployment binding are asserted unchanged during fixture cleanup.

A prior overlapping SQL regression workload produced an 8.6-second end-to-end Overview
and a later 10-second timeout. Verification withdrew that instance, as PR A requires.
The final acceptance reconstructs the instance and runs sequentially. No load/SLA or
concurrent-mutation readiness claim is made; deadlines/enforcement were not relaxed.

## Targeted verification

- Focused OAuth/CIMD/MCP Java tests: 125 reported (124 run), zero failures/errors; one SQL-opt-in
  browser fixture skipped in that no-SQL invocation (not counted as SQL acceptance).
- Real authoritative SQL regressions: six selected scenarios passed, zero skipped:
  post-read revocation, additive drift, two-tenant lifecycle/legacy exclusion, refresh
  replay, publication revision change, and transactional audit-failure rollback.
- Existing and new consent tests: 18 passed; TypeScript typecheck and development build passed.
- Servlet integration regression uses actual Boot REQUEST/ASYNC dispatch, verifying
  anonymous 401 discovery challenge and ordinary API liveness without manual filter mapping.
- Latest directly modified tests: 25 passed (browser-origin and transport regressions).
- Local critical server reactor: 225 passed (core 6, data 130, server 89), zero skipped.
- Selector and final diff checks are recorded below after execution.

The harness sources and run prerequisites are in
[`build/universal-mcp-pr-b`](../../build/universal-mcp-pr-b/README.md).
They are development evidence tooling, not a new production runtime or provisioning system.

Final isolated stock tool timings: tenant A search **4,792 ms**, Overview **4,716 ms**;
tenant B search **4,787 ms**, Overview **4,915 ms**, including HTTP admission and SQL work.
SQL recorded **7 matching READ/RETURNED/handoff disclosures**, **5 Overview PHI rows**,
and **8 released quota reservations** (including a denied foreign Case attempt).
Supplemental HTTP revocation: same credential returned a Case before revocation, empty
200 revoke, then 401 access denial and 400 refresh denial; unknown revoke also returned 200.

Cleanup passed: all task nonce databases/logins were absent after normal fixture shutdown;
historical receipt/binding assertions completed successfully. The disposable SQL container,
listeners, TLS keys/certificates, passwords and client/browser credential caches were removed.
The attached fixture's SHA-256 was reverified unchanged. No existing PR was merged or closed.

## Final repository checks

Selector base: `a81f0ecfc72c76303a8300f3ea24e0068159eb90`; areas: cases, security-data,
server; modules: core/data/server. Its affected suite passed **265 tests** (2/137/126),
zero failures/errors/skips, plus **5 deployment-binding tests**. The selector's full-suite
suggestion is informational and comes from new development-harness paths; no broad SQL
campaign or unrelated JavaFX suite was run. The relevant critical reactor passed 225 tests.

Commands used (Maven shorthand below means the task-local Maven 3.9.11 binary,
`-s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2`, with the
JDK 21 `JAVA_HOME` and `/etc/ssl/certs/java/cacerts` JVM trust):

```text
mvn -pl shale-server -am -Dtest=*OAuth*Test,Cimd*Test,Mcp*Test -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-server -am -Dtest=OAuthBrowserFilterTest,McpTransportTest -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base a81f0ecfc72c76303a8300f3ea24e0068159eb90 --head HEAD --format json --output work/pr-b-selection.json --plan-output work/pr-b-selection-plan.json
# Execute selected_maven_args from that plan, with the same Maven prefix.
python -m unittest discover -s build/deployment-binding -p test_packet.py
mvn -pl shale-server -am test
npm run typecheck
npm test -- OAuthConsent
VITE_SHALE_API_BASE_URL=https://shale.test npm run build
git diff --check
```

The six real SQL methods selected explicitly were:

- `AiCimdAuthorizationLocalSqlServerTest#revocationAfterReadWithholdsDisclosureAndPreservesOtherTenant`
- `AiCimdAuthorizationLocalSqlServerTest#admissionRejectsAdditiveDriftWithoutChangingHistoricalVerification`
- `AiCimdOAuthLocalSqlServerTest#lifecycleTwoTenantIssuanceRotationAndLegacyExclusion`
- `AiCimdOAuthLocalSqlServerTest#lifecycleRefreshReplayCommitsWholeFamilyRevocation`
- `AiCimdOAuthLocalSqlServerTest#lifecycleChangedPublicationBlocksPendingCodeExchangeAndRefresh`
- `AiCimdOAuthLocalSqlServerTest#lifecycleAuditFailureRollsBackExchangeRotationReplayAndRevocation`

Only the new onboarding SQL candidate is added. Every previously tracked SQL script and
packaged SQL resource is byte-for-byte unchanged from #1955. Audit integration uses the
existing SQL schema and transaction owners; no new audit schema or receipt refresh is introduced.
