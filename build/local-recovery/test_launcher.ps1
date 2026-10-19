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
function Write-NewJson([string]$Path,$Value){[IO.File]::WriteAllText($Path,($Value|ConvertTo-Json -Depth 10))}
function Read-Json([string]$Path){Get-Content -LiteralPath $Path -Raw|ConvertFrom-Json}
function Must-Refuse([scriptblock]$Action){$refused=$false;try{& $Action}catch{$refused=$true};if(-not $refused){throw 'Expected refusal did not occur'}}
New-PrivateDirectory $root
Must-Refuse {New-PrivateDirectory $root}
foreach($dir in @('source/shale-desktop/target/classes','source/shale-server/target/classes','deps/shale-desktop','deps/shale-server','tool-classes','tooling','reviewed')){$null=New-Item -ItemType Directory -Path (Join-Path $state $dir) -Force}
foreach($file in @('sandbox.properties','shale-desktop.cp','shale-server.cp','relay.p12','relay-key-password','manual-exercise.ps1','tooling/Sandbox.java')){[IO.File]::WriteAllText((Join-Path $state $file),'synthetic fixture')}
Write-BuildManifest
Assert-BuildManifest
[IO.File]::WriteAllText((Join-Path $state 'tooling/Sandbox.java'),'tampered synthetic fixture')
Must-Refuse {Assert-BuildManifest}
function Read-Host {param([string]$Prompt,[switch]$AsSecureString) ConvertTo-SecureString 'synthetic-only-test-secret' -AsPlainText -Force}
$info=New-Object Diagnostics.ProcessStartInfo
$info.UseShellExecute=$false
Read-SecretForChild $info 'SHALE_SANDBOX_TEST_SECRET' 'synthetic only'
if($info.EnvironmentVariables['SHALE_SANDBOX_TEST_SECRET'] -cne 'synthetic-only-test-secret'){throw 'Child secret missing'}
if([Environment]::GetEnvironmentVariable('SHALE_SANDBOX_TEST_SECRET')){throw 'Secret escaped into parent environment'}
$info.EnvironmentVariables.Remove('SHALE_SANDBOX_TEST_SECRET')
# Exact test directory only; never remove a database or exercise resource.
Remove-Item -LiteralPath $root -Recurse -Force
Write-Host ('PASS launcher private-directory refusal, build/config/tooling tamper rejection and child-only secure prompt on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
