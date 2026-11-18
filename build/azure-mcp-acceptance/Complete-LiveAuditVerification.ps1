#Requires -Version 7.4
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'LiveRuntime.ps1')
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$owner=$null;$lock=$null;$output=$null;$baseline=$null;$sourceHashes=@();$unchanged=$false;$status='FAIL';$code='AUDIT_CONTINUATION_FAILED'
try {
 $run=[IO.Path]::GetFullPath((Resolve-Path -LiteralPath $RunDirectory).Path)
 $work=[IO.Path]::GetFullPath((Join-Path $repo 'work'))+[IO.Path]::DirectorySeparatorChar
 if(-not $run.StartsWith($work,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($run) -cnotmatch '^live-mcp-[a-f0-9]{32}$'){throw 'AUDIT_RUN_REFUSED'}
 [void](Assert-PrivatePath $run)
 $manifest=@(Get-Content -Raw -LiteralPath (Join-Path $run 'sha256.json')|ConvertFrom-Json)
 foreach($item in $manifest){
  if($item.File -cnotmatch '^[A-Za-z0-9_.-]+$' -or $item.SHA256 -cnotmatch '^[A-F0-9]{64}$' -or (Get-FileHash -LiteralPath (Join-Path $run $item.File)).Hash -cne $item.SHA256){throw 'AUDIT_EVIDENCE_CHANGED'}
 }
 foreach($required in @('report.json','http-report.json','journal.jsonl','shale-server.jar')){
  if(@($manifest|Where-Object File -CEQ $required).Count -ne 1){throw 'AUDIT_EVIDENCE_REQUIRED'}
 }
 $sourceHashes=$manifest
 $original=Get-Content -Raw -LiteralPath (Join-Path $run 'report.json')|ConvertFrom-Json
 $client=Get-Content -Raw -LiteralPath (Join-Path $run 'http-report.json')|ConvertFrom-Json
 if(-not $original.Approved -or $original.Failure -cne 'durable-audit-verification' -or $client.status -cne 'PASS' -or $client.records.Count -ne 8 -or @($client.records|Where-Object status -CNE 'PASS').Count -ne 0 -or -not $client.credentialCleanup -or $client.disclosures -ne 8 -or $client.overviews -ne 6 -or -not $original.Cleanup.SqlOff -or -not $original.Cleanup.AppOff -or -not $original.Cleanup.ApplicationStopped){throw 'AUDIT_COMPLETED_CLIENT_REQUIRED'}
 Assert-LiveJar (Join-Path $run 'shale-server.jar') 'D1316428DE7CAB9A84DB1FB0DEDE1A56492342695D392C9271B61B73D40EB808' '1.0.138'
 $output=Join-Path $repo ('work/audit-completion-'+[guid]::NewGuid().ToString('N'));New-LiveDirectory $output
 [IO.File]::WriteAllText((Join-Path $repo 'work/latest-audit-completion-path.txt'),$output)
 $lock=[IO.File]::Open((Join-Path $repo 'work/clean-slate-acceptance.lock'),'OpenOrCreate','ReadWrite','None')
 $evidence=Read-LiveSqlEvidence (Join-Path $repo 'work/clean-slate-06a67de3fd7340378f9e0db9d6934d89')
 if($evidence.JournalHash -cne $original.SqlJournalHash){throw 'AUDIT_SQL_EVIDENCE_CHANGED'}
 Assert-LiveAzureConfiguration
 $settings=Get-LiveSettings
 foreach($key in @($script:LiveControls)+@('SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED')){if($settings[$key] -cne 'false'){throw 'AUDIT_REQUIRES_APP_OFF'}}
 $settings=$null
 Write-Host 'FINAL AUDIT ONLY: preserved PASS 8/8 client, ShaleMcpAcceptance.'
 Write-Host 'Read-only database verification. No deployment, activation, data changes or functional replay.'
 $owner=Get-Credential -UserName 'brian' -Message 'Existing ShaleMcpAcceptance owner for final read-only audit completion; memory only'
 Assert-LiveSqlOff $owner;Assert-LiveSynthetic $owner
 $zones=Get-LiveBinding $evidence.Directory $owner
 $path=Join-Path $run 'audit-baseline.json'
 if(Test-Path -LiteralPath $path){if(@($manifest|Where-Object File -CEQ 'audit-baseline.json').Count -ne 1){throw 'AUDIT_EVIDENCE_REQUIRED'};$baseline=Get-Content -Raw -LiteralPath $path|ConvertFrom-Json -AsHashtable;$baseline.Source='PERSISTED'}
 else {$baseline=Get-LiveRecoveredAuditBaseline $run $owner $client.disclosures $client.overviews $zones.Sql}
 [IO.File]::WriteAllText((Join-Path $output 'audit-baseline.json'),($baseline|ConvertTo-Json))
 Assert-LiveAudits $owner $baseline $client.disclosures $client.overviews
 [void](Get-LiveBinding $evidence.Directory $owner);Assert-LiveSynthetic $owner;Assert-LiveSqlOff $owner;Assert-CleanResources
 foreach($item in $manifest){if((Get-FileHash -LiteralPath (Join-Path $run $item.File)).Hash -cne $item.SHA256){throw 'AUDIT_EVIDENCE_CHANGED'}}
 $unchanged=$true;$status='PASS';$code=$null
} catch {
 $allowed=@('AUDIT_RUN_REFUSED','AUDIT_EVIDENCE_CHANGED','AUDIT_EVIDENCE_REQUIRED','AUDIT_COMPLETED_CLIENT_REQUIRED','AUDIT_SQL_EVIDENCE_CHANGED','AUDIT_REQUIRES_APP_OFF','AUDIT_RECOVERY_REFUSED','AUDIT_BASELINE_REQUIRED','DURABLE_AUDIT_VERIFICATION_FAILED')
 if($_.Exception.Message -cin $allowed){$code=$_.Exception.Message}
} finally {
 $owner=$null;if($lock){$lock.Dispose()}
 if($output){
  $result=@{Status=$status;FailureCode=$code;Scope='Read-only final audit completion of preserved 8/8 live run';OriginalRun=$run;OriginalEvidenceUnchanged=$unchanged;FunctionalClient='PASS';Disclosures=8;Overviews=6;Baseline=$baseline;SourceHashes=$sourceHashes;NoDeploymentOrActivation=$true;NoSqlMutation=$true}
  [IO.File]::WriteAllText((Join-Path $output 'report.json'),($result|ConvertTo-Json -Depth 7))
  Write-Host ("Final audit verification: "+$status+"; report: "+(Join-Path $output 'report.json'))
 }else{Write-Host ('Final audit verification refused: '+$code)}
}
