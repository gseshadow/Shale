# Universal MCP PR B — working client connection

Stacked on #1955, `a81f0ecf`. Scope: CIMD HTTP/browser/SQL connection in an explicitly
enabled disposable development deployment. Production CIMD remains OFF. No DCR,
Settings inventory, nine-pool runtime or signed catalog framework is introduced.

## Boundaries and implementation

Reuse PR A's exact external identity, original WEB consent, S256, credential namespaces,
fresh revision/approval checks, rotation/revocation and audited Case disclosure.
Existing manual OAuth providers and their validators retain their default behavior.
New providers select CIMD only for a validated HTTPS external identifier; there is no
translation into a legacy client ID. Legacy SQL cannot admit CIMD credentials.

An explicit `CimdServices` composition supplies the existing narrowly privileged
capability adapters. It creates no pools, identities or grants. Development activation
requires the existing OAuth/MCP controls, a separate test/development identity, and all
required adapters. Missing dependencies fail startup. Production/azure profiles refuse
CIMD activation in this increment; absent/false needs no new configuration.

MCP admission retains existing manual attestation/authentication. Only an unknown legacy
credential may proceed to PR A's CIMD authentication, which also verifies the unchanged
predecessor. Readers use the request-owned namespace and corresponding restricted source.
No verification is removed or moved; no per-lease full verification is added. Quotas,
committed audits, final handoff and original monotonic deadlines remain authoritative.
The trusted-operator boundary and limitations in PR A remain unchanged.

## Minimal approval interaction

The existing Shale browser login remains the only login. The authenticated authorization
page prepares a public CIMD application and tenant-confined pending request, shows the
actual client/scopes and approval state, and lets a current same-tenant administrator
approve/deny/revoke through the existing transactional approval procedures. Administrator
approval never consents or issues a token. The separate existing explicit user consent
follows approval. Ordinary users can request/check approval but cannot approve themselves.

The existing registry lacks bounded application/current-approval reads required for
idempotent onboarding. Add only one exact-client, current-tenant projection and its
empty EXECUTE-only role, not a global inventory or new authorization database. It
rechecks the original live WEB/actor and explicitly qualifies tenant approval ownership.
Existing publisher/request/decision modules remain unchanged. Changes to publication
invalidate old approval/credentials; stale approval requires explicit administrator
review, never automatic reapproval or restoration of a disabled application.

## Compatibility observations before editing

Hermes automatically supplies its hosted CIMD URL and defaults to a numeric-loopback
callback. Its document also advertises localhost aliases, which Shale's legacy policy
does not implement. The new CIMD metadata profile may discard only a strict HTTP
localhost alias paired with an otherwise accepted exact numeric-loopback callback at
the same port/path. It must retain a usable redirect, reject unsafe/unpaired alternatives,
and continue rejecting actual localhost authorization requests. No historical SQL or
legacy redirect validation is changed. This is server capability filtering, not a
patched client or manually supplied client ID.

PRM publishes the two existing scopes so the stock client discovers them itself. CIMD
advertisement is gated on the complete enabled composition; DCR is not advertised.

## Test impact identified before edits

* AS composition and request validators: OAuthConfigurationTest, OAuthMetadataTest,
  OAuthCodeExchangePolicyTest, OAuthRevocationPolicyTest, OAuthFilterIsolationTest,
  OAuthAuthorizationFlowTest, CimdBrowserBindingTest and assertion tests.
* Browser/onboarding: OAuthBrowserFilterTest, ShaleOAuthHumanTest, OAuthConsent.test.tsx,
  credential login/return-route tests; real browser against real SQL, not mocked API.
* Metadata subset/profile: CimdMetadataTest and CimdRetrievalTest; paired aliases,
  unpaired/malformed/private redirects, full exact ID and DNS/TLS/SSRF checks.
* MCP composition: McpCaseCompositionTest, McpSearchTest, McpOverviewTest,
  McpTransportTest, McpProductionOffBootTest; default/manual behavior stays intact.
* New projection: actual restricted-login SQL, foreign tenant/session, current admin,
  repeat onboarding, concurrent create, stale RowVer, revocation and audit-fault rollback.
* Full connection: unmodified CIMD client, empty Shale credential/client cache, only MCP
  URL, real discovery/login/admin approval/consent/token/tool; two-tenant changes,
  refresh/revoke and actual committed audit/resource/handoff facts.

## Audit compatibility

Public application publication uses GlobalControlPlaneAuditLog; pending request and
approve/deny/revoke use existing EntityActionAuditLog in the business SQL transaction.
Grant/session/key changes and Case PHI/READ/RETURNED audits retain PR A's owners and
fail-closed behavior. The narrow own-tenant application/approval status projection has
no requester/user inventory or Case data and requires no PHI or per-row admin-read event.
No UI constructs authoritative audits. No audit schema, vocabulary or historical receipt
is changed. Never persist/log browser credentials, tokens, assertions, state or code URLs.

## Disposable acceptance and evidence limits

