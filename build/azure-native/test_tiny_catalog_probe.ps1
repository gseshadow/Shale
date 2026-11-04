#requires -Version 5.1
# Offline: no Az module import; every Azure command in the actual launcher is mocked.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'invoke_tiny_catalog_probe.ps1')
function Assert-Offline([bool]$Ok) { if (-not $Ok) { throw 'Tiny launcher offline contract failed' } }
$Launcher = Join-Path $PSScriptRoot 'invoke_tiny_catalog_probe.ps1'
$Tokens = $null; $Errors = $null
$Ast = [Management.Automation.Language.Parser]::ParseFile($Launcher, [ref]$Tokens, [ref]$Errors)
Assert-Offline ($Errors.Count -eq 0)
$Allowed = @('Get-AzContext','Get-AzSqlServer','Get-AzSqlDatabase','New-AzSqlDatabase','Remove-AzSqlDatabase')
foreach ($Command in $Ast.FindAll({ param($N) $N -is [Management.Automation.Language.CommandAst] }, $true)) {
    $Name = $Command.GetCommandName()
    if ($Name -match '(?i)-Az') { Assert-Offline ($Name -cin $Allowed) }
    if (-not $Name) { Assert-Offline ($Command.Extent.Text -match '^& (\{|\$Java[c]?\b)') }
}
# Verify actual canonical source pins; no JDBC driver or credentials needed.
$Root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$Pins = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'tiny_catalog_probe.pins.json')) | ConvertFrom-Json
foreach ($Pin in $Pins.files) { Assert-Offline ((Get-ProbeHash (Join-Path $Root $Pin.path)) -ceq $Pin.sha256) }
$PinRefused = $false
try { Assert-ProbePins $Root ('0' * 64) 'absent.jar' } catch { $PinRefused = $true }
Assert-Offline $PinRefused

