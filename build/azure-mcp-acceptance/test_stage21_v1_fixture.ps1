# Test-only construction of the frozen historical v1 successor. Synthetic work/ only.
param($Previous,$V1,$Failure,$Packet,$OldCapture,$OldTicket)
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'FastTrack.ps1')
$Packet=Assert-PrivatePath $Packet
if ([IO.Path]::GetFileName($Packet) -cnotmatch '^supervised-test-[A-Za-z0-9_-]+$' -or
    [IO.File]::ReadAllText((Join-Path $Packet 'mock-client')) -cne 'Never executed' -or
    [IO.File]::ReadAllText((Join-Path $Packet 'synthetic-fixture-placeholder')) -cne 'Not an authoritative fixture; fingerprint is mocked, never imported.') {
    throw 'STOP: historical fixture construction is limited to owned synthetic test packets.'
}
$stages=@(Get-Content (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
$base=Get-TextSHA256 (($stages[0..17] | ForEach-Object { $_.name+':'+(Get-StageHash $Previous $_.name) }) -join "`n")
$selection=Join-Path $Packet 'fast-track-recovery.json'
$parent=(Read-Stage21JsonFile $selection).Journal
$history=Get-FastTrackHistory $parent $stages $base $Previous
$text=[IO.File]::ReadAllText((Join-Path $Previous '21-proof.sql'))
foreach ($source in @('docs/sql/ai-integration/universal-pr4a-proof.sql','docs/sql/ai-integration/universal-pr4a-proof-verify.sql')) {
    $body=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../'+$source)))
    $text=$text.Replace($body,(Add-ProofModuleDiagnosticsV1 $body $source))
}
[IO.File]::WriteAllText((Join-Path $V1 '21-proof.sql'),$text,[Text.UTF8Encoding]::new($false))
$rows=@(Import-Csv (Join-Path $V1 'sha256.csv'))
foreach ($row in $rows) { if ($row.Stage -ceq '21-proof') { $row.SHA256=(Get-FileHash (Join-Path $V1 '21-proof.sql') -Algorithm SHA256).Hash } }
$rows | Export-Csv (Join-Path $V1 'sha256.csv') -NoTypeInformation
$review=Join-Path $Packet 'synthetic-v1-review'; [void][IO.Directory]::CreateDirectory($review)
$ticket=[guid]::NewGuid().ToString('N');$sql=Join-Path $review 'checkpoint.sql'
Write-FastTrackFile $sql (New-Checkpoint24Sql $ticket $Previous)
$plan=[ordered]@{Version=1;Ticket=$ticket;PreviousDirectory=$Previous;NewDirectory=$V1;ParentJournal=$parent;ParentHash=(Get-FileHash $parent -Algorithm SHA256).Hash;SelectionHash=(Get-FileHash $selection -Algorithm SHA256).Hash;CheckpointSnapshot=$history.Checkpoint24Snapshot;FailureReport=$Failure;FailureReportHash=(Get-FileHash $Failure -Algorithm SHA256).Hash;StageHashes=@(Get-Stage21RetryHashes $V1 $stages);SqlPath=$sql;SqlHash=(Get-FileHash $sql -Algorithm SHA256).Hash}
$planPath=Join-Path $review 'plan.json';Write-FastTrackFile $planPath ($plan | ConvertTo-Json -Depth 6)
$capture=Join-Path $review 'capture.txt';Write-FastTrackFile $capture ([IO.File]::ReadAllText($OldCapture).Replace($OldTicket,$ticket))
$receipt=[ordered]@{PlanPath=$planPath;PlanHash=(Get-FileHash $planPath -Algorithm SHA256).Hash;Capture=$capture;CaptureHash=(Get-FileHash $capture -Algorithm SHA256).Hash;VerifiedNativeExitZero=$true;NoOtherSessionsOrWriters=$true;ReviewReference='synthetic-v1-review'}
$receiptPath=Join-Path $review 'receipt.json';Write-FastTrackFile $receiptPath ($receipt | ConvertTo-Json -Depth 5)
Assert-Stage21RetryReceipt $receiptPath (Get-FileHash $receiptPath -Algorithm SHA256).Hash $V1 $stages $base
$record=[ordered]@{Kind='stage21-baseline';Index=0;Hash=$base;Evidence=$receiptPath;EvidenceHash=(Get-FileHash $receiptPath -Algorithm SHA256).Hash}
$payload=$record | ConvertTo-Json -Compress
$journal=Join-Path $Packet 'synthetic-v1-failed.jsonl'
Write-FastTrackFile $journal (([ordered]@{Payload=$payload;Hash=(Get-TextSHA256 (('0'*64)+$payload))} | ConvertTo-Json -Compress)+"`n")
Write-FastTrackFile (Join-Path $Packet 'fast-track-stage21-recovery.json') (([ordered]@{Journal=$journal} | ConvertTo-Json -Compress))
