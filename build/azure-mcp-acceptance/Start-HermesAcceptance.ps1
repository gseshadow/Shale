#Requires -Version 7.4
[CmdletBinding()]
param(
    [string]$ServerJar,
    [string]$ExpectedJarSHA256,
    [string]$ExpectedBuildVersion,
    [string]$SuccessfulRun='work/clean-slate-06a67de3fd7340378f9e0db9d6934d89'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'LiveRuntime.ps1')
. (Join-Path $PSScriptRoot 'HermesAcceptance.ps1')
$priorHermesHome=$env:HERMES_HOME; $profile=$null
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$lock=$null; $oldLock=$null; $run=$null; $operation='tools'; $campaign=$false; $result=$null
try {
    $id=[guid]::NewGuid().ToString('N'); $run=Join-Path $repo ('work/hermes-mcp-'+$id); New-LiveDirectory $run
    foreach ($tool in @('az','python','hermes')) { if(-not (Get-Command $tool -CommandType Application -ErrorAction SilentlyContinue)){throw 'TOOLS_REQUIRED'} }
    & python -c 'import yaml'
    if ($LASTEXITCODE -ne 0) { throw 'LOCAL_PROFILE_YAML_REQUIRED' }
    $operation='successful-sql-evidence'
    if (-not [IO.Path]::IsPathRooted($SuccessfulRun)) { $SuccessfulRun=Join-Path $repo $SuccessfulRun }
    $evidence=Read-LiveSqlEvidence $SuccessfulRun
    $lock=[IO.File]::Open((Join-Path $repo 'work/clean-slate-acceptance.lock'),'OpenOrCreate','ReadWrite','None')
    if (Test-Path (Join-Path $evidence.Directory 'supervised.lock')) { $oldLock=[IO.File]::Open((Join-Path $evidence.Directory 'supervised.lock'),'Open','Read','None') }
    $inventory=@(Get-ChildItem -LiteralPath $evidence.Directory -Recurse -File | Where-Object Name -CNE 'live-runtime.lock' | ForEach-Object {
        @{Path=$_.FullName;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash}
    })
    [IO.File]::WriteAllText((Join-Path $run 'prior-evidence.json'),($inventory | ConvertTo-Json -Depth 4))
    $operation='exact-build'
    if (-not $ServerJar) { $ServerJar=Read-Host 'Reviewed executable shale-server JAR path' }
    if (-not $ExpectedJarSHA256) { $ExpectedJarSHA256=Read-Host 'Independently reviewed JAR SHA256' }
    if (-not $ExpectedBuildVersion) { $ExpectedBuildVersion=Read-Host 'Independently reviewed build version' }
    Assert-LiveJar $ServerJar $ExpectedJarSHA256 $ExpectedBuildVersion
    $jar=Join-Path $run 'shale-server.jar'; Copy-Item -LiteralPath $ServerJar -Destination $jar
    Assert-LiveJar $jar $ExpectedJarSHA256 $ExpectedBuildVersion
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $browserArchive=[IO.Compression.ZipFile]::OpenRead($jar)
    try { if ($null -eq $browserArchive.GetEntry('BOOT-INF/classes/acceptance-browser/index.html')) { throw 'ACCEPTANCE_BROWSER_PACKAGE_REQUIRED' } } finally { $browserArchive.Dispose() }
    $operation='credentials'
    $credentials=Get-LiveCredentials $evidence.Directory
    $owner=Get-Credential -UserName brian -Message 'Existing ShaleMcpAcceptance database owner ONLY; memory only'
    if ($null -eq $owner -or $credentials.ContainsKey($owner.UserName)) { throw 'INDEPENDENT_OWNER_REQUIRED' }
    $priorHermesHome=$env:HERMES_HOME
    $profile=Join-Path $run 'hermes-private'
    & python (Join-Path $PSScriptRoot 'prepare-hermes-profile.py') $profile
    if ($LASTEXITCODE -ne 0) { throw 'LOCAL_HERMES_PROFILE_REQUIRED' }
    $env:HERMES_HOME=$profile
    $operation='hermes-runtime-preparation'
    Write-Host 'Preparing the private Hermes runtime before any deployment or activation approval.'
    Invoke-HermesInteractiveProcess -Arguments @('mcp','list') -TimeoutMilliseconds 600000 -FailurePrefix 'HERMES_PREPARATION'
    $operation='azure-and-sql-preflight'
    Assert-LiveConfigurationTarget
    Assert-LiveSqlOff $owner; Assert-LiveSynthetic $owner
    $zones=Get-LiveBinding $evidence.Directory $owner
    $specs=@(Get-Content (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    Invoke-LiveRls $credentials; Invoke-LiveNativeChecks $specs $credentials $run
    $operation='secure-acceptance-configuration'
    Assert-LiveAzureConfiguration; Assert-LiveSettings (Get-LiveSettings) $credentials
    Assert-LiveSqlOff $owner
    $context=@{Inventory=$inventory;Id=$id;Run=$run;Hash=$ExpectedJarSHA256;Version=$ExpectedBuildVersion;Jar=$jar;Evidence=$evidence;
      Owner=$owner;Credentials=$credentials;Zones=$zones;Specs=$specs}
    $operation='controlled-campaign'; $campaign=$true
    $result=Invoke-HermesAcceptanceWindow $context
    foreach ($item in $inventory) { if ((Get-FileHash -LiteralPath $item.Path).Hash -cne $item.SHA256) { $result.Status='FAIL'; $result.Failure='PRIOR_EVIDENCE_CHANGED'; $result.FailureCode='PRIOR_EVIDENCE_CHANGED' } }
    [IO.File]::WriteAllText((Join-Path $run 'report.json'),($result | ConvertTo-Json -Depth 12))
    $files=@(Get-ChildItem -LiteralPath $run -File | Where-Object Name -CNE 'sha256.json' | ForEach-Object {@{File=$_.Name;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
    [IO.File]::WriteAllText((Join-Path $run 'sha256.json'),($files | ConvertTo-Json -Depth 4))
    Write-Host "$($result.Status): $run/report.json"
    if ($result.Status -cne 'WINDOW_COMPLETED') { throw 'ACCEPTANCE_FAILED' }
} catch {
    $failureCode=if ($campaign) { if ($null -ne $result) { ConvertTo-LiveSafeCampaignCode $result.FailureCode } else { 'CAMPAIGN_FAILED' } } else { Get-LiveSafeConfigurationFailure $_ 'PREFLIGHT_FAILED' }
    if ($run -and -not $campaign) {
        [IO.File]::WriteAllText((Join-Path $run 'report.json'),(@{Status='FAIL';Approved=$false;Failure=$failureCode;Operation=$operation} | ConvertTo-Json))
    }
    throw "Acceptance failed during $operation ($failureCode). Preserve $run; inspect safe reports. No automatic retry or reset."
} finally {
    try {
        $env:HERMES_HOME=$priorHermesHome
        if ($run -and $profile -and (Test-Path -LiteralPath $profile)) {
            try {
                $summaryStart=[Diagnostics.ProcessStartInfo]::new()
                $summaryStart.FileName=Get-Command python -CommandType Application|Select-Object -First 1 -ExpandProperty Source
                $summaryStart.ArgumentList.Add('-I');$summaryStart.ArgumentList.Add((Join-Path $PSScriptRoot 'summarize-hermes-client.py'));$summaryStart.ArgumentList.Add($run)
                [void](Invoke-HermesCapturedProcess $summaryStart 10000)
            } catch { try { [IO.File]::WriteAllText((Join-Path $run 'client-summary.json'),'["CLIENT_SUMMARY_UNAVAILABLE"]') } catch { } }
        }
        if ($profile -and (Test-Path -LiteralPath $profile)) {
            $expectedProfile=[IO.Path]::GetFullPath((Join-Path $run 'hermes-private'))
            if ([IO.Path]::GetFullPath($profile) -ceq $expectedProfile -and $expectedProfile.StartsWith([IO.Path]::GetFullPath((Join-Path $repo 'work'))+[IO.Path]::DirectorySeparatorChar)) {
                Remove-Item -LiteralPath $expectedProfile -Recurse -Force
            }
        }
        if ($run -and (Test-Path -LiteralPath $run)) {
            $finalFiles=@(Get-ChildItem -LiteralPath $run -File|Where-Object Name -CNE 'sha256.json'|ForEach-Object {@{File=$_.Name;SHA256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
            [IO.File]::WriteAllText((Join-Path $run 'sha256.json'),($finalFiles|ConvertTo-Json -Depth 4))
        }
    } finally {
        if ($null -ne $oldLock) { $oldLock.Dispose() }; if ($null -ne $lock) { $lock.Dispose() }
        $owner=$null; $credentials=$null; $humans=$null; $context=$null; $imports=$null; $script:CleanLastLines.Clear()
    }
}
