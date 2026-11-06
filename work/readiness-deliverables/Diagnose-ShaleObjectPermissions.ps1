#requires -Version 5.1
<# Default Review is offline. Diagnose reads permission metadata using one existing
catalog secret and actual contained SQL authentication. No SQL mutation or activation.
No transcripts, traces, raw error exports, or parameter/credential capture. #>
[CmdletBinding()]
param(
    [ValidateSet('Review','Diagnose')][string]$Mode='Review',
    [ValidateRange(1,2147483647)][int]$ObjectOrdinal=13,
    [string]$TenantId,
    [string]$SubscriptionId,
    [switch]$SecretSafeTelemetryReviewed,
    [switch]$AllApplicationControlsOffConfirmed
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$VerbosePreference='SilentlyContinue';$DebugPreference='SilentlyContinue'
$InformationPreference='SilentlyContinue';$ProgressPreference='SilentlyContinue'
$database='Shale'
$referenceTables=@('AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition')
if($Mode -ceq 'Review'){
    'OFFLINE REVIEW: catalog object permission diagnostic; no connections or secrets.'
    return
}
function Read-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{},$Transaction=$null) {
    $cmd=$Connection.CreateCommand(); $cmd.CommandText=$Sql; $cmd.CommandTimeout=30
    if ($Transaction) {$cmd.Transaction=$Transaction}
    try {
        foreach($key in $Parameters.Keys) {
            $p=$cmd.Parameters.Add('@'+$key,[System.Data.SqlDbType]::NVarChar,-1)
            $p.Value=$Parameters[$key]
        }
        $r=$cmd.ExecuteReader()
        try {$t=[System.Data.DataTable]::new();$t.Load($r);return ,$t} finally {$r.Dispose()}
    } finally {$cmd.Parameters.Clear();$cmd.Dispose()}
}
function New-Connection {
    $b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $b['Data Source']='tcp:shale.database.windows.net,1433';$b['Initial Catalog']='Shale'
    $b['Encrypt']='True';$b['TrustServerCertificate']=$false;$b['Pooling']=$false
    $b['Connect Timeout']=15;$b['Persist Security Info']=$false
    $b['Application Name']='ShaleObjectPermissionDiagnostic';$b['ConnectRetryCount']=0
    return [System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
}
function Same-Set($Left,$Right) {
    return ((@($Left | Sort-Object) -join "`n") -ceq (@($Right | Sort-Object) -join "`n"))
}

function Set-VerificationStep([string]$Step){$script:checkpoint=$Step}
function Open-DiagnosticAdmin($Connection){
    $secure=$null;$assigned=$false
    try {
        $secure=Read-Host -Prompt 'SQL administrator password for brian' -AsSecureString -ErrorAction Stop
        if($null -eq $secure -or $secure.Length -lt 1){throw 'STOP'}
        $secure.MakeReadOnly()
        $Connection.Credential=[System.Data.SqlClient.SqlCredential]::new('brian',$secure)
        $assigned=$true
        $Connection.Open()
    } finally {if($null -ne $secure -and !$assigned){$secure.Dispose()}}
}
function Confirm-DiagnosticIdentity($c,$Admin){
        Set-VerificationStep IDENTITY_QUERY
        $proof=Read-Sql $c @'
SELECT p.principal_id Id,p.name Name,p.type Type,p.authentication_type Auth,CONVERT(varchar(170),p.sid,2) Sid,
 ORIGINAL_LOGIN() OriginalName,DB_NAME() DbName,CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) ServerName,
 CONVERT(int,SERVERPROPERTY('EngineEdition')) Edition,
 (SELECT COUNT(*) FROM sys.dm_exec_sessions s WHERE s.session_id=@@SPID AND s.authenticating_database_id=DB_ID() AND s.original_security_id=p.sid) OwnSession
FROM sys.database_principals p WHERE p.principal_id=USER_ID();
'@
        Set-VerificationStep IDENTITY_ROW_ASSERT
        if($proof.Rows.Count -ne 1){throw 'STOP: identity evidence missing.'};$p=$proof.Rows[0]
        Set-VerificationStep INDEPENDENT_IDENTITY_QUERY
        $independent=Read-Sql $Admin 'SELECT principal_id Id,type Type,authentication_type Auth,CONVERT(varchar(170),sid,2) Sid FROM sys.database_principals WHERE name=@name;' @{name='shale_prod_ai_catalog_verifier'}
        Set-VerificationStep IDENTITY_ASSERT
        if($independent.Rows.Count -ne 1 -or $p.Id -le 4 -or $p.Name -cne 'shale_prod_ai_catalog_verifier' -or $p.OriginalName -cne 'shale_prod_ai_catalog_verifier' -or
           $p.Type -cne 'S' -or $p.Auth -ne 2 -or $p.OwnSession -ne 1 -or $p.Edition -ne 5 -or $p.DbName -cne $database -or
           $p.ServerName -notin @('shale','shale.database.windows.net') -or $p.Sid -cne $independent.Rows[0].Sid -or
           $p.Id -ne $independent.Rows[0].Id -or $p.Type -cne $independent.Rows[0].Type -or $p.Auth -ne $independent.Rows[0].Auth){throw 'STOP: actual contained authentication proof failed.'}
        Set-VerificationStep TOKEN_QUERY
        $tokens=Read-Sql $c 'SELECT name Name,type Type FROM sys.user_token;'
        Set-VerificationStep TOKEN_ASSERT
        if(!(Same-Set @('shale_prod_ai_catalog_verifier','public','AiCaseDataCatalogVerifier') @($tokens.Rows.Name)) -or
           @($tokens.Rows | Where-Object {$_.Name -ceq 'shale_prod_ai_catalog_verifier' -and $_.Type -cne 'SQL USER'}).Count){throw 'STOP: unexpected effective user token.'}
}
# Match the verifier's inventory and ordering exactly; ordinal is one-based.
function Read-ObjectInventory($Admin){
    Read-Sql $Admin @'
SELECT s.name SchemaName,o.name ObjectName,QUOTENAME(s.name)+N'.'+QUOTENAME(o.name) QualifiedName,o.object_id Id
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC') ORDER BY s.name,o.name;
'@
}
function Read-ObjectPermissions($Catalog,$Object){
    Read-Sql $Catalog @'
SELECT entity_name EntityName,subentity_name SubentityName,permission_name Permission,
 COUNT_BIG(*) OVER() SqlResultCount,
 COUNT_BIG(*) OVER(PARTITION BY permission_name) SqlPermissionCount,
 COUNT_BIG(*) OVER(PARTITION BY entity_name,subentity_name,permission_name) SqlTupleCount
FROM sys.fn_my_permissions(@n,@scope);
'@ @{n=$Object.QualifiedName;scope='OBJECT'}
}
function Read-ObjectGrants($Admin,$Object){
    Read-Sql $Admin @'
SELECT CASE p.grantee_principal_id
 WHEN DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier') THEN N'USER'
 WHEN DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier') THEN N'CATALOG_ROLE'
 WHEN DATABASE_PRINCIPAL_ID(N'public') THEN N'PUBLIC' END SourceKind,
 p.class PermissionClass,p.major_id MajorId,p.minor_id MinorId,
 p.permission_name PermissionName,p.state PermissionState,
 p.grantor_principal_id GrantorId
FROM sys.database_permissions p
WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier'),
 DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier'),DATABASE_PRINCIPAL_ID(N'public'))
 AND (p.class=0 OR (p.class=3 AND p.major_id=(SELECT schema_id FROM sys.objects WHERE object_id=CONVERT(int,@id)))
 OR (p.class=1 AND p.major_id=CONVERT(int,@id)))
ORDER BY SourceKind,p.class,p.major_id,p.minor_id,PermissionName,PermissionState,GrantorId;
'@ @{id=[string]$Object.Id}
}
function Table-Signature($Table){
    # Compare complete row multisets, including duplicates; no unique filtering.
    return (@(foreach($r in $Table.Rows){ConvertTo-Json -InputObject @($r.ItemArray) -Compress}) | Sort-Object) -join "`n"
}
function Get-ObjectReport($Actual,$Object,[int]$Ordinal,[string[]]$Allowed,$Builtin,$Grants){
    if($Actual -isnot [System.Data.DataTable] -or $Builtin.Rows.Count -lt 1){throw 'STOP'}
    $vocabulary=@($Builtin.Rows | ForEach-Object {$_.PermissionName})
    $records=@();$sqlCount=0;$multiplicityMismatch=$false
    if($Actual.Rows.Count){$sqlCount=[long]$Actual.Rows[0].SqlResultCount}
    # Exactly the existing Permission-List projection, kept duplicate-sensitive.
    $processed=@($Actual.Rows | ForEach-Object {$_.Permission})
    $records+=[pscustomobject]@{kind='TARGET';ordinal=$Ordinal;schema=$Object.SchemaName;object=$Object.ObjectName;objectId=[int]$Object.Id}
    foreach($r in $Actual.Rows){
        if($r.Permission -cnotin $vocabulary -or [long]$r.SqlResultCount -ne $sqlCount -or
           [long]$r.SqlPermissionCount -lt 1 -or [long]$r.SqlTupleCount -lt 1){throw 'STOP'}
        $nameCount=@($processed | Where-Object {$_ -ceq $r.Permission}).Count
        $tupleCount=@($Actual.Rows | Where-Object {$_.Permission -ceq $r.Permission -and $_.EntityName -ceq $r.EntityName -and $_.SubentityName -ceq $r.SubentityName}).Count
        if($nameCount -ne [long]$r.SqlPermissionCount -or $tupleCount -ne [long]$r.SqlTupleCount){$multiplicityMismatch=$true}
        $records+=[pscustomobject]@{kind='SQL_ROW';permission=$r.Permission;entity=$r.EntityName;subentity=$r.SubentityName;
            sqlPermissionMultiplicity=[long]$r.SqlPermissionCount;sqlTupleMultiplicity=[long]$r.SqlTupleCount;
            processedPermissionMultiplicity=$nameCount;dataTableTupleMultiplicity=$tupleCount;allowedName=($r.Permission -cin $Allowed)}
    }
    foreach($g in $Grants.Rows){
        if($g.SourceKind -cnotin @('USER','CATALOG_ROLE','PUBLIC') -or $g.PermissionState -cnotin @('G','W','D','R') -or
           $g.PermissionName -cnotin $vocabulary -or $g.PermissionClass -notin @(0,1,3) -or
           $g.MinorId -is [DBNull] -or $g.GrantorId -is [DBNull]){throw 'STOP'}
        $records+=[pscustomobject]@{kind='GRANT_EDGE';source=$g.SourceKind;permission=$g.PermissionName;state=$g.PermissionState;
            permissionClass=[int]$g.PermissionClass;majorId=[int]$g.MajorId;minorId=[int]$g.MinorId;grantorIsDbo=([int]$g.GrantorId -eq 1)}
    }
    foreach($h in $Builtin.Rows){
        if($h.ClassName -cnotin @('DATABASE','SCHEMA','OBJECT','COLUMN') -or $h.PermissionName -cnotmatch '^[A-Z][A-Z0-9 ]{0,127}$'){throw 'STOP'}
        $records+=[pscustomobject]@{kind='BUILTIN_HIERARCHY';permissionClass=$h.ClassName;permission=$h.PermissionName;
            coveringPermission=[string]$h.CoveringPermissionName;parentClass=[string]$h.ParentClassName;parentPermission=[string]$h.ParentCoveringPermissionName}
    }
    $missing=@($Allowed | Where-Object {$_ -cnotin $processed}).Count
    $unexpected=@($processed | Where-Object {$_ -cnotin $Allowed}).Count
    $duplicates=$processed.Count-@($processed | Sort-Object -Unique).Count
    $records+=[pscustomobject]@{kind='SUMMARY';identity='CATALOG';ordinal=$Ordinal;sqlResultCount=$sqlCount;
        dataTableCount=$Actual.Rows.Count;processedCount=$processed.Count;countsAgree=($sqlCount -eq $Actual.Rows.Count -and $sqlCount -eq $processed.Count);
        multiplicitiesAgree=(!$multiplicityMismatch);allowed=@($Allowed);missing=$missing;unexpected=$unexpected;duplicateNames=$duplicates;
        acceptance='NOT_VERIFIED';controls='OFF';mutation='NONE'}
    return [pscustomobject]@{records=@($records);mismatch=($multiplicityMismatch -or $sqlCount -ne $Actual.Rows.Count -or $sqlCount -ne $processed.Count -or $missing -gt 0 -or $unexpected -gt 0 -or $duplicates -gt 0)}
}
$script:checkpoint='GUARDS';$admin=$null;$catalog=$null;$password=$null;$secret=$null
$report=$null;$exitCode=1;$failed=$false;$cleanupFailed=$false
try {
    $tenantGuid=[guid]::Empty;$subscriptionGuid=[guid]::Empty
    if(!$SecretSafeTelemetryReviewed -or !$AllApplicationControlsOffConfirmed -or
       ![guid]::TryParse($TenantId,[ref]$tenantGuid) -or $tenantGuid -eq [guid]::Empty -or
       ![guid]::TryParse($SubscriptionId,[ref]$subscriptionGuid) -or $subscriptionGuid -eq [guid]::Empty){throw 'STOP'}
    Set-VerificationStep RUNTIME
    Add-Type -AssemblyName System.Data
    Set-VerificationStep CONTRACT
    $contractPath=Join-Path $PSScriptRoot 'role-contract.json'
    if((Get-FileHash -LiteralPath $contractPath -Algorithm SHA256).Hash -cne 'A9E6C03C7093C5A411C0644A5733C3C3805CCA2D00C2BB42FA7EF06F7D2603BB'){throw 'STOP'}
    Set-VerificationStep AZURE_CONTEXT
    Import-Module Az.Accounts -ErrorAction Stop
    Import-Module Az.KeyVault -ErrorAction Stop
    $context=Get-AzContext -ErrorAction Stop
    if(!$context -or $context.Tenant.Id -ne $TenantId -or $context.Subscription.Id -ne $SubscriptionId){throw 'STOP'}
    Set-VerificationStep ADMIN_CONNECT
    $admin=New-Connection
    Open-DiagnosticAdmin $admin
    Set-VerificationStep ADMIN_TARGET
    $target=Read-Sql $admin @'
SELECT CONVERT(int,SERVERPROPERTY('EngineEdition')) Edition,DB_NAME() DbName,
 CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) ServerName,USER_NAME() UserName;
