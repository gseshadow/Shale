# Shale Phase 3B.8 V2 production SQL packet — 2026-11-06

Prepared for review only. No SQL, code deployment, identity provisioning, setting change or activation
was executed. The companion [Invoke-ShaleV2Packet.ps1](Invoke-ShaleV2Packet.ps1) defaults to offline `Prepare`; every SQL stage
requires an explicit invocation. Supplying an authorization reference records an independently approved
change; it does not create approval. PowerShell 7 static parsing and the offline repository-location
checks passed; native Windows/ODBC SQL execution was not performed.

## Review disposition and prerequisites

The eight-document patch against `600723b92fe34731e1d6d25e598fc9e3cbfc2770` correctly accepts the
November 6 Stage B cutover/fence/effective-read PASS, Phase 1R installation/POSTCHECK PASS, listed
desktop/API smoke, function provenance, inventory hash, pool reuse and authoritative Case/RLS evidence.
Its new dated status takes precedence over the retained historical pending instructions. It changes no
SQL/source/test contract. Applicability/whitespace checks pass against the pinned document snapshot.
Accepted V2 local, client, fault, pool and repeatability evidence is reused. The Azure diagnostic stop
remains in force. Successful disposable V2 setup does not establish production install/native acceptance.

Already installed/completed; DO NOT EXECUTE again:

- Stage B `docs/sql/auth/permission-cutover/restrict-reads.sql`; Stage A is not an extra V2 installer.
- Phase 1R `docs/sql/phase1r/apply.sql`; no Phase 1R preflight/apply/rollback or provenance campaign.
- October 20 legacy-login retirement.
- AI `phase3b1.sql`, `phase3b2b.sql`, `phase3b2c1.sql`, `phase3b2c2.sql`, `revocation.sql`,
  `phase3b2d.sql`, `phase3b3.sql` under `docs/sql/ai-integration/`.
- Historical V1 `phase3b7-confinement.sql` must not run as a repair, fallback or upgrade.

Actual pre-execution blockers, not yet established by preparation:

1. A complete checkout containing baseline `600723b92fe34731e1d6d25e598fc9e3cbfc2770` in its ancestry
   and the exact three SQL byte hashes below; reviewed Microsoft ODBC
   SQLCMD 17/18 executable, Windows PowerShell 5.1 or PowerShell 7, and protected evidence directory.
   The supplied older checkout is unsuitable. The reviewed documentation patch may be applied without
   changing the pinned SQL bytes. Do not edit/regenerate SQL to satisfy a failed check.
2. Independently reviewed real server/database and authorized operator credential, network reachability
   and trusted TLS certificate. Target Azure SQL EngineEdition 5; actual database user must be `dbo`,
   complete catalog visibility, fresh autocommit connection, both context keys NULL. No application
   or restricted AI credential may be used as migration authority. Review actual connection routing.
3. Accepted installed-state change records and an uninstalled/unambiguous confinement destination.
   Existing V1, existing V2, partial objects or unexpected receipts STOP this install packet; reconcile
   existing state without replay/repair. The prerequisite must return zero findings with exit zero.
   The installer independently checks the authoritative Case/RLS and cumulative disclosure catalog/source.
4. Existing effective all-OFF App Service/global/tenant/integration posture evidenced by sanitized
   operator records; no concurrent schema, role/grant or AI provisioning changes during this window.
   Unknown settings need evidence, not an assumed OFF claim. Unexpected enabled state stops this packet.
5. Separate production V2 SQL installation authorization, approved maintenance/recovery window,
   responsible operator and agreed containment/recovery/PITR limitations. This chat grants preparation only.

Native restricted Azure catalog/read-only and disposable mutation/fault acceptance remain OPEN and
block AI readiness/activation. They are not technical prerequisites to this separately authorized,
owner-run, inactive SQL installation. Do not restart that investigation. Dedicated AI/verifier/bootstrap
credentials, independent secrets, immutable binding, complete live attestation, new JAR/hash/rollback,
public ingress/OAuth, operational/SDK/legal/security/tenant approvals are later AI/deployment gates;
they are not work to bundle into this SQL-only packet. An organization may require additional change
approval, but no approval or exception is inferred here.

## Pinned SQL and expected results

| Step | Script under `docs/sql/ai-integration/` | Exact file SHA-256 / expected result |
| --- | --- | --- |
| Read-only prerequisite | `phase3b8-confinement-prerequisite.sql` | `c0f8027403d41c1628fd156e558124d2fb90561988cf6a7f52af0b4152d07c36`; `CaseCatalogV2DriftFindingCount=0`, exit 0 |
| Separately authorized installation | `phase3b8-confinement.sql` | `7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8`; exit 0, transaction committed; no fixed PASS line is emitted |
| Read-only cumulative verification | `verify.sql` | `9d5b31bed3d0f1ef8df5d7c16addfcb9fd3c2f0fd6328a23e583b9d334225fec`; V2 branch `ConfinementCatalogDriftFindingCount=0`, exit 0 through the final source check |

