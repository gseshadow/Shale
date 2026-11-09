# Phase 4 OAuth bootstrap datasource security invariant

This scoped hardening starts from `b3d6f86c` on `codex/latest`, including merged PR #1942.
This runbook and the companion verification record were absent at that baseline. Existing authority
remains in `production-mcp-deployment-preflight.md`, `production-mcp-integration-readiness.md`, and
`testing/production-mcp-case-composition.md`; this change does not supersede their activation gates.

## Required invariant

Every enabled OAuth exchange/bootstrap datasource validates its complete dedicated configuration
before allocating a datasource, opening SQL, resolving a code, or exchanging credentials. Bootstrap,
AI Case, and readiness verifier sources share `AiJdbcUrl.validate`. No ordinary pool or credentials
are fallback candidates. Readiness validates all three URLs before allocating the AI pool.

AI JDBC URLs select only the destination and optional application name. Supported syntax is
`jdbc:sqlserver://host[:port][;databaseName=value][;applicationName=value]`, with driver-style named
instances and bracketed IPv6 also accepted. Ports are 1–65535. Duplicate keys, blank values,
control characters, malformed settings and brace-escaped values are rejected. A trailing semicolon
is accepted. Property names are case insensitive. Only `databaseName` and `applicationName` are
allowed: unknown driver settings fail closed, including future security aliases.

TLS and identity policy belong to datasource properties. Encryption is explicitly `true` and
`trustServerCertificate=false`. No hostname override is permitted: the Microsoft driver validates
the server name from the destination against a trusted server certificate. URL-supplied TLS,
authentication, embedded credentials, trust managers/stores, certificate pins, socket factories,
MARS and timeout settings are rejected even when their supplied value appears safe. This removes
URL/property precedence ambiguity. Separate required username/password settings retain their values.
Bootstrap keeps its existing five-second login and ten-second socket timeouts.

Only `exchange-enabled=true` constructs the OAuth bootstrap provider. Absent/false switches require
no bootstrap configuration and allocate no bootstrap datasource. All eight controls remain OFF by
default. No new startup SQL probe, background task, endpoint or token contract is introduced.
Malformed configuration blocks enabled startup; SQL/TLS failure withholds the exchange and emits
only `OAuth bootstrap unavailable.` without a driver exception chain. SQL log protection is installed
before driver configuration, including when Case composition is OFF.

## Operator review after merge

No immediate deployment, SQL, Azure, credential, migration, immutable binding, or activation action
is required. Before a separately authorized future release enables an affected source, review its
URL offline for this destination-only format. Remove embedded policy/credential/timeout options
through the normal governed configuration process; do not add a validation bypass. Ensure the
existing JVM trust configuration trusts the server certificate and that its hostname matches the
URL. A URL that previously supplied insecure TLS or extra driver properties will now fail startup.
Synthetic OAuth SQL fixtures also need a trusted localhost certificate for bootstrap; owner/setup
fixture connections are separate. No test/development TLS exemption is provided.

## Audit compatibility and retained gaps

This is process configuration validation, not a sensitive data read or domain/admin mutation; it
creates no audit event and needs no audit schema/migration. Existing SQL exchange, revocation and
Case disclosure audits remain authoritative and unchanged. Never audit rejected URLs or credentials.

Offline driver-property tests do not prove a deployed TLS handshake, native Azure catalog
acceptance, production readiness, identity separation, public ingress, monitoring or pilot approval.
Those existing gates remain open under their current records. Browser authorization/consent UX is
outside this PR. No production datasource was contacted and no activation/deployment is authorized.

# Phase 4 browser authorization and consent — November 9, 2026

Source baseline `4eef59b13c3a744996a0e66f9b02739f27ce677c` includes merged #1943.
This additive browser implementation supersedes only the bearer-manual UX limitation above.
All existing production readiness, native Azure, binding and activation gates remain applicable.
No production SQL, Azure change, deployment, activation or immutable-binding edit is part of this PR.

