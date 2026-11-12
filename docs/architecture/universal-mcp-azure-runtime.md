# Universal MCP Azure runtime composition

Source baseline: `39fb507b` (`codex/latest`, merged readiness PR #1960).
This successor implements runtime wiring only. All controls remain default-OFF.
No deployment, production SQL connection, configuration/secret change or activation is authorized.
[Azure readiness](../universal-mcp-azure-readiness.md) still governs staged rollout and approvals.

## Wiring and eligibility

`CimdRuntimeConfiguration` is conditional on `shale.ai.cimd.enabled=true` and `azure`/`prod`.
With the control absent/false it creates no sources, capability services or identity checks;
existing ordinary auth/runtime and dormant Boot behavior remain unchanged.
The development fixtures keep their explicit providers and separate test identity.

The old unconditional production bans are replaced by `CimdRuntimeEligibility`:

- Exactly a production profile group or a development profile group; never both or neither.
- Production requires PRODUCTION identity, canonical configured HTTPS public/browser origins,
  enabled AI/MCP and OAuth composition/authorization/exchange, and Case composition/readiness.
- Explicit `shale.ai.data.contract-version=V2` and
  `shale.ai.cimd.contract-version=DCR_V3`. No production V1/V2 external-contract fallback.
  This DCR_V3 selection is shared by predecessor registry attestation, external Case composition
  and every external lifecycle store, even if public DCR registration remains OFF.
- Every required dedicated SQL setting must be present and nonblank. Protected URL options are
  rejected, and each new capability URL must name the exact expected database explicitly.
- Configured capability connections must actually authenticate as separate Azure contained SQL
  users (EngineEdition 5, type S/authentication_type 2, own-session authenticating database/SID
  proof, neutral context/autocommit, exact own-user/public/capability-role token set).
  All capability SIDs must differ from each other and the existing predecessor Case, catalog,
  bootstrap and ordinary auth/runtime SIDs. New capabilities share the predecessor's actual
  server/database. Fixed failures name only a closed capability key, never credentials or SQL.
- Existing complete `McpAiCaseData` and `AiCimdCaseData` startup/admission verification still
  validates catalog/module source, exact permissions/role members, historical receipts, immutable
  binding, reference data and live authority. A failed verifier cannot retry another contract.
  DCR also requires successful Case verification and its live SQL control before startup completes.

A configuration value or successful principal check is not a substitute for catalog verification.
The new principal check runs at source construction; the existing complete Case verifiers still
run at startup and each admission. Verification placement, withdrawal and reconstruction rules
are unchanged. No new per-lease catalog framework, signing authority or attestation cache is added.

`CimdServices`, `CimdCaseConnections` and `DcrCapabilities` now receive production providers.
Each additional identity uses an unpooled `SQLServerDataSource` following the existing bootstrap
pattern: validated encrypted SQL TLS, hostname verification, two-second login timeout and
five-second socket timeout. No new Hikari pool or pool manager is introduced. External Case and
reviewer sources use existing `CimdCaseConnections.bounded` / `McpJdbcDeadline` decoration;
the same ten-second HTTP budget and five-second statement caps remain in force.

Tenant factories take only the existing authenticated WEB or digest-resolved owner and reuse
`RuntimeSessionService` to stamp both `ShaleClientId` and `PrincipalUserId` on a fresh dedicated
connection, with bounded setup. Neutral providers never stamp an asserted tenant. No ordinary
credential fallback, new authentication flow, SQL installer or permission grant is introduced.

## Required configuration contract (future authorized configuration only)

Keep all ten controls in the readiness document explicitly false for a dormant deployment.
This change sets no App Settings or property defaults to true. Existing ordinary JDBC configuration,
JWT secret/TTL/session cutover, browser CORS and frontend API-origin build binding are retained.

The already-established predecessor settings remain required for an enabled composition:

```text
shale.ai.data.jdbc-url / username / password / database / contract-version
shale.ai.data.verifier.jdbc-url / username / password
shale.ai.oauth.bootstrap.jdbc-url / username / password
shale.ai.oauth.public-origin / identity / browser-origin
shale.ai.mcp.allowed-origins
```

For each row below, provide all three exact Spring properties
`shale.ai.cimd.runtime.<key>.jdbc-url`, `.username`, `.password` through approved secret/configuration
facilities. No new environment aliases are defined. URL syntax is the existing `AiJdbcUrl` subset:
SQL Server destination, explicit `databaseName`, optional `applicationName`; TLS/authentication,
MARS, custom socket/trust and timeout overrides are forbidden. Do not infer resource/credential
names from these keys. Provision users/memberships only through the separately reviewed SQL packet.

| Key | Sole capability role / use |
| --- | --- |
| `case` | `AiExternalCaseRuntimeV2`: six external Case roots |
| `reviewer` | `AiCimdAuthorizationCatalogV1`: exact three metadata permissions/accessor |
| `publisher` | `AiApplicationPublisher`: validated CIMD publication |
| `approval` | `AiApplicationTenant`: original tenant request/decision |
| `onboarding` | `AiExternalOnboardingRuntimeV2`: original WEB-session projection |
| `browser` | `AiExternalOAuthBrowserV2`: browser intents/consent/code |
| `issuer` | `AiExternalOAuthIssuerV2`: code/refresh/revoke lifecycle |
| `metadata` | `AiExternalMetadataPublisherV2`: validated publication/cache |
| `resolver` | `AiExternalOAuthResolverV2`: provisional credential-owner lookup |
| `neutral` | `AiExternalOAuthNeutralV2`: unknown credential revocation |
| `dcr-endpoint` (only with DCR enabled) | `AiDcrEndpointV3`: admission/register/status |
| `dcr-metadata` (only with DCR enabled) | `AiDcrMetadataReaderV2`: immutable stored metadata |

Use independent credentials for these exact existing role contracts. Do not combine capabilities
on one user, grant new rights to old role members, use db_owner/ordinary runtime, or supply the
historical C1 `AiDcrRegistrarV2` as the HTTP registrar. Administrative DCR disable/revision management
remains a separately governed `AiDcrAdministratorV2` operation; no new administrative endpoint is added.
Unpooled connections need no pool rotation manager: drain/restart and rerun verification for
credential/security changes through the existing release lifecycle.

CIMD-only operation over the installed V3 schema may keep `shale.ai.dcr.enabled=false`; the explicit
external contract remains DCR_V3. DCR requires CIMD's complete shared external lifecycle. Its
advertisement remains independently gated by composition, capability and live SQL status.
The existing optional CIMD advertisement toggle and its true-when-enabled fallback are unchanged.

## Preserved security and audit behavior

The real Shale browser login/current durable WEB session/JTI is still the human authority.
Tenant approval and each user's consent remain separate. Exact external identifiers, redirects,
S256, resource/issuer checks, assertion replay, refresh rotation, account/approval/application
revocation, quotas, strict RLS, frozen Case algorithms and data-free final handoff are unchanged.

Existing SQL transactions still own registration `GlobalControlPlaneAuditLog`, approval/consent/
lifecycle `EntityActionAuditLog`, and Case PHI/access/resource/RETURNED audits. An audit failure
withholds successful mutation/disclosure. Provider construction and data-free configuration/identity
checks create no fabricated tenant/PHI event. No new audit schema, vocabulary, SQL module, receipt,
immutable binding, role permission or policy is changed. No sensitive payload logging is introduced.

## Acceptance still required before activation

Source/test eligibility is not native Azure acceptance. The following remain STOP gates:

1. A separately authorized isolated Azure app/slot and synthetic tenant database; exact candidate
   artifact, contained identities/permissions, complete V2/DCR_V3 installation and immutable issuer/
   resource/JVM-to-SQL-time-zone mapping. Execute actual production-profile Boot and full startup/
   admission verification there. The historical restricted Azure catalog failure remains unresolved;
   this change neither reruns that campaign nor broadens permissions/timeouts.
2. Actual-worker default `CimdMetadataService` DNS/all-answer classification, direct pinned public
   TCP 443 and original-host SNI/certificate validation, metadata/optional JWKS within five seconds.
   The Cloud proxy/test connector is not this evidence and is not a runtime fallback.
3. External HTTPS discovery/OAuth/MCP routes, REQUEST/ASYNC security chain, canonical consent origin,
   matching frontend build/CORS/deep-link, original WEB login/return/session replacement/revocation,
   and exact stock-client callback routing. Production identity can use a separate canonical HTTPS
   nonproduction origin/database binding in an isolated Azure environment without dev-profile mixing;
   that origin must never be the production destination or reuse production secrets/data.
4. Fresh unmodified stock-client CIMD and DCR connections, manual continuity, two-tenant approval/
   consent/refresh/replay/revoke, actual restricted Case reads/audits/handoff and audit-fault rollback
   using synthetic data. Scripted JDBC provider tests are not these SQL/client results.
5. Actual ingress source/NAT/fingerprint fairness: registration still uses the socket peer and ignores
   forwarded headers. Existing 100 global/10 peer/10 fingerprint attempts per UTC minute, bounded
   accounting, cumulative 5,000 identities and approved-metadata retention are unchanged. Approve
   public DCR exposure and capacity policy; a pilot tenant does not limit anonymous registration.
6. Cold/warm realistic concurrent requests and refresh/revocation within unchanged deadlines, safe
   monitoring/error/latency thresholds, containment/rollback ownership and all owner/operator/security/
   legal/SDK/tenant approvals. No larger-load/Azure SLA is established by historical local measurements.

Separate authorization is required for native testing, production SQL/provisioning, dormant release
and eventual pilot activation. Keep all production controls OFF pending those decisions.

## Focused verification

Pre-edit test-impact review covered configuration/default-OFF Boot, identity/metadata, browser and
credential routing, Case composition/access/attestation and existing DCR endpoint tests.
`CimdRuntimeConfigurationTest` adds missing-setting/URL/profile/contract refusal, dedicated actual-
principal checks using scripted JDBC, V3 capability procedure dispatch, tenant context initialization,
connection closure and deadline decoration. Existing configuration tests now assert eligible production
rather than an unconditional profile ban; the real all-OFF Boot test also checks absence of new providers.
DCR verification tests preserve refusal for failed Case verification, V1 stores and an OFF SQL switch.
These tests do not connect to SQL or enable a real deployment. Final commands/results are recorded
with the draft PR; historical SQL/interoperability results are reused only at their recorded scope.