Keep the file hash distinct from the catalog prerequisite identity
`6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a` and the installed source
receipt `Shale.AiCaseDataV2.Source=60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`.
`Shale.AiCaseDataV2` is a destination-specific catalog receipt; preserve the actual value, never copy
a disposable receipt. Both V2 receipts must exist on `dbo.AiCaseDataInvocation`, with no V1 ambiguity.
Zero findings printed before a later source failure do not pass the command. Inspect the full log and exit.
The V2 cumulative branch returns before old global/tenant output, so the packet checks OFF independently.

## PowerShell invocation

The packet is versioned at `work/readiness-deliverables/Invoke-ShaleV2Packet.ps1` alongside this document.
From the root of a complete checkout containing the merged readiness/packet PR, use the commands below.
The script accepts descendants of the reviewed baseline only when all three SQL files retain their
exact reviewed bytes; it records both baseline and current HEAD. No old-baseline checkout or script
copy outside the repository is required. Create an access-controlled evidence directory outside the
repository using the existing operator process. Populate reviewed nonsecret parameters; no password belongs in
PowerShell variables, history, process arguments, transcripts or evidence. Clear inherited SQLCMDPASSWORD
through the approved secret process before running; SQLCMD prompts for the operator password on each
new connection. Do not start a transcript. Validate ODBC SQLCMD identity offline before SQL execution.

```powershell
$ErrorActionPreference = 'Stop'
$repo = (Get-Location).Path
$packet = Join-Path $repo 'work/readiness-deliverables/Invoke-ShaleV2Packet.ps1'
$parseTokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($packet, [ref]$parseTokens, [ref]$parseErrors) | Out-Null
if ($parseErrors.Count -ne 0) { throw 'PowerShell parser errors; STOP before any use.' }
$evidence = Read-Host 'Existing protected evidence directory'

# Safe offline preparation: no SQL client or database is invoked.
& $packet -Step Prepare -RepositoryRoot $repo -EvidenceDirectory $evidence

# Populate these only from the reviewed production change record.
$sql = @{
    RepositoryRoot = $repo
    EvidenceDirectory = $evidence
    SqlcmdPath = (Read-Host 'Reviewed Microsoft ODBC sqlcmd.exe full path')
    Server = (Read-Host 'Reviewed production tcp:server-FQDN,port')
    Database = (Read-Host 'Reviewed production database')
    OperatorUser = (Read-Host 'Approved migration operator SQL user, resolving to dbo')
    LoginTimeoutSeconds = [int](Read-Host 'Reviewed positive SQLCMD login timeout seconds')
    QueryTimeoutSeconds = [int](Read-Host 'Reviewed positive SQLCMD query timeout seconds')
}

# Only when the read-only production step is authorized; then STOP and review its log.
& $packet @sql -Step Prerequisite
```

Separate installation action, ONLY after the prerequisite is accepted and actual production SQL
installation authorization has been granted; preparation authorization is insufficient:

```powershell
$approvedChange = Read-Host 'Reference to independently approved production V2 SQL change'
if ([string]::IsNullOrWhiteSpace($approvedChange)) { throw 'No approval reference; STOP' }
& $packet @sql -Step Install -ProductionAuthorizationReference $approvedChange
```

After a confirmed successful installation, run the cumulative postcheck within the approved change:

```powershell
& $packet @sql -Step Verify
# Inspect every log and exit code. Preserve receipts and OFF/settings evidence. STOP here.
```

The companion issues these exact native commands, each as a fresh process, immediately checking
`$LASTEXITCODE`; its additional read-only owner/state/OFF/receipt guards are preserved in the evidence:

```powershell
# $connection contains -S/-d/-U from the reviewed parameters, -N -b -V 16,
# reviewed positive -l/-t, -X1 (no unsafe startup script), -x (no variable expansion).
& $SqlcmdPath @connection -i (Join-Path $root 'docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql') -o $log
# Separate authorization required before this command:
& $SqlcmdPath @connection -i (Join-Path $root 'docs/sql/ai-integration/phase3b8-confinement.sql') -o $log
& $SqlcmdPath @connection -i (Join-Path $root 'docs/sql/ai-integration/verify.sql') -o $log
```

The three lines above document the script's commands; use the staged companion, not those lines as
one unattended batch. No SQLCMD variable inputs or Phase 1R includes are needed. No `-C`, `-P`, `-E`,
connection trust override, automatic retry or timeout increase. SQLCMD administrative time bounds
do not alter application/JDBC timeouts. No application principal is provisioned by this packet.

## Identity, secret and configuration boundary

For this SQL-only change, use only the separately authorized operator credential resolving to `dbo`.
The installer creates empty dbo-owned `AiCaseDataRuntime`/`AiCaseDataCatalogVerifier` roles and confined
objects/permissions/receipts; it does not create users/logins, assign real consumers or insert a binding.

