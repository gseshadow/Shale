# PR C1 — source-aware application compatibility

Base: draft #1957, `f18574784d2bf8064c7df09cefb4386a310f5b52`.
This increment has no registration HTTP endpoint or production activation.

## Pre-edit impact inventory and intended contract

Reuse the application/revision, tenant approval, consent, external credential
family/publication, RLS, audit and Case algorithms. Keep CIMD V1 and manual
authority distinct. Add explicit EXTERNAL_V2 adapters and separately callable
source-aware procedure roots over those same records. DCR reads stored metadata;
the CIMD parser, resolver and secure fetcher retain their existing rules.

The only predecessor DDL delta is the application source constraint and the
three pretenant CIMD credential locators, which must exclude DCR. A successor
checks the exact new definitions before reconstructing only those reviewed
predecessor values for comparison with unchanged historical receipts. It also
checks the newly added procedures, permissions and metadata/receipt tables.
V1 selection must reject a V2 installation; missing/partial V2 must fail closed.
No catch-and-fallback, old receipt refresh or deployment-binding edit is allowed.

Affected existing tests inspected before implementation:

- `AiCaseDataAccessTest` namespace/read/handoff/cleanup binding;
  `AiCaseDataAttestationTest`, `AiCaseDataAzureSqlDatabaseReadOnlyTest` and registry
  generation tests for verifier selection, historical graph/source preservation.
- `AiOAuthExchangeContractTest`, `AiOAuthCredentialContractTest`,
  `AiOAuthRevocationContractTest`, CIMD generation tests and JDBC lifecycle stores
  for exact procedure/argument selection and separate credential authority.
- `AiApplicationLocalSqlServerTest`,
  `AiApplicationConfinementLocalSqlServerTest`,
  `AiCimdOAuthLocalSqlServerTest`, `AiCimdAuthorizationLocalSqlServerTest` for
  two-tenant approval, current revisions, audit rollback, revocation, legacy
  exclusion and actual restricted-login Case disclosure.
- `CimdBrowserBindingTest`, `CimdClientAuthenticationTest`, metadata/retrieval,
  mapping, discovery and configuration tests for the existing CIMD/manual path.

New coverage must test disjoint generated DCR identifiers, no network fallback,
registration without authority, explicit successor selection and exact adapter
delegation. Real SQL tests require the unchanged hash-gated private fixture.

## Audit review

DCR creation is a tenant-neutral service operation. Its dedicated SQL identity
is the `GlobalControlPlaneAuditLog.OperatorId`, rather than a fabricated tenant
administrator. Use the existing AI_EXTERNAL_APPLICATION event and bounded
correlation metadata in the creation transaction. Audit failure rolls back the
application and metadata. Tenant approvals, consent, credentials and Case reads
reuse their existing transactional audit owners. Public metadata/cache reads
do not fabricate tenant PHI audit events. No secret, metadata body or redirect
is written to audit metadata.

## Verification status

The candidate compiles and has focused unit/generation coverage. **Real SQL
acceptance is unverified**: the private authoritative fixture is absent. Results
and exact commands are recorded in [C1 verification](../testing/universal-mcp-pr-c1-verification.md).

## Exact SQL delta

The new installer is `docs/sql/ai-integration/universal-pr-c1-external-v2.sql`;
the maintained generator and readable templates are under
`build/sql-confinement/generate-external-v2.py`, `external-v2-registration.sql`
and generated `external-v2-modules.sql`. No historical SQL file is edited.

Four existing catalog objects intentionally change:

| Existing object | New contract |
| --- | --- |
| `CK_AiExternalApplication_Source` | Allows immutable `CIMD` or `DCR` provenance. The existing identity trigger continues prohibiting source/identity updates. |
| `AiCimdResolveCodeV1` | Adds an application-source `CIMD` check. |
| `AiCimdResolveRefreshV1` | Adds an application-source `CIMD` check. |
| `AiCimdResolveRevocationV1` | Adds an application-source `CIMD` check. |

All other existing module definitions, grants, RLS predicates, receipt values and
deployment binding fields remain unchanged. The generated successor adds 38
modules: source-aware copies of the existing 33 proof/OAuth/onboarding procedures,
three DCR operations, one metadata immutability trigger and one private
compatibility metadata marker. Copies retain the existing shared tables and
algorithms; internal procedure calls use their V2 names. The CIMD metadata
publisher remains CIMD-only even in V2. DCR preparation uses only stored bytes.

