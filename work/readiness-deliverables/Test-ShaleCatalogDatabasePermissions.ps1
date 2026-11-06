#requires -Version 5.1
<# Offline only: synthetic SQL/Azure/console boundaries; no production entrypoint runs. #>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Require([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
$path=Join-Path $PSScriptRoot 'Diagnose-ShaleCatalogDatabasePermissions.ps1'
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
Require ($errors.Count -eq 0) 'Diagnostic parser failure.'
$source=[IO.File]::ReadAllText($path)
Require ($source -notmatch '[^\x00-\x7F]') 'Diagnostic must remain ASCII for Windows 5.1.'
Require ($ast.ScriptRequirements.RequiredPSVersion.ToString() -eq '5.1') 'Wrong minimum PowerShell version.'
foreach($kind in @('PipelineChainAst','TernaryExpressionAst')){
    Require (@($ast.FindAll({param($n) $n.GetType().Name -eq $kind},$true)).Count -eq 0) 'PowerShell 7-only syntax.'
}
Require (@($tokens | Where-Object {$_.Kind.ToString() -in @('QuestionQuestion','QuestionQuestionEquals','QuestionDot','QuestionLBracket')}).Count -eq 0) 'PowerShell 7-only operator.'
Require ($source -notmatch '(?i)EXECUTE\s+AS|ExecuteNonQuery|BeginTransaction|AsPlainText|Get-Credential|Get-AzAccessToken') 'Forbidden production boundary.'
# All SQL literals are read-only metadata SELECTs. Review AST here strings, not comments.
$sqlLiterals=@($ast.FindAll({param($n) $n -is [System.Management.Automation.Language.StringConstantExpressionAst] -and $n.Value -match '(?i)\bSELECT\b'},$true))
foreach($literal in $sqlLiterals){
    Require ($literal.Value -notmatch '(?i)\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|GRANT|REVOKE|DENY|ALTER|CREATE|DROP|TRUNCATE)\b') 'Non-read-only SQL literal.'
    Require ($literal.Value -notmatch '(?i)\bFROM\s+dbo\.') 'Protected application read.'
}
Require (@($ast.FindAll({param($n) $n -is [System.Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Get-AzKeyVaultSecret'},$true)).Count -eq 1) 'Exactly one secret GET required.'
Require ($source.Contains("-Name 'shale-ai-catalog-verifier-password'")) 'Wrong secret.'
Require ($source -notmatch 'shale-ai-case-runtime-password|shale-ai-oauth-bootstrap-password') 'Unrelated secret reference.'
Require ($source.Contains("Read-Host -Prompt 'SQL administrator password for brian' -AsSecureString")) 'Console-only secure prompt required.'
# Authentication proof must stay byte-equivalent to the existing verifier after
# substituting this one fixed identity. No weakening of the acceptance predicates.
$original=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Provision-ShaleAiIdentities.ps1'))
$start=$original.IndexOf('        Set-VerificationStep IDENTITY_QUERY')
$end=$original.IndexOf('        $dbAllowed=@')
$proof=$original.Substring($start,$end-$start).Replace('$Identity.Name',"'shale_prod_ai_catalog_verifier'").Replace('$Identity.Role',"'AiCaseDataCatalogVerifier'")
Require ($source.Replace("`r`n","`n").Contains($proof.Replace("`r`n","`n"))) 'Contained identity/SID/session/token proof changed.'
$originalTokens=$null;$originalErrors=$null
$originalAst=[System.Management.Automation.Language.Parser]::ParseInput($original,[ref]$originalTokens,[ref]$originalErrors)
$originalAllowed=$originalAst.Find({param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$dbAllowed'},$true)
$diagnosticAllowed=$ast.Find({param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$dbAllowed'},$true)
& {
    . ([scriptblock]::Create($originalAllowed.Extent.Text))
    $reference=@($dbAllowed)+@('VIEW DEFINITION')
    . ([scriptblock]::Create($diagnosticAllowed.Extent.Text))
    Require ((@($reference | Sort-Object) -join "`n") -ceq (@($dbAllowed | Sort-Object) -join "`n")) 'Approved database allowlist changed.'
}

# Load only pure helpers and construct a CLOSED connection to inspect TLS options.
foreach($name in @('New-Connection','Same-Set','Get-DiagnosticReport')){
    $node=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    Require ($null -ne $node) 'Missing helper.'
    . ([scriptblock]::Create($node.Extent.Text))
}
$c=New-Connection
try {
    $b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new($c.ConnectionString)
    Require ($c.State -eq [System.Data.ConnectionState]::Closed) 'Offline construction opened SQL.'
    Require ($b.Encrypt -and !$b.TrustServerCertificate -and !$b.Pooling -and !$b.PersistSecurityInfo -and $b.ConnectRetryCount -eq 0) 'TLS/pool/retry boundary changed.'
    Require (!$b.Password -and !$b.UserID -and $b.InitialCatalog -ceq 'Shale' -and $b.DataSource -ceq 'tcp:shale.database.windows.net,1433') 'Target/credential-string boundary changed.'
} finally {$c.Dispose()}
# Block live boundaries for actual default/guard calls.
function Import-Module {throw 'SYNTHETIC-SENSITIVE-IMPORT'}
function Get-AzContext {throw 'SYNTHETIC-SENSITIVE-CONTEXT'}
function Get-AzKeyVaultSecret {throw 'SYNTHETIC-SENSITIVE-SECRET'}
function Read-Host {throw 'SYNTHETIC-SENSITIVE-PROMPT'}
Require (@(& $path).Count -eq 1) 'Default Review is not offline.'
# Child worker replaces ALL SQL connection construction and SQL execution, while
# retaining actual credential assignment, orchestration, proofs, report and catch.
$stub=@'
param([string]$Scenario)
function Table([string[]]$Columns,$Rows){
    $t=[System.Data.DataTable]::new()
    foreach($n in $Columns){[void]$t.Columns.Add($n,[object])}
    foreach($r in $Rows){[void]$t.Rows.Add([object[]]$r)}
    return ,$t
}
function Import-Module {}
function Get-AzContext {
    $id='11111111-1111-1111-1111-111111111111'
    if($Scenario -ceq 'Context'){$id='22222222-2222-2222-2222-222222222222'}
    [pscustomobject]@{Tenant=[pscustomobject]@{Id=$id};Subscription=[pscustomobject]@{Id='11111111-1111-1111-1111-111111111111'}}
}
function Read-Host {param($Prompt,[switch]$AsSecureString,$ErrorAction)
    if(!$AsSecureString -or $Prompt -cne 'SQL administrator password for brian'){throw 'SYNTHETIC-SENSITIVE-PROMPT'}
    if($Scenario -ceq 'PromptError'){throw 'SYNTHETIC-SENSITIVE-PROMPT'}
    if($Scenario -ceq 'PromptEmpty'){return [securestring]::new()}
    ConvertTo-SecureString 'SYNTHETIC-SENSITIVE-ADMIN' -AsPlainText -Force
}
function Get-AzKeyVaultSecret {param($VaultName,$Name,$ErrorAction)
    if($VaultName -cne 'shale-prod-secrets' -or $Name -cne 'shale-ai-catalog-verifier-password'){throw 'SYNTHETIC-SENSITIVE-WRONG-SECRET'}
    if($Scenario -ceq 'SecretError'){throw 'SYNTHETIC-SENSITIVE-SECRET'}
    if($Scenario -ceq 'MalformedSecret'){return [pscustomobject]@{Enabled=$true}}
    $secret=[pscustomobject]@{Enabled=$true;SecretValue=(ConvertTo-SecureString 'SYNTHETIC-SENSITIVE-CATALOG' -AsPlainText -Force);Expires=$null;NotBefore=$null}
    if($Scenario -ceq 'Disabled'){$secret.Enabled=$false}
    if($Scenario -ceq 'Expired'){$secret.Expires=[datetime]::UtcNow.AddDays(-1)}
    if($Scenario -ceq 'Future'){$secret.NotBefore=[datetime]::UtcNow.AddDays(1)}
    return $secret
}
function New-Connection {
    $c=[pscustomobject]@{Credential=$null}
    $c | Add-Member -MemberType ScriptMethod -Name Open -Value {
        if($Scenario -ceq 'Open') {throw 'SYNTHETIC-SENSITIVE-OPEN'}
        if($this.Credential.UserId -cnotin @('brian','shale_prod_ai_catalog_verifier')){throw 'SYNTHETIC-SENSITIVE-CREDENTIAL'}
    }
    $c | Add-Member -MemberType ScriptMethod -Name Dispose -Value {if($Scenario -ceq 'Cleanup'){throw 'SYNTHETIC-SENSITIVE-CLEANUP'}}
    return $c
}
$script:grantReads=0;$script:actualReads=0
function Read-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{},$Transaction=$null){
    if($Scenario -ceq 'Query'){throw 'SYNTHETIC-SENSITIVE-QUERY'}
    if($Sql -match 'USER_NAME\(\) UserName'){
        $edition=5;if($Scenario -ceq 'Engine'){$edition=8}
        return Table @('Edition','DbName','ServerName','UserName') @(,@($edition,'Shale','shale','dbo'))
    }
    if($Sql -match 'm.member_principal_id='){
        $role='AiCaseDataCatalogVerifier';if($Scenario -ceq 'Membership'){$role='SYNTHETIC-SENSITIVE-ROLE'}
        return Table @('Role') @(,@($role))
    }
    if($Sql -match 'COUNT\(\*\) FindingCount'){
        $finding=0;if($Scenario -ceq 'SidReuse'){$finding=1}
        return Table @('FindingCount') @(,@($finding))
    }
    if($Sql -match 'dm_exec_sessions'){
        $id=10;$name='shale_prod_ai_catalog_verifier';$type='S';$auth=2;$own=1;$original=$name;$sid='AABB'
        if($Scenario -ceq 'OwnSession'){$own=0}
        if($Scenario -ceq 'LoginMapped'){$auth=1}
        if($Scenario -ceq 'Dbo'){$id=1}
        if($Scenario -ceq 'OriginalName'){$original='SYNTHETIC-SENSITIVE-ORIGINAL'}
        if($Scenario -ceq 'NullSid'){$sid=[DBNull]::Value}
        $rows=@(,@($id,$name,$type,$auth,$sid,$original,'Shale','shale',5,$own))
        if($Scenario -ceq 'NoProof'){$rows=@()}
        return Table @('Id','Name','Type','Auth','Sid','OriginalName','DbName','ServerName','Edition','OwnSession') $rows
    }
    if($Sql -match 'principal_id Id,type Type'){
        $sid='AABB';if($Scenario -ceq 'IndependentSid'){$sid='CCDD'}
        return Table @('Id','Type','Auth','Sid') @(,@(10,'S',2,$sid))
    }
    if($Sql -match 'sys.user_token'){
        $rows=@(@('shale_prod_ai_catalog_verifier','SQL USER'),@('public','ROLE'),@('AiCaseDataCatalogVerifier','ROLE'))
        if($Scenario -ceq 'Token'){$rows+=,@('SYNTHETIC-SENSITIVE-EXTRA','ROLE')}
        if($Scenario -ceq 'TokenDuplicate'){$rows+=,@('public','ROLE')}
        return Table @('Name','Type') $rows
    }
    if($Sql -match 'fn_my_permissions'){
        if($Connection.Credential.UserId -cne 'shale_prod_ai_catalog_verifier'){throw 'SYNTHETIC-SENSITIVE-IMPERSONATION'}
        $script:actualReads++
        $names=@('CONNECT','VIEW DEFINITION','SELECT','VIEW SECURITY DEFINITION')
        if($Scenario -cin @('Approved','ApprovedContract')){$names=@('CONNECT','VIEW DEFINITION','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')}
        if($Scenario -ceq 'Duplicate'){$names+='SELECT'}
        if($Scenario -ceq 'Unknown'){$names+='SYNTHETIC-SENSITIVE-PERMISSION'}
        if($Scenario -ceq 'ActualDrift' -and $script:actualReads -gt 1){$names+='UPDATE'}
        $rows=@();foreach($n in $names){$rows+=,@($n)}
        return Table @('Permission') $rows
    }
    if($Sql -match 'fn_builtin_permissions'){
        $parent='VIEW DEFINITION';if($Scenario -ceq 'Cycle'){$parent='VIEW SECURITY DEFINITION'}
        return Table @('PermissionName','CoveringPermissionName') @(@('CONNECT','CONTROL'),@('VIEW DEFINITION','CONTROL'),@('CONTROL',''),@('SELECT','CONTROL'),@('UPDATE','CONTROL'),@('VIEW SECURITY DEFINITION',$parent),@('VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','CONTROL'),@('VIEW ANY COLUMN MASTER KEY DEFINITION','CONTROL'))
    }
    if($Sql -match 'SourceKind'){
        $script:grantReads++
        $rows=@(@('USER','SELECT','G',1),@('PUBLIC','SELECT','G',1),@('CATALOG_ROLE','VIEW DEFINITION','G',1))
        if($Scenario -ceq 'Transitive'){$rows=@(,@('CATALOG_ROLE','CONTROL','G',1))}
        if($Scenario -ceq 'Deny'){$rows=@(,@('USER','SELECT','D',1))}
        if($Scenario -ceq 'GrantOption'){$rows=@(,@('USER','SELECT','W',7))}
        if($Scenario -ceq 'GrantDrift' -and $script:grantReads -gt 1){$rows+=,@('PUBLIC','UPDATE','G',1)}
        return Table @('SourceKind','PermissionName','PermissionState','GrantorId') $rows
    }
    if($Sql -match 'p.class Class'){
        $contract=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'role-contract.json') -Raw | ConvertFrom-Json
        $rows=@();foreach($e in @($contract | Where-Object {$_.Role -ceq 'AiCaseDataCatalogVerifier'})){$rows+=,@($e.Class,$e.Securable,$e.Permission,$e.State,0,1)}
        if($Scenario -cin @('Contract','ApprovedContract')){$rows=@()}
        return Table @('Class','Securable','Permission','State','MinorId','Grantor') $rows
    }
    throw 'SYNTHETIC-SENSITIVE-UNREVIEWED-QUERY'
}
'@
# Strip only production param block and I/O helper definitions from the AST.
$edits=@([pscustomobject]@{start=$ast.ParamBlock.Extent.StartOffset;length=$ast.ParamBlock.Extent.Text.Length;text=''})
foreach($name in @('New-Connection','Read-Sql')){
    $n=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    $edits+=[pscustomobject]@{start=$n.Extent.StartOffset;length=$n.Extent.Text.Length;text=''}
}
$worker=$source
foreach($edit in @($edits | Sort-Object start -Descending)){$worker=$worker.Remove($edit.start,$edit.length).Insert($edit.start,$edit.text)}
$worker=$worker.Replace('[CmdletBinding()]','')
Require ($worker -notmatch '\[System.Data.SqlClient.SqlConnection\]::new|ExecuteReader|CreateCommand') 'Live SQL boundary survived worker extraction.'
$settings=@'
$Mode='Diagnose';$TenantId='11111111-1111-1111-1111-111111111111';$SubscriptionId=$TenantId
$SecretSafeTelemetryReviewed=$true;$AllApplicationControlsOffConfirmed=$true
if($Scenario -ceq 'Guard'){$SecretSafeTelemetryReviewed=$false}
'@
$temp=Join-Path ([IO.Path]::GetTempPath()) ('shale-catalog-offline-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($temp)
$exe=(Get-Process -Id $PID).Path
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'role-contract.json') -Destination $temp
    $workerPath=Join-Path $temp 'worker.ps1'
    [IO.File]::WriteAllText($workerPath,($stub+"`n"+$settings+"`n"+$worker),[Text.Encoding]::ASCII)
    $cases=@('PromptError','PromptEmpty','MalformedSecret','ApprovedContract','Transitive','Mismatch','Approved','Duplicate','Deny','GrantOption','Contract','Guard','Context','SecretError','Disabled','Expired','Future','Open','Query','Engine','Membership','SidReuse','OwnSession','LoginMapped','Dbo','OriginalName','NullSid','NoProof','IndependentSid','Token','TokenDuplicate','Unknown','Cycle','ActualDrift','GrantDrift','Cleanup')
    foreach($case in $cases){
        $output=@(& $exe -NoProfile -File $workerPath -Scenario $case 2>&1)
        $status=$LASTEXITCODE;$captured=$output -join "`n"
        Require ($captured -notmatch 'SYNTHETIC-SENSITIVE|AABB|CCDD|SqlException|ConnectionString') ('Sensitive output escaped: '+$case)
        $expected=1
        if($case -cin @('ApprovedContract','Transitive','Mismatch','Duplicate','Deny','GrantOption','Contract')){$expected=2}
        if($case -ceq 'Approved'){$expected=0}
        Require ($status -eq $expected) ('Wrong exit status: '+$case+' observed='+$status+' expected='+$expected)
        if($expected -eq 1){
            $checkpoints=@{PromptError='ADMIN_CONNECT';PromptEmpty='ADMIN_CONNECT';MalformedSecret='KEY_VAULT';Guard='GUARDS';Context='AZURE_CONTEXT';SecretError='KEY_VAULT';Disabled='KEY_VAULT';Expired='KEY_VAULT';Future='KEY_VAULT';Open='ADMIN_CONNECT';Query='ADMIN_TARGET';Engine='ADMIN_TARGET';Membership='PRINCIPAL_CATALOG';SidReuse='PRINCIPAL_CATALOG';OwnSession='IDENTITY_ASSERT';LoginMapped='IDENTITY_ASSERT';Dbo='IDENTITY_ASSERT';OriginalName='IDENTITY_ASSERT';NullSid='IDENTITY_ASSERT';NoProof='IDENTITY_ROW_ASSERT';IndependentSid='IDENTITY_ASSERT';Token='TOKEN_ASSERT';TokenDuplicate='TOKEN_ASSERT';Unknown='REPORT';Cycle='REPORT';ActualDrift='DRIFT';GrantDrift='DRIFT';Cleanup='CLEANUP'}
            Require ($captured -ceq ('DIAGNOSTIC_STOP checkpoint='+$checkpoints[$case]+' mutation=NONE controls=OFF')) ('Wrong failure checkpoint: '+$case)
            Require ($captured -cmatch '^DIAGNOSTIC_STOP checkpoint=[A-Z_]+ mutation=NONE controls=OFF$') ('Unbounded error: '+$case);continue}
        $records=@($output | ForEach-Object {ConvertFrom-Json -InputObject ([string]$_)})
        $summary=@($records | Where-Object {$_.kind -ceq 'SUMMARY'})[0]
        Require ($summary.acceptance -ceq 'NOT_VERIFIED' -and $summary.controls -ceq 'OFF') 'Diagnostic claimed acceptance/activation.'
        if($case -ceq 'Mismatch'){
            Require ($summary.actual -eq 4 -and $summary.unexpected -eq 2 -and $summary.absent -eq 2 -and $summary.duplicates -eq 0) 'Reported mismatch counts lost.'
            Require (@($records | Where-Object {$_.kind -ceq 'UNEXPECTED' -and $_.permission -ceq 'SELECT' -and $_.occurrences -eq 1}).Count -eq 1) 'Exact unexpected permission lost.'
            Require (@($records | Where-Object {$_.kind -ceq 'ALLOWLIST_ABSENT' -and $_.occurrences -eq 0}).Count -eq 2) 'Absent names/counts lost.'
            foreach($route in @('DIRECT','INHERITED','INHERITED_HIERARCHY')){Require (@($records | Where-Object {$_.kind -ceq 'PROVENANCE' -and $_.route -ceq $route}).Count -gt 0) ('Missing provenance route '+$route)}
        }
        if($case -ceq 'Transitive'){Require (@($records | Where-Object {$_.kind -ceq 'PROVENANCE' -and $_.permission -ceq 'VIEW SECURITY DEFINITION' -and $_.path.Count -eq 3}).Count -eq 1) 'Transitive hierarchy lost.'}
        if($case -ceq 'Duplicate'){Require ($summary.unexpected -eq 3 -and $summary.duplicates -eq 1) 'Duplicate finding was suppressed.'}
        if($case -ceq 'Deny'){Require (@($records | Where-Object {$_.kind -ceq 'UNRESOLVED'}).Count -eq 2) 'DENY incorrectly counted as grant route.'}
        if($case -ceq 'GrantOption'){Require (@($records | Where-Object {$_.kind -ceq 'PROVENANCE' -and $_.state -ceq 'GRANT_WITH_GRANT_OPTION' -and !$_.grantorIsDbo}).Count -eq 1) 'Grant-option provenance lost.'}
        if($case -cin @('Contract','ApprovedContract')){Require (@($records | Where-Object {$_.kind -ceq 'ROLE_CONTRACT' -and !$_.matches}).Count -eq 1) 'Contract mismatch suppressed.'}
    }
} finally {Remove-Item -LiteralPath $temp -Recurse -Force}
'PASS: 36 offline orchestration/report/redaction scenarios; metadata-only/authentication/static guards.'
