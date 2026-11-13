#Requires -Version 7.0
<# SQL-free ODBC test: help-only identity check, then an owned loopback listener
   that speaks NO TDS. No Azure, SQL server/query, real credential or packet. #>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$SqlcmdExecutable)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'OdbcProbe.ps1')
if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { throw 'STOP: a local Windows PowerShell 7 console is required.' }
foreach ($item in Get-ChildItem Env:) {
    if ($item.Name -match '^SQLCMD' -and $item.Value) { throw 'STOP: clear ambient SQLCMD overrides; no real credentials are permitted in this probe.' }
}
try {
    $client = Get-Item -LiteralPath $SqlcmdExecutable -ErrorAction Stop
    if ($client.PSIsContainer -or $client.Extension -ine '.exe') { throw 'Not executable' }
    $hash = (Get-FileHash -LiteralPath $client.FullName -Algorithm SHA256).Hash
} catch { throw 'STOP: CLIENT_FILE_UNAVAILABLE; select the independently reviewed Microsoft ODBC SQLCMD .exe.' }
Write-Host "Local SQLCMD executable SHA256=$hash"
$identity = Read-SqlcmdClientIdentity $hash
if ($identity.Family -ceq 'GO_SQLCMD') {
    Write-Host "ODBC_PROBE phase=CLIENT_IDENTITY status=WRONG_CLIENT_GO_SQLCMD version=$($identity.Version) evidence=$($identity.Evidence) started=False exited=False exit=NOT_STARTED timeout=False prompt=False ready=False loopbackAccepted=False; listener not started."
    throw 'STOP: this is Go SQLCMD, not ODBC SQLCMD. Select the actual Microsoft ODBC 17/18 client; no compatibility fallback or credential workaround is permitted.'
}
if ((Read-Host 'Confirm reviewed ODBC executable and loopback-only offline test; type ODBC_OFFLINE_LOOPBACK_ONLY') -cne 'ODBC_OFFLINE_LOOPBACK_ONLY') { throw 'STOP: probe not approved.' }
# Help exits before login. Keep -X1 so startup scripts/SQLCMD environment are not a fallback.
$help = & (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -Executable $client.FullName -Arguments @('-X1','-?') -WatchdogSeconds 5
$helpDiagnosis = Get-OdbcProbeDiagnosis $help -HelpOnly
Write-OdbcProbeDiagnosis 'CLIENT_HELP' $helpDiagnosis
if ($helpDiagnosis.Status -cne 'HELP_COMPLETED') { throw 'STOP: CLIENT_HELP_FAILED; inspect the safe process diagnostics above. No listener was started.' }
$identity = Read-SqlcmdClientIdentity $hash $help.Lines
Write-Host "ODBC_PROBE phase=CLIENT_IDENTITY family=$($identity.Family) version=$($identity.Version) evidence=$($identity.Evidence)"
if ($identity.Family -cne 'ODBC_SQLCMD') { throw 'STOP: ODBC_CLIENT_NOT_CONFIRMED; select the genuine ODBC client, not Go SQLCMD. No listener was started.' }
if ([int]$identity.Version.Split('.')[0] -notin @(17,18)) { throw 'STOP: ODBC_VERSION_NOT_REVIEWED; select the approved ODBC 17/18 tool. No listener was started.' }
$help=$null
$listener=$null; $accepted=$null; $result=$null; $harnessFailure=''; $listenerAccepted=$false
try {
    $harnessFailure='LISTENER_START_FAILED'
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $harnessFailure='ACCEPT_FAILED'
    $accepted = $listener.AcceptTcpClientAsync()
    $arguments = @('-S',"tcp:127.0.0.1,$port",'-d','OfflinePromptProbe','-U','offline_probe',
        '-N','-b','-V','16','-r','1','-l','5','-t','1','-x','-X1','-f','65001')
    Write-Host 'OFFLINE ODBC test: wait for PASSWORD INPUT READY, then type the public word OFFLINE and press Enter. It must stay invisible. Never enter a SQL password. The inert listener cannot authenticate; a prelogin failure is expected. The probe watchdog is 45 seconds.'
    $harnessFailure='TRANSPORT_CALL_FAILED'
    $result = & (Join-Path $PSScriptRoot 'Invoke-NativeSqlcmd.ps1') -RequireConsole -Executable $client.FullName -Arguments $arguments -WatchdogSeconds 45
    $harnessFailure=''
    if (-not $accepted.IsCompleted) { try { [void]$accepted.Wait(100) } catch { $harnessFailure='ACCEPT_FAILED' } }
    if ($accepted.IsFaulted) { $harnessFailure='ACCEPT_FAILED' }
    $listenerAccepted = $accepted.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion
} catch {
    # Stage-specific closed code only; never the exception or native output.
} finally {
    try {
        if ($listener) { $listener.Stop() }
        if ($accepted -and $accepted.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion) { $accepted.Result.Dispose() }
    } catch { $harnessFailure='CLEANUP_FAILED' }
}
$diagnosis = Get-OdbcProbeDiagnosis $result $listenerAccepted $harnessFailure
Write-OdbcProbeDiagnosis 'LOGIN_PROBE' $diagnosis
if ($diagnosis.Status -cne 'EXPECTED_PRELOGIN_FAILURE') { throw 'STOP: ODBC console probe not passed. Use the status/exit/prompt/loopback fields above; do not execute acceptance SQL.' }
if ((Read-Host 'Confirm OFFLINE input was invisible and no real credential was entered; type HIDDEN_INPUT_CONFIRMED') -cne 'HIDDEN_INPUT_CONFIRMED') { throw 'STOP: hidden input not confirmed.' }
Write-Output "OFFLINE_ODBC_PROMPT_PASS PowerShell=$($PSVersionTable.PSVersion); clientVersion=$($identity.Version); synthetic login exit=$($diagnosis.ExitCode); no SQL server or query used."
