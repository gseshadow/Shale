# Preparation only by default. This file does not grant production authorization.
# Use Microsoft ODBC sqlcmd 17/18 on Windows PowerShell 5.1 or PowerShell 7.
[CmdletBinding()]
param(
    [ValidateSet('Prepare','Prerequisite','Install','Verify')][string]$Step = 'Prepare',
    [Parameter(Mandatory=$true)][string]$RepositoryRoot,
    [Parameter(Mandatory=$true)][string]$EvidenceDirectory,
    [string]$SqlcmdPath,
    [string]$Server,
    [string]$Database,
    [string]$OperatorUser,
    [int]$LoginTimeoutSeconds,
    [int]$QueryTimeoutSeconds,
    [string]$ProductionAuthorizationReference
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$baseline = '600723b92fe34731e1d6d25e598fc9e3cbfc2770'
$expected = [ordered]@{
    'docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql' = 'c0f8027403d41c1628fd156e558124d2fb90561988cf6a7f52af0b4152d07c36'
    'docs/sql/ai-integration/phase3b8-confinement.sql' = '7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8'
    'docs/sql/ai-integration/verify.sql' = '9d5b31bed3d0f1ef8df5d7c16addfcb9fd3c2f0fd6328a23e583b9d334225fec'
}
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$head = & git -C $root rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Repository HEAD unavailable; stop.' }
& git -C $root merge-base --is-ancestor $baseline HEAD
if ($LASTEXITCODE -ne 0) { throw 'The reviewed baseline must be an ancestor of this checkout; stop. Use a complete checkout containing the reviewed PR, not the older supplied checkout.' }
foreach ($relativePath in $expected.Keys) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $root $relativePath) -Algorithm SHA256).Hash
    if ($actual -ne $expected[$relativePath]) { throw "SQL byte mismatch: $relativePath; stop. Do not normalize, regenerate or repair receipts." }
}
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (-not (Test-Path -LiteralPath $evidence -PathType Container)) { throw 'Create a dedicated access-controlled evidence directory first.' }
$run = Join-Path $evidence ($Step + '-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
[ordered]@{ Baseline=$baseline; CheckoutHead="$head".Trim(); Step=$Step; Utc=(Get-Date).ToUniversalTime().ToString('o'); SqlSha256=$expected } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $run 'source.json') -Encoding UTF8
if ($Step -eq 'Prepare') { Write-Output "OFFLINE source pins PASS. No SQL invoked. Evidence: $run"; return }

# Before ANY SQL stage: separately approved target/read access; reviewed ODBC client,
# ordinary change records; effective all-OFF settings; recovery/window/owner review.
foreach ($value in @($SqlcmdPath,$Server,$Database,$OperatorUser)) {
    if ([string]::IsNullOrWhiteSpace($value)) { throw 'Supply reviewed SQLCMD executable, server, database and migration-operator user; no defaults.' }
}
if ($LoginTimeoutSeconds -le 0 -or $QueryTimeoutSeconds -le 0) { throw 'Supply reviewed positive SQLCMD login/query timeout bounds.' }
if (-not (Test-Path -LiteralPath $SqlcmdPath -PathType Leaf)) { throw 'SQLCMD executable missing.' }
if (Test-Path Env:SQLCMDPASSWORD) { throw 'Remove inherited SQLCMDPASSWORD through the approved secret process; this packet requires the interactive password prompt.' }
if ($Step -eq 'Install' -and [string]::IsNullOrWhiteSpace($ProductionAuthorizationReference)) {
    throw 'STOP: separate production V2 installation authorization is required. A supplied reference records it; it does not create or validate approval.'
}
$connection = @('-S',$Server,'-d',$Database,'-U',$OperatorUser,'-N','-b','-V','16',
    '-l',"$LoginTimeoutSeconds",'-t',"$QueryTimeoutSeconds",'-X1','-x')
$dbLiteral = $Database.Replace("'", "''")
$guard = @"
SET NOCOUNT ON;
IF DB_NAME()<>N'$dbLiteral' OR SERVERPROPERTY('EngineEdition')<>5
 THROW 59300,'Reviewed Azure SQL destination required.',1;
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59300,'Fresh dbo autocommit NULL-context session required.',1;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59300,'Complete owner catalog visibility required.',1;
IF (SELECT COUNT_BIG(*) FROM dbo.AiProductCapability)<>1
 OR EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled<>0)
 OR EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE Enabled<>0)
 THROW 59300,'Global/tenant SQL capability must remain OFF; do not change it in this packet.',1;
