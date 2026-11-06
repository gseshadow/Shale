#requires -Version 5.1
<# Offline checkpoint/redaction regressions. Extract helpers only; fake every I/O boundary. #>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Require([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Provision-ShaleAiIdentities.ps1'),[ref]$tokens,[ref]$errors)
Require ($errors.Count -eq 0) 'Provisioning parser failure.'
$helpers=''
foreach($name in @('Same-Set','Set-VerificationStep','Permission-Bit','Permission-List','Verify-Login')){
    $definition=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    Require ($null -ne $definition) ('Missing verification helper: '+$name)
    $helpers+=$definition.Extent.Text+"`n"
}
$outer=@($ast.EndBlock.Statements | Where-Object {$_ -is [System.Management.Automation.Language.TryStatementAst]})
Require ($outer.Count -eq 1) 'Missing sanitized orchestration boundary.'
# Use the actual production catch, including exit status and bounded output.
$failure=$outer[0].CatchClauses[0].Body.Extent.Text
$stubs=@'
param([string]$Scenario,[string]$Role)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$database='Shale';$stage='POST_INSTALL_VERIFY';$transaction=$null;$commitStarted=$true;$committed=$true
$script:verificationStep='';$script:verificationIdentity='';$script:verificationObject=0;$script:verificationColumn=0;$script:verificationEvidence=''
$runtimeExec=@('AiDataAuthenticateV1','AiDataSearchCasesV1','AiDataGetCaseOverviewV1','AiDataRecordCaseFailureV1','AiDataFinishCaseQuotaV1','AiDataAuthorizeCaseHandoffV1')
$bootstrapExec=@('ResolveAiClientV1','ResolveAiCredentialV1','ResolveAiOAuthCodeV1','ResolveAiRefreshV1','ResolveAiRevocationV1','ResolveAiAccessV1')
$referenceTables=@('AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition')
$identity=@{Name='SYNTHETIC-SENSITIVE-IDENTITY';Role=$Role}
if($Scenario -eq 'DisposeFailure'){$identity.Name='shale_prod_ai_catalog_verifier'}
$script:disposed=$false
function New-Connection {
    $fake=[pscustomobject]@{Credential=$null}
    $fake | Add-Member -MemberType ScriptMethod -Name Open -Value {if($Scenario -eq 'OpenFailure'){throw 'SYNTHETIC-SENSITIVE-OPEN'}}
    $fake | Add-Member -MemberType ScriptMethod -Name Dispose -Value {$script:disposed=$true;if($Scenario -eq 'DisposeFailure'){throw 'SYNTHETIC-SENSITIVE-DISPOSE'}}
    return $fake
}
function Table([string[]]$Columns,$Rows) {
    $t=[System.Data.DataTable]::new()
    foreach($column in $Columns){[void]$t.Columns.Add($column,[object])}
    foreach($row in $Rows){[void]$t.Rows.Add([object[]]$row)}
    return ,$t
}
function Read-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{},$Transaction=$null) {
    if($Sql -match 'dm_exec_sessions'){
        if($Scenario -eq 'ProofQuery'){throw 'SYNTHETIC-SENSITIVE-PROOF'}
        $rows=@();if($Scenario -ne 'ProofMissing'){
            $own=1;if($Scenario -eq 'OwnSession'){$own=0}
            $rows+=,@(10,$identity.Name,'S',2,'AABB',$identity.Name,'Shale','shale',5,$own)
        }
        return Table @('Id','Name','Type','Auth','Sid','OriginalName','DbName','ServerName','Edition','OwnSession') $rows
    }
    if($Sql -match 'FROM sys.database_principals WHERE name=@name'){
        $sid='AABB';if($Scenario -eq 'Independent'){$sid='CCDD'}
        return Table @('Id','Type','Auth','Sid') @(,@(10,'S',2,$sid))
    }
    if($Sql -match 'sys.user_token'){
        $rows=@(@($identity.Name,'SQL USER'),@('public','ROLE'),@($identity.Role,'ROLE'))
        if($Scenario -eq 'Token'){$rows+=,@('SYNTHETIC-SENSITIVE-EXTRA-ROLE','ROLE')}
        return Table @('Name','Type') $rows
    }
    if($Sql -match 'fn_my_permissions'){
        if($Parameters.scope -eq 'DATABASE'){
            if($Scenario -eq 'DatabaseQuery'){throw 'SYNTHETIC-SENSITIVE-DATABASE'}
            $perms=@('CONNECT');if($Role -eq 'AiCaseDataCatalogVerifier'){$perms+='VIEW DEFINITION'}
            if($Scenario -eq 'DatabaseUnexpected'){$perms+='CONTROL'}
            if($Scenario -eq 'DatabaseMissing'){$perms=@('CONNECT')}
        } elseif($Parameters.n -eq 'sys.sql_expression_dependencies'){
            if($Scenario -eq 'SystemQuery'){throw 'SYNTHETIC-SENSITIVE-SYSTEM'}
            $perms=@('SELECT','VIEW DEFINITION')
            if($Scenario -eq 'SystemUnexpected'){$perms+='UPDATE'}
            if($Scenario -eq 'SystemMissing'){$perms=@('VIEW DEFINITION')}
        } else {
            if($Scenario -eq 'ObjectQuery'){throw 'SYNTHETIC-SENSITIVE-OBJECT'}
            $perms=@()
            if($Role -eq 'AiCaseDataCatalogVerifier'){
                $perms=@('VIEW DEFINITION')
                if($Parameters.n -match '\[AiCaseDataDeployment\]'){$perms+='SELECT'}
            }
            if($Role -eq 'AiCaseDataRuntime' -and $Parameters.n -match '\[AiData'){$perms=@('EXECUTE')}
            if($Role -eq 'AiIntegrationBootstrap' -and $Parameters.n -match '\[ResolveAi'){$perms=@('EXECUTE')}
            if($Scenario -eq 'ObjectUnexpected'){$perms+='CONTROL'}
            if($Scenario -eq 'ObjectMissing'){$perms=@()}
            if($Scenario -eq 'ObjectDuplicate'){$perms+='VIEW DEFINITION'}
        }
        $rows=@();foreach($perm in $perms){$rows+=,@($perm)}
        return Table @('Permission') $rows
    }
    if($Sql -match 'FROM sys.objects o JOIN sys.schemas'){
        $rows=@(@('dbo','SYNTHETIC-SENSITIVE-OBJECT','[dbo].[SYNTHETIC-SENSITIVE-OBJECT]',100),@('dbo','AiCaseDataDeployment','[dbo].[AiCaseDataDeployment]',101))
        $id=102
        foreach($name in @($runtimeExec+$bootstrapExec)){
            if($Scenario -eq 'ExecuteSet' -and $name -eq $runtimeExec[0]){continue}
            $rows+=,@('dbo',$name,('[dbo].['+$name+']'),$id);$id++
        }
        return Table @('SchemaName','ObjectName','QualifiedName','Id') $rows
    }
    if($Sql -match 'FROM sys.columns'){
        if($Scenario -eq 'ColumnInventory'){throw 'SYNTHETIC-SENSITIVE-COLUMNS'}
        return Table @('Name') @(,@('SYNTHETIC-SENSITIVE-COLUMN'))
    }
    if($Sql -match 'HAS_PERMS_BY_NAME'){
        if($Scenario -eq 'ColumnQuery'){throw 'SYNTHETIC-SENSITIVE-COLUMN-QUERY'}
        $read=0;$update=0
        if($Role -eq 'AiCaseDataCatalogVerifier' -and $Parameters.o -match '\[AiCaseDataDeployment\]'){$read=1}
        if($Scenario -eq 'ColumnRead'){$read=1}
        if($Scenario -eq 'ColumnUpdate'){$update=1}
        if($Parameters.o -match '\[AiCaseDataDeployment\]'){
            if($Scenario -eq 'ReferenceRead'){$read=0}
            if($Scenario -eq 'ReferenceNull'){$read=[DBNull]::Value}
        }
        return Table @('CanRead','CanUpdate') @(,@($read,$update))
    }
    throw 'SYNTHETIC-SENSITIVE-UNEXPECTED-SQL'
}
'@
$invoke=@'
$password=ConvertTo-SecureString ([guid]::NewGuid().ToString('N')) -AsPlainText -Force
$password.MakeReadOnly()
try {
    try {Verify-Login $identity $password 'FAKE-ADMIN'}
    catch CATCH_BODY
} finally {
    $password.Dispose()
    if(!$script:disposed){throw 'Fake verification connection must be disposed.'}
}
'@
$scratch=Join-Path $PSScriptRoot ('../identity-checkpoint-tests-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($scratch)
try {
    $worker=Join-Path $scratch 'verification.ps1'
    [IO.File]::WriteAllText($worker,$stubs+"`n"+$helpers+"`n"+$invoke.Replace('CATCH_BODY',$failure))
    $cases=@(
        @('Success','COMPLETE'),@('DisposeFailure','CONNECTION_DISPOSE'),@('OpenFailure','CONNECT'),@('ProofQuery','IDENTITY_QUERY'),
        @('ProofMissing','IDENTITY_ROW_ASSERT'),@('OwnSession','IDENTITY_ASSERT'),@('Independent','IDENTITY_ASSERT'),
        @('Token','TOKEN_ASSERT'),@('DatabaseQuery','DATABASE_PERMISSIONS'),@('DatabaseUnexpected','DATABASE_PERMISSIONS'),
        @('DatabaseMissing','DATABASE_REQUIRED'),@('SystemQuery','SYSTEM_PERMISSIONS'),@('SystemUnexpected','SYSTEM_PERMISSIONS'),
        @('SystemMissing','SYSTEM_REQUIRED'),@('ObjectQuery','OBJECT_PERMISSIONS'),@('ObjectUnexpected','OBJECT_PERMISSIONS'),
        @('ObjectMissing','OBJECT_REQUIRED'),@('ObjectDuplicate','OBJECT_REQUIRED'),@('ColumnInventory','COLUMN_INVENTORY'),
        @('ColumnQuery','COLUMN_QUERY'),@('ColumnRead','COLUMN_ASSERT'),@('ColumnUpdate','COLUMN_ASSERT'),
        @('ReferenceRead','COLUMN_ASSERT'),@('ReferenceNull','COLUMN_ASSERT'),@('ExecuteSet','EXECUTE_SET')
    )
    foreach($role in @('AiCaseDataCatalogVerifier','AiCaseDataRuntime','AiIntegrationBootstrap')){
        foreach($case in $cases){
            if($role -ne 'AiCaseDataCatalogVerifier' -and $case[0] -notin @('Success','OpenFailure','ColumnRead','ColumnUpdate','ObjectUnexpected','ExecuteSet')){continue}
            if($case[0] -eq 'ExecuteSet' -and $role -ne 'AiCaseDataRuntime'){continue}
            $start=[Diagnostics.ProcessStartInfo]::new()
            $start.FileName=(Get-Process -Id $PID).Path
            $start.Arguments='-NoProfile -NonInteractive -File "'+$worker+'" -Scenario '+$case[0]+' -Role '+$role
            $start.UseShellExecute=$false;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
            $process=[Diagnostics.Process]::new();$process.StartInfo=$start
            try {
                [void]$process.Start()
                $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
                Require ($process.WaitForExit(30000)) 'Verification child timed out.'
                $captured=$stdout.Result+$stderr.Result
                if($case[0] -eq 'Success'){
                    Require ($process.ExitCode -eq 0 -and $captured -match 'Verified contained login') ('Synthetic complete verification failed: '+$role)
                } else {
                    Require ($process.ExitCode -eq 1) ('Security finding must fail closed: '+$case[0]+'/'+$role)
                    Require ($captured -match ('checkpoint='+$case[1]+'\b')) ('Wrong failing checkpoint: '+$case[0]+'/'+$role)
                    Require ($captured -match 'Commit completed; verification failed. Users remain. No destructive cleanup.') 'Post-commit recovery status changed.'
                    Require ($captured -notmatch 'SYNTHETIC-SENSITIVE') ('Sensitive diagnostic escaped: '+$case[0])
                    if($case[0] -eq 'ObjectUnexpected'){Require ($captured -match 'unexpected=1') 'Unexpected permission count missing.'}
                    if($case[0] -eq 'ObjectDuplicate'){Require ($captured -match 'missing=0 duplicates=1') 'Duplicate-row shape must remain a failing assertion, with safe counts.'}
                    if($case[0] -eq 'ObjectQuery'){Require ($captured -match 'column=0 QUERY') 'Query failure confused with permission assertion.'}
                    if($case[0] -eq 'ReferenceNull'){Require ($captured -match 'object=2 column=1 ASSERT rows=1 wantRead=1 read=NULL update=ZERO') 'NULL required column evidence must fail at its exact coordinate.'}
                }
            } finally {if(!$process.HasExited){$process.Kill()};$process.Dispose()}
        }
    }
} finally {Remove-Item -LiteralPath $scratch -Recurse -Force}
'PASS: offline real verification helpers, three identity paths, fail-closed checkpoint selection, permission/column findings and post-commit redaction.'
'NOT RUN: real SQL permission semantics, Azure access or Windows PowerShell 5.1 unless run on that host.'
