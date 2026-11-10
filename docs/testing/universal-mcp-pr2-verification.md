# Universal onboarding PR 2: isolated CIMD retrieval

Baseline: `96b7a4f645b40ead0d9070f12226f7f779e1f710`, merged #1945 on `codex/latest`.
Boundary: [universal onboarding plan, PR 2](../architecture/universal-mcp-tenant-onboarding.md#phased-pr-boundaries-and-acceptance).

## Test impact and architecture review

Before production edits, reviewed repository rules, system overview, tenancy/RLS, the AI/MCP
identity/delegation/OAuth architecture, onboarding plan and PR 1 verification, Phase 4 browser
security runbook and local test-selection guidance. Searched all OAuth code/tests for metadata,
redirects, client mappings, RS256 and JWK consumers. Inspected `CimdClientIdentifier/Test`,
`OAuthRedirectPolicy`, `OAuthClientMappingTest`, `OAuthMetadataTest`, `OAuthCodeExchangePolicyTest`,
`OAuthRevocationPolicyTest`, `PinnedClientAssertion/Test` and the browser policy tests.
Existing assertions are still authoritative; no existing constructor, validator or expectation changes.

| Production file | Tests and regression prevented |
| --- | --- |
| `CimdDestination` | Address/syntax matrix and controlled DNS HTTPS: private/reserved/mapped/transition addresses, mixed answers, aliases, rebinding and hosted callback safety. |
| `CimdHttps` | Real trusted HTTPS: pinned dialing, TLS trust/hostname rejection, ambient proxy isolation, redirect rejection, wire/decompressed/chunk/header limits and total/slow-drip deadlines. |
| `CimdMetadata` | Strict UTF-8/JSON, duplicate/type/depth/string/array limits, exact published ID, public/legacy/plural/private profiles, callbacks and public RSA key restrictions. |
| `CimdMetadataService` | Expiry/304/no-store/no-cache/Age, monotonic expiry, concurrent coalescing, sixteen-worker admission, failed DNS, negative caching, bounded eviction and independent JWKS rotation/rejection. |

Focused regressions retain manual client mapping, exact callbacks, S256, resource/issuer,
assertion rules, exchange/revocation and browser behavior. Selector server-area checks cover
OAuth configuration, ordinary login and default-OFF composition. Critical reactor covers module
and existing data/service behavior. No full-suite escalation, UI, real SQL or client E2E is implied.

## Security design

The new `oauth.cimd` package has one public service and immutable, redacted publication snapshots.
It has no Spring annotation, endpoint, configuration property, live caller or database dependency.
An instance is scoped to one exact issuer; the exact CIMD string is the LRU/coalescing key. Callers
must own/close a single shared instance per issuer, never allocate one per anonymous request.
A snapshot does not authenticate a caller or establish tenant approval, consent, registration,
assertion replay state, token authority or permission to read data.

The production transport creates a direct `Socket(Proxy.NO_PROXY)` to a validated numeric address.
It never invokes URLConnection, ProxySelector, system HTTP proxy resolution, cookies or HTTP auth.
Every fetch resolves all returned A/AAAA (at most sixteen), rejects any unsafe/mixed answer, then
pins one address without a second hostname lookup. TLS still uses the original hostname for SNI
and HTTPS endpoint identification against the JVM trust store. There is no TLS bypass, redirect,
retry, connection pooling or fallback. Cross-origin JWKS repeats the same checks. HTTPS callback
hosts are checked independently but never fetched; only the existing public native literal-loopback
exception survives. No localhost, encoded/query callback or legacy five-callback rule is broadened.

The explicit public-DNS-only URL profile rejects literals, alternative IP notation, single labels,
trailing dots and special internal/special-use namespaces. IP policy rejects private, loopback,
link-local, CGNAT, multicast, unspecified, reserved, documentation and transition allocations.
IPv6 is restricted to ordinary global unicast, excluding the special 2001::/23 range and 6to4.
This deliberately conservative subset may reject some legitimately public special-purpose hosts.
Exact encoded metadata paths are retained on the wire; decoded dot segments remain forbidden.

GET requests send only Host, Accept, Accept-Encoding, Connection and an optional validated ETag.
Responses must be 200 JSON (or solicited 304 against retained validated bytes). Limits are 32 KiB
encoded **and** expanded, 8 KiB cumulative headers/framing, depth eight, strings 2048, field names
100, arrays 32 and objects 64. Metadata caps callbacks at sixteen and display name at 100;
controls/bidi formatting are rejected. Unknown bounded information is ignored, never rendered or
fetched. Unsupported security declarations fail closed. Private/secret material is rejected.

Legacy singular and current plural auth methods are supported. Absent methods mean `none` only
for CIMD; explicit secret methods fail. The supported intersection is `none`/`private_key_jwt`;
a confidential-only document cannot become public. Explicit selection never falls back. Code is
required; offered extra grants/response types do not enable them. RS256 is the only key profile:
one or two unique bounded kids, public RSA 2048–4096 bits, signature/verify use, bounded odd exponent;
no private parameters, certificates, symmetric keys or jku/x5u fallback. Inline/remote JWKS are
mutually exclusive. Mixed methods with native callbacks are conservatively rejected because the
existing confidential callback policy prohibits them. Manual pinned keys remain separate.

Two-second connect and five-second total retrieval limits cover metadata plus JWKS and callback
checks. An absolute socket-close timer covers slow TLS/read/write as well as per-read timeouts.
Sixteen unqueued daemon workers and sixteen in-flight identities bound DNS and fetch concurrency;
same-ID requests coalesce. Native DNS calls may ignore interruption: a stuck resolver can occupy
at most sixteen workers and cause fail-closed admission until recovery. No unbounded DNS executor,
retry or queued work is created. The caller still stops waiting after five seconds.

A shared 256-entry LRU bounds positive/negative metadata and key representations; each entry holds
at most two 32 KiB documents plus bounded headers/snapshot. Five-second negatives suppress failed
lookups. HTTP freshness subtracts Age/apparent age and rounded-up response delay; maximum positive
freshness is five minutes, default zero when no freshness is supplied. Metadata/JWKS use the earlier
expiry. Monotonic deadlines prevent wall-clock rollback extending cached authority. ETag revalidation
retains bytes only after validation; no-cache always revalidates, no-store retains nothing, including
on 304. Failed revalidation removes the old representation and denies rather than serving stale data.
A revision digest covers exact metadata bytes plus public key material/kids, so key and callback
changes are visible. Cache hits deliberately permit a bounded detection delay of at most five minutes.

## Synthetic fixture and limits of evidence

Tests generate an ephemeral PKCS12 certificate using JDK keytool, trust that certificate explicitly,
and perform actual TLS handshakes and HTTP streams against an ephemeral local TLS listener. Test-only,
package-private resolver/dial seams route a validated synthetic public IP to that listener while
recording the selected numeric address. Production has no routing override or safety switch.
Wrong hostname and untrusted chains fail before HTTP. This exercises controlled DNS policy and real
TLS transport together; it is not a public-Internet routing or deployed-egress acceptance test.

A read-only inspection of ChatGPT's public client document reconfirmed the singular/plural fields,
RS256 and remote JWKS profile; no stock client was modified or run against Shale. Automated tests
are wholly synthetic and do not depend on that external document.

## Audit compatibility and next dependency

Retrieval reads attacker-published public metadata only, with no PHI, tenant identity or domain/admin
mutation. It creates no entity-action/PHI audit, schema or migration. No raw URL/body, key material,
exception chain or attacker content is logged; errors and snapshot diagnostics are fixed/redacted.
Operational abuse telemetry and distributed host/source admission remain future runtime integration
work with governed retention; no fabricated tenant actor or ad-hoc database audit is added here.
Existing transactional registration, grant, credential and disclosure audits remain unchanged.

Next is PR 3: separately reviewed additive application registry and tenant-approval model/adapter,
transactional audit representation, least-privilege/verification closure and real synthetic two-tenant
SQL acceptance. PR 4 must combine that authority with this fetcher, pin exact issuer/client/redirect/
auth/key revisions into transactions, invalidate changed pending flows, preserve assertion replay,
S256/resource/issuer and refresh/revocation controls, and close runtime egress/admission/telemetry
acceptance before advertising support. No generic JWT decoder is connected to remote keys here.

No production SQL, Azure, deployment, activation, endpoint, capability advertisement, binding,
tenant authorization, manual client, third-party client, dependency or workflow change is included.

## Verification results

Pending final focused, selector affected and critical runs. Commands/results will be recorded before PR submission.
