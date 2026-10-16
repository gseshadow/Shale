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
    # -? is intentionally LAST, so preceding switches/values are parsed first.
    # .invalid is reserved and never a SQL endpoint; no authentication supplied.
    foreach ($encryption in @('-C','-N')) {
        $arguments=@('-S','sql.invalid','-d','Synthetic Database','-U','SyntheticUser',
            '-b','-V','16','-l','1','-t','1','-y','0','-Y','0','-w','65535','-u',
            '-i',$inputFile,'-o',(Join-Path $root ($encryption+' output with spaces.txt')),$encryption,
            '-v','SecurityOwner=Brian Downing','ExpectedDatabase=Synthetic Database','-?')
        # -? terminates the -v list; build it separately because variable validation
        # deliberately disallows option injection inside the runner's variable list.
        $psi=New-SqlcmdStartInfo $executable $root $arguments[0..($arguments.Length-2)]
        $psi.Arguments += ' -?'
        $result=Invoke-HelpProcess $psi
        if ($result.ExitCode -ne 0 -or $result.Text -notmatch 'Microsoft \(R\) SQL Server Command Line Tool') {
            throw "Real ODBC rejected corrected arguments ($encryption): $($result.Text)"
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
        Write-Output ('PASS real Windows ODBC quoted-switch reproduction and corrected help parsing on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
    } else {
        Write-Output 'PASS real Linux ODBC corrected help parsing; Windows quoted-switch reproduction NOT RUN'
    }
} finally { Remove-Item -LiteralPath $root -Recurse -Force }