New tables are `AiDcrMetadataV2` (immutable metadata by application revision) and
`AiExternalControlV2` (defaults OFF). Existing application/revision, approval,
publication cache, key binding, intent, credential family, invocation, quota and
audit tables are reused. DCR identifiers are `dcr.` plus 32 database-generated
random bytes encoded as 64 lowercase hex characters. They cannot satisfy the
manual ID grammar or CIMD URL grammar. Registration accepts no selected client
ID, application UUID, tenant, actor, integration, grant or token input.

## Explicit verification successor

The installer requires a fresh neutral owner connection, exclusive `AiSchemaV1`
lock, and all existing product/proof/OAuth controls OFF. It first executes the
unchanged source-pinned registry helper's full VERIFY and unchanged additive
proof/OAuth checks. Any failure aborts installation. It neither repairs partial
state nor reruns an installer to bless drift.

The new private `AiExternalCompatibilityV2` marker carries only new
`DcrCompatV2.*` properties: source hash, original source-constraint object ID and
definition, historical receipt/binding snapshot hash, and the new catalog seal.
These are visible through existing metadata visibility and require no business
table reads or new reviewer grant. Historical `Shale.*` receipts and proof/OAuth
receipt rows are never written. No SQL from a receipt is executed.

`registry-external-v2-catalog.sql` first pins actual new module source,
owner/context/settings, the actual enabled/trusted source constraint, new tables,
trigger, execute-only roles/members, permissions and platform restrictions. Only
after those checks does it substitute the original constraint definition/order
in the predecessor fingerprint projections. Every other predecessor comparison
is retained against its unchanged receipt.

`external-authorization-v2-catalog.sql` likewise checks the actual tightened
locator definitions, then projects their fixed original source strings solely
for the old OAuth fingerprint comparison. It retains all original proof/OAuth
checks and independently compares the historical receipt/binding snapshot using
the existing source-pinned metadata accessor. Nothing catches or suppresses a
failed old verifier. Running V1 against the changed catalog still fails.

`AiExternalContract.EXTERNAL_V2` is an explicit composition choice. Default
constructors retain CIMD V1. V1 attestation rejects any successor marker/table;
V2 requires the complete successor. `AiCimdCaseData` additionally requires its
predecessor to select that same contract, and pins the request-owned
`EXTERNAL_V2` Case namespace through read, handoff and cleanup. No failed
contract retries another version. Startup/admission verification placement and
original request deadlines remain unchanged.

## Capability and deployment boundary

Existing roles are unchanged. New execute-only capability roles are:
`AiExternalCaseRuntimeV2`, `AiExternalLifecycleV2`,
`AiExternalMetadataPublisherV2`, `AiExternalOAuthBrowserV2`,
`AiExternalOAuthIssuerV2`, `AiExternalOAuthResolverV2`,
`AiExternalOAuthNeutralV2`, `AiExternalOnboardingRuntimeV2`,
`AiDcrRegistrarV2`, `AiDcrMetadataReaderV2`, `AiDcrAdministratorV2`.
They reuse the existing adapter/provider pattern. The installer creates no
login/user/membership, pool, datasource manager, certificate or signing identity.
Providers are supplied explicitly; C1 adds no automatic runtime wiring.

The registrar can create only bounded neutral metadata, with a race-safe
cumulative 5,000-DCR-record ceiling. It cannot read/approve tenants, mutate an
existing identity, or issue credentials. The separate metadata reader renews a
cache from stored immutable metadata; it does not accept replacement bytes. Its
source-pinned owner context performs one exact-application historical-use
existence check across tenant approvals, following the existing bounded
pretenant projection pattern. This prevents tenant RLS from hiding retention
eligibility from a neutral lease. It returns only public application metadata,
no tenant identifier or record, and grants no direct tenant-table read. The
separate administrator changes disablement/revision with RowVer and atomic
global audit. Reenablement advances revision and cannot restore old approval or
credentials. Existing tenant administrators use unchanged approval services;
each user still needs separate consent. Public clients require S256; reviewed
RS256 public keys support confidential `private_key_jwt` without downgrade.

Never-used metadata preparation expires after 24 hours. An identity that has
been approved is retained independently of tenant/grant/token deadlines. C1
does not delete historical application records or automatically reclaim the
cumulative cap. Distributed endpoint rate limits, source/fingerprint admission,
retention capacity and HTTP RFC 7591 errors/201/discovery belong to C2 and remain
requirements before any registration endpoint is enabled or advertised.

The existing Spring configuration still constructs the default CIMD lifecycle;
the V2 lifecycle overload is dormant until a later explicit composition. DCR
identifier recognition fails closed if that composition or its stored-metadata
capability is absent. No `registration_endpoint` is published. Production controls
stay OFF and no production SQL, activation or deployment is authorized.
