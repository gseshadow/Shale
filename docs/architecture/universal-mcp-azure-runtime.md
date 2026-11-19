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
These tests do not connect to SQL or enable a real deployment. Historical SQL/interoperability
results are reused only at their recorded scope.

Final local verification (Maven 3.9.11, Temurin JDK 21.0.13+9, validated session CA/proxy):

| Check | Result |
| --- | --- |
| Focused provider/configuration + unchanged Case/browser/credential/metadata boundaries | 102 PASS (16 data, 86 server), zero failures/errors/skips |
| Repository selector affected suite | 95 PASS, zero failures/errors/skips |
| Local critical reactor, `mvn test` | 283 PASS, zero failures/errors/skips |
| Documentation links, diff whitespace, unchanged historical SQL/resources/receipts/binding artifacts | PASS |

Exact final local shell invocations used the task-local `run-maven` wrapper, which selects that
JDK and invokes Maven with `-B -s /workspace/work/azure-mcp-runtime/settings.xml
-Dmaven.repo.local=/workspace/.cache/maven`; the settings use the inherited session proxy and
`MAVEN_OPTS` includes `-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts`:

```bash
/workspace/work/azure-mcp-runtime/run-maven -pl shale-server -am \
  -Dtest=CimdRuntimeConfigurationTest,CimdConnectionConfigurationTest,DcrConnectionConfigurationTest,OAuthConfigurationTest,OAuthMetadataTest,McpCaseCompositionTest,McpProductionOffBootTest,CimdBrowserBindingTest,CimdClientAuthenticationTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,DcrRegistrationEndpointTest,CimdMetadataTest,CimdRetrievalTest,AiCaseDataAttestationTest,AiCaseDataAccessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
PYTHONDONTWRITEBYTECODE=1 python build/test-selection/select_tests.py --base 39fb507b --head HEAD --format json
# Execute the emitted selected_maven_args through the same wrapper.
/workspace/work/azure-mcp-runtime/run-maven test
git diff 39fb507b HEAD --check
```

The selector chooses Case/server boundaries and the changed configuration tests, with no full-suite
escalation. The critical reactor is the repository's normal bounded inventory, not the all-tests
profile. No historical SQL/client campaign, native Azure run or browser UI change was introduced.
The initial system JRE had no supported compiler; an isolated JDK resolved that tooling failure.
A new test helper's Runnable/Executable compile mismatch was corrected before the passing runs;
no production contract was weakened to obtain a pass.

## Automated synthetic runtime acceptance successor

The [operator runner](../../build/azure-mcp-acceptance/live-runtime.md) reuses the successful
51/51 disposable SQL installation without installer/reset replay. It pins the existing acceptance
App Service/database, requires one explicit whole-campaign approval, verifies the exact JAR/build,
native dedicated identities/catalogs, binding and RLS, then executes dormant/enabled worker checks
and automated DCR/OAuth/MCP acceptance with independent OFF/stop cleanup. No production activation
or deployment from Codex is authorized. Live PASS evidence must come from the operator's run;
offline simulation does not close the Azure runtime gate.

Audit compatibility uses the existing Case PHI/access/resource/handoff records, audited tenant-policy
procedure and transactional global product-control audit. Operational SQL switches and Azure lifecycle
are evidenced by the private operator journal/Azure records; no new domain audit vocabulary, schema,
permissions or binding change. The linked runbook defines evidence, failure paths and the narrower
DCR automation scope; CIMD egress, browser UI, independent stock clients and capacity gates remain.

The acceptance configuration successor handles an empty App Service settings collection before that
existing campaign approval: read-only SQL prerequisites, fixed-target app-only OFF configuration,
then verified readback. It maps the existing 17 contained credentials to their dedicated providers,
generates a separate synthetic JWT key and retains it encrypted outside Git, and uses in-memory
ARM requests for secret-bearing writes. CLI appsettings/connection-string reads use explicit name
and resource group for compatibility. No SQL or production configuration is changed by preparation;
operational configuration/secret-retention evidence uses the private journal and Azure activity
records, without fabricated PHI/domain audit events or a new schema. See the runbook for encryption,
retry/conflict behavior and platform-specific operator checks.

## Data-free attestation failure diagnostics

