#requires -Version 5.1
<#
Offline preparation only. Default Review never loads Azure modules or connects.
Preflight = read-only dbo catalog checks, no Key Vault password retrieval.
SQL administrator authentication prompts securely; no credential input is accepted.
Verify = preflight plus actual contained logins, no SQL mutation.
Provision = explicit three-user authorization, one transaction, then actual logins.
No installer, role repair, password reset, capability change, or cleanup is present.
Requires compatible Az.Accounts/Az.KeyVault and the built-in System.Data.SqlClient.
Windows PowerShell 5.1 requires .NET Framework 4.7.2+; PowerShell 7.4+ is also supported.
Do not enable transcripts, debug tracing, parameter capture or diagnostic dumps.
#>
[CmdletBinding()]
param(
    [ValidateSet('Review','Preflight','Provision','Verify')][string]$Mode='Review',
    [ValidateSet('Entra','Sql')][string]$AdminAuthentication='Entra',
    [string]$TenantId,
    [string]$SubscriptionId,
    [string]$AuthorizationReference,
    [string[]]$AuthorizedUsers=@(),
    [switch]$SecretSafeTelemetryReviewed,
    [switch]$AllApplicationControlsOffConfirmed,
    [string]$ContractPath=(Join-Path $PSScriptRoot 'role-contract.json')
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$VerbosePreference='SilentlyContinue'
$DebugPreference='SilentlyContinue'
$InformationPreference='SilentlyContinue'
$ProgressPreference='SilentlyContinue'
$server='shale.database.windows.net'
$database='Shale'
$vault='shale-prod-secrets'
$identities=@(
    @{Name='shale_prod_ai_case_runtime';Role='AiCaseDataRuntime';Secret='shale-ai-case-runtime-password'},
    @{Name='shale_prod_ai_catalog_verifier';Role='AiCaseDataCatalogVerifier';Secret='shale-ai-catalog-verifier-password'},
    @{Name='shale_prod_ai_oauth_bootstrap';Role='AiIntegrationBootstrap';Secret='shale-ai-oauth-bootstrap-password'}
)
$runtimeExec=@('AiDataAuthenticateV1','AiDataSearchCasesV1','AiDataGetCaseOverviewV1','AiDataRecordCaseFailureV1','AiDataFinishCaseQuotaV1','AiDataAuthorizeCaseHandoffV1')
$bootstrapExec=@('ResolveAiClientV1','ResolveAiCredentialV1','ResolveAiOAuthCodeV1','ResolveAiRefreshV1','ResolveAiRevocationV1','ResolveAiAccessV1')
$referenceTables=@('AiCaseDataDeployment','AiDataUnicodeRange','AiDataUnicodeLower','AiDataWordTransition')
# Exact adjacent contract digest is inserted by the packet builder. Do not regenerate from live SQL.
$contractDigest='A9E6C03C7093C5A411C0644A5733C3C3805CCA2D00C2BB42FA7EF06F7D2603BB'
if ((Get-FileHash -LiteralPath $ContractPath -Algorithm SHA256).Hash -cne $contractDigest) {
    throw 'STOP: reviewed role-contract.json checksum differs.'
}
# Assign directly: Windows PowerShell 5.1 emits the JSON array as one pipeline item.
# An outer @() would wrap it in a second array instead of exposing the 324 edges.
$expected=Get-Content -LiteralPath $ContractPath -Raw | ConvertFrom-Json
if ($Mode -eq 'Review') {
    'OFFLINE REVIEW ONLY: three contained SQL users; no connections or secret retrieval.'
    'Bootstrap: dedicated password JDBC datasource; six resolver EXECUTEs; no model conversion inferred from V2.'
    $identities | ForEach-Object { '{0} -> {1} ({2})' -f $_.Name,$_.Role,$_.Secret }
    return
}
if (!$TenantId -or !$SubscriptionId -or !$AllApplicationControlsOffConfirmed) {
    throw 'STOP: explicit tenant/subscription and all-controls-OFF confirmation required.'
}
if ($Mode -in @('Provision','Verify') -and !$SecretSafeTelemetryReviewed) {
    throw 'STOP: secret-safe client/server telemetry review required before passwords are fetched.'
}
if ($AdminAuthentication -eq 'Sql' -and !$SecretSafeTelemetryReviewed) {
    throw 'STOP: secret-safe client/server telemetry review required before SQL administrator credential prompt.'
}
if ($Mode -eq 'Provision' -and ([string]::IsNullOrWhiteSpace($AuthorizationReference) -or
    @($AuthorizedUsers).Count -ne 3 -or
    ((@($identities.Name | Sort-Object) -join '|') -cne (@($AuthorizedUsers | Sort-Object) -join '|')))) {
    throw 'STOP: separate authorization reference and exact three approved usernames required.'
}
Add-Type -AssemblyName System.Data
# Never print exceptions, command objects, credentials, tokens, parameter values, or Azure results.
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
function Run-Sql($Connection,[string]$Sql,[hashtable]$Parameters=@{},$Transaction=$null) {
    $cmd=$Connection.CreateCommand();$cmd.CommandText=$Sql;$cmd.CommandTimeout=30
    if($Transaction){$cmd.Transaction=$Transaction}
    try {
        foreach($key in $Parameters.Keys){
            $p=$cmd.Parameters.Add('@'+$key,[System.Data.SqlDbType]::NVarChar,-1);$p.Value=$Parameters[$key]
        }
        [void]$cmd.ExecuteNonQuery()
    } finally {$cmd.Parameters.Clear();$cmd.Dispose()}
}
function New-Connection {
    $b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $b['Data Source']='tcp:shale.database.windows.net,1433';$b['Initial Catalog']='Shale'
    $b['Encrypt']='True';$b['TrustServerCertificate']=$false;$b['Pooling']=$false
    $b['Connect Timeout']=15;$b['Persist Security Info']=$false
    $b['Application Name']='ShaleIdentityProvisioning';$b['ConnectRetryCount']=0
    return [System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
}
function Convert-InMemory([securestring]$Value) {
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}
    finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
}
function Set-AdminAuthentication($Connection,[string]$Authentication,[string]$Tenant) {
    if ($Authentication -eq 'Sql') {
        $prompt=$null;$password=$null;$assigned=$false
        try {
            $prompt=Get-Credential -UserName 'brian' -Message 'Approved Shale SQL administrator (SQL Server Authentication)' -ErrorAction Stop
            if (!$prompt -or $prompt.UserName -cne 'brian' -or !$prompt.Password -or $prompt.Password.Length -lt 1) {
                throw 'STOP: approved SQL administrator credential required.'
            }
            # SqlCredential keeps the password out of the connection string and managed plaintext.
            $password=$prompt.Password.Copy();$password.MakeReadOnly()
            $Connection.Credential=[System.Data.SqlClient.SqlCredential]::new('brian',$password)
            $assigned=$true
        } finally {
            if ($prompt) {$prompt.Password.Dispose()};$prompt=$null
            if ($password -and !$assigned) {$password.Dispose()};$password=$null
        }
    } elseif ($Authentication -eq 'Entra') {
        $token=$null
        try {
            $token=Get-AzAccessToken -ResourceUrl 'https://database.windows.net/' -TenantId $Tenant -ErrorAction Stop
            if ($token.Token -isnot [securestring]) {throw 'STOP: current Az.Accounts with SecureString access tokens required.'}
            $Connection.AccessToken=Convert-InMemory $token.Token
        } finally {
            if ($token -and $token.Token -is [securestring]) {$token.Token.Dispose()};$token=$null
        }
    } else {throw 'STOP: unsupported administrator authentication.'}
}
function Assert-Zero($Connection,[string]$Sql,$Transaction=$null) {
    $t=Read-Sql $Connection $Sql @{} $Transaction
    if($t.Rows.Count -ne 1 -or $t.Rows[0][0] -is [DBNull] -or [long]$t.Rows[0][0] -ne 0){throw 'STOP: security finding or incomplete evidence.'}
}
function Same-Set($Left,$Right) {
    return ((@($Left | Sort-Object) -join "`n") -ceq (@($Right | Sort-Object) -join "`n"))
}
function Edge-Key($Row) {
    return '{0}|{1}|{2}|{3}|{4}' -f $Row.Role,$Row.Class,$Row.Securable,$Row.Permission,$Row.State
}
function Assert-Catalog($Connection,$Transaction=$null) {
    $t=Read-Sql $Connection @'
SELECT CONVERT(int,SERVERPROPERTY('EngineEdition')) Edition,DB_NAME() DbName,
 CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) ServerName,USER_NAME() UserName;
'@ @{} $Transaction
    if($t.Rows.Count -ne 1 -or $t.Rows[0].Edition -ne 5 -or $t.Rows[0].DbName -cne 'Shale' -or
       $t.Rows[0].ServerName -notin @('shale','shale.database.windows.net') -or $t.Rows[0].UserName -cne 'dbo'){
        throw 'STOP: wrong engine/server/database or missing complete dbo visibility.'
    }
    # Completed migration acceptance is NOT rerun. Only check roles needed for identity provisioning.
    $actual=Read-Sql $Connection @'
SELECT r.name Role,p.class Class,
 CASE WHEN p.class=0 THEN N'' WHEN p.class=1 THEN OBJECT_SCHEMA_NAME(p.major_id)+N'.'+OBJECT_NAME(p.major_id) ELSE N'UNEXPECTED' END Securable,
 p.permission_name Permission,p.state State,p.minor_id MinorId,p.grantor_principal_id Grantor
FROM sys.database_permissions p JOIN sys.database_principals r ON r.principal_id=p.grantee_principal_id
WHERE r.name IN(N'AiCaseDataRuntime',N'AiCaseDataCatalogVerifier',N'AiIntegrationBootstrap');
'@ @{} $Transaction
    $a=@($actual.Rows | ForEach-Object {Edge-Key $_} | Sort-Object)
    $e=@($expected | ForEach-Object {Edge-Key $_} | Sort-Object)
    if(!(Same-Set $e $a) -or @($actual.Rows | Where-Object {$_.MinorId -ne 0 -or $_.Grantor -ne 1}).Count){
        throw 'STOP: installed role edges differ from reviewed contract; no grant repair is authorized.'
    }
    Assert-Zero $Connection @'
SELECT CASE WHEN (SELECT COUNT(*) FROM sys.database_principals WHERE name IN
 (N'AiCaseDataRuntime',N'AiCaseDataCatalogVerifier',N'AiIntegrationBootstrap')
 AND type='R' AND is_fixed_role=0 AND owning_principal_id=1)=3 THEN 0 ELSE 1 END;
'@ $Transaction
    Assert-Zero $Connection @'
SELECT COUNT(*) FROM sys.database_role_members m
WHERE m.member_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiCaseDataRuntime'),DATABASE_PRINCIPAL_ID(N'AiCaseDataCatalogVerifier'),DATABASE_PRINCIPAL_ID(N'AiIntegrationBootstrap'));
'@ $Transaction
    # Same public-authority predicate as the accepted Azure attestation implementation.
    Assert-Zero $Connection @'
SELECT COUNT(*) FROM sys.database_permissions p LEFT JOIN sys.all_objects o ON p.class=1 AND o.object_id=p.major_id
WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID('public')
 AND (p.state='D' AND p.permission_name LIKE '%DEFINITION%'
 OR p.state IN('G','W') AND (p.state='W' OR NOT(
 p.class=0 AND p.permission_name IN('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')
 OR p.class=1 AND p.permission_name IN('SELECT','EXECUTE','VIEW DEFINITION')
 AND OBJECT_SCHEMA_NAME(p.major_id) IN('sys','INFORMATION_SCHEMA')
 AND (COALESCE(o.is_ms_shipped,0)=1 OR p.major_id<0 AND p.permission_name='SELECT')
 OR p.class IN(1,3) AND p.permission_name='VIEW DEFINITION')));
'@ $Transaction
    Assert-Zero $Connection @'
SELECT CASE WHEN (SELECT COUNT(*) FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)=1
 AND NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled<>0)
 AND NOT EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE Enabled<>0) THEN 0 ELSE 1 END;
