# Dot-sourcing defines seams only; no Azure or SQL execution.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'CleanSlate.ps1')
$script:LiveAppId="$script:CleanResource/Microsoft.Web/sites/shale-mcp-acceptance"
$script:LiveOrigin='https://shale-mcp-acceptance.azurewebsites.net'
$script:LiveClientWatchdogMinutes=180
function ConvertTo-LiveEnvironmentName([string]$Property) {
    $name=$Property.Replace('.','_').Replace('-','').ToUpperInvariant()
    if ($name -cnotmatch '^[A-Z_][A-Z0-9_]*$') { throw 'INVALID_AZURE_SETTING_NAME' }
    return $name
}
function ConvertTo-LiveEnvironmentSettings($Properties) {
    $settings=@{}
    foreach ($property in $Properties.Keys) {
        $name=ConvertTo-LiveEnvironmentName $property
        if ($settings.ContainsKey($name)) { throw 'AZURE_SETTING_NAME_COLLISION' }
        $settings[$name]=$Properties[$property]
    }
    return $settings
}
$script:LiveControlProperties=@('shale.ai.enabled','shale.ai.mcp.enabled','shale.ai.mcp.case-data-composition-enabled',
 'shale.ai.mcp.search-cases-enabled','shale.ai.mcp.get-case-overview-enabled','shale.ai.oauth.composition-enabled',
 'shale.ai.oauth.authorization-enabled','shale.ai.oauth.exchange-enabled','shale.ai.cimd.enabled','shale.ai.dcr.enabled')
