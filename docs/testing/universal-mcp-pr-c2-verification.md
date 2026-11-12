# PR C2 — actual DCR acceptance

Base: draft #1957, `8aea2c4c22489e0f23b057ec479dcb01b53d31e2`.
Date: November 12, 2026 (UTC). Development only; production controls remain OFF.

**Passed:** real RFC 7591 registration; stock URL-only DCR connection in two fresh
profiles; actual browser login, administrator approval and separate user consent;
tokens and both Case tools; HTTP tenant isolation, refresh/revocation and manual
continuity; fresh stock Hermes/CIMD regression; final authoritative SQL acceptance.
No production endpoint, production SQL, deployment, activation or merge occurred.

## Stock client and exact configuration

Unmodified **NousResearch Hermes Agent**, commit
`74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5` (`git.74dde23`, clean checkout),
Python **3.14.7**, MCP SDK **2.3.0**, installed with its frozen lock and `mcp` extra.
Two separate empty `HERMES_HOME` profiles used:

```text
hermes mcp add shale --url https://shale.test/mcp --auth oauth --connect-timeout 300
```

No client ID, metadata URL, callback override, scopes or protocol option. The auth
choice and interactive timeout are ordinary CLI options; server SQL/Case deadlines
were not relaxed. The CLI itself performed discovery, automatic DCR, OAuth and tool
listing. The same thin tool driver used in PR B invoked unchanged Hermes discovery
and tool-handler functions with normal arguments, with no model/API key. It does
not implement OAuth, transport, credential storage or SQL.

For DCR acceptance, the actual Shale development server was configured with
`shale.ai.cimd.metadata-advertisement-enabled=false`: AS metadata advertised DCR
and withheld optional CIMD metadata-document support. Hermes therefore selected
DCR automatically. This is a server capability profile, not a supplied client ID,
client patch or rewriting proxy. The lifecycle remained fully configured and
verified. Normal CIMD advertisement defaults true when CIMD is enabled; restoring
it in the same binary/database and using another fresh Hermes profile verified
its original CIMD URL identity and both Case tools, without a registration POST.

The headless test opener transferred the stock CLI's own printed URL privately to
Firefox. Actual Shale credentials were checked through existing BCrypt/SQL auth
and durable WEB sessions. Each profile separately approved the tenant application
and then explicitly consented to user access. Screenshots show the two distinct
screens. No code/token was injected into the browser or client.

[Client/version record](evidence/universal-mcp-pr-c2/clients.json),
[browser A](evidence/universal-mcp-pr-c2/browser-a.json),
[browser B](evidence/universal-mcp-pr-c2/browser-b.json),
[tenant approval](evidence/universal-mcp-pr-c2/tenant-approval.png),
[explicit consent](evidence/universal-mcp-pr-c2/user-consent.png),
[tools A](evidence/universal-mcp-pr-c2/tools-a.json),
[tools B](evidence/universal-mcp-pr-c2/tools-b.json),
[CIMD tools](evidence/universal-mcp-pr-c2/tools-cimd.json).

## Actual HTTP, browser and SQL evidence

[Sanitized DCR HTTP](evidence/universal-mcp-pr-c2/http-dcr.json) records the 401
challenge, protected-resource/AS discovery, actual registration JSON and 201
accepted metadata, browser API operations, token issuance and modern MCP calls.
Generated client IDs are redacted after checking their namespace; state/code URLs,
credentials, assertions, private keys and tokens are excluded. Unknown registration
fields confer no authority. Both public `none` and public-RS256 `private_key_jwt`
registration returned 201 without a client secret. Full stock flow used public
S256; confidential exact-identity/assertion behavior is covered by focused tests.

[HTTP security](evidence/universal-mcp-pr-c2/http-security-evidence.json) verifies:

- B cannot disclose A's Case; ordinary users and B administrators cannot change
  A's approval.
- Access/refresh rotation succeeds. A approval revocation denies A access/refresh
  while B continues. B family revocation denies its access/refresh; unknown-token
  revocation returns the same empty 200.
- A fresh ordinary consent/code exchange for the **existing** manual registration
  yields an authorized Case call. Its original short-lived fixture token had
  expired; no credential lifetime or authorization check was extended.

[Registration policy/abuse](evidence/universal-mcp-pr-c2/http-registration-security.json)
records invalid default authentication, invalid redirects, caller-selected identity,
actual 413, confidential registration and source 429 with positive Retry-After.
[Disabled-control evidence](evidence/universal-mcp-pr-c2/disabled-registration.json)
shows that switching the disposable SQL DCR control OFF removes advertisement
and returns 503 for registration. Restoring it retains rate state.

[SQL counts](evidence/universal-mcp-pr-c2/sql.json) record three DCR applications
with three matching creation audits (two stock profiles plus neutral confidential
registration), then one CIMD application. Eight external disclosures matched
READ/ALLOWED_READ, RETURNED resources and released quota/handoff records. Six PHI
Overview audits include the separate manual read. These are actual restricted
Case operations over the authoritative fixture, not mocked SQL responses.

## Authoritative acceptance and targeted tests

The attached fixture remained unchanged, SHA-256:
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
The inherited C1 owner gate checks that exact hash before the first SQL connection.
SQL Server **16.0.4315.2 Developer** ran only on loopback in the owned container.
JDBC used encryption, hostname verification and `trustServerCertificate=false`.
A disposable CA was trusted explicitly; no TLS verification was disabled in the
C2 authoritative or live stock-client fixtures.

