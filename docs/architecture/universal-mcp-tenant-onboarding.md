# Universal MCP tenant onboarding

Review date: November 9, 2026 (America/Denver). Source baseline:
`c4800cb515e35edd79e6b55f1f61536bd17b3ebf`, current `codex/latest`, including #1943/#1944.
The supplied original checkout was older; it was preserved. This is a proposed successor to the
manual-registration-only pilot contract, not evidence of production readiness or activation.

## Product contract and decision

A compatible client needs only `https://api.myshale.com/mcp`, existing Shale credentials, browser
authorization, and any required tenant administrator approval. No client patch, pasted client ID,
SQL work, tenant URL, or bespoke integration is part of tenant onboarding. OAuth still carries a
client ID: CIMD supplies its own HTTPS identifier; DCR obtains one automatically. URL-only describes
the user's setup, not omission of standard protocol fields.

Implement CIMD as the preferred open-client mechanism, RFC 7591 DCR as a compatibility mechanism,
and preserve existing manually provisioned public and pinned-key confidential registrations.
Separate an OAuth application's identity from its tenant-specific approval and each user's consent.
Registration, HTTPS domain ownership, client authentication, tenant approval and consent are distinct.
None substitutes for another. An attacker can publish metadata; publication grants no Shale access.

The first PR adds only a pure CIMD identifier syntax value object and its tests, plus this plan.
It has no call site in the active OAuth flow, Spring bean, fetch, DNS lookup, database access,
registration endpoint, metadata advertisement, configuration change or feature switch. It deliberately
does not broaden any existing client-ID validator. End-to-end onboarding remains future work.

## Standards reviewed and revision policy

Primary sources fetched during this review:

