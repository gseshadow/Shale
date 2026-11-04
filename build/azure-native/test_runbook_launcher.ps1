#requires -Version 5.1
# Offline only: execute the real section 2 with mocked console/Azure commands.
# Synthetic markers exist only beneath a new temporary directory. No Az module is loaded.
$ErrorActionPreference = 'Stop'
$Runbook = Join-Path $PSScriptRoot '../../docs/testing/native-azure-readonly-operator-runbook.md'
$Text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Runbook).Path)
$Section = [regex]::Match($Text, '(?ms)^## 2\. .*?(?=^## |\z)')
$Fence = [regex]::Match($Section.Value, '(?s)```powershell\r?\n(.*?)```')
if (-not $Fence.Success) { throw 'Required section 2 fence missing' }
$Tokens = $null
$ParseErrors = $null
$Ast = [System.Management.Automation.Language.Parser]::ParseInput($Fence.Groups[1].Value, [ref]$Tokens, [ref]$ParseErrors)
if ($ParseErrors.Count -ne 0) { throw 'Section 2 does not parse' }
# Fail closed if section 2 gains any Azure command outside this fully mocked inventory.
$AllowedAzure = @('Connect-AzAccount','Set-AzContext','Get-AzLocation','Get-AzResourceGroup',
    'New-AzResourceGroup','New-AzSqlServer','New-AzSqlServerFirewallRule','New-AzSqlDatabase',
    'Get-AzSqlServer','Get-AzSqlDatabase')