## Security design

1. With existing composition/authorization switches enabled and the optional
   `shale.ai.oauth.browser-origin` configured, an unauthenticated HTML navigation to the existing
   authorization endpoint redirects to that fixed first-party origin's `/oauth/mcp/authorize` route.
   The original OAuth query travels in the fragment, never as a WEB credential. Host/Forwarded
   headers cannot select the UI origin. The property reuses canonical origin and production/test
   separation validation; production requires HTTPS. Without it, the existing bearer-only protocol
   remains available. This property is not an activation switch; all eight switches retain OFF defaults.
2. Existing startup verification, credential login, safe return, WEB session issuance and session-generation
   request controls own authentication. The OAuth route is protected but is not a sidebar destination.
   No new account, cookie, Spring session, credential store or password endpoint exists. Every protocol
   request independently revalidates the signed, session-bound WEB bearer, durable session/JTI and WEB type.
   Generic cookies, legacy tokens, DESKTOP sessions and asserted tenant/user identities cannot authorize.
3. The browser uses authenticated JSON requests with `credentials: omit`, `redirect: error`, no-store,
   no-referrer, a 16 KiB response bound and an eight-second request/body deadline. The server wraps
   maintained-AS redirects as JSON only for this browser representation; token/revocation contracts
   are untouched. Thus fetch never follows a client callback with the WEB bearer. Only the final
   validated callback navigates the browser; no WEB bearer is put in history, fragments or URLs.
4. Consent displays registered integration name/ID, both requested permission descriptions, approving
   user and tenant, five-minute intent deadline, one-hour grant deadline, and the existing disclosure
   notice. Approve grants the displayed requested scopes; Deny submits no scope. Neither mounting,
   loading nor returning from login approves. No cached consent or automatic retry exists. Decision
   buttons coalesce duplicate clicks; an uncertain decision requires restarting from the client.
5. The existing maintained Authorization Server and SQL procedures remain authoritative for exact
   registered redirects (including the existing native loopback-port rule), original client state,
   canonical issuer/resource, mandatory S256, tenant/client/user/original-WEB-session binding,
   five-minute intent expiry, 60-second hashed single-use codes, authority rechecks and replay control.
   Denial consumes the durable intent and emits only `access_denied`; invalid/expired/foreign requests
   fail locally without credentials. Browser callbacks additionally check original target and state.
6. A consent nonce is random, durable, single-use and bound to the original WEB session, distinct from
   client state. Ambient cookies never authenticate consent, so a foreign page cannot create an
   authenticated approval. Origin checks reject foreign/null/duplicate origins; cross-origin browser
   access allows only the configured UI origin, GET/POST and Authorization/Accept/Content-Type, without
   credentials. Bearer-only nonbrowser requests can omit Origin. No session identifier is accepted from
   OAuth input or created by this transport, preventing a second session/fixation path.
7. The API emits no-store/no-referrer and frame denial on browser endpoints. The static hosting config
   adds frame-ancestors/X-Frame-Options denial and no-referrer to protect the actual consent document.
   The browser removes the initial fragment after loading the durable intent; refresh then requires a
   new request. No third-party assets, analytics, token storage or payload logging is added.

## Audit compatibility

Existing `AiOAuthTransactionStore` / SQL lifecycle ownership is unchanged. Grant creation, intent
consumption, code hash and `AI_DELEGATION_GRANT` entity-action audit commit in the existing business
transaction; audit failure rolls back issuance. Exchange, revocation and disclosure audits retain their
existing seams, schemas, sanitization and metadata allowlists. Consent viewing exposes only already
available registration configuration and the requesting actor's identity, not Case/PHI data; it adds no
PHI read audit. Begin/deny retain existing durable intent history; this PR adds no separate UI-event or
failed-login audit. Startup origin validation and browser navigation are not domain mutations. No audit
schema/migration is needed. No bearer/code/nonce/client state or payload is added to audit metadata.