| Source | Consequence |
| --- | --- |
| [MCP 2026-07-28 authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization), [registration](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/client-registration), [security](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/security-considerations) | Current published revision; registration priority is existing preregistration, CIMD if advertised, then DCR if advertised. CIMD SHOULD be supported; DCR is deprecated but MAY be supported for compatibility. Do not remove DCR merely because it is deprecated. |
| [CIMD draft-00](https://www.ietf.org/archive/id/draft-ietf-oauth-client-id-metadata-document-00.txt) | Revision explicitly referenced by this MCP specification. HTTPS URL identifier, exact document/client-ID matching, metadata redirects and trust policy. |
| [CIMD draft-03](https://www.ietf.org/archive/id/draft-ietf-oauth-client-id-metadata-document-03.txt), [live draft status](https://datatracker.ietf.org/doc/draft-ietf-oauth-client-id-metadata-document/) | Current IETF draft at review; not a final RFC. Adds/clarifies plural authentication-method metadata, no automatic HTTP redirects, metadata/key lifecycle and caching. Pin supported behavior and test both legacy singular and current plural fields. Do not silently equate draft-00 and draft-03. |
| [RFC 7591](https://www.rfc-editor.org/rfc/rfc7591), [RFC 7592](https://www.rfc-editor.org/rfc/rfc7592) | Standard DCR creation; registration management is separate and optional. Tenant Settings revocation is not a public DCR-management endpoint. |
| [RFC 9728](https://www.rfc-editor.org/rfc/rfc9728), [RFC 8414](https://www.rfc-editor.org/rfc/rfc8414), [RFC 8707](https://www.rfc-editor.org/rfc/rfc8707), [RFC 9207](https://www.rfc-editor.org/rfc/rfc9207) | Resource challenge/discovery, path-aware AS metadata, exact resource/audience, authorization-response issuer. |
| [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636), [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252), [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700), [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009) | S256, tightly constrained native loopback callbacks, OAuth security BCP, rotating refresh and revocation. OAuth 2.1 remains a draft. |

Compatibility profile: accept legacy singular `token_endpoint_auth_method` when plural metadata is
absent. For CIMD with neither field, use `none` under draft-03, never the RFC 7591 shared-secret default.
For plural metadata, validate a nonempty string array, reject forbidden secret-based methods, compute
the supported intersection and apply approved policy. Bind the permitted methods/key revision to the
transaction; a confidential-only declaration must never downgrade to public. Support `none` and reviewed
`private_key_jwt` only; no client secret, password, implicit, client-credentials or token-exchange grant.
Filter offered grant/response types to implemented code/refresh and `code`; require the code flow.
An extra offered grant is not permission to execute it. Unknown informational metadata is ignored,
bounded and never rendered as HTML; unsupported security-critical declarations fail closed.

Before advertising CIMD, test the legacy and plural profiles against independent clients. Before
advertising DCR, implement the actual RFC 7591 endpoint. The configured Spring endpoint path alone
does not implement DCR. Do not advertise capabilities before the full durable path exists.

## Observed blockers and retained strengths

Paths below are relative to the repository root. Historical roadmap sections describe earlier phases;
the latest source and dated Phase 4 records establish the following current behavior.

| Blocker/evidence | Required change |
| --- | --- |
| `oauth/OAuthMetadataContract.java` advertises neither `client_id_metadata_document_supported` nor `registration_endpoint`. `McpOAuthSecurityConfiguration.FoundationGate` denies `/oauth/mcp/register` even though AS settings name that path. | Add gated CIMD resolution and a real DCR creation endpoint, then advertise only operational mechanisms. |
| `TenantBoundOAuthClientRepository`, `AiOAuthTransactionStore.loadClient/consume`, `AiOAuthExchangeStore.resolve`, `OAuthCodeExchange.validateRequest`, `OAuthRevocation.validateRequest` and SQL client resolvers require opaque 1–64 character IDs. | Preserve legacy handling; add an exact external identifier binding consistently through authorization, code, token, refresh, revocation and assertions. Merely relaxing one regex cannot work safely. |
| `phase3b1.sql`: `AiIntegrationRegistration.ClientId` is globally unique `varchar(64)`; registration creates a random ID, belongs to one tenant, requires an administrator, and expires within 90 days. | Model one external application identity with independent tenant approvals; a single CIMD URL must work for tenant A and tenant B. Keep legacy rows/IDs unchanged. |
| `DurableOAuthAuthorization.findByClientId` loads only a pre-existing active tenant registration under authenticated WEB authority. No path creates a pending client/approval. | Authenticate the human first, then resolve/create only that tenant's pending request; never infer a tenant from client metadata. |
| Phase 4 browser route now exists in `OAuthBrowserFilter` and `OAuthConsent.tsx`, but requires approved `browser-origin`, matching web/server artifacts and hosting/CORS/header setup. | Reuse it; deployment acceptance is still outstanding. No separate password page or external-client WEB bearer entry. |
| No CIMD fetch/cache/key lifecycle or DCR abuse controls exist. Existing confidential clients use local administratively pinned RS256 keys only. | Add isolated bounded fetching and reviewed automatic public-key onboarding; retain the manual pinned path. |
| Existing administration is backend service/procedure driven (`AiIntegrationSecurityService`); no complete Settings application/approval/self-grant inventory exists. | Expose authenticated, tenant-confined Settings flows and missing bounded read projections. |
| Protected-resource metadata currently publishes resource/issuer only; the strict authorize flow rejects missing scopes. | Publish the existing two scopes in protected-resource discovery or appropriate 401 scope guidance before relying on clients to choose scopes automatically. Do not require users to configure scope strings. |
| `McpTransportConfiguration`/SDK accept only `2026-07-28`, modern discovery/per-request metadata; legacy initialize/session clients cannot connect. | Test actual application versions. If necessary, add a separately reviewed maintained transport/version adapter preserving all authority, budgets and disclosure behavior. Do not silently downgrade. |
| MCP rejects supplied Origins outside at most eight configured exact origins; no general MCP browser CORS. OAuth browser CORS allows only Shale UI. | Determine actual host request topology. Server-side clients need no new Origin. Browser-direct clients need a separate reviewed exact-origin/preflight design; never copy arbitrary registered redirect origins into CORS. |
| Global/tenant controls default OFF; native Azure, binding/attestation, artifact/ingress and operator/legal/tenant gates remain open in production readiness records. | Preserve those gates. Product onboarding work neither activates MCP nor closes operational acceptance. |

Existing strengths to retain: maintained Spring AS; WEB session/JTI/live-user validation; original
session-bound five-minute consent intents; explicit approval/denial; 60-second single-use hashed code;
S256 for every client; exact resource/issuer; opaque five-minute access credentials and rotating refresh;
live grant/registration/key/session checks; confined digest-to-owner bootstrap; audit-before-commit;
strict tenant RLS and independent Case confinement; durable extraction quotas and revocation.
Current browser grants have a one-hour deadline and credentials clip to live deadlines. Do not extend
any existing lifetime, registration expiry or consent just to hide reconnects.

## Target identity and persistence boundary

Proposed entities below are logical design names, not claims about installed tables or authorized SQL.

| Logical record | Contents and authority |
| --- | --- |
| OAuth application | Issuer + exact external client ID; source MANUAL/CIMD/DCR; immutable internal UUID; bounded validated metadata revision/digest; approved auth methods/public key version; disabled state. No tenant/user/grant credentials. DCR IDs are random, issuer-local, server generated; CIMD external IDs remain their exact HTTPS URLs. |
| Tenant application approval | Strict tenant owner + application UUID + internal existing integration reference; pending/approved/denied/revoked state, scope ceiling, authenticated approver, RowVer, expiry/revision. Unique tenant/application active binding; deny/revoke history retained. |
| Pending onboarding request | Authenticated requester/tenant/session, application+metadata revision, bounded requested scopes, expiry and consumed state. It is not a grant or token. Administrator approval cannot consume another user's consent. |
| Existing consent/grant/session/credential | Reuse current authority and live-revocation semantics; add a versioned exact application/wire-ID binding to code and credential lifecycle where needed. Never overwrite a grant's tenant. |

Keep existing `AiIntegrationRegistration` rows and internal opaque IDs. A separate exact external-ID
mapping can relate an application to each tenant's existing integration; this avoids forcing a long
CIMD URL into `varchar(64)` or moving existing foreign keys. The external wire ID never changes:
code/token/revocation requests and client assertions continue to use the standard CIMD URL or DCR ID.
Internal surrogate handles are storage keys only, never an invented registration protocol or a client
configuration requirement. Do not hash/truncate a URL and expose that hash as a replacement client ID.
Use binary exact identity semantics; if indexing by digest, confirm the full original value on every
match and collision. Require all application, tenant, grant and transaction joins, not the digest alone.

The current SQL procedures do not accept this mapping. Implement additive versioned adapters/modules
and test the complete lifecycle before wiring it in. Ordinary runtime receives no global SELECT or
registry-write powers; the bootstrap identity remains resolver-only. A dedicated, narrowly privileged
registry writer may create only non-tenant DCR metadata through bounded procedures. Its credentials
are operator-managed, never a tenant setup step. Tenant approval runs through tenant-initialized
connections, current `Users.is_admin`, strict FILTER and INSERT/UPDATE BLOCK, composite ownership
constraints and transactional audits. Pre-login registry reads cannot read tenants or users.

Inspect all source/module receipt, dependency and least-privilege closure checks before adding SQL.
Additive procedures/roles may require a separately reviewed verification-contract successor; do not
edit historical installers, refresh installed receipts, widen bootstrap, replay migrations, or weaken
Case confinement to make new modules pass. Preserve `AiCaseDataDeployment` and its immutable trigger,
issuer/resource/time-zone tuple and binding files byte-for-byte. Any need to change that binding is
outside this roadmap and blocks the affected implementation boundary. No SQL is included in PR 1.

## Shared-endpoint flow

1. User enters the one MCP URL. An unauthenticated request receives the existing RFC 9728 challenge
   pointing to `https://api.myshale.com/.well-known/oauth-protected-resource/mcp`. Discovery returns
   the shared resource and issuer `https://api.myshale.com/oauth/mcp`; the AS metadata URL is
   `https://api.myshale.com/.well-known/oauth-authorization-server/oauth/mcp`. These are target
   identities derived from the requested product URL, not an assertion of current production binding.
   If approved binding differs, stop deployment review; do not rewrite it.
2. A previously configured manual client keeps its current path. Otherwise the client supplies its
   CIMD URL if supported, or POSTs standard JSON metadata to advertised DCR and receives an ID.
   Registration is tenant-neutral and cannot enable AI or create approval, consent or access.
3. The client opens standard authorize with client ID, exact redirect, S256 challenge, state, scopes
   and resource. Shale performs cheap syntax/admission checks before network/SQL work. Invalid client
   metadata or redirects fail locally, never through an unvalidated callback.
4. Existing Shale WEB login authenticates the human. Server authority derives tenant/user/session
   from live session validation. Neither client ID, query/header, metadata, redirect, email domain nor
   client assertion supplies tenant authority. The authorize parameter allowlist continues rejecting
   `tenant_id`, `ShaleClientId`, `user_id`, `integration_id` and equivalents.
5. Under that tenant context, resolve the application approval. Missing AI opt-in or approval displays
   a Shale request/administrator flow. Only an active same-tenant admin can enable policy and approve
   a bounded scope ceiling. Normal users can request approval but cannot approve themselves as admin.
   A tenant policy may allow eligible users to consent to an already approved client; it never removes
   user consent. An administrator connecting as themselves still sees a separate explicit consent.
6. If approval is asynchronous, expire the original five-minute intent; show pending status in Shale
   and allow the user to restart connection from the AI application after approval. Do not extend a
   code/intent, invent a polling grant, or hold the third-party callback indefinitely. Approval is not
   consent. A fresh flow reruns live eligibility and metadata checks.
7. Consent displays application name as unverified presentation, metadata origin/identifier, actual
   callback hostname, requesting user/tenant, exact permission descriptions, data disclosure notice
   and current deadlines. Explicit Approve creates the existing grant/code transaction; Deny creates
   none. Preserve nonce/session/original-request binding, frame denial, no-referrer/no-store and WEB
   bearer confinement to Shale. Invalid metadata changes require restart, never silent substitution.
8. Code exchange resolves provisional owner from the code digest, then proves the complete
   application/wire-ID, issuer/resource, redirect, S256 and approved tenant/user/grant binding.
   Confidential assertions verify the external client ID as `iss`/`sub`, exact token-endpoint audience,
   pinned algorithm, bounded exp/iat/JTI and atomic replay protection. Never substitute an internal
   storage ID into a signed assertion. No caller tenant lookup is introduced.
9. Every MCP request uses the existing verified immutable integration context and live checks, quotas,
   shared secured Case operations, fail-closed audit and final disclosure checks. Same application
   in two tenants produces distinct grants and token families. Refresh/revocation use credential
   digest-derived owner, not a client-chosen tenant; unknown revocations remain non-oracular.

## CIMD fetching and metadata controls

- PR 1 `CimdClientIdentifier` enforces HTTPS, non-root path, no userinfo/fragment/dot segments,
  valid port and a 2048-character ASCII URI bound, retaining the exact string. No query is an explicit
  Shale profile restriction consistent with draft-03's SHOULD NOT. It is syntax only: private IP and
  nonexistent-host strings can pass syntax and MUST fail or be confined by the future fetcher.
- Future fetches use a dedicated client/egress boundary with no cookies, Authorization, Shale bearer,
  proxy credentials to origin, inherited URL credentials or tenant headers. Request JSON with TLS
  certificate/hostname validation; accept 200 JSON only; never follow redirects. Revalidate all JWKS
  URLs independently, including destinations on a different origin when policy permits.
- Deny loopback, private, link-local, multicast, unspecified, reserved and cloud-metadata destinations
  for metadata/JWKS. Resolve all A/AAAA, reject unsafe/mixed answers, pin the selected validated address
  for the connection while preserving TLS hostname/SNI; validate every new connection/retry. Cover
  IPv4-mapped IPv6, unusual IP notation and DNS rebinding. If an outbound proxy resolves DNS, enforce
  the same policy at that proxy; application DNS prechecks alone do not prove safety. Redirect URIs
  are never fetched. Native callback exceptions do not apply to metadata fetching.
- Initial proposed bounds: 32 KiB decompressed metadata/JWKS each, JSON depth 8, 5 redirects in
  metadata (matching the current registry maximum), 2 live signing keys, name 100 characters,
  2-second connect and 5-second total fetch deadline, 16 concurrent fetches per replica. Reject
  duplicate JSON keys/type confusion, invalid UTF-8, control/bidi display characters, private JWK
  parameters and incompatible algorithms. Bound arrays/strings and actual streaming/decompressed
  bytes, not only Content-Length. Unsupported security-critical fields cannot become policy.
- Cache by exact issuer/client URL and validated revision with bounded LRU/coalescing, short negative
  caching and conditional revalidation. Honor no-store/no-cache and HTTP freshness, with a maximum
  five-minute positive freshness; never extend origin freshness. No stale-on-error for new authority.
  `304` can refresh only an existing validated representation. Pin metadata/redirect/auth-method/key
  revisions to authorization. Removed callbacks/keys and security-critical metadata changes invalidate
  pending flows and require review/reconsent as applicable; additions never enlarge existing grants.
  A failed revalidation denies new authorization and refresh. Existing short-lived credentials remain
  subject to explicit live revocation and clipped deadlines; document the bounded cache detection delay.
- `jwks`/`jwks_uri` are mutually exclusive. CIMD confidential onboarding needs safe key discovery,
  kid/algorithm binding, rotation overlap limits and replay storage before accepting remote JWKS.
  Do not enable a generic JWT decoder's jku/x5u/key fallback. Manual pinned-key behavior remains.
- Render escaped plain text; show the real metadata/callback host prominently. A familiar name/logo
  is not verification. Do not fetch/display remote logos initially or navigate informative client links
  automatically. Do not require client_uri to share client-ID/redirect origin; draft-03 prohibits that
  identity assumption. Reputation/domain review can add scrutiny, never replace consent.

## RFC 7591 DCR boundary and abuse controls

Expose `POST /oauth/mcp/register` only when its reviewed capability is explicitly enabled. Accept
bounded `application/json`; emit standard 201 metadata with generated client_id, registration time,
accepted redirects/grants/response types and explicit `token_endpoint_auth_method`. Initial automatic
DCR profile is public `none` with code/S256 (optional refresh), no client secret. Reject an explicit
unsupported authentication method with `invalid_client_metadata`; do not quietly change a request
for confidential authentication into public. Follow RFC 7591 defaulting rules when fields are absent:
its default shared-secret method is unsupported here, so reject that combination rather than silently
treating an omitted method as `none`. Handle OIDC `application_type` deliberately; Shale's OAuth-only
endpoint must not accidentally apply OIDC web defaults to native callbacks.

Use standard `invalid_redirect_uri` / `invalid_client_metadata`, 413 for actual oversized input and
429 with Retry-After for admission. Ignore unknown extension fields as RFC 7591 requires, within
body limits; do not turn arbitrary fields into tenant authority or rendered content. No public RFC
7592 update/delete, registration access token or initial access token is required for this profile.
Requiring an out-of-band initial token would violate the product requirement. Confidential DCR can
be a later standard extension with reviewed public-key proof; manual confidential clients remain.

Proposed distributed admission defaults for validation/tuning: 10 creation requests/minute/source
address, 100/minute globally, 5,000 unapproved records globally, 24-hour expiry for never-used pending
DCR records. Also budget by metadata host/client fingerprint and authenticated tenant/user when known;
IP alone is neither identity nor sufficient protection. Enforce trusted proxy address handling, indexes,
race-safe storage caps, cheap prefetch rejection, circuit breakers and bounded security telemetry.
Do not require CAPTCHA or proprietary challenge exchanges at the machine registration endpoint.
Use browser risk review/admin approval where needed. Reject new requests when caps are exhausted;
do not evict active registrations or disrupt ordinary login. NAT fairness/capacity require measurements.

A new DCR ID never grants fresh extraction budget: existing durable tenant/user/integration quotas
must aggregate across application registrations and new processes. A malicious user cannot escape
tenant/user limits by registering repeatedly. Do not auto-expire an actively approved OAuth application
identity; distinguish pending registry retention from existing tenant approval/grant/token expiry.
Keep current 90-day tenant registration limits until a separately reviewed renewal policy; renewal must
be an ordinary authorized browser/admin flow and must not resurrect revoked grants or extend consent.

Redirect validation retains current strict exact HTTPS strings and literal IP loopback host/path with
only a public native port exception. Reject wildcard, userinfo, fragments, unsafe schemes, malformed
encoding, dot-path aliases, private-network hosted callbacks and redirect substitution. CIMD does not
prove that a public client with a loopback redirect controls the vendor identity; warn on local callbacks.
Current policy rejects query/encoded-path callbacks and `http://localhost:PORT/callback`; real clients
may require them. Support for localhost requires its own consistent authorization/SQL/exchange/UI
policy change, loopback-only meaning and tests (never suffix matches such as localhost.evil). Support
for valid fixed query/encoded callbacks, if needed, must retain exact comparison and the original
redeemed redirect; do not broaden by normalization. These are compatibility gates, not a reason to
weaken existing redirect validation inside PR 1.

## Shale Settings management

Reuse Settings navigation, session-generation request guards, server service ports, RowVer and the
existing administration/audit conventions. Proposed section: Settings > Integrations > MCP clients.

| Viewer/action | Required behavior |
| --- | --- |
| Signed-in member: My authorized clients | Own grants/families only: display name, exact client identity/source, approved scopes, tenant, created/expiry, active/revoked state and bounded last-use if authoritative data exists. No inferred live presence, token/key secrets, other users or tenant IDs in requests. |
| Member: revoke my access | Revalidate current WEB session and ownership; revoke own grant and all associated credential families durably; confirm effect and retain history. Lost response reloads state, never implies success. Reconnecting requires fresh consent. |
| Tenant administrator: requests and clients | Tenant-confined pending requests, application identity/metadata provenance, requester, scope ceiling, expiry and state. Approve/deny using current is_admin and RowVer. Approval cannot create another user's consent or token. |
| Administrator: manage access | Enable/disable tenant policy; reduce ceiling, revoke an application approval/all tenant families or a selected same-tenant grant. Warn on impact, require explicit confirmation, handle stale versions. A client-wide tenant revocation cannot affect another tenant. |
| Advanced manual client | Keep existing provisioning, exact callbacks, public/pinned-key confidential selection, scoped key rotation/revoke and expiry controls. No auto-conversion of existing clients to CIMD/DCR. Secrets/private keys never redisplayed. |
| Tenant security review | Bounded, paginated, authorized lifecycle/disclosure history; no PHI payloads, raw search terms, callback queries, state, codes, assertions, token/digest or arbitrary metadata URLs in audit output. |

Necessary proposed REST surfaces are authenticated own-grant list/revoke and tenant-admin request,
approval/denial, registration list/revoke/ceiling APIs. Extend existing service methods where possible;
do not route the UI directly to SQL or globally searchable client inventory. Every read derives its
tenant from server principal. Every mutation has transactional authority and audit, scoped response
allowlists and bounded pagination. Add protected review/deep-link routes; never make arbitrary client
callback URLs into Shale safe-return destinations.

## Audit compatibility

Existing `EntityActionAuditLog` entities `TENANT_AI_POLICY`, `AI_INTEGRATION`, `AI_CLIENT_KEY`,
`AI_DELEGATION_GRANT`, `AI_DELEGATED_SESSION` and current integration disclosure/security audit remain
the owners for policy, integration/ceiling, keys, consent/grants, families, reuse and tool disclosure.
Their DAO/procedure transaction writes audit before commit and rolls back on failure. Preserve tenant,
actor, entity/parent/case and correlation attribution and allowlisted sanitized state metadata.

Pending request/approval/denial and external-application revision need explicit new event representation:
review existing allowlists and add a scoped additive audit extension if they cannot represent these
actions. Do not falsely log anonymous DCR creation as a tenant administrator mutation. Pre-auth DCR
and fetch abuse use bounded operational security records with retention/access policy, no fabricated
tenant actor, no PHI, no raw URLs/bodies. Authenticated tenant binding creation/approval is audited in
the same transaction as that change. Global registry disablement needs the existing governed operator
audit model or an explicitly reviewed extension, never a new tenant bypass.

Listing own consent metadata has no Case/PHI read and needs no per-row PHI event. Tenant-wide grant,
requester and lifecycle views are sensitive administrative reads: review the established administrative
read-audit seam and add a closed discriminator if needed, fail closed on required audit. No event for
tab clicks or pure syntax validation. PR 1 uses no audit schema and changes no audit seam.

## Interoperability evidence

No third-party application was run end-to-end against Shale in this review. Published documentation,
public metadata/source inspection and existing synthetic SDK tests are different evidence levels.
The Phase 4 browser's mocked-API UI test plus separate real SQL lifecycle tests do not constitute a
combined URL-only client test. Existing SDK fixtures use provisioned credentials and are insufficient.

| Client | Evidence inspected | Status / exact remaining checks |
| --- | --- | --- |
| ChatGPT | [Official authentication guide](https://developers.openai.com/apps-sdk/build/auth/) documents CIMD, DCR, predefined clients, S256, none/private_key_jwt and issuer checks. Public [client document](https://chatgpt.com/oauth/client.json) fetched successfully: exact URL client ID, HTTPS callback, plural none/private_key_jwt plus singular private_key_jwt, RS256 and remote JWKS. | Published/source configuration verified; Shale connection UNVERIFIED. Test stable and callback-specific ID modes, plural method negotiation, JWKS rotation, path-scoped issuer/resource, scopes, current transport, consent, refresh and revoke. Do not reject its entire metadata because it offers an extra unimplemented grant; never enable that grant. Workspace plan/admin/app-creation permissions are normal client-side prerequisites. |
| Claude Code | [Official MCP guide](https://code.claude.com/docs/en/mcp) documents DCR and automatically discovered CIMD, browser OAuth, random callback ports and `http://localhost:PORT/callback`. | Documented support; Shale connection UNVERIFIED. Existing literal-IP-only redirect policy can reject its localhost callback. Pin the actual release and test native callback, resource/issuer/PKCE/scopes and MCP revision. No --client-id in acceptance. |
| Claude web/Desktop connectors | [Claude platform remote-MCP documentation](https://platform.claude.com/docs/en/agents-and-tools/remote-mcp-servers) confirms remote services but is not a proof of claude.ai/Desktop registration behavior. | UNKNOWN per product/build/workspace. Do not infer from Claude Code or Anthropic API connector, which can receive externally supplied tokens. Record actual registration/auth/transport support and any workspace administrator prerequisite. |
| Hermes (assumed NousResearch/hermes-agent) | Inspected public main `74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5`: [OAuth manager](https://github.com/NousResearch/hermes-agent/blob/74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5/tools/mcp_oauth_manager.py), [provider](https://github.com/NousResearch/hermes-agent/blob/74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5/tools/mcp_oauth_provider.py), [transport](https://github.com/NousResearch/hermes-agent/blob/74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5/tools/mcp_tool.py). Uses SDK OAuthClientProvider, discovery, cached DCR clients and CIMD-aware rejection handling. | Source indications only; installed Hermes version/SDK, automatic CIMD identifier availability and Shale E2E remain UNKNOWN. Pin source/package and SDK; run stock URL setup plus interactive login. Test protocol revision, callback host, exact resource/state/iss, registration metadata and refresh. Existing source compatibility fallbacks are not a reason to weaken Shale issuer validation. If another Hermes product was intended, no compatibility claim transfers. |
| Other MCP clients / Inspector / independent SDKs | Existing Shale pinned TypeScript client 2.4.0 and Python fixtures plus `McpSdkCompatibilityTest` establish their recorded synthetic scope only. | Classify CIMD-capable, DCR-capable, manual-only and unsupported transport separately. Run a fresh maintained stock client with automatic registration enabled; a script with a supplied ID is not URL-only evidence. |

Record exact application/SDK build, OS, date, transport revision, registration mechanism, discovery,
callback form, auth method, resource/issuer/scopes, signed-out flow, denial, refresh, revocation and
sanitized outcome for every claimed supported client. A manual-only client can remain supported
through advanced provisioning, but cannot pass the URL-only product test. A legacy protocol client
cannot pass merely because its OAuth succeeds. Never patch vendor source or require its proprietary
registration path when CIMD/DCR applies.

## Phased PR boundaries and acceptance

All increments stay default OFF. Each PR includes its own pre-edit test-impact inventory, focused
tests, selector affected suite and local critical reactor. SQL fixtures are disposable, nonproduction,
TLS-validated and independently permissioned. No production configuration/SQL/credential/binding
change or activation is authorized by this plan.

| PR | Scope | Acceptance / dependency |
| --- | --- | --- |
| 1 — this PR | Exact bounded CIMD identifier value object and security/implementation plan. | New syntax tests pass; manual mappings, browser, metadata, exchange/revocation regressions pass; no references in active flow, no advertised capability or SQL/binding diff. This is a foundation, not onboarding completion. |
| 2 | Isolated metadata/JWKS fetcher, parser, cache and method-selection policy, initially unwired. | Real local HTTPS/controlled DNS tests for SSRF, rebinding, proxy resolution, redirects, duplicate JSON, decompression/stream limits, cache expiry/304/no-store, timeout and key changes. Legacy/plural method matrix; no secret/private-key acceptance. |
| 3 | Additive application registry and tenant-approval model/adapter, audited lifecycle and verification successor if needed. | Synthetic real SQL: two tenants share one external CIMD ID with independent grants; all tenant tables FILTER/BLOCK and immutable owners; least-privilege/receipt closure; failed audit rollback; deny/revoke/race/RowVer/history; legacy rows untouched. Review exact migration/verification design before implementation. |
| 4 | Connect CIMD identity to maintained authorize/code/exchange/refresh/revoke; gated metadata advertisement. | Entire wire-ID binding, safe unknown-client failure, original WEB session, S256/resource/issuer, code replay, assertion replay/key lifecycle, no downgrade, expired/revoked states and cross-replica restart. Requires PRs 2–3. No client ID mapping exposed as a workaround. |
| 5 | Standard public DCR endpoint, separate admission/storage quotas and truthful gated discovery. | RFC 7591 defaults/errors/201; none+S256 clients can register before login without tenant; no tenant authority from fields; distributed abuse caps and expiration preserve approved clients; missing/unsupported auth method handled as specified. Requires registry; no forced dependency on CIMD at the client. |
| 6a | Authenticated own-grant and tenant-admin management APIs/read audit. | Own/foreign access tests, admin removal mid-request, bounded inventories, transactional revoke/deny/ceiling/policy, RowVer/response-loss and audit faults; no global tenant inventory. |
| 6b | Settings UI and pending/admin approval plus explicit consent presentation. | Actual browser + backend integration, keyboard/mobile, safe origin/return behavior, malicious names/links, expired approval restart, no automatic consent; legacy provisioning stays usable. Requires 6a and identity binding. |
| 7 | Stock-client compatibility and fully combined URL-only synthetic acceptance. | Matrix above with at least one stock CIMD and one stock DCR path where available; no supplied client ID; login/admin/consent/tools/refresh/revoke and two-tenant isolation. Transport/localhost/CORS compatibility changes, if necessary, are separate narrowly reviewed PRs before claiming that client passes. |
| 8 | Production readiness proposal only after synthetic acceptance. | Retain all existing native Azure/V2/identity/binding/ingress/artifact/operations/legal/operator/tenant gates. Produce a concrete release/rollback proposal; obtain separately required approval before any production action. No activation in this task. |

Suggested next implementation is PR 2, followed by the reviewed registry/tenant design. Do not start
by enabling Spring's registration endpoint or loosening the 64-character regex: neither establishes
tenant isolation or the complete client binding. PR 1 is independently safe to review/merge.

## Required end-to-end synthetic acceptance

Use a disposable deployment of actual Shale server, built web UI, runtime/bootstrap/registry identities
and SQL with strict RLS/audits, plus TLS-trusted synthetic ingress and controlled public metadata host.
Use two synthetic tenants A/B, an A member, an A admin and a B user/admin, ordinary existing WEB
accounts and synthetic Cases. Fixture account/feature setup is allowed test infrastructure; no
precreated OAuth client/tenant integration/grant is allowed in the URL-only paths. Never point this
fixture at production, disable TLS validation, or use the production binding/credentials. A dedicated
synthetic immutable binding may be installed once as fixture setup under its own approved contract.

1. Start a fresh stock client profile with empty credentials, registration and token cache. Supply
   only the synthetic deployment's single `/mcp` URL. Configure no client_id, secret, Shale bearer,
   tenant, callback override, discovery override or scopes. The client's own hosted CIMD URL is
   permitted automatic client behavior; the test must not inject it through a manual ID setting.
2. Observe sanitized challenge -> protected-resource -> AS discovery. In the CIMD run the application
   automatically supplies its metadata URL, Shale validates/fetches it, and no DCR POST is required.
   In a separate stock DCR-capable-client run it POSTs registration, receives/reuses its own ID, then
   authorizes. Test the real mechanisms rather than mocking registration or token responses.
3. Browser opens real Shale login. Sign in as the A member. Attempted caller tenant B parameters
   fail locally. Member requests client approval; no code/grant/access exists yet. A admin signs in
   normally, approves only that tenant's ceiling/opt-in. If the original flow expired, restart from
   the client with the same URL; no pasted technical values. Reject admin approval from B.
4. A member sees authoritative client/callback/user/tenant/scopes/deadlines. Verify no grant on load,
   sign-in or administrator approval alone. Explicitly Approve. The client validates state/iss and
   exchanges its single-use code using its own verifier/resource and selected client auth method.
5. Stock MCP discovery/list/call returns only authorized tools and A data. Record successful
   `search_cases` and authorized `get_case_overview`; B entity IDs yield no foreign data/existence
   leakage. Validate live policy, quotas and committed audit/resource/final-disclosure attribution.
6. Complete real refresh rotation (wait through short token expiry or controlled fixture clock);
   reject wrong verifier/client/redirect/resource, code replay, refresh replay and unsupported method.
   Reused refresh revokes its family. No ordinary WEB bearer works on MCP or reaches callbacks.
7. Connect B using the same endpoint and, for CIMD, the exact same external client ID. Obtain B's
   independent administrator approval/consent without technical setup. Check distinct grants,
   families and RLS contexts across pooled requests and a server restart/replacement replica.
8. In A Settings, revoke own grant and reject the old access and refresh on the next admission;
   reconnect only after fresh consent. Revoke A tenant application and test all A users/families
   denied while B stays valid. Tenant disable/re-enable cannot resurrect prior revoked authority.
9. Negative campaigns: denial/timeout, stale/replaced WEB session, hostile metadata/SSRF/redirect,
   key or callback change between consent/exchange, registration flood, concurrent approve/revoke,
   injected audit failure and lost response. No token on failed consent/audit; no data without its
   committed audit; denied requests cannot consume unbounded resources or escape extraction limits.
10. Retain only sanitized step outcomes, build/SDK/revision/mechanism, audit IDs/counts, server-side
    correlation and fixture teardown results. Never raw HAR, authorization headers, callback/code
    URLs, client state, verifiers, token bodies or credentials. Automate with Playwright controlling
    the real Shale UI plus stock-client orchestration; do not patch the third-party client's source.

Pass means the user supplied only URL + normal credentials/browser/admin decisions and used tools.
Separate CIMD/DCR passes, manual-client regression, two-tenant isolation and revocation evidence are
required. This scenario is specified here, not executed by PR 1. Production acceptance remains open.
