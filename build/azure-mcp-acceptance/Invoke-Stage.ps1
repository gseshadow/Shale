#Requires -Version 7.0
<# Future authorized execution only. Default is a nonconnecting command preview. One stage/process. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RenderedDirectory,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][string]$OperatorUser,
    [string]$ApprovedSHA256,
    [string]$Acknowledgement,
    [switch]$Execute,
    [switch]$Supervised,
    [switch]$ReadOnlyCheck
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($OperatorUser -notmatch '^[A-Za-z][A-Za-z0-9_@.\-]{0,127}$') { throw 'STOP: invalid operator principal spelling.' }
$stages = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
. (Join-Path $PSScriptRoot 'Supervision.ps1')
$allowed = @($stages | ForEach-Object name)
if ($ReadOnlyCheck) { $Stage = 'reconcile'; $stages += [pscustomobject]@{name='reconcile'; sources=@('Reconcile.sql')}; $allowed += 'reconcile' }
if ($Stage -cnotin $allowed) { throw 'STOP: unknown stage; no arbitrary SQL path allowed.' }
$selected = $stages | Where-Object name -CEQ $Stage
if (($selected.PSObject.Properties.Name -contains 'user') -and $OperatorUser -cne $selected.user) { throw 'STOP: this stage requires its exact independent contained user.' }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$dir = [IO.Path]::GetFullPath($RenderedDirectory)
if (-not $dir.StartsWith((Join-Path $repo 'work') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'STOP: use private checkout work/ rendered packet.' }
$file = Join-Path $dir ($Stage + '.sql')
[void](Assert-PrivatePath $file)
$actualHash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
if ($Supervised) { [void](Get-StageHash $dir $Stage) }
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
if ($Supervised) {
    $sqlcmdArgs += @('-s','|','-W','-w','65535')
    Write-Host "Preparing SQLCMD authentication for $OperatorUser. Do not type until PASSWORD INPUT READY appears; raw native output is suppressed. Ctrl+C stops and requires reconciliation."
    $exitCode = $null
    $captured = @()
    $numbers = @()
    $failure = 'PROCESS_START_OR_INTERRUPTED'
    try {
        # Resolve an application explicitly: functions/aliases cannot replace the
        # independently reviewed ODBC executable in a supervised session.
        $client = Get-Command sqlcmd -CommandType Application -ErrorAction Stop
        $capture = & (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -Executable $client.Source -Arguments $sqlcmdArgs -RequireConsole
        $captured = @($capture.Lines)
        if ($capture.ExitCode -is [int]) { $exitCode = $capture.ExitCode }
        $numbers = @(Get-SafeSqlErrorNumbers $captured)
        if ($capture.FailureCode) {
            if ($capture.FailureCode -cin @('CONSOLE_REQUIRED','PROCESS_START_OR_INTERRUPTED','OUTPUT_LIMIT')) { $failure=$capture.FailureCode }
            throw 'Transport unavailable'
        }
        if ($null -eq $exitCode) { throw 'Unknown process outcome' }
        if ($numbers.Count) { $failure='SQL_ERROR'; throw 'SQL error' }
        if ($exitCode -ne 0) { $failure='PROCESS_EXIT'; throw 'Process failed' }
        if (-not $capture.PasswordPromptObserved -or -not $capture.PasswordInputReadyShown) { $failure='NATIVE_PROMPT_UNCONFIRMED'; throw 'Native prompt not confirmed' }
        $failure = 'INVALID_RESULTS'
        $safe = Read-SafeResult $captured $Stage @($selected.sources).Count
        [pscustomobject]@{ Stage=$Stage; ExitCode=$exitCode; Result=$safe; SqlErrorNumbers=@(); FailureCode='' }
    } catch {
        [pscustomobject]@{ Stage=$Stage; ExitCode=$exitCode; Result='FAILED_RESULT_OR_PROCESS'; SqlErrorNumbers=$numbers; FailureCode=$failure }
    } finally { $captured=$null; $capture=$null }

    return
}
& sqlcmd @sqlcmdArgs
if ($LASTEXITCODE -ne 0) { throw "STOP: stage failed (exit $LASTEXITCODE). Do not continue or retry; reconcile commit outcome." }
Write-Output "SQLCMD completed stage=$Stage; inspect all finding counts/statuses before authorizing the next stage."
