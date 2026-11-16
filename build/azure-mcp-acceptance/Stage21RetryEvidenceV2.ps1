# Resolves immutable selection successors; existing selectors/journals are not edited.
function Get-SelectedStage21Recovery([string]$Packet) {
    $selection=Assert-PrivatePath (Join-Path $Packet 'fast-track-stage21-recovery.json')
    if (-not (Test-Path -LiteralPath $selection)) { $selection=Assert-PrivatePath (Join-Path $Packet 'fast-track-recovery.json') }
    $seen=@()
    for ($depth=0;$depth -lt 5;$depth++) {
        $row=Read-Stage21JsonFile $selection
        $journal=Assert-PrivatePath $row.Journal
        if ([IO.Path]::GetDirectoryName($journal) -cne $Packet -or $journal -cin $seen) { throw 'STOP: invalid recovery selection ancestry.' }
        $seen+=$journal
        $successors=@()
        foreach ($item in Get-ChildItem -LiteralPath $Packet -Filter 'fast-track-stage21-recovery-*.json') {
            $path=Assert-PrivatePath $item.FullName
            $next=Read-Stage21JsonFile $path
            if ($next.ParentJournal -ceq $journal) { $successors += [pscustomobject]@{Path=$path;Record=$next} }
        }
        if ($successors.Count -gt 1) { throw 'STOP: parallel recovery selections refused.' }
        if (-not $successors.Count) { return [pscustomobject]@{Path=$selection;Journal=$journal} }
        $hash=(Get-FileHash -LiteralPath $journal -Algorithm SHA256).Hash
        if ($successors[0].Record.ParentHash -cne $hash -or [IO.Path]::GetFileName($successors[0].Path) -cne ('fast-track-stage21-recovery-'+$hash+'.json')) { throw 'STOP: recovery parent changed, including valid-prefix truncation.' }
        $selection=$successors[0].Path
    }
    throw 'STOP: excessive recovery selection ancestry.'
}
function Assert-Stage21RetryPlanV2($Plan,[string]$Directory,[object[]]$Stages,[string]$BaselineHash,[int]$Depth) {
    $packet=Assert-PrivatePath ([IO.Path]::GetDirectoryName($Directory))
    $old=Assert-PrivatePath $Plan.PreviousDirectory
    $new=Assert-PrivatePath $Plan.NewDirectory
    if ($Plan.Ticket -cnotmatch '^[a-f0-9]{32}$' -or [IO.Path]::GetDirectoryName($old) -cne $packet -or [IO.Path]::GetDirectoryName($new) -cne $packet) { throw 'STOP: invalid pre-throw recovery plan.' }
    $selection=Assert-PrivatePath $Plan.ParentSelection
    if ((Get-FileHash -LiteralPath $selection -Algorithm SHA256).Hash -cne $Plan.SelectionHash) { throw 'STOP: parent selection changed.' }
    $selected=Read-Stage21JsonFile $selection
    $parent=Assert-PrivatePath $Plan.ParentJournal
    if ($selected.Journal -cne $parent -or (Get-FileHash -LiteralPath $parent -Algorithm SHA256).Hash -cne $Plan.ParentHash) { throw 'STOP: failed parent journal changed.' }
    $first=Read-Stage21JsonText ([IO.File]::ReadLines($parent) | Select-Object -First 1)
    $baseline=Read-Stage21JsonText $first.Payload
    if ($baseline.Kind -cne 'stage21-baseline') { throw 'STOP: expected the previously recovered Stage 21 journal.' }
    $priorReceipt=Read-Stage21JsonFile $baseline.Evidence
    $priorPlan=Read-Stage21JsonFile $priorReceipt.PlanPath
    if ($priorPlan.Version -ne 1) { throw 'STOP: only one v1 diagnostic failure can use this recovery.' }
    $history=Get-FastTrackHistory $parent $Stages $BaselineHash $old ($Depth+1)
    if (-not $history.Recovered -or -not $history.Pending -or $history.Next -ne 24 -or $history.GroupStart -ne 24 -or $history.GroupEnd -ne 25 -or $history.FailedStage -cne '21-proof' -or $Plan.CheckpointSnapshot -cne $history.Checkpoint24Snapshot) { throw 'STOP: supported unresolved Stage 21 checkpoint not established.' }
    $legacy=Get-LegacyProofPacket ([IO.File]::ReadAllText((Join-Path $old '21-proof.sql')))
    $expected=$legacy
    foreach ($source in @('docs/sql/ai-integration/universal-pr4a-proof.sql','docs/sql/ai-integration/universal-pr4a-proof-verify.sql')) {
        $body=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../'+$source)))
        $expected=$expected.Replace($body,(Add-ProofModuleDiagnostics $body $source))
    }
    if ([IO.File]::ReadAllText((Join-Path $new '21-proof.sql')) -cne $expected) { throw 'STOP: only reviewed pre-THROW instrumentation may change Stage 21.' }
    $newHashes=@(Get-Stage21RetryHashes $new $Stages); $current=@(Get-Stage21RetryHashes $Directory $Stages)
    if (($newHashes | ConvertTo-Json -Compress) -cne ($Plan.StageHashes | ConvertTo-Json -Compress) -or ($current | ConvertTo-Json -Compress) -cne ($Plan.StageHashes | ConvertTo-Json -Compress)) { throw 'STOP: recovery rendering changed.' }
    foreach ($i in 0..29) { if ($i -ne 24 -and (Get-StageHash $old $Stages[$i].name) -cne $newHashes[$i].Hash) { throw 'STOP: previously completed or unrelated SQL changed.' } }
    $reportPath=Assert-PrivatePath $Plan.FailureReport
    if ((Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash -cne $Plan.FailureReportHash) { throw 'STOP: failed report changed.' }
    $report=Read-Stage21JsonFile $reportPath
    if ($report.Server -cne 'shale.database.windows.net' -or $report.Database -cne 'ShaleMcpAcceptance' -or $report.Outcome -cne 'STOPPED' -or $report.CompletedCount -ne 24 -or $report.CompletedThrough -cne '20-pr3' -or $report.FailureStage -cne '21-proof' -or $report.ExitCode -ne 16 -or @($report.SqlErrorNumbers).Count -ne 1 -or $report.SqlErrorNumbers[0] -ne 59400 -or @($report.Assertions).Count -ne 1 -or $report.Assertions[0] -cne 'PROOF_MODULE_DRIFT' -or @($report.SessionFailures).Count -ne 0 -or @($report.AttemptedGroupStages).Count -ne 1 -or $report.AttemptedGroupStages[0] -cne '21-proof' -or $report.RollbackConfirmed -ne $false -or $report.ProofDiagnosticsValid -ne $true) { throw 'STOP: unsupported historical failure report.' }
    $names=@([IO.File]::ReadAllLines((Join-Path $PSScriptRoot '../sql-confinement/cimd-v1-modules.sha256')) | ForEach-Object { $_.Split('=')[0] })
    $flags=@('OBJECT_MISSING','DEFINITION_UNAVAILABLE','OWNERSHIP','ANSI_NULLS','QUOTED_IDENTIFIER','EXECUTION_CONTEXT','DEFINITION_HASH')
    if (@($report.ProofModules).Count -ne 16) { throw 'STOP: historical all-absent observation not established.' }
    $seen=@()
    foreach ($module in $report.ProofModules) {
        if ($module.Module -cnotin $names -or $module.Module -cin $seen -or @($module.FailedProperties).Count -ne 7 -or @(Compare-Object $flags @($module.FailedProperties) -CaseSensitive).Count -ne 0) { throw 'STOP: historical all-absent observation differs.' }
        $seen+=$module.Module
    }
    $sql=Assert-PrivatePath $Plan.SqlPath
    if ((Get-FileHash -LiteralPath $sql -Algorithm SHA256).Hash -cne $Plan.SqlHash -or [IO.File]::ReadAllText($sql) -cne (New-Checkpoint24Sql $Plan.Ticket $old)) { throw 'STOP: read-only probe changed.' }
}
