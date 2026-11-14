#Requires -Version 7.0
<# One foreground run approval. Fixed acceptance destination; no Azure management.
   Consecutive stages for one identity share a fail-fast SQLCMD connection. #>
[CmdletBinding()]
param([string]$RenderedDirectory,[string]$AuthoritativeFixture,[string]$SqlcmdExecutable,[string]$OperatorUser)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'FastTrack.ps1')
. (Join-Path $PSScriptRoot 'OdbcProbe.ps1')
$lock=$null; $report=$null; $reportPath=$null
try {
    if (-not $RenderedDirectory) { $RenderedDirectory=Read-Host 'Existing reviewed rendered packet directory' }
    if (-not $AuthoritativeFixture) { $AuthoritativeFixture=Read-Host 'Unchanged authoritative fixture path' }
    if (-not $SqlcmdExecutable) { $SqlcmdExecutable=Read-Host 'Reviewed Microsoft ODBC SQLCMD absolute executable path' }
    if (-not $OperatorUser) { $OperatorUser=Read-Host 'Existing acceptance-only SQL owner username' }
    if ($OperatorUser -notmatch '^[A-Za-z][A-Za-z0-9_@.\-]{0,127}$') { throw 'STOP: invalid owner principal.' }
    if (-not [IO.Path]::IsPathRooted($SqlcmdExecutable)) { throw 'STOP: SQLCMD must be selected by absolute path.' }
    $client=(Get-Item -LiteralPath $SqlcmdExecutable).FullName
    $clientHash=(Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash
    $identity=Read-SqlcmdClientIdentity $clientHash
    if ($identity.Family -ceq 'GO_SQLCMD') { throw 'STOP: select reviewed ODBC SQLCMD; the selected binary is Go SQLCMD.' }
    foreach ($item in Get-ChildItem Env:) {
        if ($item.Name -match '^(SQLCMD|SQLCONNSTR_|SQLAZURECONNSTR_|SPRING_DATASOURCE|SHALE_(APP|RT|AUTH|SERVER|SUGGESTION))' -and $item.Value) { throw 'STOP: clean shell required; ambient SQLCMD/application credentials are not accepted.' }
    }
    $help=& (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -Executable $client -Arguments @('-X1','-?') -WatchdogSeconds 5
    if ($help.FailureCode -or $help.ExitCode -ne 0) { throw 'STOP: SQLCMD help-only inspection failed; no SQL run started.' }
    $identity=Read-SqlcmdClientIdentity $clientHash $help.Lines
    if ($identity.Family -cne 'ODBC_SQLCMD' -or [int]$identity.Version.Split('.')[0] -notin @(17,18)) { throw 'STOP: reviewed Microsoft ODBC 17/18 SQLCMD required.' }
    $help=$null
    $dir=Assert-PrivatePath $RenderedDirectory
    $packet=Assert-PrivatePath ([IO.Path]::GetDirectoryName($dir))
    $lockPath=Assert-PrivatePath (Join-Path $packet 'supervised.lock')
    $lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $stages=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    if ($OperatorUser -cin @($stages | Where-Object { $_.PSObject.Properties.Name -contains 'user' } | ForEach-Object user)) { throw 'STOP: owner must be distinct from restricted acceptance identities.' }
    $hashes=@{}; foreach ($stage in $stages) { $hashes[$stage.name]=Get-StageHash $dir $stage.name }
    $baseline=Get-TextSHA256 (($stages[0..17] | ForEach-Object { $_.name+':'+$hashes[$_.name] }) -join "`n")
    $strict=Assert-PrivatePath (Join-Path $packet 'supervised.jsonl')
    if (Test-Path -LiteralPath $strict) {
        # Do not silently discard progress or uncertain outcomes from strict mode.
        $rows=@([IO.File]::ReadAllLines($strict))
        if ($rows.Count -ne 1 -or -not [IO.File]::ReadAllText($strict).EndsWith("`n")) { throw 'STOP: strict-mode progress/uncertainty exists; reconcile it in strict mode before changing modes.' }
        try {
            $row=$rows[0] | ConvertFrom-Json; $record=$row.Payload | ConvertFrom-Json
            if ($row.Hash -cne (Get-TextSHA256 (('0'*64)+[string]$row.Payload)) -or $record.Index -ne 0 -or $record.Stage -cne '15b-foundation-users' -or $record.Kind -cne 'baseline' -or $record.SHA256 -cne $baseline) { throw 'Invalid baseline' }
        } catch { throw 'STOP: strict checkpoint is not a verified 18-stage baseline.' }
    }
    $journal=Assert-PrivatePath (Join-Path $packet 'fast-track.jsonl')
    $history=Get-FastTrackHistory $journal $stages $baseline $dir
    $next=$history.Next; $script:chain=$history.Chain; $script:index=$history.Index
    if ($history.Pending) { throw 'STOP: interrupted/failed fast-track group; independently reconcile it before any retry. No completed stage will replay.' }
    if ($next -eq 51) { Write-Output 'FAST-TRACK: already complete 51/51; nothing executed.'; return }
    # Full offline prerequisites, including the Stage 16 JVM/timezone evidence,
    # are checked BEFORE approval/execution. No evidence or secret is fabricated.
    & (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $dir -AuthoritativeFixture $AuthoritativeFixture -Stage All -PreparedOnly -SqlcmdExecutable $client | ForEach-Object { if ($_ -like 'BLOCKED:*') { Write-Host $_ } }
    $run=Assert-PrivatePath (Join-Path $packet ('fast-track-'+[guid]::NewGuid().ToString('N')))
    [void][IO.Directory]::CreateDirectory($run)
    $reportPath=Join-Path $run 'report.json'
    $report=[ordered]@{Mode='FAST_TRACK'; Server='shale.database.windows.net'; Database='ShaleMcpAcceptance'; CompletedThrough=$stages[$next-1].name; CompletedCount=$next; CompletedStages=@(); Outcome='NOT_STARTED'; FailureStage=$null; ExitCode=$null; SqlErrorNumbers=@(); FailureCode=''; AttemptedGroupStages=@(); NextAction='Approve the reviewed acceptance run.'}
    function Append-RunEvent($Record) {
        $Record.Index=$script:index
        $payload=$Record | ConvertTo-Json -Compress
        $digest=Get-TextSHA256 ($script:chain+$payload)
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes((([ordered]@{Payload=$payload;Hash=$digest} | ConvertTo-Json -Compress)+"`n"))
        $stream=[IO.File]::Open($journal,[IO.FileMode]::Append,[IO.FileAccess]::Write,[IO.FileShare]::Read)
        try { $stream.Write($bytes); $stream.Flush($true) } finally { $stream.Dispose() }
        $script:chain=$digest; $script:index++
    }
    $templates=Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance'
    $target=[IO.File]::ReadAllText((Join-Path $templates 'target.sql'))
    $owner=[IO.File]::ReadAllText((Join-Path $templates 'owner.sql'))
    $groups=@(); $cursor=$next
    while ($cursor -lt 51) {
        $start=$cursor; $user=$OperatorUser
        if ($stages[$cursor].PSObject.Properties.Name -contains 'user') { $user=$stages[$cursor].user }
        $files=@()
        if ($cursor -eq 18) {
            $checkpoint=Join-Path $run 'checkpoint18.sql'
            $sql=":on error exit`n"+$target+"`n"+$owner+"`nGO`n"
            $sql+="IF EXISTS(SELECT 1 FROM dbo.ShaleClients) OR EXISTS(SELECT 1 FROM dbo.Users) OR EXISTS(SELECT 1 FROM dbo.Cases) THROW 59810,'Fast-track requires the verified empty 18-stage checkpoint; no seed replay.',1;`n"
            $sql+="IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'mcpacc_auth',N'mcpacc_runtime',N'mcpacc_predecessor',N'mcpacc_verifier',N'mcpacc_bootstrap') AND type='S' AND authentication_type=2)<>5 THROW 59810,'Foundation identities missing.',1;`nGO`nSELECT CAST('FAST_CHECKPOINT_OK:18' AS varchar(96)) AS FastTrackStatus;`nGO`n"
            Write-FastTrackFile $checkpoint $sql; $files+=$checkpoint
        }
        do {
            $stage=$stages[$cursor]
            $restricted=$stage.PSObject.Properties.Name -contains 'user'
            $fence=$target+"`n"; if (-not $restricted) { $fence+=$owner+"`n" }; $fence+="GO`n"
            $before=Join-Path $run ($stage.name+'-begin.sql'); $after=Join-Path $run ($stage.name+'-done.sql')
            Write-FastTrackFile $before (":on error exit`n"+$fence+"SELECT CAST('FAST_STAGE_BEGIN:$($stage.name)' AS varchar(96)) AS FastTrackStatus;`nGO`n")
            Write-FastTrackFile $after (":on error exit`n"+$fence+"SELECT CAST('FAST_STAGE_DONE:$($stage.name)' AS varchar(96)) AS FastTrackStatus;`nGO`n")
            $files+=@($before,(Join-Path $dir ($stage.name+'.sql')),$after)
            $cursor++
            if ($cursor -ge 51) { break }
            $nextUser=$OperatorUser; if ($stages[$cursor].PSObject.Properties.Name -contains 'user') { $nextUser=$stages[$cursor].user }
        } while ($nextUser -ceq $user)
        # ODBC's documented -i file1,file2 syntax has no unambiguous comma escape.
        if (@($files | Where-Object { $_.Contains(',') }).Count -or (($files -join ',').Length -gt 24000)) { throw 'STOP: input paths cannot contain commas or exceed the batch argument limit; use strict mode or a shorter reviewed checkout path.' }
        $groups += [pscustomobject]@{Start=$start; End=$cursor; User=$user; Restricted=$restricted; Files=$files; Hashes=@($files | ForEach-Object {(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash})}
    }
    Write-Output "FAST-TRACK: $next/51 complete; run $($stages[$next].name) through $($stages[-1].name). Target ONLY shale.database.windows.net / ShaleMcpAcceptance."
    Write-Output "ODBC version=$($identity.Version) SHA256=$clientHash. Stage hashes verified automatically. Stage 16 evidence must already be approved. Application/MCP stay OFF."
    if ((Read-Host 'Approve this entire reviewed disposable acceptance run, existing inputs/credentials, retained 18-stage evidence and all remaining SQL transitions; type FAST_TRACK_SHALE_MCP_ACCEPTANCE') -cne 'FAST_TRACK_SHALE_MCP_ACCEPTANCE') { throw 'STOP: run approval refused; no SQL executed.' }
    if ($script:index -eq 0) { Append-RunEvent ([ordered]@{Kind='baseline';Hash=$baseline;Index=0}) }
    $report.Outcome='RUNNING'
    foreach ($group in $groups) {
        if ((Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash -cne $clientHash) { throw 'STOP: reviewed SQLCMD executable changed.' }
        for ($i=0; $i -lt $group.Files.Count; $i++) {
            if ((Get-FileHash -LiteralPath $group.Files[$i] -Algorithm SHA256).Hash -cne $group.Hashes[$i]) { throw 'STOP: approved batch inputs changed.' }
        }
        $report.AttemptedGroupStages=@($stages[$group.Start..($group.End-1)] | ForEach-Object name)
        Append-RunEvent ([ordered]@{Kind='pending';Start=$group.Start;End=$group.End;Index=0})
        $seconds=if ($group.Restricted) {'5'} else {'20'}
        $arguments=@('-S','tcp:shale.database.windows.net,1433','-d','ShaleMcpAcceptance','-U',$group.User,'-N','-b','-V','11','-r','1','-l','30','-t',$seconds,'-x','-X1','-f','65001','-i',($group.Files -join ','),'-s','|','-W','-w','65535')
        Write-Host "Executing $($stages[$group.Start].name)..$($stages[$group.End-1].name); wait for PASSWORD INPUT READY, then use this connection's existing credential."
        $capture=& (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -Executable $client -Arguments $arguments -RequireConsole
        $numbers=@(Get-SafeSqlErrorNumbers $capture.Lines)
        $parsed=Read-FastTrackBatch $capture.Lines @($stages[$group.Start..($group.End-1)])
        foreach ($done in $parsed.Completed) {
            Append-RunEvent ([ordered]@{Kind='complete';Stage=$done.Stage;Hash=$hashes[$done.Stage];Index=0})
            $next++; $report.CompletedCount=$next; $report.CompletedThrough=$stages[$next-1].name; $report.CompletedStages+= $done.Stage
            Write-Output "Completed $next/51: $($done.Stage)"
        }
        if ($capture.FailureCode -or $capture.ExitCode -ne 0 -or $numbers.Count -or -not $capture.PasswordPromptObserved -or -not $capture.PasswordInputReadyShown -or -not $parsed.Complete) {
            $report.FailureStage=if ($next -lt $group.End) {$stages[$next].name} else {$stages[$group.End-1].name}
            $report.ExitCode=$capture.ExitCode; $report.SqlErrorNumbers=$numbers
            $report.FailureCode=if ($capture.FailureCode) {$capture.FailureCode} elseif ($numbers.Count) {'SQL_ERROR'} elseif ($capture.ExitCode -ne 0) {'PROCESS_EXIT'} elseif (-not $capture.PasswordPromptObserved -or -not $capture.PasswordInputReadyShown) {'PASSWORD_PROMPT_NOT_CONFIRMED'} else {'INCOMPLETE_RESULTS'}
            Append-RunEvent ([ordered]@{Kind='failed';Stage=$report.FailureStage;Index=0})
            throw 'STOP: batch failed or outcome uncertain. Reconcile the failed group independently; no automatic retry.'
        }
        Append-RunEvent ([ordered]@{Kind='verified';Index=0})
        $capture=$null
    }
    $report.Outcome='COMPLETE'; $report.NextAction='51/51 SQL preparation complete. Keep application/MCP OFF; runtime acceptance is separate.'
} catch {
    if ($report) { $report.Outcome='STOPPED'; $report.NextAction=$_.Exception.Message }
    if (-not $report) { Write-Host ('FAST-TRACK: no SQL stage executed by this attempt; next='+$_.Exception.Message) }
    Write-Error ('Fast-track stopped. '+$_.Exception.Message) -ErrorAction Continue
    $failed=$true
} finally {
    if ($reportPath -and $report) { Write-FastTrackFile $reportPath ($report | ConvertTo-Json -Depth 5); Write-Host "Report: $reportPath; completed=$($report.CompletedCount)/51; outcome=$($report.Outcome); next=$($report.NextAction)" }
    if ($lock) { $lock.Dispose() }
}
if (Get-Variable failed -ErrorAction SilentlyContinue) { if ($failed) { exit 1 } }
