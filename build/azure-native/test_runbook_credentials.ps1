#requires -Version 5.1
# Offline only. Execute the exact runbook block; never print credentials or contact Azure.
$ErrorActionPreference = 'Stop'
if (@(Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*').Count -ne 0) {
    throw 'Use a fresh process without SHALE_AZURE_* environment values for this offline check'
}
$Identities = @('AI','VERIFIER','AUTH','RUNTIME','BOOTSTRAP')
$Runbook = Join-Path $PSScriptRoot '../../docs/testing/native-azure-readonly-operator-runbook.md'
$Text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Runbook).Path)
# Parse every PowerShell fence without executing resource/setup commands.
foreach ($Fence in [regex]::Matches($Text, '(?s)```powershell\r?\n(.*?)```')) {
    $Tokens = $null
    $ParseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput(
        $Fence.Groups[1].Value, [ref]$Tokens, [ref]$ParseErrors
    )
    if ($ParseErrors.Count -ne 0) { throw 'Runbook contains invalid PowerShell syntax' }
}
$Blocks = [regex]::Matches($Text, '(?s)# BEGIN OFFLINE-TESTED CONTAINED CREDENTIALS\r?\n(.*?)# END OFFLINE-TESTED CONTAINED CREDENTIALS')
if ($Blocks.Count -ne 1) { throw 'Expected exactly one marked runbook credential block' }
$Generate = [scriptblock]::Create($Blocks[0].Groups[1].Value)
$Previous = @{}
$FailureCaught = $false
try {
    foreach ($Round in @(1,2)) {
        # Dot-source so local cleanup is observable. Capture all streams to forbid output.
        $Output = @(. $Generate *>&1)
        if ($Output.Count -ne 0) { throw 'Credential block emitted output' }
        if ($null -ne $Bytes -or $null -ne $Secret -or $null -ne $rng) {
            throw 'Credential block retained local secret or RNG references'
        }
        $Seen = @{}
        foreach ($Identity in $Identities) {
            $Username = [Environment]::GetEnvironmentVariable("SHALE_AZURE_${Identity}_USERNAME", 'Process')
            $Password = [Environment]::GetEnvironmentVariable("SHALE_AZURE_${Identity}_PASSWORD", 'Process')
            if ($Username -cne "azro_$($Identity.ToLowerInvariant())" -or
                $Password.Length -ne 68 -or -not $Password.EndsWith('aB9!')) {
                throw 'Credential shape mismatch'
            }
            $Decoded = [Convert]::FromBase64String($Password.Substring(0,64))
            try {
                if ($Decoded.Length -ne 48) { throw 'Expected 48 random bytes per identity' }
            }
            finally { [Array]::Clear($Decoded, 0, $Decoded.Length); $Decoded = $null }
            if ($Seen.ContainsKey($Password) -or ($Round -eq 2 -and $Previous.ContainsKey($Password))) {
                throw 'Credential reuse across identities or generation rounds'
            }
            $Seen[$Password] = $true
            $Password = $null
        }
        $Previous = $Seen
    }
    # Fail partway through generation. This shadows only buffer allocation; the
    # runbook still uses the real cryptographic instance for the preceding users.
    $script:Allocations = 0
    function New-Object {
        param([string]$TypeName, [int]$Length)
        $script:Allocations++
        if ($script:Allocations -eq 3) { throw 'Injected offline allocation failure' }
        return ,(Microsoft.PowerShell.Utility\New-Object -TypeName $TypeName -ArgumentList $Length)
    }
    try {
        $Output = @(. $Generate *>&1)
    }
    catch { $FailureCaught = $true }
    finally { Remove-Item Function:New-Object -ErrorAction SilentlyContinue }
    if (-not $FailureCaught) { throw 'Expected partial generation to fail' }
    foreach ($Identity in $Identities) {
        foreach ($Kind in @('USERNAME','PASSWORD')) {
            if ($null -ne [Environment]::GetEnvironmentVariable("SHALE_AZURE_${Identity}_${Kind}", 'Process')) {
                throw 'Partial generation left a credential in the process environment'
            }
        }
    }
    if ($null -ne $Bytes -or $null -ne $Secret -or $null -ne $rng) {
        throw 'Failure cleanup retained local secret or RNG references'
    }
}
finally {
    foreach ($Identity in $Identities) {
        foreach ($Kind in @('USERNAME','PASSWORD')) {
            [Environment]::SetEnvironmentVariable("SHALE_AZURE_${Identity}_${Kind}", $null, 'Process')
        }
    }
    $Previous = $null
    $Seen = $null
    $Password = $null
    $Output = $null
}
Write-Output 'RUNBOOK_CREDENTIALS PASS rounds=2 identities=5 partialFailureCleanup=true'
