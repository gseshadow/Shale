# PR 4D: signed boxed catalog verification

Stacked on #1952, base `93b8610ca9caef69669ebd11dee9a1971557abad`.
CIMD remains OFF, unadvertised and unwired from HTTP/browser providers.

**Outcome:** the versioned catalog and all-nine-pool startup readiness can pass on the approved boxed
SQL Server profile. The unsigned PR 4C gate still fails with 59310. This does **not** establish complete
request-serving readiness: the retained Case-disclosure budget diagnostic fails closed when repeated
full attestation consumes the original proof/disclosure deadlines. Native Azure acceptance remains open.
No production owner, SQL, identity, credential, receipt, setting or binding was changed.

## Review, alternatives and privilege boundary

Reviewed repository rules, schema and tenancy/RLS guidance, system overview, #1948 compatibility,
PR 4A/4B/4C handoffs and current deployment/native-readiness records. Before editing, inspected and
searched all runtime configuration constructors, role/root uses, existing actual-identity and
V1/V2 authoritative tests, startup fencing and generator contracts. V1 constructors and their
refusal evidence remain supported; explicit new settings select V2. A failed V2 never tries V1.

Disposable probes establish the boundary:

| Fixed module context | Server metadata | sysadmin | Disposition |
| --- | --- | --- | --- |
| Unsigned owner context, sa-owned DB | Absent | 0 | Reproduces 59310 from unchanged #1948 |
| Metadata-signed owner context without AUTHENTICATE SERVER | Absent | 0 | Insufficient |
| AUTHENTICATE SERVER with sa-owned DB | Present | **1** | **Rejected**, unacceptable elevation |
| AUTHENTICATE SERVER with dedicated disabled non-admin owner | Present | **0** | Implemented, with live capability/owner/signature checks |

The installer does not provision an owner or change database ownership. A separately governed owner
must already be a disabled SQL login, have no server role or scoped grant beyond ordinary CONNECT SQL,
own only this application database and own no endpoint/server principal. Its SID must differ from all
new runtime identities and match an independently supplied deployment binding. An sa/sysadmin owner,
enabled owner, changed owner or excessive effective server authority refuses. A production destination
that cannot adopt this owner profile is unsupported; do not add TRUSTWORTHY, impersonation, broad
runtime SELECT, direct receipt-table grants or signatures to historical modules as a workaround.

Rejected alternatives include authenticating an sa owner, changing old verifiers to omit server
checks, granting receipt-table SELECT and resealing their catalogs, or supplying broad database SELECT
through a signer. The implemented profile retains the existing fixed module's database-owner context
and adds only bounded server metadata capability. It requires an explicit owner-preparation decision;
this draft authorizes no production preparation. A different execution model needs a separately
reviewed versioned mechanism rather than relaxing this profile.

## Independently versioned mechanism

The new one-shot OFF installer creates `AiCimdCatalogV2`, `AiCimdAttestCatalogV2` and a new signing
certificate. It embeds the unchanged PR 4B verifier byte-for-byte, transitively retaining original
V1/V2 dispatch, PR 3/#1948 and PR 4A/4B source/catalog/before receipts. The PR 4C closed graph is reused.
No old SQL/module/receipt/manifest/binding or generator is edited or refreshed. The new installer is
[explicitly boxed-only](../sql/ai-integration/boxed/universal-pr4d-catalog.sql), outside the existing
Azure-compatible top-level inventory and the sealed V1 generator inputs. Their discovery rules remain
unchanged; V1 `--check` proves every V1 generated artifact remains identical.