Final authoritative selection: **4 passed, 0 failures/errors/skips**, 215.440 seconds:

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-server -am -Dtest=AiDcrEndpointLocalSqlServerTest#c2* \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

[Individual SQL results](evidence/universal-mcp-pr-c2/authoritative-sql-tests.json)
cover neutral creation/two-tenant consent and legacy isolation; refresh, approval
and application revision invalidation; exact injected audit-fault rollback plus
V1/C1 rejection and successor drift/historical preservation; concurrent durable
source/global/fingerprint admission, expired buckets and approved-metadata retention.
The audit regression checks the injected SQL fault code, excluding an earlier
registration refusal as a false success. Final installer source:
`086cf01d99097a2a5a43d9e5a77fdf5a28443916c1795559e55aa2d8e97f0a13`.

Latest focused reports across documented runs: **126 passed**, zero failures,
errors or skips; [class inventory](evidence/universal-mcp-pr-c2/targeted-tests.json).
They cover store/admission delegation, explicit verification selection, metadata
and development gates, strict registration/UTF-8/JSON/body limits, public/private
keys, S256/redirect binding, CIMD secure retrieval, manual mappings, OAuth refresh/
revocation and real all-OFF prod/azure Boot. The latter checks no DCR capabilities,
no AI pool and closed registration route. Commands used this focused class set:

```text
DcrRegistrationEndpointTest,DcrConnectionConfigurationTest,DcrApplicationMetadataTest,
OAuthMetadataTest,CimdConnectionConfigurationTest,AiDcrApplicationStoreTest,
CimdBrowserBindingTest,CimdClientAuthenticationTest,OAuthClientMappingTest,
OAuthAuthorizationFlowTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,
CimdMetadataTest,CimdRetrievalTest,McpCaseCompositionTest,AiCaseDataAttestationTest,
AiCaseDataAccessTest,McpProductionOffBootTest
```

Use the same Maven form above with those `-Dtest` selections. The manual OAuth
fixture needs the disposable SQL certificate passed as a **forked test JVM** trust
property, not only MAVEN_OPTS. Its initial environment failures resolved with the
correct explicit truststore; no production code or historical test was weakened.

Other executed checks:

```text
# From shale-web:
npm test -- src/OAuthConsent.test.tsx        # 19 passed
VITE_SHALE_API_BASE_URL=https://shale.test npm run build  # typecheck/build passed
# From repository root:
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/sql-confinement -p test_dcr_v3.py -v      # 3 passed
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/sql-confinement -p test_external_v2.py -v # 6 passed
python -m unittest discover -s build/test-selection -p test_select_tests.py # 32 passed
python build/test-selection/select_tests.py --base 8aea2c4c --head HEAD --format json
# Diff whitespace check passed.
```

The selector identified cases, security-data and server. Execution stayed focused
on changed Case verification/routing, registry/admission/audit and OAuth/browser
contracts per the requested scope; no broad critical/affected/full historical
campaign was repeated. New tests are registered in the relevant selector manifests.
All **280** baseline artifacts under SQL/resources/confinement/deployment-binding
roots are byte-identical, including C1. [Preservation receipt](evidence/universal-mcp-pr-c2/historical-preservation.json).

## Corrections, scope and remaining deployment requirements

Real SQL found and corrected C2-only admission transaction handoff, optional empty
catalog material, publication retention and multiline fingerprint projection issues.
Azure portability checks required the existing guarded batch encoding. Existing
CIMD/manual tests caught an overly broad localhost rule; the final allowance is
DCR-specific. Browser testing found and corrected CIMD-only consent-page routing.
The final registrar rechecks the DCR control under HOLDLOCK inside its mutation.
Historical SQL, receipts and immutable binding values were never rewritten.

The exact two trigger deltas, five new procedure roots/helpers, two tables, one
execute-only role and reused components are detailed in the
[C2 contract](../architecture/universal-mcp-pr-c2.md). Creation audit uses the
existing schema and dedicated SQL identity on the registration transaction;
there is no fabricated administrator or new audit framework. Public-payload expiry
retains identity/revision/audit history. Production switches remain OFF.

Deployment still requires native Azure acceptance, reviewed explicit V3 composition/
role assignments, trusted production ingress/source attribution and NAT/rate tuning,
capacity policy for the cumulative 5,000-identity ceiling, and the existing release/
operator gates. Payload cleanup is bounded and opportunistic; approved identities
are retained. Default direct CIMD egress was not accepted here: the declared PR B
fresh-DNS/numeric-proxy/vendor-TLS test seam was reused. No new signing/pool/security
architecture was introduced to close those separate gates.

`mcp-remote` 0.16.6 default legacy transport remains incompatible; its unmodified
`--protocol auto` mode completed tools but is supplemental, not URL-only evidence.
Inspector distribution source likewise defaults ad-hoc connections to legacy and
was inspected only. Hermes supplies the independent complete URL-only DCR pass.
No third-party client was patched and Shale's protocol/deadline checks were retained.

The live fixture closed with receipt/binding equality; zero fixture databases and
logins remained after all tests. The owned container, TLS private material, passwords,
raw logs and client/browser credential caches were removed. The PR stays draft;
no merge, production SQL deployment or activation is authorized by this acceptance.
