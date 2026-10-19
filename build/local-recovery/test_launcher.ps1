#requires -Version 5.1
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$launcher=Join-Path $PSScriptRoot 'sandbox.ps1'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($launcher,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Launcher syntax failed'}
# Exercise helpers without running the launcher, provisioning, Maven, SQLCMD or real secrets.
foreach($f in $ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)) { . ([scriptblock]::Create($f.Extent.Text)) }
$root=Join-Path ([IO.Path]::GetTempPath()) ('ShaleSandboxLauncherTest-'+[guid]::NewGuid().ToString('N'))
$state=$root
$JavaHome=$env:JAVA_HOME
# Load the same JSON helpers the real launcher imports, without executing any phase.
$operator=Join-Path $PSScriptRoot '../../docs/sql/auth/permission-cutover/retirement-operator.ps1'
$operatorAst=[Management.Automation.Language.Parser]::ParseFile($operator,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Operator helper syntax failed'}
foreach($f in $operatorAst.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)) {
    if($f.Name -in @('Write-NewText','Write-NewJson','Read-Json')) { . ([scriptblock]::Create($f.Extent.Text)) }
}
$readJson=${function:Read-Json}
function Must-Refuse([scriptblock]$Action,[string]$ExpectedMessage=''){
    $refused=$false
    try{& $Action}catch{
        if($ExpectedMessage -and $_.Exception.Message -cne $ExpectedMessage){throw}
        $refused=$true
    }
    if(-not $refused){throw 'Expected refusal did not occur'}
}
New-PrivateDirectory $root
Must-Refuse {New-PrivateDirectory $root}
foreach($dir in @('source/shale-desktop/target/classes','source/shale-server/target/classes','deps/shale-desktop','deps/shale-server','tool-classes','tooling','reviewed')){$null=New-Item -ItemType Directory -Path (Join-Path $state $dir) -Force}
foreach($file in @('sandbox.properties','shale-desktop.cp','shale-server.cp','relay.p12','relay-key-password','manual-exercise.ps1','tooling/Sandbox.java')){[IO.File]::WriteAllText((Join-Path $state $file),'synthetic fixture')}
Write-BuildManifest
$manifest=Join-Path $state 'build-manifest.json'
$manifestHash=(Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash
foreach($arrayMode in @('HostJsonReader','PS51PipelineShape')) {
    function Read-Json([string]$Path){
        $parsed=& $readJson $Path
        if($arrayMode -eq 'PS51PipelineShape') { ,$parsed } else { $parsed }
    }
    # Also check the real helper directly on this host. Simulating PS5.1's single
    # array output makes Core catch regressions in the production assertion.
    if($arrayMode -eq 'HostJsonReader') { Set-Item Function:Read-Json $readJson }
    $records=Read-Json $manifest
    $records=@($records)
    if($records.Count -ne 7){throw 'Fixture must materialize seven manifest records'}
    if($arrayMode -eq 'PS51PipelineShape' -and @(Read-Json $manifest).Count -ne 1){throw 'PS5.1 pipeline-array fixture did not reproduce the defect'}
    Assert-BuildManifest
    $added=Join-Path $state 'tooling/added.java'
    [IO.File]::WriteAllText($added,'added synthetic fixture')
    Must-Refuse {Assert-BuildManifest} 'Build inventory changed; stop'
    Remove-Item -LiteralPath $added
    $deleted=Join-Path $state 'tooling/Sandbox.java'
    Remove-Item -LiteralPath $deleted
    Must-Refuse {Assert-BuildManifest} 'Build inventory changed; stop'
    # Replace a deleted file with a different path: same count must still fail.
    [IO.File]::WriteAllText($added,'synthetic fixture')
    Must-Refuse {Assert-BuildManifest}
    Remove-Item -LiteralPath $added
    [IO.File]::WriteAllText($deleted,'synthetic fixture')
    foreach($record in $records) {
        $path=Join-Path $state $record.Path
        [IO.File]::WriteAllText($path,'tampered synthetic fixture')
        Must-Refuse {Assert-BuildManifest} 'Built artifact or tooling changed; stop'
        [IO.File]::WriteAllText($path,'synthetic fixture')
    }
    Assert-BuildManifest
    if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -cne $manifestHash){throw 'Test must not rewrite the manifest to accept changed inventory'}
}
Set-Item Function:Read-Json $readJson
# Startup correction is additive and bound to the original immutable manifest.
$ToolingUpdate='a'*40
$update=Get-StartupUpdatePath
$null=New-Item -ItemType Directory -Path (Join-Path $update 'classes') -Force
$null=New-Item -ItemType Directory -Path (Join-Path $update 'sources')
foreach($file in @('classes/LocalBootstrap.class','sources/LocalBootstrap.java')){[IO.File]::WriteAllText((Join-Path $update $file),'synthetic update')}
$updateFiles=@(Get-UpdateFiles $update | ForEach-Object {@{Path=$_.FullName.Substring($update.Length+1);Sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
$receiptPath=Join-Path $update 'receipt.json'
$receipt=@{Schema=1;Revision=$ToolingUpdate;State=$state;OriginalManifestSha256=$manifestHash;Files=$updateFiles}
Write-NewJson $receiptPath $receipt
Assert-StartupUpdate $update
Assert-BuildManifest
$receiptBytes=[IO.File]::ReadAllBytes($receiptPath)
foreach($field in @('Revision','State','OriginalManifestSha256')) {
    $receipt[$field]='changed'
    Remove-Item -LiteralPath $receiptPath
    Write-NewJson $receiptPath $receipt
    Must-Refuse {Assert-StartupUpdate $update}
    [IO.File]::WriteAllBytes($receiptPath,$receiptBytes)
    $receipt=Read-Json $receiptPath
    # Restore the mutable map for the next negative case.
    $receipt=@{Schema=1;Revision=$ToolingUpdate;State=$state;OriginalManifestSha256=$manifestHash;Files=$updateFiles}
}
foreach($file in $updateFiles) {
    $path=Join-Path $update $file.Path
    [IO.File]::WriteAllText($path,'tampered update')
    Must-Refuse {Assert-StartupUpdate $update} 'Startup update artifact changed'
    [IO.File]::WriteAllText($path,'synthetic update')
}
$added=Join-Path $update 'classes/Unexpected.class'
[IO.File]::WriteAllText($added,'added')
Must-Refuse {Assert-StartupUpdate $update} 'Startup update inventory changed'
Remove-Item -LiteralPath $added
Assert-StartupUpdate $update
Assert-BuildManifest
if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -cne $manifestHash){throw 'Startup correction changed original manifest'}
function Read-Host {param([string]$Prompt,[switch]$AsSecureString) ConvertTo-SecureString 'synthetic-only-test-secret' -AsPlainText -Force}
$info=New-Object Diagnostics.ProcessStartInfo
$info.UseShellExecute=$false
Read-SecretForChild $info 'SHALE_SANDBOX_TEST_SECRET' 'synthetic only'
if($info.EnvironmentVariables['SHALE_SANDBOX_TEST_SECRET'] -cne 'synthetic-only-test-secret'){throw 'Child secret missing'}
if([Environment]::GetEnvironmentVariable('SHALE_SANDBOX_TEST_SECRET')){throw 'Secret escaped into parent environment'}
$info.EnvironmentVariables.Remove('SHALE_SANDBOX_TEST_SECRET')
# Exact test directory only; never remove a database or exercise resource.
Remove-Item -LiteralPath $root -Recurse -Force
Write-Host ('PASS launcher private-directory refusal, host JSON reader/simulated PS5.1 manifest arrays, valid inventory, added/deleted/replaced/modified-file rejection, startup receipt binding/tamper refusal, unchanged original manifest and child-only secure prompt on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