'@ $Transaction
    foreach($i in $identities) {
        $t=Read-Sql $Connection @'
SELECT principal_id Id,name Name,type Type,authentication_type Auth,CONVERT(varchar(170),sid,2) Sid
FROM sys.database_principals WHERE name=@name;
'@ @{name=$i.Name} $Transaction
        if($t.Rows.Count -gt 1){throw 'STOP: ambiguous identity.'}
        if($t.Rows.Count -eq 0){continue}
        $p=$t.Rows[0]
        if($p.Id -le 4 -or $p.Name -cne $i.Name -or $p.Type -cne 'S' -or $p.Auth -ne 2 -or !$p.Sid){throw 'STOP: incompatible existing principal.'}
        $membership=Read-Sql $Connection @'
SELECT r.name Role FROM sys.database_role_members m JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
WHERE m.member_principal_id=DATABASE_PRINCIPAL_ID(@name);
'@ @{name=$i.Name} $Transaction
        if($membership.Rows.Count -ne 1 -or $membership.Rows[0].Role -cne $i.Role){throw 'STOP: existing identity has missing/unexpected membership; no automatic repair.'}
        $findings=Read-Sql $Connection @'
DECLARE @id int=DATABASE_PRINCIPAL_ID(@name);
SELECT (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=@id AND NOT(class=0 AND permission_name='CONNECT' AND state='G'))
 +(SELECT COUNT(*) FROM sys.database_permissions WHERE grantor_principal_id=@id)
 +(SELECT COUNT(*) FROM sys.database_principals WHERE sid=(SELECT sid FROM sys.database_principals WHERE principal_id=@id) AND principal_id<>@id) FindingCount;
'@ @{name=$i.Name} $Transaction
        if([long]$findings.Rows[0][0] -ne 0){throw 'STOP: direct authority, grant provenance, or SID reuse.'}
    }
    # Preserve known ordinary identities and require meaningful SID evidence for both.
    Assert-Zero $Connection @'