## Deployment dependencies and acceptance

These are future operator actions, not instructions to execute during source review:

- Deploy matching reviewed server and web artifacts through the normal governed process, retaining
  compatible rollback artifacts and all eight switches OFF. No new SQL migration or binding edit is
  required for this browser delta. Do not rerun previously accepted predecessor migrations.
- Set the exact HTTPS Shale UI origin in `shale.ai.oauth.browser-origin` (relaxed environment name
  `SHALE_AI_OAUTH_BROWSER_ORIGIN`) only in a separately authorized configuration change. It must have
  no path, query, trailing slash or fragment. Compile the web app with the approved canonical API origin.
  Existing `/api/auth/*` CORS configuration must already allow that UI. The OAuth filter supplies its own
  narrow CORS because these endpoints execute before MVC. Do not add wildcards or credentialed CORS.
- Serve `/oauth/mcp/authorize` through SPA fallback at the web host. Verify effective frame denial,
  no-referrer and HTTPS for HTML, and no-store for OAuth API responses at CDN/proxy/host layers. The supplied SWA
  header configuration must actually be published; meta referrer alone does not prevent framing.
  Suppress query strings, Location headers, bodies and Authorization in ingress/APM/error capture on
  OAuth routes. Initial API authorization URLs contain client state; code callbacks necessarily reach
  the client. The client must immediately redeem/clear callbacks, validate state and `iss`, avoid
  referrer/analytics leakage, and protect its PKCE verifier. Never collect raw HAR or callback URLs.
- Independently satisfy existing SQL/identity/V2/native acceptance, TLS/ingress and operator/tenant/legal
  gates before any authorization/exchange/MCP switch or grant is enabled. This PR authorizes none.
- Acceptance on a separately approved synthetic deployment: signed-out and signed-in flows; keyboard
  and mobile consent; correct integration/user/tenant/scopes; no automatic approval; Approve and Deny;
  expired/unknown/duplicate/substituted parameters; foreign tenant and replacement/revoked session;
  code replay/wrong PKCE; audit failure; reload and response loss; foreign Origin and framing attempts.
  Verify exact state/issuer at callback, no credential after failure/denial, and unchanged ordinary
  login/refresh/logout/session-revocation behavior. Record sanitized outcomes only.

## MCP 2026-07-28 and Hermes

The revision remains pinned to `2026-07-28`, with `server/discover`, per-request protocol metadata and
no legacy initialize/session downgrade. This change affects browser authorization transport only:
RFC 8414/RFC 9728 discovery, resource indicators, S256 code exchange, issuer responses, public `none`,
pinned confidential `private_key_jwt`, refresh rotation and revocation keep their contracts.
`McpSdkCompatibilityTest` verifies the pinned SDK/revision; existing protocol fixtures remain authoritative.

Hermes live interoperability is **OPEN**, not inferred from the UI name or mocked browser test. Capture
its exact version and confirm modern `server/discover`, required protocol headers, RFC 9728/8414
path-aware discovery, one exact resource, state and `iss` verification, canonical S256 and exact registered
callback (or supported native loopback). Client registration remains administratively preprovisioned;
DCR, arbitrary client metadata URL registration, confidential client secrets and legacy MCP revisions
are not added. A Hermes build requiring those features cannot use this profile without separate work.
Test public authorization, denial, expiry, refresh and revocation against an approved synthetic deployment
before claiming Hermes support. No manual WEB bearer entry should be needed in the implemented UX.

## Rollback and containment

Disable authorization/exchange/MCP through existing governed controls and revoke affected credential
families/grants when required. Remove browser-origin configuration to return to the previous bearer-only
surface, or restore compatible server/web artifacts together after draining pending browser transactions.
In-flight intents expire after five minutes; issued codes after 60 seconds. Removing the UI does not
revoke existing delegated credentials. Retain additive SQL, immutable binding, audits, consumed intent/code
history and revocation state; never restore database history or loosen redirects/session checks to recover.