'@
    if($target.Rows.Count -ne 1 -or $target.Rows[0].Edition -ne 5 -or $target.Rows[0].DbName -cne 'Shale' -or
       $target.Rows[0].ServerName -notin @('shale','shale.database.windows.net') -or $target.Rows[0].UserName -cne 'dbo'){throw 'STOP'}
    Set-VerificationStep PRINCIPAL_CATALOG
    $membership=Read-Sql $admin @'
SELECT r.name Role FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
WHERE m.member_principal_id=DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier');
'@
    if($membership.Rows.Count -ne 1 -or $membership.Rows[0].Role -cne 'AiCaseDataCatalogVerifier'){throw 'STOP'}
    $reuse=Read-Sql $admin @'
SELECT COUNT(*) FindingCount FROM sys.database_principals p
WHERE p.principal_id<>DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier')
AND p.sid=(SELECT sid FROM sys.database_principals WHERE name=N'shale_prod_ai_catalog_verifier');
'@
    if($reuse.Rows.Count -ne 1 -or $reuse.Rows[0].FindingCount -ne 0){throw 'STOP'}
    Set-VerificationStep KEY_VAULT
    $secret=Get-AzKeyVaultSecret -VaultName 'shale-prod-secrets' -Name 'shale-ai-catalog-verifier-password' -ErrorAction Stop
    if(!$secret -or $secret.Enabled -ne $true -or !$secret.SecretValue -or $secret.SecretValue.Length -lt 1 -or
       ($secret.Expires -and $secret.Expires -le [datetime]::UtcNow) -or
       ($secret.NotBefore -and $secret.NotBefore -gt [datetime]::UtcNow)){throw 'STOP'}
    $password=$secret.SecretValue.Copy();$password.MakeReadOnly();$secret=$null
    Set-VerificationStep CONNECT
    $catalog=New-Connection
    $catalog.Credential=[System.Data.SqlClient.SqlCredential]::new('shale_prod_ai_catalog_verifier',$password)
    $catalog.Open()
    Confirm-DiagnosticIdentity $catalog $admin
    Set-VerificationStep OBJECT_INVENTORY
    $inventory=Read-ObjectInventory $admin
    if($ObjectOrdinal -gt $inventory.Rows.Count){throw 'STOP'}
    $object=$inventory.Rows[$ObjectOrdinal-1]
    $allowed=@('VIEW DEFINITION')
    if($object.SchemaName -ceq 'dbo' -and $object.ObjectName -cin $referenceTables){$allowed+='SELECT'}
    Set-VerificationStep OBJECT_PERMISSIONS
    $before=Read-ObjectGrants $admin $object
    $actual=Read-ObjectPermissions $catalog $object
    Set-VerificationStep HIERARCHY
    $builtin=Read-Sql $catalog @'
