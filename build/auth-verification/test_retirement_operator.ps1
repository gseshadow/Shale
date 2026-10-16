# Offline behavioral checks; supports native Windows PS 5.1 and Core. Never connects to SQL.
param([string]$RunnerPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if (-not $RunnerPath) { $RunnerPath=Join-Path $PSScriptRoot '../../docs/sql/auth/permission-cutover/retirement-operator.ps1' }
$tokens=$null; $errors=$null
$null=[Management.Automation.Language.Parser]::ParseFile($RunnerPath,[ref]$tokens,[ref]$errors)
if ($errors.Count) { throw ($errors | Out-String) }
. $RunnerPath
function Expect-Failure([scriptblock]$Action,[string]$Message) {
    $failed=$false
    try { & $Action } catch { $failed=$true }
    if (-not $failed) { throw "Expected refusal: $Message" }
}
function Assert-Equal($Actual,$Expected,[string]$Message) { if ($Actual -cne $Expected) { throw $Message } }
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('retirement-offline-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    Assert-Equal (ConvertTo-SqlcmdArguments @('-S','tcp:127.0.0.1,14333','-i','C:\SQL files\setup.sql','-v','SecurityOwner=Brian Downing','ExpectedDatabase=Shale')) '-S tcp:127.0.0.1,14333 -i "C:\SQL files\setup.sql" -v SecurityOwner="Brian Downing" ExpectedDatabase=Shale' 'ODBC switches/value quoting regressed'
    Assert-Equal (ConvertTo-SqlcmdArguments @('-V','16','-v','Owner=Brian Downing')) '-V 16 -v Owner="Brian Downing"' 'Severity -V confused with variables -v'
    Assert-Equal (ConvertTo-SqlcmdArguments @('-i','C:\SQL files\')) '-i "C:\SQL files\\"' 'Trailing backslash lost'
    foreach ($bad in @('bad"quote',"bad`rline","bad`nline","bad`0nul")) {
        Expect-Failure { ConvertTo-SqlcmdArguments @('-i',$bad) } 'unsafe process argument'
    }
    Expect-Failure { ConvertTo-SqlcmdArguments @('-v','-S=inject') } 'variable name injection'
    # Unsupported clients must refuse before any prompt, directory or SQL transport.
    # A nonexistent executable exercises refusal without depending on installed tools.
    $PacketPath=Join-Path $testRoot 'unsupported-must-not-exist'
    $SqlcmdPath=Join-Path $testRoot 'missing-sqlcmd.exe'
    function Get-SourceRevision { return ('a'*40) }
    function Read-Host { throw 'TEST ERROR: unsupported client requested input' }
    $message=''
    try { Invoke-LocalRehearsal } catch { $message=$_.Exception.Message }
    if ($message -notmatch 'SQLCMD PREFLIGHT REFUSED' -or $message -notmatch 'explicit -SqlcmdPath') { throw "Wrong preflight refusal: $message" }
    if (Test-Path -LiteralPath $PacketPath) { throw 'Unsupported client created rehearsal evidence' }
    . $RunnerPath
    # Capture the real rehearsal's generated setup, without a SQL connection. SQLCMD
    # consumes :r directives and GO lines; every resulting CREATE batch must equal
    # the entire canonical CRLF body, including its leading/trailing whitespace.
    $PacketPath=Join-Path $testRoot 'assembly with spaces'
    function Get-SourceRevision { return ('a'*40) }
    function Assert-SupportedSqlcmd { return 'OFFLINE_ONLY' }
    function Confirm-Exact { }
    function Read-Host { param($Prompt,[switch]$AsSecureString)
        if ($AsSecureString) { return (ConvertTo-SecureString 'OFFLINE_ONLY' -AsPlainText -Force) }
        return 'OFFLINE_ONLY'
    }
    function Invoke-PacketSql {
        param($Root,$Server,$Database,$User,$Password,$InputFile,$Variables,$Log,$Executable,$Deadline,[switch]$LocalOnly)
        if ($InputFile -like '*-create.sql') { return 'OFFLINE_ONLY' }
        if ($InputFile -notlike '*-setup.sql') { throw 'Unexpected offline assembly transport' }
        $script:generatedSetup=[IO.File]::ReadAllText($InputFile)
        throw 'OFFLINE_SETUP_CAPTURED'
    }
    $message=''
    try { Invoke-LocalRehearsal } catch { $message=$_.Exception.Message }
    Assert-Equal $message 'OFFLINE_SETUP_CAPTURED' 'Rehearsal setup capture failed'
    $expanded=[regex]::Replace($script:generatedSetup, '(?m)^:r "([^"]+)"\r?\n', {
        param($match)
        [IO.File]::ReadAllText($match.Groups[1].Value)
    })
    $batches=[regex]::Split($expanded, '(?m)^GO\r?\n')
    $expectedLengths=@(1252,1528,596); $index=0
    foreach ($name in @('sp_login_get_hash','sp_login_mark_failure','sp_login_mark_success')) {
        $canonical=[IO.File]::ReadAllText((Join-Path $script:SourceRoot ('shale-server/src/test/resources/auth/'+$name+'.sql'))).Replace("`r`n","`n").Replace("`n","`r`n")
        $created=@($batches | Where-Object { $_ -match ('CREATE\s+PROC dbo\.'+$name+'\b') })
        Assert-Equal $created.Count 1 "Missing/duplicate CREATE batch for $name"
        Assert-Equal $created[0] $canonical "Generated $name batch differs from canonical body (possible leading CRLF after GO)"
        Assert-Equal ([Text.Encoding]::Unicode.GetByteCount($created[0])) $expectedLengths[$index++] "Wrong UTF-16LE length for $name"
    }
    Remove-Item Function:Read-Host
    . $RunnerPath
    $now=Get-Utc '2026-10-16T20:00:00Z'
    Assert-Window $now ($now.AddMinutes(30)) ($now.AddMinutes(20)) 600 ($now.AddMinutes(1))
    Expect-Failure { Get-Utc '2026-10-16T20:00:00' } 'UTC suffix required'
    Expect-Failure { Assert-Window $now ($now.AddMinutes(31)) ($now.AddMinutes(20)) 600 $now } 'window >30'
    Expect-Failure { Assert-Window $now ($now.AddMinutes(30)) ($now.AddMinutes(21)) 600 $now } 'reserve <10'
    Expect-Failure { Assert-Window $now ($now.AddMinutes(30)) ($now.AddMinutes(20)) 601 $now } 'measured larger reserve'
    Expect-Failure { Assert-Window $now ($now.AddMinutes(30)) ($now.AddMinutes(20)) 600 ($now.AddMinutes(20)) } 'deadline'
    Expect-Failure { Assert-Window $now ($now.AddMinutes(30)) ($now.AddMinutes(20)) 600 ($now.AddSeconds(-1)) } 'early'
    foreach ($bad in @('',"record' OR 1=1",'$(SQLCMDPASSWORD)','https://private.invalid','line;SQL')) { Expect-Failure { Assert-SafeValue $bad } 'unsafe substitution' }
    Assert-SafeValue 'Brian Downing'; Assert-SafeValue 'ActualRecord:20261016-01'
    $before='0x'+('AB'*32); $after='0x'+('CD'*32)
    $output=Join-Path $testRoot 'apply.sqlcmd.txt'
    # Real SQLCMD emits UTF-16 with BOM; do not depend on host default text encoding.
    [IO.File]::WriteAllText($output,"CheckName BeforeHash AfterHash`r`nLegacyRetirementCommitted $before $after`r`n",[Text.Encoding]::Unicode)
    Assert-Equal (Get-SavedHashes $output 'Apply' $before) $after 'UTF16 commit not parsed'
    Expect-Failure { Get-SavedHashes $output 'Apply' $after } 'wrong original before'
    [IO.File]::WriteAllText($output,"LegacyRetirementCommitted $before`n",[Text.Encoding]::Unicode)
    Expect-Failure { Get-SavedHashes $output 'Apply' $before } 'partial commit row'
    [IO.File]::WriteAllText($output,"LegacyRetirementCommitted $before $after`nLegacyRetirementCommitted $before $after`n",[Text.Encoding]::Unicode)
    Expect-Failure { Get-SavedHashes $output 'Apply' $before } 'ambiguous duplicate commit'
    $marker=Join-Path $testRoot 'attempt.json'
    Write-NewJson $marker @{Outcome='UNCERTAIN'}
    Expect-Failure { Write-NewJson $marker @{Outcome='OVERWRITE'} } 'durable marker cannot be overwritten'
    Assert-Equal (Read-Json $marker).Outcome 'UNCERTAIN' 'uncertain original destroyed'
    # PENDING/missing facts cannot produce approved substitutions.
    $ready=[pscustomobject]@{}
    foreach ($key in @('IdentityAuthority','KnownArtifacts','DesktopPassword','DesktopRemembered','ApiPassword','ApiRemembered','NeighboringWork','OperationalReadiness','ExactSqlcmdRehearsal','ManualRecovery')) {
        $ready | Add-Member NoteProperty $key ([pscustomobject]@{Status='PASSED'; Evidence='REAL-TEST-RECORD'; ScopeAndLimits='Synthetic offline test only'})
    }
    $ready | Add-Member NoteProperty MeasuredRecoverySeconds 720
    $ready | Add-Member NoteProperty KnownCallerDisposition 'NO_IDENTIFIED_UNRESOLVED_CALLER'
    $ready | Add-Member NoteProperty FollowUp ([pscustomobject]@{Owner='Brian Downing'; DailyReviewThroughUtc=[datetime]::UtcNow.AddDays(8).ToString('yyyy-MM-ddTHH:mm:ssZ'); KnownDueRuns='Synthetic test only'; ReopenedRiskReviewUtc=[datetime]::UtcNow.AddDays(9).ToString('yyyy-MM-ddTHH:mm:ssZ')})
    Assert-Readiness $ready
    foreach ($key in @('IdentityAuthority','KnownArtifacts','DesktopPassword','DesktopRemembered','ApiPassword','ApiRemembered','NeighboringWork','OperationalReadiness','ExactSqlcmdRehearsal','ManualRecovery')) {
        $ready.$key.Status='PENDING'; Expect-Failure { Assert-Readiness $ready } "PENDING $key"; $ready.$key.Status='PASSED'
    }
    $ready.MeasuredRecoverySeconds=$null; Expect-Failure { Assert-Readiness $ready } 'missing manual timing'; $ready.MeasuredRecoverySeconds=720
    # Approval refusals stop; defaults/empty/earlier approvals cannot count.
    function Read-Host { param($Prompt,[switch]$AsSecureString) return 'Earlier approval' }
    Expect-Failure { Confirm-Exact 'test' 'ACCEPT BOUNDED PRODUCTION RISK' } 'earlier approval'
    # No transport needed: an existing attempt must refuse before checking readiness or password.
    $PacketPath=$testRoot; $Phase='Apply'
    function Read-Packet { return [pscustomobject]@{BeforeHash=$before} }
    Write-NewJson (Join-Path $testRoot 'packet.json') @{BeforeHash=$before}
    Write-NewText (Join-Path $testRoot 'packet-seal.txt') (Get-Digest (Join-Path $testRoot 'packet.json'))
    Write-NewText (Join-Path $testRoot 'BeforeHash.txt') $before
    Write-NewJson (Join-Path $testRoot 'apply-attempt.json') @{Outcome='UNCERTAIN'}
    function Invoke-PacketSql { throw 'TEST ERROR: transport must never be reached' }
    $message=''
    try { Invoke-Retirement } catch { $message=$_.Exception.Message }
    if ($message -notmatch 'NEVER replay') { throw "Attempt replay not refused first: $message" }
    # Exercise the complete packet lifecycle using a fresh synthetic Git checkout and transport stub.
    # The stub is defined only in this test; the shipped runner has no transport bypass switch.
    . $RunnerPath
    $repo=Join-Path $testRoot 'source-repo'; New-Item -ItemType Directory -Path $repo | Out-Null
    $relative='docs/sql/auth/permission-cutover'
    $dest=Join-Path $repo $relative; New-Item -ItemType Directory -Path $dest -Force | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath (Split-Path -Parent $RunnerPath) -File) { Copy-Item -LiteralPath $file.FullName -Destination $dest }
    & git -C $repo init --quiet
    & git -C $repo add .
    & git -C $repo -c user.name=OfflineTest -c user.email=offline@example.invalid commit -qm 'Synthetic reviewed packet'
    if($LASTEXITCODE -ne 0){throw 'Synthetic Git fixture failed'}
    $script:SourceRoot=$repo; $script:BaseRevision=(& git -C $repo rev-parse HEAD).Trim(); $ReviewedRevision=$script:BaseRevision
    Write-NewText (Join-Path $repo 'unrelated.txt') 'Preserve unrelated work'
    Get-SourceRevision $repo $ReviewedRevision | Out-Null
    Add-Content -LiteralPath (Join-Path $dest 'operator-postcheck.sql') -Value '-- local relevant drift'
    Expect-Failure { Get-SourceRevision $repo $ReviewedRevision } 'relevant modifications refused'
    & git -C $repo restore -- docs/sql/auth/permission-cutover/operator-postcheck.sql
    $EvidencePath=Join-Path $testRoot 'evidence'; New-Item -ItemType Directory -Path $EvidencePath | Out-Null
    Write-NewText (Join-Path $EvidencePath 'legacy-retirement-baseline.txt') ("LegacyRetirementBaseline "+$script:Baseline+"`r`n")
    Write-NewText (Join-Path $EvidencePath 'inspect-legacy-login.txt') 'SYNTHETIC evidence, not operator captures'
    Write-NewText (Join-Path $EvidencePath 'capture.txt') 'SYNTHETIC evidence, not operator captures'
    $PacketPath=Join-Path $testRoot 'packet'
    $Phase='Prepare'
    function Read-Host { param($Prompt,[switch]$AsSecureString)
        if($Prompt -like '*endpoint*'){return 'sql.invalid'}
        return 'syntheticoperator'
    }
    function Assert-SupportedSqlcmd { param($Executable) return $Executable } # lifecycle only; real client checked separately
    function Invoke-PacketSql { throw 'Prepare must not connect' }
    Invoke-Retirement
    $packet=Read-Packet $PacketPath
    Assert-Equal $packet.RiskAcceptance 'PENDING' 'Prepare fabricated risk approval'
    Assert-Equal $packet.PackageApproval 'PENDING' 'Prepare fabricated package approval'
    Assert-Equal $packet.WindowAuthorization 'PENDING' 'Prepare fabricated window approval'
    Assert-Equal (Get-Content -LiteralPath (Join-Path $repo 'unrelated.txt') -Raw) 'Preserve unrelated work' 'Unrelated work lost'
    Expect-Failure { Invoke-Retirement } 'existing packet refused'
    $tamper=Join-Path $PacketPath 'source/docs/sql/auth/permission-cutover/operator-postcheck.sql'
    Add-Content -LiteralPath $tamper -Value '-- tampered'
    Expect-Failure { Read-Packet $PacketPath } 'staged SQL tampering refused'
    Copy-Item -LiteralPath (Join-Path $dest 'operator-postcheck.sql') -Destination $tamper -Force
    $null=Read-Packet $PacketPath
    # Real readiness is never supplied by the runner. Only this test creates synthetic complete records.
    $ready.MeasuredRecoverySeconds=720
    $ready | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $PacketPath 'readiness.json') -Encoding UTF8
    $Phase='Apply'
    $WindowStartUtc=[datetime]::UtcNow.AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $WindowEndUtc=[datetime]::UtcNow.AddMinutes(28).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $RollbackStartByUtc=[datetime]::UtcNow.AddMinutes(15).ToString('yyyy-MM-ddTHH:mm:ssZ')
    function Read-Host { param($Prompt,[switch]$AsSecureString) return 'REFUSED' }
    Expect-Failure { Invoke-Retirement } 'explicit final approval refused'
    if(Test-Path -LiteralPath (Join-Path $PacketPath 'apply-attempt.json')) {throw 'Attempt marked before final approvals'}
    function Read-Host { param($Prompt,[switch]$AsSecureString)
        if($AsSecureString){return (ConvertTo-SecureString 'SYNTHETIC_ONLY' -AsPlainText -Force)}
        if($Prompt -match 'Type exactly: (.+)$'){return $Matches[1]}
        if($Prompt -like '*Observed safe*'){return 'PASSED'}
        return 'SYNTHETIC-TEST-RECORD'
    }
    $script:calls=0
    function Invoke-PacketSql {
        param($Root,$Server,$Database,$User,$Password,$InputFile,$Variables,$Log,$Executable,$Deadline)
        $script:calls++
        if(-not(Test-Path -LiteralPath (Join-Path $PacketPath 'apply-attempt.json'))){throw 'Attempt record must precede SQL'}
        if($InputFile -like '*controlled-retire-legacy*') {
            if(-not(Test-Path -LiteralPath (Join-Path $PacketPath 'apply-command.json'))){throw 'Rollback must be staged before mutation'}
            Write-NewText ($Log+'.sqlcmd.txt') ("LegacyRetirementCommitted "+$script:Baseline+' '+$after+"`r`n")
            throw 'SYNTHETIC nonzero exit after commit'
        }
        if($InputFile -like '*controlled-rollback*') { Write-NewText ($Log+'.sqlcmd.txt') ("LegacyRetirementRolledBack "+$script:Baseline+"`r`n") }
        elseif($InputFile -like '*baseline*') { Write-NewText ($Log+'.sqlcmd.txt') ("LegacyRetirementBaseline "+$script:Baseline+"`r`n") }
        else { Write-NewText ($Log+'.sqlcmd.txt') "OperatorCatalogVerified`r`n" }
        return ($Log+'.sqlcmd.txt')
    }
    $failureMessage=''
    try { Invoke-Retirement } catch { $failureMessage=$_.Exception.Message }
    if($failureMessage -notmatch 'SYNTHETIC nonzero exit after commit'){throw "Unexpected apply refusal: $failureMessage"}
    Assert-Equal (Get-Content -LiteralPath (Join-Path $PacketPath 'AfterHash.txt') -Raw) $after 'AfterHash lost on nonzero exit'
    Assert-Equal $script:calls 1 'Unexpected transport replay'
    Expect-Failure { Invoke-Retirement } 'apply may never replay'
    Assert-Equal $script:calls 1 'Apply replay reached SQL'
    $Phase='Verify'; Invoke-Retirement
    # Advance only this synthetic test's authorization clock; rollback must remain usable after expiry.
    $authPath=Join-Path $PacketPath 'authorization.json'; $auth=Read-Json $authPath
    $auth.EndUtc='2000-01-01T00:00:00Z'; $auth.RollbackByUtc='2000-01-01T00:00:00Z'
    $auth | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $authPath -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $PacketPath 'authorization-sha256.txt') -Value (Get-Digest $authPath)
    $callCount=$script:calls
    Expect-Failure { Invoke-Retirement } 'expired trial verification must direct rollback'
    Assert-Equal $script:calls $callCount 'Expired trial consumed rollback reserve'
    $Phase='Rollback'; Invoke-Retirement
    $Phase='Verify'; Invoke-Retirement
    if(-not(Test-Path -LiteralPath (Join-Path $PacketPath 'rolled-back.json'))){throw 'Restored catalog not recorded'}
    Write-Output ('PASS retirement operator offline behavior on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
} finally {
    # Exact fresh offline test directory only. No operator evidence or SQL resources.
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