Use the existing separate `https://shale.test/mcp` development identity, corresponding to
the production product URL `https://api.myshale.com/mcp`. Do not weaken the existing
production/test identity separation or contact production to use its literal hostname.
Fixture bindings are installed once in a new nonce database; existing bindings are never
rewritten. The approved private schema hash remains mandatory.

This environment has no direct public TCP/TLS egress even outside the command sandbox.
A focused probe established that its governed HTTP proxy supports CONNECT to the
already validated numeric public address with TLS verification against the original
vendor hostname. The disposable harness may use the existing package-private connector
seam for that route, preserving all-answer DNS validation/pinning and no ambient Shale
credentials. Production fetcher defaults and security checks remain unchanged. Record
this routing difference; direct-egress deployment acceptance is not established here.

Record actual client commit/version/SDK, mechanism, endpoint, browser/SQL outcomes,
negative checks and cleanup. Component passes cannot substitute for a stock-client
connection. Any remaining exact interoperability failure must remain a blocker.

The real fixture rejected combining `AiCimdOnboardingRuntimeV1` with an existing
`AiTenantApplicationRuntimeV1` member: the historical registry verifier enforces exact
member permissions. The onboarding adapter therefore requires its own execute-only
identity, supplied explicitly through `CimdServices.onboarding`. Do not add that grant
to an existing approval/browser/issuer member or modify the historical verifier/receipt.
This is an adapter identity requirement, not a pool manager or an automatically provisioned
runtime topology. Authentication is separately the existing pretenant module and runtime
WEB session implementation; neither identity is a Case data capability.

## Servlet trust boundary found by the stock HTTP attempt

The current dependencies include Spring Security configuration but not Boot's modular
security servlet auto-configuration. The chains exist in the bean factory; the existing
Tomcat fixtures explicitly register `FilterChainProxy`. A Boot deployment of that same
composition did not register it: anonymous MCP POST reached the SDK with no authority
attributes and failed HTTP 500, so OAuth discovery could not begin. The first stock
attempt demonstrated this; it is not an accepted connection.

Register the existing `springSecurityFilterChain` through a lazy servlet delegate mapped
only to MCP, protected-resource metadata and OAuth/AS metadata namespaces, including
REQUEST and ASYNC dispatch. Body admission keeps its earlier order; the existing gate,
verification, scoped readers and deadlines stay in place. This adds the missing servlet
boundary and no credential authority. Ordinary Shale API routes retain their existing
controller/service authentication. Do not add a generic fallback security chain or move
confinement checks into servlet registration. Real anonymous HTTP must return the existing
401 discovery challenge before claiming integration acceptance.

The TLS reverse-proxy attempt also exposed Spring's relative consent-page default:
its JSON redirect used the internal HTTP connector origin. Pin the maintained provider's
consent page to `properties.issuer() + "/consent"`, the already validated configured
external HTTPS identity. Do not trust Host/Forwarded headers to construct that redirect.
The browser retains its exact configured API-origin check; neither it nor SQL redirect
validation is relaxed to accommodate an internal connector address.

## Exact stock-client negotiation blocker and bounded compatibility response

Hermes defaults to an unmodified auto negotiation: legacy `initialize` first, then
`server/discover` only on the standard method-not-found/unsupported-version signal.
Observed requests carry legacy protocol `2025-11-25`, no modern `Mcp-Method` or `_meta`.
The current modern-only SDK reports header mismatch before method lookup; Hermes correctly
does not interpret arbitrary header mismatch as permission to change protocols.

For a bounded, authenticated, syntactically valid legacy initialization envelope only,
return the existing unsupported-method JSON-RPC error (-32601/HTTP 404). This does not
implement legacy initialize or tool routing. Modern header/body equality, supported
version checks and every security/admission check remain authoritative for discovery
and tools. No client patch, manually supplied OAuth ID or protocol setting is used.
Regression impact: `McpTransportTest.protocolAndHeaderBodyMismatchNeverDowngrade` plus
new legacy-rejection assertions; malformed/body limits, authentication, unknown versions
and unchanged modern operations must retain their existing denials.

The disposable metadata route also uses a task-owned platform-resolver adapter through
the existing package-private test seam. It refreshes actual A/AAAA answers outside the
JVM's stalled resolver, rejects evidence older than 30 seconds, and passes every answer
to the unchanged all-answer public-address classifier before pinning a numeric CONNECT.
Vendor content is fetched live with original-host TLS verification; no metadata document
is substituted. The default production DNS/socket implementations are unchanged. This
exercises integration under a declared fixture route, not acceptance of production DNS
egress or proxy configuration. Successful observed live retrieval was under one second,
within the existing five-second metadata budget.

## Deadline and concurrency observation

A first two-tenant stock run completed disclosure but concurrent catalog-mutation SQL
regressions added substantial load: one returned Overview took 8.6 seconds end to end
and a subsequent denial hit the unchanged 10-second HTTP deadline. PR A's synchronized
full admission verification withdrew that instance after verification failed, requiring
reconstruction. This existing fail-closed rule is preserved. Run the final acceptance
sequentially on a rebuilt disposable instance, without concurrent schema/capability
mutation tests. Record both observations; do not claim load/SLA acceptance or hide the
failure by changing deadlines, catalog verification placement or automatic recovery.