SELECT class_desc ClassName,permission_name PermissionName,covering_permission_name CoveringPermissionName,
 parent_class_desc ParentClassName,parent_covering_permission_name ParentCoveringPermissionName
FROM sys.fn_builtin_permissions(DEFAULT) WHERE class_desc IN(N'DATABASE',N'SCHEMA',N'OBJECT',N'COLUMN')
ORDER BY class_desc,permission_name;
'@
    Set-VerificationStep ROLE_CONTRACT
    $expected=Get-Content -LiteralPath $contractPath -Raw | ConvertFrom-Json
    $role=Read-Sql $admin @'
SELECT p.class Class,CASE WHEN p.class=0 THEN N'' WHEN p.class=1 THEN
 OBJECT_SCHEMA_NAME(p.major_id)+N'.'+OBJECT_NAME(p.major_id) ELSE N'UNEXPECTED' END Securable,
 p.permission_name Permission,p.state State,p.minor_id MinorId,p.grantor_principal_id Grantor
FROM sys.database_permissions p WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier');
'@
    $wanted=@($expected | Where-Object {$_.Role -ceq 'AiCaseDataCatalogVerifier'} | ForEach-Object {'{0}|{1}|{2}|{3}' -f $_.Class,$_.Securable,$_.Permission,$_.State})
    $found=@($role.Rows | ForEach-Object {'{0}|{1}|{2}|{3}' -f $_.Class,$_.Securable,$_.Permission,$_.State})
    $contractMatches=(Same-Set $wanted $found) -and @($role.Rows | Where-Object {$_.MinorId -ne 0 -or $_.Grantor -ne 1}).Count -eq 0
    Set-VerificationStep DRIFT
    $after=Read-ObjectGrants $admin $object
    $repeat=Read-ObjectPermissions $catalog $object
    $inventoryAfter=Read-ObjectInventory $admin
    Confirm-DiagnosticIdentity $catalog $admin
    Set-VerificationStep DRIFT
    if((Table-Signature $before) -cne (Table-Signature $after) -or
       (Table-Signature $actual) -cne (Table-Signature $repeat) -or
       (Table-Signature $inventory) -cne (Table-Signature $inventoryAfter)){throw 'STOP'}
    Set-VerificationStep REPORT
    $report=Get-ObjectReport $actual $object $ObjectOrdinal $allowed $builtin $before
    $report.records+= [pscustomobject]@{kind='ROLE_CONTRACT';matches=[bool]$contractMatches}
    $exitCode=0;if($report.mismatch -or !$contractMatches){$exitCode=2}
} catch {$failed=$true;$exitCode=1}
finally {
    foreach($connection in @($catalog,$admin)){
        if($null -ne $connection){
            $credential=$null
            try{$credential=$connection.Credential}catch{$cleanupFailed=$true}
            try{$connection.Dispose()}catch{$cleanupFailed=$true}
            if($null -ne $credential){try{$credential.Password.Dispose()}catch{$cleanupFailed=$true}}
            $credential=$null
        }
    }
    if($null -ne $password){try{$password.Dispose()}catch{$cleanupFailed=$true}}
    if($null -ne $secret){try{if($null -ne $secret.SecretValue){$secret.SecretValue.Dispose()}}catch{$cleanupFailed=$true}}
    $password=$null;$secret=$null;$catalog=$null;$admin=$null
}
if($failed -or $cleanupFailed){
    # Never format the caught ErrorRecord. Checkpoint is internal fixed vocabulary.
    $label=$script:checkpoint
    if($cleanupFailed -and !$failed){$label='CLEANUP'}
    [Console]::Out.WriteLine(('DIAGNOSTIC_STOP checkpoint={0} mutation=NONE controls=OFF' -f $label))
    exit 1
}
foreach($record in $report.records){[Console]::Out.WriteLine((ConvertTo-Json -InputObject $record -Compress -Depth 6))}
exit $exitCode
