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
# Console.WriteLine bypasses host-specific Write-Error wrapping. Only allowlisted
# checkpoint labels, integer coordinates and fixed evidence states can be emitted.
$referenceDiagnostic=@'
if($Scenario -eq 'ReferenceNull'){
    $checkpoint='OTHER'
    if($script:verificationStep -cin @('CONNECT','IDENTITY_QUERY','IDENTITY_ROW_ASSERT','INDEPENDENT_IDENTITY_QUERY','IDENTITY_ASSERT','TOKEN_QUERY','TOKEN_ASSERT','DATABASE_PERMISSIONS','DATABASE_REQUIRED','SYSTEM_PERMISSIONS','SYSTEM_REQUIRED','OBJECT_INVENTORY','OBJECT_PERMISSIONS','OBJECT_REQUIRED','COLUMN_INVENTORY','COLUMN_QUERY','COLUMN_ASSERT','EXECUTE_SET','CONNECTION_DISPOSE','COMPLETE')){$checkpoint=$script:verificationStep}
    $rows='UNKNOWN';$want='UNKNOWN';$read='UNKNOWN';$update='UNKNOWN'
    $bits=[regex]::Match($script:verificationEvidence,'^ASSERT rows=([0-9]+) wantRead=([01])(?: read=(NULL|ZERO|ONE|OTHER) update=(NULL|ZERO|ONE|OTHER))?$')
    if($bits.Success){
        $rows=$bits.Groups[1].Value;$want=$bits.Groups[2].Value
        if($bits.Groups[3].Success){$read=$bits.Groups[3].Value;$update=$bits.Groups[4].Value}
    }
    $fixture='INVALID';if($script:referenceNullFixtureValid){$fixture='VALID'}
    [Console]::Out.WriteLine(('OFFLINE_REFERENCE_NULL checkpoint={0} object={1} column={2} rows={3} wantRead={4} read={5} update={6} fixture={7}' -f $checkpoint,[int]$script:verificationObject,[int]$script:verificationColumn,$rows,$want,$read,$update,$fixture))
}
'@
$failure=$failure.Insert(1,"`n"+$referenceDiagnostic+"`n")
$stubs=@'
param([string]$Scenario,[string]$Role,[ValidateSet('Host','Wrapped')][string]$ErrorLayout='Host')
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
$script:disposed=$false;$script:referenceNullFixtureValid=$false
# Exercise a wrapped host message without changing the production catch or state.
if($ErrorLayout -ceq 'Wrapped'){
    $ErrorView='NormalView'
    function Write-Error {
        param([string]$Message,$ErrorAction)
        $Message=$Message.Replace('wantRead=1 read=NULL',"wantRead=1`r`nread=NULL")
        Microsoft.PowerShell.Utility\Write-Error -Message $Message -ErrorAction $ErrorAction
    }
}
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
        $result=Table @('CanRead','CanUpdate') @(,@($read,$update))
        if($Scenario -eq 'ReferenceNull' -and $Parameters.o -match '\[AiCaseDataDeployment\]'){
            $script:referenceNullFixtureValid=$result -is [System.Data.DataTable] -and
                $result.Rows.Count -eq 1 -and $result.Columns.Count -eq 2 -and
                $result.Rows[0].IsNull('CanRead') -and $result.Rows[0]['CanRead'] -is [DBNull] -and
                $result.Rows[0]['CanUpdate'] -is [int] -and $result.Rows[0]['CanUpdate'] -eq 0
        }
        return ,$result
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
    $workerSource=$stubs+"`n"+$helpers+"`n"+$invoke.Replace('CATCH_BODY',$failure)
    $workerTokens=$null;$workerErrors=$null
    $workerAst=[System.Management.Automation.Language.Parser]::ParseInput($workerSource,[ref]$workerTokens,[ref]$workerErrors)
    Require ($workerErrors.Count -eq 0) 'Offline worker parser failure.'
    # Fail before launching if extraction accidentally includes real I/O helpers or
    # the production entrypoint. This is a regression guard, not an OS sandbox.
    $allowedCommands=@('Set-StrictMode','Add-Type','Add-Member','Table','Read-Sql','New-Connection','Set-VerificationStep','Same-Set','Permission-List','Permission-Bit','Verify-Login','Where-Object','ForEach-Object','Sort-Object','ConvertTo-SecureString','Write-Error','Microsoft.PowerShell.Utility\Write-Error')
    foreach($command in $workerAst.FindAll({param($n) $n -is [System.Management.Automation.Language.CommandAst]},$true)){
        Require ($command.GetCommandName() -cin $allowedCommands) 'Offline worker contains an unreviewed command; no child launched.'
    }
    $allowedTypes=@('string','string[]','int','object','object[]','pscustomobject','System.Data.DataTable','DBNull','securestring','System.Data.SqlClient.SqlException','System.Data.SqlClient.SqlCredential','guid','regex','Console')
    foreach($type in $workerAst.FindAll({param($n) $n -is [System.Management.Automation.Language.TypeExpressionAst]},$true)){
        Require ($type.TypeName.FullName -cin $allowedTypes) 'Offline worker contains an unreviewed type; no child launched.'
    }
    foreach($boundary in @('New-Connection','Read-Sql')){
        Require (@($workerAst.FindAll({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $boundary},$true)).Count -eq 1) 'Offline boundary must have exactly one fake definition.'
    }
    [IO.File]::WriteAllText($worker,$workerSource)
    $cases=@(
        @('Success','COMPLETE'),@('DisposeFailure','CONNECTION_DISPOSE'),@('OpenFailure','CONNECT'),@('ProofQuery','IDENTITY_QUERY'),
        @('ProofMissing','IDENTITY_ROW_ASSERT'),@('OwnSession','IDENTITY_ASSERT'),@('Independent','IDENTITY_ASSERT'),
        @('Token','TOKEN_ASSERT'),@('DatabaseQuery','DATABASE_PERMISSIONS'),@('DatabaseUnexpected','DATABASE_PERMISSIONS'),
        @('DatabaseMissing','DATABASE_REQUIRED'),@('SystemQuery','SYSTEM_PERMISSIONS'),@('SystemUnexpected','SYSTEM_PERMISSIONS'),
        @('SystemMissing','SYSTEM_REQUIRED'),@('ObjectQuery','OBJECT_PERMISSIONS'),@('ObjectUnexpected','OBJECT_PERMISSIONS'),
        @('ObjectMissing','OBJECT_REQUIRED'),@('ObjectDuplicate','OBJECT_REQUIRED'),@('ColumnInventory','COLUMN_INVENTORY'),
        @('ColumnQuery','COLUMN_QUERY'),@('ColumnRead','COLUMN_ASSERT'),@('ColumnUpdate','COLUMN_ASSERT'),
        @('ReferenceRead','COLUMN_ASSERT'),@('ReferenceNull','COLUMN_ASSERT'),@('ReferenceNull','COLUMN_ASSERT','Wrapped'),@('ExecuteSet','EXECUTE_SET')
    )
    foreach($role in @('AiCaseDataCatalogVerifier','AiCaseDataRuntime','AiIntegrationBootstrap')){
        foreach($case in $cases){
            if($role -ne 'AiCaseDataCatalogVerifier' -and $case[0] -notin @('Success','OpenFailure','ColumnRead','ColumnUpdate','ObjectUnexpected','ExecuteSet')){continue}
            if($case[0] -eq 'ExecuteSet' -and $role -ne 'AiCaseDataRuntime'){continue}
            $start=[Diagnostics.ProcessStartInfo]::new()
            $start.FileName=(Get-Process -Id $PID).Path
            $layout='Host';if($case.Count -eq 3){$layout=$case[2]}
            $start.Arguments='-NoProfile -NonInteractive -File "'+$worker+'" -Scenario '+$case[0]+' -Role '+$role+' -ErrorLayout '+$layout
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
                    if($case[0] -eq 'ReferenceNull'){
                        # Parse stdout only. Never append raw captured output to a failure.
                        $record=[regex]::Match($stdout.Result,'(?m)^OFFLINE_REFERENCE_NULL checkpoint=([A-Z_]+) object=([0-9]+) column=([0-9]+) rows=([0-9]+|UNKNOWN) wantRead=([01]|UNKNOWN) read=(NULL|ZERO|ONE|OTHER|UNKNOWN) update=(NULL|ZERO|ONE|OTHER|UNKNOWN) fixture=(VALID|INVALID)\r?$')
                        Require $record.Success 'ReferenceNull bounded diagnostic record missing; raw output suppressed.'
                        $bounded=$record.Value.TrimEnd([char]13)
                        if($layout -ceq 'Wrapped'){
                            Require ($captured -notmatch 'object=2 column=1 ASSERT rows=1 wantRead=1 read=NULL update=ZERO') 'Wrapped regression must reproduce the original literal-space matcher failure.'
                        }
                        Require ($bounded -ceq 'OFFLINE_REFERENCE_NULL checkpoint=COLUMN_ASSERT object=2 column=1 rows=1 wantRead=1 read=NULL update=ZERO fixture=VALID') ('NULL required column evidence must fail at its exact coordinate. '+$bounded)
                    }
                    Require ($process.ExitCode -eq 1) ('Security finding must fail closed: '+$case[0]+'/'+$role)
                    Require ($captured -match ('checkpoint='+$case[1]+'\b')) ('Wrong failing checkpoint: '+$case[0]+'/'+$role)
                    Require ($captured -match 'Commit completed; verification failed. Users remain. No destructive cleanup.') 'Post-commit recovery status changed.'
                    Require ($captured -notmatch 'SYNTHETIC-SENSITIVE') ('Sensitive diagnostic escaped: '+$case[0])
                    if($case[0] -eq 'ObjectUnexpected'){Require ($captured -match 'unexpected=1') 'Unexpected permission count missing.'}
                    if($case[0] -eq 'ObjectDuplicate'){Require ($captured -match 'missing=0 duplicates=1') 'Duplicate-row shape must remain a failing assertion, with safe counts.'}
                    if($case[0] -eq 'ObjectQuery'){Require ($captured -match 'column=0 QUERY') 'Query failure confused with permission assertion.'}
                }
            } finally {if(!$process.HasExited){$process.Kill()};$process.Dispose()}
        }
    }
} finally {Remove-Item -LiteralPath $scratch -Recurse -Force}
'PASS: offline real verification helpers, three identity paths, fail-closed checkpoints, ReferenceNull typed fixture/unformatted evidence/wrapped-output regression, reviewed fake I/O boundaries and redaction.'
'NOT RUN: real SQL permission semantics, Azure access or Windows PowerShell 5.1 unless run on that host.'
