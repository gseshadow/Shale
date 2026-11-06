#requires -Version 5.1
<# Offline synthetic tests only. Never execute the production Diagnose path. #>
[CmdletBinding()]param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Require([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
function Parse([string]$Path){
    $tokens=$null;$errors=$null
    $ast=[System.Management.Automation.Language.Parser]::ParseFile($Path,[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) 'Parser errors.'
    Require ($ast.ScriptRequirements.RequiredPSVersion.ToString() -eq '5.1') 'Wrong minimum shell.'
    Require (@($tokens | Where-Object {$_.Kind.ToString() -in @('QuestionQuestion','QuestionQuestionEquals','QuestionDot','QuestionLBracket')}).Count -eq 0) 'Unsupported 5.1 operator.'
    foreach($kind in @('PipelineChainAst','TernaryExpressionAst')){
        Require (@($ast.FindAll({param($n) $n.GetType().Name -eq $kind},$true)).Count -eq 0) 'Unsupported 5.1 syntax.'
    }
    return $ast
}
$path=Join-Path $PSScriptRoot 'Diagnose-ShaleObjectPermissions.ps1'
$ast=Parse $path
$source=[IO.File]::ReadAllText($path).Replace("`r`n","`n")
$baseSource=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Diagnose-ShaleCatalogDatabasePermissions.ps1')).Replace("`r`n","`n")
$originalPath=Join-Path $PSScriptRoot 'Provision-ShaleAiIdentities.ps1'
$originalAst=Parse $originalPath
function Definition($Tree,[string]$Name){
    $node=$Tree.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $Name},$true)
    Require ($null -ne $node) ('Missing function '+$Name)
    return $node.Extent.Text.Replace("`r`n","`n")
}
$baseAst=Parse (Join-Path $PSScriptRoot 'Diagnose-ShaleCatalogDatabasePermissions.ps1')
foreach($name in @('Read-Sql','Same-Set','Open-DiagnosticAdmin','Confirm-DiagnosticIdentity')){
    Require ((Definition $ast $name) -ceq (Definition $baseAst $name)) ('Existing credential/identity boundary changed: '+$name)
}
$inventorySql=@($originalAst.FindAll({param($n) $n -is [System.Management.Automation.Language.StringConstantExpressionAst] -and $n.Value -like '*SELECT s.name SchemaName*'},$true))
Require ($inventorySql.Count -eq 1 -and $source.Contains($inventorySql[0].Value.Replace("`r`n","`n"))) 'Ordinal inventory differs from verifier.'
Require ($source -notmatch '[^\x00-\x7F]|ExecuteNonQuery|BeginTransaction|AsPlainText|Get-Credential|Get-AzAccessToken|(?i)EXECUTE\s+AS') 'Forbidden encoding or production boundary.'
foreach($sql in @($ast.FindAll({param($n) $n -is [System.Management.Automation.Language.StringConstantExpressionAst] -and $n.Value -match '(?i)\bSELECT\b'},$true))){
    Require ($sql.Value -notmatch '(?i)\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|GRANT|REVOKE|DENY|ALTER|CREATE|DROP|TRUNCATE)\b|\bFROM\s+dbo\.') 'SQL must read metadata only.'
}
Require (@($ast.FindAll({param($n) $n -is [System.Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Get-AzKeyVaultSecret'},$true)).Count -eq 1) 'Only one secret GET permitted.'
Require ($source.Contains("-Name 'shale-ai-catalog-verifier-password'")) 'Unexpected secret consumer.'
foreach($name in @('Get-ObjectReport','Read-Sql','Table-Signature','Same-Set','New-Connection')){. ([scriptblock]::Create((Definition $ast $name)))}
$c=New-Connection
try{
    $b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new($c.ConnectionString)
    Require ($c.State -eq 'Closed' -and $b.Encrypt -and !$b.TrustServerCertificate -and !$b.Pooling -and !$b.PersistSecurityInfo -and $b.ConnectRetryCount -eq 0) 'TLS/closed connection contract changed.'
    Require (!$b.Password -and !$b.UserID) 'Credential in connection string.'
}finally{$c.Dispose()}
function Table([string[]]$Names,$Rows){
    $t=[System.Data.DataTable]::new()
    foreach($n in $Names){[void]$t.Columns.Add($n,[object])}
    foreach($r in $Rows){[void]$t.Rows.Add([object[]]$r)}
    return ,$t
}
$builtin=Table @('ClassName','PermissionName','CoveringPermissionName','ParentClassName','ParentCoveringPermissionName') @(
    @('OBJECT','SELECT','CONTROL','SCHEMA','SELECT'),@('OBJECT','VIEW DEFINITION','CONTROL','SCHEMA','VIEW DEFINITION'),
    @('OBJECT','UPDATE','CONTROL','SCHEMA','UPDATE'))
$grants=Table @('SourceKind','PermissionClass','MajorId','MinorId','PermissionName','PermissionState','GrantorId') @(
    @('CATALOG_ROLE',1,123,0,'SELECT','G',1),@('CATALOG_ROLE',0,0,0,'VIEW DEFINITION','G',1))
$object=[pscustomobject]@{SchemaName='dbo';ObjectName='SYNTHETIC_REFERENCE';QualifiedName='[dbo].[SYNTHETIC_REFERENCE]';Id=123}
$allowed=@('VIEW DEFINITION','SELECT')
$columns=@('EntityName','SubentityName','Permission','SqlResultCount','SqlPermissionCount','SqlTupleCount')
# Eight rows / two names: six repeated names arise from different subentities.
$rows=@()
$rows+= ,@('dbo.SYNTHETIC_REFERENCE','','VIEW DEFINITION',8,1,1)
foreach($sub in @('','SyntheticColumn1','SyntheticColumn2','SyntheticColumn3','SyntheticColumn4','SyntheticColumn5','SyntheticColumn6')){$rows+= ,@('dbo.SYNTHETIC_REFERENCE',$sub,'SELECT',8,7,1)}
$actual=Table $columns $rows
# Exercise the REAL Read-Sql DataTable.Load and unary-comma return with a local
# DataTableReader, not a mocked permission projection. No SqlConnection exists.
$fake=[pscustomobject]@{InputTable=$actual}
$fake | Add-Member ScriptMethod CreateCommand {
    $cmd=[pscustomobject]@{CommandText='';CommandTimeout=0;Parameters=[pscustomobject]@{};InputTable=$this.InputTable}
    $cmd.Parameters | Add-Member ScriptMethod Clear {}
    $cmd | Add-Member ScriptMethod ExecuteReader {return ,$this.InputTable.CreateDataReader()}
    $cmd | Add-Member ScriptMethod Dispose {}
    return $cmd
}
$loaded=Read-Sql $fake 'SYNTHETIC METADATA ONLY'
Require ($loaded -is [System.Data.DataTable] -and $loaded.Rows.Count -eq 8) 'Read-Sql changed row cardinality or enumerated the table.'
$report=Get-ObjectReport $loaded $object 13 $allowed $builtin $grants
$summary=@($report.records | Where-Object {$_.kind -ceq 'SUMMARY'})[0]
Require ($report.mismatch -and $summary.sqlResultCount -eq 8 -and $summary.dataTableCount -eq 8 -and $summary.processedCount -eq 8 -and $summary.duplicateNames -eq 6 -and $summary.countsAgree -and $summary.multiplicitiesAgree) 'Eight-row reproduction must retain and reject all six duplicate names.'
Require (@($report.records | Where-Object {$_.kind -ceq 'SQL_ROW' -and $_.sqlTupleMultiplicity -eq 1}).Count -eq 8) 'Distinct subentities mistaken for identical SQL rows.'
# Identical full tuples: retain and flag real duplicates, never deduplicate.
$repeated=Table $columns @(@('e','','SELECT',2,2,2),@('e','','SELECT',2,2,2))
$r=Get-ObjectReport $repeated $object 13 $allowed $builtin $grants
Require ($r.mismatch -and @($r.records | Where-Object {$_.kind -ceq 'SQL_ROW' -and $_.sqlTupleMultiplicity -eq 2}).Count -eq 2) 'Identical SQL tuples lost.'
# Zero/one/two/unexpected/missing rows and SQL/PowerShell disagreement.
foreach($case in @('Empty','One','Exact','Unexpected','CountMismatch','MultiplicityMismatch')){
    $fixture=@()
    if($case -ne 'Empty'){$fixture+= ,@('e','','SELECT',1,1,1)}
    if($case -eq 'Exact'){$fixture=@(@('e','','SELECT',2,1,1),@('e','','VIEW DEFINITION',2,1,1))}
    if($case -eq 'Unexpected'){$fixture=@(,@('e','','UPDATE',1,1,1))}
    if($case -eq 'CountMismatch'){$fixture[0][3]=8}
    if($case -eq 'MultiplicityMismatch'){$fixture[0][4]=4}
    $table=Table $columns $fixture
    $r=Get-ObjectReport $table $object 13 $allowed $builtin $grants
    Require ($r.mismatch -eq ($case -ne 'Exact')) ('Incorrect mismatch result '+$case)
    $summary=@($r.records | Where-Object {$_.kind -ceq 'SUMMARY'})[0]
    if($case -eq 'CountMismatch'){Require (!$summary.countsAgree) 'SQL cardinality disagreement hidden.'}
    if($case -eq 'MultiplicityMismatch'){Require (!$summary.multiplicitiesAgree) 'SQL multiplicity disagreement hidden.'}
}
$changed=$actual.Copy();$changed.Rows[0].SubentityName='changed'
Require ((Table-Signature $actual) -cne (Table-Signature $changed)) 'Subentity drift hidden.'
$changed=$actual.Copy();$changed.ImportRow($actual.Rows[0])
Require ((Table-Signature $actual) -cne (Table-Signature $changed)) 'Duplicate drift hidden.'
$invalid=$actual.Copy();$invalid.Rows[0].Permission='SYNTHETIC-SENSITIVE-UNKNOWN'
$stopped=$false
try{Get-ObjectReport $invalid $object 13 $allowed $builtin $grants | Out-Null}catch{$stopped=$true}
Require $stopped 'Unknown permission vocabulary must stop.'
# Run the existing Permission-List and Same-Set themselves against the enriched
# synthetic DataTable. Keeping SQL subentities does not change their projection.
& {
    function Read-Sql {param($Connection,$Sql,$Parameters) return ,$loaded}
    . ([scriptblock]::Create((Definition $originalAst 'Permission-List')))
    . ([scriptblock]::Create((Definition $originalAst 'Same-Set')))
    $perms=@(Permission-List $null $object.QualifiedName 'OBJECT' $allowed)
    Require ($perms.Count -eq 8 -and !(Same-Set $allowed $perms)) 'Existing verifier must retain eight names and fail exact comparison.'
}
function Import-Module {throw 'SYNTHETIC-SENSITIVE-IMPORT'}
function Get-AzContext {throw 'SYNTHETIC-SENSITIVE-CONTEXT'}
function Get-AzKeyVaultSecret {throw 'SYNTHETIC-SENSITIVE-SECRET'}
function Read-Host {throw 'SYNTHETIC-SENSITIVE-PROMPT'}
Require (@(& $path).Count -eq 1) 'Default mode reached external boundary.'
'PASS: metadata-only SQL, exact ordinal inventory, authentication parity, TLS, real DataTableReader/Load/projection counts, eight-row/six-name-duplicate reproduction, identical tuples, empty/exact/missing/unexpected evidence, count/multiplicity disagreement, drift and unknown vocabulary.'
'NOT VERIFIED: Windows PowerShell 5.1 runtime or production SQL semantics.'
# Reuse the established synthetic authentication boundary fixtures. The original
# database diagnostic never runs; only its literal stub is loaded into child shells.
$baseTestAst=Parse (Join-Path $PSScriptRoot 'Test-ShaleCatalogDatabasePermissions.ps1')
$stubAssignment=$baseTestAst.Find({param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -ceq '$stub'},$true)
$stubLiteral=$stubAssignment.Find({param($n) $n -is [System.Management.Automation.Language.StringConstantExpressionAst]},$true)
Require ($null -ne $stubLiteral) 'Missing reusable synthetic boundary fixture.'
$stub=$stubLiteral.Value.Replace('function Read-Sql(','function Read-BaseSql(')
$objectStub=@'
function Read-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{},$Transaction=$null){
    if($Sql -match 'SELECT s.name SchemaName'){
        $script:inventoryReads++
        $rows=@();$limit=13;if($Scenario -ceq 'OrdinalMissing'){$limit=12}
        for($i=1;$i -le $limit;$i++){
            $name='SyntheticObject'+$i;if($i -eq 13){$name='AiCaseDataDeployment'}
            $id=$i;if($Scenario -ceq 'InventoryDrift' -and $script:inventoryReads -gt 1){$id+=100}
            $rows+= ,@('dbo',$name,('[dbo].['+$name+']'),$id)
        }
        return Table @('SchemaName','ObjectName','QualifiedName','Id') $rows
    }
    if($Sql -match 'fn_my_permissions'){
        if($Connection.Credential.UserId -cne 'shale_prod_ai_catalog_verifier' -or $Parameters.n -cne '[dbo].[AiCaseDataDeployment]' -or $Parameters.scope -cne 'OBJECT'){throw 'SYNTHETIC-SENSITIVE-TARGET'}
        $script:actualReads++
        $rows=@()
        if($Scenario -cin @('Exact','Contract')){
            $rows=@(@('dbo.AiCaseDataDeployment','','SELECT',2,1,1),@('dbo.AiCaseDataDeployment','','VIEW DEFINITION',2,1,1))
        }else{
            $rows+= ,@('dbo.AiCaseDataDeployment','','VIEW DEFINITION',8,1,1)
            foreach($sub in @('','Column1','Column2','Column3','Column4','Column5','Column6')){$rows+= ,@('dbo.AiCaseDataDeployment',$sub,'SELECT',8,7,1)}
        }
        if($Scenario -ceq 'CountMismatch'){foreach($r in $rows){$r[3]=9}}
        if($Scenario -ceq 'Unknown'){$rows[0][2]='SYNTHETIC-SENSITIVE-UNKNOWN'}
        if($Scenario -ceq 'ActualDrift' -and $script:actualReads -gt 1){$rows[0][1]='ChangedColumn'}
        return Table @('EntityName','SubentityName','Permission','SqlResultCount','SqlPermissionCount','SqlTupleCount') $rows
    }
    if($Sql -match 'fn_builtin_permissions'){
        return Table @('ClassName','PermissionName','CoveringPermissionName','ParentClassName','ParentCoveringPermissionName') @(
            @('OBJECT','SELECT','CONTROL','SCHEMA','SELECT'),@('OBJECT','VIEW DEFINITION','CONTROL','SCHEMA','VIEW DEFINITION'))
    }
    if($Sql -match 'SourceKind'){
        $script:grantReads++
        $state='G';if($Scenario -ceq 'GrantOption'){$state='W'};if($Scenario -ceq 'Deny'){$state='D'}
        $rows=@(@('CATALOG_ROLE',1,13,0,'SELECT',$state,1),@('CATALOG_ROLE',0,0,0,'VIEW DEFINITION','G',1))
        if($Scenario -ceq 'GrantDrift' -and $script:grantReads -gt 1){$rows[0][3]=1}
        return Table @('SourceKind','PermissionClass','MajorId','MinorId','PermissionName','PermissionState','GrantorId') $rows
    }
    return Read-BaseSql $Connection $Sql $Parameters $Transaction
}
$script:inventoryReads=0
$Mode='Diagnose';$ObjectOrdinal=13
$TenantId='11111111-1111-1111-1111-111111111111';$SubscriptionId=$TenantId
$SecretSafeTelemetryReviewed=$true;$AllApplicationControlsOffConfirmed=$true
if($Scenario -ceq 'Guard'){$SecretSafeTelemetryReviewed=$false}
'@
$edits=@([pscustomobject]@{start=$ast.ParamBlock.Extent.StartOffset;length=$ast.ParamBlock.Extent.Text.Length})
foreach($name in @('New-Connection','Read-Sql')){
    $node=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    $edits+=[pscustomobject]@{start=$node.Extent.StartOffset;length=$node.Extent.Text.Length}
}
$worker=[IO.File]::ReadAllText($path)
foreach($edit in @($edits | Sort-Object start -Descending)){$worker=$worker.Remove($edit.start,$edit.length)}
$worker=$worker.Replace('[CmdletBinding()]','')
Require ($worker -notmatch '\[System.Data.SqlClient.SqlConnection\]::new|ExecuteReader|CreateCommand') 'Live SQL survived extraction.'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('shale-object-offline-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($temp)
try{
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'role-contract.json') -Destination $temp
    $workerPath=Join-Path $temp 'worker.ps1'
    [IO.File]::WriteAllText($workerPath,$stub+"`n"+$objectStub+"`n"+$worker,[Text.Encoding]::ASCII)
    $exe=(Get-Process -Id $PID).Path
    $cases=@('Exact','DuplicateNames','CountMismatch','Contract','GrantOption','Deny','PromptError','PromptEmpty','MalformedSecret','Guard','Context','SecretError','Disabled','Expired','Future','Open','Query','Engine','Membership','SidReuse','OwnSession','LoginMapped','Dbo','OriginalName','NullSid','NoProof','IndependentSid','Token','TokenDuplicate','Unknown','ActualDrift','GrantDrift','InventoryDrift','OrdinalMissing','Cleanup')
    foreach($case in $cases){
        $output=@(& $exe -NoProfile -File $workerPath -Scenario $case 2>&1)
        $status=$LASTEXITCODE;$captured=$output -join "`n"
        Require ($captured -notmatch 'SYNTHETIC-SENSITIVE|AABB|CCDD|SqlException|ConnectionString') ('Sensitive evidence escaped '+$case)
        $expected=1
        if($case -cin @('DuplicateNames','CountMismatch','Contract','GrantOption','Deny')){$expected=2}
        if($case -ceq 'Exact'){$expected=0}
        Require ($status -eq $expected) ('Wrong exit '+$case+': '+$status)
        if($expected -eq 1){
            Require ($captured -cmatch '^DIAGNOSTIC_STOP checkpoint=[A-Z_]+ mutation=NONE controls=OFF$') ('Unsanitized failure '+$case)
            continue
        }
        $records=@($output | ForEach-Object {ConvertFrom-Json -InputObject ([string]$_)})
        $summary=@($records | Where-Object {$_.kind -ceq 'SUMMARY'})[0]
        Require ($summary.acceptance -ceq 'NOT_VERIFIED' -and $summary.controls -ceq 'OFF') 'Diagnostic claimed acceptance.'
        if($case -ceq 'DuplicateNames'){
            Require ($summary.sqlResultCount -eq 8 -and $summary.processedCount -eq 8 -and $summary.duplicateNames -eq 6 -and $summary.countsAgree) 'Duplicate counts lost in orchestration.'
        }
        if($case -ceq 'CountMismatch'){Require (!$summary.countsAgree -and $summary.sqlResultCount -eq 9 -and $summary.processedCount -eq 8) 'Count disagreement suppressed.'}
        if($case -ceq 'Contract'){Require (@($records | Where-Object {$_.kind -ceq 'ROLE_CONTRACT' -and !$_.matches}).Count -eq 1) 'Contract drift hidden.'}
        if($case -cin @('GrantOption','Deny')){
            $state='W';if($case -ceq 'Deny'){$state='D'}
            Require (@($records | Where-Object {$_.kind -ceq 'GRANT_EDGE' -and $_.state -ceq $state}).Count -eq 1) 'Grant state normalized or lost.'
        }
    }
}finally{Remove-Item -LiteralPath $temp -Recurse -Force}
'PASS: 35 isolated orchestration/redaction cases; SQL/Azure/prompt boundaries replaced with synthetic fixtures.'
