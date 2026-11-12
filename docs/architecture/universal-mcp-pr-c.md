# Universal MCP PR C — DCR security dependency

PR C1 now has an implementation candidate for this dependency; see
[source-aware compatibility contract](universal-mcp-pr-c1.md). Its real SQL
acceptance remains unverified while the private authoritative fixture is absent.
The report below records the original PR C investigation. No DCR HTTP endpoint
or production capability is added by C1.

Status: **BLOCKED; DCR is not implemented or advertised.** This is a dependency
report under the PR C instruction to document the smallest necessary security
correction instead of expanding into another security architecture. It is not
the requested working compatibility implementation or stock-client acceptance.

Baseline: `3f044f8dd422190d910a1553ee3ef886b259a7e0`, `codex/latest`, including
merged #1955 and #1956. Review date: November 12, 2026 (America/Denver).
Production MCP/OAuth/CIMD/DCR controls remain unchanged and OFF. No production
connection, SQL execution, activation, deployment or automatic merge is authorized.

## Verified implementation dependency

PR A/B's shared authority path is a **CIMD-only SQL contract**, rather than a
source-neutral external-application contract. An RFC 7591 HTTP endpoint cannot
currently register a distinct DCR application into that path:

| Boundary | Current source evidence | Consequence for DCR |
| --- | --- | --- |
| Application provenance | `docs/sql/ai-integration/universal-pr3.sql`, `CK_AiExternalApplication_Source`: `CHECK(Source='CIMD')` | A row with DCR provenance cannot be stored. |
| Publication | `AiApplicationRegistryService.publishCimd` and `AiPublishApplicationV1` require an HTTPS identifier; the procedure inserts literal `CIMD`. | There is no tenant-neutral DCR creation API. Calling this API would misclassify DCR as CIMD. |
| Approval identity | `AiTenantApplicationApproval.ApplicationId` and its revision have foreign keys into `AiExternalApplication` / `AiExternalApplicationRevision`. | A standalone DCR ledger cannot reuse approval merely by supplying its own application UUID. |
| OAuth lifecycle | `build/sql-confinement/cimd-oauth-v1-modules.sql`, `AiCimdPublishMetadataV1`, `AiCimdOAuthAuthorityV1`, `AiCimdBeginOAuthV1`; `cimd-oauth-v1-extra.sql`, `AiCimdLoadClientV1`, `AiCimdResolvePublicationV1`, `AiCimdAuthorizeRevocationV1` require `Source='CIMD'`. | Registration alone cannot reach browser consent, exchange, refresh, revocation and Case authority as DCR. |
| HTTP dispatch | `CimdOAuthProviders.external`, `CimdClientIdentifier`, `CimdBrowserRouting`, `CimdOAuthLifecycle.prepare` select HTTPS CIMD identity and retrieve its document. | An opaque DCR ID would enter the manual path or fail CIMD parsing/retrieval. |
| Registry verification | `shale-data/src/main/resources/ai/registry-confinement-v1-catalog.sql` hashes constraint definitions and module definitions; compares the result to `Shale.UniversalMcpPR3` and `Shale.RegistryConfinementV1.Catalog`. | Simply broadening the source constraint or changing these procedures is detected as drift. |
| Admission | `AiCaseDataAttestation.verify` executes that registry verifier when registry objects are present; `AiCimdCaseData.verify` runs the predecessor and additive OAuth verification. | An unreviewed source change withdraws Case readiness, including the existing composition. |

These are source-inspection findings. No SQL error, successful SQL execution,
or live catalog mutation is claimed in this environment. The checked-in SQL
generation tests independently confirm that the reviewed artifacts and their
predecessors remain unchanged; they do not establish runtime DCR acceptance.

Neither a server-hosted synthetic CIMD document nor a DCR-to-manual ID alias
corrects this boundary. The former still records DCR as CIMD and exposes the
CIMD publication authority to that identity; the latter lacks a shared exact
application identity across tenants. Registration must not impersonate either
source. No such workaround is introduced.

