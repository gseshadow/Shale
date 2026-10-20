#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Prepare','Apply','Verify','Rollback','Rehearse')][string]$Phase = 'Prepare',
    [string]$PacketPath,
    [string]$ReviewedRevision,
    [string]$EvidencePath = 'C:\ShaleVerification\RetirementPacket-20261016-135011',
    [string]$SqlcmdPath = 'sqlcmd.exe',
    [string]$WindowStartUtc,
    [string]$WindowEndUtc,
    [string]$RollbackStartByUtc,
    [int]$LocalPort = 14333
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Baseline = '0x579CCE23B55F60F121F04B4D44E148D06247598A2EECC2EBF198555CE8AAC561'
$script:BaseRevision = '0987b3f8e4afac5aa5a8b17a27a75743138512d1'
$script:SourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))

function Write-NewText([string]$Path, [string]$Text) {
    # CreateNew prevents replay/overwriting original records; Flush(true) persists local data.
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text); $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) }
    finally { $stream.Dispose() }
}
function Write-NewJson([string]$Path, $Value) { Write-NewText $Path ($Value | ConvertTo-Json -Depth 30) }
function Read-Json([string]$Path) { Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
function Get-Digest([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Assert-Hex([string]$Value) {
    if ($Value -cnotmatch '^0x[0-9A-Fa-f]{64}$') { throw 'Missing/invalid saved catalog hash. Never infer it from current state.' }
}
function Assert-SafeValue([string]$Value) {
    if ($Value -notmatch '^[A-Za-z0-9][A-Za-z0-9 ._:-]{0,199}$') { throw 'Use a nonempty safe private record ID/name, not SQL, a URL, quote or file contents.' }
}
function Get-Utc($Value) {
    # Core may deserialize ISO JSON strings as DateTime; Windows PS5.1 retains strings.
    if ($Value -is [datetime]) {
        if ($Value.Kind -eq [DateTimeKind]::Unspecified) { throw 'UTC DateTime required.' }
        return $Value.ToUniversalTime()
    }
    $result = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($Value, 'yyyy-MM-ddTHH:mm:ssZ', [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal, [ref]$result)) {
        throw 'Exact UTC time required: yyyy-MM-ddTHH:mm:ssZ.'
    }
    return $result
}
function Assert-Window($Start, $End, $RollbackBy, [double]$MeasuredSeconds, [datetime]$Now = [datetime]::UtcNow) {
    $reserve = [math]::Max(600, [math]::Ceiling($MeasuredSeconds))
    if ($MeasuredSeconds -le 0 -or $Start -ge $RollbackBy -or $End -le $Start -or
        ($End-$Start).TotalMinutes -gt 30 -or ($End-$RollbackBy).TotalSeconds -lt $reserve -or
        $Now -lt $Start -or $Now -ge $RollbackBy) {
        throw 'Window refused: <=30 minutes; >=10 minutes or measured recovery reserved; apply only before rollback deadline. No extension.'
    }
}
function Confirm-Exact([string]$Message, [string]$Literal) {
    if ((Read-Host "$Message`nType exactly: $Literal") -cne $Literal) { throw 'Decision remains PENDING; stopped before apply.' }
}
function Assert-PrivatePath([string]$Path, [string]$Repo) {
    $full = [IO.Path]::GetFullPath($Path)
    $repoFull = [IO.Path]::GetFullPath($Repo).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ($full -eq $repoFull -or $full.StartsWith($repoFull+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Private evidence and packet must be outside the repository.'
    }
    # Also reject another Git worktree, and reparse ancestors. Trusted exclusive ACLs are still required.
    $cursor = $full
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse path refused.' }
            if ((Test-Path -LiteralPath (Join-Path $cursor '.git/HEAD')) -or (Test-Path -LiteralPath (Join-Path $cursor '.git') -PathType Leaf)) { throw 'Packet/evidence is inside a Git checkout.' }
        }
        $parent = Split-Path -Parent $cursor
        if ($parent -eq $cursor) { break }; $cursor = $parent
    }
    return $full
}
function Get-SourceRevision([string]$Repo, [string]$Reviewed) {
    if ($Reviewed -notmatch '^[0-9a-f]{40}$') { throw 'Supply the full reviewed package commit SHA.' }
    $head = (& git -C $Repo rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -cne $Reviewed) { throw 'Checkout differs from reviewed package revision.' }
    & git -C $Repo merge-base --is-ancestor $script:BaseRevision HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Reviewed PR1880 base is not an ancestor.' }
    $paths = @('docs/sql/auth/permission-cutover','docs/testing/auth-module-permission-cutover.md',
        'shale-server/src/test/resources/auth','build/auth-verification','AGENTS.md','architecture/codex-prompt-rules.md','architecture/database-schema.md','architecture/tenancy-and-rls.md')
    $dirty = & git -C $Repo status --porcelain --untracked-files=all -- @paths
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Relevant local modifications/untracked files refused; preserve unrelated work.' }
    return $head
}
function Get-SavedHashes([string]$Output, [string]$Kind, [string]$Before = '') {
    $text = Get-Content -LiteralPath $Output -Raw
    if ($Kind -eq 'Apply') {
        $matchesFound = [regex]::Matches($text,'(?m)^\s*LegacyRetirementCommitted\s+(0x[0-9A-Fa-f]{64})\s+(0x[0-9A-Fa-f]{64})\s*$')
        if ($matchesFound.Count -ne 1 -or $matchesFound[0].Groups[1].Value -ine $Before) {
            throw 'Commit/hash output incomplete or ambiguous. Commit status UNCERTAIN; never replay apply or query a replacement expected AfterHash.'
        }
        return $matchesFound[0].Groups[2].Value
    }
    $marker = if ($Kind -eq 'Rollback') { 'LegacyRetirementRolledBack' } else { 'LegacyRetirementBaseline' }
    $matchesFound = [regex]::Matches($text, '(?m)^\s*'+$marker+'\s+(0x[0-9A-Fa-f]{64})\s*$')
    if ($matchesFound.Count -ne 1) { throw 'Complete unambiguous saved hash output required.' }
    return $matchesFound[0].Groups[1].Value
}
function ConvertTo-SqlcmdArguments([string[]]$Arguments) {
    # Windows ODBC SQLCMD parses the raw command line itself: quoted switches are
    # not options. Always quote -v values: punctuation in unquoted ISO timestamps
    # is also parsed as option syntax. Keep Name= outside the value quotes.
    $variables = $false
    $encoded = foreach ($argument in $Arguments) {
        if ($argument -match '["\r\n\x00]') { throw 'Unsafe process argument.' }
        if ($argument -ceq '-v') { $variables=$true; '-v'; continue }
        $prefix=''; $value=$argument
        if ($variables) {
            if ($argument -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'Invalid SQLCMD variable assignment.' }
            $prefix=$Matches[1]+'='; $value=$Matches[2]
        }
        if ($variables -or $value -eq '' -or $value -match '\s') {
            # Preserve trailing backslashes before a closing quote under Windows argv rules.
            $value='"'+[regex]::Replace($value, '(\\+)$', '$1$1')+'"'
        }
        $prefix+$value
    }
    return ($encoded -join ' ')
}
function New-SqlcmdStartInfo([string]$Executable, [string]$Root, [string[]]$Arguments) {
    $command = Get-Command $Executable -CommandType Application -ErrorAction Stop
    $psi = New-Object Diagnostics.ProcessStartInfo
    # FileName is a path, never a quoted command string. No shell is involved.
    $psi.FileName=$command.Source; $psi.WorkingDirectory=$Root
    $psi.Arguments=ConvertTo-SqlcmdArguments $Arguments
    $psi.UseShellExecute=$false; $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
    foreach ($name in @($psi.EnvironmentVariables.Keys)) {
        if ($name -like 'SQLCMD*') { $psi.EnvironmentVariables.Remove($name) }
    }
    return $psi
}
function Assert-SupportedSqlcmd([string]$Executable) {
    $action = 'Use Microsoft ODBC SQLCMD 17/18 with explicit -SqlcmdPath (for example C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE). Go SQLCMD is unsupported. No credentials or rehearsal resource should be supplied/created; preserve earlier evidence/resources and choose a new directory.'
    $process=New-Object Diagnostics.Process
    try {
        # Help only: no server, input SQL, credentials or connection. Scrub SQLCMDINI too.
        $process.StartInfo=New-SqlcmdStartInfo $Executable $script:SourceRoot @('-?')
        if (-not $process.Start()) { throw 'Help process did not start.' }
        $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(10000)) { $process.Kill(); $process.WaitForExit(); throw 'Help probe exceeded 10 seconds.' }
        $help=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0 -or $help -notmatch 'Microsoft \(R\) SQL Server Command Line Tool' -or
            $help -notmatch '(?m)^Version\s+1[78]\.' -or $help -match '(?i)go-sqlcmd|--version|Version: v') {
            throw 'Unrecognized/unsupported SQLCMD help signature.'
        }
        foreach ($option in @('u','y','Y','w','b','V','i','o')) {
            if ($help -cnotmatch ('\[-'+$option+'(?:[ \[])')) { throw "Required ODBC option missing: -$option" }
        }
        Write-Host "SQLCMD PREFLIGHT PASSED - Microsoft ODBC - help only - no connection: $($process.StartInfo.FileName)"
        return $process.StartInfo.FileName
    } catch { throw "SQLCMD PREFLIGHT REFUSED: $($_.Exception.Message) $action" }
    finally { $process.Dispose() }
}
function Invoke-PacketSql([string]$Root, [string]$Server, [string]$Database, [string]$User,
    [Security.SecureString]$Password, [string]$InputFile, [string[]]$Variables, [string]$Log,
    [string]$Executable, [Nullable[datetime]]$Deadline = $null, [switch]$LocalOnly) {
    # SQLCMD -U password prompt is implemented here as Read-Host -AsSecureString at phase entry.
    # Only the child environment gets SQLCMDPASSWORD. No password argument, transcript or global env edit.
    if ($Server -notmatch '^[A-Za-z0-9][A-Za-z0-9.,:_-]{0,254}$' -or $User -notmatch '^[A-Za-z0-9][A-Za-z0-9_.@-]{0,127}$') { throw 'Unsafe connection selector.' }
    Assert-SafeValue $Database
    $args = @('-S',$Server,'-d',$Database,'-U',$User,'-b','-V','16','-l','30','-t','30','-y','0','-Y','0','-w','65535','-u',
        '-i',$InputFile,'-o',($Log+'.sqlcmd.txt'))
    if ($LocalOnly) { $args += '-C' } else { $args += '-N' } # production: encrypted, certificate verified
    if ($Variables.Count) { $args += '-v'; $args += $Variables }
    $psi = New-SqlcmdStartInfo $Executable $Root $args
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
    try { $psi.EnvironmentVariables['SQLCMDPASSWORD'] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if (Test-Path -LiteralPath ($Log+'.sqlcmd.txt')) { throw 'Preserve existing SQLCMD output; choose a new invocation log.' }
    $started=$false
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $psi
    $outStream = [IO.File]::Open($Log+'.stdout.bin',[IO.FileMode]::CreateNew)
    $errStream = [IO.File]::Open($Log+'.stderr.bin',[IO.FileMode]::CreateNew)
    try {
        if (-not $process.Start()) { throw 'SQLCMD did not start.' }; $started=$true
        $psi.EnvironmentVariables.Remove('SQLCMDPASSWORD')
        $outTask = $process.StandardOutput.BaseStream.CopyToAsync($outStream)
        $errTask = $process.StandardError.BaseStream.CopyToAsync($errStream)
        $ticks = 0; $expired = $false
        while (-not $process.WaitForExit(1000)) {
            if ($Deadline -and [datetime]::UtcNow -ge $Deadline) { $expired=$true; $process.Kill(); break }
            if (($ticks++ % 15) -eq 0 -and $Deadline) { Write-Host "UTC $([datetime]::UtcNow.ToString('o')); STOP/APPLY cutoff $($Deadline.ToString('o'))" }
        }
        $process.WaitForExit()
        # CopyToAsync may surface VoidTaskResult through PowerShell's success
        # pipeline. Await both drains, but return only the log path below.
        $null = $outTask.GetAwaiter().GetResult()
        $null = $errTask.GetAwaiter().GetResult()
        $outStream.Flush($true); $errStream.Flush($true)
        if (Test-Path -LiteralPath ($Log+'.sqlcmd.txt')) {
            $durable = [IO.File]::Open($Log+'.sqlcmd.txt',[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite)
            try { $durable.Flush($true) } finally { $durable.Dispose() }
        }
        $result = @{ExitCode=$process.ExitCode; DeadlineExceeded=$expired; EndUtc=[datetime]::UtcNow.ToString('o')}
        Write-NewJson ($Log+'.result.json') $result
        if ($expired -or $process.ExitCode -ne 0) { throw "SQLCMD failed/interrupted: ExitCode=$($process.ExitCode); DeadlineExceeded=$expired; output prefix: $Log. Preserve ALL output; commit status may be UNCERTAIN. No apply replay." }
    } finally {
        $psi.EnvironmentVariables.Remove('SQLCMDPASSWORD')
        if ($started -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $outStream.Flush($true); $errStream.Flush($true)
        $outStream.Dispose(); $errStream.Dispose(); $process.Dispose()
    }
    return ($Log+'.sqlcmd.txt')
}
function Assert-ExpectedSqlRefusal([string]$Log, [int]$ExpectedError, [bool]$Caught, [string]$Failure) {
    # Exit 1 can be a client/parser failure before SQL starts. Require the actual
    # SQL Server error header, a non-client exit, and no deadline interruption.
    $resultPath=$Log+'.result.json'; $outputPath=$Log+'.sqlcmd.txt'; $stderrPath=$Log+'.stderr.bin'
    $result=$null; $text=''; $stderr=''
    if (Test-Path -LiteralPath $resultPath) { $result=Read-Json $resultPath }
    if (Test-Path -LiteralPath $outputPath) { $text=Get-Content -LiteralPath $outputPath -Raw }
    if (Test-Path -LiteralPath $stderrPath) { $stderr=[Text.Encoding]::Default.GetString([IO.File]::ReadAllBytes($stderrPath)) }
    if (-not $Caught -or -not $result -or $result.DeadlineExceeded -or $result.ExitCode -in @(0,1) -or
        $text -notmatch ('(?m)^\s*Msg\s+'+$ExpectedError+',\s*Level\s+16\b')) {
        $status=if ($result) { "ExitCode=$($result.ExitCode); DeadlineExceeded=$($result.DeadlineExceeded)" } else { 'No process result' }
        throw "Local rehearsal expected SQL error $ExpectedError was NOT verified. $status. SQL output (may be absent): $outputPath; retained stderr: $stderrPath; retained result: $resultPath; retained stdout: $Log.stdout.bin. Transport failure: $Failure`nClient stderr: $stderr`nPreserve evidence and nonce-owned database; no automatic retry or cleanup."
    }
}
function Read-Packet([string]$Path) {
    $root = Assert-PrivatePath $Path $script:SourceRoot
    $packet = Read-Json (Join-Path $root 'packet.json')
    foreach ($entry in $packet.Files) {
        $file = [IO.Path]::GetFullPath((Join-Path $root $entry.Path))
        if (-not $file.StartsWith($root.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid packet path.' }
        if ((Get-Digest $file) -cne $entry.SHA256) { throw "Packet changed: $($entry.Path). Recovery needs the original packet, not a rebuilt baseline." }
    }
    # The runner executing the later phase must itself be the staged reviewed bytes.
    if ((Get-Digest $PSCommandPath) -cne (Get-Digest (Join-Path $root 'source/docs/sql/auth/permission-cutover/retirement-operator.ps1'))) { throw 'Run the original staged runner.' }
    return $packet
}
function Prepare-Packet {
    $revision = Get-SourceRevision $script:SourceRoot $ReviewedRevision
    $root = Assert-PrivatePath $PacketPath $script:SourceRoot
    $evidence = Assert-PrivatePath $EvidencePath $script:SourceRoot
    if (Test-Path -LiteralPath $root) { throw 'Use a NEW private packet directory; preserve every earlier packet.' }
    $SqlcmdPath = Assert-SupportedSqlcmd $SqlcmdPath
    $baselineFile = Join-Path $evidence 'legacy-retirement-baseline.txt'
    if ((Get-SavedHashes $baselineFile 'Baseline') -ine $script:Baseline) { throw 'Original reported baseline differs. Stop for review, do not adopt current state.' }
    $server = Read-Host 'Existing approved Shale SQL server endpoint (no connection will be made)'
    $user = Read-Host 'Brian: existing approved SQL operator username (dbo metadata authority)'
    if ($server -notmatch '^[A-Za-z0-9][A-Za-z0-9.,:_-]{0,254}$' -or $user -notmatch '^[A-Za-z0-9][A-Za-z0-9_.@-]{0,127}$') { throw 'Invalid connection selector.' }
    New-Item -ItemType Directory -Path $root | Out-Null
    $files = @()
    $sourceFiles = @(& git -C $script:SourceRoot ls-files 'docs/sql/auth/permission-cutover/*' 'shale-server/src/test/resources/auth/sp_login_*.sql' 'docs/testing/auth-module-permission-cutover.md' 'AGENTS.md' 'architecture/codex-prompt-rules.md' 'architecture/database-schema.md' 'architecture/tenancy-and-rls.md')
    if ($LASTEXITCODE -ne 0) { throw 'Source inventory failed.' }
    foreach ($relative in $sourceFiles) {
        $dest = Join-Path $root ('source/'+$relative)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
        Copy-Item -LiteralPath (Join-Path $script:SourceRoot $relative) -Destination $dest
        $files += @{Path='source/'+$relative; SHA256=(Get-Digest $dest)}
    }
    foreach ($name in @('inspect-legacy-login.txt','capture.txt','legacy-retirement-baseline.txt')) {
        $dest = Join-Path $root $name
        Copy-Item -LiteralPath (Join-Path $evidence $name) -Destination $dest
        $files += @{Path=$name; SHA256=(Get-Digest $dest)}
    }
    $readiness = [ordered]@{}
    foreach ($name in @('IdentityAuthority','KnownArtifacts','DesktopPassword','DesktopRemembered','ApiPassword','ApiRemembered',
        'NeighboringWork','OperationalReadiness','ExactSqlcmdRehearsal','ManualRecovery')) {
        $readiness[$name] = @{Status='PENDING'; Evidence=''; ScopeAndLimits=''}
    }
    $readiness['MeasuredRecoverySeconds'] = $null
    $readiness['KnownCallerDisposition'] = 'PENDING'
    $readiness['FollowUp'] = @{Owner='Brian Downing'; DailyReviewThroughUtc='PENDING'; KnownDueRuns='PENDING'; ReopenedRiskReviewUtc='PENDING'}
    Write-NewJson (Join-Path $root 'readiness.json') $readiness
    $packet = [ordered]@{Revision=$revision; CreatedUtc=[datetime]::UtcNow.ToString('o'); Owner='Brian Downing';
        IndependentHumanVerification='ABSENT - operator-selected single-person process'; Server=$server; User=$user; Database='Shale';
        BeforeHash=$script:Baseline; Files=$files; RiskAcceptance='PENDING'; PackageApproval='PENDING'; WindowAuthorization='PENDING'}
    Write-NewJson (Join-Path $root 'packet.json') $packet
    Write-NewText (Join-Path $root 'BeforeHash.txt') $script:Baseline
    Write-NewText (Join-Path $root 'decisions-PENDING.txt') "Risk acceptance PENDING. Completed-package approval PENDING. Exact execution window PENDING.`r`nNo approval or compatibility finding follows from preparation. Read OPERATOR.md and evidence-scope.md in staged source."
    $runner = Join-Path $root 'source/docs/sql/auth/permission-cutover/retirement-operator.ps1'
    $prefix = "& '$($runner.Replace("'","''"))' -PacketPath '$($root.Replace("'","''"))' -SqlcmdPath '$($SqlcmdPath.Replace("'","''"))'"
    Write-NewText (Join-Path $root 'commands.ps1.txt') @"
# Review only. Apply requires exact UTC values and explicit interactive Brian approvals.
$prefix -Phase Apply -WindowStartUtc 'YYYY-MM-DDTHH:mm:ssZ' -WindowEndUtc 'YYYY-MM-DDTHH:mm:ssZ' -RollbackStartByUtc 'YYYY-MM-DDTHH:mm:ssZ'
$prefix -Phase Verify
$prefix -Phase Rollback
# Guarded SQL entry points staged TOGETHER (no hand-written grants):
# Apply: source/docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql
# Rollback: source/docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql
# Exact command arguments and SQLCMD variables are retained before each invocation.
"@
    $seal = Get-Digest (Join-Path $root 'packet.json')
    Write-NewText (Join-Path $root 'packet-seal.txt') $seal
    Write-Host "PREPARED - NO CONNECTION - NOT AUTHORIZED`nPacket: $root`nRevision: $revision`nPacket SHA256: $seal`nBeforeHash: $script:Baseline`nRisk/package/window: PENDING`nRead commands.ps1.txt and staged OPERATOR.md."
}
function Assert-Readiness($Readiness) {
    foreach ($name in @('IdentityAuthority','KnownArtifacts','DesktopPassword','DesktopRemembered','ApiPassword','ApiRemembered',
        'NeighboringWork','OperationalReadiness','ExactSqlcmdRehearsal','ManualRecovery')) {
        $row = $Readiness.$name
        if ($row.Status -cne 'PASSED' -or -not $row.ScopeAndLimits) { throw "Readiness $name is unresolved. Record only actual bounded evidence; never convert missing tests into PASSED." }
        Assert-SafeValue $row.Evidence
    }
    if ($Readiness.KnownCallerDisposition -cne 'NO_IDENTIFIED_UNRESOLVED_CALLER') { throw 'Identified callers/failures need separate disposition before apply.' }
    if ($Readiness.MeasuredRecoverySeconds -isnot [ValueType] -or [double]$Readiness.MeasuredRecoverySeconds -le 0) { throw 'Measure rollback/reverification INCLUDING manual checks; the 44.57-second suite is insufficient.' }
    if ($Readiness.FollowUp.Owner -cne 'Brian Downing' -or (Get-Utc $Readiness.FollowUp.DailyReviewThroughUtc) -lt [datetime]::UtcNow.AddDays(7) -or
        (Get-Utc $Readiness.FollowUp.ReopenedRiskReviewUtc) -le [datetime]::UtcNow -or $Readiness.FollowUp.KnownDueRuns -eq 'PENDING' -or -not $Readiness.FollowUp.KnownDueRuns) { throw 'Set seven-day/known-due-run follow-up and reopened-risk review dates from real decisions.' }
}
function Get-ApprovalVariables($Packet, $Readiness, [string]$Risk, [string]$Review, [string]$Window) {
    # Only called AFTER explicit decisions. These values record, and do not prove, actual decisions.
    return @('ExpectedDatabase=Shale','LegacyAcceptanceRoute=CONTROLLED_CHANGE','ReviewedIdentityMapping=CONFIRMED',
        'LegacyRetirementApproval=APPROVED','RuntimeLegacyDisposition=DENY_THREE_MODULES','ExclusiveChangeWindow=CONFIRMED',
        'CompletedPackageReview=APPROVED','ControlledWindowAuthorization=AUTHORIZED','UnknownClientRisk=ACCEPTED_NOT_ZERO_PROOF',
        'IrrecoverableEffectsRisk=ACCEPTED','ReopenedCapabilityRisk=ACCEPTED','RepresentativeChecks=PASSED','RollbackRehearsal=PASSED',
        'SecurityOwner=Brian Downing','ServiceOwner=Brian Downing',"RiskApprovalEvidence=$Risk","PackageReviewEvidence=$Review", "WindowAuthorizationEvidence=$Window",
        "KnownArtifactEvidence=$($Readiness.KnownArtifacts.Evidence)","CompatibilityEvidence=$($Readiness.DesktopPassword.Evidence)",
        "OperationalReadinessEvidence=$($Readiness.OperationalReadiness.Evidence)","RollbackRehearsalEvidence=$($Readiness.ExactSqlcmdRehearsal.Evidence)")
}
function Get-AfterHash([string]$Root, $Packet) {
    $hash = Get-SavedHashes (Join-Path $Root 'apply.sqlcmd.txt') 'Apply' $Packet.BeforeHash
    Assert-Hex $hash
    if (Test-Path -LiteralPath (Join-Path $Root 'AfterHash.txt')) {
        if ((Get-Content -LiteralPath (Join-Path $Root 'AfterHash.txt') -Raw).Trim() -ine $hash) { throw 'Saved AfterHash differs from original output.' }
    } else { Write-NewText (Join-Path $Root 'AfterHash.txt') $hash }
    $readyPath=Join-Path $Root 'rollback-ready.json'
    if (-not (Test-Path -LiteralPath $readyPath)) {
        $saved=Read-Json (Join-Path $Root 'authorization.json')
        Write-NewJson $readyPath @{Entry='controlled-rollback-legacy-login.sql'; Variables=(@($saved.Variables)+@('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$($Packet.BeforeHash)","LegacyAfterHash=$hash"))}
    }
    return $hash
}
function Invoke-Retirement {
    if ($Phase -eq 'Prepare') { Prepare-Packet; return }
    if ($Phase -eq 'Rehearse') { Invoke-LocalRehearsal; return }
    $root = [IO.Path]::GetFullPath($PacketPath)
    $packet = Read-Packet $root
    $seal = Get-Digest (Join-Path $root 'packet.json')
    if ((Get-Content -LiteralPath (Join-Path $root 'packet-seal.txt') -Raw).Trim() -cne $seal) { throw 'Packet manifest changed; use original retained packet.' }
    Assert-Hex $packet.BeforeHash
    if ((Get-Content -LiteralPath (Join-Path $root 'BeforeHash.txt') -Raw).Trim() -ine $packet.BeforeHash) { throw 'Original BeforeHash changed.' }
    if ($Phase -eq 'Apply') {
        if (Test-Path -LiteralPath (Join-Path $root 'apply-attempt.json')) { throw 'Apply already attempted; NEVER replay. Inspect outputs/verify/guarded rollback.' }
        $SqlcmdPath = Assert-SupportedSqlcmd $SqlcmdPath
        $ready = Read-Json (Join-Path $root 'readiness.json'); Assert-Readiness $ready
        $readySeal=Get-Digest (Join-Path $root 'readiness.json')
        Write-Host "Review readiness.json SHA256 $readySeal with packet SHA256 $seal"
        $start=Get-Utc $WindowStartUtc; $end=Get-Utc $WindowEndUtc; $by=Get-Utc $RollbackStartByUtc
        Assert-Window $start $end $by ([double]$ready.MeasuredRecoverySeconds)
        Write-Host "UTC NOW $([datetime]::UtcNow.ToString('o')); start $WindowStartUtc; ROLLBACK BY $RollbackStartByUtc; HARD END $WindowEndUtc"
        Write-Host 'Brian holds all roles; independent human verification ABSENT. Exclusive metadata control required. Budget starts at first admission pause or apply, whichever is earlier.'
        Confirm-Exact 'Brian: accept unknown/older/offline clients (20-30 devices, about four inaccessible); incomplete invocation knowledge; irrecoverable accounting/work/external effects; reopened vulnerable rollback capabilities.' 'ACCEPT BOUNDED PRODUCTION RISK'
        $risk=Read-Host 'Actual private risk decision ID'; Assert-SafeValue $risk
        Confirm-Exact "Brian: approve this concrete packet SHA256 $seal and current readiness evidence, including absence of independent human verification." "APPROVE PACKET $seal READINESS $readySeal"
        $review=Read-Host 'Actual completed-package review ID'; Assert-SafeValue $review
        Confirm-Exact "Brian: authorize exactly $WindowStartUtc to $WindowEndUtc; rollback by $RollbackStartByUtc; pre-authorize exact guarded recovery and keep exclusive catalog control. No Stage A/B or unrelated DDL." "AUTHORIZE $WindowStartUtc $WindowEndUtc $RollbackStartByUtc"
        $window=Read-Host 'Actual window authorization ID'; Assert-SafeValue $window
        $vars = @(Get-ApprovalVariables $packet $ready $risk $review $window)
        $auth = @{PacketSHA256=$seal; ReadinessSHA256=$readySeal; Readiness=$ready; Variables=$vars; StartUtc=$WindowStartUtc; EndUtc=$WindowEndUtc; RollbackByUtc=$RollbackStartByUtc;
            DecisionUtc=[datetime]::UtcNow.ToString('o'); IndependentHumanVerification='ABSENT'; RollbackPreauthorized=$true}
        $password = Read-Host 'Brian: SQL operator password (interactive; never written to arguments or packet)' -AsSecureString
        try {
            Assert-Window $start $end $by ([double]$ready.MeasuredRecoverySeconds)
            # Save original authorizations and stage rollback parameters BEFORE any connection/mutation.
            Write-NewJson (Join-Path $root 'authorization.json') $auth
            Write-NewText (Join-Path $root 'authorization-sha256.txt') (Get-Digest (Join-Path $root 'authorization.json'))
            $applyVars = $vars + @("LegacyBeforeHash=$($packet.BeforeHash)","WindowStartUtc=$($start.ToString('s'))","WindowEndUtc=$($end.ToString('s'))","RollbackStartByUtc=$($by.ToString('s'))")
            Write-NewJson (Join-Path $root 'apply-command.json') @{Entry='controlled-retire-legacy-login.sql'; Variables=$applyVars; RollbackEntry='controlled-rollback-legacy-login.sql'; RollbackVariables=($vars+@('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$($packet.BeforeHash)",'LegacyAfterHash=FROM_ORIGINAL_EMITTED_OUTPUT_ONLY'))}
            Write-NewJson (Join-Path $root 'apply-attempt.json') @{Utc=[datetime]::UtcNow.ToString('o'); Outcome='UNCERTAIN_UNTIL_OUTPUT_REVIEW'; PacketSHA256=$seal}
            try {
                $output = Invoke-PacketSql (Join-Path $root 'source') $packet.Server $packet.Database $packet.User $password 'docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql' $applyVars (Join-Path $root 'apply') $SqlcmdPath $by
            } finally {
                # A nonzero exit can occur AFTER commit. Preserve an emitted identity even then.
                if (Test-Path -LiteralPath (Join-Path $root 'apply.sqlcmd.txt')) {
                    try { $null = Get-AfterHash $root $packet } catch { Write-Warning 'Original emitted AfterHash unavailable. Keep all output; exact-state incident review required. No apply replay.' }
                }
            }
            $after = Get-AfterHash $root $packet
            Write-Host "SQL COMMIT REPORTED. BeforeHash $($packet.BeforeHash); AfterHash $after. Acceptance PENDING. Run Verify now. ROLLBACK BY $RollbackStartByUtc; HARD END $WindowEndUtc."
        } finally { $password.Dispose() }
        return
    }
    $auth = Read-Json (Join-Path $root 'authorization.json')
    if ($auth.PacketSHA256 -cne $seal -or (Get-Digest (Join-Path $root 'authorization.json')) -cne
        (Get-Content -LiteralPath (Join-Path $root 'authorization-sha256.txt') -Raw).Trim()) { throw 'Original authorization changed.' }
    $after = Get-AfterHash $root $packet # parse even nonzero original apply: commit may have happened; never recalculate
    $SqlcmdPath = Assert-SupportedSqlcmd $SqlcmdPath
    $password = Read-Host 'Brian: SQL operator password' -AsSecureString
    try {
        if ($Phase -eq 'Rollback') {
            Confirm-Exact 'Exact recovery reopens the vulnerable hash/accounting/runtime capabilities; it cannot repair lost accounting, work, audit or external effects. Original recovery preauthorization applies even after expiry.' 'ROLL BACK ORIGINAL PACKET'
            $vars = @($auth.Variables) + @('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$($packet.BeforeHash)","LegacyAfterHash=$after")
            $log = Join-Path $root ('rollback-'+[guid]::NewGuid().ToString('N'))
            Write-NewJson ($log+'.command.json') @{Entry='controlled-rollback-legacy-login.sql'; Variables=$vars; Utc=[datetime]::UtcNow.ToString('o')}
            $watch=[Diagnostics.Stopwatch]::StartNew()
            $output = Invoke-PacketSql (Join-Path $root 'source') $packet.Server $packet.Database $packet.User $password 'docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql' $vars $log $SqlcmdPath
            if ((Get-SavedHashes $output 'Rollback') -ine $packet.BeforeHash) { throw 'Rollback equality not confirmed. Escalate exact-state incident; never replay grants.' }
            $watch.Stop()
            Write-NewJson (Join-Path $root 'rolled-back.json') @{Utc=[datetime]::UtcNow.ToString('o'); BeforeHash=$packet.BeforeHash; SqlSeconds=$watch.Elapsed.TotalSeconds; Output=$output}
            Write-Host 'Exact original catalog restoration reported. Repeat safe app checks and reconcile effects; SQL time alone is not complete recovery.'
        } else {
            $rolledBack = Test-Path -LiteralPath (Join-Path $root 'rolled-back.json')
            $expected = if ($rolledBack) { $packet.BeforeHash } else { $after }
            $log = Join-Path $root ('verify-'+[guid]::NewGuid().ToString('N'))
            $scriptFile = if ($rolledBack) { 'legacy-retirement-baseline.sql' } else { 'operator-postcheck.sql' }
            $vars = @('ExpectedDatabase=Shale',"LegacyAfterHash=$expected")
            $deadline=$null
            if (-not $rolledBack) {
                $deadline=Get-Utc $auth.RollbackByUtc
                Write-Host "ROLLBACK BY $((Get-Utc $auth.RollbackByUtc).ToString('o')); HARD END $((Get-Utc $auth.EndUtc).ToString('o'))"
                if ([datetime]::UtcNow -ge $deadline) { throw 'ROLLBACK NOW: deadline reached; do not spend recovery reserve on trial checks.' }
            }
            $output = Invoke-PacketSql (Join-Path $root 'source') $packet.Server $packet.Database $packet.User $password ("docs/sql/auth/permission-cutover/"+$scriptFile) $vars $log $SqlcmdPath $deadline
            if ($rolledBack -and (Get-SavedHashes $output 'Baseline') -ine $expected) { throw 'Original catalog equality not confirmed.' }
            if (-not $rolledBack -and (Get-Content -LiteralPath $output -Raw) -notmatch '(?m)^\s*OperatorCatalogVerified\s*$') { throw 'Catalog verification output incomplete.' }
            Write-Host "1. CATALOG CHECK COMPLETED (no legacy procedure invocation).`n2. Run safe PC-000 desktop/API password and remembered checks with EXISTING settings. Record results now; never invent replay/isolation/API tests.`n3. Unknown clients and direct-SQL failures remain unobserved; buffered API logs do not establish comprehensive coverage."
            $result = Read-Host 'Observed safe representative app outcome: PASSED, FAILED or INCOMPLETE'
            if ($result -cnotin @('PASSED','FAILED','INCOMPLETE')) { throw 'Record an actual outcome.' }
            $notes=Read-Host 'Private check reference and scope/limits (no passwords, tokens or personal data)'
            $record=@{Utc=[datetime]::UtcNow.ToString('o'); CatalogOutput=$output; SafeRepresentativeOutcome=$result; Scope=$notes; UnknownClientCompatibility='UNKNOWN'; ApiLogLimit='BUFFERED; NO DIRECT-SQL COVERAGE'}
            Write-NewJson ($log+'.checks.json') $record
            if (-not $rolledBack) {
                Write-Host "ROLLBACK BY $((Get-Utc $auth.RollbackByUtc).ToString('o')); HARD END $((Get-Utc $auth.EndUtc).ToString('o')). No automatic extension; no automatic acceptance."
                if ($result -ne 'PASSED' -or [datetime]::UtcNow -ge (Get-Utc $auth.RollbackByUtc)) { throw 'ROLLBACK NOW using saved packet. Failed/incomplete acceptance or deadline reached; incident lead Brian.' }
                Write-Host 'Safe checks reported; full runbook acceptance remains a Brian decision. Monitor each minute during trial/first staffed hour; roll back on any failure, complaint or lost visibility.'
            }
        }
    } finally { $password.Dispose() }
}

# Local rehearsal implementation is defined below; dot-sourcing exposes functions for offline tests only.
function Invoke-LocalRehearsal {
    # Bounded missing SQLCMD rehearsal only. No Azure, Docker, fleet inventory or Maven run.
    if ($LocalPort -lt 1024 -or $LocalPort -gt 65535) { throw 'Dedicated loopback port required.' }
    $revision = Get-SourceRevision $script:SourceRoot $ReviewedRevision
    $root = Assert-PrivatePath $PacketPath $script:SourceRoot
    if (Test-Path -LiteralPath $root) { throw 'Use a fresh LOCAL rehearsal output directory.' }
    $SqlcmdPath = Assert-SupportedSqlcmd $SqlcmdPath
    Confirm-Exact 'This creates and later removes ONE new synthetic database on the existing dedicated LOCAL disposable instance. No Shale data/copies/credentials. No existing database is adopted.' 'LOCAL DISPOSABLE SQLCMD REHEARSAL'
    $user=Read-Host 'Dedicated LOCAL SQL admin username (never production credentials)'
    $password=Read-Host 'Dedicated LOCAL SQL admin password' -AsSecureString
    New-Item -ItemType Directory -Path $root | Out-Null
    $nonce=[guid]::NewGuid().ToString('N'); $database='RetireRehearsal_'+$nonce
    $server='tcp:127.0.0.1,'+$LocalPort
    Write-NewJson (Join-Path $root 'local-owner.json') @{Database=$database; Nonce=$nonce; Server=$server; Revision=$revision; ManualRecovery='PENDING'; ProductionAcceptance='PENDING'}
    try {
        $localGuard = "IF CONVERT(int,SERVERPROPERTY('EngineEdition')) NOT IN(2,3,4) OR DB_NAME()<>N'$database' OR CONVERT(nvarchar(128),(SELECT value FROM sys.extended_properties WHERE class=0 AND name=N'RetirementRehearsalNonce'))<>N'$nonce' OR NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'RetirementRehearsalNonce') THROW 58500,N'Exact local ownership required',1;`r`nGO`r`n"
        # Each SQLCMD connection, including every rejection, receives the same local nonce guard.
        function Local-Sql([string]$Name,[string]$Sql,[string[]]$Vars=@(),[int]$ExpectedError=0,[string]$Db=$database) {
            $script:localSequence++
            $inputPath=Join-Path $root ($script:localSequence.ToString()+'-'+$Name+'.sql')
            if ($Db -eq $database) { $Sql=$localGuard+$Sql }
            Write-NewText $inputPath $Sql
            $log=Join-Path $root ($script:localSequence.ToString()+'-'+$Name)
            $caught=$false; $failure=''
            if ([datetime]::UtcNow -ge $script:localDeadline) { throw 'Local rehearsal 15-minute bound reached; preserve owned resource/evidence for exact review.' }
            try { $null=Invoke-PacketSql $script:SourceRoot $server $Db $user $password $inputPath $Vars $log $SqlcmdPath $script:localDeadline -LocalOnly }
            catch { $caught=$true; $failure=$_.Exception.Message; if (-not $ExpectedError) { throw } }
            if ($ExpectedError) {
                Assert-ExpectedSqlRefusal $log $ExpectedError $caught $failure
            }
            return ($log+'.sqlcmd.txt')
        }
        $script:localSequence=0
        $script:localDeadline=[datetime]::UtcNow.AddMinutes(15)
        $create="IF CONVERT(int,SERVERPROPERTY('EngineEdition')) NOT IN(2,3,4) OR DB_NAME()<>N'master' OR DB_ID(N'$database') IS NOT NULL THROW 58500,N'Fresh local target required',1;`r`nGO`r`nCREATE DATABASE [$database] COLLATE SQL_Latin1_General_CP1_CI_AS;`r`nGO`r`nUSE [$database];`r`nEXEC sys.sp_addextendedproperty @name=N'RetirementRehearsalNonce',@value=N'$nonce';`r`nGO`r`n"
        $null=Local-Sql 'create' $create @() 0 'master'
        $setup=@'
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
CREATE TABLE dbo.Users(id int, email nvarchar(255), password_hash nvarchar(255), is_deleted bit, failed_attempts int, lock_until datetime2, last_login datetime2);
CREATE TABLE dbo.AuthConfig(id int, max_attempts int, lock_minutes int);
CREATE USER shale_app WITHOUT LOGIN;
CREATE USER shale_runtime WITHOUT LOGIN;
CREATE ROLE shale_runtime_role AUTHORIZATION dbo;
ALTER ROLE shale_runtime_role ADD MEMBER shale_runtime;
GRANT SELECT ON dbo.Users TO shale_app AS dbo;
DENY VIEW DEFINITION TO shale_app AS dbo;
GRANT EXECUTE TO shale_runtime AS dbo;
GRANT EXECUTE ON SCHEMA::dbo TO shale_runtime_role AS dbo;
GO
CREATE PROC dbo.ResolveDesktopRememberCredential AS SELECT 1 AS SyntheticOnly;
GO
GRANT EXECUTE ON dbo.ResolveDesktopRememberCredential TO shale_app AS dbo;
GO
'@
        # Terminate the here-string's final GO once. A blank line after later GOs
        # becomes part of the next stored procedure definition through :r.
        $setup += "`r`n"
        foreach ($name in @('sp_login_get_hash','sp_login_mark_failure','sp_login_mark_success')) {
            $body=[IO.File]::ReadAllText((Join-Path $script:SourceRoot ('shale-server/src/test/resources/auth/'+$name+'.sql'))).Replace("`r`n","`n").Replace("`n","`r`n")
            $bodyFile=Join-Path $root ($name+'.sql'); Write-NewText $bodyFile $body
            $setup += ":r `"$bodyFile`"`r`nGO`r`nGRANT EXECUTE ON dbo.$name TO shale_app AS dbo;`r`nGO`r`n"
        }
        $null=Local-Sql 'setup' $setup
        $baseOut=Local-Sql 'baseline' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$database")
        $before=Get-SavedHashes $baseOut 'Baseline'; Write-NewText (Join-Path $root 'BeforeHash.txt') $before
        # Explicitly SYNTHETIC acknowledgements, isolated here; never read by production Apply.
        $vars=@("ExpectedDatabase=$database",'LegacyAcceptanceRoute=CONTROLLED_CHANGE','ReviewedIdentityMapping=CONFIRMED',
            'LegacyRetirementApproval=APPROVED','RuntimeLegacyDisposition=DENY_THREE_MODULES','ExclusiveChangeWindow=CONFIRMED',
            'CompletedPackageReview=APPROVED','ControlledWindowAuthorization=AUTHORIZED','UnknownClientRisk=ACCEPTED_NOT_ZERO_PROOF',
            'IrrecoverableEffectsRisk=ACCEPTED','ReopenedCapabilityRisk=ACCEPTED','RepresentativeChecks=PASSED','RollbackRehearsal=PASSED',
            'SecurityOwner=SYNTHETIC','ServiceOwner=SYNTHETIC')
        foreach ($key in @('RiskApprovalEvidence','PackageReviewEvidence','WindowAuthorizationEvidence','KnownArtifactEvidence','CompatibilityEvidence','OperationalReadinessEvidence','RollbackRehearsalEvidence')) { $vars += "$key=SYNTHETIC_LOCAL_ONLY" }
        $now=[datetime]::UtcNow
        $times=@("WindowStartUtc=$($now.AddMinutes(-1).ToString('s'))","WindowEndUtc=$($now.AddMinutes(28).ToString('s'))","RollbackStartByUtc=$($now.AddMinutes(17).ToString('s'))")
        $apply=@($vars)+@("LegacyBeforeHash=$before")+$times
        $entry=':r docs/sql/auth/permission-cutover/controlled-retire-legacy-login.sql'
        $null=Local-Sql 'missing-decision' $entry @($apply | ForEach-Object { if ($_ -eq 'CompletedPackageReview=APPROVED') { 'CompletedPackageReview=PENDING' } else { $_ } }) 58360
        $null=Local-Sql 'expired-window' $entry @($apply | ForEach-Object { if ($_ -like 'RollbackStartByUtc=*') { 'RollbackStartByUtc=2000-01-01T00:00:00' } else { $_ } }) 58361
        $null=Local-Sql 'drift' $entry @($apply | ForEach-Object { if ($_ -like 'LegacyBeforeHash=*') { 'LegacyBeforeHash=0x'+('00'*32) } else { $_ } }) 58344
        # Post-mutation failure must close SQLCMD's connection and roll back every ACL change.
        $null=Local-Sql 'inject-postcheck' 'CREATE ROLE ShaleAdmins; ALTER ROLE ShaleAdmins ADD MEMBER shale_runtime;'
        $injected=Local-Sql 'injected-baseline' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$database")
        $injectedHash=Get-SavedHashes $injected 'Baseline'
        $null=Local-Sql 'atomic-refusal' $entry @($apply | ForEach-Object { if ($_ -like 'LegacyBeforeHash=*') { "LegacyBeforeHash=$injectedHash" } else { $_ } }) 58347
        $check=Local-Sql 'atomic-equality' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$database")
        if ((Get-SavedHashes $check 'Baseline') -ine $injectedHash) { throw 'SQLCMD failure did not preserve exact injected baseline.' }
        $null=Local-Sql 'remove-injection' 'ALTER ROLE ShaleAdmins DROP MEMBER shale_runtime; DROP ROLE ShaleAdmins;'
        $out=Local-Sql 'apply' $entry $apply
        $after=Get-SavedHashes $out 'Apply' $before; Write-NewText (Join-Path $root 'AfterHash.txt') $after
        $null=Local-Sql 'catalog' ':r docs/sql/auth/permission-cutover/operator-postcheck.sql' @("ExpectedDatabase=$database","LegacyAfterHash=$after")
        $rollback=@($vars)+@('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED',"LegacyBeforeHash=$before","LegacyAfterHash=$after")
        $rollbackEntry=':r docs/sql/auth/permission-cutover/controlled-rollback-legacy-login.sql'
        $null=Local-Sql 'rollback-drift' $rollbackEntry @($rollback | ForEach-Object { if ($_ -like 'LegacyAfterHash=*') { 'LegacyAfterHash=0x'+('00'*32) } else { $_ } }) 58344
        $watch=[Diagnostics.Stopwatch]::StartNew()
        $out=Local-Sql 'rollback' $rollbackEntry $rollback
        if ((Get-SavedHashes $out 'Rollback') -ine $before) { throw 'Exact recovery failed.' }
        $out=Local-Sql 'reverification' ':r docs/sql/auth/permission-cutover/legacy-retirement-baseline.sql' @("ExpectedDatabase=$database")
        if ((Get-SavedHashes $out 'Baseline') -ine $before) { throw 'Recovery reverification failed.' }
        $watch.Stop()
        Write-NewJson (Join-Path $root 'rehearsal-result.json') @{SqlcmdRecoveryAndCatalogSeconds=$watch.Elapsed.TotalSeconds; ManualAppRecovery='NOT MEASURED'; ExactSqlcmdChecks='PASSED_LOCAL_ONLY'; Revision=$revision; BeforeHash=$before; AfterHash=$after}
        # Only exact newly created database, original nonce, no forced disconnect or SINGLE_USER.
        $drop="USE master;`r`nGO`r`nIF CONVERT(int,SERVERPROPERTY('EngineEdition')) NOT IN(2,3,4) OR NOT EXISTS(SELECT 1 FROM [$database].sys.extended_properties WHERE class=0 AND name=N'RetirementRehearsalNonce' AND CONVERT(nvarchar(128),value)=N'$nonce') THROW 58500,N'Exact local owner required',1;`r`nDROP DATABASE [$database];`r`nGO`r`n"
        $null=Local-Sql 'exact-cleanup' $drop @() 0 'master'
        Write-Host "LOCAL SQLCMD REHEARSAL COMPLETED; retained $root. SQL-only recovery/reverification $($watch.Elapsed.TotalSeconds) seconds. Manual recovery and all production decisions remain PENDING."
    } finally { $password.Dispose() }
    # On failure preserve database/output/nonce for exact review; no name-pattern cleanup or retry.
}
if ($MyInvocation.InvocationName -ne '.') {
    try { Invoke-Retirement }
    catch { Write-Error $_ -ErrorAction Continue; exit 1 }
}