foreach ($Command in $Ast.FindAll({ param($Node) $Node -is [System.Management.Automation.Language.CommandAst] }, $true)) {
    $Name = $Command.GetCommandName()
    if (-not $Name -or ($Name -match '(?i)-Az' -and $Name -cnotin $AllowedAzure)) {
        throw 'Unmocked or dynamic command in section 2; update offline inventory first'
    }
}
$Create = [scriptblock]::Create($Fence.Groups[1].Value)
$Scratch = Join-Path ([IO.Path]::GetTempPath()) ('shale-launcher-offline-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($Scratch)
$script:Events = New-Object 'System.Collections.Generic.List[string]'
function Assert-Offline([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Assert-Unconsumed {
    Assert-Offline ((Test-Path -LiteralPath $ReaderValidationPath) -and (Test-Path -LiteralPath $ValidationPath)) 'Preflight burned a marker'
    Assert-Offline (-not (Test-Path work/azure-native/resource-scope.json)) 'Preflight wrote a resource scope'
    Assert-Offline ($script:Events.Count -eq 0) 'Preflight mutated resources or consumed evidence'
}
function Connect-AzAccount {
    [CmdletBinding()] param()
    Assert-Unconsumed
    if ($script:Scenario -eq 'login') { throw 'Injected login cancellation' }
    if ($script:Scenario -eq 'loginNull') { return $null }
    return [pscustomobject]@{ Offline=$true }
}
function Set-AzContext {
    [CmdletBinding()] param([string]$SubscriptionId)
    Assert-Unconsumed
    if ($script:Scenario -eq 'context') { throw 'Injected context cancellation' }
    if ($script:Scenario -eq 'contextNull') { return $null }
    return [pscustomobject]@{ Subscription=[pscustomobject]@{ Id=$SubscriptionId } }
}
function Get-AzLocation {
    [CmdletBinding()] param()
    Assert-Unconsumed
    if ($script:Scenario -eq 'regionPreflight') { throw 'Injected region preflight failure' }
    return [pscustomobject]@{ Location='offline-region' }
}
function Get-AzResourceGroup {
    [CmdletBinding()] param()
    Assert-Unconsumed
    if ($script:Scenario -eq 'collision') { return [pscustomobject]@{ ResourceGroupName=$ResourceGroup } }
}
function Read-Host {
    param([string]$Prompt, [switch]$AsSecureString)
    Assert-Unconsumed
    switch -Regex ($Prompt) {
        '^Authorized acceptance' { return 'offline-subscription' }
        '^Authorized Azure region' {
            if ($script:Scenario -eq 'region') { throw 'Injected region cancellation' }
            if ($script:Scenario -eq 'regionEmpty') { return '' }
            if ($script:Scenario -eq 'regionInvalid') { return 'invalid-region' }
            return 'offline-region'
        }
        '^Password for ' {
            Assert-Offline $AsSecureString.IsPresent 'Password prompt must use AsSecureString'
            if ($script:Scenario -eq 'credential') { throw 'Injected credential cancellation' }
            $Secure = New-Object System.Security.SecureString
            if ($script:Scenario -ne 'credentialEmpty') { $Secure.AppendChar([char]120) }
            return $Secure
        }
        '^This workstation public IPv4' {
            if ($script:Scenario -eq 'ipv4Cancel') { throw 'Injected IPv4 cancellation' }
            if ($script:Scenario -eq 'ipv4') { return '256.1.2.3' }
            if ($script:Scenario -eq 'ipv6') { return '::1' }
            if ($script:Scenario -eq 'ipv4Short') { return '127.1' }
            if ($script:Scenario -eq 'ipv4Cidr') { return '192.0.2.1/32' }
            if ($script:Scenario -eq 'ipv4Zero') { return '0.0.0.0' }
            return '192.0.2.1'
        }
        default { throw 'Unexpected console prompt in offline section 2' }
    }
}
function Write-Host { param([string]$Object) }
function Remove-Item {
    [CmdletBinding()] param([string]$LiteralPath)
    Assert-Offline ($LiteralPath -in @($ReaderValidationPath,$ValidationPath)) 'Unexpected removal in section 2'
    Assert-Offline (-not (Test-Path work/azure-native/resource-scope.json)) 'Scope existed before marker consumption'
    $Kind = if ($LiteralPath -eq $ReaderValidationPath) { 'reader' } else { 'live' }
    $script:Events.Add($Kind)
    Microsoft.PowerShell.Management\Remove-Item -LiteralPath $LiteralPath -ErrorAction Stop
}
function Set-Content {
    [CmdletBinding()] param([string]$Encoding, [Parameter(Position=0)][string]$Path,
        [Parameter(ValueFromPipeline=$true)][object]$Value)
    process {
        Assert-Offline (($script:Events -join ',') -ceq 'reader,live') 'Scope write must immediately follow exactly one consumption of both markers'
        Assert-Offline (-not ((Test-Path -LiteralPath $ReaderValidationPath) -or (Test-Path -LiteralPath $ValidationPath))) 'Scope write preceded marker deletion'
        Assert-Offline ($Path -ceq 'work/azure-native/resource-scope.json') 'Unexpected file output'
        $script:Events.Add('scope')
        Microsoft.PowerShell.Management\Set-Content -Encoding $Encoding -Path $Path -Value $Value
    }
}
function New-AzResourceGroup {
    param($Name,$Location,$Tag)
    Assert-Offline (($script:Events -join ',') -ceq 'reader,live,scope') 'First mutation must follow consumption and the recovery record exactly once'
    Assert-Offline ($ScopeWritten -and (Test-Path work/azure-native/resource-scope.json)) 'Recovery scope missing before first mutation'
    $script:Events.Add('mutation')
    throw 'OFFLINE_FIRST_MUTATION'
}
# Even a reordered resource command is trapped before any Azure SDK call.
function New-AzSqlServer { throw 'Unexpected resource mutation order' }
function New-AzSqlServerFirewallRule { throw 'Unexpected resource mutation order' }
function New-AzSqlDatabase { throw 'Unexpected resource mutation order' }
function Get-AzSqlServer { throw 'Unexpected post-mutation lookup' }
function Get-AzSqlDatabase { throw 'Unexpected post-mutation lookup' }
$Scenarios = @('login','loginNull','context','contextNull','region','regionEmpty','regionInvalid',
    'regionPreflight','credential','credentialEmpty','ipv4Cancel','ipv4','ipv6','ipv4Short',
    'ipv4Cidr','ipv4Zero','collision','success')
$Expected = @{
    login='Injected login cancellation'; loginNull='Azure login cancelled';
    context='Injected context cancellation'; contextNull='Authorized subscription context selection failed';
    region='Injected region cancellation'; regionEmpty='An authorized Azure region is required';
    regionInvalid='Select an available Azure region'; regionPreflight='Injected region preflight failure';
    credential='Injected credential cancellation'; credentialEmpty='Console credential entry cancelled or empty';
    ipv4Cancel='Injected IPv4 cancellation'; ipv4='A single workstation IPv4 address is required';
    ipv6='A single workstation IPv4 address is required'; ipv4Short='A single workstation IPv4 address is required';
    ipv4Cidr='A single workstation IPv4 address is required'; ipv4Zero='A single workstation IPv4 address is required';
    collision='Generated resource group already exists; never adopt it'; success='OFFLINE_FIRST_MUTATION'
}
Push-Location $Scratch
try {
    foreach ($script:Scenario in $Scenarios) {
        [void][IO.Directory]::CreateDirectory((Join-Path $Scratch $script:Scenario))
        Push-Location (Join-Path $Scratch $script:Scenario)
        try {
            [void][IO.Directory]::CreateDirectory((Join-Path (Get-Location).Path 'work/azure-native'))
            $ReaderValidationPath = 'work/azure-native/reader.synthetic.json'
            $ValidationPath = 'work/azure-native/live.synthetic.json'
            [IO.File]::WriteAllText((Join-Path (Get-Location).Path $ReaderValidationPath), 'offline reader evidence')
            [IO.File]::WriteAllText((Join-Path (Get-Location).Path $ValidationPath), 'offline live evidence')
            $LiveReaderDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash
            $LiveMarkerDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ValidationPath).Hash
            $script:Events.Clear()
            $ScopeWritten = $false
            $Caught = $null
            try { . $Create }
            catch { $Caught = $_.Exception.Message }
            Assert-Offline ($null -ne $Caught) 'Expected cancellation/refusal or intercepted first mutation'
            Assert-Offline ($Caught -ceq $Expected[$script:Scenario]) "Scenario $script:Scenario failed outside its expected input/boundary"
            if ($script:Scenario -eq 'success') {
                Assert-Offline ($Caught -ceq 'OFFLINE_FIRST_MUTATION') 'Successful preflight did not reach mocked first mutation'
                Assert-Offline (($script:Events -join ',') -ceq 'reader,live,scope,mutation') 'Marker consumption/mutation ordering differs'
            }
            else {
                Assert-Unconsumed
                Assert-Offline ((Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash -ceq $LiveReaderDigest) 'Reader marker changed on cancellation'
                Assert-Offline ((Get-FileHash -Algorithm SHA256 -LiteralPath $ValidationPath).Hash -ceq $LiveMarkerDigest) 'Live marker changed on cancellation'
            }
        }
        finally { $AdminCredential = $null; Pop-Location }
    }
}
finally {
    Pop-Location
    Microsoft.PowerShell.Management\Remove-Item -LiteralPath $Scratch -Recurse -Force
}
Write-Output "RUNBOOK_LAUNCHER PASS scenarios=$($Scenarios.Count) azureExecuted=false"
