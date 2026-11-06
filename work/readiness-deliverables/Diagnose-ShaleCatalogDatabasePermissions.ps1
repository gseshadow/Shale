#requires -Version 5.1
<# Default Review is offline. Diagnose reads permission metadata using one existing
catalog secret and actual contained SQL authentication. No SQL mutation or activation.
No transcripts, traces, raw error exports, or parameter/credential capture. #>
[CmdletBinding()]
param(
    [ValidateSet('Review','Diagnose')][string]$Mode='Review',
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
$dbAllowed=@('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION','VIEW DEFINITION')
if($Mode -ceq 'Review'){
    'OFFLINE REVIEW: catalog database permission diagnostic; no connections or secrets.'
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
    $b['Application Name']='ShaleCatalogPermissionDiagnostic';$b['ConnectRetryCount']=0
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
function Read-DiagnosticGrants($Admin){
    Read-Sql $Admin @'
SELECT CASE p.grantee_principal_id
 WHEN DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier') THEN N'USER'
 WHEN DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier') THEN N'CATALOG_ROLE'
 WHEN DATABASE_PRINCIPAL_ID(N'public') THEN N'PUBLIC' END SourceKind,
 p.permission_name PermissionName,p.state PermissionState,
 p.grantor_principal_id GrantorId
FROM sys.database_permissions p
WHERE p.class=0 AND p.grantee_principal_id IN(
 DATABASE_PRINCIPAL_ID(N'shale_prod_ai_catalog_verifier'),
 DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier'),DATABASE_PRINCIPAL_ID(N'public'))
ORDER BY SourceKind,PermissionName,PermissionState,GrantorId;
'@
}
function Get-DiagnosticReport($Actual,$Builtin,$Grants){
    # Only live engine built-in permission vocabulary can reach structured output.
    # It is metadata, never arbitrary catalog names, input strings or SQL exceptions.
    $vocabulary=[System.Collections.Generic.Dictionary[string,bool]]::new([StringComparer]::Ordinal)
    $cover=[System.Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    if($Builtin.Rows.Count -lt 1){throw 'STOP'}
    foreach($r in $Builtin.Rows){
        $name=[string]$r.PermissionName;$parent=[string]$r.CoveringPermissionName
        if($name -cnotmatch '^[A-Z][A-Z0-9 ]{0,127}$' -or $vocabulary.ContainsKey($name)){throw 'STOP'}
        $vocabulary.Add($name,$true);$cover.Add($name,$parent)
    }
    foreach($name in $cover.Keys){
        if($cover[$name] -and !$vocabulary.ContainsKey($cover[$name])){throw 'STOP'}
    }
    foreach($g in $Grants.Rows){
        if(!$vocabulary.ContainsKey([string]$g.PermissionName) -or
           $g.SourceKind -cnotin @('USER','CATALOG_ROLE','PUBLIC') -or
           $g.PermissionState -cnotin @('G','W','D') -or $g.GrantorId -is [DBNull]){throw 'STOP'}
    }
    $counts=New-Object 'System.Collections.Generic.Dictionary[string,int]' ([StringComparer]::Ordinal)
    foreach($r in $Actual.Rows){
        $name=[string]$r.Permission
        if(!$vocabulary.ContainsKey($name)){throw 'STOP'}
        if(!$counts.ContainsKey($name)){$counts.Add($name,0)}
        $counts[$name]++
    }
    $records=@();$unexpected=0;$duplicates=0;$absent=0
    foreach($name in @($counts.Keys | Sort-Object)){
        $duplicates+=$counts[$name]-1
        $kind='APPROVED_PRESENT'
        if($name -cnotin $dbAllowed){$kind='UNEXPECTED';$unexpected+=$counts[$name]}
        $records+=[pscustomobject]@{kind=$kind;permission=$name;occurrences=$counts[$name]}
        if($kind -ceq 'UNEXPECTED'){
            $path=@();$current=$name;$grantPaths=0
            while($current){
                if($current -cin $path -or $path.Count -ge 128){throw 'STOP'}
                $path+= $current
                foreach($g in @($Grants.Rows | Where-Object {$_.PermissionName -ceq $current})){
                    $route='DIRECT';if($g.SourceKind -cne 'USER'){$route='INHERITED'}
                    if($path.Count -gt 1){$route+='_HIERARCHY'}
                    $state='DENY';if($g.PermissionState -ceq 'G'){$state='GRANT'}
                    if($g.PermissionState -ceq 'W'){$state='GRANT_WITH_GRANT_OPTION'}
                    if($state -cne 'DENY'){$grantPaths++}
                    $records+=[pscustomobject]@{kind='PROVENANCE';permission=$name;source=$g.SourceKind;
                        route=$route;state=$state;grantorIsDbo=([int]$g.GrantorId -eq 1);
                        path=@($path)}
                }
                $current=$cover[$current]
            }
            $records+=[pscustomobject]@{kind='HIERARCHY';permission=$name;path=@($path)}
            if(!$grantPaths){$records+=[pscustomobject]@{kind='UNRESOLVED';permission=$name}}
        }
    }
    foreach($name in $dbAllowed){
        if(!$counts.ContainsKey($name)){$absent++;$records+=[pscustomobject]@{kind='ALLOWLIST_ABSENT';permission=$name;occurrences=0}}
    }
    $records+=[pscustomobject]@{kind='SUMMARY';identity='CATALOG';actual=$Actual.Rows.Count;
        allowed=$dbAllowed.Count;unexpected=$unexpected;duplicates=$duplicates;absent=$absent;
        acceptance='NOT_VERIFIED';controls='OFF'}
    return [pscustomobject]@{records=@($records);mismatch=($unexpected -gt 0 -or $absent -gt 0 -or $duplicates -gt 0)}
}
function Grant-Signature($Table){
    return (@($Table.Rows | ForEach-Object {'{0}|{1}|{2}|{3}' -f $_.SourceKind,$_.PermissionName,$_.PermissionState,$_.GrantorId} | Sort-Object) -join "`n")
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
    Set-VerificationStep DATABASE_PERMISSIONS
    $before=Read-DiagnosticGrants $admin
    $actual=Read-Sql $catalog 'SELECT permission_name Permission FROM sys.fn_my_permissions(NULL,N''DATABASE'');'
    Set-VerificationStep HIERARCHY
    $builtin=Read-Sql $catalog @'
SELECT permission_name PermissionName,covering_permission_name CoveringPermissionName
FROM sys.fn_builtin_permissions(DEFAULT) WHERE class_desc=N'DATABASE' ORDER BY permission_name;
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
    $after=Read-DiagnosticGrants $admin
    $repeat=Read-Sql $catalog 'SELECT permission_name Permission FROM sys.fn_my_permissions(NULL,N''DATABASE'');'
    Confirm-DiagnosticIdentity $catalog $admin
    Set-VerificationStep DRIFT
    if((Grant-Signature $before) -cne (Grant-Signature $after) -or !(Same-Set @($actual.Rows.Permission) @($repeat.Rows.Permission))){throw 'STOP'}
    Set-VerificationStep REPORT
    $report=Get-DiagnosticReport $actual $builtin $before
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
