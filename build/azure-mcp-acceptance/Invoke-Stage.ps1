#Requires -Version 7.0
<# Future authorized execution only. Default is a nonconnecting command preview. One stage/process. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RenderedDirectory,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][string]$OperatorUser,
    [string]$ApprovedSHA256,
    [string]$Acknowledgement,
    [switch]$Execute
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($OperatorUser -notmatch '^[A-Za-z][A-Za-z0-9_@.\-]{0,127}$') { throw 'STOP: invalid operator principal spelling.' }
$stages = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
$allowed = @($stages | ForEach-Object name)
if ($Stage -cnotin $allowed) { throw 'STOP: unknown stage; no arbitrary SQL path allowed.' }
$selected = $stages | Where-Object name -CEQ $Stage
if (($selected.PSObject.Properties.Name -contains 'user') -and $OperatorUser -cne $selected.user) { throw 'STOP: this stage requires its exact independent contained user.' }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$dir = [IO.Path]::GetFullPath($RenderedDirectory)
if (-not $dir.StartsWith((Join-Path $repo 'work') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'STOP: use private checkout work/ rendered packet.' }
$file = Join-Path $dir ($Stage + '.sql')
$actualHash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
# No caller-configurable destination, TLS override, raw args, query, timeout or authentication fallback.
$querySeconds = if ($selected.PSObject.Properties.Name -contains 'user') { '5' } elseif ($Stage -ceq '14-v2-install') { '600' } else { '20' }
$sqlcmdArgs = @('-S','tcp:shale.database.windows.net,1433','-d','ShaleMcpAcceptance',
    '-U',$OperatorUser,'-N','-b','-V','16','-r','1','-l','30','-t',$querySeconds,'-x','-X1','-f','65001','-i',$file)
if (-not $Execute) {
    Write-Output 'PREVIEW ONLY: SQLCMD; destination shale.database.windows.net / ShaleMcpAcceptance; validated TLS; password prompt.'
    Write-Output "Stage=$Stage SHA256=$actualHash"
    exit 0
}
if ($Acknowledgement -cne 'EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE' -or $ApprovedSHA256 -notmatch '^[a-fA-F0-9]{64}$' -or $ApprovedSHA256 -ine $actualHash) {
    throw 'STOP: exact stage authorization acknowledgement and independently approved SQL hash required.'
}
# -X1 disables startup scripts/environment SQLCMD variables. Reject ambient auth/input anyway.
foreach ($item in Get-ChildItem Env:) {
    if ($item.Name -match '^(SQLCMD|SQLCONNSTR_|SQLAZURECONNSTR_|SPRING_DATASOURCE|SHALE_(APP|RT|AUTH|SERVER|SUGGESTION))' -and $item.Value) {
        throw 'STOP: use a clean operator shell without application/SQLCMD credentials or startup overrides.'
    }
}
& sqlcmd @sqlcmdArgs
if ($LASTEXITCODE -ne 0) { throw "STOP: stage failed (exit $LASTEXITCODE). Do not continue or retry; reconcile commit outcome." }
Write-Output "SQLCMD completed stage=$Stage; inspect all finding counts/statuses before authorizing the next stage."