$script:LiveControls=@($script:LiveControlProperties | ForEach-Object {ConvertTo-LiveEnvironmentName $_})
function New-LiveDirectory([string]$Path) {
    [void][IO.Directory]::CreateDirectory($Path)
    if ($IsWindows) {
        $acl=[Security.AccessControl.DirectorySecurity]::new(); $acl.SetAccessRuleProtection($true,$false)
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User; $acl.SetOwner($sid)
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        Set-Acl -LiteralPath $Path -AclObject $acl
    } else { [IO.File]::SetUnixFileMode($Path,[IO.UnixFileMode]448) }
}
function Read-LiveSqlEvidence([string]$Path) {
    $dir=Assert-PrivatePath $Path
    if ((Get-FileHash (Join-Path $PSScriptRoot 'stages.json')).Hash -cne 'C7F3374BEE53A4EC3566B5A6645AF7DC250669504F69BDD1028A66F666B0C5F5') { throw 'SQL_MANIFEST_CHANGED' }
    $report=Get-Content -LiteralPath (Join-Path $dir 'report.json') -Raw | ConvertFrom-Json
    if ($report.Status -cne 'complete' -or $report.Completed -ne 51 -or $report.Total -ne 51 -or
        $report.Application -cne 'Stopped' -or $report.Activation -cne 'OFF') { throw 'SQL_SUCCESS_REQUIRED' }
    $journal=Join-Path $dir 'journal.jsonl'; $previous='0'*64; $events=@()
    $bytes=[IO.File]::ReadAllText($journal)
    if (-not $bytes.EndsWith("`n")) { throw 'SQL_JOURNAL_TRUNCATED' }
    foreach ($line in [IO.File]::ReadAllLines($journal)) {
        $record=$line | ConvertFrom-Json
        if ($record.Previous -cne $previous -or $record.Hash -cne (Get-TextSHA256 ($previous+"`n"+$record.Payload))) { throw 'SQL_JOURNAL_INTEGRITY' }
        $previous=$record.Hash; $events+=($record.Payload | ConvertFrom-Json)
    }
    $done=@($events | Where-Object Kind -CEQ 'stage-complete')
    $specs=@(Get-Content (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    if ($done.Count -ne 51 -or $events[-1].Kind -cne 'complete') { throw 'SQL_JOURNAL_INCOMPLETE' }
    # The final PASS field counts verified zero-valued finding columns, not failed findings.
    $files=@{}
    for ($i=0;$i -lt 51;$i++) {
        if ($done[$i].Stage -cne $specs[$i].name -or $done[$i].Completed -ne ($i+1) -or $done[$i].Result -cnotmatch ('^PASS:'+@($specs[$i].sources).Count+':[0-9]{1,3}$')) { throw 'SQL_STAGE_INCOMPLETE' }
        $matches=@(Get-ChildItem -LiteralPath $dir -Directory -Filter 'rendered-*' | ForEach-Object {
            $candidate=Join-Path $_.FullName ($specs[$i].name+'.sql')
            if ((Test-Path -LiteralPath $candidate) -and (Get-StageHash $_.FullName $specs[$i].name) -ceq $done[$i].SHA256) { $candidate }
        })
        if (-not $matches.Count) { throw 'SQL_STAGE_BYTES_MISSING' }; $files[$specs[$i].name]=$matches[0]
    }
    return @{Directory=$dir;Files=$files;JournalHash=$previous}
}
function Get-LiveCredentials([string]$Directory) {
    # Read only. Unlike Get-CleanIdentityCredentials this never fills or rewrites private SQL.
    $credentials=@{}
    foreach ($file in @('identities-foundation.sql','identities.sql')) {
        $text=[IO.File]::ReadAllText((Join-Path $Directory $file))
        foreach ($m in [regex]::Matches($text,"\(N'(mcpacc_[a-z_]+)',(?:N'[A-Za-z0-9]+'|NULL),(?:N'((?:''|[^'])*)'|NULL)\)")) {
            $name=$m.Groups[1].Value
            if ($credentials.ContainsKey($name)) { throw 'DUPLICATE_IDENTITY' }
            if ($m.Groups[2].Success) { $secret=ConvertTo-SecureString $m.Groups[2].Value.Replace("''", "'") -AsPlainText -Force; $c=[pscredential]::new($name,$secret) }
            else { $c=Get-Credential -UserName $name -Message 'Existing acceptance contained credential; memory only' }
            if ($null -eq $c -or $c.UserName -cne $name) { throw 'EXACT_IDENTITY_REQUIRED' }; $credentials[$name]=$c
        }
    }
    if ($credentials.Count -ne 17) { throw 'SEVENTEEN_IDENTITIES_REQUIRED' }
    return $credentials
}
function Get-LiveSettings {
    $values=@{}; $rows=Invoke-CleanAzure @('webapp','config','appsettings','list','--name','shale-mcp-acceptance','--resource-group','Shale','--subscription',$script:CleanSubscription) -PreserveSettingStrings
    foreach ($row in $rows) { if ($values.ContainsKey($row.name)) { throw 'DUPLICATE_APP_SETTING' }; $values[$row.name]=$row.value }
    Assert-LiveSettingNames $values
    return $values
}
function Assert-LiveSettings($Settings,$Credentials) {
    Assert-LiveSettingNames $Settings
    $required=@{'WEBSITES_CONTAINER_START_TIME_LIMIT'='1800';'SPRING_PROFILES_ACTIVE'='azure';'shale.ai.oauth.identity'='PRODUCTION';
      'shale.ai.oauth.public-origin'=$script:LiveOrigin;'shale.ai.oauth.browser-origin'=$script:LiveOrigin;
      'shale.ai.mcp.allowed-origins'=$script:LiveOrigin;'shale.ai.data.database'='ShaleMcpAcceptance';
      'shale.ai.data.contract-version'='V2';'shale.ai.cimd.contract-version'='DCR_V3';'shale.ai.acceptance.slow-verification'='true'}
    $required=ConvertTo-LiveEnvironmentSettings $required
    foreach ($key in $required.Keys) { if ($Settings[$key] -cne $required[$key]) { throw 'REQUIRED_APP_CONFIGURATION' } }
    if (-not $Settings['SHALE_AUTH_TOKEN_SECRET'] -or $Settings['SHALE_AUTH_TOKEN_SECRET'].Length -lt 32 -or
        -not $Settings['SHALE_AUTH_SESSION_BINDING_CUTOVER_AT']) { throw 'ACCEPTANCE_AUTH_CONFIGURATION_REQUIRED' }
    if ($Settings['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED'] -cne 'false') { throw 'ADVERTISEMENT_MUST_BE_OFF' }
    foreach ($key in $script:LiveControls) { if ($Settings[$key] -cne 'false') { throw 'APP_CONTROLS_MUST_BE_OFF' } }
    $sources=Get-LiveSources
    foreach ($prefix in $sources.Keys) {
        $ordinary=$prefix.StartsWith('SHALE_')
        $urlKey=if($ordinary){$prefix+'_DB_URL'}else{ConvertTo-LiveEnvironmentName ($prefix+'.jdbc-url')}
        $userKey=if($ordinary){$prefix+'_DB_USER'}else{ConvertTo-LiveEnvironmentName ($prefix+'.username')}
        $passKey=if($ordinary){$prefix+'_DB_PASSWORD'}else{ConvertTo-LiveEnvironmentName ($prefix+'.password')}
        # Match the exact reviewed restricted URL subset. Runtime itself still does full native verification.
        if ($Settings[$urlKey] -cnotmatch '^jdbc:sqlserver://shale\.database\.windows\.net(?::1433)?;databaseName=ShaleMcpAcceptance;?$' -or
            $Settings[$userKey] -cne ('mcpacc_'+$sources[$prefix]) -or
            $Settings[$passKey] -cne $Credentials['mcpacc_'+$sources[$prefix]].GetNetworkCredential().Password) { throw 'DEDICATED_DATASOURCE_CONFIGURATION' }
    }
    foreach ($key in @('SHALE_APP_JDBC_URL','SHALE_APP_USER','SHALE_APP_PASS','SHALE_RT_JDBC_URL','SHALE_RT_USER','SHALE_RT_PASS')) {
        if ($Settings.ContainsKey($key)) { throw 'ORDINARY_DATASOURCE_ALIAS_REFUSED' }
    }
}
function Set-LiveAppControls([bool]$Enabled,[string]$Run) {
    # Only booleans, no credentials, enter Azure CLI argv/file/output.
    $settings=@{}; foreach ($key in $script:LiveControls) { $settings[$key]=$Enabled.ToString().ToLowerInvariant() }
    $settings['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED']='false'
    $path=Join-Path $Run 'controls.json'; [IO.File]::WriteAllText($path,($settings | ConvertTo-Json))
    $current=Get-LiveSettings
    Assert-LiveSettingNames $current
    foreach($key in $settings.Keys){$current[$key]=$settings[$key]}
    Invoke-LiveArm 'Settings' $current
}
function Invoke-LiveOwner([string]$Sql,[pscredential]$Owner) {
    $guard="IF USER_NAME()<>N'dbo' OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1 THROW 59801,'OWNER_REQUIRED',1;"
    return Invoke-CleanSource ($guard+"`n"+$Sql) $Owner
}
function Assert-LiveSynthetic([pscredential]$Owner) {
    [void](Invoke-LiveOwner @'
IF (SELECT COUNT(*) FROM sys.database_principals WHERE name LIKE N'mcpacc[_]%' AND type='S' AND authentication_type=2)<>17
 OR (SELECT COUNT(DISTINCT sid) FROM sys.database_principals WHERE name LIKE N'mcpacc[_]%')<>17
 THROW 59804,'DISTINCT_CONTAINED_IDENTITIES_REQUIRED',1;
IF (SELECT COUNT(*) FROM dbo.ShaleClients)<>2 OR
 EXISTS(SELECT 1 FROM dbo.ShaleClients WHERE NOT ((Id=7 AND Code=N'LOCAL_A') OR (Id=8 AND Code=N'LOCAL_B'))) OR
 (SELECT COUNT(*) FROM dbo.Users)<>4 OR EXISTS(SELECT 1 FROM dbo.Users WHERE NOT
 ((id=71 AND ShaleClientId=7 AND email=N'admin@example.invalid') OR (id=72 AND ShaleClientId=7 AND email=N'a72@example.invalid') OR
 (id=81 AND ShaleClientId=8 AND email=N'badmin@example.invalid') OR (id=82 AND ShaleClientId=8 AND email=N'b82@example.invalid'))) OR
 (SELECT COUNT(*) FROM dbo.Cases)<>3 OR EXISTS(SELECT 1 FROM dbo.Cases WHERE NOT
 ((Id=100 AND ShaleClientId=7 AND IsDeleted=0 AND Name=N'Literal Alpha') OR (Id=200 AND ShaleClientId=8 AND IsDeleted=0 AND Name=N'Literal Alpha') OR
 (Id=300 AND ShaleClientId=7 AND IsDeleted=1 AND Name=N'Literal deleted')))
 THROW 59804,'SYNTHETIC_STATE_REQUIRED',1;
'@ $Owner)
}
function Get-LiveBinding([string]$Directory,[pscredential]$Owner) {
    $text=[IO.File]::ReadAllText((Join-Path $Directory 'binding-inputs.sql')); $zones=@{}
    foreach ($kind in @('Java','Sql')) {
        $m=[regex]::Match($text,"@Bind${kind}Zone\s+nvarchar\(max\)\s*=\s*N'([A-Za-z0-9_+./: -]{1,100})'")
        if (-not $m.Success) { throw 'BINDING_EVIDENCE_REQUIRED' }; $zones[$kind]=$m.Groups[1].Value
    }
    $pairs=[regex]::Matches($text,"\(\s*'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})'\s*,\s*'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})'\s*\)")
    if ($pairs.Count -ne 28) { throw 'BINDING_SAMPLES_REQUIRED' }
    $sql=@"
IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment)<>1 OR NOT EXISTS(SELECT 1 FROM dbo.AiCaseDataDeployment WHERE Id=1
 AND Issuer=N'$script:LiveOrigin/oauth/mcp' AND Resource=N'$script:LiveOrigin/mcp' AND Contract='case-read-v1'
 AND JavaTimeZone='$($zones.Java)' AND SqlTimeZone=N'$($zones.Sql)')
 OR NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.AiDataImmutableDeployment') AND is_disabled=0)
 THROW 59804,'IMMUTABLE_BINDING_REQUIRED',1;
"@
    foreach ($pair in $pairs) { $sql+="`nIF CONVERT(datetime2(7),(CONVERT(datetime2(7),'$($pair.Groups[1].Value)') AT TIME ZONE 'UTC') AT TIME ZONE N'$($zones.Sql)')<>CONVERT(datetime2(7),'$($pair.Groups[2].Value)') THROW 59804,'BINDING_SAMPLE_MISMATCH',1;" }
    [void](Invoke-LiveOwner $sql $Owner); return $zones
}
function Get-LiveTenantPolicyVersion([int]$Tenant,[int]$User,[pscredential]$Credential) {
    if (-not (($Tenant -eq 7 -and $User -eq 71) -or ($Tenant -eq 8 -and $User -eq 81)) -or
        $Credential.UserName -cne 'mcpacc_runtime') { throw 'SYNTHETIC_TENANT_POLICY_IDENTITY_REQUIRED' }
    $connection=New-CleanConnection $Credential;$command=$null;$reader=$null
    $lines=[Collections.Generic.List[string]]::new();$version='NULL'
    try {
        $connection.Open()
        $target=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/target.sql'))
        Invoke-CleanCommand $connection $target $lines
        $setup="SET NOCOUNT ON; SET IMPLICIT_TRANSACTIONS OFF; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET QUOTED_IDENTIFIER ON; SET NUMERIC_ROUNDABORT OFF; EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=$Tenant; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=$User;"
        Invoke-CleanCommand $connection $setup $lines
        # Execute directly: INSERT EXEC would add a transaction and violate AiLockV1's contract.
        $command=$connection.CreateCommand();$command.CommandTimeout=30;$command.CommandText='EXEC dbo.AiGetTenantPolicyV1;'
        $reader=$command.ExecuteReader()
        if ($reader.FieldCount -ne 2 -or $reader.GetName(0) -cne 'Enabled' -or $reader.GetName(1) -cne 'RowVer' -or -not $reader.Read()) { throw 'TENANT_POLICY_RESULT_REQUIRED' }
        if (-not $reader.IsDBNull(1)) {
            $bytes=$reader.GetValue(1)
            if ($bytes -isnot [byte[]]) { throw 'TENANT_POLICY_VERSION_REQUIRED' }
            # This driver returns an empty timestamp for the getter's absent-policy LEFT JOIN.
            # Treat it as NULL only for an OFF result; the setter still enforces creation/RowVer rules.
            if ($bytes.Length -eq 0 -and -not $reader.GetBoolean(0)) { $version='NULL' }
            elseif ($bytes.Length -eq 8) { $version='0x'+[Convert]::ToHexString($bytes) }
            else { throw 'TENANT_POLICY_VERSION_REQUIRED' }
        }
        if ($reader.Read() -or $reader.NextResult()) { throw 'TENANT_POLICY_SINGLE_RESULT_REQUIRED' }
        $reader.Dispose();$reader=$null
        Invoke-CleanCommand $connection "IF @@TRANCOUNT<>0 THROW 59800,'SOURCE_TRANSACTION_OPEN',1;" $lines
        return $version
    } catch { throw 'TENANT_POLICY_READ_FAILED' } finally {
        if ($null -ne $reader) { $reader.Dispose() };if ($null -ne $command) { $command.Dispose() }
        $connection.Dispose();$bytes=$null;$lines.Clear()
    }
}
function Set-LiveSqlControls([bool]$Enabled,[pscredential]$Owner,$Credentials) {
    $bit=if($Enabled){1}else{0}
    $failed=$false
    # Operational switches have no application audit vocabulary; the private run journal owns evidence.
    foreach ($table in @('AiCimdProofControlV1','AiCimdOAuthControlV1','AiExternalControlV2','AiDcrControlV3')) {
        try { [void](Invoke-LiveOwner "UPDATE dbo.$table SET Enabled=$bit WHERE Id=1; IF @@ROWCOUNT<>1 THROW 59804,'CONTROL_SINGLETON_REQUIRED',1;" $Owner) }
        catch { $failed=$true; if($Enabled){throw 'SQL_ACTIVATION_FAILED'} }
    }
    $action=if($Enabled){'AI_ENABLED'}else{'AI_DISABLED'}
    $sql="SET XACT_ABORT ON; BEGIN TRANSACTION; DECLARE @rc int; EXEC @rc=sys.sp_getapplock @Resource=N'AiCapabilityV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000; IF @rc<0 THROW 59804,'CAPABILITY_LOCK_REQUIRED',1;"
    # Same established transaction-bound product audit; no role/grant/schema change.
    $sql+="UPDATE dbo.AiProductCapability SET Enabled=$bit,Revision=Revision+1,UpdatedAt=SYSUTCDATETIME(),UpdatedByOperator=USER_NAME() WHERE Id=1; IF @@ROWCOUNT<>1 THROW 59804,'CONTROL_SINGLETON_REQUIRED',1;"
    $sql+="INSERT dbo.GlobalControlPlaneAuditLog(OperatorId,EntityType,EntityId,Action,Metadata) VALUES(USER_NAME(),'AI_PRODUCT_CAPABILITY',1,'$action',N'{`"CORRELATION_ID`":`"$([guid]::NewGuid())`"}'); COMMIT;"
    try { [void](Invoke-LiveOwner $sql $Owner) } catch { $failed=$true; if($Enabled){throw 'SQL_ACTIVATION_FAILED'} }
    foreach ($pair in @(@(7,71),@(8,81))) {
        $tenant=$pair[0];$user=$pair[1]
        try {
            $version=Get-LiveTenantPolicyVersion $tenant $user $Credentials['mcpacc_runtime']
            if ($version -cnotmatch '^(NULL|0x[A-F0-9]{16})$') { throw 'TENANT_POLICY_VERSION_REQUIRED' }
            $sql="EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=$tenant; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=$user; DECLARE @v varbinary(8)=$version,@c uniqueidentifier=NEWID(); EXEC dbo.AiSetTenantPolicyV1 @Enabled=$bit,@ExpectedRowVer=@v,@CorrelationId=@c;"
            [void](Invoke-CleanSource $sql $Credentials['mcpacc_runtime'])
        } catch { $failed=$true; if($Enabled){throw 'SQL_ACTIVATION_FAILED'} }
    }
    if($failed){throw 'SQL_SHUTDOWN_NOT_CONFIRMED'}
}

function Assert-LiveSqlOff([pscredential]$Owner) {
    [void](Invoke-CleanSource ([IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/owner.sql'))) $Owner)
}
function Get-LiveWorkerLogPaths {
    $paths=[Collections.Generic.List[string]]::new();$token=$null
    try {
        $token=Invoke-CleanAzure @('account','get-access-token','--resource','https://management.azure.com/','--subscription',$script:CleanSubscription)
        foreach ($scope in @('','Application/')) {
            try {
                $entries=Invoke-RestMethod -Uri ('https://shale-mcp-acceptance.scm.azurewebsites.net/api/vfs/LogFiles/'+$scope) -Method Get -TimeoutSec 30 -MaximumRedirection 0 -Headers @{Authorization=('Bearer '+$token.accessToken)}
            } catch {
                $status=0;try {$status=[int]$_.Exception.Response.StatusCode}catch{}
                if ($scope -ceq 'Application/' -and $status -eq 404) { continue }
                throw 'WORKER_LOG_INVENTORY_FAILED'
            }
            foreach ($entry in $entries) {
                $name=[string]$entry.name
                $selected=if ($scope -ceq '') { $name.EndsWith('docker.log',[StringComparison]::Ordinal) } else { $name.EndsWith('.log',[StringComparison]::Ordinal) }
                if (-not $selected) { continue }
                if ($name -cnotmatch '^[A-Za-z0-9_.-]+$') { throw 'WORKER_LOG_NAME_REFUSED' }
                $path='/home/LogFiles/'+$scope+$name
                if ($paths.Contains($path)) { throw 'WORKER_LOG_INVENTORY_FAILED' }
                $paths.Add($path)
                if ($paths.Count -gt 64) { throw 'WORKER_LOG_BOUND_EXCEEDED' }
            }
        }
        return $paths.ToArray()
    } finally {$token=$null;$entries=$null}
}
function ConvertTo-LiveSafeCampaignCode([string]$Code) {
    $allowed=@('OPERATOR_DECLINED','PRIOR_EVIDENCE_CHANGED','DEPLOYMENT_STARTED_APP_UNEXPECTEDLY',
      'DEPLOYED_JAR_HASH_MISMATCH','WORKER_READINESS_EVIDENCE_MISSING','HEALTH_TIMEOUT',
      'DORMANT_READINESS_REQUIRED','DORMANT_ROUTE_OPEN','SCM_COMMAND_FAILED',
      'SCM_READ_TIMEOUT','SCM_READ_TRANSPORT_FAILED','SCM_READ_COMMAND_REFUSED',
      'WORKER_LOG_INVENTORY_FAILED','WORKER_LOG_NAME_REFUSED','WORKER_LOG_BOUND_EXCEEDED',
      'WORKER_LOG_ARCHIVE_UNCONFIRMED','SQL_ACTIVATION_FAILED','SQL_SHUTDOWN_NOT_CONFIRMED',
      'TENANT_POLICY_READ_FAILED','CLEANUP_NOT_CONFIRMED','CAMPAIGN_FAILED',
      'LIVE_CLIENT_START_FAILED','LIVE_CLIENT_INPUT_FAILED','LIVE_CLIENT_FAILED','LIVE_CLIENT_TIMEOUT','LIVE_CLIENT_REPORT_FAILED')
    if ($Code -cin $allowed -or $Code -cmatch '^CAMPAIGN_HTTP_[45][0-9]{2}$') { return $Code }
    # Reuse the configuration writer's closed ARM-code grammar during activation.
    try { throw $Code } catch {
        $configurationCode=Get-LiveSafeConfigurationFailure $_
        if ($configurationCode -cne 'ACCEPTANCE_CONFIGURATION_FAILED') { return $configurationCode }
    }
    if ($Code -ceq 'ACCEPTANCE_CONFIGURATION_WRITE_FAILED') { return $Code }
    return 'CAMPAIGN_FAILED'
}
function Get-LiveSafeCampaignFailure($Failure) {
    $code=ConvertTo-LiveSafeCampaignCode $Failure.Exception.Message
    if ($code -cne 'CAMPAIGN_FAILED') { return $code }
    $status=0;try {$status=[int]$Failure.Exception.Response.StatusCode}catch{}
    if ($status -ge 400 -and $status -le 599) { return ('CAMPAIGN_HTTP_'+$status) }
    return $code
}
function Get-LiveCampaignDiagnostic($Failure) {
    # No messages, paths, stack text, SQL, response bodies or arbitrary type names are retained.
    $source='unknown';$line=0;$kind='UNKNOWN'
    try {
        $candidate=[IO.Path]::GetFileName($Failure.InvocationInfo.ScriptName)
        if ($candidate -cin @('LiveRuntime.ps1','LiveConfiguration.ps1','CleanSlate.ps1')) {
            $source=$candidate;$line=[int]$Failure.InvocationInfo.ScriptLineNumber
        }
    } catch { }
    $cause=$Failure.Exception
    for ($depth=0;$null -ne $cause -and $depth -lt 8;$depth++) {
        if ($cause -is [TimeoutException]) { $kind='TIMEOUT';break }
        if ($cause -is [OperationCanceledException]) { $kind='CANCELED';break }
        if ($cause -is [Net.Http.HttpRequestException]) { $kind='HTTP_TRANSPORT';break }
        if ($cause -is [IO.IOException]) { $kind='IO';break }
        $cause=$cause.InnerException
    }
    return @{Source=$source;Line=$line;Kind=$kind}
}
function Invoke-LiveScm([string]$Command,[switch]$ReadOnly) {
    # Only these fixed evidence reads may receive the longer transport budget and one retry.
    if ($ReadOnly -and $Command -cne 'sha256sum /home/site/wwwroot/app.jar' -and
        $Command -cnotmatch '^tail -c 200000 /home/LogFiles/(?:Application/)?[A-Za-z0-9_.-]+\.log$') {
        throw 'SCM_READ_COMMAND_REFUSED'
    }
    $timeout=if($ReadOnly){180}else{45};$attempts=if($ReadOnly){2}else{1}
    $token=Invoke-CleanAzure @('account','get-access-token','--resource','https://management.azure.com/')
    try {
        for($attempt=1;$attempt -le $attempts;$attempt++) {
            try {
                $result=Invoke-RestMethod -Uri 'https://shale-mcp-acceptance.scm.azurewebsites.net/api/command' -Method Post -TimeoutSec $timeout -MaximumRedirection 0 -Headers @{Authorization=('Bearer '+$token.accessToken)} -ContentType 'application/json' -Body (@{command=$Command;dir='/home/site/wwwroot'} | ConvertTo-Json)
            } catch {
                if(-not $ReadOnly){throw} # Archive/move mutations are never replayed.
                $failure=$_;$kind=(Get-LiveCampaignDiagnostic $failure).Kind;$status=0
                try{$status=[int]$failure.Exception.Response.StatusCode}catch{}
                $transient=if($status -ge 400){$status -in @(408,429,500,502,503,504)}else{$kind -cin @('TIMEOUT','CANCELED','HTTP_TRANSPORT')}
                if($transient -and $attempt -lt $attempts){Start-Sleep -Seconds 2;continue}
                if($kind -cin @('TIMEOUT','CANCELED')){throw 'SCM_READ_TIMEOUT'}
                if($kind -ceq 'HTTP_TRANSPORT' -and $status -eq 0){throw 'SCM_READ_TRANSPORT_FAILED'}
                throw $failure
            }
            # Invalid output or failed commands are evidence failures, never retry candidates.
            if ($result.ExitCode -ne 0 -or $result.Output.Length -gt 2000000) { throw 'SCM_COMMAND_FAILED' }
            return $result.Output
        }
    } finally { $token=$null }
}
function Assert-LiveWorker([string]$Hash,[string]$JavaZone,[switch]$Ready) {
    $remote=Invoke-LiveScm 'sha256sum /home/site/wwwroot/app.jar' -ReadOnly
    if ($remote.Trim() -cnotmatch '^([a-fA-F0-9]{64})  /home/site/wwwroot/app.jar$' -or $Matches[1] -ine $Hash) { throw 'DEPLOYED_JAR_HASH_MISMATCH' }
    # Old worker logs are moved out of this directory before start; only closed markers are retained.
    $logs=[Text.StringBuilder]::new()
    foreach ($path in @(Get-LiveWorkerLogPaths)) {
        [void]$logs.AppendLine((Invoke-LiveScm ('tail -c 200000 '+$path) -ReadOnly))
        if ($logs.Length -gt 2000000) { throw 'WORKER_LOG_BOUND_EXCEEDED' }
    }
    $logs=$logs.ToString()
    $zone=[regex]::Escape($JavaZone)
    if ($logs -notmatch "SHALE_JVM_STARTUP zoneId=$zone javaVersion=21[.+]" -or ($Ready -and $logs -notmatch 'AI_CASE_SQL_READINESS ready')) { throw 'WORKER_READINESS_EVIDENCE_MISSING' }
    return @([regex]::Matches($logs,'SHALE_JVM_STARTUP [^\r\n]{1,400}|AI_CASE_SQL_READINESS (?:ready|disabled|not-ready)') | ForEach-Object Value)
}
function Wait-LiveHealth {
    $deadline=[datetime]::UtcNow.AddMinutes(30)
    do {
        try { $r=Invoke-WebRequest -Uri "$script:LiveOrigin/api/health/db" -TimeoutSec 10 -MaximumRedirection 0; if($r.StatusCode -eq 200){return} } catch { }
        Start-Sleep -Seconds 3
    } while ([datetime]::UtcNow -lt $deadline)
    throw 'HEALTH_TIMEOUT'
}
function Stop-LiveAcceptance([pscredential]$Owner,$Credentials,[string]$Run,[switch]$VerifySqlOnly) {
    # Independent attempts: SQL failure cannot suppress app OFF/stop, or vice versa.
    $result=@{SqlOff=$false;AppOff=$false;ApplicationStopped=$false}
    try { if (-not $VerifySqlOnly) { Set-LiveSqlControls $false $Owner $Credentials }; Assert-LiveSqlOff $Owner; $result.SqlOff=$true } catch { }
    try {
        Set-LiveAppControls $false $Run; $settings=Get-LiveSettings
        if (@($script:LiveControls | Where-Object {$settings[$_] -cne 'false'}).Count -or $settings['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED'] -cne 'false') { throw 'OFF_NOT_CONFIRMED' }; $result.AppOff=$true
    } catch { }
    try {
        [void](Invoke-CleanAzure @('webapp','stop','--ids',$script:LiveAppId))
        $app=Invoke-CleanAzure @('webapp','show','--ids',$script:LiveAppId)
        if ($app.id -ine $script:LiveAppId -or $app.state -cne 'Stopped') { throw 'STOP_NOT_CONFIRMED' }; $result.ApplicationStopped=$true
    } catch { }
    return $result
}
function Assert-LiveJar([string]$Path,[string]$Hash,[string]$Version) {
    if ($Hash -cnotmatch '^[A-Fa-f0-9]{64}$' -or $Version -cnotmatch '^[A-Za-z0-9_.+-]{1,128}$' -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Hash) { throw 'EXACT_JAR_REQUIRED' }
    $zip=[IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entries=@('BOOT-INF/classes/com/shale/server/ShaleServerApplication.class',
          'BOOT-INF/classes/com/shale/server/oauth/CimdRuntimeConfiguration.class','META-INF/build-info.properties')
        foreach ($entry in $entries) { if (-not $zip.GetEntry($entry)) { throw 'EXECUTABLE_SERVER_JAR_REQUIRED' } }
        if (@($zip.Entries | Where-Object FullName -match '(Fixture|LiveMetadata|ConnectionServer)\.class$').Count) { throw 'FIXTURE_JAR_REFUSED' }
        $reader=[IO.StreamReader]::new($zip.GetEntry($entries[2]).Open())
        try { $info=$reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($info -notmatch '(?m)^build.artifact=shale-server\r?$' -or
            $info -notmatch ('(?m)^build.version='+[regex]::Escape($Version)+'\r?$')) { throw 'BUILD_INFO_MISMATCH' }
    } finally { $zip.Dispose() }
}
function Assert-LiveAzureConfiguration {
    Assert-CleanResources
    $account=Invoke-CleanAzure @('account','show')
    if ($account.id -ine $script:CleanSubscription) { throw 'WRONG_AZURE_SUBSCRIPTION' }
    $app=Invoke-CleanAzure @('webapp','show','--ids',$script:LiveAppId)
    if (-not $app.httpsOnly -or $app.defaultHostName -cne 'shale-mcp-acceptance.azurewebsites.net' -or $app.kind -notmatch 'linux') { throw 'HTTPS_LINUX_APP_REQUIRED' }
    $config=Invoke-CleanAzure @('webapp','config','show','--ids',$script:LiveAppId)
    if ($config.appCommandLine -cne 'java -jar /home/site/wwwroot/app.jar --spring.profiles.active=azure' -or
        $config.linuxFxVersion -cnotmatch '^JAVA\|21(?:-java21)?$' -or $config.minTlsVersion -notin @('1.2','1.3') -or
        $config.ftpsState -cne 'Disabled' -or $config.numberOfWorkers -ne 1) { throw 'APP_STARTUP_CONFIGURATION_REQUIRED' }
    $strings=Invoke-CleanAzure @('webapp','config','connection-string','list','--name','shale-mcp-acceptance','--resource-group','Shale','--subscription',$script:CleanSubscription)
    if (@($strings.PSObject.Properties).Count -gt 0) { throw 'UNREVIEWED_CONNECTION_STRINGS' }
}
function Invoke-LiveRls($Credentials) {
    foreach ($pair in @(@(7,71,100),@(8,81,200))) {
        $t=$pair[0];$u=$pair[1];$id=$pair[2]
        $sql="EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=$t; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=$u; IF (SELECT COUNT(*) FROM dbo.Cases WHERE IsDeleted=0)<>1 OR NOT EXISTS(SELECT 1 FROM dbo.Cases WHERE Id=$id) OR EXISTS(SELECT 1 FROM dbo.Cases WHERE ShaleClientId<>$t) THROW 59804,'RLS_ISOLATION_REQUIRED',1;"
        [void](Invoke-CleanSource $sql $Credentials['mcpacc_runtime'])
    }
    [void](Invoke-CleanSource "IF EXISTS(SELECT 1 FROM dbo.Cases) THROW 59804,'NEUTRAL_RLS_DENIAL_REQUIRED',1;" $Credentials['mcpacc_runtime'])
}
function Get-LiveAuditBaseline([pscredential]$Owner) {
    $lines=Invoke-LiveOwner "SELECT CONCAT('LIVE_BASE:',COALESCE((SELECT MAX(Id) FROM dbo.IntegrationAccessAuditLog),0),':',(SELECT COUNT_BIG(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read')) AS AcceptanceResult;" $Owner
    $markers=@($lines | Where-Object {$_ -cmatch '^LIVE_BASE:[0-9]+:[0-9]+$'})
    if ($markers.Count -ne 1) { throw 'AUDIT_BASELINE_REQUIRED' }
    $values=$markers[0].Split(':'); return @{Access=[long]$values[1];Phi=[long]$values[2]}
}
function Assert-LiveAudits([pscredential]$Owner,$Baseline,[int]$Disclosures,[int]$Overviews) {
    if ($Disclosures -lt 7 -or $Overviews -lt 5) { throw 'LIVE_DISCLOSURES_REQUIRED' }
    $sql=@"
DECLARE @matched bigint=(SELECT COUNT(DISTINCT i.InvocationId) FROM dbo.AiCimdInvocationV1 i
 JOIN dbo.IntegrationAccessAuditLog a ON a.ShaleClientId=i.ShaleClientId AND a.Id=i.ReadEventId AND a.InvocationId=i.InvocationId AND a.UserId=i.UserId AND a.Stage='READ' AND a.Outcome='ALLOWED_READ'
 JOIN dbo.IntegrationAccessAuditResource r ON r.ShaleClientId=a.ShaleClientId AND r.EventId=a.Id AND r.Relation='RETURNED'
 JOIN dbo.AiQuotaReservation q ON q.InvocationId=i.InvocationId AND q.ShaleClientId=i.ShaleClientId AND q.UserId=i.UserId AND q.ReleasedAt IS NOT NULL
 WHERE i.HandoffConsumed=1 AND a.Id>$($Baseline.Access));
IF @matched<>$Disclosures OR (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Id>$($Baseline.Access) AND Stage='READ' AND Outcome='ALLOWED_READ')<>$Disclosures
 OR (SELECT COUNT_BIG(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read')-$($Baseline.Phi)<>$Overviews
 OR EXISTS(SELECT 1 FROM dbo.IntegrationAccessAuditResource r JOIN dbo.IntegrationAccessAuditLog a ON a.ShaleClientId=r.ShaleClientId AND a.Id=r.EventId
 WHERE a.Id>$($Baseline.Access) AND r.Relation='RETURNED' AND NOT ((r.ShaleClientId=7 AND r.CaseId=100) OR (r.ShaleClientId=8 AND r.CaseId=200)))
 OR (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Id>$($Baseline.Access) AND Outcome IN('DENIED','INVALID_INPUT'))<6
 THROW 59804,'DURABLE_AUDIT_ACCEPTANCE_FAILED',1;
SELECT CONCAT('LIVE_AUDIT_PASS:',@matched) AS AcceptanceResult;
"@
    [void](Invoke-LiveOwner $sql $Owner)
}
function Get-LiveClientProgress([string]$Run,[int]$ElapsedMinutes=0) {
    $stream=$null;$reader=$null
    try {
        # Allow the child's atomic replacement while this snapshot is being read on Windows.
        $stream=[IO.File]::Open((Join-Path $Run 'http-report.json'),'Open','Read',([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        $reader=[IO.StreamReader]::new($stream)
        $report=$reader.ReadToEnd() | ConvertFrom-Json
        $phases=@('https-discovery','synthetic-web-login-registration-approval','oauth-s256-state-code-replay',
            'sdk-initialize-tools-case-reads-isolation-denials','refresh-rotation-resource-binding',
            'revocation-access-refresh-denied','natural-credential-expiry','approval-revocation-tenant-isolation')
        $phase=if($report.currentCheck -cin $phases){$report.currentCheck}else{'pending'}
        $completed=@($report.records | Where-Object {$_.status -ceq 'PASS'}).Count
        $responses=@($report.sdkDiagnostics.requests | Where-Object {$null -ne $_.status}).Count
        $disclosures=[int]$report.disclosures;$overviews=[int]$report.overviews
        if($completed -gt 8 -or $responses -gt 128 -or $disclosures -lt 0 -or $disclosures -gt 10000 -or $overviews -lt 0 -or $overviews -gt 10000){throw 'INVALID_PROGRESS'}
        return "Live MCP acceptance: elapsed=$ElapsedMinutes min; phase=$phase; checkpoints=$completed/8; SDK responses=$responses; disclosures=$disclosures; overviews=$overviews."
    } catch { return "Live MCP acceptance running: elapsed=$ElapsedMinutes min; waiting for checkpoint evidence." }
    finally { if($reader){$reader.Dispose()}elseif($stream){$stream.Dispose()} }
}
function Invoke-LiveClient([string]$Run,$HumanCredentials) {
    $psi=[Diagnostics.ProcessStartInfo]::new()
    try {
        # Get-Command may return multiple ApplicationInfo objects on Windows.
        # Match the first executable that the preflight's plain python invocation uses.
        $psi.FileName=Get-Command python -CommandType Application | Select-Object -First 1 -ExpandProperty Source
        if ([string]::IsNullOrWhiteSpace($psi.FileName)) { throw 'PYTHON_UNAVAILABLE' }
    } catch { throw 'LIVE_CLIENT_START_FAILED' }
    $psi.UseShellExecute=$false; $psi.RedirectStandardInput=$true; $psi.RedirectStandardOutput=$true; $psi.RedirectStandardError=$true
    $psi.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
    $psi.Environment['PYTHONIOENCODING']='utf-8'
    $psi.ArgumentList.Add((Join-Path $PSScriptRoot '../mcp-transport/live-azure-python.py')); $psi.ArgumentList.Add($Run)
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$psi; $started=$false
    try {
        try { $started=$process.Start(); if (-not $started) { throw 'START_REFUSED' } }
        catch { throw 'LIVE_CLIENT_START_FAILED' }
        $output=$process.StandardOutput.ReadToEndAsync(); $errors=$process.StandardError.ReadToEndAsync()
        try {
            $inputs=@{}; foreach ($key in $HumanCredentials.Keys) { $inputs[$key]=$HumanCredentials[$key].GetNetworkCredential().Password }
            $process.StandardInput.WriteLine(($inputs | ConvertTo-Json -Compress)); $process.StandardInput.Close()
        } catch { throw 'LIVE_CLIENT_INPUT_FAILED' } finally {$inputs=$null}
        $startedAt=[datetime]::UtcNow; $deadline=$startedAt.AddMinutes($script:LiveClientWatchdogMinutes); $next=$startedAt
        while (-not $process.WaitForExit(1000)) {
            if ([datetime]::UtcNow -gt $deadline) { throw 'LIVE_CLIENT_TIMEOUT' }
            if ([datetime]::UtcNow -ge $next) { Write-Host (Get-LiveClientProgress $Run ([int][math]::Floor(([datetime]::UtcNow-$startedAt).TotalMinutes))); $next=[datetime]::UtcNow.AddSeconds(30) }
        }
        # Raw child streams are never copied into console/reports. The child persists closed evidence fields.
        if ($process.ExitCode -ne 0) { throw 'LIVE_CLIENT_FAILED' }
        try {
            $report=Get-Content -LiteralPath (Join-Path $Run 'http-report.json') -Raw | ConvertFrom-Json
            if ($report.status -cne 'PASS' -or -not $report.credentialCleanup) { throw 'REPORT_REFUSED' }
        } catch { throw 'LIVE_CLIENT_REPORT_FAILED' }
        return $report
    } finally { if ($started -and -not $process.HasExited) { try { $process.Kill($true); [void]$process.WaitForExit(10000) } catch { } }; $process.Dispose(); $inputs=$null }
}
function Start-LiveWorker([ValidatePattern('^acceptance-[a-f0-9]{32}-(before-dormant|dormant)$')][string]$Archive) {
    # SCM executes argv, not a shell: no glob/quote/operator interpretation is assumed.
    [void](Invoke-LiveScm ("mkdir -p /home/LogFiles/$Archive/Application"))
    foreach ($path in @(Get-LiveWorkerLogPaths)) {
        $relative=$path.Substring('/home/LogFiles/'.Length)
        [void](Invoke-LiveScm ("mv -- $path /home/LogFiles/$Archive/$relative"))
    }
    if (@(Get-LiveWorkerLogPaths).Count) { throw 'WORKER_LOG_ARCHIVE_UNCONFIRMED' }
    [void](Invoke-CleanAzure @('webapp','start','--ids',$script:LiveAppId))
    Wait-LiveHealth
}
function Invoke-LiveCampaign($Context) {
    $approved=$false; $sqlActivationAttempted=$false; $failure=$null; $failureCode=$null; $diagnostic=$null; $phase='approval'; $cleanup=$null; $steps=[Collections.Generic.List[object]]::new()
    try {
        # The ONLY approval. No Force/switch/environment/parameter can supply this acknowledgement.
        Write-Host "Target: $script:LiveAppId / ShaleMcpAcceptance; JAR SHA256 $($Context.Hash); build $($Context.Version)"
        Write-Host 'Deploy dormant, verify worker, activate only synthetic acceptance, run MCP/OAuth and audits, then OFF and stop.'
        Write-Host 'Dedicated acceptance verification budget: 600 seconds per admission, 180-second SQL/network reads, 30-second login. Every security check remains enabled; SQL tool transactions retain their existing short limits.'
        Write-Host "Whole client campaign watchdog: $script:LiveClientWatchdogMinutes minutes; per-admission and SQL transaction limits remain unchanged."
        if ((Read-Host 'Type RUN_SHALE_MCP_ACCEPTANCE to approve this whole controlled run') -cne 'RUN_SHALE_MCP_ACCEPTANCE') { throw 'OPERATOR_DECLINED' }
        $approved=$true
        New-CleanEvent (Join-Path $Context.Run 'journal.jsonl') @{Kind='approved';JarSHA256=$Context.Hash;Build=$Context.Version}
        # Repeat prerequisites after approval; do not use approval to override drift.
        $phase='repeat-preflight'
        foreach ($item in $Context.Inventory) { if ((Get-FileHash -LiteralPath $item.Path).Hash -cne $item.SHA256) { throw 'PRIOR_EVIDENCE_CHANGED' } }
        Assert-LiveAzureConfiguration; Assert-LiveSettings (Get-LiveSettings) $Context.Credentials; Assert-LiveSqlOff $Context.Owner
        Assert-LiveSynthetic $Context.Owner
        Assert-LiveJar $Context.Jar $Context.Hash $Context.Version
        $phase='guarded-deployment'
        [void](Invoke-CleanAzure @('webapp','deploy','--ids',$script:LiveAppId,'--src-path',$Context.Jar,'--type','jar','--restart','false','--clean','false','--track-status','false'))
        $app=Invoke-CleanAzure @('webapp','show','--ids',$script:LiveAppId)
        if ($app.state -cne 'Stopped') { throw 'DEPLOYMENT_STARTED_APP_UNEXPECTEDLY' }
        $phase='dormant-startup'
        Start-LiveWorker ('acceptance-'+$Context.Id+'-before-dormant')
        $markers=Assert-LiveWorker $Context.Hash $Context.Zones.Java
        if (@($markers | Where-Object {$_ -ceq 'AI_CASE_SQL_READINESS disabled'}).Count -ne 1) { throw 'DORMANT_READINESS_REQUIRED' }
        foreach ($route in @('/mcp','/oauth/mcp/register','/oauth/mcp/token')) {
            $r=Invoke-WebRequest -Uri ($script:LiveOrigin+$route) -Method Post -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck -MaximumRedirection 0 -TimeoutSec 10
            if ($r.StatusCode -ne 404) { throw 'DORMANT_ROUTE_OPEN' }
        }
        $steps.Add(@{Id='dormant-worker';Status='PASS';Markers=$markers})
        [void](Invoke-CleanAzure @('webapp','stop','--ids',$script:LiveAppId)); Assert-CleanResources
        $phase='activation-prerequisites'
        [void](Get-LiveBinding $Context.Evidence.Directory $Context.Owner)
        Invoke-LiveRls $Context.Credentials
        # All native principal/catalog checks precede activation. No installer stage is callable here.
        Invoke-LiveNativeChecks $Context.Specs $Context.Credentials $Context.Run
        $steps.Add(@{Id='native-identities-catalogs-binding-rls';Status='PASS'})
        $baseline=Get-LiveAuditBaseline $Context.Owner
        $phase='synthetic-sql-activation'
        $sqlActivationAttempted=$true # Set before dispatch: partial activation must receive full shutdown.
        Set-LiveSqlControls $true $Context.Owner $Context.Credentials
        $phase='synthetic-app-activation'
        Set-LiveAppControls $true $Context.Run
        $phase='activation-resource-verification'
        Assert-CleanResources
        $phase='enabled-worker-start-and-health'
        Start-LiveWorker ('acceptance-'+$Context.Id+'-dormant')
        $phase='enabled-worker-evidence'
        $markers=Assert-LiveWorker $Context.Hash $Context.Zones.Java -Ready
        $steps.Add(@{Id='enabled-startup';Status='PASS';Markers=$markers})
        $phase='live-client'
        $client=Invoke-LiveClient $Context.Run $Context.Humans
        $steps.Add(@{Id='live-client';Status='PASS'})
        $phase='durable-audit-verification'
        Assert-LiveAudits $Context.Owner $baseline $client.disclosures $client.overviews
        [void](Get-LiveBinding $Context.Evidence.Directory $Context.Owner); Assert-LiveSynthetic $Context.Owner
        $steps.Add(@{Id='durable-audits-unchanged-binding-and-fixture';Status='PASS'})
    } catch { $failure=$phase; $failureCode=Get-LiveSafeCampaignFailure $_; $diagnostic=Get-LiveCampaignDiagnostic $_ } finally {
        if ($approved) { $cleanup=Stop-LiveAcceptance $Context.Owner $Context.Credentials $Context.Run -VerifySqlOnly:(-not $sqlActivationAttempted) }
        $pass=$approved -and -not $failure -and $cleanup.SqlOff -and $cleanup.AppOff -and $cleanup.ApplicationStopped
        if (-not $failure -and -not $pass) { $failureCode='CLEANUP_NOT_CONFIRMED' }
        $result=@{Status=$(if($pass){'PASS'}else{'FAIL'});Approved=$approved;Failure=$failure;FailureCode=$failureCode;FailureDiagnostic=$diagnostic;Cleanup=$cleanup;Steps=$steps.ToArray();
          JarSHA256=$Context.Hash;BuildVersion=$Context.Version;SqlJournalHash=$Context.Evidence.JournalHash;
          Scope='Synthetic acceptance DCR/OAuth/MCP runtime; no production or CIMD/stock-browser certification'}
        [IO.File]::WriteAllText((Join-Path $Context.Run 'report.json'),($result | ConvertTo-Json -Depth 12))
        New-CleanEvent (Join-Path $Context.Run 'journal.jsonl') @{Kind='finished';Status=$result.Status;Cleanup=$cleanup}
    }
    return $result
}

function Invoke-LiveNativeChecks($Specs,$Credentials,[string]$Run) {
    # Execute only current repository READ-ONLY sources, never arbitrary retained rendered SQL.
    $template=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/identity-check.sql'))
    foreach ($spec in $Specs | Where-Object { $_.name -match '^(31|32|3[3-9]|4[0-9])-' }) {
        $sql=''; $part=0
        foreach ($source in $spec.sources) {
            $part++
            if ($source.StartsWith('identity:')) {
                $role=$source.Substring(9)
                if ($spec.user -cnotmatch '^mcpacc_[a-z_]+$' -or $role -cnotmatch '^[A-Za-z0-9]*$') { throw 'IDENTITY_MANIFEST_REFUSED' }
                $sql+=$template.Replace('__USER__',$spec.user).Replace('__ROLE__',$role)
            } else {
                if ($source -cnotin @('shale-data/src/main/resources/ai/confinement-v2-catalog.sql','shale-data/src/main/resources/ai/registry-dcr-v3-catalog.sql','shale-data/src/main/resources/ai/authorization-dcr-v3-catalog.sql')) { throw 'READ_ONLY_SOURCE_REQUIRED' }
                $sql+=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../'+$source)))
            }
            $sql+="`nGO`nSELECT 'ACCEPTANCE_SOURCE_OK:$($spec.name):$part' AS AcceptanceResult;`nGO`n"
        }
        if ($Run) { New-CleanEvent (Join-Path $Run 'journal.jsonl') @{Kind='native-check-pending';Stage=$spec.name;User=$spec.user;SHA256=(Get-TextSHA256 $sql)} }
        $result=Invoke-CleanStage $spec $sql $Credentials[$spec.user]
        if ($Run) { New-CleanEvent (Join-Path $Run 'journal.jsonl') @{Kind='native-check-complete';Stage=$spec.name;User=$spec.user;Result=$result} }
    }
}

. (Join-Path $PSScriptRoot 'LiveConfiguration.ps1')
