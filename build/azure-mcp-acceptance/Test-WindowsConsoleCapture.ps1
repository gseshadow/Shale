#Requires -Version 7.0
<# OFFLINE ONLY: starts a local pwsh fixture, NEVER SQLCMD or a network client.
   Type the public word OFFLINE, never an actual password. #>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { throw 'STOP: this check requires a local Windows PowerShell 7 console.' }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory = Assert-PrivatePath (Join-Path $repo ('work/console-probe-' + [guid]::NewGuid().ToString('N')))
[void][IO.Directory]::CreateDirectory($directory)
$child = Join-Path $directory 'offline-console-child.ps1'
try {
    [IO.File]::WriteAllText($child, @'
$ErrorActionPreference='Stop'
if ([Console]::IsInputRedirected) { exit 21 }
[Console]::Error.Write('Password: ')
$typed=[Text.StringBuilder]::new()
do {
 $key=[Console]::ReadKey($true)
 if ($key.Key -ne [ConsoleKey]::Enter) { [void]$typed.Append($key.KeyChar) }
} while ($key.Key -ne [ConsoleKey]::Enter)
if ($typed.ToString() -cne 'OFFLINE') { exit 22 }
$typed.Clear() | Out-Null
[Console]::Out.WriteLine('CAPTURE_OK')
[Console]::Error.WriteLine('Msg 50001, Level 16, State 1, synthetic offline fixture')
exit 17
'@, [Text.UTF8Encoding]::new($false))
    Write-Host 'OFFLINE console check: wait for PASSWORD INPUT READY, then type the public word OFFLINE and press Enter. Input must remain invisible. Never enter a SQL password.'
    $executable = (Get-Process -Id $PID).Path
    $result = & (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -RequireConsole -Executable $executable -Arguments @('-NoLogo','-NoProfile','-File',$child)
    $numbers = @(Get-SafeSqlErrorNumbers $result.Lines)
    if ($result.FailureCode -or -not $result.PasswordInputReadyShown -or $result.ExitCode -ne 17 -or $result.Lines[0].Trim() -cne 'CAPTURE_OK' -or $numbers.Count -ne 1 -or $numbers[0] -ne 50001) { throw 'STOP: offline console transport check failed; do not execute acceptance SQL.' }
    Write-Output "OFFLINE_CONSOLE_CAPTURE_PASS PowerShell=$($PSVersionTable.PSVersion); synthetic exit=17 SQL-error=50001. This is not ODBC/SQL acceptance."
} finally {
    if (Test-Path -LiteralPath $child) { Remove-Item -LiteralPath $child }
    if (Test-Path -LiteralPath $directory) { Remove-Item -LiteralPath $directory }
}
