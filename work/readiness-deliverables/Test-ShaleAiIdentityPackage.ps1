#requires -Version 5.1
<# Offline only. No Azure modules, credentials, SQL connections or production access.
Run on each supported operator shell; this is not a SQL or Azure integration test. #>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$scriptPath=Join-Path $PSScriptRoot 'Provision-ShaleAiIdentities.ps1'
$tokens=$null;$parseErrors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw 'Provisioning script has parser errors.'}
$source=[IO.File]::ReadAllText($scriptPath)
if($source -match '[^\x00-\x7F]'){throw 'Provisioning script must remain ASCII for Windows PowerShell 5.1 encoding compatibility.'}
if($ast.ScriptRequirements.RequiredPSVersion.ToString() -ne '5.1'){throw 'Windows PowerShell minimum version differs.'}
# Reject language constructs unavailable in 5.1. Windows execution is still required
# to test the actual 5.1 engine, .NET Framework and installed Az module versions.
foreach($kind in @('PipelineChainAst','TernaryExpressionAst')){
    if(@($ast.FindAll({param($n) $n.GetType().Name -eq $kind},$true)).Count){throw 'PowerShell 7-only AST syntax found.'}
}
if(@($tokens | Where-Object { $_.Kind.ToString() -in @('QuestionQuestion','QuestionQuestionEquals','QuestionDot','QuestionLBracket') }).Count){throw 'PowerShell 7-only operator found.'}
function Require([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
# Block external boundaries while exercising the real default mode and guards.
function Import-Module {throw 'TEST: Azure modules must not load during offline checks.'}
function Get-AzContext {throw 'TEST: Azure context must not be inspected during offline checks.'}
function Get-AzKeyVaultSecret {throw 'TEST: Key Vault must never be called during offline checks.'}
function Get-AzAccessToken {throw 'TEST: token acquisition must never be called during offline checks.'}
$review=@(& $scriptPath)
Require ($review.Count -eq 5) 'Default Review must return the offline banner and exactly three identity mappings.'
Require ($review[0] -like 'OFFLINE REVIEW ONLY:*') 'Default mode is no longer offline.'
function Expect-Stop([hashtable]$Arguments,[string]$ExpectedMessage){
    $stopped=$false
    try{& $scriptPath @Arguments | Out-Null}
    catch{Require ($_.Exception.Message -like $ExpectedMessage) 'Unexpected guard failure; check whether an external boundary was reached.';$stopped=$true}
    Require $stopped 'Expected fail-closed guard did not stop.'
}
Expect-Stop @{Mode='Provision'} 'STOP: explicit tenant/subscription*'
Expect-Stop @{Mode='Verify';TenantId='offline';SubscriptionId='offline';AllApplicationControlsOffConfirmed=$true} 'STOP: secret-safe client/server telemetry*'
$approved=@('shale_prod_ai_case_runtime','shale_prod_ai_catalog_verifier','shale_prod_ai_oauth_bootstrap')
$guard=@{Mode='Provision';TenantId='offline';SubscriptionId='offline';AllApplicationControlsOffConfirmed=$true;SecretSafeTelemetryReviewed=$true;AuthorizedUsers=$approved}
Expect-Stop $guard 'STOP: separate authorization reference*'
$guard.AuthorizationReference='   '
Expect-Stop $guard 'STOP: separate authorization reference*'
$guard.AuthorizationReference='OFFLINE-GUARD-TEST';$guard.AuthorizedUsers=@('shale_app','shale_runtime','public')
Expect-Stop $guard 'STOP: separate authorization reference*'
$guard.AuthorizedUsers=@($approved[0],$approved[0],$approved[2])
Expect-Stop $guard 'STOP: separate authorization reference*'
# Validate all package checksums, not just the contract's embedded digest.
foreach($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'SHA256SUMS.txt')){
    Require ($line -match '^([a-f0-9]{64})  ([A-Za-z0-9._-]+)$') 'Malformed package checksum entry.'
    $expectedHash=$Matches[1];$name=$Matches[2]
    Require ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $name) -Algorithm SHA256).Hash -ieq $expectedHash) ('Package checksum mismatch: '+$name)
}
$contract=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'role-contract.json') -Raw | ConvertFrom-Json)
Require ($contract.Count -eq 324) 'Exact role contract edge count differs.'
foreach($item in @(@('AiCaseDataRuntime',228),@('AiCaseDataCatalogVerifier',6),@('AiIntegrationBootstrap',90))){
    Require (@($contract | Where-Object {$_.Role -ceq $item[0]}).Count -eq $item[1]) ('Role permission count differs: '+$item[0])
}
# Exercise connection construction and SecureString conversion with ephemeral synthetic
# data. Extract only these helper definitions; never execute the provisioning body.
foreach($name in @('New-Connection','Convert-InMemory','Same-Set')){
    $definition=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    Require ($null -ne $definition) ('Missing helper: '+$name)
    . ([scriptblock]::Create($definition.Extent.Text))
}
$c=New-Connection
try{
    Require ($c.State -eq [System.Data.ConnectionState]::Closed) 'Offline construction unexpectedly opened a connection.'
    $settings=[System.Data.SqlClient.SqlConnectionStringBuilder]::new($c.ConnectionString)
    Require ($settings['Data Source'] -ceq 'tcp:shale.database.windows.net,1433' -and $settings['Initial Catalog'] -ceq 'Shale') 'Production target changed.'
    Require ($settings['Encrypt'] -and !$settings['TrustServerCertificate'] -and !$settings['Pooling'] -and !$settings['Persist Security Info']) 'TLS/credential/pool guard changed.'
    Require (!$settings['Password'] -and !$settings['User ID']) 'Connection string must contain no credentials.'
} finally {$c.Dispose()}
Require (Same-Set @() @()) 'Empty permission-set comparison fails.'
Require (!(Same-Set @('CONNECT') @())) 'Missing permission must not compare equal.'
Require (!(Same-Set @('public') @('PUBLIC'))) 'Identity/token checks must remain case-sensitive.'
$synthetic=[guid]::NewGuid().ToString('N');$secure=ConvertTo-SecureString $synthetic -AsPlainText -Force
try{
    $secure.MakeReadOnly()
    Require ([string]::Equals((Convert-InMemory $secure),$synthetic,[StringComparison]::Ordinal)) 'In-memory SecureString conversion failed.'
    $credential=[System.Data.SqlClient.SqlCredential]::new('offline-test-identity',$secure)
    Require ($credential.UserId -ceq 'offline-test-identity') 'Built-in SqlCredential API unavailable.'
} finally {$synthetic=$null;$credential=$null;$secure.Dispose()}
'PASS: parser, 5.1 syntax review, offline default, authorization/telemetry guards, exact package hashes/role counts, closed TLS connection construction and in-memory credential APIs.'
'Host: PowerShell '+$PSVersionTable.PSVersion+'; '+[Environment]::OSVersion.Platform
'NOT RUN: Windows PowerShell 5.1 host unless this command is run there; Azure/SQL integration or production checks.'
