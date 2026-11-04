#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ExpectedDigest,
    [string]$JdbcJar,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SubscriptionId,
    [string]$ResourceGroupName,
    [string]$ServerName,
    [System.Management.Automation.PSCredential]$OwnerCredential,
    [switch]$AuthorizeTinySyntheticProbe,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'

function Get-ProbeHash([string]$Path) {
    # Canonical UTF-8/LF for source pins, portable across Windows git CRLF checkouts.
    $Bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($Path).Replace("`r`n", "`n"))
    $Sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($Sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $Sha.Dispose() }
}
function Assert-ProbePins([string]$Root, [string]$Digest, [string]$Jar) {
    $ManifestPath = Join-Path $Root 'build/azure-native/tiny_catalog_probe.pins.json'
    if ($Digest -cnotmatch '^[a-f0-9]{64}$' -or (Get-ProbeHash $ManifestPath) -cne $Digest) { throw 'Pin refusal' }
    $Pins = [IO.File]::ReadAllText($ManifestPath) | ConvertFrom-Json
    foreach ($Pin in $Pins.files) {
        if ((Get-ProbeHash (Join-Path $Root $Pin.path)) -cne $Pin.sha256) { throw 'Pin refusal' }
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash.ToLowerInvariant() -cne $Pins.jdbcSha256) { throw 'Pin refusal' }
}
function Invoke-ProbeCompile([string]$Javac, [string]$Scratch, [string]$Source) {
    & {
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $global:LASTEXITCODE = 1
        & $Javac '--release' '21' '-d' $Scratch $Source *> $null
        return ($LASTEXITCODE -eq 0)
    }
}
function Invoke-ProbeJava([string]$Java, [string]$ClassPath, [string]$Batch) {
    # Native stderr never reaches the console or an exception/report renderer.
    $Lines = @(& {
        $ErrorActionPreference = 'Continue'
        $PSNativeCommandUseErrorActionPreference = $false
        $global:LASTEXITCODE = 1
        & $Java '-cp' $ClassPath 'TinyCatalogProbe' $Batch 2>$null
        if ($LASTEXITCODE -ne 0) { 'NATIVE_FAILURE' }
    })
    return $Lines
}
function Invoke-TinyCatalogProbe {
    $ProgressPreference = 'SilentlyContinue'
    $Root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $Database = 'catalogprobe_' + [guid]::NewGuid().ToString('N')
    $MayExist = $false
    $Scratch = $null
    $EnvNames = @('TINY_PROBE_HOST','TINY_PROBE_DATABASE','TINY_PROBE_OWNER','TINY_PROBE_OWNER_PASSWORD','TINY_PROBE_PASSWORD','TINY_PROBE_OPT_IN')
    $EnvironmentOwned = $false
    try {
        Assert-ProbePins $Root $ExpectedDigest $JdbcJar
        if ($ValidateOnly) { return }
        if (-not $AuthorizeTinySyntheticProbe -or $env:OS -cne 'Windows_NT' -or
            -not $OwnerCredential -or -not $SubscriptionId -or -not $ResourceGroupName -or
            $ServerName -cnotmatch '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$') { throw 'Scope refusal' }
        foreach ($Name in $EnvNames) {
            if ($null -ne [Environment]::GetEnvironmentVariable($Name, 'Process')) { throw 'Environment refusal' }
        }
        $Java = Join-Path $JavaHome 'bin/java.exe'
        $Javac = Join-Path $JavaHome 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $Java) -or -not (Test-Path -LiteralPath $Javac)) { throw 'JDK refusal' }
        $Context = Get-AzContext -ErrorAction Stop 2>$null 3>$null 4>$null 5>$null 6>$null
        if ($Context.Subscription.Id -cne $SubscriptionId) { throw 'Context refusal' }
        $Server = Get-AzSqlServer -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -ErrorAction Stop 2>$null 3>$null 4>$null 5>$null 6>$null
        if ($Server.Tags['Purpose'] -cne 'ShaleCatalogProbe' -or $Server.Tags['Environment'] -cne 'Nonproduction' -or
            $Server.ServerName -cne $ServerName -or $Server.ResourceGroupName -cne $ResourceGroupName) { throw 'Scope refusal' }
        $Existing = @(Get-AzSqlDatabase -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -ErrorAction Stop 2>$null 3>$null 4>$null 5>$null 6>$null)
        if (@($Existing | Where-Object { $_.DatabaseName -ceq $Database }).Count -ne 0) { throw 'Collision refusal' }
        $Scratch = Join-Path ([IO.Path]::GetTempPath()) ('tiny-catalog-' + [guid]::NewGuid().ToString('N'))
        [void][IO.Directory]::CreateDirectory($Scratch)
        if (-not (Invoke-ProbeCompile $Javac $Scratch (Join-Path $Root 'build/azure-native/TinyCatalogProbe.java'))) { throw 'Compile refusal' }
        $Random = New-Object byte[] 32
        $Rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $Rng.GetBytes($Random) } finally { $Rng.Dispose() }
        $Password = 'Aa1!' + ([BitConverter]::ToString($Random)).Replace('-', '').ToLowerInvariant()
        $EnvironmentOwned = $true
        $env:TINY_PROBE_HOST = $ServerName + '.database.windows.net'
        $env:TINY_PROBE_DATABASE = $Database
        $env:TINY_PROBE_OWNER = $OwnerCredential.UserName
        $env:TINY_PROBE_OWNER_PASSWORD = $OwnerCredential.GetNetworkCredential().Password
        $env:TINY_PROBE_PASSWORD = $Password
        $env:TINY_PROBE_OPT_IN = 'AUTHORIZE_TINY_SYNTHETIC_ONLY'
        # Mark before submission: an ambiguous creation failure still enters exact cleanup.
        $MayExist = $true
        New-AzSqlDatabase -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -DatabaseName $Database -Edition Standard -RequestedServiceObjectiveName S0 -ErrorAction Stop *> $null
        $Lines = @(Invoke-ProbeJava $Java ($Scratch + [IO.Path]::PathSeparator + $JdbcJar) (Join-Path $Root 'shale-data/src/test/resources/ai/confinement-v2-catalog-components.sql'))
        $Ids = @('DATABASE','SECURITY','PRINCIPALS','DEPENDENCIES','COMPONENT_1','BATCH_19')
        if ($Lines.Count -eq 1 -and $Lines[0] -cmatch '^SETUP\t(PERMISSION|QUERY_TIMEOUT|SOCKET_TIMEOUT|BINDING|OTHER_SANITIZED)\t[0-9]{1,12}\tconnectionClosed=true$') {
            $Lines[0] | Write-Output
        } else {
            if ($Lines.Count -ne 6) { throw 'Evidence refusal' }
            for ($Index = 0; $Index -lt 6; $Index++) {
                if ($Lines[$Index] -cnotmatch ('^' + $Ids[$Index] + '\t(PASS|PERMISSION|QUERY_TIMEOUT|SOCKET_TIMEOUT|BINDING|OTHER_SANITIZED)\t[0-9]{1,12}\tconnectionClosed=(true|false)$')) { throw 'Evidence refusal' }
            }
            $Lines | Write-Output
        }
    } catch {
        Write-Output "LAUNCHER`tOTHER_SANITIZED`t0`tconnectionClosed=true"
    } finally {
        if ($EnvironmentOwned) {
            foreach ($Name in $EnvNames) { [Environment]::SetEnvironmentVariable($Name, $null, 'Process') }
        }
        $Password = $null
        $OwnerCredential = $null
        if ($MayExist) {
            $Clock = [Diagnostics.Stopwatch]::StartNew()
            try {
                # Exact nonce database only. Never drop/adopt any existing resource.
                $Present = @(Get-AzSqlDatabase -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -ErrorAction Stop 2>$null 3>$null 4>$null 5>$null 6>$null | Where-Object { $_.DatabaseName -ceq $Database })
                if ($Present.Count -gt 0) {
                    Remove-AzSqlDatabase -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -DatabaseName $Database -Force -ErrorAction Stop *> $null
                }
                $Remaining = @(Get-AzSqlDatabase -DefaultProfile $Context -ResourceGroupName $ResourceGroupName -ServerName $ServerName -ErrorAction Stop 2>$null 3>$null 4>$null 5>$null 6>$null | Where-Object { $_.DatabaseName -ceq $Database })
                if ($Remaining.Count -ne 0) { throw 'Cleanup refusal' }
                Write-Output ("CLEANUP`tPASS`t" + $Clock.ElapsedMilliseconds + "`tconnectionClosed=true")
            } catch {
                Write-Output ("CLEANUP`tOTHER_SANITIZED`t" + $Clock.ElapsedMilliseconds + "`tconnectionClosed=true")
            }
        }
        if ($Scratch) { Remove-Item -LiteralPath $Scratch -Recurse -Force -ErrorAction SilentlyContinue 2>$null }
    }
}
# Dot-sourcing is for offline mocks; normal execution enters the guarded launcher.
if ($MyInvocation.InvocationName -ne '.') {
    $Results = @(Invoke-TinyCatalogProbe)
    $Results | Write-Output
    if (@($Results | Where-Object { $_ -cnotmatch '^([A-Z_0-9]+)\tPASS\t[0-9]+\tconnectionClosed=(true|false)$' }).Count -ne 0 -or
        (-not $ValidateOnly -and $Results.Count -ne 7)) { exit 1 }
}