function Assert-ProbePins { if ($script:Scenario -eq 'pins') { throw 'CANARY raw pin' } }
function Get-AzContext {
    [CmdletBinding()]param()
    if ($script:Scenario -eq 'context') { throw 'CANARY context' }
    return [pscustomobject]@{ Subscription = [pscustomobject]@{ Id='offline-sub' } }
}
function Get-AzSqlServer {
    [CmdletBinding()]param($DefaultProfile,$ResourceGroupName,$ServerName)
    Write-Warning 'CANARY warning target'
    Write-Information 'CANARY info target' -InformationAction Continue
    Write-Verbose 'CANARY verbose target' -Verbose
    $Tags = @{ Purpose='ShaleCatalogProbe'; Environment='Nonproduction' }
    if ($script:Scenario -eq 'tags') { $Tags.Environment='Production' }
    return [pscustomobject]@{ ServerName=$ServerName; ResourceGroupName=$ResourceGroupName; Tags=$Tags }
}
function Get-AzSqlDatabase {
    [CmdletBinding()]param($DefaultProfile,$ResourceGroupName,$ServerName)
    if ($script:Scenario -eq 'collision' -and -not $script:Created) { return [pscustomobject]@{ DatabaseName=$Database } }
    if ($script:Scenario -eq 'verifyError' -and $script:Removed) { throw 'CANARY verification' }
    if ($script:Exists) { return [pscustomobject]@{ DatabaseName=$script:ExactDatabase } }
}
function New-AzSqlDatabase {
    [CmdletBinding()]param($DefaultProfile,$ResourceGroupName,$ServerName,$DatabaseName,$Edition,$RequestedServiceObjectiveName)
    Assert-Offline ($DatabaseName -cmatch '^catalogprobe_[a-f0-9]{32}$')
    Assert-Offline ($Edition -ceq 'Standard' -and $RequestedServiceObjectiveName -ceq 'S0')
    Assert-Offline ($env:TINY_PROBE_OWNER_PASSWORD -ceq 'CANARY owner password')
    Assert-Offline ($env:TINY_PROBE_PASSWORD -cmatch '^Aa1![a-f0-9]{64}$')
    $script:Created = $true
    $script:ExactDatabase = $DatabaseName
    if ($script:Scenario -eq 'createBefore') { throw 'CANARY create' }
    $script:Exists = $true
    if ($script:Scenario -eq 'createAfter') { throw 'CANARY create' }
}
function Remove-AzSqlDatabase {
    [CmdletBinding()]param($DefaultProfile,$ResourceGroupName,$ServerName,$DatabaseName,[switch]$Force)
    Assert-Offline ($DatabaseName -ceq $script:ExactDatabase -and $Force)
    $script:Removed = $true
    if ($script:Scenario -eq 'removeError') { throw 'CANARY drop' }
    if ($script:Scenario -ne 'stillPresent') { $script:Exists = $false }
}
function Invoke-ProbeCompile {
    param($Javac,$Scratch,$Source)
    Assert-Offline ($Source.EndsWith('TinyCatalogProbe.java'))
    return ($script:Scenario -ne 'compile')
}
function Invoke-ProbeJava {
    param($Java,$ClassPath,$Batch)
    $script:JavaCalls++
    Assert-Offline ($Batch.EndsWith('confinement-v2-catalog-components.sql'))
    if ($script:Scenario -eq 'nativeThrow') { throw 'CANARY driver SQL target SID' }
    if ($script:Scenario -eq 'raw') { return 'CANARY raw SQL target SID' }
    if ($script:Scenario -eq 'missing') { return @() }
    if ($script:Scenario -eq 'setup') { return "SETUP`tPERMISSION`t2`tconnectionClosed=true" }
    if ($script:Scenario -eq 'extra') { "DATABASE`tPASS`t0`tconnectionClosed=false" }
    foreach ($Id in @('DATABASE','SECURITY','PRINCIPALS','DEPENDENCIES','COMPONENT_1','BATCH_19')) {
        $Status = if ($script:Scenario -eq 'socket') { 'SOCKET_TIMEOUT' } else { 'PASS' }
        "$Id`t$Status`t5`tconnectionClosed=false"
    }
}
$Scratch = Join-Path ([IO.Path]::GetTempPath()) ('tiny-probe-test-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory((Join-Path $Scratch 'bin'))
[IO.File]::WriteAllText((Join-Path $Scratch 'bin/java.exe'), '')
[IO.File]::WriteAllText((Join-Path $Scratch 'bin/javac.exe'), '')
$OldOS = $env:OS
$OldProbe = [Environment]::GetEnvironmentVariable('TINY_PROBE_HOST','Process')
try {
    $env:OS = 'Windows_NT'
    [Environment]::SetEnvironmentVariable('TINY_PROBE_HOST',$null,'Process')
    $Cases = @('pass','pins','authorization','context','tags','collision','compile','createBefore','createAfter',
        'nativeThrow','raw','missing','setup','extra','socket','removeError','stillPresent','verifyError','environment','validateOnly')
    foreach ($script:Scenario in $Cases) {
        $script:Exists=$false; $script:Created=$false; $script:Removed=$false; $script:JavaCalls=0; $script:ExactDatabase=$null
        $ExpectedDigest='offline'; $JdbcJar='offline'; $JavaHome=$Scratch
        $SubscriptionId='offline-sub'; $ResourceGroupName='offline-group'; $ServerName='offline-server'
        $OwnerCredential=New-Object Management.Automation.PSCredential('offline-owner', (ConvertTo-SecureString 'CANARY owner password' -AsPlainText -Force))
        $AuthorizeTinySyntheticProbe=($script:Scenario -ne 'authorization')
        $ValidateOnly=($script:Scenario -eq 'validateOnly')
        if ($script:Scenario -eq 'environment') { $env:TINY_PROBE_HOST='CANARY inherited' }
        $Lines = @(Invoke-TinyCatalogProbe *>&1)
        foreach ($Line in $Lines) {
            Assert-Offline ($Line -cmatch '^(DATABASE|SECURITY|PRINCIPALS|DEPENDENCIES|COMPONENT_1|BATCH_19|LAUNCHER|SETUP|CLEANUP)\t(PASS|PERMISSION|QUERY_TIMEOUT|SOCKET_TIMEOUT|BINDING|OTHER_SANITIZED)\t[0-9]+\tconnectionClosed=(true|false)$')
            Assert-Offline (-not $Line.Contains('CANARY'))
        }
        if ($script:Scenario -in @('pass','socket')) { Assert-Offline ($Lines.Count -eq 7 -and $script:JavaCalls -eq 1) }
        if ($script:Scenario -eq 'validateOnly') { Assert-Offline ($Lines.Count -eq 0 -and -not $script:Created) }
        elseif ($script:Scenario -in @('pins','authorization','context','tags','collision','compile','environment')) {
            Assert-Offline (-not $script:Created -and -not $script:Removed -and $script:JavaCalls -eq 0 -and $Lines.Count -eq 1)
        } else {
            Assert-Offline $script:Created
            Assert-Offline ($Lines[-1].StartsWith("CLEANUP`t"))
            if ($script:Scenario -in @('removeError','stillPresent','verifyError')) {
                Assert-Offline ($Lines[-1].StartsWith("CLEANUP`tOTHER_SANITIZED`t"))
            } else {
                Assert-Offline (-not $script:Exists -and $Lines[-1].StartsWith("CLEANUP`tPASS`t"))
            }
        }
        if ($script:Scenario -ne 'environment') { Assert-Offline ($null -eq [Environment]::GetEnvironmentVariable('TINY_PROBE_HOST','Process')) }
        [Environment]::SetEnvironmentVariable('TINY_PROBE_HOST',$null,'Process')
    }
    # Exercise the actual executable entry fence too: no authorization/pins => no Azure.
    $Exe = (Get-Process -Id $PID).Path
    $Refusal = @(& $Exe -NoProfile -File $Launcher 2>$null)
    Assert-Offline ($LASTEXITCODE -eq 1 -and $Refusal.Count -eq 1 -and $Refusal[0] -ceq "LAUNCHER`tOTHER_SANITIZED`t0`tconnectionClosed=true")
    Write-Output 'TINY_CATALOG_LAUNCHER PASS scenarios=21 azureExecuted=false'
} finally {
    $env:OS=$OldOS
    [Environment]::SetEnvironmentVariable('TINY_PROBE_HOST',$OldProbe,'Process')
    Remove-Item -LiteralPath $Scratch -Recurse -Force
}
