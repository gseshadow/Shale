# Private in-memory transports for the Windows acceptance operator. No shell or output files.
function Invoke-HermesCapturedProcess([Diagnostics.ProcessStartInfo]$StartInfo,[int]$TimeoutMilliseconds=180000) {
    $StartInfo.UseShellExecute=$false; $StartInfo.CreateNoWindow=$true
    $StartInfo.RedirectStandardOutput=$true; $StartInfo.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new();$process.StartInfo=$StartInfo
    $stdout=$null;$stderr=$null;$started=$false
    try {
        if (-not $process.Start()) { throw 'HERMES_AZURE_PROCESS_FAILED' }
        $started=$true
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds)) { throw 'HERMES_AZURE_TIMEOUT' }
        if ($process.ExitCode -ne 0) { throw 'HERMES_AZURE_FAILED' }
        $result=$stdout.GetAwaiter().GetResult();[void]$stderr.GetAwaiter().GetResult()
        return $result
    } catch {
        if ($_.Exception.Message -cin @('HERMES_AZURE_TIMEOUT','HERMES_AZURE_FAILED','HERMES_AZURE_PROCESS_FAILED')) { throw }
        throw 'HERMES_AZURE_PROCESS_FAILED'
    } finally {
        if ($started -and -not $process.HasExited) { $process.Kill($true);[void]$process.WaitForExit(10000) }
        $stdout=$null;$stderr=$null;$process.Dispose()
    }
}
function Invoke-HermesAzureRaw([string[]]$Arguments) {
    # Use the installed MSI CLI's Python entry point instead of az.cmd. Ctrl+C must never
    # leave a batch-job Y/N prompt between the owner credential and OFF/stop cleanup.
    $az=Get-Command az -CommandType Application | Where-Object Source -Like '*.cmd' | Select-Object -First 1
    if (-not $IsWindows -or -not $az) { throw 'WINDOWS_AZURE_CLI_REQUIRED' }
    $python=[IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetDirectoryName($az.Source)) '../python.exe'))
    if (-not (Test-Path -LiteralPath $python -PathType Leaf)) { throw 'WINDOWS_AZURE_CLI_REQUIRED' }
    $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$python
    $start.Environment['AZ_INSTALLER']='MSI'
    $start.ArgumentList.Add('-IBm');$start.ArgumentList.Add('azure.cli')
    foreach($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    Invoke-HermesCapturedProcess $start
}
function Invoke-HermesInteractiveProcess([string[]]$Arguments,[int]$TimeoutMilliseconds,[string]$FailurePrefix) {
    $executable=Get-Command hermes -CommandType Application | Select-Object -First 1 -ExpandProperty Source
    $process=Start-Process -FilePath $executable -ArgumentList $Arguments -NoNewWindow -PassThru
    $timer=[Diagnostics.Stopwatch]::StartNew();$nextProgress=30000
    try {
        while (-not $process.WaitForExit(1000)) {
            if ($timer.ElapsedMilliseconds -ge $TimeoutMilliseconds) { throw ($FailurePrefix+'_TIMEOUT') }
            if ($timer.ElapsedMilliseconds -ge $nextProgress) {
                Write-Host ('Hermes window still open; '+[int]($timer.ElapsedMilliseconds/1000)+' seconds elapsed. This is a wait notice, not a test result.')
                $nextProgress+=30000
            }
        }
        if ($process.ExitCode -ne 0) { throw ($FailurePrefix+'_FAILED') }
    } finally {
        if (-not $process.HasExited) { $process.Kill($true);[void]$process.WaitForExit(10000) }
        $process.Dispose()
    }
}
