# Real Microsoft ODBC SQLCMD parsing only. No SQL connection, credentials or rehearsal.
# Native Windows PS5.1 is authoritative for the Windows raw command-line regression.
param([Parameter(Mandatory=$true)][string]$SqlcmdPath, [string]$RunnerPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if (-not $RunnerPath) { $RunnerPath=Join-Path $PSScriptRoot '../../docs/sql/auth/permission-cutover/retirement-operator.ps1' }
$requestedSqlcmd=$SqlcmdPath
. $RunnerPath
$executable=Assert-SupportedSqlcmd $requestedSqlcmd
$root=Join-Path ([IO.Path]::GetTempPath()) ('retirement real odbc '+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
function Invoke-HelpProcess($Psi) {
    $process=New-Object Diagnostics.Process
    $process.StartInfo=$Psi
    try {
        if (-not $process.Start()) { throw 'Real ODBC process did not start' }
        $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(10000)) { $process.Kill(); $process.WaitForExit(); throw 'Help-only parsing exceeded 10 seconds' }
        return @{ExitCode=$process.ExitCode; Text=($stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult())}
    } finally { $process.Dispose() }
}
try {
    $inputFile=Join-Path $root 'input with spaces.sql'
    [IO.File]::WriteAllText($inputFile,'-- HELP ONLY: never execute SQL')
    # Capture the COMPLETE actual rehearsal variable list at invocation 4. Only
    # test transport/prompts are stubbed; no SQL connection or database is created.
    $PacketPath=Join-Path $root 'synthetic rehearsal shapes'
    function Get-SourceRevision { return ('a'*40) }
    function Assert-SupportedSqlcmd { return $executable }
    function Confirm-Exact { }
    function Read-Host { param($Prompt,[switch]$AsSecureString)
        if ($AsSecureString) { return (ConvertTo-SecureString 'SYNTHETIC_ONLY' -AsPlainText -Force) }
        return 'SyntheticUser'
    }
    function Invoke-PacketSql {
        param($Root,$Server,$Database,$User,$Password,$InputFile,$Variables,$Log,$Executable,$Deadline,[switch]$LocalOnly)
        if ($InputFile -like '*-create.sql' -or $InputFile -like '*-setup.sql') { return 'NO_SQL_CONNECTION' }
        if ($InputFile -like '*-baseline.sql') {
            Write-NewText ($Log+'.sqlcmd.txt') ('LegacyRetirementBaseline '+$script:Baseline)
            return ($Log+'.sqlcmd.txt')
        }
        if ($InputFile -notlike '*-missing-decision.sql') { throw 'Unexpected shape capture invocation' }
        $script:rehearsalVariables=@($Variables)
        throw 'SYNTHETIC_SHAPES_CAPTURED'
    }
    function Assert-ExpectedSqlRefusal { throw 'SYNTHETIC_SHAPES_CAPTURED' }
    $message=''
    try { Invoke-LocalRehearsal } catch { $message=$_.Exception.Message }
    if ($message -cne 'SYNTHETIC_SHAPES_CAPTURED') { throw "Rehearsal shape capture failed: $message" }
    # Production names/evidence come from the actual approval-variable builder.
    $ready=[pscustomobject]@{}
    foreach ($key in @('KnownArtifacts','DesktopPassword','OperationalReadiness','ExactSqlcmdRehearsal')) {
        $ready | Add-Member NoteProperty $key ([pscustomobject]@{Evidence='Synthetic Record:20261016-01'})
    }
    $production=@(Get-ApprovalVariables ([pscustomobject]@{Database='Shale'}) $ready 'Synthetic Risk:20261016-01' 'Synthetic Review:20261016-02' 'Synthetic Window:20261016-03')
    $times=@('WindowStartUtc=2026-10-16T21:58:55','WindowEndUtc=2026-10-16T22:27:55','RollbackStartByUtc=2026-10-16T22:16:55')
    $hashes=@(('LegacyBeforeHash='+$script:Baseline),('LegacyAfterHash=0x'+('CD'*32)))
    $shapes=[ordered]@{
        Rehearsal=$script:rehearsalVariables
        Apply=($production+@($hashes[0])+$times)
        Rollback=($production+@('LegacyRollbackApproval=APPROVED','RollbackCompatibilityReviewed=CONFIRMED')+$hashes)
        Verify=@('ExpectedDatabase=Shale',$hashes[1])
    }
    # Extra transport edge cases; production record validation remains unchanged.
    $edges=@('EvidencePath=C:\Evidence\','SpacedEvidencePath=C:\Evidence records\')
    # -? is LAST so every preceding option/assignment is parsed. Reserved .invalid
    # and no credentials: help only, never a SQL connection or SQL execution.
    foreach ($shape in $shapes.Keys) {
      foreach ($encryption in @('-C','-N')) {
        $arguments=@('-S','sql.invalid','-d','Synthetic Database','-U','SyntheticUser',
            '-b','-V','16','-l','1','-t','1','-y','0','-Y','0','-w','65535','-u',
            '-i',$inputFile,'-o',(Join-Path $root ($shape+$encryption+' output with spaces.txt')),$encryption,
            '-v')+@($shapes[$shape])+$edges
        $psi=New-SqlcmdStartInfo $executable $root $arguments
        $psi.Arguments += ' -?'
        $result=Invoke-HelpProcess $psi
        Write-NewJson (Join-Path $root ($shape+$encryption+'.json')) @{Arguments=$psi.Arguments; Result=$result}
        if ($result.ExitCode -ne 0 -or $result.Text -notmatch 'Microsoft \(R\) SQL Server Command Line Tool' -or $result.Text -match '(?i)Invalid argument|Unknown Option') {
            throw "Real ODBC rejected complete $shape arguments ($encryption): $($result.Text)"
        }
      }
    }
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        # Negative control: exact old EVERY-token-quoted serializer, real Windows ODBC.
        # Unknown Option is the defect; a permissive argv echo/fake cannot prove this.
        $psi=New-SqlcmdStartInfo $executable $root @('-?')
        $psi.Arguments='"-S" "sql.invalid" "-?"'
        $old=Invoke-HelpProcess $psi
        if ($old.ExitCode -eq 0 -or $old.Text -notmatch '(?i)Unknown Option') {
            throw "Old serializer did not reproduce Windows ODBC Unknown Option: $($old.Text)"
        }
        # Negative control for the remaining defect: keep switches bare, leave
        # only non-whitespace -v values unquoted, exactly as the reviewed runner did.
        $psi=New-SqlcmdStartInfo $executable $root @('-?')
        $oldVariables=foreach ($assignment in $shapes.Rehearsal) {
            $pair=$assignment -split '=',2
            if ($pair[1] -match '\s') { $pair[0]+'="'+$pair[1]+'"' } else { $assignment }
        }
        $psi.Arguments='-v '+($oldVariables -join ' ')+' -?'
        $oldValues=Invoke-HelpProcess $psi
        Write-NewJson (Join-Path $root 'unquoted-timestamps-negative.json') @{Arguments=$psi.Arguments; Result=$oldValues}
        if ($oldValues.ExitCode -eq 0 -or $oldValues.Text -notmatch '(?i)Invalid argument') {
            throw "Whitespace-only serializer did not reproduce Windows ODBC timestamp parser failure: $($oldValues.Text)"
        }
        Write-Output ('PASS real Windows ODBC quoted-switch reproduction and complete-variable timestamp reproduction/corrected help parsing on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
    } else {
        Write-Output 'PASS real Linux ODBC complete-variable help parsing; native Windows timestamp/quoted-switch reproduction NOT RUN'
    }
} finally { Write-Output "Retained help-only regression evidence: $root" }
