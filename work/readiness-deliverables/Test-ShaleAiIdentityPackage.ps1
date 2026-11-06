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
function Get-Credential {throw 'TEST: credential prompt must not occur during offline default/guard checks.'}
$review=@(& $scriptPath)
Require ($review.Count -eq 5) 'Default Review must return the offline banner and exactly three identity mappings.'
Require ($review[0] -like 'OFFLINE REVIEW ONLY:*') 'Default mode is no longer offline.'
$sqlReview=@(& $scriptPath -AdminAuthentication Sql)
Require ($sqlReview.Count -eq 5 -and $sqlReview[0] -like 'OFFLINE REVIEW ONLY:*') 'SQL option must preserve offline Review without a prompt.'
Require ($ast.ParamBlock.Parameters.Name.VariablePath.UserPath -notcontains 'Credential') 'Credentials must originate inside the secure prompt.'
Require ($ast.ParamBlock.Parameters.Name.VariablePath.UserPath -notcontains 'Password') 'Password arguments are forbidden.'
function Expect-Stop([hashtable]$Arguments,[string]$ExpectedMessage){
    $stopped=$false
    try{& $scriptPath @Arguments | Out-Null}
    catch{Require ($_.Exception.Message -like $ExpectedMessage) 'Unexpected guard failure; check whether an external boundary was reached.';$stopped=$true}
    Require $stopped 'Expected fail-closed guard did not stop.'
}
Expect-Stop @{Mode='Provision'} 'STOP: explicit tenant/subscription*'
Expect-Stop @{Mode='Preflight';AdminAuthentication='Sql';TenantId='offline';SubscriptionId='offline';AllApplicationControlsOffConfirmed=$true} 'STOP: secret-safe client/server telemetry review required before SQL administrator credential prompt.'
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
# Direct assignment supports both 5.1's array object and 7's enumerated output.
$ContractPath=Join-Path $PSScriptRoot 'role-contract.json'
$contract=Get-Content -LiteralPath $ContractPath -Raw | ConvertFrom-Json
Require ($contract.Count -eq 324) 'Exact role contract edge count differs.'
foreach($item in @(@('AiCaseDataRuntime',228),@('AiCaseDataCatalogVerifier',6),@('AiIntegrationBootstrap',90))){
    Require (@($contract | Where-Object {$_.Role -ceq $item[0]}).Count -eq $item[1]) ('Role permission count differs: '+$item[0])
}
# Replay BOTH real loader statements under each ConvertFrom-Json output behavior.
# The 5.1 case emits one array object, even when this test runs under PowerShell 7.
# No JSON, permission entry or external boundary is changed by these scoped stubs.
$testTokens=$null;$testParseErrors=$null
$testAst=[System.Management.Automation.Language.Parser]::ParseFile($PSCommandPath,[ref]$testTokens,[ref]$testParseErrors)
Require ($testParseErrors.Count -eq 0) 'Offline test has parser errors.'
foreach($loaderCase in @(@($ast,'expected'),@($testAst,'contract'))){
    $variable=$loaderCase[1]
    $loaders=@($loaderCase[0].EndBlock.Statements | Where-Object {
        $_ -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $_.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
        $_.Left.VariablePath.UserPath -ceq $variable
    })
    Require ($loaders.Count -eq 1) ('Expected one JSON loader for '+$variable)
    foreach($shape in @('ArrayObject51','Enumerated7')){
        & {
            param($loader,$variable,$shape,$entries,$ContractPath)
            function ConvertFrom-Json {
                param([Parameter(ValueFromPipeline=$true)]$InputObject)
                end {
                    if($shape -eq 'ArrayObject51'){Write-Output -NoEnumerate $entries}
                    else {foreach($entry in $entries){$entry}}
                }
            }
            . ([scriptblock]::Create($loader.Extent.Text))
            $loaded=Get-Variable -Name $variable -ValueOnly
            Require ($loaded.Count -eq 324) ('JSON loader must expose 324 entries: '+$variable+'/'+$shape)
            for($index=0;$index -lt 324;$index++){
                Require ([object]::ReferenceEquals($loaded[$index],$entries[$index])) ('JSON loader changed or nested a permission entry: '+$variable+'/'+$shape)
            }
        } $loaders[0] $variable $shape $contract (Join-Path $PSScriptRoot 'role-contract.json')
    }
}
# Exercise connection construction and SecureString conversion with ephemeral synthetic
# data. Extract only these helper definitions; never execute the provisioning body.
foreach($name in @('New-Connection','Convert-InMemory','Same-Set','Set-AdminAuthentication')){
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
# Test both authentication paths using synthetic credentials and CLOSED connections.
# These helpers never call Open; no prompt, Azure module or external service is used.
$script:promptCount=0;$script:tokenCount=0;$script:promptCase='Valid'
function Get-Credential {
    param($UserName,$Message,$ErrorAction)
    $script:promptCount++
    Require ($UserName -ceq 'brian') 'SQL prompt must identify the approved administrator.'
    if($script:promptCase -eq 'Cancelled'){return $null}
    $name='brian';if($script:promptCase -eq 'WrongLogin'){$name='unapproved'}
    $password=[securestring]::new()
    if($script:promptCase -ne 'Empty'){foreach($ch in ([guid]::NewGuid().ToString('N')).ToCharArray()){$password.AppendChar($ch)}}
    return [pscredential]::new($name,$password)
}
function Get-AzAccessToken {
    param($ResourceUrl,$TenantId,$ErrorAction)
    $script:tokenCount++
    Require ($ResourceUrl -ceq 'https://database.windows.net/' -and $TenantId -ceq 'offline-tenant') 'Entra audience/tenant changed.'
    if($script:promptCase -eq 'PlainToken'){return [pscustomobject]@{Token='offline-synthetic-token'}}
    $secureToken=ConvertTo-SecureString 'offline-synthetic-token' -AsPlainText -Force
    return [pscustomobject]@{Token=$secureToken}
}
$c=New-Connection
try{
    $output=@(Set-AdminAuthentication $c Sql 'offline-tenant')
    Require ($output.Count -eq 0) 'Authentication must emit no credential or token objects.'
    Require ($c.State -eq [System.Data.ConnectionState]::Closed -and $c.Credential.UserId -ceq 'brian') 'SQL authentication must set only the closed connection credential.'
    Require ($c.Credential.Password.IsReadOnly() -and !$c.AccessToken -and $script:tokenCount -eq 0) 'SQL mode must use read-only SecureString and never acquire an Entra token.'
    $settings=[System.Data.SqlClient.SqlConnectionStringBuilder]::new($c.ConnectionString)
    Require (!$settings['Password'] -and !$settings['User ID'] -and !$settings['Integrated Security']) 'SQL credentials must stay outside the connection string.'
} finally {if($c.Credential){$c.Credential.Password.Dispose()};$c.Dispose()}
foreach($case in @('Cancelled','WrongLogin','Empty','PlainToken')){
    $script:promptCase=$case;$c=New-Connection;$stopped=$false
    try{
        $method='Sql';if($case -eq 'PlainToken'){$method='Entra'}
        try{Set-AdminAuthentication $c $method 'offline-tenant'}catch{$stopped=$true}
        Require $stopped ('Authentication must stop for synthetic case: '+$case)
        Require (!$c.Credential -and !$c.AccessToken -and $c.State -eq [System.Data.ConnectionState]::Closed) 'Rejected authentication must leave the connection unauthenticated and closed.'
    } finally {$c.Dispose()}
}
$script:promptCase='Valid';$before=$script:promptCount;$c=New-Connection
try{
    $output=@(Set-AdminAuthentication $c Entra 'offline-tenant')
    Require ($output.Count -eq 0 -and !$c.Credential -and $c.AccessToken -ceq 'offline-synthetic-token') 'Entra token path regressed.'
    Require ($script:promptCount -eq $before -and $c.State -eq [System.Data.ConnectionState]::Closed) 'Entra path must never prompt or open a connection in helper tests.'
} finally {$c.AccessToken='';$c.Dispose()}
# Exercise the original orchestration try/catch/finally in isolated child shells.
# Only authentication helpers and orchestration are extracted. A fake connection
# throws on Open; Azure/prompt boundaries are stubs. No SQL network call is possible.
$outerTry=@($ast.EndBlock.Statements | Where-Object {$_ -is [System.Management.Automation.Language.TryStatementAst]})
Require ($outerTry.Count -eq 1) 'Expected one fail-closed orchestration boundary.'
$helpers=''
foreach($name in @('Set-AdminAuthentication','Convert-InMemory')){
    $definition=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    $helpers+=$definition.Extent.Text+"`n"
}
$stubs=@'
param([string]$AdminAuthentication,[string]$Scenario)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$TenantId='offline-tenant';$SubscriptionId='offline-subscription'
$admin=$null;$transaction=$null;$commitStarted=$false;$committed=$false
$stage='AZURE_CONTEXT';$passwords=@{}
function Import-Module {param($Name)}
function Get-AzContext {
    if($Scenario -eq 'ContextFailure'){throw 'SYNTHETIC-SENSITIVE-CONTEXT'}
    return [pscustomobject]@{Tenant=[pscustomobject]@{Id=$TenantId};Subscription=[pscustomobject]@{Id=$SubscriptionId}}
}
function Get-AzKeyVaultSecret {throw 'SYNTHETIC-SENSITIVE-UNEXPECTED-VAULT'}
function Get-AzAccessToken {
    param($ResourceUrl,$TenantId,$ErrorAction)
    if($AdminAuthentication -eq 'Sql'){throw 'SYNTHETIC-SENSITIVE-UNEXPECTED-TOKEN'}
    throw 'SYNTHETIC-SENSITIVE-TOKEN-FAILURE'
}
function Get-Credential {
    param($UserName,$Message,$ErrorAction)
    if($AdminAuthentication -eq 'Entra'){throw 'SYNTHETIC-SENSITIVE-UNEXPECTED-PROMPT'}
    if($Scenario -eq 'PromptFailure'){throw 'SYNTHETIC-SENSITIVE-PROMPT-FAILURE'}
    $password=ConvertTo-SecureString ([guid]::NewGuid().ToString('N')) -AsPlainText -Force
    return [pscredential]::new('brian',$password)
}
function New-Connection {
    $fake=[pscustomobject]@{Credential=$null;AccessToken=''}
    $fake | Add-Member -MemberType ScriptMethod -Name Open -Value {throw 'SYNTHETIC-SENSITIVE-OPEN-FAILURE'}
    $fake | Add-Member -MemberType ScriptMethod -Name Dispose -Value {}
    return $fake
}
'@
$scratch=Join-Path $PSScriptRoot ('../identity-offline-tests-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($scratch)
try{
    $worker=Join-Path $scratch 'sanitized-boundary.ps1'
    [IO.File]::WriteAllText($worker,$stubs+"`n"+$helpers+"`n"+$outerTry[0].Extent.Text)
    foreach($case in @(@('Sql','OpenFailure','ADMIN_CONNECT'),@('Sql','PromptFailure','ADMIN_CONNECT'),@('Entra','TokenFailure','ADMIN_CONNECT'),@('Sql','ContextFailure','AZURE_CONTEXT'))){
        $start=[Diagnostics.ProcessStartInfo]::new()
        $start.FileName=(Get-Process -Id $PID).Path
        $start.Arguments='-NoProfile -NonInteractive -File "'+$worker+'" -AdminAuthentication '+$case[0]+' -Scenario '+$case[1]
        $start.UseShellExecute=$false;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $process=[Diagnostics.Process]::new();$process.StartInfo=$start
        try{
            [void]$process.Start()
            $stdoutTask=$process.StandardOutput.ReadToEndAsync();$stderrTask=$process.StandardError.ReadToEndAsync()
            Require ($process.WaitForExit(30000)) 'Offline child timed out.'
            $captured=$stdoutTask.Result+$stderrTask.Result
            Require ($process.ExitCode -eq 1) 'Administrative connection failure must exit nonzero.'
            Require ($captured -match ('STOP at '+$case[2]) -and $captured -match 'No SQL mutation started') 'Failure must retain bounded stage and recovery status.'
            Require ($captured -notmatch 'SYNTHETIC-SENSITIVE') 'Raw exception escaped the sanitized boundary.'
        } finally {if(!$process.HasExited){$process.Kill()};$process.Dispose()}
    }
} finally {Remove-Item -LiteralPath $scratch -Recurse -Force}
'PASS: parser, 5.1 syntax review, offline defaults, authorization/telemetry guards, package hashes/role counts, TLS construction, prompted SqlCredential, cancellation/invalid credentials, Entra token path and no authentication output.'
'PASS: isolated orchestration failures (SQL open/prompt, Entra token, Azure context) exit nonzero with sanitized stage/recovery output.'
'PASS: production/test JSON loaders retain all 324 entries under Windows PowerShell 5.1 array-object and PowerShell 7 enumerated output.'
'Host: PowerShell '+$PSVersionTable.PSVersion+'; '+[Environment]::OSVersion.Platform
'NOT RUN: Windows PowerShell 5.1 host unless this command is run there; Azure/SQL integration or production checks.'
