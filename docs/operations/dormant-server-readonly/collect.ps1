# PREPARED ONLY. No production command has been run during packet authoring.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Sql','Azure','Decide')][string]$Mode,
    [string]$SqlcmdPath='sqlcmd.exe',
    [string]$AppResourceId,
    [string]$SqlResult,
    [string]$AzureResult,
    [string]$ReviewedEvidence
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$source='600723b92fe34731e1d6d25e598fc9e3cbfc2770'
$contract=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'contract.json') -Raw | ConvertFrom-Json
$facts=New-Object System.Collections.Generic.List[object]
function Fact([string]$Name,[string]$Status,[long]$FindingCount=0,[string]$Hash='-') {
    if($Name -cnotmatch '^[A-Z][A-Z0-9_]{0,63}$' -or $Status -cnotmatch '^(PASS|FAIL|UNKNOWN)$' -or
       $FindingCount -lt 0 -or $Hash -cnotmatch '^(-|[A-Fa-f0-9]{40}|[A-Fa-f0-9]{64})$'){throw 'Invalid sanitized fact.'}
    $facts.Add([pscustomobject]@{name=$Name;status=$Status;findingCount=$FindingCount;hash=$Hash.ToUpperInvariant()})
}
function Sha([byte[]]$Bytes) {
    $h=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($h.ComputeHash($Bytes))).Replace('-','') } finally {$h.Dispose()}
}
function Property($Object,[string]$Name) {
    if($null -eq $Object){return $null}; $p=$Object.PSObject.Properties[$Name]
    if($null -eq $p){return $null}; return $p.Value
}
function Emit { [pscustomobject]@{sourceSha=$source;facts=@($facts.ToArray())} | ConvertTo-Json -Depth 8 }
if($Mode -eq 'Sql') {
    # SQLCMDINI can execute an arbitrary startup script. Never inherit it or an existing password.
    if($env:SQLCMDINI -or $env:SQLCMDPASSWORD){Fact 'SQL_TRANSPORT' 'UNKNOWN' 1; Emit; exit 2}
    $secure=$null; $bstr=[IntPtr]::Zero; $p=$null
    try {
        $exe=(Get-Command $SqlcmdPath -CommandType Application -ErrorAction Stop).Source
        $help=(& $exe '-?' 2>$null | Out-String)
        if($help -notmatch 'Microsoft.*SQL Server Command Line Tool' -or $help -match '(?i)go-sqlcmd'){throw 'Microsoft ODBC sqlcmd required.'}
        $script=Join-Path $PSScriptRoot 'catalog.sql'
        if($script -match '["\r\n]'){throw 'Unsafe path.'}
        $secure=Read-Host 'SQL password for brian (not retained or printed)' -AsSecureString
        $bstr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        $start=New-Object Diagnostics.ProcessStartInfo
        $start.FileName=$exe; $start.UseShellExecute=$false
        # Microsoft ODBC sqlcmd only: validated TLS, no -C, -P, integrated or interactive Entra login.
        $start.Arguments='-S "tcp:shale.database.windows.net,1433" -d "Shale" -U "brian" -N -b -V 16 -l 5 -t 30 -h -1 -W -w 512 -x -X1 -i "'+$script+'"'
        $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
        $start.EnvironmentVariables.Remove('SQLCMDINI')
        $start.EnvironmentVariables['SQLCMDPASSWORD']=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        $p=New-Object Diagnostics.Process; $p.StartInfo=$start
        [void]$p.Start(); $start.EnvironmentVariables.Remove('SQLCMDPASSWORD')
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr); $bstr=[IntPtr]::Zero
        $stdout=$p.StandardOutput.ReadToEndAsync(); $stderr=$p.StandardError.ReadToEndAsync()
        if(-not $p.WaitForExit(90000)){$p.Kill(); throw 'Bound exceeded.'}
        $raw=$stdout.GetAwaiter().GetResult(); $discard=$stderr.GetAwaiter().GetResult()
        # No raw driver messages, SQL text, identity, error record or connection information is printed.
        if($p.ExitCode -ne 0){Fact 'SQL_TRANSPORT' 'UNKNOWN' 1; Emit; exit 2}
        $seen=@{}
        foreach($line in ($raw -split '\r?\n')) {
            if([string]::IsNullOrWhiteSpace($line)){continue}
            if($line.Trim() -cnotmatch '^FACT\|([A-Z][A-Z0-9_]{0,63})\|(PASS|FAIL|UNKNOWN)\|([0-9]{1,9})\|(-|[A-Fa-f0-9]{64})$'){throw 'Unexpected output.'}
            $name=$Matches[1];$state=$Matches[2];$count=[long]$Matches[3];$hash=$Matches[4]
            if($seen.ContainsKey($name)){throw 'Duplicate fact.'}; $seen[$name]=$true
            Fact $name $state $count $hash
        }
        if(-not $seen.ContainsKey('SQL_COMPLETE')){Fact 'SQL_TRANSPORT' 'UNKNOWN' 1}
    } catch {
        $facts.Clear(); Fact 'SQL_TRANSPORT' 'UNKNOWN' 1
    } finally {
        if($bstr -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)}
        if($null -ne $secure){$secure.Dispose()}; if($null -ne $p){$p.Dispose()}
    }
    Emit; exit 0
}
function AzJson([string[]]$Arguments) {
    # All call sites below are inventory/read commands. Do not add --debug or print returned objects.
    $raw=(& az @Arguments --only-show-errors --output json 2>$null | Out-String)
    if($LASTEXITCODE -ne 0){throw 'Azure inventory unavailable.'}
    return ($raw | ConvertFrom-Json)
}
if($Mode -eq 'Azure') {
    try {
        if($AppResourceId -notmatch '^/subscriptions/[0-9a-fA-F-]{36}/resourceGroups/[^/?#]+/providers/Microsoft.Web/sites/[A-Za-z0-9-]+$'){throw 'Existing resource ID required.'}
        $site=AzJson @('webapp','show','--ids',$AppResourceId)
        if((Property $site 'id') -ine $AppResourceId -or (Property $site 'defaultHostName') -cne 'shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net'){throw 'Target mismatch.'}
        Fact 'AZURE_TARGET' 'PASS'
        $config=AzJson @('webapp','config','show','--ids',$AppResourceId)
        $startup=Property $config 'appCommandLine'
        if($startup -ceq 'java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure' -or $startup -ceq 'java -jar /home/site/wwwroot/shale-server.jar'){Fact 'STARTUP_COMMAND' 'PASS'}else{Fact 'STARTUP_COMMAND' 'UNKNOWN' 1}
        $settings=AzJson @('webapp','config','appsettings','list','--ids',$AppResourceId)
        $map=@{};foreach($item in $settings){if($map.ContainsKey([string]$item.name)){throw 'Ambiguous setting names.'};$map[[string]$item.name]=[string]$item.value}
        function Value([string[]]$Names) {
            foreach($n in $Names){if($map.ContainsKey($n) -and -not [string]::IsNullOrWhiteSpace($map[$n])){return $map[$n]}}
            return $null
        }
        foreach($pair in @(@('AUTH','APP','shale_app'),@('RUNTIME','RT','shale_runtime'))) {
            $kind=$pair[0];$prefix=$pair[1];$user=$pair[2]
            $url=Value @("SHALE_${prefix}_DB_URL","SHALE_${prefix}_JDBC_URL")
            $who=Value @("SHALE_${prefix}_DB_USER","SHALE_${prefix}_USER")
            $state='UNKNOWN'
            if($url -and $who -and $url -notmatch '^@Microsoft.KeyVault\(' -and $who -notmatch '^@Microsoft.KeyVault\('){
                $state='FAIL'
                if($url -match '^jdbc:sqlserver://shale\.database\.windows\.net(?::1433)?;' -and
                   $url -match '(?i);databaseName=Shale(?:;|$)' -and $who -ceq $user -and
                   $url -notmatch '(?i);\s*(encrypt\s*=\s*(false|no|optional)|trustServerCertificate\s*=\s*true)'){$state='PASS'}
            }
            Fact ('ORDINARY_DB_'+$kind) $state $(if($state -eq 'PASS'){0}else{1})
        }
        $secretState='PASS'
        foreach($names in @(@('SHALE_APP_DB_PASSWORD','SHALE_APP_PASS'),@('SHALE_RT_DB_PASSWORD','SHALE_RT_PASS'),@('SHALE_AUTH_TOKEN_SECRET'))) {
            $value=Value $names
            if(-not $value -or $value -match '^@Microsoft.KeyVault\('){$secretState='UNKNOWN'}
            elseif($names[0] -eq 'SHALE_AUTH_TOKEN_SECRET' -and $value.Length -lt 32){$secretState='FAIL';break}
        }
        Fact 'ORDINARY_SECRET_PRESENCE' $secretState $(if($secretState -eq 'PASS'){0}else{1})
        $boundary=Value @('SHALE_AUTH_SESSION_BINDING_CUTOVER_AT');$ttl=Value @('SHALE_AUTH_TOKEN_TTL_SECONDS')
        $profiles=Value @('SPRING_PROFILES_ACTIVE');$parsed=[DateTimeOffset]::MinValue;$ttlNumber=0L
        $ordinary='PASS'
        if(-not $boundary -or $profiles -cne 'azure'){$ordinary='UNKNOWN'}
        elseif($boundary -notmatch '^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$' -or -not [DateTimeOffset]::TryParse($boundary,[ref]$parsed)){$ordinary='FAIL'}
        if($ttl -and (-not [long]::TryParse($ttl,[ref]$ttlNumber) -or $ttlNumber -le 0)){$ordinary='FAIL'}
        $cors=Value @('SHALE_ALLOWED_CORS_ORIGINS','SHALE_SERVER_ALLOWED_CORS_ORIGINS')
        if(-not $cors -and $ordinary -ne 'FAIL'){$ordinary='UNKNOWN'}
        if($cors -and $cors.Contains('*')){$ordinary='FAIL'}
        Fact 'ORDINARY_SESSION_SETTINGS' $ordinary $(if($ordinary -eq 'PASS'){0}else{1})
        # Every spelling whose punctuation-free name matches is examined: conflicting overrides stop.
        $bad=0;$missing=0
        foreach($control in $contract.offControls) {
            $normalized=($control -replace '[^a-zA-Z0-9]','').ToUpperInvariant()
            $keys=@($map.Keys | Where-Object {(($_ -replace '[^a-zA-Z0-9]','').ToUpperInvariant()) -ceq $normalized})
            $valid=@($keys | Where-Object {$_ -ceq $control -or $_ -ceq $control.Replace('.','_').Replace('-','_').ToUpperInvariant()})
            if($valid.Count -eq 0){$missing++}
            foreach($key in $keys){if($map[$key].Trim() -ine 'false'){$bad++}}
        }
        $off='PASS';if($bad -gt 0){$off='FAIL'}elseif($missing -gt 0){$off='UNKNOWN'}
        Fact 'OFF_CONFIGURED' $off ($bad+$missing)
        # Presence facts only; environment overrides can invalidate JVM-effective conclusions.
        $overrideCount=@($map.Keys | Where-Object {$_ -match '^(JAVA|JDK_JAVA_OPTIONS|CATALINA|SPRING_APPLICATION_JSON|SPRING_CONFIG|SPRING_PROFILES|SHALE_)' -and $_ -ne 'SPRING_PROFILES_ACTIVE' -and $_ -match '(OPTIONS|OPTS|CONFIG|JSON|PROFILES)'}).Count
        Fact 'OVERRIDE_REVIEW' 'UNKNOWN' $overrideCount
        # Azure configuration is not a running-JVM effective-properties endpoint.
        Fact 'EFFECTIVE_CONFIG_REVIEW' 'UNKNOWN' 1
        Fact 'ACTIVE_ARTIFACT_REVIEW' 'UNKNOWN' 1
        # Hash only the executable at the documented path via GET. This does NOT establish active bytes.
        $client=$null;$handler=$null;$access=$null;$token=$null;$bytes=$null
        try {
            $scm=@((Property $site 'enabledHostNames') | Where-Object {$_ -match '^shale-api-hsd6hrcya0g4amhv\.scm\.southcentralus-01\.azurewebsites\.net$'})
            if($scm.Count -ne 1){throw 'No unique SCM host.'}
            $token=AzJson @('account','get-access-token','--resource','https://management.azure.com/')
            $access=Property $token 'accessToken';if(-not $access){throw 'No existing Azure token.'}
            Add-Type -AssemblyName System.Net.Http
            $handler=New-Object Net.Http.HttpClientHandler;$handler.AllowAutoRedirect=$false
            $client=New-Object Net.Http.HttpClient($handler);$client.Timeout=[TimeSpan]::FromSeconds(60)
            $client.MaxResponseContentBufferSize=268435456
            $client.DefaultRequestHeaders.Authorization=New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer',$access)
            $bytes=$client.GetByteArrayAsync('https://'+$scm[0]+'/api/vfs/site/wwwroot/shale-server.jar').GetAwaiter().GetResult()
            Fact 'ARTIFACT_ON_DISK' 'PASS' 0 (Sha $bytes)
            # Deployment metadata is informational: latest success alone is not active-JAR parity.
            try {
                $metadataBytes=$client.GetByteArrayAsync('https://'+$scm[0]+'/api/deployments/latest').GetAwaiter().GetResult()
                if($metadataBytes.Length -gt 1048576){throw 'Metadata bound exceeded.'}
                $deployment=[Text.Encoding]::UTF8.GetString($metadataBytes) | ConvertFrom-Json
                $commit=Property $deployment 'id'
                if($commit -cmatch '^[A-Fa-f0-9]{40}$'){Fact 'LATEST_DEPLOYMENT_COMMIT' 'PASS' 0 $commit}
                else {Fact 'LATEST_DEPLOYMENT_COMMIT' 'UNKNOWN' 1}
            } catch {Fact 'LATEST_DEPLOYMENT_COMMIT' 'UNKNOWN' 1}

            Add-Type -AssemblyName System.IO.Compression
            $stream=New-Object IO.MemoryStream(,$bytes);$zip=New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Read)
            try {
                $entry=$zip.GetEntry('BOOT-INF/classes/META-INF/build-info.properties')
                if($null -eq $entry -or $entry.Length -gt 8192){throw 'No bounded build metadata.'}
                $reader=New-Object IO.StreamReader($entry.Open())
                try {$build=$reader.ReadToEnd()} finally {$reader.Dispose()}
                $match=[regex]::Match($build,'(?m)^build\.version=(\d{1,4})\.(\d{1,4})\.(\d{1,8})\r?$')
                if(-not $match.Success){throw 'No bounded version.'}
                Fact 'ARTIFACT_VERSION_MAJOR' 'PASS' ([long]$match.Groups[1].Value)
                Fact 'ARTIFACT_VERSION_MINOR' 'PASS' ([long]$match.Groups[2].Value)
                Fact 'ARTIFACT_VERSION_BUILD' 'PASS' ([long]$match.Groups[3].Value)
            } finally {$zip.Dispose();$stream.Dispose()}
        } catch {Fact 'ARTIFACT_METADATA' 'UNKNOWN' 1} finally {
            if($null -ne $client){$client.Dispose()}elseif($null -ne $handler){$handler.Dispose()}
            $access=$null;$token=$null;$bytes=$null
        }
    } catch { $facts.Clear();Fact 'AZURE_TARGET' 'UNKNOWN' 1 }
    Emit;exit 0
}
function ReviewRecordMatches($Proof) {
    try {
        $path=Property $Proof 'recordPath';$expected=Property $Proof 'recordSha256'
        if(-not $path -or $expected -cnotmatch '^[A-Fa-f0-9]{64}$'){return $false}
        $file=Get-Item -LiteralPath $path
        if($file.PSIsContainer -or $file.Length -gt 1048576){return $false}
        return ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ieq $expected)
    } catch {return $false}
}
# Local-only decision; no network or sqlcmd operation occurs in this mode.
try {
    $sql=Get-Content -LiteralPath $SqlResult -Raw | ConvertFrom-Json
    $azure=Get-Content -LiteralPath $AzureResult -Raw | ConvertFrom-Json
    if($sql.sourceSha -cne $source -or $azure.sourceSha -cne $source){throw 'Source mismatch.'}
    $all=@{};foreach($f in @($sql.facts)+@($azure.facts)) {
        if($all.ContainsKey($f.name)){throw 'Duplicate fact.'}
        if($f.name -cnotmatch '^[A-Z][A-Z0-9_]{0,63}$' -or $f.status -cnotmatch '^(PASS|FAIL|UNKNOWN)$' -or
           $f.hash -cnotmatch '^(-|[A-Fa-f0-9]{40}|[A-Fa-f0-9]{64})$' -or [long]$f.findingCount -lt 0){throw 'Invalid facts.'}
        $all[$f.name]=$f
    }
    $record=$null
    if($ReviewedEvidence){$record=Get-Content -LiteralPath $ReviewedEvidence -Raw | ConvertFrom-Json}
    # Never override FAIL. These entries require independently reviewed EXISTING evidence, not a
    # newly observed hash copied back as its own expectation. They are not activation approvals.
    if($record -and $record.sourceSha -ceq $source -and
       $record.sqlInventorySha256 -ieq (Get-FileHash -LiteralPath $SqlResult -Algorithm SHA256).Hash -and
       $record.azureInventorySha256 -ieq (Get-FileHash -LiteralPath $AzureResult -Algorithm SHA256).Hash) {
        foreach($name in @('STRICT_PREDICATE_REVIEW','SESSION_CATALOG_REVIEW','ORDINARY_API_REVIEW')) {
            $proof=Property $record $name
            if($proof -and $all.ContainsKey($name) -and $all[$name].status -eq 'UNKNOWN' -and
               (Property $proof 'reviewedExistingRecord') -ceq 'PASS' -and
               (Property $proof 'recordSha256') -cmatch '^[A-Fa-f0-9]{64}$' -and (ReviewRecordMatches $proof) -and
               (Property $proof 'expectedHash') -cmatch '^[A-Fa-f0-9]{64}$' -and
               $all[$name].hash -ine '-' -and $all[$name].hash -ieq $proof.expectedHash){$all[$name].status='PASS';$all[$name].findingCount=0}
        }
        # These facts are not observable through SQL catalogs/App Settings alone. The independent
        # record must explicitly cover the exact source, active artifact, current target/settings
        # and all ordinary API schemas/grants, runtime profiles/overrides and original session boundary.
        foreach($name in @('ACTIVE_ARTIFACT_REVIEW','EFFECTIVE_CONFIG_REVIEW')) {
            $proof=Property $record $name
            if($proof -and $all.ContainsKey($name) -and $all.ContainsKey('ARTIFACT_ON_DISK') -and
               $all['ARTIFACT_ON_DISK'].status -eq 'PASS' -and $all[$name].status -eq 'UNKNOWN' -and
               (Property $proof 'reviewedExistingRecord') -ceq 'PASS' -and
               (Property $proof 'recordSha256') -cmatch '^[A-Fa-f0-9]{64}$' -and (ReviewRecordMatches $proof) -and
               (Property $proof 'scopeComplete') -ceq 'PASS' -and
               (Property $proof 'activeJarSha256') -ieq $all['ARTIFACT_ON_DISK'].hash){$all[$name].status='PASS';$all[$name].findingCount=0}
        }
    }
    # A verified effective-configuration record can resolve opaque Key Vault/default/alias evidence,
    # but must explicitly attest each fact. It cannot change any observed FAIL to PASS.
    if($all.ContainsKey('EFFECTIVE_CONFIG_REVIEW') -and $all['EFFECTIVE_CONFIG_REVIEW'].status -eq 'PASS') {
        $proof=Property $record 'EFFECTIVE_CONFIG_REVIEW';$verified=Property $proof 'verifiedFacts'
        foreach($name in @('ORDINARY_DB_AUTH','ORDINARY_DB_RUNTIME','ORDINARY_SECRET_PRESENCE','ORDINARY_SESSION_SETTINGS','OFF_CONFIGURED')) {
            if($all.ContainsKey($name) -and $all[$name].status -eq 'UNKNOWN' -and (Property $verified $name) -ceq 'PASS') {$all[$name].status='PASS';$all[$name].findingCount=0}
        }
    }
    $blocked=New-Object System.Collections.Generic.List[string]
    foreach($name in @($contract.requiredSqlFacts)+@($contract.requiredAzureFacts)+@('SQL_COMPLETE')) {
        if(-not $all.ContainsKey($name) -or $all[$name].status -cne 'PASS' -or [long]$all[$name].findingCount -ne 0){$blocked.Add($name)}
    }
    if($blocked.Count -eq 0){'DORMANT_JAR_COMPATIBILITY PASS';exit 0}
    'DORMANT_JAR_COMPATIBILITY BLOCKED';foreach($name in $blocked){'UNRESOLVED '+$name};exit 2
} catch {'DORMANT_JAR_COMPATIBILITY BLOCKED';'UNRESOLVED EVIDENCE_FORMAT_OR_SOURCE';exit 2}
