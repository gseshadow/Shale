# PR C2 — development DCR endpoint

Base: PR #1957, `8aea2c4c`. Stacked development-only increment; production OFF.

## Impact inventory before implementation

Read C1/C simplified contract, tenant-onboarding plan, database AI schema,
tenancy/RLS, system overview, API migration/readiness and deployment boundaries.
Existing affected coverage: OAuthMetadataTest, OAuthConfigurationTest,
CimdConnectionConfigurationTest, DcrApplicationMetadataTest, OAuthClientMappingTest,
CimdBrowserBindingTest, OAuthAuthorizationFlowTest, OAuthCodeExchangePolicyTest,
OAuthRevocationPolicyTest, McpCaseCompositionTest, AiDcrApplicationStoreTest,
AiCaseDataAttestationTest, AiCaseDataAccessTest and four C1 authoritative SQL tests.
Review the existing stock Hermes harness and preserve its HTTP/browser flow.

## Narrow SQL dependency and successor

C1 prohibits metadata deletion, including expired registrations. C2 therefore
needs reviewed metadata and publication-cache trigger deltas to reclaim expired never-approved public metadata.
Use a private owner-context cleanup procedure, following the existing bounded
neutral retention projection. It may remove only expired metadata/publication
bytes for identities with no tenant approval ever; identity/revision/audit history
is preserved. Approved applications are never removed. Preserve C1's cumulative
5,000-identity cap as an additional hard storage bound; reaching it requires an
operator capacity decision, not automatic eviction.

Add durable minute buckets for global/source/fingerprint admission, bounded by
fixed caps and expired-bucket cleanup. SQL transaction locks serialize admission
across replicas. Source attribution uses the actual socket peer, ignoring forwarded
headers; production trusted-ingress design remains outside this development gate.
Registration and pending capacity share the existing registry application lock.

Select an explicit C2 successor which pins actual changed trigger/new modules
and new schema/permissions before projecting only the fixed predecessor trigger sources
for C1 comparisons. C1 receipt properties and predecessor verifiers are unchanged.
No fallback, receipt refresh, new pool or attestation framework.

## HTTP and composition

POST /oauth/mcp/register accepts at most 32 KiB JSON. RFC 7591 defaults are
honored: omitted authentication means unsupported client_secret_basic and is
rejected. Public none and private_key_jwt/RS256 only; code/refresh, closed scopes,
validated redirects and public keys. Bounded unknown extensions are ignored;
caller identity/credential fields are refused. No registration management token.

Default-OFF development configuration requires complete verified C2 Case/OAuth
composition, explicit registrar/stored-metadata capabilities and live SQL control.
Only then may discovery publish registration_endpoint. Public clients retain
mandatory S256. Existing tenant approval/login/explicit consent and credential/
Case algorithms are reused without granting authority during registration.

## Audit compatibility

Creation uses C1's SQL-identity-attributed GlobalControlPlaneAuditLog on the same
transaction; no administrator is fabricated. Cleanup retains identity/revision/
audit history and deletes only expired public payload/cache bytes; no tenant data
or PHI read is introduced. Rate accounting is short-lived security state and is
not a tenant mutation/audit event. No addresses, metadata bodies, redirect values,
keys, assertions, credentials or tokens enter audit metadata. Existing approval,
consent, revocation and Case audit owners remain authoritative.

## Acceptance

Use hash-verified authoritative SQL, disposable TLS and https://shale.test/mcp.
Record independent stock DCR client/version and actual discovery, registration,
browser/admin/consent, token/tool, refresh/revoke and tenant-isolation evidence.
A custom harness is supplemental only. Production endpoints are never contacted.

## Implemented SQL delta and deployment selection

The installer `universal-pr-c2-dcr-v3.sql` requires C1 verification and all
product/proof/OAuth/external switches OFF. It changes only two existing modules:
`AiDcrImmutableMetadataV2` and `AiCimdImmutablePublicationV1`. Updates remain
immutable except the existing publication freshness update. Deletion requires
owner context, DCR provenance, expired public metadata and no historical approved
integration in any tenant. Empty cleanup is a no-op. Neither application identity,
revision history nor creation audit is deleted. Active assertion references and
key bindings prevent cleanup; expired assertion cleanup is bounded to 256 rows.
Serializable approval checks and application locks protect retention races.

Five new procedures are `AiDcrAdmissionV3`, `AiDcrRegisterApplicationV3`,
`AiDcrExpirePendingV3` (private), `AiDcrStatusV3` and `AiDcrCompatibilityV3`
(private marker). Two new tables hold default-OFF control and minute rate buckets.
One execute-only `AiDcrEndpointV3` role gets only admission, registration and status.
The original C1 registrar is never the HTTP capability. The new registrar checks
the DCR switch again under HOLDLOCK inside its mutation transaction.

`AiExternalContract.DCR_V3` selects the narrow verification successor, retaining
C1's actual lifecycle/Case procedures and EXTERNAL_V2 request namespace. The new
verifier reuses the C1 graph and contained/boxed identity checks. Both original
trigger sources are projected only after actual replacement-source pins pass.
All old comparisons/receipts remain; neither V1 nor C1 is suppressed or refreshed.
The SQL catalog batches retain guarded platform dispatch/encoding. There is no
new pool, signing certificate, catalog framework, RLS policy or tenant authority.

Durable limits are fixed SQL UTC-minute windows: 100 attempts globally, 10 per
socket-peer digest and 10 identical canonical metadata fingerprints. Accounting
contains no raw IP or metadata and is capped at 1,024 rows; expired windows are
removed under the same admission lock. Invalid requests consume source budget.
Creation failures still consume attempted-registration budget. HTTP concurrency
cannot exceed the SQL lifetime 5,000-identity ceiling. This is intentionally a
bounded development capacity, not automatic reclamation of approved identities.
Production ingress/NAT calibration and capacity expansion remain operator work.

## Existing browser and metadata compatibility

The existing consent page now recognizes exact DCR identifiers for the same
onboarding flow and accepts an exact localhost callback only for DCR. Original
manual/CIMD numeric-loopback rules remain; there is no Settings redesign.

`shale.ai.cimd.metadata-advertisement-enabled` defaults true when CIMD is enabled.
A development operator may withhold this optional metadata capability while still
serving the existing CIMD path, yielding a standards-based DCR-only advertisement.
DCR advertisement independently requires authorization/exchange and verified DCR
composition/control. This permits stock clients to choose DCR automatically;
it supplies no client ID, metadata document or protocol setting. Switching this
advertisement grants no authority and changes no SQL. The disposable harness uses
both advertisement modes to verify DCR and the original Hermes/CIMD path.

Public registration returns the accepted code/refresh profile, even when the
requested/default grant list contains only authorization_code; the response makes
that supported server profile explicit. RFC 7592 registration management is not
implemented or advertised. Confidential registration accepts inline validated
public RS256 JWKS only; remote JWKS/software statements and secret methods are
outside this profile. Both public and confidential authorization require S256.