For later readiness, the AI runtime and verifier on Azure SQL Database must be independent contained
SQL users (`type=S`, authentication_type=DATABASE/2), own user/public plus only their respective role,
no server roles/master-login mapping, no broad data/control/ownership/impersonation/grant authority.
The runtime receives only the six reviewed EXECUTEs: `AiDataAuthenticateV1`, `AiDataSearchCasesV1`,
`AiDataGetCaseOverviewV1`, `AiDataRecordCaseFailureV1`, `AiDataFinishCaseQuotaV1`,
`AiDataAuthorizeCaseHandoffV1`. The verifier keeps only the reviewed definition/dependency/four-reference-
table authorities. Azure does not require SQL Server/MI server metadata grants. Bootstrap remains a
separately governed, distinct identity, also distinct from ordinary auth/runtime and AI/verifier.

Later JDBC destinations require explicit database, encrypted certificate/hostname-validated TLS,
independent secrets/consumers and pools. Preserve ordinary auth/runtime secrets, signing secret,
session cutover boundary, TLS and CORS. Configure exact `shale.ai.data.*`, `.verifier.*`,
`shale.ai.oauth.bootstrap.*`, PRODUCTION identity/canonical HTTPS origin and explicit
`shale.ai.data.contract-version=V2` only in a separately governed configuration stage. Do not append
URL overrides for TLS/authentication/MARS/timeouts. The immutable Id=1 `case-read-v1` deployment binding
must use actual issuer/resource/database and reviewed existing Java/SQL time-zone mapping; never guess UTC.
No identities, new secrets, binding writes or App Service configuration changes are part of this packet.

All eight effective application switches must remain false, including external App Service/JVM overrides:

```properties
shale.ai.enabled=false
shale.ai.mcp.enabled=false
shale.ai.mcp.case-data-composition-enabled=false
shale.ai.mcp.search-cases-enabled=false
shale.ai.mcp.get-case-overview-enabled=false
shale.ai.oauth.composition-enabled=false
shale.ai.oauth.authorization-enabled=false
shale.ai.oauth.exchange-enabled=false
```

`AiProductCapability.Enabled=0`, tenant policies missing/disabled, no new integration/grant/session/
credential authority; readiness remains `disabled`. Preserve
`logging.level.io.modelcontextprotocol.modern.JsonRpc=OFF` and no payload/bind/credential logging.
This is the required OFF posture, not a claim that preparation freshly observed live settings.

## Evidence, failure stops and recovery

Preserve the original eight-document patch/hash and baseline, three exact file hashes, accepted Stage B/
Phase 1R/predecessor/retirement records and provenance, operator/change authorization and recovery plan,
SQLCMD version/target/time bounds, UTC start/end, all command exit codes/full protected output, both actual
V2 receipts and effective sanitized OFF/settings evidence before/after. Retain accepted test evidence;
do not collect credential hashes, bearer/proof/keys, passwords, PHI, raw SQL binds or application rows.

STOP on any missing authorization/input/visibility, wrong source/destination/user/context, existing or
ambiguous confinement state, any drift/source/receipt/ownership finding, SQL exception, timeout,
disconnect, lock failure, nonzero exit, missing expected result or unexpected enabled capability.
Do not continue to install after a failed prerequisite or to readiness after successful owner verification.
No automatic retry, rehash, grant widening, schema/module rewrite, V1 fallback or receipt refresh.

Before commit, the installer uses `XACT_ABORT`, one owned transaction and CATCH rollback/rethrow.
A timeout/disconnect/lost acknowledgement still leaves an uncertain observed outcome: preserve evidence,
stop, and separately authorize read-only state reconciliation before any retry. Never assume nonzero exit
means nothing committed. An installer exit zero alone is provisional until cumulative verification passes.

After commit, there is no reviewed V2 down/uninstall script in this packet. Leave additive SQL installed,
retain all history/receipts and all capabilities OFF. Do not roll back Stage B/Phase 1R or remove the seven
predecessors. If containment is needed, separately authorize disabling dedicated AI consumers/connection
authority and draining any AI pools; never restore shared-runtime fallback. This packet creates none of
those consumers. Corrective DDL/uninstall/permission changes require a separate reviewed change.

Azure PITR/restore is an independently approved database-wide recovery operation with ordinary-write/data-
loss/routing implications, not a V2 rollback command. Azure physical database identity can change, invalidating
the accepted Phase 1R identity receipt; destination-bound V2/binding evidence also needs review. Do not
copy/reissue receipts or assume an old artifact/receipt makes a restored destination ready. No restore,
uninstall, Phase 1R rollback, code deployment or activation command is supplied.

## Validation scope

Exact original SQL Git blobs/file hashes and the existing eight-document patch applicability were checked
offline. Companion command destinations, allowed scripts, source pins, OFF controls and read-only guards
were reviewed. PowerShell 7.4.19 native Parser.ParseFile reported zero errors; offline Prepare was checked
from the documented repository location on the baseline and a descendant commit, with source-tampering
and unrelated-history rejection checks. SQLCMD was not invoked in those checks. Native Windows/ODBC
execution and live SQL acceptance are not claimed. No accepted runtime/SQL test campaign was repeated,
no stopped Azure work resumed and no production connection occurred.