## Smallest necessary correction

The necessary correction is a narrowly reviewed **source-aware successor to the
existing registry/OAuth contract**, using the same application, approval,
integration, grant, credential and Case authority model:

1. Permit immutable DCR provenance for newly generated identities. Keep existing
   CIMD rows and manual registrations unchanged. Provide a separate bounded
   tenant-neutral creation operation; the anonymous registration capability must
   never receive the general CIMD publisher, tenant approval or issuer capability.
2. Use a disjoint generated wire namespace, for example `dcr.` plus 32 random
   bytes encoded as unpadded base64url. The dot excludes the current manual ID
   grammar, and the value is not a CIMD URL. Reserve the namespace in every
   registration, resolver and publication entry point. Verify exact issuer,
   full client ID, provenance and metadata revision on every match.
3. Make the maintained metadata, browser and lifecycle adapters select the stored
   DCR record without a network fetch. Reuse existing tenant request/approval,
   original WEB login, separate explicit consent, S256, digest-resolved ownership,
   rotation/replay, revocation, live revision checks, RLS and Case disclosure.
   Registration supplies no tenant, integration, grant or credential authority.
4. Supply an explicit successor verification resource for the reviewed source
   constraint and module changes, with installation preconditions and tests of
   exactly that delta. Preserve every historical SQL file, receipt and immutable
   deployment binding. Do not recalculate an old receipt over a changed catalog,
   skip the registry verifier, or silently fall back to an older contract.
   The current V1 verifier cannot continue passing against an altered constraint;
   runtime contract selection must be deliberate and fail closed. Existing
   installations require the already established drain/verify/reconstruct rollout.
5. Add race-safe durable registration admission and retention at the creation
   transaction, through the narrowly scoped adapter. Bound source and metadata
   fingerprint rates, global creation rate and pending storage. Keep trusted
   source-address handling independent of caller-supplied forwarded headers.
   Never evict an approved application to admit a new registration. Preserve
   existing tenant/user extraction quotas across repeated registrations.

This correction needs no new catalog-attestation framework, pool manager,
certificate/signing authority, Settings redesign or Case algorithm. Its review
must explicitly identify the changed source constraint, affected routines and
successor verification selection. This report does not implement or authorize a
production schema transition.

## RFC 7591 contract once the dependency is corrected

Implement `POST /oauth/mcp/register` behind an explicit default-OFF development
gate. Advertise `registration_endpoint` only after the complete durable
registration, approval, issuance and Case composition is verified. Fail startup
on partial enabled composition; production/azure remain prohibited in this PR.

Accept bounded JSON and return standard 201 metadata, generated `client_id`,
`client_id_issued_at`, accepted redirects/grants/response types/scopes and explicit
`token_endpoint_auth_method`. Registration does not create a tenant approval or
consent, and does not return tokens. RFC 7592 management is outside scope.

Support explicit `none` with mandatory S256, and the existing supported
confidential method `private_key_jwt`/RS256 using validated public keys. Do not
downgrade confidential registration to public. RFC 7591 defaults an omitted
authentication method to `client_secret_basic`; reject that unsupported method
with `invalid_client_metadata`. Do not invent a shared-secret method to satisfy
the default. Existing manually pinned confidential clients keep their behavior.
The historical plan's public-only initial DCR profile is superseded by PR C's
explicit confidential-client requirement.

Validate exact redirects, methods, scopes and code/refresh combinations before
writing. Preserve the existing redirect policy; any stock-client localhost or
protocol incompatibility needs a focused compatibility correction and evidence.
Handle `application_type` explicitly without accidentally imposing OIDC defaults.
Ignore bounded unknown RFC 7591 extensions; never interpret tenant-like extension
fields as authority. Reject invalid security metadata and caller-provided client
identity/credentials instead of accepting a selected ID. Use standard
`invalid_redirect_uri` / `invalid_client_metadata`, 413 for actual oversized
input and 429 with Retry-After for admission. Require no initial access token,
CAPTCHA, manually supplied ID or proprietary client change.

