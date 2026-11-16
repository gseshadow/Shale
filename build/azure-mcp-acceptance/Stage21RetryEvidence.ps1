# Shared offline validation for the single latest-attempt recovery. No connection.
. (Join-Path $PSScriptRoot 'ProofModuleDiagnostics.ps1')
. (Join-Path $PSScriptRoot 'Checkpoint24.ps1')
function Read-Stage21JsonText([string]$Text) {
    try { return ($Text | ConvertFrom-Json) }
    catch { throw 'STOP: invalid recovery JSON; contents suppressed.' }
}
function Read-Stage21JsonFile([string]$Path) {
    try { return (Read-Stage21JsonText ([IO.File]::ReadAllText((Assert-PrivatePath $Path)))) }
    catch { throw 'STOP: invalid recovery JSON file; contents suppressed.' }
}
function Get-Stage21RetryHashes([string]$Directory,[object[]]$Stages) {
    @($Stages[0..29] | ForEach-Object { [pscustomobject]@{Stage=$_.name;Hash=(Get-StageHash $Directory $_.name)} })
}
function Assert-Stage21RetryPlan($Plan,[string]$Directory,[object[]]$Stages,[string]$BaselineHash,[int]$Depth=0) {
    if ($Plan.Version -eq 2) { Assert-Stage21RetryPlanV2 $Plan $Directory $Stages $BaselineHash $Depth; return }
    $packet=Assert-PrivatePath ([IO.Path]::GetDirectoryName($Directory))
    $old=Assert-PrivatePath $Plan.PreviousDirectory
    $new=Assert-PrivatePath $Plan.NewDirectory
    if ($Plan.Version -ne 1 -or $Plan.Ticket -cnotmatch '^[a-f0-9]{32}$' -or
        [IO.Path]::GetDirectoryName($old) -cne $packet -or [IO.Path]::GetDirectoryName($new) -cne $packet) { throw 'STOP: invalid retry plan destination.' }
    $selection=Assert-PrivatePath (Join-Path $packet 'fast-track-recovery.json')
    if ((Get-FileHash -LiteralPath $selection -Algorithm SHA256).Hash -cne $Plan.SelectionHash) { throw 'STOP: original recovery selection changed.' }
    $selected=Read-Stage21JsonFile $selection
    $parent=Assert-PrivatePath $Plan.ParentJournal
    if ($selected.Journal -cne $parent -or (Get-FileHash -LiteralPath $parent -Algorithm SHA256).Hash -cne $Plan.ParentHash) { throw 'STOP: failed parent journal changed.' }
    # Only the original recovered24 fork can be the parent. No recursive retry chain.
    $first=Read-Stage21JsonText ([IO.File]::ReadLines($parent) | Select-Object -First 1)
    if ((Read-Stage21JsonText $first.Payload).Kind -cne 'baseline') { throw 'STOP: retry-of-retry is not supported.' }
    $history=Get-FastTrackHistory $parent $Stages $BaselineHash $old ($Depth+1)
    if (-not $history.Recovered -or -not $history.Pending -or $history.Next -ne 24 -or
        $history.GroupStart -ne 24 -or $history.GroupEnd -ne 25 -or $history.FailedStage -cne '21-proof' -or
        -not $history.Checkpoint24Snapshot) { throw 'STOP: parent must be the unresolved single Stage 21 attempt at checkpoint24.' }
    if ($Plan.CheckpointSnapshot -cne $history.Checkpoint24Snapshot) { throw 'STOP: prior checkpoint evidence changed.' }
    if ((Get-StageHash $old '21-proof') -cne 'F3D4E8EB41054B0E5A5EF4A608497B6936DB0D889E77A1C99D9BE72A6FD5827E') { throw 'STOP: unsupported failed Stage 21 packet.' }
    $expected=[IO.File]::ReadAllText((Join-Path $old '21-proof.sql'))
    foreach ($source in @('docs/sql/ai-integration/universal-pr4a-proof.sql','docs/sql/ai-integration/universal-pr4a-proof-verify.sql')) {
        $body=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../'+$source)))
        if ($expected.IndexOf($body,[StringComparison]::Ordinal) -lt 0 -or $expected.IndexOf($body,[StringComparison]::Ordinal) -ne $expected.LastIndexOf($body,[StringComparison]::Ordinal)) { throw 'STOP: old proof source shape differs.' }
        $expected=$expected.Replace($body,(Add-ProofModuleDiagnosticsV1 $body $source))
    }
    if ([IO.File]::ReadAllText((Join-Path $new '21-proof.sql')) -cne $expected) { throw 'STOP: only reviewed diagnostic instrumentation may change Stage 21.' }
    $newHashes=@(Get-Stage21RetryHashes $new $Stages)
    $current=@(Get-Stage21RetryHashes $Directory $Stages)
    if (($newHashes | ConvertTo-Json -Compress) -cne ($Plan.StageHashes | ConvertTo-Json -Compress) -or
        ($current | ConvertTo-Json -Compress) -cne ($Plan.StageHashes | ConvertTo-Json -Compress)) { throw 'STOP: retry rendering changed.' }
    foreach ($i in 0..29) {
        if ($i -ne 24 -and (Get-StageHash $old $Stages[$i].name) -cne $newHashes[$i].Hash) { throw 'STOP: a stage other than the pending Stage 21 changed.' }
    }
    $reportPath=Assert-PrivatePath $Plan.FailureReport
    if ((Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash -cne $Plan.FailureReportHash) { throw 'STOP: failure report changed.' }
    $report=Read-Stage21JsonFile $reportPath
    if ($report.Server -cne 'shale.database.windows.net' -or $report.Database -cne 'ShaleMcpAcceptance' -or
        $report.Outcome -cne 'STOPPED' -or $report.FailureStage -cne '21-proof' -or $report.CompletedCount -ne 24 -or
        $report.CompletedThrough -cne '20-pr3' -or $report.ExitCode -ne 16 -or @($report.SqlErrorNumbers).Count -ne 1 -or
        $report.SqlErrorNumbers[0] -ne 59400 -or @($report.Assertions).Count -ne 1 -or $report.Assertions[0] -cne 'PROOF_MODULE_DRIFT' -or
        @($report.SessionFailures).Count -ne 0 -or @($report.AttemptedGroupStages).Count -ne 1 -or $report.AttemptedGroupStages[0] -cne '21-proof') { throw 'STOP: report does not establish the supported failed attempt.' }
    $sql=Assert-PrivatePath $Plan.SqlPath
    if ((Get-FileHash -LiteralPath $sql -Algorithm SHA256).Hash -cne $Plan.SqlHash -or
        [IO.File]::ReadAllText($sql) -cne (New-Checkpoint24Sql $Plan.Ticket $old)) { throw 'STOP: read-only probe changed.' }
}
function Read-Stage21RetryCapture([string]$Path,$Plan) {
    $path=Assert-PrivatePath $Path
    $lines=@([IO.File]::ReadAllLines($path) | ForEach-Object { $_.Trim().TrimStart([char]0xFEFF).Trim() } | Where-Object { $_ })
    $snapshot=Read-SafeResult $lines 'reconcile' 1
    if ($snapshot -cne $Plan.CheckpointSnapshot) { throw 'STOP: current database observation differs from the verified checkpoint24.' }
    $markers=@($lines | Where-Object { $_.Contains('RECOVERY24_OK:') })
    if ($markers.Count -ne 1 -or $markers[0] -cne ('RECOVERY24_OK:'+$Plan.Ticket) -or $lines[-1] -cne $markers[0]) { throw 'STOP: incomplete or wrong retry probe capture.' }
    $sources=@($lines | Where-Object { $_.StartsWith('ACCEPTANCE_SOURCE_OK:') })
    if ($sources.Count -ne 1 -or $sources[0] -cne 'ACCEPTANCE_SOURCE_OK:18-binding-postcheck:1' -or 'BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED' -cnotin $lines) { throw 'STOP: committed binding verification missing.' }
    [void](Read-SafeResult (@($lines | Where-Object { -not $_.StartsWith('ACCEPTANCE_SOURCE_OK:') })+@('ACCEPTANCE_SOURCE_OK:20-pr3:1','ACCEPTANCE_SOURCE_OK:20-pr3:2','ACCEPTANCE_SOURCE_OK:20-pr3:3')) '20-pr3' 3)
    return $snapshot
}
function Assert-Stage21RetryReceipt([string]$Path,[string]$Hash,[string]$Directory,[object[]]$Stages,[string]$BaselineHash,[int]$Depth=0) {
    $path=Assert-PrivatePath $Path
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $Hash) { throw 'STOP: retry receipt changed.' }
    $receipt=Read-Stage21JsonFile $path
    if (-not $receipt.VerifiedNativeExitZero -or -not $receipt.NoOtherSessionsOrWriters -or [string]::IsNullOrWhiteSpace($receipt.ReviewReference)) { throw 'STOP: retry evidence lacks independent review.' }
    $planPath=Assert-PrivatePath $receipt.PlanPath
    if ((Get-FileHash -LiteralPath $planPath -Algorithm SHA256).Hash -cne $receipt.PlanHash) { throw 'STOP: retry plan changed.' }
    $plan=Read-Stage21JsonFile $planPath
    Assert-Stage21RetryPlan $plan $Directory $Stages $BaselineHash $Depth
    $capture=Assert-PrivatePath $receipt.Capture
    if ((Get-FileHash -LiteralPath $capture -Algorithm SHA256).Hash -cne $receipt.CaptureHash) { throw 'STOP: retry observation changed.' }
    [void](Read-Stage21RetryCapture $capture $plan)
}

. (Join-Path $PSScriptRoot 'Stage21RetryEvidenceV2.ps1')
