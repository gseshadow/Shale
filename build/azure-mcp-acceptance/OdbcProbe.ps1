# Pure, allowlisted client/probe interpretation. No process or network on import.
Set-StrictMode -Version Latest
function Read-SqlcmdClientIdentity([string]$SHA256, [object[]]$HelpLines = @()) {
    # Verified against Microsoft's v1.10.0 Windows amd64 release asset, not a
    # path/filename guess. This is a Go deny-list entry, never an ODBC approval.
    if ($SHA256 -ieq 'D9DBD1A8BD26213747B246DEBA1E13663A7E1DEF530CD961D7582B41A5E27852') {
        return [pscustomobject]@{Family='GO_SQLCMD'; Version='1.10.0'; Evidence='RELEASE_SHA256'}
    }
    $text = ($HelpLines | ForEach-Object { [string]$_ }) -join "`n"
    if ($text -match '(?im)^\s*sqlcmd: Install/Create/Query SQL Server') {
        $version=''
        if ($text -match '(?im)^\s*Version:\s*v?([0-9]{1,5}(?:\.[0-9]{1,5}){1,3})(?:\s|$)') { $version=$Matches[1] }
        return [pscustomobject]@{Family='GO_SQLCMD'; Version=$version; Evidence='HELP_BANNER'}
    }
    if ($text -match '(?im)^\s*Microsoft\s+\(R\)\s+SQL Server Command Line Tool\s*$' -and
        $text -match '(?im)^\s*Version\s+([0-9]{1,5}(?:\.[0-9]{1,5}){1,3})(?:\s|$)') {
        return [pscustomobject]@{Family='ODBC_SQLCMD'; Version=$Matches[1]; Evidence='HELP_BANNER'}
    }
    return [pscustomobject]@{Family='UNKNOWN'; Version=''; Evidence='UNRECOGNIZED'}
}
function Get-OdbcProbeDiagnosis($NativeResult, [bool]$ListenerAccepted = $false,
    [ValidateSet('','LISTENER_START_FAILED','ACCEPT_FAILED','TRANSPORT_CALL_FAILED','CLEANUP_FAILED')][string]$HarnessFailure = '', [switch]$HelpOnly) {
    $required=@('ExitCode','Started','HasExited','TimedOut','ElapsedMilliseconds','OsErrorCode','StdoutCharacters','StderrCharacters','PasswordPromptObserved','PasswordInputReadyShown','FailureCode')
    $valid = $null -ne $NativeResult
    if ($valid) { foreach ($name in $required) { if ($name -cnotin $NativeResult.PSObject.Properties.Name) { $valid=$false; break } } }
    if (-not $valid) {
        $NativeResult=[pscustomobject]@{ExitCode=$null; Started=$false; HasExited=$false; TimedOut=$false; ElapsedMilliseconds=0L; OsErrorCode=$null; StdoutCharacters=0; StderrCharacters=0; PasswordPromptObserved=$false; PasswordInputReadyShown=$false; FailureCode='NATIVE_RESULT_INVALID'}
    }
    $codes=@('','CONSOLE_REQUIRED','PROCESS_START_FAILED','PROCESS_CAPTURE_FAILED','PROCESS_INTERRUPTED','PROCESS_START_OR_INTERRUPTED','PROCESS_TIMEOUT','OUTPUT_LIMIT','NATIVE_RESULT_INVALID')
    $code=if ($NativeResult.FailureCode -cin $codes) { [string]$NativeResult.FailureCode } else { 'NATIVE_RESULT_INVALID' }
    # Copy primitive metadata only, never Lines, exception text or unknown strings.
    $exit=$null; $osCode=$null
    if (($NativeResult.ExitCode -is [int] -or $NativeResult.ExitCode -is [long]) -and $NativeResult.ExitCode -ge [int]::MinValue -and $NativeResult.ExitCode -le [int]::MaxValue) { $exit=[int]$NativeResult.ExitCode }
    if (($NativeResult.OsErrorCode -is [int] -or $NativeResult.OsErrorCode -is [long]) -and $NativeResult.OsErrorCode -ge [int]::MinValue -and $NativeResult.OsErrorCode -le [int]::MaxValue) { $osCode=[int]$NativeResult.OsErrorCode }
    $started=$NativeResult.Started -is [bool] -and $NativeResult.Started; $exited=$NativeResult.HasExited -is [bool] -and $NativeResult.HasExited
    $timeout=$NativeResult.TimedOut -is [bool] -and $NativeResult.TimedOut
    $prompt=$NativeResult.PasswordPromptObserved -is [bool] -and $NativeResult.PasswordPromptObserved; $ready=$NativeResult.PasswordInputReadyShown -is [bool] -and $NativeResult.PasswordInputReadyShown
    $elapsed=0L; $outLength=0; $errLength=0
    if ($NativeResult.ElapsedMilliseconds -is [long] -or $NativeResult.ElapsedMilliseconds -is [int]) { $elapsed=[Math]::Max(0L,[long]$NativeResult.ElapsedMilliseconds) }
    if (($NativeResult.StdoutCharacters -is [int] -or $NativeResult.StdoutCharacters -is [long]) -and $NativeResult.StdoutCharacters -ge 0 -and $NativeResult.StdoutCharacters -le [int]::MaxValue) { $outLength=[Math]::Max(0,[int]$NativeResult.StdoutCharacters) }
    if (($NativeResult.StderrCharacters -is [int] -or $NativeResult.StderrCharacters -is [long]) -and $NativeResult.StderrCharacters -ge 0 -and $NativeResult.StderrCharacters -le [int]::MaxValue) { $errLength=[Math]::Max(0,[int]$NativeResult.StderrCharacters) }
    $status = if ($HarnessFailure) { 'HARNESS_FAILURE' }
        elseif ($timeout -or $code -ceq 'PROCESS_TIMEOUT') { if ($prompt) {'TIMEOUT_AFTER_PROMPT'} else {'TIMEOUT_BEFORE_PROMPT'} }
        elseif ($code -ceq 'CONSOLE_REQUIRED') { 'CONSOLE_REQUIRED' }
        elseif ($code -ceq 'PROCESS_INTERRUPTED') { 'PROCESS_INTERRUPTED' }
        elseif ($code -ceq 'PROCESS_START_FAILED') { 'PROCESS_START_FAILURE' }
        elseif ($code) { 'TRANSPORT_FAILURE' }
        elseif (-not $started -or -not $exited -or $null -eq $exit) { 'UNKNOWN_PROCESS_OUTCOME' }
        elseif ($HelpOnly) { if ($prompt) {'UNEXPECTED_HELP_PROMPT'} elseif ($exit -eq 0) {'HELP_COMPLETED'} else {'HELP_PROCESS_EXIT'} }
        elseif (-not $prompt) { 'MISSING_PROMPT_PROCESS_EXIT' }
        elseif (-not $ready) { 'PROMPT_OBSERVED_AFTER_PROCESS_EXIT' }
        elseif ($exit -eq 0) { 'UNEXPECTED_SUCCESS' }
        elseif (-not $ListenerAccepted) { 'PROCESS_EXIT_BEFORE_LOOPBACK' }
        else { 'EXPECTED_PRELOGIN_FAILURE' }
    [pscustomobject]@{ Status=$status; Started=$started; HasExited=$exited; ExitCode=$exit; TimedOut=$timeout;
        PromptObserved=$prompt; InputReadyShown=$ready; ListenerAccepted=$ListenerAccepted;
        ElapsedMilliseconds=$elapsed; OsErrorCode=$osCode; StdoutCharacters=$outLength; StderrCharacters=$errLength;
        TransportCode=$code; HarnessCode=$HarnessFailure }
}
function Write-OdbcProbeDiagnosis([ValidateSet('CLIENT_HELP','LOGIN_PROBE')][string]$Phase, $Diagnosis) {
    $exitText=if ($null -eq $Diagnosis.ExitCode) {'UNKNOWN'} else {[string]$Diagnosis.ExitCode}
    $osText=if ($null -eq $Diagnosis.OsErrorCode) {'NONE'} else {[string]$Diagnosis.OsErrorCode}
    $transportText=if ($Diagnosis.TransportCode) {$Diagnosis.TransportCode} else {'NONE'}
    $harnessText=if ($Diagnosis.HarnessCode) {$Diagnosis.HarnessCode} else {'NONE'}
    Write-Host "ODBC_PROBE phase=$Phase status=$($Diagnosis.Status) started=$($Diagnosis.Started) exited=$($Diagnosis.HasExited) exit=$exitText timeout=$($Diagnosis.TimedOut) prompt=$($Diagnosis.PromptObserved) ready=$($Diagnosis.InputReadyShown) loopbackAccepted=$($Diagnosis.ListenerAccepted) elapsedMs=$($Diagnosis.ElapsedMilliseconds) stdoutChars=$($Diagnosis.StdoutCharacters) stderrChars=$($Diagnosis.StderrCharacters) transport=$transportText osError=$osText harness=$harnessText"
}
