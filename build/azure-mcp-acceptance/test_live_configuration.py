"""Offline configuration regressions. All Azure, REST, SQL and prompt seams use synthetic data."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).parent


class LiveConfigurationTest(unittest.TestCase):
    def ps(self, body):
        result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command',
            "$ErrorActionPreference='Stop'; . ./build/azure-mcp-acceptance/LiveRuntime.ps1; "
            "function Invoke-CleanAzure {throw 'OFFLINE_AZURE_FORBIDDEN'}; "
            "function Invoke-RestMethod {throw 'OFFLINE_HTTP_FORBIDDEN'}; "
            "function Invoke-CleanSource {throw 'OFFLINE_SQL_FORBIDDEN'}; " + body + '\nexit 0'],
            cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn('SYNTHETIC_SECRET_SENTINEL', result.stdout + result.stderr)
        return result.stdout

    def credentials(self):
        return r'''
$c=@{}
foreach($suffix in (Get-LiveSources).Values){
 $name='mcpacc_'+$suffix
 $c[$name]=[pscredential]::new($name,(ConvertTo-SecureString ('SYNTHETIC_SECRET_SENTINEL_'+$name) -AsPlainText -Force))
}
$a=@{TokenSecret=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64));Cutover='2000-01-01T00:00:00Z'}
'''

    def test_all_seventeen_sources_and_off_controls_construct_from_existing_credentials(self):
        self.ps(self.credentials() + r'''
$s=New-LiveSettings $c $a
Assert-LiveSettings $s $c
if(@($s.Keys|Where-Object {$_ -match '_PASSWORD$'}).Count -ne 17){throw 'Missing dedicated credentials'}
if(@($script:LiveControls|Where-Object {$s[$_] -cne 'false'}).Count -or $s['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED'] -cne 'false'){throw 'Configuration enabled AI'}
if($s.SHALE_AUTH_TOKEN_SECRET -cin @($c.Values|ForEach-Object {$_.GetNetworkCredential().Password})){throw 'JWT reused SQL credential'}
if($s.SHALE_ALLOWED_CORS_ORIGINS -cne $script:LiveOrigin -or $s.SHALE_AUTH_TOKEN_TTL_SECONDS -cne '3600'){throw 'Ordinary auth/CORS missing'}
$c.Remove('mcpacc_case');$refused=$false;try{New-LiveSettings $c $a}catch{$refused=$true};if(-not $refused){throw 'Missing SQL credential accepted'}
''')

    def test_every_generated_name_maps_to_its_spring_property_or_explicit_alias(self):
        # Independent inventory prevents an omitted source/control or incorrect hyphen mapping.
        import json
        result = json.loads(self.ps(self.credentials() + r'''
$s=New-LiveSettings $c $a
@{Map=(Get-LiveSettingMap);Names=@($s.Keys)} | ConvertTo-Json -Depth 4 -Compress
'''))
        aliases = ['SPRING_PROFILES_ACTIVE', 'SHALE_ALLOWED_CORS_ORIGINS',
                   'SHALE_AUTH_TOKEN_SECRET', 'SHALE_AUTH_TOKEN_TTL_SECONDS',
                   'SHALE_AUTH_SESSION_BINDING_CUTOVER_AT', 'PORT', 'WEBSITES_PORT']
        properties = [
            'logging.level.io.modelcontextprotocol.modern.JsonRpc',
            'shale.ai.oauth.identity', 'shale.ai.oauth.public-origin',
            'shale.ai.oauth.browser-origin', 'shale.ai.mcp.allowed-origins',
            'shale.ai.data.database', 'shale.ai.data.contract-version',
            'shale.ai.cimd.contract-version', 'shale.ai.cimd.metadata-advertisement-enabled',
            'shale.ai.enabled', 'shale.ai.mcp.enabled',
            'shale.ai.mcp.case-data-composition-enabled', 'shale.ai.mcp.search-cases-enabled',
            'shale.ai.mcp.get-case-overview-enabled', 'shale.ai.oauth.composition-enabled',
            'shale.ai.oauth.authorization-enabled', 'shale.ai.oauth.exchange-enabled',
            'shale.ai.cimd.enabled', 'shale.ai.dcr.enabled']
        for prefix in ('SHALE_APP', 'SHALE_RT'):
            aliases.extend(prefix + suffix for suffix in ('_DB_URL', '_DB_USER', '_DB_PASSWORD'))
        prefixes = ['shale.ai.data', 'shale.ai.data.verifier', 'shale.ai.oauth.bootstrap']
        prefixes.extend('shale.ai.cimd.runtime.' + capability for capability in (
            'case', 'reviewer', 'publisher', 'approval', 'onboarding', 'browser',
            'issuer', 'metadata', 'resolver', 'neutral', 'dcr-endpoint', 'dcr-metadata'))
        for prefix in prefixes:
            properties.extend(prefix + suffix for suffix in ('.jdbc-url', '.username', '.password'))
        expected = {prop: prop.replace('.', '_').replace('-', '').upper()
                    for prop in properties + aliases}
        self.assertEqual(result['Map'], expected)
        self.assertCountEqual(result['Names'], expected.values())
        self.assertEqual(len(set(expected.values())), len(expected), 'Spring mapping collision')
        for name in result['Names']:
            self.assertRegex(name, r'^[A-Z_][A-Z0-9_]*$', name)
        self.assertEqual(expected['shale.ai.mcp.search-cases-enabled'], 'SHALE_AI_MCP_SEARCHCASESENABLED')
        self.assertEqual(expected['shale.ai.cimd.runtime.case.jdbc-url'], 'SHALE_AI_CIMD_RUNTIME_CASE_JDBCURL')

    def test_real_azure_json_readback_preserves_setting_strings(self):
        # Exercise the actual CLI parser: object mocks previously hid timestamp coercion.
        self.ps(self.credentials() + r'''
. ./build/azure-mcp-acceptance/CleanSlate.ps1
$script:originalSettings=New-LiveSettings $c $a
$script:originalSettings.ACCEPTANCE_OFFSET='2026-11-17T10:20:30.1234567-06:00'
$script:originalSettings.ACCEPTANCE_TEXT='00123'
$script:originalSettings.ACCEPTANCE_BOOLEAN='false'
$script:originalSettings.ACCEPTANCE_EMPTY=''
$script:jsonRows=@($script:originalSettings.Keys | ForEach-Object {[pscustomobject]@{name=$_;value=$script:originalSettings[$_];slotSetting=$false}}) | ConvertTo-Json -Compress
function az { $global:LASTEXITCODE=0; return $script:jsonRows }
$s=Get-LiveSettings
foreach($key in $script:originalSettings.Keys){
 if($s[$key] -isnot [string] -or $s[$key] -cne $script:originalSettings[$key]){throw 'Azure setting string changed during JSON parsing'}
}
Assert-LiveSettings $s $c
$script:jsonRows='[]';if((Get-LiveSettings).Count -ne 0){throw 'Empty app settings not preserved'}
$script:jsonRows='[{"name":"SHALE_AI_ENABLED","value":"false"},{"name":"SHALE_AI_ENABLED","value":"false"}]'
$refused=$false;try{Get-LiveSettings}catch{$refused=$true};if(-not $refused){throw 'Duplicate settings accepted'}
$script:jsonRows='[{"name":"SHALE_AI_ENABLED","value":false}]'
$refused=$false;try{Get-LiveSettings}catch{$refused=$true};if(-not $refused){throw 'Non-string setting accepted'}
''')

    def test_mapping_collisions_and_unsafe_readback_names_are_refused(self):
        self.ps(r'''
foreach($properties in @(@{'shale.ai.a-b'='x';'shale.ai.ab'='y'},@{'a.b'='x';'a_b'='y'})){
 $refused=$false;try{ConvertTo-LiveEnvironmentSettings $properties}catch{$refused=$true;if($_.Exception.Message -cne 'AZURE_SETTING_NAME_COLLISION'){throw 'Wrong collision diagnostic'}}
 if(-not $refused){throw 'Colliding Spring properties accepted'}
}
foreach($name in @('shale.ai.enabled','shale.ai.cimd.runtime.case.jdbc-url','SHALE_AI_MCP_SEARCH_CASES_ENABLED',
 'SHALE_AI_CIMD_RUNTIME_DCR_ENDPOINT_JDBC_URL','SHALE_AI_UNKNOWN','shale_ai_enabled','shale_auth_token_secret','SPRING_APPLICATION_JSON','JAVA_OPTS')){
 $script:name=$name
 function Invoke-CleanAzure {return @([pscustomobject]@{name=$script:name;value='SYNTHETIC_SECRET_SENTINEL'})}
 $refused=$false;try{Get-LiveSettings}catch{$refused=$true}
 if(-not $refused){throw 'Unsafe Azure readback accepted'}
}
function Invoke-CleanAzure {return @([pscustomobject]@{name='SHALE_AI_ENABLED';value='false'},[pscustomobject]@{name='SHALE_AI_ENABLED';value='false'})}
$refused=$false;try{Get-LiveSettings}catch{$refused=$true};if(-not $refused){throw 'Duplicate readback accepted'}
''')

    def test_activation_and_off_use_only_mapped_control_names(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(self.credentials() + r'''
$script:settings=New-LiveSettings $c $a
function Get-LiveSettings {return $script:settings.Clone()}
function Invoke-LiveArm($Operation,$Properties){Assert-LiveSettingNames $Properties;$script:settings=$Properties.Clone()}
foreach($enabled in @($true,$false)){
 Set-LiveAppControls $enabled 'DIRECTORY'
 foreach($property in $script:LiveControlProperties){
  $key=ConvertTo-LiveEnvironmentName $property
  if($script:settings.ContainsKey($property) -or $script:settings[$key] -cne $enabled.ToString().ToLowerInvariant()){throw 'Control mapping mismatch'}
 }
 if($script:settings.SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED -cne 'false'){throw 'Advertisement activated'}
}
Assert-LiveSettings $script:settings $c
'''.replace('DIRECTORY', directory))

    def test_http_rejections_survive_outer_handler_and_independent_cleanup_safely(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(self.credentials() + r'''
function Assert-LiveConfigurationTarget {}
function Get-LiveAuthMaterial {return $a}
function Get-LiveSettings {return @{}}
function Invoke-RestMethod {
 $response=[Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]$script:status)
 $exception=[Microsoft.PowerShell.Commands.HttpResponseException]::new('SYNTHETIC_SECRET_SENTINEL',$response)
 $record=[Management.Automation.ErrorRecord]::new($exception,'private-error',[Management.Automation.ErrorCategory]::InvalidOperation,$null)
 $record.ErrorDetails=[Management.Automation.ErrorDetails]::new($script:details)
 throw $record
}
function Set-LiveAppControls { $script:off=$true }
function Invoke-CleanAzure($Arguments){if($Arguments[1] -ceq 'stop'){$script:stopped=$true};return @{accessToken='SYNTHETIC_SECRET_SENTINEL'}}
function Assert-CleanResources {}
foreach($case in @(
 @(400,'BadRequest','BAD_REQUEST'),@(403,'AuthorizationFailed','AUTHORIZATION_FAILED'),
 @(409,'Conflict','CONFLICT'),@(429,'TooManyRequests','TOO_MANY_REQUESTS'),
 @(500,'SYNTHETIC_SECRET_SENTINEL','HTTP_ERROR'),@(503,'ServiceUnavailable','SERVICE_UNAVAILABLE'),
 @(400,'malformed','HTTP_ERROR'),@(400,'web-shape','BAD_REQUEST'))){
 $script:status=$case[0];$script:details=@{error=@{code=$case[1];message='SYNTHETIC_SECRET_SENTINEL';details=@('SYNTHETIC_SECRET_SENTINEL')}}|ConvertTo-Json -Depth 4
 if($case[1] -ceq 'malformed'){$script:details='SYNTHETIC_SECRET_SENTINEL'}
 if($case[1] -ceq 'web-shape'){$script:details=@{Code='BadRequest';Message='SYNTHETIC_SECRET_SENTINEL'}|ConvertTo-Json}
 $expected='ACCEPTANCE_AZURE_SETTINGS_HTTP_'+$case[0]+'_'+$case[2]
 foreach($operation in @('Settings','Startup','Https')){
  $refused=$false;try{Invoke-LiveArm $operation @{secret='SYNTHETIC_SECRET_SENTINEL'}}catch{
   $refused=$true;if($_.Exception.Message -cne $expected.Replace('SETTINGS',$operation.ToUpperInvariant())){throw 'Unsafe or imprecise ARM diagnostic'}
  };if(-not $refused){throw 'HTTP failure accepted'}
 }
 $script:off=$false;$script:stopped=$false;$refused=$false
 try{Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'}catch{$refused=$true;if($_.Exception.Message -cne $expected){throw 'Outer handler masked HTTP rejection'}}
 if(-not $refused -or -not $script:off -or -not $script:stopped){throw 'HTTP rejection bypassed cleanup'}
}
'''.replace('DIRECTORY', directory))

    def test_operator_entrypoint_reports_only_closed_azure_failure_codes(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path ./build/azure-mcp-acceptance 'Start-LiveRuntimeAcceptance.ps1'),[ref]$null,[ref]$null)
$entryTry=@($ast.EndBlock.Statements | Where-Object {$_ -is [Management.Automation.Language.TryStatementAst]})[0]
$block=$entryTry.CatchClauses[0].Body.Extent.Text
$handler=[scriptblock]::Create($block.Substring(1,$block.Length-2))
$run='DIRECTORY';$campaign=$false;$operation='secure-acceptance-configuration'
foreach($testFailure in @('ACCEPTANCE_AZURE_SETTINGS_HTTP_400_BAD_REQUEST','SYNTHETIC_SECRET_SENTINEL','ACCEPTANCE_AZURE_SETTINGS_HTTP_400_SYNTHETIC_SECRET_SENTINEL')){
 $expected=if($testFailure -ceq 'ACCEPTANCE_AZURE_SETTINGS_HTTP_400_BAD_REQUEST'){$testFailure}else{'PREFLIGHT_FAILED'}
 $refused=$false
 try{try{throw $testFailure}catch{. $handler}}catch{
  $refused=$true
  if($_.Exception.Message.Contains('SYNTHETIC_SECRET_SENTINEL') -or -not $_.Exception.Message.Contains($expected)){throw 'Unsafe operator diagnostic'}
 }
 $report=Get-Content (Join-Path $run 'report.json') -Raw|ConvertFrom-Json
 if(-not $refused -or $report.Failure -cne $expected -or $report.Approved){throw 'Unsafe operator report'}
}
'''.replace('DIRECTORY', directory))

    def test_secret_crypto_roundtrip_wrong_passphrase_tampering_and_target_binding(self):
        self.ps(self.credentials() + r'''
$p=ConvertTo-SecureString 'offline passphrase never production 123' -AsPlainText -Force
$e=Protect-LiveAuth $a ('A'*64) $p
if(($e|ConvertTo-Json).Contains($a.TokenSecret)){throw 'Plain secret serialized'}
$r=Unprotect-LiveAuth $e ('A'*64) $p
if($r.TokenSecret -cne $a.TokenSecret -or $r.Cutover -cne $a.Cutover){throw 'Secret changed during encryption'}
foreach($bad in @('passphrase','journal','tag','target')){
 $x=$e.Clone();$h='A'*64;$key=$p
 switch($bad){
 'passphrase'{$key=ConvertTo-SecureString 'different offline passphrase 456' -AsPlainText -Force}
 'journal'{$h='B'*64}
 'tag'{$x.Tag=[Convert]::ToBase64String([byte[]]::new(16))}
 'target'{$x.Application='/subscriptions/other/providers/Microsoft.Web/sites/production'}
 }
 $refused=$false;try{Unprotect-LiveAuth $x $h $key}catch{$refused=$true;if($_.Exception.Message -cne 'ACCEPTANCE_SECRET_STORE_UNAVAILABLE'){throw 'Raw crypto exception leaked'}}
 if(-not $refused){throw 'Invalid encrypted material accepted'}
}
''')

    def test_encrypted_material_is_reused_without_rotating_or_changing_old_evidence(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
# Redirect only the fixed private-store path into a disposable work directory.
function Assert-PrivatePath($Path){return $Path.Replace((Join-Path ([IO.Path]::GetFullPath('.')) 'work/live-mcp-configuration'),'DIRECTORY/store')}
function Get-LiveStorePassphrase {return ConvertTo-SecureString 'offline secret store passphrase 1234' -AsPlainText -Force}
$a=Get-LiveAuthMaterial ('A'*64)
$p='DIRECTORY/store/'+('A'*64)+'.auth.json';$before=(Get-FileHash $p).Hash
$b=Get-LiveAuthMaterial ('A'*64)
if($a.TokenSecret -cne $b.TokenSecret -or (Get-FileHash $p).Hash -cne $before){throw 'Secret rotated on retry'}
if([IO.File]::ReadAllText($p).Contains($a.TokenSecret)){throw 'Plaintext secret reached disk'}
if(-not $IsWindows -and (([int][IO.File]::GetUnixFileMode($p) -band 63) -ne 0)){throw 'World/group-readable secret file'}
[IO.File]::WriteAllText($p,'corrupt')
$refused=$false;try{Get-LiveAuthMaterial ('A'*64)}catch{$refused=$true}
if(-not $refused -or [IO.File]::ReadAllText($p) -cne 'corrupt'){throw 'Corrupt retained store silently regenerated'}
'''.replace('DIRECTORY', directory))

    def test_world_readable_existing_secret_store_is_refused(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
if(-not $IsWindows){
 [IO.File]::SetUnixFileMode('DIRECTORY',[IO.UnixFileMode]493)
 $refused=$false;try{Assert-LiveSecretPermissions 'DIRECTORY'}catch{$refused=$true}
 if(-not $refused){throw 'Insecure existing secret store accepted'}
}
'''.replace('DIRECTORY', directory))

    def test_azure_cli_uses_name_and_group_and_secret_put_stays_in_memory(self):
        self.ps(r'''
$script:argv=[Collections.Generic.List[string]]::new()
function Invoke-CleanAzure($Arguments){
 $script:argv.Add(($Arguments -join '|'))
 if($Arguments[0] -eq 'account'){return @{accessToken='SYNTHETIC_SECRET_SENTINEL_BEARER';id=$script:CleanSubscription}}
 if($Arguments[2] -eq 'appsettings'){return @()}
 if($Arguments[2] -eq 'connection-string'){return [pscustomobject]@{}}
 return @{defaultHostName='shale-mcp-acceptance.azurewebsites.net';kind='app,linux'}
}
function Assert-CleanResources {}
function Invoke-RestMethod($Uri,$Method,$Headers,$Body,$ContentType,$TimeoutSec,$MaximumRedirection,$Verbose,$Debug,$ErrorAction){
 if($Uri -cne ('https://management.azure.com'+$script:LiveAppId+'/config/appsettings?api-version=2023-12-01') -or $Method -cne 'Put' -or $MaximumRedirection -ne 0){throw 'Wrong ARM target/method'}
 if(($Body|ConvertFrom-Json).properties.secret -cne 'SYNTHETIC_SECRET_SENTINEL'){throw 'Missing in-memory body'}
 return @{ignored='SYNTHETIC_SECRET_SENTINEL'}
}
[void](Get-LiveSettings);Assert-LiveConfigurationTarget
Invoke-LiveArm 'Settings' @{secret='SYNTHETIC_SECRET_SENTINEL'}
foreach($call in $script:argv){
 if($call -match 'appsettings|connection-string'){
  if($call -match '--ids' -or $call -notmatch '--name\|shale-mcp-acceptance\|--resource-group\|Shale'){throw 'CLI compatibility regression'}
 }
 if($call.Contains('SYNTHETIC_SECRET_SENTINEL')){throw 'Secret in process arguments'}
}
function Invoke-RestMethod {throw 'SYNTHETIC_SECRET_SENTINEL'}
$refused=$false;try{Invoke-LiveArm 'Settings' @{secret='SYNTHETIC_SECRET_SENTINEL'}}catch{$refused=$true;if($_.Exception.Message -cne 'ACCEPTANCE_CONFIGURATION_WRITE_FAILED'){throw 'Raw REST exception leaked'}}
if(-not $refused){throw 'REST failure accepted'}
''')

    def test_empty_settings_provision_and_readback_then_existing_campaign_is_not_started(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(self.credentials() + r'''
$script:settings=@{};$script:trace=[Collections.Generic.List[string]]::new()
function Assert-LiveConfigurationTarget {$script:trace.Add('target')}
function Get-LiveSettings {return $script:settings.Clone()}
function Get-LiveAuthMaterial {return $a}
function Invoke-LiveArm($Operation,$Properties){
 $script:trace.Add($Operation)
 if($Operation -ceq 'Settings'){$script:settings=$Properties.Clone()}
 if($Operation -ceq 'Startup' -and ($Properties.linuxFxVersion -cne 'JAVA|21-java21' -or $Properties.appCommandLine -cne 'java -jar /home/site/wwwroot/app.jar --spring.profiles.active=azure')){throw 'Wrong startup'}
}
function Assert-CleanResources {$script:trace.Add('stopped')}
function Assert-LiveAzureConfiguration {$script:trace.Add('readback')}
function Invoke-CleanSource {throw 'Configuration attempted SQL'}
Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'
if(($script:trace -join ',') -cne 'target,Settings,stopped,Startup,stopped,Https,readback'){throw 'Configuration phase order changed'}
Assert-LiveSettings $script:settings $c
$script:settings.ApplicationInsightsAgent_EXTENSION_VERSION='~3'
$script:trace.Clear()
Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'
if($script:settings.SHALE_AUTH_TOKEN_SECRET -cne $a.TokenSecret){throw 'Retry rotated authentication'}
if($script:settings.ApplicationInsightsAgent_EXTENSION_VERSION -cne '~3'){throw 'Unrelated Azure setting changed'}
'''.replace('DIRECTORY', directory))

    def test_configuration_failure_attempts_off_and_stop_without_touching_sql(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(self.credentials() + r'''
function Assert-LiveConfigurationTarget {}
function Get-LiveAuthMaterial {return $a}
function Get-LiveSettings {return @{}}
function Invoke-LiveArm {throw 'SYNTHETIC_SECRET_SENTINEL'}
function Set-LiveAppControls($Enabled,$Run){if($Enabled){throw 'Unapproved activation'};$script:off=$true}
function Invoke-CleanAzure($Arguments){if($Arguments[1] -cne 'stop'){throw 'Unexpected Azure call'};$script:stopped=$true}
$script:off=$false;$script:stopped=$false
$refused=$false;try{Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'}catch{$refused=$true;if($_.Exception.Message -cne 'ACCEPTANCE_CONFIGURATION_FAILED'){throw 'Raw failure leaked'}}
if(-not $refused -or -not $script:off -or -not $script:stopped){throw 'Configuration cleanup missing'}
'''.replace('DIRECTORY', directory))

    def test_active_or_conflicting_existing_settings_refused_before_azure_writes(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(self.credentials() + r'''
function Assert-LiveConfigurationTarget {}
function Get-LiveAuthMaterial {return $a}
function Invoke-LiveArm {throw 'Unsafe write reached'}
foreach($mode in @('active','legacy-active','wrong-database','unknown-jwt','alias')){
 $script:mode=$mode
 function Get-LiveSettings {
  switch($script:mode){
   'active'{return @{'SHALE_AI_ENABLED'='true'}}
   'legacy-active'{return @{'shale.ai.enabled'='true'}}
   'alias'{return @{'SHALE_AI_MCP_SEARCH_CASES_ENABLED'='true'}}
   'wrong-database'{return @{'SHALE_APP_DB_URL'='jdbc:sqlserver://shale.database.windows.net;databaseName=Shale'}}
   'unknown-jwt'{return @{'SHALE_AUTH_TOKEN_SECRET'='untrusted-existing-secret'}}
  }
 }
 $refused=$false;try{Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'}catch{$refused=$true}
 if(-not $refused -or (Test-Path 'DIRECTORY/journal.jsonl')){throw 'Conflict reached mutation journal'}
}
'''.replace('DIRECTORY', directory))

    def test_campaign_control_writes_keep_credentials_in_memory_and_out_of_control_file(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
function Get-LiveSettings {return @{SECRET='SYNTHETIC_SECRET_SENTINEL'}}
function Invoke-LiveArm($Operation,$Properties){
 if($Operation -cne 'Settings' -or $Properties.secret -cne 'SYNTHETIC_SECRET_SENTINEL'){throw 'Existing credentials lost'}
 foreach($key in $script:LiveControls){if($Properties[$key] -cne 'false'){throw 'Unexpected activation'}}
}
Set-LiveAppControls $false 'DIRECTORY'
if([IO.File]::ReadAllText('DIRECTORY/controls.json').Contains('SYNTHETIC_SECRET_SENTINEL')){throw 'Credential serialized in control evidence'}
'''.replace('DIRECTORY', directory))

    def test_single_operator_entrypoint_configures_before_campaign_approval(self):
        entry = (TOOL / 'Start-LiveRuntimeAcceptance.ps1').read_text()
        helper = (TOOL / 'LiveRuntime.ps1').read_text()
        config = (TOOL / 'LiveConfiguration.ps1').read_text()
        self.assertLess(entry.index('Invoke-LiveNativeChecks $specs'), entry.index('Initialize-LiveConfiguration'))
        self.assertLess(entry.index('Initialize-LiveConfiguration'), entry.index('Invoke-LiveCampaign'))
        self.assertEqual((entry + helper + config).count("Read-Host 'Type RUN_SHALE_MCP_ACCEPTANCE"), 1)
        for forbidden in ('Start-LiveWorker', 'Set-LiveSqlControls', 'CREATE USER', 'ALTER ROLE', 'DROP DATABASE'):
            self.assertNotIn(forbidden, config)
        self.assertIn('/work/live-mcp-configuration/', (ROOT / '.gitignore').read_text())
        self.assertNotIn('--settings', config)
        self.assertIn('CreateNew', config)


if __name__ == '__main__':
    unittest.main()
