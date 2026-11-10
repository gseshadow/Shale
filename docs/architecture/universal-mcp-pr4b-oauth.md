# PR 4B: confined CIMD OAuth lifecycle candidate

Stack: #1949 -> #1950 -> this draft. CIMD remains OFF and unadvertised.
This candidate adds no production pool, identity membership, activation or deployment.

## Pre-edit review and test impact

Reviewed `codex-prompt-rules`, database schema, tenancy/RLS, system overview, web API
migration/readiness, the universal onboarding plan, PR 3 registry review, PR 4 security
dependency and PR 4A proof design. Inspected the actual legacy authorization, exchange,
credential, revocation, browser, assertion and CIMD retrieval implementations and tests.

| Contract | Existing tests inspected | Required successor evidence |
| --- | --- | --- |
| Original WEB, explicit consent, exact external ID/S256/callback | OAuthAuthorizationFlowTest, OAuthBrowserFilterTest, ShaleOAuthHumanTest | Confined new transaction namespace; no legacy registration activation |
| Single-use exchange, rotation, revocation | AiOAuthExchangeContractTest, AiOAuthCredentialContractTest, AiOAuthRevocationContractTest | Real two-tenant JDBC, replay and failed-audit rollback |
| Publication/approval and proof | AiCimdProofLocalSqlServerTest and V1, registry/confinement tests | Existing PR 4A family/credential namespace; current revision/approval checked at each mutation |
| Private-key method/key binding | PinnedClientAssertionTest, CimdMetadataTest/CimdRetrievalTest | Exact external iss/sub, remote kid mapping, selected method with no downgrade |
| Historical receipts and manual clients | Original V1/V2 verifiers, registry supplement, PR 4A verifier | No historical source/receipt/binding writes; independent new seal |

## Boundary and audit review

A dedicated metadata publisher supplies only PR 2 validated public snapshots matching an
already governed application publication. It cannot publish a new registry revision,
enable an application, approve a tenant or issue credentials. Revalidation of identical
bytes renews only bounded freshness. Changed metadata requires a new registry publication
and fresh tenant approval. The accepted detection delay is at most five minutes.

New browser and issuer identities must remain separate from legacy runtime/bootstrap and
Case identities. All new procedures are same-owner static chains; only digest-owner
resolution executes as owner, with NULL caller context. Tenant procedures require fresh
leases and derive actor/tenant from initialized authenticated Shale authority. Session
context is not independent SQL authentication. Production composition requires a new
full identity/catalog attestor and dedicated pools; the old attestor cannot authorize them.

Grant creation, family issuance, rotation and family revocation use existing
EntityActionAuditLog entities on their business SQL transaction. Audit failure rolls back
code consumption, JTI reservation, credentials and family state. Public metadata cache
refresh and pending consent without a grant carry no PHI or domain authority and add no
audit event. No URL, callback, state, key, assertion, digest or credential enters audit
metadata. No historical audit vocabulary or receipt is modified.

Implementation and exact executed verification are recorded below before handoff.

## Implemented candidate

The additive installer creates seven tables, twenty-five modules and five initially empty
roles. The metadata publisher resolves an unchanged, exact digest/method publication and
stores bounded validated public callbacks/keys/freshness. It never advances a registry
revision. Browser, issuer, resolver and neutral-revocation roles are separate. No existing
role receives an EXECUTE. New tenant tables retain the exact existing FILTER and both
BLOCK predicates. Only the three digest-to-owner resolvers execute as owner.

The browser namespace retains the original WEB session, nonce, external identity,
application revision/digest, approval identity/RowVer, internal dormant anchor, selected
method/key, callback, requested scopes and explicit decision. Spring consent nonces retain
the existing bounded base64url/padding contract; credentials remain 256-bit unpadded
CSPRNG values. SQL stores code/credential/JTI digests. S256 and callback checks use the
existing policies, including public numeric loopback callbacks; exchange binds the actual
callback exactly. The maintained browser adapter now accepts a persistence contract;
manual persistence remains its installed implementation. CIMD projection preserves the
full wire identity and SQL-clipped consent deadline.

