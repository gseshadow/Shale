#Requires -Version 7.0
<# Offline prepare/finalize only. Resolves one evidenced failed single-stage
   attempt into a NEW journal; existing histories/selections are never modified. #>
[CmdletBinding(DefaultParameterSetName='Prepare')]
param(
    [Parameter(Mandatory)][string]$RenderedDirectory,
    [Parameter(Mandatory,ParameterSetName='Prepare')][string]$PreviousRenderedDirectory,
    [Parameter(Mandatory,ParameterSetName='Prepare')][string]$FailureReport,
    [Parameter(Mandatory,ParameterSetName='Prepare')][string]$ReviewDirectory,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$PlanPath,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$Capture,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$ReviewReference,
    [Parameter(Mandatory,ParameterSetName='Finalize')][switch]$VerifiedNativeExitZero,
    [Parameter(Mandatory,ParameterSetName='Finalize')][switch]$NoOtherSessionsOrWriters
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'FastTrack.ps1')
$dir=Assert-PrivatePath $RenderedDirectory
$packet=Assert-PrivatePath ([IO.Path]::GetDirectoryName($dir))
$lockPath=Assert-PrivatePath (Join-Path $packet 'supervised.lock')
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {

    $stages=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    $baseline=Get-TextSHA256 (($stages[0..17] | ForEach-Object { $_.name+':'+(Get-StageHash $dir $_.name) }) -join "`n")
    if ($PSCmdlet.ParameterSetName -ceq 'Prepare') {
        $old=Assert-PrivatePath $PreviousRenderedDirectory
        $selected=Get-SelectedStage21Recovery $packet
        $selection=$selected.Path
        $parent=Assert-PrivatePath $selected.Journal
        $history=Get-FastTrackHistory $parent $stages $baseline $old
        $failure=Assert-PrivatePath $FailureReport
        $review=Assert-PrivatePath $ReviewDirectory
        if (Test-Path -LiteralPath $review) { throw 'STOP: preserve existing review; choose a new directory.' }
        [void][IO.Directory]::CreateDirectory($review)
        $ticket=[guid]::NewGuid().ToString('N')
        $sqlPath=Join-Path $review 'checkpoint24.sql'
        Write-FastTrackFile $sqlPath (New-Checkpoint24Sql $ticket $old)
        $plan=[ordered]@{Version=2;ParentSelection=$selection;Ticket=$ticket;PreviousDirectory=$old;NewDirectory=$dir;ParentJournal=$parent;ParentHash=(Get-FileHash -LiteralPath $parent -Algorithm SHA256).Hash;SelectionHash=(Get-FileHash -LiteralPath $selection -Algorithm SHA256).Hash;CheckpointSnapshot=$history.Checkpoint24Snapshot;FailureReport=$failure;FailureReportHash=(Get-FileHash -LiteralPath $failure -Algorithm SHA256).Hash;StageHashes=@(Get-Stage21RetryHashes $dir $stages);SqlPath=$sqlPath;SqlHash=(Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash}
        Assert-Stage21RetryPlan ([pscustomobject]$plan) $dir $stages $baseline
        Write-FastTrackFile (Join-Path $review 'plan.json') ($plan | ConvertTo-Json -Depth 6)
        Write-Output 'Prepared one read-only probe and evidence plan. No SQL executed. Existing histories preserved.'
        return
    }
    if (-not $VerifiedNativeExitZero -or -not $NoOtherSessionsOrWriters -or [string]::IsNullOrWhiteSpace($ReviewReference)) { throw 'STOP: independent native-exit and no-writer review required.' }
    $planFile=Assert-PrivatePath $PlanPath
    $plan=Read-Stage21JsonFile $planFile
    Assert-Stage21RetryPlan $plan $dir $stages $baseline
    $selected=Get-SelectedStage21Recovery $packet
    if ($selected.Journal -cne $plan.ParentJournal -or $selected.Path -cne $plan.ParentSelection) { throw 'STOP: another successor is already selected.' }
    $capturePath=Assert-PrivatePath $Capture
    [void](Read-Stage21RetryCapture $capturePath $plan)
    $review=Assert-PrivatePath ([IO.Path]::GetDirectoryName($planFile))
    $receiptPath=Join-Path $review ('receipt-'+[guid]::NewGuid().ToString('N')+'.json')
    $receipt=[ordered]@{PlanPath=$planFile;PlanHash=(Get-FileHash -LiteralPath $planFile -Algorithm SHA256).Hash;Capture=$capturePath;CaptureHash=(Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash;VerifiedNativeExitZero=$true;NoOtherSessionsOrWriters=$true;ReviewReference=$ReviewReference}
    Write-FastTrackFile $receiptPath ($receipt | ConvertTo-Json -Depth 5)
    $receiptHash=(Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash
    Assert-Stage21RetryReceipt $receiptPath $receiptHash $dir $stages $baseline
    $record=[ordered]@{Kind='stage21-baseline';Index=0;Hash=$baseline;Evidence=$receiptPath;EvidenceHash=$receiptHash}
    $payload=$record | ConvertTo-Json -Compress
    $journal=Join-Path $packet ('fast-track-stage21-'+[guid]::NewGuid().ToString('N')+'.jsonl')
    Write-FastTrackFile $journal (([ordered]@{Payload=$payload;Hash=(Get-TextSHA256 (('0'*64)+$payload))} | ConvertTo-Json -Compress)+"`n")
    [void](Get-FastTrackHistory $journal $stages $baseline $dir)
    $selectionPath=Assert-PrivatePath (Join-Path $packet ('fast-track-stage21-recovery-'+$plan.ParentHash+'.json'))
    Write-FastTrackFile $selectionPath (([ordered]@{Journal=$journal;ParentJournal=$plan.ParentJournal;ParentHash=$plan.ParentHash} | ConvertTo-Json -Compress))
    Write-Output "Selected NEW journal: $journal; verified checkpoint24; next=21-proof. No SQL executed."
} catch {
    if ($_.Exception.Message.StartsWith('STOP:')) { throw $_.Exception.Message }
    throw 'STOP: invalid recovery input; raw exception suppressed.'
} finally { $lock.Dispose() }
