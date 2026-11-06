#requires -Version 5.1
<# Offline verifier regressions. Extract functions only; all SQL boundaries are synthetic. #>
[CmdletBinding()]param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Require([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
$tokens=$null;$errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Provision-ShaleAiIdentities.ps1'),[ref]$tokens,[ref]$errors)
Require ($errors.Count -eq 0) 'Verifier parser failed.'
foreach($name in @('Read-Sql','Edge-Key','Same-Set','Get-CatalogDatabaseDerivedPermissions','Object-PermissionEvidence','Permission-List')){
    $node=$ast.Find({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    Require ($null -ne $node) ('Missing verifier helper: '+$name)
    . ([scriptblock]::Create($node.Extent.Text))
}
function Table([string[]]$Names,$Rows){
    $t=[System.Data.DataTable]::new()
    foreach($n in $Names){[void]$t.Columns.Add($n,[object])}
    foreach($r in $Rows){[void]$t.Rows.Add([object[]]$r)}
    return ,$t
}
$referenceTables=@('AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition')
$expected=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'role-contract.json') -Raw | ConvertFrom-Json
$entity='dbo.AiCaseDataDeployment';$qualified='[dbo].[AiCaseDataDeployment]'
$rows=@(@($entity,'','SELECT',[long]8),@($entity,'','VIEW DEFINITION',[long]8))
foreach($index in 1..6){$rows+=,@($entity,('SyntheticColumn'+$index),'SELECT',[long]8)}
$inputTable=Table @('EntityName','SubentityName','Permission','SqlResultCount') $rows
# Real DataTable.Load/unary-comma path; no SQL connection or credential exists.
$fake=[pscustomobject]@{InputTable=$inputTable}
$fake | Add-Member ScriptMethod CreateCommand {
    $cmd=[pscustomobject]@{CommandText='';CommandTimeout=0;Parameters=[pscustomobject]@{};InputTable=$this.InputTable}
    $cmd.Parameters | Add-Member ScriptMethod Clear {}
    $cmd | Add-Member ScriptMethod ExecuteReader {return ,$this.InputTable.CreateDataReader()}
    $cmd | Add-Member ScriptMethod Dispose {}
    return $cmd
}
$loaded=Read-Sql $fake 'SYNTHETIC METADATA ONLY'
Require ($loaded -is [System.Data.DataTable] -and $loaded.Rows.Count -eq 8) 'Real result loader changed eight-row cardinality.'
$script:case='Exact';$script:contractReads=0;$script:verificationEvidence=''
function Read-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{}){
    if($Sql -match 'fn_my_permissions'){
        Require ($Sql -match 'entity_name EntityName,subentity_name SubentityName,permission_name Permission' -and $Sql -match 'COUNT_BIG\(\*\) OVER\(\) SqlResultCount' -and $Sql -notmatch '(?i)\bDISTINCT\b') 'Effective evidence query lost tuples/counts.'
        return ,$loaded
    }
    if($Sql -match "WHERE r.name=N'AiCaseDataCatalogVerifier'"){
        $script:contractReads++
        $edges=@();foreach($e in @($expected | Where-Object {$_.Role -ceq 'AiCaseDataCatalogVerifier'})){$edges+=,@($e.Role,$e.Class,$e.Securable,$e.Permission,$e.State,0,1)}
        switch($script:case){
            'GrantOption' {$edges[1][4]='W'}
            'Deny' {$edges[1][4]='D'}
            'Revoke' {$edges[1][4]='R'}
            'Grantor' {$edges[1][6]=2}
            'ColumnGrant' {$edges[1][5]=1}
            'ExtraGrant' {$edges+=,@('AiCaseDataCatalogVerifier',1,$entity,'SELECT','G',1,1)}
            'MissingGrant' {$edges=$edges[1..5]}
            'DuplicateGrant' {$edges+=,@($edges[1])}
        }
        return Table @('Role','Class','Securable','Permission','State','MinorId','Grantor') $edges
    }
    if($Sql -match 'fn_builtin_permissions'){
        return Table @('ClassName','PermissionName','CoveringPermissionName') @(@('DATABASE','VIEW PERFORMANCE DEFINITION','VIEW DEFINITION'),@('DATABASE','VIEW SECURITY DEFINITION','VIEW DEFINITION'))
    }
    if($Sql -match 'FROM sys.columns'){
        $rows=@();foreach($index in 1..6){$rows+=,@(('SyntheticColumn'+$index))}
        return Table @('Name') $rows
    }
    throw 'Unreviewed offline SQL boundary.'
}
$script:verificationObject=13
$perms=@(Permission-List $null $qualified 'OBJECT' @('SELECT','VIEW DEFINITION') 'FAKE-ADMIN' 'AiCaseDataCatalogVerifier' $entity)
$e=$script:verificationObjectEvidence
Require ((Same-Set $perms @('SELECT','VIEW DEFINITION')) -and $e.SqlCount -eq 8 -and $e.DataTableCount -eq 8 -and $e.ProcessedCount -eq 8 -and $e.ColumnPermissions.Count -eq 6 -and [object]::ReferenceEquals($e.Table,$loaded) -and $script:contractReads -eq 1) 'Production object 13: full eight-row evidence must survive and only two object permissions compare.'
Require ((@($e.Table.Rows.Permission).Count-@($e.Table.Rows.Permission | Sort-Object -Unique).Count) -eq 6) 'Production fixture must retain six repeated names.'
# Accept the exact supplied qualified spelling too, without changing raw evidence.
$loaded=$inputTable.Copy();foreach($r in $loaded.Rows){$r.EntityName=$qualified}
$perms=@(Permission-List $null $qualified 'OBJECT' @('SELECT','VIEW DEFINITION') 'FAKE-ADMIN' 'AiCaseDataCatalogVerifier' $entity)
Require ((Same-Set $perms @('SELECT','VIEW DEFINITION')) -and $script:verificationObjectEvidence.Table.Rows[0].EntityName -ceq $qualified) 'Qualified target spelling must retain its raw entity evidence.'
# Each independent corruption must stop; no deduplication, scope flattening or count coercion.
foreach($case in @('IdenticalTuple','ColumnTupleDuplicate','Unexpected','ColumnUpdate','ColumnViewDefinition','MissingSelect','MissingDefinition','WrongEntity','NullEntity','NullSubentity','NullPermission','UnknownColumn','SqlCount','PartialSqlCount','NullCount','StringCount','Empty','ColumnOnly','WrongRole','UnapprovedObject','EmptyAllowlist','GrantOption','Deny','Revoke','Grantor','ColumnGrant','ExtraGrant','MissingGrant','DuplicateGrant')){
    $script:case=$case;$loaded=$inputTable.Copy();$role='AiCaseDataCatalogVerifier';$target=$entity;$allowed=@('SELECT','VIEW DEFINITION')
    switch($case){
        'IdenticalTuple' {$loaded.ImportRow($loaded.Rows[0]);foreach($r in $loaded.Rows){$r.SqlResultCount=9}}
        'ColumnTupleDuplicate' {$loaded.Rows[3].SubentityName=$loaded.Rows[2].SubentityName}
        'Unexpected' {$loaded.Rows[0].Permission='CONTROL'}
        'ColumnUpdate' {$loaded.Rows[2].Permission='UPDATE'}
        'ColumnViewDefinition' {$loaded.Rows[2].Permission='VIEW DEFINITION'}
        'MissingSelect' {$loaded.Rows.RemoveAt(0);foreach($r in $loaded.Rows){$r.SqlResultCount=7}}
        'MissingDefinition' {$loaded.Rows.RemoveAt(1);foreach($r in $loaded.Rows){$r.SqlResultCount=7}}
        'WrongEntity' {$loaded.Rows[2].EntityName='dbo.Other'}
        'NullEntity' {$loaded.Rows[2].EntityName=[DBNull]::Value}
        'NullSubentity' {$loaded.Rows[2].SubentityName=[DBNull]::Value}
        'NullPermission' {$loaded.Rows[2].Permission=[DBNull]::Value}
        'UnknownColumn' {$loaded.Rows[2].SubentityName='Unknown'}
        'SqlCount' {foreach($r in $loaded.Rows){$r.SqlResultCount=7}}
        'PartialSqlCount' {$loaded.Rows[7].SqlResultCount=7}
        'NullCount' {$loaded.Rows[0].SqlResultCount=[DBNull]::Value}
        'StringCount' {$loaded.Rows[0].SqlResultCount='8'}
        'Empty' {$loaded.Rows.Clear()}
        'ColumnOnly' {$loaded.Rows[0].SubentityName='SyntheticColumn0'}
        'WrongRole' {$role='AiCaseDataRuntime'}
        'UnapprovedObject' {$target='dbo.Other';foreach($r in $loaded.Rows){$r.EntityName=$target}}
        'EmptyAllowlist' {$allowed=@()}
    }
    $stopped=$false
    try{
        $actual=@(Permission-List $null $qualified 'OBJECT' $allowed 'FAKE-ADMIN' $role $target)
        if(!(Same-Set $allowed $actual)){throw 'Required object authority missing.'}
    }catch{$stopped=$true}
    Require $stopped ('Object permission fault was accepted: '+$case)
}
# Object-only legacy shapes remain valid for metadata, execution and invisible objects.
$script:case='Exact'
foreach($perms in @(@('VIEW DEFINITION'),@('EXECUTE'),@())){
    $rows=@();foreach($p in $perms){$rows+=,@($entity,'',$p,$perms.Count)}
    $loaded=Table @('EntityName','SubentityName','Permission','SqlResultCount') $rows
    $actual=@(Permission-List $null $qualified 'OBJECT' $perms $null 'AiCaseDataRuntime' $entity)
    Require (Same-Set $perms $actual) 'Object-only/empty legacy permission shape changed.'
}
'PASS: production object 13 eight-row tuple regression, real DataTable.Load counts, object/column separation, 29 independent tuple/scope/count/role-edge faults, object-only/empty evidence.'
'NOT VERIFIED: Windows PowerShell 5.1 or production SQL; all boundaries above are offline.'
