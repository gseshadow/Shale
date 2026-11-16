#Requires -Version 7.0
<# Offline only. Prepare read-only SQL for independent operator execution, or
   create a NEW journal fork from reviewed captures. Never connects or edits history. #>
[CmdletBinding(DefaultParameterSetName='Prepare')]
param(
    [Parameter(Mandatory)][string]$RenderedDirectory,
    [Parameter(Mandatory,ParameterSetName='Prepare')][string]$ReviewDirectory,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$PlanPath,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$CaptureA,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$CaptureB,
    [Parameter(Mandatory,ParameterSetName='Finalize')][string]$ReviewReference,
    [Parameter(Mandatory,ParameterSetName='Finalize')][switch]$VerifiedNativeExitZero,
    [Parameter(Mandatory,ParameterSetName='Finalize')][switch]$NoOtherSessionsOrWriters
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'FastTrack.ps1')
$dir=Assert-PrivatePath $RenderedDirectory
$packet=Assert-PrivatePath ([IO.Path]::GetDirectoryName($dir))
$journal=Assert-PrivatePath (Join-Path $packet 'fast-track.jsonl')
function New-Checkpoint24Sql([string]$ticket) {
        $templates=Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance'
        $sql=":on error exit`n"+[IO.File]::ReadAllText((Join-Path $templates 'target.sql'))+"`n"+[IO.File]::ReadAllText((Join-Path $templates 'owner.sql'))+"`nGO`n"
        $sql+="SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET QUOTED_IDENTIFIER ON; SET NUMERIC_ROUNDABORT OFF;`nGO`n"
        # Reuse unchanged read-only receipt/source/permission validators, never an installer.
        foreach ($source in @('universal-pr3-verify.sql','universal-pr3-confinement-verify.sql')) {
            $sql+=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../docs/sql/ai-integration/'+$source)))+"`nGO`n"
        }
        # Stage 18's historical V2 verifier predates PR3's new role permissions.
        # Its exact bytes are replaced ONLY in this derived read-only probe by the
        # current PR3-aware verifier above. Binding inputs/assertions remain exact;
        # the completed stage file/hash is never changed or re-executed as a stage.
        $binding=[IO.File]::ReadAllText((Join-Path $dir '18-binding-postcheck.sql'))
        $legacy=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/phase3b8-confinement-verify.sql'))
        $at=$binding.IndexOf($legacy,[StringComparison]::Ordinal)
        if ($at -lt 0 -or $at -ne $binding.LastIndexOf($legacy,[StringComparison]::Ordinal)) { throw 'STOP: historical postcheck shape changed; independent reconciliation required.' }
        $sql+=$binding.Remove($at,$legacy.Length)+"`nGO`n"
        $sql+=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Stage21Checkpoint.sql'))+"`nGO`n"
        $sql+=([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Reconcile.sql')).Replace('PRINT @AcceptanceSnapshot;','SELECT @AcceptanceSnapshot AS AcceptanceResult;'))+"`nGO`n"
        $sql+="SELECT CAST('RECOVERY24_OK:$ticket' AS varchar(96)) AS RecoveryStatus;`nGO`n"
        return $sql
}
$lock=[IO.File]::Open((Join-Path $packet 'supervised.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    $selectionPath=Assert-PrivatePath (Join-Path $packet 'fast-track-recovery.json')
    if (Test-Path -LiteralPath $selectionPath) { throw 'STOP: a recovery fork is already selected; preserve it and use only that fork.' }
    $stages=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    $baseline=Get-TextSHA256 (($stages[0..17] | ForEach-Object { $_.name+':'+(Get-StageHash $dir $_.name) }) -join "`n")
    $history=Get-FastTrackHistory $journal $stages $baseline $dir
    if (-not $history.Pending -or $history.Next -ne 24 -or $history.GroupStart -ne 19 -or $history.GroupEnd -ne 30 -or $history.FailedStage -cne '21-proof') {
        throw 'STOP: only the intact failed 16-26 group with verified 24-stage prefix is supported; reconcile other states separately.'
    }
    $original=[IO.File]::ReadAllText($journal)
    $journalHash=(Get-FileHash -LiteralPath $journal -Algorithm SHA256).Hash
    $hashes=@($stages[0..29] | ForEach-Object { [ordered]@{Stage=$_.name;Hash=(Get-StageHash $dir $_.name)} })
    if ($PSCmdlet.ParameterSetName -ceq 'Prepare') {
        $review=Assert-PrivatePath $ReviewDirectory
        if (Test-Path -LiteralPath $review) { throw 'STOP: review directory must be new.' }
        [void][IO.Directory]::CreateDirectory($review)
        $ticket=[guid]::NewGuid().ToString('N')
        $sql=New-Checkpoint24Sql $ticket
        $sqlPath=Join-Path $review 'checkpoint24.sql'
        Write-FastTrackFile $sqlPath $sql
        $plan=[ordered]@{Version=1;Checkpoint=24;Ticket=$ticket;Journal=$journal;JournalHash=$journalHash;RenderedDirectory=$dir;StageHashes=$hashes;SqlPath=$sqlPath;SqlHash=(Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash}
        Write-FastTrackFile (Join-Path $review 'plan.json') ($plan | ConvertTo-Json -Depth 6)
        Write-Output 'Prepared read-only checkpoint SQL and plan. No SQL executed. Follow stage21-recovery.md before finalization.'
        return
    }
    if (-not $VerifiedNativeExitZero -or -not $NoOtherSessionsOrWriters -or [string]::IsNullOrWhiteSpace($ReviewReference)) { throw 'STOP: independent provenance/session review required.' }
    $planFile=Assert-PrivatePath $PlanPath
    $plan=Get-Content -LiteralPath $planFile -Raw | ConvertFrom-Json
    if ($plan.Version -ne 1 -or $plan.Checkpoint -ne 24 -or $plan.Ticket -cnotmatch '^[a-f0-9]{32}$' -or $plan.Journal -cne $journal -or $plan.JournalHash -cne $journalHash -or $plan.RenderedDirectory -cne $dir) { throw 'STOP: stale or wrong recovery plan.' }
    if (($plan.StageHashes | ConvertTo-Json -Compress) -cne ($hashes | ConvertTo-Json -Compress)) { throw 'STOP: packet changed since preparation.' }
    $sqlPath=Assert-PrivatePath $plan.SqlPath
    if ((Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash -cne $plan.SqlHash -or [IO.File]::ReadAllText($sqlPath) -cne (New-Checkpoint24Sql $plan.Ticket)) { throw 'STOP: reviewed probe changed.' }
    $captures=@(); $snapshot=$null
    foreach ($path in @($CaptureA,$CaptureB)) {
        $path=Assert-PrivatePath $path
        if ($captures.Count -and $path -ceq $captures[0].Path) { throw 'STOP: two separate captures required.' }
        $lines=@([IO.File]::ReadAllLines($path) | ForEach-Object { $_.Trim().TrimStart([char]0xFEFF).Trim() } | Where-Object { $_ })
        $current=Read-SafeResult $lines 'reconcile' 1
        if ($snapshot -and $snapshot -cne $current) { throw 'STOP: database changed between observations.' }
        $snapshot=$current
        $markers=@($lines | Where-Object { $_.Contains('RECOVERY24_OK:') })
        if ($markers.Count -ne 1 -or $markers[0] -cne ('RECOVERY24_OK:'+$plan.Ticket) -or $lines[-1] -cne $markers[0]) { throw 'STOP: incomplete or wrong checkpoint evidence; retain headers and use -W for capture.' }
        foreach ($finding in @('ApplicationCatalogDriftFindingCount','ConfinementCatalogDriftFindingCount','RegistryConfinementFindingCount')) {
            # Captures use headers: the existing parser checks exact named zero rows.
            if ($finding -cnotin $lines) { throw 'STOP: missing predecessor findings.' }
        }
        $sourceMarkers=@($lines | Where-Object { $_.StartsWith('ACCEPTANCE_SOURCE_OK:') })
        if ($sourceMarkers.Count -ne 1 -or $sourceMarkers[0] -cne 'ACCEPTANCE_SOURCE_OK:18-binding-postcheck:1' -or 'BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED' -cnotin $lines) { throw 'STOP: committed binding postcheck missing.' }
        # Reuse full finding validation, supplying only its source-completion protocol locally.
        [void](Read-SafeResult (@($lines | Where-Object { -not $_.StartsWith('ACCEPTANCE_SOURCE_OK:') })+@('ACCEPTANCE_SOURCE_OK:20-pr3:1','ACCEPTANCE_SOURCE_OK:20-pr3:2','ACCEPTANCE_SOURCE_OK:20-pr3:3')) '20-pr3' 3)
        $captures += [ordered]@{Path=$path;Hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
    }
    $review=Assert-PrivatePath ([IO.Path]::GetDirectoryName($planFile))
    $receiptPath=Join-Path $review ('receipt-'+[guid]::NewGuid().ToString('N')+'.json')
    $receipt=[ordered]@{Checkpoint=24;PlanPath=$planFile;PlanHash=(Get-FileHash -LiteralPath $planFile -Algorithm SHA256).Hash;JournalHash=$journalHash;Snapshot=$snapshot;Captures=$captures;ReviewReference=$ReviewReference;VerifiedNativeExitZero=$true;NoOtherSessionsOrWriters=$true}
    Write-FastTrackFile $receiptPath ($receipt | ConvertTo-Json -Depth 6)
    $record=[ordered]@{Kind='recovered24';Index=$history.Index;SourceJournal=$journal;SourceHash=$journalHash;Receipt=$receiptPath;ReceiptHash=(Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash}
    $payload=$record | ConvertTo-Json -Compress
    $line=([ordered]@{Payload=$payload;Hash=(Get-TextSHA256 ($history.Chain+$payload))} | ConvertTo-Json -Compress)+"`n"
    $fork=Join-Path $packet ('fast-track-recovered-'+[guid]::NewGuid().ToString('N')+'.jsonl')
    Write-FastTrackFile $fork ($original+$line)
    [void](Get-FastTrackHistory $fork $stages $baseline $dir)
    Write-FastTrackFile $selectionPath (([ordered]@{Journal=$fork} | ConvertTo-Json -Compress))
    Write-Output "Recovery fork: $fork; checkpoint=24; next=21-proof. Original journal preserved; no SQL executed."
} finally { $lock.Dispose() }