| Principal | Authority |
| --- | --- |
| New catalog SQL login | Normal CONNECT/public defaults, server VIEW ANY DEFINITION/database inventory, member of only AiCimdCatalogV2 |
| AiCimdCatalogV2 | Database VIEW DEFINITION and EXECUTE only on AiCimdAttestCatalogV2; no direct application data/column access |
| AiCimdCatalogSignerV2 | Non-connectable certificate-mapped login; exactly VIEW ANY DEFINITION, VIEW ANY DATABASE, AUTHENTICATE SERVER and CONNECT ANY DATABASE; no role, grant option, endpoint or principal ownership |
| Dedicated disabled owner | Existing dbo authority only in the target DB, ordinary server defaults; cannot establish a new login session |
| Eight business identities | Their unchanged exact procedure roots and permissions from [PR 4C](universal-mcp-pr4c-runtime.md#exact-permission-matrix); no signing or system-database grants |

CREATE LOGIN's redundant explicit CONNECT SQL grant is revoked from the certificate login. Certificate
login type C has no password authentication; public CONNECT SQL defaults do not make it a credential.
CONNECT ANY DATABASE is confined to the fixed signed module and is needed to inspect model and other
catalogs that business sessions cannot access. The normal catalog/business callers do not receive it.
No general IMPERSONATE capability is granted. The signed procedure validates its live effective server
permission vocabulary and rejects sysadmin/CONTROL SERVER **before** executing any historical batch.
A changed owner cannot turn the signed code into an unchecked administrator path.

The certificate's public copy is installed in master and the single metadata login is mapped to it.
Only the new V2 module is signed. The temporary certificate-encryption password is generated inside
SQL, never returned, and the private key is removed before final verification/commit. Re-signing,
private-key presence, missing/extra signatures, changed source/settings/owner, certificate mismatch or
expiry fails closed. Signing privileges, role grants and module creation are one transaction; an
injected late permission fault rolls back all new database/master objects and leaves old receipts
unchanged. No operator key backup or reusable signing credential is created.

Runtime configuration now requires `shale.ai.cimd.catalog-version=V2`,
`shale.ai.cimd.catalog-certificate-sha256` and `shale.ai.cimd.catalog-owner-sid`, in addition to all nine
independent pool settings and existing immutable deployment values. Bindings are public-key SHA-256
and the exact SQL owner SID, independently approved/configured; they are not auto-discovered as runtime
authority. The new record/configuration string forms redact their values. Java pins the new module's
UTF-16LE SHA-256, type/context/ANSI/QI/owner/grants and signature envelope before entering it, then
checks its returned public certificate hash and owner SID against that independent binding.
The existing deployment binding keeps `case-read-v1` even when confinement V2 is selected.

## System-database and actual-session authority

A source-pinned baseline records the clean Microsoft SQL Server 2022 image
`mcr.microsoft.com/mssql/server@sha256:11927f5edc5122658e90b3a80ee36e8aef779310bf771415c1d89271859c73fd`,
product `16.0.4315.2`. Baseline capture uses metadata from a fresh disposable image, not an existing
production destination or the private application fixture. A new build, legitimate vendor variant,
extra system object, unknown signer or authority change is a finding requiring source review; there
is no baseline-adoption/refresh mode. The profile contains 3,687 permission rows (3,329 public/guest),
1,568 persistent securable objects and 1,190 authority facts, including 971 module signatures.

The independent signed verifier checks:

- Every system-database permission class, object/column grant, denial, grant option and grantor;
  PUBLIC/GUEST grants are included, not inferred from an empty filtered catalog.
- Complete system principals, authentication categories, mapped server identities, role membership
  and role ownership; module signatures and their certificate/key names; vendor signer server
  principals/permissions/memberships; trigger state/order and security-policy predicates.
- All persistent system-database objects, including non-Microsoft additions, with owner, module
  source/settings/execution context. Local temporary objects are not shared persistent authority;
  global temporary additions refuse. Non-securable PK/UQ constraints are excluded: a master internal
  pool PK is hidden even under VIEW ANY DEFINITION, and no baseline grant targets such a key. No
  VIEW SERVER STATE/PERFORMANCE STATE grant is added to reveal it. All executable local SQL modules
  have visible hashed definitions in the approved baseline; opaque changed definitions refuse.
- The built-in empty guest-owned schema is recognized explicitly. New guest-schema objects,
  additional guest/public ownership, role inheritance, unknown signatures/objects and server
  triggers refuse. This covers implicit schema ownership as well as explicit ACLs.
- Every other database from complete server inventory, including inaccessible databases: none of
  the nine runtime SIDs, the disabled owner or the signer may have an unexpected mapped principal.
  Offline/inaccessible metadata is a finding, not absence. The signer inspects metadata only.

These are the exact approved vendor PUBLIC/GUEST capabilities, not a grant of arbitrary system data
access. Normal business callers still have guest/public tokens in master, msdb and tempdb and zero
access to model. Those states and effective database permissions are probed using their actual
sessions. Other user databases must have zero HAS_DBACCESS. Unknown/NULL results refuse. No per-user
system grants are provisioned. This is an intentionally strict boxed profile; other applications'
system customizations may require an independently reviewed profile before deployment.

All nine actual application identities, inherited tokens, scoped grants, ownership, object/column
effective permissions and separation from legacy/auth/runtime/bootstrap remain checked before startup
and each service lease. V2 batches object probes from the independent inventory using the complete
OBJECT permission vocabulary and all columns' SELECT/UPDATE/REFERENCES; required EXECUTE roots must
also exist. This reduces network round trips without caching or dropping checks. Historical V1 probing
is unchanged. The V2 catalog execution has a fifteen-second SQL query bound and sixteen-second JDBC network
bound. Complete system authority verification exceeded the old five-second catalog bound and refused
readiness; this dedicated limit permits that metadata-only verification to finish without letting
the driver's short login/validation socket timeout override it. V1 and business query limits, and all
proof/disclosure deadlines, remain unchanged. This does not make request composition fit those budgets.

## Acceptance, limits and audit compatibility

The authoritative private fixture matches approved SHA-256
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f` before and after use.
Only its restrictive hash-gated DDL import is used, with synthetic rows in nonce-owned databases and
actual restricted logins. The attachment is neither modified nor committed. No production SQL or
HTTP/browser endpoint is contacted. TLS uses generated localhost certificates/private truststores
with `encrypt=true;trustServerCertificate=false`; the pooled test destination is `localhost`, matching
the certificate. No system trust changes.

The scoped acceptance campaign selects only new methods: V2 catalog/startup/lease acceptance, V2
permission/binding drift, and one V1 catalog/startup repetition. It reproduces the unsigned 59310,
rejects the sa-owner profile, verifies late-install atomic rollback, erases keys, passes full actual
catalog/identity checks and real pooled startup, resolves/rotates a CIMD credential and authenticates
its proof through the dedicated Case pool. Direct Case/receipt reads and signing-key reuse are denied;
manual Case continuity and every historical receipt/binding remain intact.

Drift tests cover enabled/changed owner, signer server authority, server trigger, inherited reader role,
PUBLIC column grant with actual guest read, implicit guest-schema access, guest role inheritance,
system mapped user, another-user-database mapping, certificate/owner binding mismatch, missing signature
and permanent runtime shutdown after failure. Findings are restored only in the disposable fixture;
readiness must pass again after each reversible fault. Signing authority is not broadened to make any
check pass. The final missing-signature fault intentionally remains broken until fixture disposal.

**Full request-serving acceptance is still NO-GO.** The opt-in
`catalogCaseDisclosureBudgetDiagnostic` retains the original positive Case-disclosure assertion.
It fails closed with repeated full attestation inside the original ten-second proof/eight-second
read budgets. Development timing showed roughly four seconds in the unchanged historical/catalog
batch plus actual-session checks; batching reduced overhead but does not establish a complete
response within that budget. The diagnostic failed during development before the final expansion of system authority checks;
it was not repeated after that expansion. It is separate from catalog/startup acceptance, is not
counted as a pass, and has no TTL/cache, skipped verifier or extended disclosure window workaround.
Before HTTP integration, implement/review deadline-aware request composition and demonstrate the
complete audited handoff. Do not advertise readiness from a startup/catalog pass alone.

Catalog verification reads metadata, receipts/binding and existing non-PHI diagnostic counts; no new
PHI/domain audit event is fabricated. Existing transactional business/PHI/integration audits and
fail-closed disclosure are unchanged. Signing/owner preparation belongs to governed operator SQL
change records; this draft adds no audit schema/vocabulary or historical allowlist edit. No credential,
assertion, URL, module body or sensitive payload is logged as new application evidence.

Test-impact review:

| Changed contract | Relevant verification |
| --- | --- |
| Runtime configuration/binding and V1 compatibility constructors | Updated AiCimdRuntimeTest; AiCaseDataAttestationTest; selector's server configuration checks |
| Actual identity/permission probing, signed root and pooled leases | Three explicitly selected authoritative V1/V2 methods; scoped drift assertions; existing AiCimdRuntimeSqlServerTest is selected with SQL opt-ins absent |
| New signing/system profile and frozen predecessor generator inputs | Five test_cimd_catalog_v2.py contracts, V1/V2 generation checks and predecessor-byte comparison |

The selector selects security-data and its core/data/server dependencies. Its informational unknown
resource-path escalation does not justify replaying all historical SQL or the all-tests profile.
Final local validation (2026-11-11, JDK 21 / Maven 3.9.11):

| Check | Result |
| --- | --- |
| Three explicitly selected authoritative SQL methods | 3 PASS, no failures/errors/skips |
| AiCimdRuntimeTest + AiCaseDataAttestationTest | 18 PASS |
| test_cimd_catalog_v2.py | 5 PASS, including V1/V2 generation checks |
| Selector security-data/core/data/server batch | 162 PASS |
| Default critical reactor | 254 PASS |
| Full Case-disclosure diagnostic | Development FAIL CLOSED; unresolved, not counted as acceptance |
| Native Azure | NOT RUN; both independent gates OPEN |
| Predecessor bytes | All 73 existing SQL/resource/confinement-build files identical to #1952 |
| Independent TLS cleanup | Zero user DBs, nonce SQL logins, V2 signer logins and master certificates |

Normalized Maven commands below retain the executed selections; local JDK/repository/truststore
wrappers supplied tooling and disposable TLS material. Authoritative SQL variables use the existing
PR 4B fixture harness, approved fixture hash and explicit LOCAL_DISPOSABLE_AI opt-in. They are absent
for focused/affected/critical regression commands. No inherited historical SQL methods are selected.

```sh
mvn -pl shale-data -am '-Dtest=AiCimdCatalogV2SqlServerTest#catalogSuccessfulReadinessAndConfinedRuntime+catalogPermissionAndBindingDriftFailsClosed,AiCimdCatalogV2V1SqlServerTest#catalogSuccessfulReadinessAndConfinedRuntime' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-data -am -Dtest=AiCimdRuntimeTest,AiCaseDataAttestationTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/sql-confinement -p test_cimd_catalog_v2.py
python3 build/test-selection/select_tests.py --base 93b8610ca9caef69669ebd11dee9a1971557abad --head HEAD --plan-output work/pr4c/selection.json
# Execute the emitted selected_maven_batches (security-data, core/data/server).
mvn test
git diff --check 93b8610ca9caef69669ebd11dee9a1971557abad
```

The boxed installer was subsequently relocated into boxed/ with byte-identical SQL (SHA-256
`126a661092ae68cffbb531b85d3ec667824cf258312c88581c7d0e712ebe42e1`); focused and affected
checks validate the final path and unchanged Azure inventory. The test cleanup was corrected to
avoid opening SQL when setup is legitimately skipped and no owner login was created. Neither change
alters tested SQL or the successful actual-login cleanup path; no historical SQL replay was needed.
The accepted authoritative attachment hash remained unchanged after execution. Following independent
cleanup inspection, the disposable container and generated passwords, TLS private keys and private
truststores were removed. No all-tests campaign or GitHub Actions tests were run.

## Native Azure and HTTP dependencies

V2 signing is explicitly boxed-only. Azure SQL Database's contained-user/catalog model does not gain
AUTHENTICATE SERVER or a certificate login in master from this design. The Java V2 path refuses that
platform; it never issues boxed probes as a substitute. SQL Managed Instance/other boxed builds also
need separately reviewed system profiles. Existing native restricted-catalog and disposable
mutation/fault gates remain open; this PR does not restart the stopped investigation or substitute
production. Old contained V1 behavior retains its original scope and is not new V2 acceptance.

Independent acceptance requires separately authorized disposable Azure SQL Database resources, nine
distinct SQL-contained users with verified SIDs/authentication and no privileged mappings/groups,
independent TLS pools, exact unchanged V2/#1948/PR3/4A/4B receipts/binding, and a separately versioned
contained catalog mechanism that proves complete metadata without boxed server catalogs or runtime
data grants. Then prove actual accepted service leases, cross-tenant rejection, pool reuse/rotation,
source/schema/receipt/binding and permission faults, replay/revocation/audit rollback, complete Case
proof/disclosure and manual continuity. Native restricted catalog/read-only acceptance and native
disposable mutation/fault acceptance are separate gates; neither is satisfied by these Linux tests.
See [current native acceptance](../testing/native-azure-readonly-acceptance.md) and
[diagnostic closeout](../testing/native-azure-diagnostic-closeout.md); no archived retry packet is
authorized or selected by this draft.

Before HTTP/browser composition: approved destination/disabled-owner/system-baseline and signer
provisioning/binding; complete request/deadline and audited disclosure acceptance; maintained manual/
CIMD provider routing without translation or fallback; combined original-WEB/metadata/signature/
HTTPS/SQL lifecycle; independent native acceptance; and existing artifact/configuration/ingress/
logging/legal/operator/tenant gates. Signer rotation and a future certificate expiry require governed
provisioning and reattestation, not an in-place receipt refresh. DCR, Settings, stock clients,
activation/advertisement, production changes and automatic merge remain outside this draft.