Redeem owns one SQL transaction: current authority, exact code/client/issuer/resource/
callback/S256, client method/key, JTI reservation, consume, original grant, session,
immutable PR 4A family receipt, initial ACCESS/REFRESH rows and audits. The session retains
its internal client ID and NULL legacy transaction. No registration is activated and no
new credential enters a legacy table. Refresh rechecks current application/approval,
ceiling, policy, user, WEB, grant, session and key; rotates generation atomically; and
commits whole-family revocation before returning replay denial. Access expires within
five minutes; refresh idle authority within eight hours; all authority is clipped by the
original consent/session and current approval/WEB/key deadlines. Nothing extends consent.

RS256 assertions retain the full external `iss`/`sub` and exact endpoint audience. The
remote `kid` maps to an approved publication and tenant-local pinned key. The supported
SQL key profile uses exponent 65537. Confidential-only publication approvals produce a
confidential anchor; mixed-method publications currently produce a public anchor. A
request requiring private-key authentication cannot downgrade that selection. Every JTI
also reserves a global application/kid/digest key, across tenants, replicas, endpoint
audiences and publication revisions. Expired receipts are cleaned in bounded batches;
live global reservations have an application capacity bound. Tenant-local replay history
remains separately RLS-confined.

Known-token revocation is digest-owner-bound and audits session/family invalidation.
Unknown-token revocation authenticates current public metadata and reserves assertions in
the same global replay transaction without choosing a tenant. Its empty success grants
no domain authority. This short-lived replay ledger is a security receipt, like the
existing assertion ledger, not a fabricated tenant audit event. All meaningful grant,
key and session mutations retain the existing audit schema/vocabulary.

The new independent receipt records new source/catalog facts and an immutable before-hash
of all historical Shale receipts, the binding and PR 4A receipt. Binary hashes are rendered
as hexadecimal before XML serialization. The unchanged cumulative, registry supplement
and PR 4A verifiers run before/after installation and before its final commit. New checks
cover exact module bytes/context, explicit role permissions, RLS, catalog fingerprints,
member/inherited authority, signatures, ownership and SQL-platform login facts. Installation
is one-shot, unscoped dbo, schema-locked and global capability OFF; the new control is OFF.
No historical receipt or binding is resealed.

## Runtime integration boundary and remaining gaps

`CimdOAuthLifecycle` and `AiCimdOAuthStore` are explicit, unregistered composition seams.
There is no new Spring bean, property, endpoint, advertisement, login, membership or pool.
The existing deployed/manual security chain continues using its original store and
providers. The new browser/issuer/resolver/metadata/neutral factories must not reuse that
runtime or bootstrap identity. A complete attestor must include both historical and new
verifiers, effective object/column permissions, endpoint identity separation, cross-database
probes and native Azure acceptance. A restricted catalog execution contract remains open.
That prerequisite is isolated here; no Java eligibility fallback or broadened SQL role is
provided to bypass it.

The next phase must install separately governed, dedicated pools and the new full attestor,
route the maintained AS providers between manual and CIMD namespaces without translation,
and execute an actual WEB/browser + SQL + HTTP lifecycle through a disposable deployment.
The current browser tests are adapter contracts, not Chromium or stock-client acceptance.
The new metadata/lifecycle composition also needs that real HTTPS/SQL combined test; PR 2's
fetcher tests alone do not establish its combined behavior. The candidate deliberately
fails new authority when no-store metadata has no positive remaining freshness.

Known and unknown revocation authenticate the current exact publication digest and public
method/key profile independently of WEB, grant or approval liveness. Known credentials
retain their immutable digest-derived owner. Client authentication, global replay
reservation, family mutation and any required audit commit atomically. A publication
change between signature verification and SQL rejects the stale digest. Inactive known
credentials and repeated/unknown tokens return empty success without granting authority.

DCR, Settings, production/native Azure activation, stock-client URL-only acceptance,
artifact/ingress/binding/operator/legal/tenant gates and automatic merge remain outside this
candidate. Passing local tests cannot authorize installation or feature activation.
