# ShaleMcpAcceptance installation packet

**PREPARATION ONLY. No Azure/SQL connection, migration, credential provisioning, deployment,
firewall change or activation is authorized by this PR.** Packet introduced in #1963 from
`bf320bb352ebae6932a22b666c8ed9285eaed77a`; reconciled against latest fetched `codex/latest`
`17840f66` (merged #1963). Current operator instructions and validation are in the
[next-steps review](../../../testing/azure-mcp-acceptance-next-steps.md).

This packet prepares existing migrations for the operator-confirmed fresh **Online Standard S0**
`ShaleMcpAcceptance`, collation `SQL_Latin1_General_CP1_CI_AS`, on `shale.database.windows.net`,
resource group `Shale`, subscription `cd334e90-1e97-4b68-b4e3-80bf7b438546`.
It does not independently verify those live facts. Exact database resource ID:

```text
/subscriptions/cd334e90-1e97-4b68-b4e3-80bf7b438546/resourceGroups/Shale/providers/Microsoft.Sql/servers/shale/databases/ShaleMcpAcceptance
```

Production `Shale`, `Shale_Copy`, `Shale_Backup_Pre_v2Merge`, `auth-invocation-test`, `master`
and every unexpected database are outside this packet. There is no deletion, rename, reset,
production connection or server-level change command.

Read the [acceptance runbook](../../../testing/universal-mcp-azure-acceptance-runbook.md),
[readiness plan](../../../universal-mcp-azure-readiness.md),
[runtime contract](../../../architecture/universal-mcp-azure-runtime.md), and
[binding packet](../../../production-mcp-deployment-binding-readiness.md).
Tools are in [build/azure-mcp-acceptance](../../../../build/azure-mcp-acceptance/).

## Scope and Azure adaptations

| Existing disposable behavior | Narrow native Azure adaptation |
| --- | --- |
| Loopback nonce DB/server logins, master access, automatic drop | Fixed existing acceptance DB, EngineEdition 5, contained SQL users, no master/server login/cleanup SQL. Never relax old launcher's destination guards. |
| Hash-checked fixture imported with JDBC batches | Same `CaseReadVerificationSafety.schema` parser, unchanged fixture bytes/SHA-256; offline export preserves batch/module UTF-16LE source through `sp_executesql`. SET batches remain in the outer session. SQLCMD GO delimiters cannot add bytes to module definitions. No replacement schema. |
| Authoritative fixture already has three FILTERs | Reuse V2 `beforeConfinement` normalization only for this fresh import, followed by existing Phase 1R PREFLIGHT/APPLY/POSTCHECK and V2 prerequisite. Reviewed hashes are explicit inputs, never automatically adopted from an arbitrary live catalog. |
| Local direct binding to shale.test / operator JVM UTC | Pause for actual Azure worker JVM evidence, approved canonical nonproduction origin and SQL-zone mapping; reuse unchanged binding preflight/install/postcheck, 28 Java/SQL samples. Never insert a placeholder and update it later. |
| Local fixture enables all controls and issues credentials | No enablement or credential issuance. Five SQL control tables remain OFF, tenant policy absent; synthetic rows only. Native SQLCMD catalog/principal checks are separate from future enabled Boot/client acceptance. |
| SQL Server metadata dispatcher | Existing Azure platform branches/physical-incarnation identity and contained-user attestation. No new permission or timeout workaround for the historical native verifier failure. |

The operator deliberately selected shared SQL server `shale` and existing Linux B1 plan
`shale-api-plan` for stopped test app `shale-mcp-acceptance` in group `Shale`. No dedicated group,
plan, SQL server or SWA is required for this OFF-only phase. Production app `shale-api` and database
`Shale` remain off-limits. Sharing carries B1 contention, server firewall/admin and platform risks;
this cannot prove dedicated infrastructure isolation. No shared resource is cleanup-owned.
Use only existing approved network paths and owner headroom/abort thresholds. See
[concise next operator steps](../../../testing/azure-mcp-acceptance-next-steps.md) for exact metadata,
stopped-app runtime/time-zone checks, SQLCMD/SSMS investigation and remaining gates.

Exact proposed tuple (operator-supplied, not live route evidence):

```text
Origin   https://shale-mcp-acceptance.azurewebsites.net
Issuer   https://shale-mcp-acceptance.azurewebsites.net/oauth/mcp
Resource https://shale-mcp-acceptance.azurewebsites.net/mcp
```

The packet now copies [binding-inputs.sql](binding-inputs.sql), which supplies only these known
values/server/database. [binding-target.sql](binding-target.sql) additionally rejects another DB/
origin/issuer/resource. Java/SQL zones remain NULL, approvals zero and 28 samples absent; shared
binding validation/one-time insertion/immutable-conflict refusal is unchanged. Existing private
inputs are never overwritten: reconcile them explicitly and re-render/approve new hashes.

## Offline preparation and validation (PowerShell 7, JDK 21)

Run from the repository root in a private shell. Restrict the packet directory to the operator;
it will eventually contain credential-bearing SQL, fixture derivatives and private binding inputs.
No transcript, echo/input logging, shared drive or commit of `work/`. The private authoritative
fixture is **not in this checkout**; acquire it through the approved existing channel, never reconstruct it.
The test origin is supplied above; actual worker zone, SQL-zone mapping and live HTTPS route evidence
remain pending. The stopped app cannot provide a current running-JVM observation.

```powershell
$ErrorActionPreference = 'Stop'
$Packet = Join-Path (Get-Location) 'work/azure-mcp-acceptance'
# Use an approved private absolute path, without changing the fixture bytes/line endings.
$Fixture = Read-Host 'Private authoritative-candidate.sql path'
if ((Get-FileHash -LiteralPath $Fixture -Algorithm SHA256).Hash -cne
    '4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F') {
    throw 'STOP: authoritative fixture hash mismatch'
}
./build/azure-mcp-acceptance/Prepare-Packet.ps1 -PacketDirectory $Packet -AuthoritativeFixture $Fixture
# Offline test dependencies: Python 3, PowerShell 7 and JDK 21 on PATH.
python build/azure-mcp-acceptance/test_packet.py
if ($LASTEXITCODE -ne 0) { throw 'STOP: offline packet validation failed' }
git diff --check
```

Omit `-AuthoritativeFixture` only to review packaging: the fixture stage becomes a deliberate THROW,
including when an older fixture.sql exists. This is not a usable import packet. The source file hash
and parser are required again on **every** real render; generated SQL has a separate reviewed hash.
`Prepare-Packet.ps1` writes a new `rendered-<nonce>/` and `sha256.csv`, expands existing `:r` includes,
refuses reconnect/shell/database-switch directives and unresolved SQLCMD variables. It does not
connect or run SQL. Do not edit rendered migrations. Edit only approved private inputs, then re-render
and independently review/approve new hashes. Keep source commit, fixture hash, tool versions,
packet hashes, per-stage receipts and the exact signed-off destination/cleanup manifest.

## Later separately approved discovery and execution

These are **future commands, not tasks to execute for this PR**. First verify management-plane metadata:

```powershell
$Sub = 'cd334e90-1e97-4b68-b4e3-80bf7b438546'
az account show --subscription $Sub --query '{subscription:id,tenant:tenantId}' -o json
if ($LASTEXITCODE -ne 0) { throw 'STOP: subscription read failed' }
az sql db show --subscription $Sub --resource-group Shale --server shale --name ShaleMcpAcceptance `
  --query '{id:id,status:status,sku:sku,collation:collation,elasticPoolId:elasticPoolId}' -o json
if ($LASTEXITCODE -ne 0) { throw 'STOP: database inventory failed' }
```

Compare exact ID above, tenant, Online status, Standard/S0, collation and no elastic pool. Metadata
cannot establish emptiness/schema/users. The separately approved first SQL connection executes
`00-empty`: exact binary DB/server/collation, EngineEdition 5, neutral autocommit dbo, existing CONTROL
DATABASE visibility, no custom objects/users/roles/schemas, Stage 0 seven finding counts zero.
Unknown visibility is STOP, never an instruction to grant broader access or empty the database.

Use the existing validated-TLS **ODBC SQLCMD** with reviewed support for `-N -b -V 16 -X1 -x -f 65001`.
The operator has since reported Go SQLCMD v1.10.0 at `C:\Program Files\SqlCmd\sqlcmd.exe`
and successful explicit `ShaleMcpAcceptance` SQL-auth connectivity, with interactive password.
Earlier unavailable-database errors named other DBs and are not reproducible against this target;
original cause is undetermined. This does not validate the ODBC packet flags or certificate settings.
The wrapper resolves `sqlcmd` through PowerShell/PATH, so pin an approved ODBC executable in the
private operator shell and confirm `Get-Command sqlcmd` resolves it before any later `-Execute`.
Do not silently switch SQLCMD variants or weaken encryption if flags differ. The wrapper hardcodes
`-S tcp:shale.database.windows.net,1433 -d ShaleMcpAcceptance`; no destination parameter exists.
`-X1` blocks startup/environment scripts, `-x` disables variable substitution, password is interactive
(`-U` only), never `-P`, `-C`, `-e` or secret `-v`. A fresh process is used for each stage. Owner DDL's
20-second ordinary statement budget and existing V2 owner's 600-second installation budget
(`14-v2-install` only) are separate from restricted catalog/identity checks' five-second statement cap and the application's unchanged ten-second overall/five-second SQL budgets.

```powershell
# Set this to the exact newly rendered private directory, not a production path.
$Rendered = Read-Host 'Reviewed rendered directory'
$Owner = Read-Host 'Approved independent acceptance-only installation owner'
# Preview only: no SQLCMD process or connection.
./build/azure-mcp-acceptance/Invoke-Stage.ps1 -RenderedDirectory $Rendered -Stage 00-empty -OperatorUser $Owner

# ONLY AFTER separate exact-stage execution authorization; never loop over every stage.
$ApprovedHash = Read-Host 'Independently approved SHA256 for 00-empty'
./build/azure-mcp-acceptance/Invoke-Stage.ps1 -RenderedDirectory $Rendered -Stage 00-empty `
  -OperatorUser $Owner -ApprovedSHA256 $ApprovedHash `
  -Acknowledgement EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE -Execute
# The wrapper throws on SQLCMD failure. Inspect statuses/counts before authorizing the next stage.
```

The acknowledgement is a local execution guard, **not** an approval grant. Use a clean shell without
SQLCMD/app credential variables; do not reuse production credentials, settings, secrets or owner
identity in the app. No automatic retries/resume/regrant. Directly executing leaf SQL bypasses packet
hash/manifest review and is not the supported installation path.

## Ordered stage review and mandatory pauses

[stages.json](../../../../build/azure-mcp-acceptance/stages.json) is the exact ordered source manifest;
all original migration/module/catalog/receipt files stay unchanged. Owner/OFF fences run before/after
each source (with GO isolation so CREATE PROCEDURE batch rules remain valid).

| Stages | Existing operations and evidence required before advancing |
| --- | --- |
| `00-empty`, `01-fixture` | Approved metadata, then empty/Stage 0 checks; hash/parser-gated DDL, no data copied. |
| `02`–`08` | `phase3b1`, `phase3b2b`, `phase3b2c1`, `phase3b2c2`, `revocation`, `phase3b2d`, `phase3b3`, in that order, all controls OFF. |
| `09-normalize` | Private normalize.sql approval defaults 0. Exclusive fresh-fixture window only: remove candidate's three FILTERs exactly as existing helper; inspect definitions and non-target predicate hash against the approved fixture derivation. No application may serve during this intermediate state. |
| `10`–`12` | Fill private phase1r.json with independently reviewed strict/overlay/other-predicate SHA-256 values; re-render. Existing engine PREFLIGHT, APPLY, POSTCHECK, exact acceptance DB/collation. Blank inputs deliberately THROW. Never query/copy arbitrary live hashes to bless drift. |
| `13`–`15` | V2 prerequisite, `phase3b8-confinement.sql`, `verify.sql` and V2 confinement verifier. Require all finding counts zero and V2 source/catalog receipts. Never substitute V1 phase3b7. |
| `15a`–`15c` | Existing Oct 14 browser-auth modules; five contained foundation identities, synthetic tenant/user/Case rows. Imported DDL must supply session/instance/audit/remembered prerequisites. Missing prerequisite is STOP for review, not blind replay of production foundation/auth-cutover scripts. |
| **PAUSE before 16** | Separate dormant deployment/worker diagnostics approval, correct ordinary prerequisites and all ten app controls false (optional CIMD advertisement false); no app is deployed by this packet. Obtain actual-worker `SHALE_JVM_STARTUP`, same-worker default-fetcher metadata probe PASS and BindingTimeZoneProbe's 28 samples. Use the exact test origin above; current worker zone/SQL mapping remain unknown. The test app is stopped: do not start/deploy under this preparation task. |
| `16`–`18` | Fill only private binding-inputs.sql: actual SQL server identity, `ShaleMcpAcceptance`, the exact origin above, issuer=origin+/oauth/mcp, resource=origin+/mcp, actual Java/SQL zones, 28 rows and evidence/OFF confirmations. Existing preflight then separately approved one-time install then fresh postcheck. Approve bit defaults 0. Existing conflicting binding is STOP, no UPDATE/DELETE/rebind. Preserve receipt and tuple evidence. |
| `19`–`20` | PR3 authority inventory, confinement prepare, registry install, PR3 and confinement verifiers at their historical version. |
| `21`–`24` | Proof install/verify, OAuth install/verify, reviewer, onboarding. Controls remain OFF. |
| `25`–`26` | Existing C1 EXTERNAL_V2 then C2 DCR_V3 installer, preserving predecessor receipts/binding. Do not rerun historical V1/C1 verifiers against the successor. |
| `28`, `30` | Twelve separate capability users; owner Stage 0 + final version-selected DCR_V3 registry/authorization catalogs and installed-OFF/seed checks. Never enable controls to make catalog checks pass. |
| `31`, `32` | Actual `mcpacc_verifier` connection: packaged Case V2 + registry-dcr-v3 catalogs. Actual `mcpacc_reviewer`: authorization-dcr-v3 catalog/receipt accessor. These stages pin the username and omit owner-only fences. No impersonation or added reviewer grants. |
| `33`–`49` | Seventeen individual actual contained-user connections: authentication type 2, own-session database/SID proof, exact own-user/public/role token set, no admin authority; capability direct Case SELECT TOP(0) must fail specifically with 229. No SQL to production to prove denial. |

The owner-only final catalog pass is not restricted-principal acceptance. SQLCMD catalog/token
stages do not replace complete `AiCaseDataAttestation(V2,DCR_V3)`, `AiCimdCaseData(DCR_V3)` and runtime
provider checks at real Boot/admission under unchanged deadlines. Those remain later native/app
acceptance gates, with the runbook's stock Hermes CIMD/DCR/browser/audit/revocation/concurrency
matrix. Historical `VERIFIER_COMPONENT_READ` remains unresolved until actual supported native tests
pass; failure here means STOP, no grants/timeout adjustment or older-contract fallback.

## Identity and synthetic seed review

Private `identities-foundation.sql` and `identities.sql` start with approval=0/NULL secrets.
Only after separate approval, supply 5 then 12 independent passwords (at least 20 characters,
all distinct across **both** files and from the owner/production credentials), escape SQL literals,
protect files and re-render. No shared/master login, db_owner/db_datareader/db_datawriter,
whole-schema grant, ownership, impersonation or production secret. Owner metadata checks and
future real provider verification establish distinct SIDs across all 17 identities.

| Private contained user | Exact role (existing installer owns its grants/denials) |
| --- | --- |
| mcpacc_auth | No role; object EXECUTE on LookupAuthenticationCredentialV1 only |
| mcpacc_runtime | AiIntegrationRuntime + existing ordinary fixture object ACL listed in SQL |
| mcpacc_predecessor / mcpacc_verifier / mcpacc_bootstrap | AiCaseDataRuntime / AiCaseDataCatalogVerifier / AiIntegrationBootstrap |
| mcpacc_case / mcpacc_reviewer | AiExternalCaseRuntimeV2 / AiCimdAuthorizationCatalogV1 |
| mcpacc_publisher / mcpacc_approval / mcpacc_onboarding | AiApplicationPublisher / AiApplicationTenant / AiExternalOnboardingRuntimeV2 |
| mcpacc_browser / mcpacc_issuer / mcpacc_metadata | AiExternalOAuthBrowserV2 / AiExternalOAuthIssuerV2 / AiExternalMetadataPublisherV2 |
| mcpacc_resolver / mcpacc_neutral | AiExternalOAuthResolverV2 / AiExternalOAuthNeutralV2 |
| mcpacc_dcr_endpoint / mcpacc_dcr_metadata | AiDcrEndpointV3 / AiDcrMetadataReaderV2 |

Reviewer retains exactly VIEW DEFINITION, dependency-view SELECT and receipt-accessor EXECUTE.
No legacy CIMD identities, old AiDcrRegistrarV2 HTTP registrar or application DCR-administrator user.
All ordinary/predecessor/capability JDBC URLs later explicitly name `databaseName=ShaleMcpAcceptance`;
`shale.ai.data.database` matches; app contracts are Case V2 and external DCR_V3 even with DCR OFF.

Private seed.sql requires approval and four independently generated BCrypt hashes from the existing
Spring BCryptPasswordEncoder; use synthetic passwords held privately, never shell arguments/history.
Seeds tenants 7/8, admins 71/81, ordinary users 72/82; Cases 100→7, 200→8 and deleted 300→7, matching
existing fixture methods. Seed does not create WEB sessions, manual/external registrations, approvals,
consents, credential families or enabled tenant policies. Ordinary WEB login and manual-client
continuity preparation use existing governed flows only in a separately approved acceptance phase.

Audit compatibility: owner-only synthetic fixture setup follows the existing test seam and is recorded
in installation evidence, not fabricated as a human domain mutation. No audit schema/module/allowlist
changes. Later registration uses GlobalControlPlaneAuditLog; tenant approval/consent/lifecycle uses
EntityActionAuditLog; Case disclosure requires PHI/access/RETURNED/handoff/quota audit, transactionally.
Audit failure must withhold success; failure injection is excluded from this installation packet.

## STOP, containment and cleanup

STOP on missing execution approval, wrong metadata/name/ID/collation/edition, nonempty initial state,
unknown consumer/visibility, wrong fixture hash, unresolved input, incomplete ordinary dependencies,
wrong identity/permission/SID, controls ON, partial SQL state, failed Stage 0/catalog/finding/status,
conflicting binding, time-zone/egress failure, lost connection or uncertain commit. No next stage,
retry/reseal, permission/timeout increase, firewall change, production SQL or resource replacement.

Keep all ten app switches false and all five installed SQL controls zero; tenant policy absent.
Never activate DCR to complete installation. Stop/drain only a separately approved test app if a
later phase has deployed one; preserve evidence and separately investigate partial state. Re-render
only offline inputs; this does not authorize replaying committed stages. No SQL uninstall/down/reset.

Database was created for acceptance but cleanup is **not automatic**. Record ownership/expiry,
retention/backup/recovery requirements and whether to retain it for the remaining native campaign.
After acceptance/abort, retain sanitized hashes/status/count/timing and required audit/binding/receipt
history; obtain separate approval to revoke test authority and dispose of only the exact
ShaleMcpAcceptance resource. Never delete/change shared `shale-api-plan`, server `shale`, resource group `Shale`, production app
`shale-api`, production DB or either
existing copy/backup. A restoration/copy can change Azure physical-incarnation receipts: no receipt
refresh/adoption to make a restore pass; re-review a fresh fixture installation separately.
Destroy credential SQL copies, fixture derivatives and private test caches/logs under the approved
retention plan. Confirm cleanup by successful inventory, not a failed read. Any FAIL/OPEN keeps
Azure acceptance incomplete; no production activation or merge follows from an installed-OFF result.

## Historical preparation validation recorded for PR #1963

Offline on November 13, 2026: PowerShell 7.4.6, Temurin JDK 21.0.13+9, Maven 3.9.11.
No SQLCMD client, SQL connection, migration, user creation, deployment or Azure CLI discovery ran.
Tests install a PowerShell SQLCMD mock so an unexpected client invocation cannot reach Azure.

| Check | Result |
| --- | --- |
| `python build/azure-mcp-acceptance/test_packet.py` | 7 PASS: stage fences/order, blocked inputs/hash, source-byte preservation, contained-user templates, preview/approval refusals and restricted-principal packaging |
| `python -m unittest discover -s build/deployment-binding -p test_packet.py` | 5 PASS |
| `python -m unittest discover -s build/sql-confinement -p test_external_v2.py` | 6 PASS |
| `mvn -pl shale-data -am -Dtest=CaseReadVerificationSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test` | 6 PASS |
| Repository selector's `selected_maven_args` for all changed files | 160 PASS, no failures/errors/skips |
| `mvn test` critical reactor | 283 PASS, no failures/errors/skips |
| Local links/fences, `git diff --check`, unchanged existing migrations/resources | PASS |

Maven invocations also used `-B -s /workspace/work/acceptance-tools/settings.xml
-Dmaven.repo.local=/workspace/work/acceptance-tools/m2`, the task-local JDK/Maven and the session
CA truststore. Settings contain only the session proxy route, not SQL credentials. Selection used
`python build/test-selection/select_tests.py` with one `--path` for each changed file and
`--format json`; its informational all-tests escalation for previously unmapped tooling was not run.
No production Java was changed. Reproduce offline checks with your normal validated build setup;
keep every SQL/native opt-in absent.

**Unverified by design:** authoritative fixture import (private input absent), live empty target,
SQL syntax/catalog/permissions on native Azure, live origin routing/JVM-zone binding, real Boot/admission,
browser/stock-client flows and cleanup. These results validate preparation only, not installation
or Azure acceptance. The default fixture/Phase1R/normalization/binding/user/seed inputs remain blocked.

## Stage 09 evidence comparison (read-only, fresh fixture only)

The Stage 09 expected state combines the authoritative fixture with the exact predicate additions
from Stages 02-08, in `stages.json` order. The fixture alone has 77 non-target bindings; the unchanged
**116-binding requirement applies after composing the 39 migration additions**. The earlier
fixture-only count failure was an expected-state scope error, not a reason to lower that requirement.

The unchanged authoritative parser accepts 1,742 batches. All 80 fixture ADD PREDICATE clauses
are parsed under `sec.TenantFilter`; there is no missing policy, unsupported SQL or fixture hash
difference behind this count failure. The two full fixture function hashes also reproduce the
previously supplied strict/overlay observations, without establishing approval.

| Scope / policy | FILTER | BLOCK | Total |
| --- | ---: | ---: | ---: |
| Fixture, sec.TenantFilter | 63 | 17 | 80 |
| Three target FILTERs, sec.TenantFilter | 3 | 0 | 3 |
| Fixture non-target bindings, sec.TenantFilter | 60 | 17 | 77 |
| Stage 02 additions, sec.TenantFilter | 9 | 18 | 27 |
| Stage 03 additions, sec.TenantFilter | 1 | 2 | 3 |
| Stage 04 additions, sec.TenantFilter | 1 | 2 | 3 |
| Stages 05-07 additions | 0 | 0 | 0 |
| Stage 08 additions, sec.TenantFilter | 2 | 4 | 6 |
| Expected Stage 09 non-target bindings | 73 | 43 | 116 |
| Complete Stage 09 inventory, including targets | 76 | 43 | 119 |

Fixture BLOCK operations are eight AFTER INSERT, eight AFTER UPDATE and one BEFORE UPDATE.
Stages 02-08 add thirteen AFTER INSERT and thirteen AFTER UPDATE blocks. Their 39 additions
explain `77 + 39 = 116`; a count alone does not prove the corresponding live bindings or local hash.
Evidence comes from `stages.json` and the ADD clauses in `phase3b1.sql` (27), `phase3b2b.sql` (3),
`phase3b2c1.sql` (3) and `phase3b3.sql` (6), without executing any of them. The other three sources
are also checked; they add zero predicates. No predicate list is hardcoded or obtained from live SQL.

All seven reviewed source files are SHA-256 pinned in the utility; `.gitattributes` preserves their
LF bytes on Windows. The exact ordered manifest span between `01-fixture` and `09-normalize` must
match. Changed source bytes, reordered/missing/extra sources, unsupported security SQL, duplicate or
conflicting predicate slots/table definitions, or wrong counts fail closed. Do not refresh these
source fingerprints to make drift pass; any source change needs independent review of this extractor.
The parser supports only the reviewed literal `EXEC(N'ALTER SECURITY POLICY ...')` form and Stage
02's exact `SET @sql`/`@policy` concatenation. It resolves that stage's sole enabled, schema-bound
TenantFilter against the fixture's `sec.TenantFilter`. It interprets no general dynamic SQL and
never executes a source. ADD clauses reuse the fixture binding parser, including ordered arguments.

The new target tables are derived from CREATE TABLE declarations in the same reviewed sources,
checked for conflicts with the fixture/prior stages, and included in expected ownership checks.
Function bytes, dependencies/signatures, policies and all existing comparison guards remain in
force. Version 2 expected JSON records original fixture counts and each ordered migration's path,
SHA-256 and parsed addition count. Comparison rechecks those sources and the complete migration
bindings in the expected artifact. Version 1 artifacts fail; regenerate into a new private path.

Use [phase1r_evidence.py](../../../../build/azure-mcp-acceptance/phase1r_evidence.py)
and [Collect-Phase1R.sql](../../../../build/azure-mcp-acceptance/Collect-Phase1R.sql)
with the verified `d3780673acc68399af171e406fb3b481a899fe07` base plus this reviewed utility.
The utility does not execute SQL, edit private inputs, approve normalization or invoke stages.
The collector reads system catalogs only, never application/control rows, credential catalogs or
another database. It reuses `target.sql` but deliberately omits the row-reading `owner.sql` fence;
existing dbo/CONTROL visibility is checked directly. Keep the test app stopped, SQL/app controls
OFF under the existing Stage 00-08 evidence, and exclude other schema/policy maintenance throughout
collection and review. This comparison is point-in-time evidence, not a concurrency fence.

### Generate expected evidence offline

PowerShell 7, Python 3.9+ and JDK 21; run from the acceptance checkout root. Do not change the
fixture bytes. This calls the unchanged `ExportFixture` / `CaseReadVerificationSafety.schema`,
then parses the fixture bindings and the reviewed Stage 02-08 source additions. No replacement
importer or SQL runner is introduced. Keep all seven source files and `stages.json` unchanged.

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'C:\Eclipse\Workspace\shale-parent'
git merge-base --is-ancestor 'd3780673acc68399af171e406fb3b481a899fe07' HEAD
if ($LASTEXITCODE -ne 0) { throw 'STOP: verified base commit is not an ancestor' }
# Retain this exact utility commit and independently review its diff from the verified base.
git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'STOP: cannot identify utility checkout' }
$Fixture = 'C:\ShalePrivateEvidence\authoritative-fixture-20261028\authoritative-candidate.sql'
$Evidence = Join-Path (Get-Location) ('work/phase1r-review-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Evidence | Out-Null
$Expected = Join-Path $Evidence 'expected.json'
$Capture = Join-Path $Evidence 'metadata.hex'
$Report = Join-Path $Evidence 'report.json'
python build/azure-mcp-acceptance/phase1r_evidence.py expected --fixture $Fixture --out $Expected
if ($LASTEXITCODE -ne 0) { throw 'STOP: baseline generation failed' }
```

Expected (exit 0): authoritative parser PASS with 1,742 batches; fixture counts `80 / 3 / 77`;
per-stage addition counts `27, 3, 3, 0, 0, 0, 6`; Stage 09 counts `119 / 3 / 116`; then
baseline-generation PASS and expected-file SHA-256. Counts are total / target / non-target.
The JSON contains both complete UTF-16LE definitions and the composed predicate/policy/ownership
inventory, plus source provenance. The exact fixture hash is pinned in the existing exporter and
comparator. The fixture, expected JSON, captures and derived evidence remain private; never commit
them. Preserve the independently reviewed fixture/migration review references and the exact expected
file hash. Successful offline generation is not Azure evidence equivalence or normalization approval.

The two tenant functions must be the fixture's expression-only, single-int-parameter functions.
Any relational body, user-object dependency syntax, unsupported policy syntax or unexpected
non-target count stops baseline generation, rather than inventing dependencies or skipping clauses.
Dependencies must then be empty in the destination catalog. Byte differences, including line endings,
are failures; there is no normalization switch. Function mismatches give the byte offset, lengths,
hashes and next 32 bytes. Binding comparison unquotes identifiers but preserves case, ordered argument
tokens, literals, predicate kind and operation; it counts duplicates and compares every policy's state.

### Export once, then compare offline

Select the already approved **ODBC SQLCMD** executable (not the unreviewed Go replacement), existing
acceptance owner and existing approved route. The following is the sole SQL connection command;
it pins the FQDN/database, validates TLS and prompts privately for the existing SQL-auth password.
No `-P`, credential files, production connection, permission grant or owner-fence row read is used.

```powershell
$SqlcmdExe = Read-Host 'Approved Microsoft ODBC SQLCMD executable path'
$OwnerUser = Read-Host 'Existing acceptance SQL owner username'
if (Test-Path -LiteralPath $Capture) { throw 'STOP: use a new capture path' }
& $SqlcmdExe -S 'tcp:shale.database.windows.net,1433' -d 'ShaleMcpAcceptance' `
    -U $OwnerUser -N -b -V 16 -r 1 -l 30 -t 20 -x -X1 -f 65001 `
    -h -1 -W -w 4096 -i 'build/azure-mcp-acceptance/Collect-Phase1R.sql' -o $Capture
if ($LASTEXITCODE -ne 0) { throw 'STOP: export failed; preserve partial evidence, do not approve it' }
python build/azure-mcp-acceptance/phase1r_evidence.py compare `
    --expected $Expected --capture $Capture --report $Report
if ($LASTEXITCODE -ne 0) { throw 'STOP: evidence comparison failed; inspect report.json' }
Get-Content -LiteralPath $Report
```

The export uses bounded hex lines so definitions and ordered JSON cannot be silently truncated by
SQLCMD long-cell settings. A malformed, incomplete or banner-contaminated capture fails closed.
Each function now includes a catalog `dependencyCount` and a `dependencies` array. The collector
emits explicit `[]` only when a separate COUNT_BIG over that function's `sys.sql_expression_dependencies`
rows is zero; otherwise it preserves the dependency query's JSON without a null fallback. Comparison
requires an array and a matching non-negative integer count, and still rejects any dependency.
Null, missing, malformed or inconsistent evidence fails even if a supplied count is zero. Older
captures without this count are insufficient: preserve them and export to a new private capture
path using the patched collector when authorized. Never edit a capture to replace null with [].
Reports and baselines use create-new semantics. Preserve a failed capture/report; use new paths
for any separately justified recollection, never overwrite evidence or refresh hashes to bless drift.

PASS requires byte-for-byte complete function equality; engine-equivalent flags/signatures;
matching effective ownership, policy states, all 116 non-target bindings and the exact three target
FILTERs; no target BLOCK/alternate policy, Phase1R receipt or invocation object; and built-in int
target columns. Complete bindings compare policy, target, kind, operation, function and ordered
arguments; counts alone never pass. All missing/extra bindings, including duplicates, are reported explicitly. Database-
specific IDs are reconciled between raw and named catalog rows but excluded from portable binding
comparison. The report recomputes the unchanged engine's UTF-16LE `p.* ... FOR JSON PATH` fingerprint,
excluding all predicates on Users/auditLog/Statuses across all policies. This hash remains local
to this database incarnation and is not a fixture-portable hash.

STOP on any FAIL/unknown state, unsupported syntax, missing independent baseline provenance, wrong
destination/collation/engine, changed supplied observation, unknown consumers or competing DDL.
The collector intentionally cannot reconfirm control-table values or stopped-app state by reading
application rows; retain the existing separate evidence. A PASS is evidence equivalence only:
every report explicitly retains `normalizationApproved: false`.

### Conditional private input preparation after independent approval

Do not run this section until the report passes, an independent reviewer approves the evidence
and the no-consumer normalization window, and those approvals are recorded against the expected,
capture, report and utility-file hashes. No approval is inferred from matching supplied hashes.
The following steps are for the operator; the comparator never performs them.

```powershell
$r = Get-Content -LiteralPath $Report -Raw | ConvertFrom-Json
if ($r.outcome -cne 'PASS' -or @($r.discrepancies).Count -ne 0) { throw 'STOP: comparison did not pass' }
$strict = $r.observedFunctionHashes.fn_FilterByTenant
$overlay = $r.observedFunctionHashes.fn_FilterByTenantOrGlobal
$other = $r.observedOtherPredicatesHash
if ($strict -cne 'DCA29AC712CF8BE8BADBA8D62A324857EB73931C6B98367C656C6FD094738502' -or
    $overlay -cne '8A33CF8C28F999A1118D6B225E38930861F3774D55E568453716616F79FB0AC3' -or
    $other -cne '358C9071BFB302B94F48B64D5C92CD50DCF66AD5FC43402D2DF27557AC7DCD3E') {
    throw 'STOP: supplied Stage 09 observation changed; reconcile separately'
}
$Packet = Join-Path (Get-Location) 'work/azure-mcp-acceptance'
$normalize = Join-Path $Packet 'normalize.sql'
$template = [IO.File]::ReadAllText('docs/sql/ai-integration/azure-acceptance/normalize.sql')
if ([IO.File]::ReadAllText($normalize) -cne $template) {
    throw 'STOP: private normalize.sql differs from the unapproved source template'
}
$phasePath = Join-Path $Packet 'phase1r.json'
$oldPhase = Get-Content -LiteralPath $phasePath -Raw | ConvertFrom-Json -AsHashtable
$keys = @('ReviewedStrictHash','ReviewedOverlayHash','ReviewedOtherPredicatesHash')
if ($oldPhase.Count -ne 3 -or @($keys | Where-Object { -not $oldPhase.ContainsKey($_) -or $null -ne $oldPhase[$_] }).Count -ne 0) {
    throw 'STOP: private Phase1R inputs no longer have their unapproved defaults'
}
# Only now, with independent approvals already recorded, make these two private edits.
[ordered]@{ ReviewedStrictHash=$strict; ReviewedOverlayHash=$overlay; ReviewedOtherPredicatesHash=$other } |
    ConvertTo-Json | Set-Content -LiteralPath $phasePath -Encoding utf8NoBOM
[IO.File]::WriteAllText($normalize,
    $template.Replace('@NormalizeApproved bit=0','@NormalizeApproved bit=1'), [Text.UTF8Encoding]::new($false))
./build/azure-mcp-acceptance/Prepare-Packet.ps1 -PacketDirectory $Packet -AuthoritativeFixture $Fixture
```

Review the two private input changes and their SHA-256 values. Use the exact new rendered directory
printed by `Prepare-Packet.ps1`, never a guessed/latest directory. Do not edit rendered SQL:

```powershell
$Rendered = Read-Host 'Exact newly printed rendered directory'
$manifest = Import-Csv -LiteralPath (Join-Path $Rendered 'sha256.csv')
Get-FileHash -LiteralPath $normalize,$phasePath -Algorithm SHA256
foreach ($stage in @('09-normalize','10-phase1r-preflight','11-phase1r-apply','12-phase1r-postcheck')) {
    $file = Join-Path $Rendered ($stage + '.sql')
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
    $entry = @($manifest | Where-Object Stage -CEQ $stage)
    if ($entry.Count -ne 1 -or $entry[0].SHA256 -cne $hash) { throw "STOP: manifest mismatch: $stage" }
    [pscustomobject]@{ Stage=$stage; SHA256=$hash }
}
```

Independently inspect the four rendered files: Stage 09 removes only the three exact FILTERs;
10-12 contain the unchanged engine in PREFLIGHT/APPLY/POSTCHECK order with the exact reviewed
hashes, acceptance database and collation. Keep the source commit, fixture review reference,
physical-incarnation observation, evidence/input/rendered hashes and stage-specific approval record.
Any edit invalidates its hash approval. No stage, including PREFLIGHT, is executed by this procedure.
Leave `Invoke-Stage.ps1 -Execute`, deployment/start, SQL permissions, production and MCP activation
outside this evidence review. There is no merge or commit operation.

Audit compatibility: collection reads catalog metadata only; no sensitive application read or domain/
administrative mutation occurs. Evidence files are private operator records. No application audit
event, audit schema, permission or migration change is introduced.
