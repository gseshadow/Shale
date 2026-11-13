#Requires -Version 7.0
<# Internal process transport. Output is returned in memory to the guarded wrapper,
   never forwarded to the terminal or written to a temporary file. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string[]]$Arguments,
    [switch]$RequireConsole,
    [ValidateRange(0,600)][int]$WatchdogSeconds = 0
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$process = $null
$started = $false
$stdout = $null
$stderr = $null
$failure = 'PROCESS_START_FAILED'
$promptObserved = $false
$promptShown = $false
$timedOut = $false
$osError = $null
$streams = @()
$clock = [Diagnostics.Stopwatch]::StartNew()
function Stop-CapturedChild {
    try { if ($process -and $started -and -not $process.HasExited) { $process.Kill($true); [void]$process.WaitForExit(5000) } } catch { }
}
function New-CaptureResult([string]$Code, [object[]]$Lines = @()) {
    $exited=$false; $exit=$null
    try { if ($started) { $exited=$process.HasExited; if ($exited) { $exit=$process.ExitCode } } } catch { }
    $outLength=0; $errLength=0
    if ($streams.Count -eq 2) { $outLength=$streams[0].Text.Length; $errLength=$streams[1].Text.Length }
    [pscustomobject]@{ ExitCode=$exit; Lines=$Lines; Started=$started; HasExited=$exited;
        TimedOut=$timedOut; ElapsedMilliseconds=$clock.ElapsedMilliseconds; OsErrorCode=$osError;
        StdoutCharacters=$outLength; StderrCharacters=$errLength;
        PasswordPromptObserved=$promptObserved; PasswordInputReadyShown=$promptShown; FailureCode=$Code }
}
try {
    # No password pipe, Read-Host credential, child environment credential, new
    # console or detached/background process. ODBC owns hidden password input.
    if ($RequireConsole -and ($Host.Name -cne 'ConsoleHost' -or [Console]::IsInputRedirected)) {
        return (New-CaptureResult 'CONSOLE_REQUIRED')
    }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $false
    $info.RedirectStandardInput = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $info.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in $Arguments) { [void]$info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $started = $process.Start()
    if (-not $started) { throw 'Process did not start' }
    $failure = 'PROCESS_CAPTURE_FAILED'
    # Read both pipes concurrently in chunks. ReadToEnd alone cannot reveal a
    # no-newline password prompt while SQLCMD is waiting for console input.
    $streams = @($process.StandardOutput,$process.StandardError) | ForEach-Object {
        $buffer = [char[]]::new(4096)
        [pscustomobject]@{ Reader=$_; Buffer=$buffer; Text=[Text.StringBuilder]::new(); Done=$false; PrefixChecked=$false; Task=$_.ReadAsync($buffer,0,$buffer.Length) }
    }
    $promptObserved = $false
    $promptShown = $false
    $failure = 'PROCESS_CAPTURE_FAILED'
    while (-not $process.HasExited -or @($streams | Where-Object { -not $_.Done }).Count) {
        if ($WatchdogSeconds -gt 0 -and $clock.Elapsed.TotalSeconds -ge $WatchdogSeconds) {
            $timedOut=$true; $failure='PROCESS_TIMEOUT'; throw 'Watchdog elapsed'
        }
        foreach ($stream in $streams) {
            if ($stream.Done -or -not $stream.Task.IsCompleted) { continue }
            $count = $stream.Task.GetAwaiter().GetResult()
            if ($count -eq 0) { $stream.Done=$true; continue }
            [void]$stream.Text.Append($stream.Buffer,0,$count)
            if (($streams[0].Text.Length + $streams[1].Text.Length) -gt 16000000) { $failure='OUTPUT_LIMIT'; throw 'Output limit' }
            if (-not $stream.PrefixChecked -and $stream.Text.Length -ge 9) {
                $stream.PrefixChecked=$true
                if ($stream.Text.ToString(0,9) -ceq 'Password:') {
                    $promptObserved=$true
                    if ($RequireConsole -and -not $promptShown -and -not $process.HasExited) {
                        Write-Host 'PASSWORD INPUT READY: native hidden console input is active. Enter the approved input and press Enter; Ctrl+C stops.'
                        $promptShown=$true
                    }
                }
            }
            $stream.Task=$stream.Reader.ReadAsync($stream.Buffer,0,$stream.Buffer.Length)
        }
        # Short waits permit PowerShell cancellation to unwind into finally.
        if (-not $process.HasExited) { [void]$process.WaitForExit(50) }
        else { [Threading.Thread]::Sleep(5) }
    }
    $stdout = $streams[0].Text.ToString()
    $stderr = $streams[1].Text.ToString()
    # Strip ONLY the exact native prompt prefix; all other output stays private.
    $stdout = [regex]::Replace($stdout, '\APassword: ?', '')
    $stderr = [regex]::Replace($stderr, '\APassword: ?', '')
    if ($process.ExitCode -in @(-1073741510,130)) { $failure='PROCESS_INTERRUPTED'; throw 'Native interrupt exit' }
    New-CaptureResult '' @($stdout,$stderr)
} catch {
    # Never forward exception text, which can contain process output or paths.
    $exception=$_.Exception
    for ($i=0; $i -lt 3 -and $exception; $i++) {
        if ($exception -is [ComponentModel.Win32Exception]) { $osError=$exception.NativeErrorCode; break }
        if ($exception -is [Management.Automation.PipelineStoppedException] -or $exception -is [OperationCanceledException]) { $failure='PROCESS_INTERRUPTED'; break }
        $exception=$exception.InnerException
    }
    Stop-CapturedChild
    New-CaptureResult $failure
} finally {
    if ($process) {
        # Best-effort cleanup on ordinary Ctrl+C/pipeline cancellation. Force-kill
        # or power loss may prevent finally from running; never infer rollback.
        # A killed/lost client is NOT proof of rollback; the ledger stays pending.
        Stop-CapturedChild
        $process.Dispose()
    }
    $clock.Stop()
    $stdout = $null
    $stderr = $null
    $streams = $null
}