The predecessor Case attestation preserves its existing fail-closed stage and numeric SQL code,
adding a closed identity substep (caller/verifier metadata, each forbidden-source connect/platform/
identity/close, containment and authority checks), exception category, bounded monotonic elapsed
milliseconds and interruption flag. Categories distinguish the source-owned MCP deadline from a
query timeout, connection failure, SQL error, refusal or unexpected failure. These fields observe
failure only: no query, permission, contract, comparison, retry, timeout or ten-second budget changes.
Driver messages/SQL states, credentials, URLs, identity values, RowVer and throwable causes remain
excluded. Platform failures distinguish caller/verifier connection opening from each first platform-property
read. Later stages report substep NONE rather than a stale platform/identity label. A bounded
16-node cause/next-exception inspection adds only a closed origin category: DNS, TLS, network
timeout/connect/socket or UNCLASSIFIED. It never emits cause messages, class names, SQL states
or target values, retains no throwable cause, and cannot turn a failure into acceptance.

Audit compatibility: these are operational security-verification diagnostics without tenant/domain
mutation or PHI disclosure; existing operational logs apply and no domain audit/schema is added.
Native enabled-worker acceptance remains unverified until a separately reviewed diagnostic JAR is
run through the existing explicit acceptance approval gate. Local JDBC timing is diagnostic evidence,
not certification of the Azure worker or permission to increase deadlines.

## Azure implied metadata permission compatibility

Azure SQL exposes VIEW SECURITY DEFINITION and VIEW PERFORMANCE DEFINITION as effective
children of the already-required VIEW DEFINITION grant. The acceptance engine confirmed their
covering_permission_name is VIEW DEFINITION via sys.fn_builtin_permissions('DATABASE').
The verifier's effective-permission comparison accepts exactly these two implied permissions only when
VIEW DEFINITION is already in that comparison's allowlist, at database or object scope.
AI caller allowlists remain unchanged; CONTROL, data access, cryptographically secured
definitions and state permissions remain forbidden. No SQL grant, ownership, schema, receipt,
binding or audit mutation is introduced.

