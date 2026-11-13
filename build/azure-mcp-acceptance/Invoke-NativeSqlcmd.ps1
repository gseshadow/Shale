#Requires -Version 7.0
<# Internal process transport. Output is returned in memory to the guarded wrapper,
   never forwarded to the terminal or written to a temporary file. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string[]]$Arguments,
    [switch]$RequireConsole
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$process = $null
$started = $false
$stdout = $null
$stderr = $null
$failure = 'PROCESS_START_OR_INTERRUPTED'
try {
    # No password pipe, Read-Host credential, child environment credential, new
    # console or detached/background process. ODBC owns hidden password input.
    if ($RequireConsole -and ($Host.Name -cne 'ConsoleHost' -or [Console]::IsInputRedirected)) {
        return [pscustomobject]@{ ExitCode=$null; Lines=@(); PasswordPromptObserved=$false; PasswordInputReadyShown=$false; FailureCode='CONSOLE_REQUIRED' }
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
    # Read both pipes concurrently in chunks. ReadToEnd alone cannot reveal a
    # no-newline password prompt while SQLCMD is waiting for console input.
    $streams = @($process.StandardOutput,$process.StandardError) | ForEach-Object {
        $buffer = [char[]]::new(4096)
        [pscustomobject]@{ Reader=$_; Buffer=$buffer; Text=[Text.StringBuilder]::new(); Done=$false; PrefixChecked=$false; Task=$_.ReadAsync($buffer,0,$buffer.Length) }
    }
    $promptObserved = $false
    $promptShown = $false
    $failure = 'PROCESS_START_OR_INTERRUPTED'
    while (-not $process.HasExited -or @($streams | Where-Object { -not $_.Done }).Count) {
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
    [pscustomobject]@{ ExitCode=$process.ExitCode; Lines=@($stdout,$stderr); PasswordPromptObserved=$promptObserved; PasswordInputReadyShown=$promptShown; FailureCode='' }
} catch {
    # Never forward exception text, which can contain process output or paths.
    [pscustomobject]@{ ExitCode=$null; Lines=@(); PasswordPromptObserved=$false; PasswordInputReadyShown=$false; FailureCode=$failure }
} finally {
    if ($process) {
        # Best-effort cleanup on ordinary Ctrl+C/pipeline cancellation. Force-kill
        # or power loss may prevent finally from running; never infer rollback.
        # A killed/lost client is NOT proof of rollback; the ledger stays pending.
        try { if ($started -and -not $process.HasExited) { $process.Kill($true); [void]$process.WaitForExit(5000) } } catch { }
        $process.Dispose()
    }
    $stdout = $null
    $stderr = $null
    $streams = $null
}
