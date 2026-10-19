#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('New','Api','Desktop','Relay','Verify','SelfTest')][string]$Phase,
    [Parameter(Mandatory=$true)][string]$StatePath,
    [string]$ExportPath,
    [string]$ReviewedRevision,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Maven = 'mvn.cmd',
    [ValidateRange(1024,65535)][int]$SqlPort = 14333,
    [ValidateRange(1024,65535)][int]$ApiPort = 18080,
    [ValidateRange(1024,65535)][int]$LivePort = 18081,
    [ValidateRange(1024,65535)][int]$WsPort = 18082
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$state = [IO.Path]::GetFullPath($StatePath)
$expected = 'a34db4f3f2371e20db97ee8913d30de333ace3a53c1dc12196644694da9aebe5'
$appRevision = '59d8374858764788c621f6f8d986773b58ab79c4'
# Use the already verified argument transport, unchanged. Dot sourcing performs no phase.
$sandboxPhase=$Phase; $sandboxRevision=$ReviewedRevision
. (Join-Path $repo 'docs/sql/auth/permission-cutover/retirement-operator.ps1')
$Phase=$sandboxPhase; $ReviewedRevision=$sandboxRevision
function Run-Native([string]$Exe,[string[]]$Tokens) {
    $oldJavaHome=$env:JAVA_HOME
    try {
        $env:JAVA_HOME=$JavaHome
        & $Exe @Tokens
        if ($LASTEXITCODE -ne 0) { throw 'Local sandbox command failed; preserve state; no automatic retry' }
    } finally { $env:JAVA_HOME=$oldJavaHome }
}
function New-PrivateDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'Fresh private state path required; never adopt failed resources' }
    $null = New-Item -ItemType Directory -Path $Path
    if ($env:OS -eq 'Windows_NT') {
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetOwner($sid)
        $acl.SetAccessRuleProtection($true,$false)
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $Path -AclObject $acl
    } else { Run-Native 'chmod' @('700',$Path) }
}
function Read-SecretForChild($Info,[string]$Name,[string]$Prompt) {
    $secret = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $Info.EnvironmentVariables[$Name] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $secret.Dispose() }
}
function Get-BuildFiles {
    foreach ($module in @('shale-desktop','shale-server')) {
        Get-ChildItem -LiteralPath (Join-Path $state "source/$module/target/classes") -Recurse -File
        Get-ChildItem -LiteralPath (Join-Path $state "deps/$module") -Recurse -File
    }
    Get-ChildItem -LiteralPath (Join-Path $state 'tool-classes') -Recurse -File
    Get-ChildItem -LiteralPath (Join-Path $state 'tooling') -Recurse -File
    Get-ChildItem -LiteralPath (Join-Path $state 'reviewed') -Recurse -File
    foreach ($file in @('sandbox.properties','shale-desktop.cp','shale-server.cp','relay.p12','relay-key-password','manual-exercise.ps1')) { Get-Item -LiteralPath (Join-Path $state $file) }
}
function Write-BuildManifest {
    $records = @(Get-BuildFiles | Sort-Object FullName | ForEach-Object { @{Path=$_.FullName.Substring($state.Length+1);Sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    Write-NewJson (Join-Path $state 'build-manifest.json') $records
}
function Assert-BuildManifest {
    # PS5.1 emits a JSON array as one pipeline object. Capture it first so @()
    # enumerates the records, rather than wrapping that pipeline object in an array.
    $records = Read-Json (Join-Path $state 'build-manifest.json')
    $records = @($records)
    if (@(Get-BuildFiles).Count -ne $records.Count) { throw 'Build inventory changed; stop' }
    foreach ($record in $records) {
        if ((Get-FileHash -LiteralPath (Join-Path $state $record.Path) -Algorithm SHA256).Hash -cne $record.Sha256) { throw 'Built artifact or tooling changed; stop' }
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Explicit JDK 21 JavaHome required' }
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if ($env:OS -ne 'Windows_NT') { $java=Join-Path $JavaHome 'bin/java';$javac=Join-Path $JavaHome 'bin/javac';$keytool=Join-Path $JavaHome 'bin/keytool' }
foreach ($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_ARGS','MAVEN_OPTS')) {
    if (-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($key))) { throw "Inherited JVM/Maven injection refused: $key" }
}
if ($Phase -eq 'New') {
    if ($ReviewedRevision -notmatch '^[0-9a-f]{40}$') { throw 'Full reviewed tooling commit required' }
    $head = (& git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -cne $ReviewedRevision) { throw 'Tooling HEAD must equal reviewed commit' }
    $dirty = & git -C $repo status --porcelain -- build/local-recovery
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Reviewed tooling must be clean' }
    if ($state.StartsWith($repo.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Private input/state must be outside checkout' }
    if ((Get-FileHash -LiteralPath $ExportPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected) { throw 'Export SHA256 mismatch; no resources created' }
    if (@($SqlPort,$ApiPort,$LivePort,$WsPort | Select-Object -Unique).Count -ne 4) { throw 'Distinct local ports required' }
    $null=Assert-PrivatePath $state $repo
    New-PrivateDirectory $state
    Copy-Item -LiteralPath $ExportPath -Destination (Join-Path $state 'private-input.txt')
    Copy-Item -LiteralPath $PSScriptRoot -Destination (Join-Path $state 'tooling') -Recurse
    # Read canonical bodies and baseline includes from the reviewed tooling checkout, never the private production packet.
    foreach ($relative in @('docs/sql/auth/permission-cutover','shale-server/src/test/resources/auth')) {
        $destination=Join-Path $state ('reviewed/'+$relative)
        $null=New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force
        Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination $destination -Recurse
    }
    Run-Native 'git' @('-C',$repo,'worktree','add','--detach',(Join-Path $state 'source'),$appRevision)
    # Each classpath dependency is snapshotted; later Maven installs cannot silently change the exercise.
    Run-Native $Maven @('-B','-f',(Join-Path $state 'source/pom.xml'),'-pl','shale-desktop,shale-server','-am','-DskipTests','install')
    foreach ($module in @('shale-desktop','shale-server')) {
        Run-Native $Maven @('-B','-f',(Join-Path $state "source/$module/pom.xml"),'dependency:copy-dependencies','-DincludeScope=runtime',('-DoutputDirectory='+(Join-Path $state "deps/$module")))
        $jars = @(Get-ChildItem -LiteralPath (Join-Path $state "deps/$module") -Filter '*.jar' | Sort-Object Name | ForEach-Object { $_.FullName })
        [IO.File]::WriteAllText((Join-Path $state "$module.cp"),($jars -join [IO.Path]::PathSeparator),(New-Object Text.UTF8Encoding($false)))
    }
    $cp = (Join-Path $state 'source/shale-desktop/target/classes')+[IO.Path]::PathSeparator+(Get-Content -LiteralPath (Join-Path $state 'shale-desktop.cp') -Raw)+[IO.Path]::PathSeparator+(Join-Path $state 'source/shale-server/target/classes')+[IO.Path]::PathSeparator+(Get-Content -LiteralPath (Join-Path $state 'shale-server.cp') -Raw)
    $null=New-Item -ItemType Directory -Path (Join-Path $state 'tool-classes')
    Run-Native $javac @('-cp',$cp,'-d',(Join-Path $state 'tool-classes'),(Join-Path $state 'tooling/Sandbox.java'),(Join-Path $state 'tooling/LocalBootstrap.java'),(Join-Path $state 'tooling/LocalRelay.java'),(Join-Path $state 'tooling/LocalSqlDriver.java'),(Join-Path $state 'tooling/substitutes/DesktopUiUpdateLauncher.java'),(Join-Path $state 'tooling/ContractChecks.java'),(Join-Path $state 'tooling/RelayChecks.java'))
    $nonce = [guid]::NewGuid().ToString('N')
    $props = "database=ShaleRetirementExercise_$nonce`nnonce=$nonce`nappLogin=ShaleRecoveryApp_$nonce`nruntimeLogin=ShaleRecoveryRuntime_$nonce`nsqlPort=$SqlPort`napiPort=$ApiPort`nlivePort=$LivePort`nwsPort=$WsPort`napplicationRevision=$appRevision`ntoolingRevision=$head`n"
    [IO.File]::WriteAllText((Join-Path $state 'sandbox.properties'),$props,(New-Object Text.UTF8Encoding($false)))
    # Private certificate is only for the substitute WebSocket server. Public cert goes into a dedicated trust store.
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $relayPassword = [Convert]::ToBase64String($bytes)
    [IO.File]::WriteAllText((Join-Path $state 'relay-key-password'),$relayPassword,(New-Object Text.UTF8Encoding($false)))
    $ki = New-Object Diagnostics.ProcessStartInfo
    $ki.FileName=$keytool;$ki.UseShellExecute=$false;$ki.WorkingDirectory=$state
    $ki.EnvironmentVariables['SHALE_LOCAL_KEY_PASS']=$relayPassword
    $ki.Arguments=ConvertTo-SqlcmdArguments @('-genkeypair','-alias','local-relay','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=127.0.0.1','-ext','SAN=IP:127.0.0.1','-storetype','PKCS12','-keystore',(Join-Path $state 'relay.p12'),'-storepass:env','SHALE_LOCAL_KEY_PASS','-noprompt')
    $kp=[Diagnostics.Process]::Start($ki);$kp.WaitForExit();if($kp.ExitCode -ne 0){throw 'Local certificate creation failed'}
    $ki.EnvironmentVariables.Remove('SHALE_LOCAL_KEY_PASS');$relayPassword=$null
    # Stage the existing section-3 block byte-for-byte as text; do not execute it here.
    $acceptance=Get-Content -LiteralPath (Join-Path $repo 'docs/sql/auth/permission-cutover/remaining-acceptance.md') -Raw
    $section=$acceptance.Substring($acceptance.IndexOf('## 3. One timed operator recovery exercise'))
    $block=[regex]::Match($section,'(?s)```powershell\r?\n(.*?)\r?\n```')
    if(-not $block.Success){throw 'Existing manual exercise block not found'}
    [IO.File]::WriteAllText((Join-Path $state 'manual-exercise.ps1'),$block.Groups[1].Value,(New-Object Text.UTF8Encoding($false)))
    Write-BuildManifest
}
Assert-BuildManifest
if ((Get-FileHash -LiteralPath (Join-Path $state 'private-input.txt') -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expected) { throw 'Private export changed' }
# Prefer the review checkout for unchanged canonical resources and SQL includes. No password arguments/global env edits.
$desktopCp = (Get-Content -LiteralPath (Join-Path $state 'shale-desktop.cp') -Raw).Trim()
$serverCp = (Get-Content -LiteralPath (Join-Path $state 'shale-server.cp') -Raw).Trim()
$cp = @((Join-Path $state 'tool-classes'),(Join-Path $state 'source/shale-desktop/target/classes'),$desktopCp,(Join-Path $state 'source/shale-server/target/classes'),$serverCp) -join [IO.Path]::PathSeparator
$mode = $Phase.ToLowerInvariant();if($Phase -eq 'New'){$mode='provision'}
$info = New-Object Diagnostics.ProcessStartInfo
$info.FileName=$java;$info.UseShellExecute=$false;$info.WorkingDirectory=$state
$info.Arguments=ConvertTo-SqlcmdArguments @('-cp',$cp,'Sandbox',$mode,$state,$PSScriptRoot)
foreach ($name in @($info.EnvironmentVariables.Keys)) {
    if ($name -match '^(SHALE_|SPRING_|SQLCONNSTR_|SQLAZURECONNSTR_|FUNCTION_|LIVE_|NEGOTIATE_|WEBPUBSUB_|APP_|DB_)') { $info.EnvironmentVariables.Remove($name) }
}
try {
    if ($Phase -in @('New','Verify')) {
        $info.EnvironmentVariables['SHALE_SANDBOX_ADMIN_USER']=Read-Host 'Dedicated LOCAL SQL admin username (never Azure)'
        Read-SecretForChild $info 'SHALE_SANDBOX_ADMIN_PASSWORD' 'Dedicated LOCAL SQL admin password'
        Read-SecretForChild $info 'SHALE_SANDBOX_USER_PASSWORD' 'Synthetic users application password (same value for New/Verify)'
    }
    if ($Phase -in @('New','Api','Desktop','Verify')) {
        Read-SecretForChild $info 'SHALE_SANDBOX_APP_PASSWORD' 'Dedicated LOCAL auth-login password (>=16 chars; retain privately)'
        Read-SecretForChild $info 'SHALE_SANDBOX_RUNTIME_PASSWORD' 'Distinct LOCAL runtime-login password (>=16 chars; retain privately)'
    }
    if ($Phase -eq 'Api') { Read-SecretForChild $info 'SHALE_SANDBOX_TOKEN_SECRET' 'LOCAL API signing secret (>=32 chars; retain for API restart)'}
    Write-Host "LOCAL SYNTHETIC ONLY. State: $state. No automatic cleanup/retry."
    $process=[Diagnostics.Process]::Start($info);$process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Sandbox stopped; preserve exact state/resources; do not rerun New on this state' }
} finally {
    foreach ($name in @($info.EnvironmentVariables.Keys)) { if ($name -like 'SHALE_SANDBOX_*') { $info.EnvironmentVariables.Remove($name) } }
}
