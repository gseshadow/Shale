# Acceptance configuration only. Importing defines functions and never contacts Azure or SQL.
function Get-LiveSources {
    $sources=@{'SHALE_APP'='auth';'SHALE_RT'='runtime';'shale.ai.data'='predecessor';
      'shale.ai.data.verifier'='verifier';'shale.ai.oauth.bootstrap'='bootstrap'}
    foreach ($cap in @('case','reviewer','publisher','approval','onboarding','browser','issuer','metadata','resolver','neutral','dcr-endpoint','dcr-metadata')) {
        $sources['shale.ai.cimd.runtime.'+$cap]=$cap.Replace('-','_')
    }
    return $sources
}
function Get-LiveSettingMap {
    # Property -> Azure name; ordinary application aliases are consumed explicitly by existing code.
    $properties=@('SPRING_PROFILES_ACTIVE','SHALE_ALLOWED_CORS_ORIGINS','SHALE_AUTH_TOKEN_SECRET',
      'SHALE_AUTH_TOKEN_TTL_SECONDS','SHALE_AUTH_SESSION_BINDING_CUTOVER_AT','PORT','WEBSITES_PORT',
      'logging.level.io.modelcontextprotocol.modern.JsonRpc','shale.ai.oauth.identity',
      'shale.ai.oauth.public-origin','shale.ai.oauth.browser-origin','shale.ai.mcp.allowed-origins',
      'shale.ai.data.database','shale.ai.data.contract-version','shale.ai.cimd.contract-version',
      'shale.ai.cimd.metadata-advertisement-enabled')+@($script:LiveControlProperties)
    foreach ($prefix in (Get-LiveSources).Keys) {
        if ($prefix.StartsWith('SHALE_')) { foreach ($suffix in @('_DB_URL','_DB_USER','_DB_PASSWORD')) { $properties+=($prefix+$suffix) } }
        else { foreach ($suffix in @('.jdbc-url','.username','.password')) { $properties+=($prefix+$suffix) } }
    }
    $map=@{};$names=@{}
    foreach ($property in $properties) {
        $name=ConvertTo-LiveEnvironmentName $property
        if ($names.ContainsKey($name)) { throw 'AZURE_SETTING_NAME_COLLISION' }
        $names[$name]=$true;$map[$property]=$name
    }
    return $map
}
function Assert-LiveSettingNames($Settings) {
    $reviewed=@((Get-LiveSettingMap).Values)
    foreach ($key in $Settings.Keys) {
        if ($key -cnotmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'INVALID_AZURE_SETTING_NAME' }
        if ($key -iin $reviewed -and $key -cnotin $reviewed) { throw 'CONFIGURATION_ALIAS_REFUSED' }
        if ($key -match '^(JAVA_OPTS|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|SPRING_APPLICATION_JSON|SPRING_CONFIG_.*|SPRING_MAIN_.*|WEBSITE_RUN_FROM_PACKAGE)$') { throw 'CONFIGURATION_OVERRIDE_REFUSED' }
        if (($key -match '^SHALE_AI' -or $key -match '^LOGGING_LEVEL_') -and $key -cnotin $reviewed) { throw 'CONFIGURATION_ALIAS_REFUSED' }
    }
}
function New-LiveSettings($Credentials,$Auth) {
    $settings=@{'SPRING_PROFILES_ACTIVE'='azure';'shale.ai.oauth.identity'='PRODUCTION';
      'shale.ai.oauth.public-origin'=$script:LiveOrigin;'shale.ai.oauth.browser-origin'=$script:LiveOrigin;
      'shale.ai.mcp.allowed-origins'=$script:LiveOrigin;'SHALE_ALLOWED_CORS_ORIGINS'=$script:LiveOrigin;
      'shale.ai.data.database'='ShaleMcpAcceptance';'shale.ai.data.contract-version'='V2';'shale.ai.cimd.contract-version'='DCR_V3';
      'SHALE_AUTH_TOKEN_SECRET'=$Auth.TokenSecret;'SHALE_AUTH_TOKEN_TTL_SECONDS'='3600';
      'SHALE_AUTH_SESSION_BINDING_CUTOVER_AT'=$Auth.Cutover;
      'PORT'='8080';'WEBSITES_PORT'='8080';'logging.level.io.modelcontextprotocol.modern.JsonRpc'='OFF';
      'shale.ai.cimd.metadata-advertisement-enabled'='false'}
    foreach ($key in $script:LiveControlProperties) { $settings[$key]='false' }
    $sources=Get-LiveSources
    foreach ($prefix in $sources.Keys) {
        $name='mcpacc_'+$sources[$prefix]
        if (-not $Credentials.ContainsKey($name) -or $Credentials[$name].UserName -cne $name) { throw 'EXACT_CONTAINED_CREDENTIAL_REQUIRED' }
        $ordinary=$prefix.StartsWith('SHALE_')
        $url=if($ordinary){$prefix+'_DB_URL'}else{$prefix+'.jdbc-url'}
        $user=if($ordinary){$prefix+'_DB_USER'}else{$prefix+'.username'}
        $password=if($ordinary){$prefix+'_DB_PASSWORD'}else{$prefix+'.password'}
        $settings[$url]='jdbc:sqlserver://shale.database.windows.net:1433;databaseName=ShaleMcpAcceptance'
        $settings[$user]=$name; $settings[$password]=$Credentials[$name].GetNetworkCredential().Password
    }
    $settings=ConvertTo-LiveEnvironmentSettings $settings
    Assert-LiveSettings $settings $Credentials
    return $settings
}
function Assert-LiveSecretPermissions([string]$Path) {
    [void](Assert-PrivatePath $Path)
    if ($IsWindows) {
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl=Get-Acl -LiteralPath $Path
        if ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -cne $sid.Value) { throw 'PRIVATE_SECRET_OWNER_REQUIRED' }
        foreach ($rule in $acl.Access) {
            if ($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid.Value) { throw 'PRIVATE_SECRET_ACL_REQUIRED' }
        }
    } elseif (([int][IO.File]::GetUnixFileMode($Path) -band 63) -ne 0) { throw 'PRIVATE_SECRET_MODE_REQUIRED' }
}
function Get-LiveStorePassphrase {
    return Read-Host 'Acceptance secret-store passphrase (20+ characters; retain for subsequent runs; never a production password)' -AsSecureString
}
function Get-LiveStoreKey([securestring]$Passphrase,[byte[]]$Salt) {
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($Passphrase)
    $bytes=$null
    try {
        $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ($plain.Length -lt 20) { throw 'SECRET_STORE_PASSPHRASE_REQUIRED' }
        $bytes=[Text.Encoding]::UTF8.GetBytes($plain)
        return ,[Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($bytes,$Salt,210000,[Security.Cryptography.HashAlgorithmName]::SHA256,32)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $plain=$null
        if ($null -ne $bytes) { [Security.Cryptography.CryptographicOperations]::ZeroMemory($bytes) }
    }
}
function Protect-LiveAuth($Auth,[string]$JournalHash,[securestring]$Passphrase) {
    $aad=[Text.Encoding]::UTF8.GetBytes("ShaleMcpAcceptance|$script:LiveAppId|$JournalHash|1")
    if ($Auth.Cutover -cne '2000-01-01T00:00:00Z') { throw 'SECRET_STORE_CONTENT_REFUSED' }
    $plain=[Text.Encoding]::UTF8.GetBytes((@{TokenSecret=$Auth.TokenSecret;CutoverEpoch=946684800} | ConvertTo-Json -Compress))
    $key=$null; $cipher=$null
    try {
        $envelope=@{Version=1;Application=$script:LiveAppId;SqlJournalHash=$JournalHash}
        if ($IsWindows -and $null -eq $Passphrase) {
            $envelope.Mode='DPAPI-CurrentUser'
            $cipher=[Security.Cryptography.ProtectedData]::Protect($plain,$aad,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        } else {
            $envelope.Mode='PBKDF2-SHA256-AES256-GCM'
            $salt=[Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
            $nonce=[Security.Cryptography.RandomNumberGenerator]::GetBytes(12); $tag=[byte[]]::new(16)
            $key=Get-LiveStoreKey $Passphrase $salt; $cipher=[byte[]]::new($plain.Length)
            $aes=[Security.Cryptography.AesGcm]::new($key,16)
            try { $aes.Encrypt($nonce,$plain,$cipher,$tag,$aad) } finally { $aes.Dispose() }
            $envelope.Salt=[Convert]::ToBase64String($salt);$envelope.Nonce=[Convert]::ToBase64String($nonce);$envelope.Tag=[Convert]::ToBase64String($tag)
        }
        $envelope.Ciphertext=[Convert]::ToBase64String($cipher)
        return $envelope
    } finally {
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($plain)
        if ($null -ne $key) { [Security.Cryptography.CryptographicOperations]::ZeroMemory($key) }
    }
}
function Unprotect-LiveAuth($Envelope,[string]$JournalHash,[securestring]$Passphrase) {
    $key=$null; $plain=$null
    try {
        if ($Envelope.Version -ne 1 -or $Envelope.Application -cne $script:LiveAppId -or $Envelope.SqlJournalHash -cne $JournalHash) { throw 'SECRET_STORE_BINDING_REFUSED' }
        $aad=[Text.Encoding]::UTF8.GetBytes("ShaleMcpAcceptance|$script:LiveAppId|$JournalHash|1")
        $cipher=[Convert]::FromBase64String($Envelope.Ciphertext)
        if ($Envelope.Mode -ceq 'DPAPI-CurrentUser' -and $IsWindows) {
            $plain=[Security.Cryptography.ProtectedData]::Unprotect($cipher,$aad,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        } elseif ($Envelope.Mode -ceq 'PBKDF2-SHA256-AES256-GCM') {
            $salt=[Convert]::FromBase64String($Envelope.Salt);$nonce=[Convert]::FromBase64String($Envelope.Nonce);$tag=[Convert]::FromBase64String($Envelope.Tag)
            if ($salt.Length -ne 16 -or $nonce.Length -ne 12 -or $tag.Length -ne 16) { throw 'SECRET_STORE_SHAPE_REFUSED' }
            $key=Get-LiveStoreKey $Passphrase $salt; $plain=[byte[]]::new($cipher.Length)
            $aes=[Security.Cryptography.AesGcm]::new($key,16)
            try { $aes.Decrypt($nonce,$cipher,$tag,$plain,$aad) } finally { $aes.Dispose() }
        } else { throw 'SECRET_STORE_MODE_REFUSED' }
        $auth=[Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json -AsHashtable
        if ($auth.TokenSecret -cnotmatch '^[A-Za-z0-9+/]{86}==$' -or $auth.CutoverEpoch -ne 946684800) { throw 'SECRET_STORE_CONTENT_REFUSED' }
        return @{TokenSecret=$auth.TokenSecret;Cutover='2000-01-01T00:00:00Z'}
    } catch { throw 'ACCEPTANCE_SECRET_STORE_UNAVAILABLE' } finally {
        if ($null -ne $plain) { [Security.Cryptography.CryptographicOperations]::ZeroMemory($plain) }
        if ($null -ne $key) { [Security.Cryptography.CryptographicOperations]::ZeroMemory($key) }
    }
}
function Get-LiveAuthMaterial([string]$JournalHash) {
    if ($JournalHash -cnotmatch '^[A-F0-9]{64}$') { throw 'SQL_JOURNAL_HASH_REQUIRED' }
    $repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $dir=Assert-PrivatePath (Join-Path $repo 'work/live-mcp-configuration')
    if (Test-Path -LiteralPath $dir) { Assert-LiveSecretPermissions $dir } else { New-LiveDirectory $dir }
    $path=Assert-PrivatePath (Join-Path $dir ($JournalHash+'.auth.json'))
    $passphrase=$null; $auth=$null
    try {
        if (Test-Path -LiteralPath $path) {
            Assert-LiveSecretPermissions $path
            $envelope=Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            if ($envelope.Mode -ceq 'PBKDF2-SHA256-AES256-GCM') { $passphrase=Get-LiveStorePassphrase }
            return Unprotect-LiveAuth $envelope $JournalHash $passphrase
        }
        $secret=[Security.Cryptography.RandomNumberGenerator]::GetBytes(64)
        try { $auth=@{TokenSecret=[Convert]::ToBase64String($secret);Cutover='2000-01-01T00:00:00Z'} }
        finally { [Security.Cryptography.CryptographicOperations]::ZeroMemory($secret) }
        if (-not $IsWindows) { $passphrase=Get-LiveStorePassphrase }
        $envelope=Protect-LiveAuth $auth $JournalHash $passphrase
        # Parent is already private. CreateNew refuses replacement, including a concurrent writer.
        $stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try {
            if (-not $IsWindows) { [IO.File]::SetUnixFileMode($path,[IO.UnixFileMode]384) }
            $bytes=[Text.Encoding]::UTF8.GetBytes(($envelope | ConvertTo-Json -Compress))
            $stream.Write($bytes);$stream.Flush($true)
        } finally { $stream.Dispose() }
        Assert-LiveSecretPermissions $path
        # Verify retained ciphertext before provisioning Azure settings, including Windows DPAPI.
        $saved=Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        $retained=Unprotect-LiveAuth $saved $JournalHash $passphrase
        if ($retained.TokenSecret -cne $auth.TokenSecret) { throw 'SECRET_STORE_READBACK_REFUSED' }
        return $retained
    } catch { throw 'ACCEPTANCE_SECRET_STORE_UNAVAILABLE' } finally { $passphrase=$null; $auth=$null }
}
function Get-LiveAzureRejection($Failure,[string]$Operation) {
    # Read only structured status/code. Never emit response bodies, messages, headers or URLs.
    $status=0;$code='HTTP_ERROR'
    try {
        $response=$Failure.Exception.Response
        $status=[int]$response.StatusCode
    } catch { }
    if ($status -lt 400 -or $status -gt 599) { return 'ACCEPTANCE_CONFIGURATION_WRITE_FAILED' }
    try {
        $payload=$Failure.ErrorDetails.Message | ConvertFrom-Json -ErrorAction Stop
        # ARM error envelopes and Microsoft.Web's top-level Code shape are both structured.
        $errorBody=if ($payload.PSObject.Properties['error']) { $payload.error } else { $payload }
        $azureCode=$errorBody.code
        $allowed=@{BadRequest='BAD_REQUEST';InvalidRequestContent='INVALID_REQUEST_CONTENT';
          InvalidParameter='INVALID_PARAMETER';Unauthorized='UNAUTHORIZED';AuthorizationFailed='AUTHORIZATION_FAILED';
          Forbidden='FORBIDDEN';ResourceNotFound='RESOURCE_NOT_FOUND';ResourceGroupNotFound='RESOURCE_GROUP_NOT_FOUND';
          Conflict='CONFLICT';TooManyRequests='TOO_MANY_REQUESTS';InternalServerError='INTERNAL_SERVER_ERROR';
          ServiceUnavailable='SERVICE_UNAVAILABLE'}
        if ($azureCode -is [string] -and $allowed.ContainsKey($azureCode)) { $code=$allowed[$azureCode] }
    } catch { }
    return ('ACCEPTANCE_AZURE_'+$Operation.ToUpperInvariant()+'_HTTP_'+$status+'_'+$code)
}
function Get-LiveSafeConfigurationFailure($Failure,[string]$Fallback='ACCEPTANCE_CONFIGURATION_FAILED') {
    $safe=$Failure.Exception.Message
    if ($safe -cmatch '^ACCEPTANCE_AZURE_(SETTINGS|STARTUP|HTTPS)_HTTP_[45][0-9]{2}_(HTTP_ERROR|BAD_REQUEST|INVALID_REQUEST_CONTENT|INVALID_PARAMETER|UNAUTHORIZED|AUTHORIZATION_FAILED|FORBIDDEN|RESOURCE_NOT_FOUND|RESOURCE_GROUP_NOT_FOUND|CONFLICT|TOO_MANY_REQUESTS|INTERNAL_SERVER_ERROR|SERVICE_UNAVAILABLE)$') { return $safe }
    return $Fallback
}
function Invoke-LiveArm([ValidateSet('Settings','Startup','Https')][string]$Operation,$Properties) {
    # Fixed target and operation map. Never send a secret via CLI arguments, a request-body file or CLI HTTP logs.
    $suffix=switch($Operation){'Settings'{'/config/appsettings'} 'Startup'{'/config/web'} 'Https'{''}}
    $method=if($Operation -ceq 'Settings'){'Put'}else{'Patch'}
    $token=$null; $body=$null
    try {
        $token=Invoke-CleanAzure @('account','get-access-token','--resource','https://management.azure.com/','--subscription',$script:CleanSubscription)
        $body=@{properties=$Properties} | ConvertTo-Json -Depth 6 -Compress
        [void](Invoke-RestMethod -Uri ("https://management.azure.com"+$script:LiveAppId+$suffix+'?api-version=2023-12-01') -Method $method -TimeoutSec 60 -MaximumRedirection 0 -Headers @{Authorization=('Bearer '+$token.accessToken)} -ContentType 'application/json' -Body $body -Verbose:$false -Debug:$false -ErrorAction Stop)
    } catch { throw (Get-LiveAzureRejection $_ $Operation) } finally { $token=$null;$body=$null }
}
function Assert-LiveConfigurationTarget {
    Assert-CleanResources
    $account=Invoke-CleanAzure @('account','show')
    if ($account.id -ine $script:CleanSubscription) { throw 'WRONG_AZURE_SUBSCRIPTION' }
    $app=Invoke-CleanAzure @('webapp','show','--ids',$script:LiveAppId)
    if ($app.defaultHostName -cne 'shale-mcp-acceptance.azurewebsites.net' -or $app.kind -notmatch 'linux') { throw 'ACCEPTANCE_LINUX_APP_REQUIRED' }
    $strings=Invoke-CleanAzure @('webapp','config','connection-string','list','--name','shale-mcp-acceptance','--resource-group','Shale','--subscription',$script:CleanSubscription)
    if (@($strings.PSObject.Properties).Count -gt 0) { throw 'UNREVIEWED_CONNECTION_STRINGS' }
}
function Initialize-LiveConfiguration($Credentials,[string]$JournalHash,[string]$Run) {
    $started=$false;$complete=$false;$auth=$null;$settings=$null
    try {
        Assert-LiveConfigurationTarget
        $existing=Get-LiveSettings
        Assert-LiveSettingNames $existing
        foreach ($key in @($script:LiveControls)+@('SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED')) {
            if ($existing.ContainsKey($key) -and $existing[$key] -cne 'false') { throw 'CONFIGURATION_REQUIRES_OFF_CONTROLS' }
        }
        $auth=Get-LiveAuthMaterial $JournalHash
        $settings=New-LiveSettings $Credentials $auth
        foreach ($key in $settings.Keys) {
            if ($existing.ContainsKey($key) -and $existing[$key] -cne $settings[$key]) { throw 'EXISTING_ACCEPTANCE_SETTING_CONFLICT' }
        }
        $merged=$existing.Clone();foreach($key in $settings.Keys){$merged[$key]=$settings[$key]}
        Assert-LiveSettings $merged $Credentials
        New-CleanEvent (Join-Path $Run 'journal.jsonl') @{Kind='configuration-pending';Application=$script:LiveAppId;SqlJournalHash=$JournalHash;SettingNames=@($settings.Keys | Sort-Object)}
        $started=$true
        Invoke-LiveArm 'Settings' $merged
        Assert-CleanResources
        Invoke-LiveArm 'Startup' @{linuxFxVersion='JAVA|21-java21';appCommandLine='java -jar /home/site/wwwroot/app.jar --spring.profiles.active=azure';minTlsVersion='1.2';ftpsState='Disabled';numberOfWorkers=1;cors=@{allowedOrigins=@($script:LiveOrigin);supportCredentials=$false}}
        Assert-CleanResources
        Invoke-LiveArm 'Https' @{httpsOnly=$true}
        Assert-LiveAzureConfiguration
        $actual=Get-LiveSettings;Assert-LiveSettings $actual $Credentials
        foreach ($key in $settings.Keys) { if($actual[$key] -cne $settings[$key]){throw 'CONFIGURATION_READBACK_MISMATCH'} }
        New-CleanEvent (Join-Path $Run 'journal.jsonl') @{Kind='configuration-complete';Application='Stopped';Activation='OFF';SqlJournalHash=$JournalHash}
        $complete=$true
    } catch {
        throw (Get-LiveSafeConfigurationFailure $_)
    } finally {
        if ($started -and -not $complete) {
            # Configuration never writes SQL. Independently attempt OFF/stop if an Azure write fails.
            $off=$false;$stopped=$false
            try {
                Set-LiveAppControls $false $Run
                $observed=Get-LiveSettings
                if (@(@($script:LiveControls)+@('SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED') | Where-Object {$observed[$_] -cne 'false'}).Count) { throw 'OFF_UNCONFIRMED' }
                $off=$true
            } catch { }
            try { [void](Invoke-CleanAzure @('webapp','stop','--ids',$script:LiveAppId));Assert-CleanResources;$stopped=$true } catch { }
            try { New-CleanEvent (Join-Path $Run 'journal.jsonl') @{Kind='configuration-failed';ControlsOff=$off;ApplicationStopped=$stopped} } catch { }
        }
        $auth=$null;$settings=$null;$existing=$null;$merged=$null;$actual=$null
    }
}
