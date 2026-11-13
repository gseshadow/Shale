#Requires -Version 7.0
<# Local, SQL-free ODBC console test. A loopback TCP listener that speaks NO TDS
   prevents authentication and SQL execution. No Azure/acceptance endpoint or
   private packet is used. Type OFFLINE, never a real password. #>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$SqlcmdExecutable)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { throw 'STOP: a local Windows PowerShell 7 console is required.' }
foreach ($item in Get-ChildItem Env:) {
    if ($item.Name -match '^SQLCMD' -and $item.Value) { throw 'STOP: clear ambient SQLCMD overrides; no real credentials are permitted in this probe.' }
}
$client = Get-Item -LiteralPath $SqlcmdExecutable -ErrorAction Stop
if ($client.PSIsContainer -or $client.Extension -ine '.exe') { throw 'STOP: select the independently reviewed Microsoft ODBC SQLCMD executable.' }
Write-Host "Local ODBC probe executable SHA256=$((Get-FileHash -LiteralPath $client.FullName -Algorithm SHA256).Hash)"
if ((Read-Host 'Confirm reviewed ODBC executable and loopback-only offline test; type ODBC_OFFLINE_LOOPBACK_ONLY') -cne 'ODBC_OFFLINE_LOOPBACK_ONLY') { throw 'STOP: probe not approved.' }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$accepted = $null
try {
    # Keep the ephemeral port owned by this inert listener for the entire child
    # lifetime. It cannot accidentally select a local SQL Server or another host.
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $accepted = $listener.AcceptTcpClientAsync()
    $arguments = @('-S',"tcp:127.0.0.1,$port",'-d','OfflinePromptProbe','-U','offline_probe',
        '-N','-b','-V','16','-r','1','-l','1','-t','1','-x','-X1','-f','65001')
    Write-Host 'OFFLINE ODBC test: wait for PASSWORD INPUT READY, then type the public word OFFLINE and press Enter. It must stay invisible. Never enter a SQL password. A login/prelogin failure is EXPECTED: the listener is not a SQL server.'
    $result = & (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -RequireConsole -Executable $client.FullName -Arguments $arguments
    # Seeing an English native password prompt plus a nonzero native exit proves
    # the prompt path occurred; hidden-input behavior must also be observed locally.
    if ($result.FailureCode -or -not $result.PasswordInputReadyShown -or -not $result.PasswordPromptObserved -or $null -eq $result.ExitCode -or $result.ExitCode -eq 0) { throw 'STOP: ODBC console probe inconclusive/failed; do not execute acceptance SQL.' }
    if ((Read-Host 'Confirm OFFLINE input was invisible and no real credential was entered; type HIDDEN_INPUT_CONFIRMED') -cne 'HIDDEN_INPUT_CONFIRMED') { throw 'STOP: hidden input not confirmed.' }
    Write-Output "OFFLINE_ODBC_PROMPT_PASS PowerShell=$($PSVersionTable.PSVersion); synthetic login exit=$($result.ExitCode); no SQL server or query used."
} finally {
    # No TDS response, login authentication, SQL text, credential file or raw log.
    $listener.Stop()
    if ($accepted -and $accepted.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion) { $accepted.Result.Dispose() }
}
