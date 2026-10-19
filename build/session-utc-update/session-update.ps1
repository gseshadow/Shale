#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Stage','Api','Desktop','Verify')][string]$Phase,
    [Parameter(Mandatory=$true)][string]$StatePath,
    [Parameter(Mandatory=$true)][string]$ReviewedRevision,
    [Parameter(Mandatory=$true)][string]$JavaHome
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$state=(Resolve-Path -LiteralPath $StatePath).Path
$startup='62a12940aeb5d6994016432cd8985ec66cd98bf7'
$pinned='59d8374858764788c621f6f8d986773b58ab79c4'
if($ReviewedRevision -notmatch '^[0-9a-f]{40}$'){throw 'Full reviewed revision required'}
$head=(& git -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -or $head -cne $ReviewedRevision){throw 'Use the isolated checkout at the reviewed commit'}
$dirty=& git -C $repo status --porcelain -- build/session-utc-update shale-data/src/main/java/com/shale/data/util/UtcJdbcTimestamp.java shale-data/src/main/java/com/shale/data/dao/UserSessionDao.java build/session-utc-update/compatibility/SqlRememberCredentialStore.java shale-server/src/main/java/com/shale/server/runtime/SessionManagementService.java
if($LASTEXITCODE -or $dirty){throw 'Reviewed update sources must be clean'}
foreach($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_ARGS','MAVEN_OPTS')){
    if([Environment]::GetEnvironmentVariable($key)){throw "Inherited JVM injection refused: $key"}
}
function Hash([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
function ReadJson([string]$path){ConvertFrom-Json -InputObject ([IO.File]::ReadAllText($path))}
function Assert-Tree([string]$root,$records,[string[]]$folders){
    $seen=@{}
    foreach($record in @($records)){
        $relative=[string]$record.Path
        if([IO.Path]::IsPathRooted($relative) -or $relative -match '(^|[/\\])\.\.([/\\]|$)' -or $relative -notmatch '^[A-Za-z0-9_$./\\-]+$' -or $seen.ContainsKey($relative)){throw 'Invalid manifest path'}
        if($folders -and $relative -notmatch '^(classes|sources)[/\\]'){throw 'Unexpected additive artifact path'}
        $seen[$relative]=$true
        $file=Join-Path $root $relative
        if((Hash $file) -cne $record.Sha256){throw "Artifact mismatch: $relative"}
    }
    if($folders){
        $actual=@(foreach($folder in $folders){Get-ChildItem -LiteralPath (Join-Path $root $folder) -Recurse -File})
        if($actual.Count -ne $seen.Count){throw 'Additive artifact inventory changed'}
    }
}
function Assert-SandboxProperties([string]$props){
    foreach($line in @(('applicationRevision='+$pinned),'database=ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c','sqlPort=14333')){
        if(@($props -split '\r?\n') -cnotcontains $line){throw 'Unsupported sandbox identity; no resource changes made'}
    }
}
function Assert-Original {
    if(-not(Test-Path -LiteralPath (Join-Path $state 'provisioned') -PathType Leaf)){throw 'Completed existing provisioning required'}
    $originalRecords=ReadJson (Join-Path $state 'build-manifest.json')
    Assert-Tree $state $originalRecords @()
    $originalFiles=@(foreach($folder in @('source/shale-desktop/target/classes','source/shale-server/target/classes','deps/shale-desktop','deps/shale-server','tool-classes','tooling','reviewed')){Get-ChildItem -LiteralPath (Join-Path $state $folder) -Recurse -File})
    $originalFiles+=@(foreach($file in @('sandbox.properties','shale-desktop.cp','shale-server.cp','relay.p12','relay-key-password','manual-exercise.ps1')){Get-Item -LiteralPath (Join-Path $state $file)})
    if($originalFiles.Count -ne @($originalRecords).Count){throw 'Original build inventory changed'}
    if((Hash (Join-Path $state 'private-input.txt')).ToLowerInvariant() -cne 'a34db4f3f2371e20db97ee8913d30de333ace3a53c1dc12196644694da9aebe5'){throw 'Original private export changed'}
    Assert-SandboxProperties ([IO.File]::ReadAllText((Join-Path $state 'sandbox.properties')))
    $sourceHead=(& git -C (Join-Path $state 'source') rev-parse HEAD).Trim()
    if($LASTEXITCODE -or $sourceHead -cne $pinned){throw 'Pinned source revision changed'}
    $startupPath=Join-Path $state ('startup-updates/'+$startup)
    $receipt=ReadJson (Join-Path $startupPath 'receipt.json')
    if($receipt.Schema -ne 1 -or $receipt.Revision -cne $startup -or $receipt.State -cne $state -or $receipt.OriginalManifestSha256 -cne (Hash (Join-Path $state 'build-manifest.json'))){throw 'Startup overlay binding changed'}
    Assert-Tree $startupPath $receipt.Files @('classes','sources')
}
$update=Join-Path $state ('session-updates/'+$ReviewedRevision)
# Refuse reparse traversal for owned writes and class loading.
foreach($candidate in @($state,$update,(Join-Path $state ('startup-updates/'+$startup)))){
    $cursor=[IO.Path]::GetFullPath($candidate)
    while($cursor){
        if(Test-Path -LiteralPath $cursor){if((Get-Item -Force -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Reparse paths refused'}}
        $parent=[IO.Path]::GetDirectoryName($cursor);if($parent -eq $cursor){break};$cursor=$parent
    }
}
Assert-Original
$manifestHash=Hash (Join-Path $state 'build-manifest.json')
$startupReceiptHash=Hash (Join-Path $state ('startup-updates/'+$startup+'/receipt.json'))
$sep=[IO.Path]::PathSeparator
$baseCp=@((Join-Path $state ('startup-updates/'+$startup+'/classes')),(Join-Path $state 'tool-classes'),(Join-Path $state 'source/shale-desktop/target/classes'),([IO.File]::ReadAllText((Join-Path $state 'shale-desktop.cp')).Trim()),(Join-Path $state 'source/shale-server/target/classes'),([IO.File]::ReadAllText((Join-Path $state 'shale-server.cp')).Trim())) -join $sep
$java=Join-Path $JavaHome 'bin/java.exe';$javac=Join-Path $JavaHome 'bin/javac.exe'
if($env:OS -ne 'Windows_NT'){$java=Join-Path $JavaHome 'bin/java';$javac=Join-Path $JavaHome 'bin/javac'}
function Native([string]$exe,[string[]]$tokens){& $exe @tokens;if($LASTEXITCODE){throw 'Update stopped; retain all evidence; no automatic retry'}}
if($Phase -eq 'Stage'){
    if(Test-Path -LiteralPath $update){throw 'Update already attempted; never overwrite or retry blindly'}
    $null=New-Item -ItemType Directory -Path (Join-Path $update 'classes')
    $null=New-Item -ItemType Directory -Path (Join-Path $update 'sources')
    $null=New-Item -ItemType Directory -Path (Join-Path $update 'empty-sourcepath')
    $sources=@()
    foreach($relative in @('shale-data/src/main/java/com/shale/data/util/UtcJdbcTimestamp.java','shale-data/src/main/java/com/shale/data/dao/UserSessionDao.java','build/session-utc-update/compatibility/SqlRememberCredentialStore.java','shale-server/src/main/java/com/shale/server/runtime/SessionManagementService.java','build/session-utc-update/SessionUpdateChecks.java')){
        $destination=Join-Path $update ('sources/'+[IO.Path]::GetFileName($relative))
        Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination $destination
        $sources+=$destination
    }
    Native $javac (@('--release','21','-proc:none','-implicit:none','-sourcepath',(Join-Path $update 'empty-sourcepath'),'-cp',$baseCp,'-d',(Join-Path $update 'classes'))+$sources)
    Native $java @('-cp',((Join-Path $update 'classes')+$sep+$baseCp),'SessionUpdateChecks',(Join-Path $update 'classes'))
    Assert-Original
    if((Hash (Join-Path $state 'build-manifest.json')) -cne $manifestHash -or (Hash (Join-Path $state ('startup-updates/'+$startup+'/receipt.json'))) -cne $startupReceiptHash){throw 'Original receipt/manifest changed during staging'}
    $records=@(foreach($folder in @('classes','sources')){Get-ChildItem -LiteralPath (Join-Path $update $folder) -Recurse -File | ForEach-Object {@{Path=$_.FullName.Substring($update.Length+1);Sha256=(Hash $_.FullName)}}})
    $receipt=@{Schema=1;Revision=$ReviewedRevision;State=$state;ApplicationRevision=$pinned;StartupRevision=$startup;OriginalManifestSha256=$manifestHash;StartupReceiptSha256=$startupReceiptHash;Files=$records}
    $json=$receipt | ConvertTo-Json -Depth 8
    $stream=[IO.File]::Open((Join-Path $update 'receipt.json'),[IO.FileMode]::CreateNew)
    try{$bytes=(New-Object Text.UTF8Encoding($false)).GetBytes($json);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
    Write-Host 'Additive compatibility classes compiled. No database/session operation performed. Windows runtime acceptance remains pending.'
    return
}
$receipt=ReadJson (Join-Path $update 'receipt.json')
if($receipt.Schema -ne 1 -or $receipt.Revision -cne $ReviewedRevision -or $receipt.State -cne $state -or $receipt.ApplicationRevision -cne $pinned -or $receipt.StartupRevision -cne $startup -or $receipt.OriginalManifestSha256 -cne $manifestHash -or $receipt.StartupReceiptSha256 -cne $startupReceiptHash){throw 'Session update binding changed'}
Assert-Tree $update $receipt.Files @('classes','sources')
# Reuse the exact unchanged startup Sandbox and LocalBootstrap, including all guards and secure child setup.
# Sandbox already passes shale.local.toolClasses to its children as a classpath prefix.
$prefix=(Join-Path $update 'classes')+$sep+(Join-Path $state ('startup-updates/'+$startup+'/classes'))
$cp=$prefix+$sep+$baseCp
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=$java;$info.UseShellExecute=$false;$info.WorkingDirectory=$state
foreach($key in @($info.EnvironmentVariables.Keys)){
    if($key -match '^(SHALE_|SPRING_|SQLCONNSTR_|SQLAZURECONNSTR_|FUNCTION_|LIVE_|NEGOTIATE_|WEBPUBSUB_|APP_|DB_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|JAVA_OPTS$|MAVEN_OPTS$)'){$info.EnvironmentVariables.Remove($key)}
}
function Secret([string]$name,[string]$prompt){
    $secret=Read-Host $prompt -AsSecureString;$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try{$info.EnvironmentVariables[$name]=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr);$secret.Dispose()}
}
# Windows ProcessStartInfo uses one string. Quote each token with the standard backslash/quote transport.
function Quote([string]$value){'"'+[regex]::Replace([regex]::Replace($value,'(\\*)"','$1$1\"'),'(\\+)$','$1$1')+'"'}
$tokens=@('-Djava.security.manager=allow',('-Djava.io.tmpdir='+(Join-Path $state 'tmp')),('-Djdk.net.unixdomain.tmpdir='+(Join-Path $state 'tmp')),('-Dshale.local.toolClasses='+$prefix),'-cp',$cp,'Sandbox',$Phase.ToLowerInvariant(),$state,(Join-Path $state 'tooling'))
$info.EnvironmentVariables['TEMP']=Join-Path $state 'tmp'
$info.EnvironmentVariables['TMP']=Join-Path $state 'tmp'
$info.Arguments=(@($tokens | ForEach-Object {Quote $_}) -join ' ')
try{
    Secret 'SHALE_SANDBOX_APP_PASSWORD' 'Existing LOCAL app-login password'
    Secret 'SHALE_SANDBOX_RUNTIME_PASSWORD' 'Existing LOCAL runtime-login password'
    if($Phase -eq 'Api'){Secret 'SHALE_SANDBOX_TOKEN_SECRET' 'Existing LOCAL API signing secret'}
    if($Phase -eq 'Verify'){
        $info.EnvironmentVariables['SHALE_SANDBOX_ADMIN_USER']=Read-Host 'Existing dedicated LOCAL provisioner username (never Azure)'
        Secret 'SHALE_SANDBOX_ADMIN_PASSWORD' 'Existing LOCAL provisioner password (Verify only)'
        Secret 'SHALE_SANDBOX_USER_PASSWORD' 'Existing synthetic user password (Verify only)'
    }
    $process=[Diagnostics.Process]::Start($info);$process.WaitForExit()
    if($process.ExitCode){throw 'Sandbox stopped; preserve exact evidence; do not retry blindly'}
}finally{foreach($key in @($info.EnvironmentVariables.Keys)){if($key -like 'SHALE_SANDBOX_*'){$info.EnvironmentVariables.Remove($key)}}}
