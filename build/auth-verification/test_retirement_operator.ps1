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
