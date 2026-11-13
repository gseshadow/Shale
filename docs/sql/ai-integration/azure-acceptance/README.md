# ShaleMcpAcceptance installation packet

**PREPARATION ONLY. No Azure/SQL connection, migration, credential provisioning, deployment,
firewall change or activation is authorized by this PR.** Source baseline: latest fetched
`codex/latest`, `bf320bb352ebae6932a22b666c8ed9285eaed77a` (PR #1962).

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

The logical server is shared with production. This cannot meet the original runbook's dedicated
nonproduction-server/no-shared-production-dependency requirement. The operator's fresh-DB confirmation
supports preparing this exact packet, not waiving that evidence limit. Later execution needs explicit
shared-server risk acceptance: existing firewall/network/admin/configuration and service dependencies
remain shared. If the existing approved route is unavailable, STOP; do not change it. S0 is not a
capacity guarantee. No production SQL inspection is needed or permitted.

## Offline preparation and validation (PowerShell 7, JDK 21)

Run from the repository root in a private shell. Restrict the packet directory to the operator;
it will eventually contain credential-bearing SQL, fixture derivatives and private binding inputs.
No transcript, echo/input logging, shared drive or commit of `work/`. The private authoritative
fixture is **not in this checkout**; acquire it through the approved existing channel, never reconstruct it.
The nonproduction origin and actual worker zone are also pending; blocked stages are intentional.

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
| **PAUSE before 16** | Separate dormant deployment/worker diagnostics approval, correct ordinary prerequisites and all ten app controls false (optional CIMD advertisement false); no app is deployed by this packet. Obtain actual-worker `SHALE_JVM_STARTUP`, same-worker default-fetcher metadata probe PASS and BindingTimeZoneProbe's 28 samples. Canonical test origin/zone still unknown: do not proceed until available. |
| `16`–`18` | Fill only private binding-inputs.sql: actual SQL server identity, `ShaleMcpAcceptance`, approved nonproduction origin, issuer=origin+/oauth/mcp, resource=origin+/mcp, actual Java/SQL zones, 28 rows and evidence/OFF confirmations. Existing preflight then separately approved one-time install then fresh postcheck. Approve bit defaults 0. Existing conflicting binding is STOP, no UPDATE/DELETE/rebind. Preserve receipt and tuple evidence. |
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
ShaleMcpAcceptance resource. Never delete the shared server, resource group, production DB or either
existing copy/backup. A restoration/copy can change Azure physical-incarnation receipts: no receipt
refresh/adoption to make a restore pass; re-review a fresh fixture installation separately.
Destroy credential SQL copies, fixture derivatives and private test caches/logs under the approved
retention plan. Confirm cleanup by successful inventory, not a failed read. Any FAIL/OPEN keeps
Azure acceptance incomplete; no production activation or merge follows from an installed-OFF result.

## Preparation validation recorded for this PR

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
SQL syntax/catalog/permissions on native Azure, actual origin/JVM-zone binding, real Boot/admission,
browser/stock-client flows and cleanup. These results validate preparation only, not installation
or Azure acceptance. The default fixture/Phase1R/normalization/binding/user/seed inputs remain blocked.