The longer read-only diagnostic exposed this false rejection after catalog checks completed.
The deployed ten-second full-attestation budget separately prevents completion on acceptance;
passing local unit tests does not resolve that timing issue or certify MCP/OAuth acceptance.
See [Microsoft's metadata visibility documentation](https://learn.microsoft.com/en-us/sql/relational-databases/security/metadata-visibility-configuration?view=sql-server-ver16)
for the granular metadata permissions. Operational read-only verification adds no domain event.

### Acceptance evidence for the compatibility correction (2026-11-17)

A direct read-only probe of ShaleMcpAcceptance using the candidate correction completed the
full predecessor V2/DCR_V3 attestation: PASS in 167,483 ms, 2,588 recorded operations.
The original reviewed class failed OWNERSHIP because its verifier database permission
comparison rejected the two implied definition permissions. Read-only counts showed zero
owned schemas/objects/principals, zero grant-option permissions and zero unexpected direct
AI-user grants. The engine's built-in permission hierarchy established the implication.

The probe used the five existing dedicated predecessor, verifier, auth, runtime and bootstrap
identities; private retained credentials were passed over stdin. It held a 600-second watchdog,
180-second diagnostic reads/network timeout and 30-second login bound. These are probe-only
transport settings; no deployed timeout, security check, query, grant or production resource
changed. The original 51/51 SQL journal was revalidated unchanged and the acceptance app
remained stopped with controls OFF. Private report and safe operation hashes are retained
under work/readonly-attestation-0a144c59bdd647378c3b6cd216e621bc.

This proves the complete read-only SQL verification can pass with the permission correction;
it is not HTTP, MCP, OAuth or activation acceptance. The original ten-second full-verification
budget remains a separate runtime blocker. Catalog/registry batches alone measured about
7.5 and 16.7 seconds in the longer probe before the remaining per-object/column checks.
Do not retry the unchanged runtime campaign or report it ready on this evidence.


## Fenced long-budget acceptance verification

The dedicated acceptance runner can explicitly enable the longer bounded verification budget
documented in its runbook. Java requires the exact App Service name, acceptance origin/database,
Azure profile/identity grammar and all 17 fixed SQL destination URLs. Normal runtimes retain all
existing defaults. This changes startup/admission and read-only attestation transport time only;
it neither skips nor caches verification and does not extend SQL tool transaction/invocation
leases, quota reservations, token lifetimes or independent failure-audit accounting.

Audit compatibility remains the existing operational configuration journal and Azure lifecycle
records, with unchanged transactional policy and disclosure audits. No SQL schema, grants,
receipt, binding, audit vocabulary or production deployment is introduced. Longer-budget
functional acceptance is not evidence of production latency or capacity suitability.


## Preserve column authority checks while batching transport

The predecessor and registry identity verifiers use the independent catalog's unchanged column
inventory. Up to 256 bound names are checked per caller-identity VALUES query, using the same
HAS_PERMS_BY_NAME SELECT/UPDATE predicates. SQL NULL continues to mean no demonstrated
grant, as before. Every inventoried name must occur exactly once in the response; missing,
duplicate or extra rows fail closed. Names never become SQL source. Empty inventories retain
their original behavior. Identity, object permissions, source/receipt hashes and all other
attestation checks remain fresh and unchanged.

This removes per-column network round trips between the acceptance App Service (South Central
US) and database (East US). No SQL installation, authority, feature switch, tenant isolation,
audit schema or business mutation changes. These remain operational read-only verification
queries, so no new domain audit event is introduced.

The full corrected read-only predecessor V2/DCR_V3 probe passed on ShaleMcpAcceptance
in 79,767 ms with 1,057 recorded operations, versus 167,483 ms and 2,588 operations
before batching, from the same operator workstation. This is a diagnostic comparison,
not an Azure-worker latency or capacity certificate. The private candidate class/log hashes
are retained under work/readonly-batched-attestation-24f6bf9d081e40f7927681656228c9fd.
Original 51/51 SQL evidence remains unchanged; no deployment or activation was performed.


## Fresh object-authority transport batching

A read-only profile of the accepted D131 artifact took 106,090 ms for complete predecessor
and external readiness from the operator workstation: 1,064 SQL executions, approximately
51.8 seconds of repeated object permissions/column inventory/column permissions and 45.9
seconds in the three existing catalog batches. Acceptance SQL/client/audit evidence stays
unchanged. This timing is not an Azure-worker or stock-client latency certificate.

The predecessor now obtains its unchanged object/type/column inventory in one independent
verifier query, retaining objects without columns. Object permissions use up to 64 bound
objects per transport batch under each original identity. Each object's 15151 visibility
error has its own TRY/CATCH result; it cannot imply empty permissions for other objects.
Required roots and the reviewer metadata allowlist, including the existing implied VIEW
DEFINITION children, retain their previous rules. Column SELECT/UPDATE checks bind up to
256 independent object/column pairs per query. Null permission answers preserve the prior
no-demonstrated-grant behavior. Missing/extra/misattributed responses, malformed inventories
and unexpected errors fail closed. No cross-request cache or skipped verification is added.

The three packaged catalog verifiers, source/receipt checks, binding/time samples, identity
boundaries, defaults, admission placement, withdrawal, tool transactions, quotas and expiry
remain unchanged. Registry publication identity verification retains its existing path.
No SQL installation, permission, schema, persistent setting or domain mutation is introduced.
These are operational metadata reads: existing operational evidence applies; no PHI/entity
audit entry is fabricated and the established disclosure audits remain unchanged. Candidate
read-only timings must be recorded separately from the accepted original artifact; only a
separately approved deployment/campaign can establish changed-worker functional acceptance.

Candidate read-only comparison on the same acceptance database completed PASS in 59,828 ms
with 112 SQL executions, versus 106,090 ms / 1,064 executions for the accepted artifact
(43.6% elapsed reduction). The unchanged catalog batches still took 44.2 seconds; normal
ten-second admission and stock-client timing remain unproven. Candidate class/metrics hashes
and the private report are preserved under work/readonly-authority-batch-531bac799cd24e0b9ce33f64f0c838de.
The accepted JAR, original 51/51 SQL journal, completed 8/8 client run and final audit completion
remain separate unchanged evidence. No deployment or activation was performed.


## Fresh module and timezone transport batches

The predecessor module-body verifier now binds up to 64 module names in one catalog query.
Bound CASE comparisons attribute each requested name, and the bound IN filter retains the
original dbo/schema/object/module lookup, UTF-16LE body digest,
effective dbo ownership and caller-versus-owner execution rules. Fixed numeric ordinals
associate each returned row with its expected manifest entry; missing, extra, duplicated
or out-of-range rows fail closed. No names or definitions are interpolated into SQL.

Timezone verification binds all original 28 UTC samples (January, March, April, July,
October, November and December 15 in 2025-2028) in one VALUES query, using the existing
UTC JDBC Calendar binding and identical datetime2/AT TIME ZONE conversion. Every returned
wall time must still match the Java zone exactly, with a unique expected sample ordinal.
The 13 installed module reads and 28 timezone reads become two SQL executions. Existing
query/admission budgets, independent verifier identity and per-admission freshness remain.

The three packaged catalog scripts, hashes/receipts, immutable binding, authority checks,
withdrawal, transaction deadlines, quotas and disclosure audits are unchanged. These are
operational read-only metadata checks, with existing operational evidence and no fabricated
PHI/entity audit events, schema/grant change, deployment or activation. The preceding
S3 capacity comparison restored S0; this batching comparison requires no capacity change.
Read-only candidate timings are separate from the preserved completed acceptance evidence;
normal ten-second admission and changed-worker/client acceptance remain unverified.