SELECT CASE WHEN (SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'shale_app',N'shale_runtime') AND DATALENGTH(sid)>0)=2 THEN 0 ELSE 1 END;
'@ $Transaction
    # Token-wide ownership (user/public/role), not just direct user ownership.
    foreach($i in $identities){
        $f=Read-Sql $Connection @'
DECLARE @ids table(Id int);INSERT @ids SELECT principal_id FROM sys.database_principals WHERE name IN(@name,@role,N'public');
SELECT (SELECT COUNT(*) FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.objects WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.types WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.assemblies WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.xml_schema_collections WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.certificates WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.asymmetric_keys WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.symmetric_keys WHERE principal_id IN(SELECT Id FROM @ids))
 +(SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT Id FROM @ids) AND state='W') FindingCount;
'@ @{name=$i.Name;role=$i.Role} $Transaction
        if([long]$f.Rows[0][0] -ne 0){throw 'STOP: ownership or grant-option authority.'}
    }
}
# Whole unrelated identity/grant snapshot: excludes only approved user rows, their CONNECT,
# and incoming role membership. No passwords, password hashes, application rows or secret metadata.
function Security-Snapshot($Connection,$Transaction=$null) {
    $t=Read-Sql $Connection @'
DECLARE @a table(Id int);INSERT @a SELECT principal_id FROM sys.database_principals WHERE name IN
(N'shale_prod_ai_case_runtime',N'shale_prod_ai_catalog_verifier',N'shale_prod_ai_oauth_bootstrap');
SELECT
 (SELECT principal_id,name,type,authentication_type,CONVERT(varchar(170),sid,2) sid,default_schema_name,owning_principal_id,is_fixed_role FROM sys.database_principals WHERE principal_id NOT IN(SELECT Id FROM @a) ORDER BY principal_id FOR JSON PATH,INCLUDE_NULL_VALUES) Principals,
 (SELECT * FROM sys.database_role_members WHERE member_principal_id NOT IN(SELECT Id FROM @a) ORDER BY role_principal_id,member_principal_id FOR JSON PATH,INCLUDE_NULL_VALUES) Memberships,
 (SELECT * FROM sys.database_permissions WHERE grantee_principal_id NOT IN(SELECT Id FROM @a) ORDER BY class,major_id,minor_id,grantee_principal_id,grantor_principal_id,type FOR JSON PATH,INCLUDE_NULL_VALUES) Permissions;
'@ @{} $Transaction
    return ($t.Rows[0].Principals+'|'+$t.Rows[0].Memberships+'|'+$t.Rows[0].Permissions)
}
function Permission-List($Connection,[string]$Name,[string]$Scope,[string[]]$Allowed) {
    $securable=$Name;if($Scope -ceq 'DATABASE'){$securable=[DBNull]::Value}
    try{$t=Read-Sql $Connection 'SELECT permission_name Permission FROM sys.fn_my_permissions(@n,@scope);' @{n=$securable;scope=$Scope}}
    catch {
        # Accepted attestation permits invisible objects only when their allowlist is empty.
        $ex=$_.Exception
        while($ex.InnerException){$ex=$ex.InnerException}
        if($Scope -eq 'OBJECT' -and $Allowed.Count -eq 0 -and $ex -is [System.Data.SqlClient.SqlException] -and $ex.Number -eq 15151){return @()}
        throw 'STOP: effective permission evidence unavailable.'
    }
    $actual=@($t.Rows | ForEach-Object {$_.Permission})
    foreach($p in $actual){if($p -cnotin $Allowed){throw 'STOP: unexpected effective permission.'}}
    return $actual
}
function Verify-Login($Identity,[securestring]$Password,$Admin) {
    $c=New-Connection
    try {
        $c.Credential=[System.Data.SqlClient.SqlCredential]::new($Identity.Name,$Password)
        $c.Open()
        $proof=Read-Sql $c @'
SELECT p.principal_id Id,p.name Name,p.type Type,p.authentication_type Auth,CONVERT(varchar(170),p.sid,2) Sid,
 ORIGINAL_LOGIN() OriginalName,DB_NAME() DbName,CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) ServerName,
 CONVERT(int,SERVERPROPERTY('EngineEdition')) Edition,
 (SELECT COUNT(*) FROM sys.dm_exec_sessions s WHERE s.session_id=@@SPID AND s.authenticating_database_id=DB_ID() AND s.original_security_id=p.sid) OwnSession
FROM sys.database_principals p WHERE p.principal_id=USER_ID();
'@
        if($proof.Rows.Count -ne 1){throw 'STOP: identity evidence missing.'};$p=$proof.Rows[0]
        $independent=Read-Sql $Admin 'SELECT principal_id Id,type Type,authentication_type Auth,CONVERT(varchar(170),sid,2) Sid FROM sys.database_principals WHERE name=@name;' @{name=$Identity.Name}
        if($independent.Rows.Count -ne 1 -or $p.Id -le 4 -or $p.Name -cne $Identity.Name -or $p.OriginalName -cne $Identity.Name -or
           $p.Type -cne 'S' -or $p.Auth -ne 2 -or $p.OwnSession -ne 1 -or $p.Edition -ne 5 -or $p.DbName -cne $database -or
           $p.ServerName -notin @('shale','shale.database.windows.net') -or $p.Sid -cne $independent.Rows[0].Sid -or
           $p.Id -ne $independent.Rows[0].Id -or $p.Type -cne $independent.Rows[0].Type -or $p.Auth -ne $independent.Rows[0].Auth){throw 'STOP: actual contained authentication proof failed.'}
        $tokens=Read-Sql $c 'SELECT name Name,type Type FROM sys.user_token;'
        if(!(Same-Set @($Identity.Name,'public',$Identity.Role) @($tokens.Rows.Name)) -or
           @($tokens.Rows | Where-Object {$_.Name -ceq $Identity.Name -and $_.Type -cne 'SQL USER'}).Count){throw 'STOP: unexpected effective user token.'}
        $dbAllowed=@('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')
        if($Identity.Role -ceq 'AiCaseDataCatalogVerifier'){$dbAllowed+= 'VIEW DEFINITION'}
        $dbPerms=@(Permission-List $c '' 'DATABASE' $dbAllowed)
        if('CONNECT' -cnotin $dbPerms -or ($Identity.Role -ceq 'AiCaseDataCatalogVerifier' -and 'VIEW DEFINITION' -cnotin $dbPerms)){throw 'STOP: required database permission missing.'}
        if($Identity.Role -ceq 'AiCaseDataCatalogVerifier'){
            $systemPerms=@(Permission-List $c 'sys.sql_expression_dependencies' 'OBJECT' @('SELECT','VIEW DEFINITION'))
            if('SELECT' -cnotin $systemPerms){throw 'STOP: dependency catalog SELECT missing.'}
        }
        # Inventory through dbo, then probe with the actual restricted login; no protected data reads.
        $objects=Read-Sql $Admin @'
SELECT s.name SchemaName,o.name ObjectName,QUOTENAME(s.name)+N'.'+QUOTENAME(o.name) QualifiedName,o.object_id Id
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC') ORDER BY s.name,o.name;
'@
        $execFound=@()
        foreach($o in $objects.Rows){
            $allowed=@()
            if($Identity.Role -ceq 'AiCaseDataRuntime' -and $o.SchemaName -ceq 'dbo' -and $o.ObjectName -cin $runtimeExec){$allowed=@('EXECUTE')}
            if($Identity.Role -ceq 'AiIntegrationBootstrap' -and $o.SchemaName -ceq 'dbo' -and $o.ObjectName -cin $bootstrapExec){$allowed=@('EXECUTE')}
            if($Identity.Role -ceq 'AiCaseDataCatalogVerifier'){
                $allowed=@('VIEW DEFINITION');if($o.SchemaName -ceq 'dbo' -and $o.ObjectName -cin $referenceTables){$allowed+='SELECT'}
            }
            $perms=@(Permission-List $c $o.QualifiedName 'OBJECT' $allowed)
            if('EXECUTE' -cin $perms){$execFound+=$o.ObjectName}
            if(!(Same-Set $allowed $perms)){throw 'STOP: required object authority missing.'}
            $columns=Read-Sql $Admin 'SELECT name Name FROM sys.columns WHERE object_id=CONVERT(int,@id);' @{id=[string]$o.Id}
            foreach($col in $columns.Rows){
                $check=Read-Sql $c "SELECT HAS_PERMS_BY_NAME(@o,'OBJECT','SELECT',@c,'COLUMN') CanRead,HAS_PERMS_BY_NAME(@o,'OBJECT','UPDATE',@c,'COLUMN') CanUpdate;" @{o=$o.QualifiedName;c=$col.Name}
                $wantRead=$Identity.Role -ceq 'AiCaseDataCatalogVerifier' -and $o.SchemaName -ceq 'dbo' -and $o.ObjectName -cin $referenceTables
                if($check.Rows.Count -ne 1 -or $check.Rows[0].CanUpdate -eq 1 -or
                   (!$wantRead -and $check.Rows[0].CanRead -eq 1) -or ($wantRead -and $check.Rows[0].CanRead -ne 1)){throw 'STOP: unexpected/missing column authority.'}
                # NULL for invisible objects is not used as a standalone proof. Complete dbo grant
                # graph and the empty object allowlist above jointly exclude column grant exceptions.
            }
        }
        $wanted=@();if($Identity.Role -ceq 'AiCaseDataRuntime'){$wanted=$runtimeExec}
        if($Identity.Role -ceq 'AiIntegrationBootstrap'){$wanted=$bootstrapExec}
        if(!(Same-Set $wanted $execFound)){throw 'STOP: exact EXECUTE set mismatch.'}
        'Verified contained login and permission closure: '+$Identity.Name
    } finally {$c.Dispose()}
}
$admin=$null;$transaction=$null;$commitStarted=$false;$committed=$false;$stage='AZURE_CONTEXT'
$passwords=@{}
try {
    Import-Module Az.Accounts
    Import-Module Az.KeyVault
    $azContext=Get-AzContext
    if (!$azContext -or $azContext.Tenant.Id -ne $TenantId -or $azContext.Subscription.Id -ne $SubscriptionId) {
        throw 'STOP: Azure context mismatch; authenticate/select the approved context outside this script.'
    }
    $stage='ADMIN_CONNECT'
    $admin=New-Connection
    Set-AdminAuthentication $admin $AdminAuthentication $TenantId
    $admin.Open()
    $stage='PREFLIGHT';Assert-Catalog $admin
    $baseline=Security-Snapshot $admin
    $missing=@()
    foreach($i in $identities){
        $t=Read-Sql $admin 'SELECT COUNT(*) FindingCount FROM sys.database_principals WHERE name=@name;' @{name=$i.Name}
        if([int]$t.Rows[0][0] -eq 0){$missing+=$i}
    }
    if($Mode -eq 'Preflight'){
        foreach($i in $identities){'{0}: {1}' -f $i.Name,$(if($i -in $missing){'ABSENT; creation requires separate authorization'}else{'EXISTS; structural checks passed'})}
        'Read-only preflight passed. No Key Vault secrets fetched; no SQL mutation.';return
    }
    if($Mode -eq 'Verify' -and $missing.Count){throw 'STOP: Verify requires all three users to exist.'}
    $stage='KEY_VAULT'
    foreach($i in $identities){
        # No -AsPlainText, az CLI, console output, file, SQLCMD or environment-variable transport.
        $s=Get-AzKeyVaultSecret -VaultName $vault -Name $i.Secret -ErrorAction Stop
        if(!$s -or $s.Enabled -ne $true -or !$s.SecretValue -or $s.SecretValue.Length -lt 1 -or
           ($s.Expires -and $s.Expires -le [datetime]::UtcNow) -or ($s.NotBefore -and $s.NotBefore -gt [datetime]::UtcNow)){throw 'STOP: secret unavailable/disabled/outside validity.'}
        $passwords[$i.Name]=$s.SecretValue.Copy();$passwords[$i.Name].MakeReadOnly();$s=$null
    }
    # Detect identical secrets in memory without persisting hashes or values.
    for($a=0;$a -lt 3;$a++){for($b=$a+1;$b -lt 3;$b++){
        $left=$null;$right=$null
        try{$left=Convert-InMemory $passwords[$identities[$a].Name];$right=Convert-InMemory $passwords[$identities[$b].Name]
            if([string]::Equals($left,$right,[StringComparison]::Ordinal)){throw 'STOP: independent passwords required.'}
        } finally {$left=$null;$right=$null}
    }}
    $stage='EXISTING_LOGIN_VERIFY'
    foreach($i in $identities){if($i -notin $missing){Verify-Login $i $passwords[$i.Name] $admin}}
    if($Mode -eq 'Provision' -and $missing.Count){
        $stage='TRANSACTION_PREFLIGHT'
        $transaction=$admin.BeginTransaction([System.Data.IsolationLevel]::Serializable)
        Run-Sql $admin @'
SET XACT_ABORT ON;SET LOCK_TIMEOUT 15000;
DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'Shale.AI.IdentityProvisioning.V2',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=15000;
IF @r<0 THROW 59800,'Provisioning lock unavailable.',1;
'@ @{} $transaction
        Assert-Catalog $admin $transaction
        if((Security-Snapshot $admin $transaction) -cne $baseline){throw 'STOP: security catalog changed after preflight.'}
        foreach($i in $missing){
            $stage='CREATE_'+$i.Name;$plain=$null
            try {
                $plain=Convert-InMemory $passwords[$i.Name]
                # Password is a bound TDS RPC parameter. CREATE USER syntax requires a literal;
                # escape it inside SQL memory only. No secret is interpolated into client CommandText.
                Run-Sql $admin @'
IF DATABASE_PRINCIPAL_ID(@name) IS NOT NULL THROW 59801,'User appeared; stop.',1;
IF @name NOT IN(N'shale_prod_ai_case_runtime',N'shale_prod_ai_catalog_verifier',N'shale_prod_ai_oauth_bootstrap') THROW 59802,'Unapproved user.',1;
IF NOT((@name=N'shale_prod_ai_case_runtime' AND @role=N'AiCaseDataRuntime') OR
 (@name=N'shale_prod_ai_catalog_verifier' AND @role=N'AiCaseDataCatalogVerifier') OR
 (@name=N'shale_prod_ai_oauth_bootstrap' AND @role=N'AiIntegrationBootstrap')) THROW 59803,'Unapproved mapping.',1;
DECLARE @ddl nvarchar(max)=N'CREATE USER '+QUOTENAME(@name)+N' WITH PASSWORD=N'''+REPLACE(@password,N'''',N'''''')+N''';';
EXEC sys.sp_executesql @ddl;SET @ddl=NULL;
SET @ddl=N'ALTER ROLE '+QUOTENAME(@role)+N' ADD MEMBER '+QUOTENAME(@name)+N';';
EXEC sys.sp_executesql @ddl;SET @ddl=NULL;
'@ @{name=$i.Name;role=$i.Role;password=$plain} $transaction
            } finally {$plain=$null}
        }
        $stage='BEFORE_COMMIT';Assert-Catalog $admin $transaction
        if((Security-Snapshot $admin $transaction) -cne $baseline){throw 'STOP: unrelated identity/grant preservation failed.'}
        $stage='COMMIT';$commitStarted=$true;$transaction.Commit();$committed=$true
        $transaction.Dispose();$transaction=$null
    }
    $stage='POST_INSTALL_VERIFY';Assert-Catalog $admin
    if((Security-Snapshot $admin) -cne $baseline){throw 'STOP: unrelated identity/grant drift.'}
    foreach($i in $identities){Verify-Login $i $passwords[$i.Name] $admin}
    Assert-Catalog $admin
    'PASS: three identities verified. AI/MCP/OAuth activation remains unauthorized; keep every control OFF.'
} catch {
    $rollback='No SQL mutation started.'
    if($transaction -and !$commitStarted){
        try{$transaction.Rollback();$rollback='Uncommitted provisioning transaction rolled back.'}
        catch{$rollback='Rollback unconfirmed; reconnect read-only and reconcile before any retry.'}
    } elseif($commitStarted -and !$committed){$rollback='COMMIT OUTCOME UNKNOWN. Reconnect read-only; do not retry, rotate, drop, or revoke.'}
    elseif($committed){$rollback='Commit completed; verification failed. Users remain. No destructive cleanup. Keep controls OFF.'}
    # Only a bounded stage label and recovery status are emitted; raw exception records are suppressed.
    Write-Error -Message ('STOP at '+$stage+'. '+$rollback+' See execution checklist.') -ErrorAction Continue
    exit 1
} finally {
    if($transaction){$transaction.Dispose()}
    if($admin){
        $adminPassword=$null
        if($admin.Credential){$adminPassword=$admin.Credential.Password}
        $admin.Dispose()
        if($adminPassword){$adminPassword.Dispose()};$adminPassword=$null
    }
    foreach($v in $passwords.Values){$v.Dispose()};$passwords.Clear()
    # Managed strings cannot be reliably zeroed; terminate the dedicated process after execution.
    $Error.Clear()
}