The onboarding plan's proposed admission limits remain measurement candidates:
10 requests/minute/source, 100/minute globally, 5,000 unapproved records and
24-hour retention for never-used pending registrations. Enforce them atomically
across replicas, bound rate-accounting storage itself, and test races and NAT
fairness. An approved identity must survive pending-record cleanup; approval,
grant and credential deadlines retain their current independent semantics.

## Pre-edit test-impact review

No production symbol, SQL, configuration, UI or test expectation is changed by
this report. Existing coverage was inspected before deciding against an unsafe
HTTP-only implementation:

- `OAuthMetadataTest`, `OAuthConfigurationTest`,
  `CimdConnectionConfigurationTest`, `OAuthClientMappingTest`,
  `CimdClientIdentifierTest`: truthful capability advertisement, disabled
  registration route, development composition, and existing identity grammar.
- `AiApplicationLocalSqlServerTest` and
  `AiApplicationConfinementLocalSqlServerTest`: immutable receipts, catalog drift,
  exact identity, tenant approval and preserved manual authority. These require
  the real disposable SQL fixture for runtime verification.
- `test_registry_v1.py`, `test_cimd_v1.py`, `test_cimd_oauth_v1.py`: generated
  artifact consistency, predecessor preservation and fixed credential namespaces.
- PR A/B lifecycle/browser/Case tests and the stock Hermes harness remain the
  starting point for the successor's two-tenant, publication change, approval
  change, audit rollback, refresh/revoke and manual/CIMD regressions. Existing
  evidence is historical and is not counted as a new PR C pass.

## Audit compatibility

No authoritative mutation or sensitive read is added here, so no new audit event
or migration is needed for this report. The future DCR creation seam must first
resolve anonymous registration attribution: the existing general publisher uses
operator-attributed `GlobalControlPlaneAuditLog`, which must not be fabricated as
an authenticated administrator action for an anonymous client. Use bounded
sanitized registration security telemetry and document its retention; if a
durable creation audit requires a vocabulary/attribution adjustment, specify only
that gap. Metadata read/admission checks do not fabricate tenant PHI audit rows.

Tenant approval and consent mutations must continue auditing on the authoritative
SQL connection before commit, with audit failure rolling back the mutation.
Case reads retain PHI and access/resource/handoff auditing. No metadata body,
redirect, browser credential, assertion, code or token may enter audit metadata.

## Acceptance still blocked

The required hash-gated private fixture `authoritative-candidate.sql`, SHA-256
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`,
is absent from the fresh workspace and attachment directories. The previous
PR B attachment path is not present. Do not reconstruct or substitute it.
This is an additional test prerequisite, separate from the source-contract
dependency above.

No unmodified DCR client has completed registration, browser authentication,
administrator approval, explicit consent, issuance, Case tools, refresh or
revocation in PR C. No client name/version or sanitized HTTP/SQL success evidence
can truthfully be recorded yet. Cross-tenant runtime checks and a fresh
CIMD/Hermes regression are also unexecuted. DCR remains unverified and unavailable.

After the narrow successor is implemented, use fresh stock DCR profiles in two
tenants with only `https://shale.test/mcp`, existing synthetic Shale credentials
and the existing browser UI. Record the exact unmodified client release/commit,
SDK, registration POST, 201 metadata field names, separate approval/consent,
token/tool outcomes, refresh/revocation, cross-tenant denials, publication and
approval invalidation, and matching sanitized SQL audit/handoff counts. Retain
manual and stock Hermes/CIMD regressions. No production hostname is contacted.

Verification executed for this report is recorded in
[PR C verification](../testing/universal-mcp-pr-c-verification.md).