SELECT CAST(0 AS int) AS SqlEnabledCapabilityFindingCount;
"@
$before = @'
IF OBJECT_ID(N'dbo.AiCaseDataInvocation') IS NOT NULL
 OR DATABASE_PRINCIPAL_ID(N'AiCaseDataRuntime') IS NOT NULL
 OR DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier') IS NOT NULL
 OR EXISTS(SELECT 1 FROM sys.extended_properties WHERE name IN
 (N'Shale.AiCaseDataV1',N'Shale.AiCaseDataV1.Source',N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source'))
 THROW 59300,'Existing or ambiguous confinement state: STOP, no installer replay or V1 adoption.',1;
'@
$after = @'
IF OBJECT_ID(N'dbo.AiCaseDataInvocation',N'U') IS NULL
 OR (SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND minor_id=0
 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name IN(N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source'))<>2
 THROW 59300,'Both V2 receipts required before cumulative verification; no V1 dispatch.',1;
IF EXISTS(SELECT 1 FROM sys.extended_properties WHERE name IN(N'Shale.AiCaseDataV1',N'Shale.AiCaseDataV1.Source'))
 THROW 59300,'Unexpected V1 receipt: STOP; no upgrade or fallback.',1;
IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND minor_id=0
 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseDataV2.Source'),0x)
 <>0x60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891
 THROW 59300,'Unexpected V2 source receipt.',1;
SELECT name AS ReceiptName,CONVERT(varchar(64),CONVERT(varbinary(32),value),2) AS ReceiptHash
 FROM sys.extended_properties WHERE class=1 AND minor_id=0 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation')
 AND name IN(N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source') ORDER BY name;
'@
$encoding = New-Object System.Text.UTF8Encoding($false)
$guardPath = Join-Path $run 'owner-off-state-check.sql'
[IO.File]::WriteAllText($guardPath, $guard + "`n" + $(if ($Step -eq 'Verify') { $after } else { $before }), $encoding)

function Invoke-CheckedSqlFile([string]$Path, [string]$Label) {
    $log = Join-Path $run ($Label + '.sqlcmd.log')
    # No -P/-C/-E; password comes only from SQLCMD's own interactive prompt.
    # -X1 suppresses unsafe SQLCMD startup commands; -x disables variable substitution.
    & $SqlcmdPath @connection -i $Path -o $log
    $code = $LASTEXITCODE
    "$Label exit=$code" | Add-Content -LiteralPath (Join-Path $run 'exit-codes.txt')
    if ($code -ne 0) { throw "$Label failed or outcome uncertain; STOP. Preserve $run. No automatic retry, repair or cleanup." }
    Write-Output "$Label exit=0; inspect full protected log: $log"
}
Invoke-CheckedSqlFile $guardPath 'owner-off-state-check'
switch ($Step) {
    'Prerequisite' {
        Invoke-CheckedSqlFile (Join-Path $root 'docs/sql/ai-integration/phase3b8-confinement-prerequisite.sql') 'v2-prerequisite'
        Write-Output 'Require CaseCatalogV2DriftFindingCount=0 and no SQL error. STOP before installation pending separate authorization.'
    }
    'Install' {
        # Operator must already have accepted the prerequisite log and real production authorization.
        $ProductionAuthorizationReference | Set-Content -LiteralPath (Join-Path $run 'authorization-reference.txt')
        Invoke-CheckedSqlFile (Join-Path $root 'docs/sql/ai-integration/phase3b8-confinement.sql') 'v2-install'
        Write-Output 'Installer exit=0 is provisional. Next: separately run Step Verify; no readiness/activation.'
    }
    'Verify' {
        Invoke-CheckedSqlFile (Join-Path $root 'docs/sql/ai-integration/verify.sql') 'v2-cumulative-verify'
        # V2 verification returns early; verify SQL OFF state again independently.
        Invoke-CheckedSqlFile $guardPath 'receipts-and-sql-off-after'
        Write-Output 'Require ConfinementCatalogDriftFindingCount=0, both exact V2 receipts, OFF evidence and every exit=0. SQL preparation only; STOP.'
    }
}
