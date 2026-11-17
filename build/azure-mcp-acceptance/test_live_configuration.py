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
if(@($s.Keys|Where-Object {$_ -match '\.password$|_DB_PASSWORD$'}).Count -ne 17){throw 'Missing dedicated credentials'}
if(@($script:LiveControls|Where-Object {$s[$_] -cne 'false'}).Count -or $s['shale.ai.cimd.metadata-advertisement-enabled'] -cne 'false'){throw 'Configuration enabled AI'}
if($s.SHALE_AUTH_TOKEN_SECRET -cin @($c.Values|ForEach-Object {$_.GetNetworkCredential().Password})){throw 'JWT reused SQL credential'}
if($s.SHALE_ALLOWED_CORS_ORIGINS -cne $script:LiveOrigin -or $s.SHALE_AUTH_TOKEN_TTL_SECONDS -cne '3600'){throw 'Ordinary auth/CORS missing'}
$c.Remove('mcpacc_case');$refused=$false;try{New-LiveSettings $c $a}catch{$refused=$true};if(-not $refused){throw 'Missing SQL credential accepted'}
''')

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
 if($Operation -ceq 'Startup' -and ($Properties.linuxFxVersion -cne 'JAVA|21-java21' -or $Properties.appCommandLine -cne 'java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure')){throw 'Wrong startup'}
}
function Assert-CleanResources {$script:trace.Add('stopped')}
function Assert-LiveAzureConfiguration {$script:trace.Add('readback')}
function Invoke-CleanSource {throw 'Configuration attempted SQL'}
Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'
if(($script:trace -join ',') -cne 'target,Settings,stopped,Startup,stopped,Https,readback'){throw 'Configuration phase order changed'}
Assert-LiveSettings $script:settings $c
$script:trace.Clear()
Initialize-LiveConfiguration $c ('A'*64) 'DIRECTORY'
if($script:settings.SHALE_AUTH_TOKEN_SECRET -cne $a.TokenSecret){throw 'Retry rotated authentication'}
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
foreach($mode in @('active','wrong-database','unknown-jwt')){
 $script:mode=$mode
 function Get-LiveSettings {
  switch($script:mode){
   'active'{return @{'shale.ai.enabled'='true'}}
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
function Get-LiveSettings {return @{secret='SYNTHETIC_SECRET_SENTINEL'}}
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
