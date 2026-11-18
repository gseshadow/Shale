"""Offline regression: all Azure/SQL/HTTP seams are replaced before invocation."""
import asyncio
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).parent
spec = importlib.util.spec_from_file_location('live_driver', ROOT / 'build/mcp-transport/live-azure-python.py')
live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live)


class LiveRuntimeTest(unittest.TestCase):
    def ps(self, body):
        result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command',
            "$ErrorActionPreference='Stop'; . ./build/azure-mcp-acceptance/LiveRuntime.ps1; " + body + '\nexit 0'],
            cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result.stdout

    def test_real_client_process_uses_one_python_path_and_utf8_stdin_without_network(self):
        # Exercise the real entrypoint/process pipes; only the first HTTP attempt is blocked.
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            root = Path(directory)
            parsed = root / 'input-ok'
            blocked = root / 'network-blocked'
            (root / 'sitecustomize.py').write_text(
                "import json,pathlib,urllib.request\n"
                "original=json.loads\n"
                "def loads(text,*args,**kwargs):\n"
                " result=original(text,*args,**kwargs)\n"
                " if isinstance(result,dict) and set(result)=={'admin@example.invalid','badmin@example.invalid','a72@example.invalid'}:\n"
                "  assert all(value=='synthetic_\\u03a9' for value in result.values())\n"
                f"  pathlib.Path({str(parsed)!r}).touch()\n"
                " return result\n"
                "json.loads=loads\n"
                "def deny(*args,**kwargs):\n"
                f" pathlib.Path({str(blocked)!r}).touch()\n"
                " raise RuntimeError('OFFLINE_NETWORK_BLOCKED')\n"
                "urllib.request.OpenerDirector.open=deny\n",
                encoding='utf-8')
            body = r"""
$run='DIRECTORY'
$python='PYTHON_EXECUTABLE'
function Get-Command($Name,$CommandType) {
 if($Name -cne 'python'){throw 'Unexpected executable lookup'}
 return @([pscustomobject]@{Source=$python},[pscustomobject]@{Source='SYNTHETIC_SECOND_EXECUTABLE'},[pscustomobject]@{Source='SYNTHETIC_THIRD_EXECUTABLE'})
}
$env:PYTHONPATH=$run
$credentials=@{}
foreach($email in @('admin@example.invalid','badmin@example.invalid','a72@example.invalid')){
 $credentials[$email]=[pscredential]::new($email,(ConvertTo-SecureString ('synthetic_'+[char]0x03a9) -AsPlainText -Force))
}
$code=$null;try{Invoke-LiveClient $run $credentials}catch{$code=$_.Exception.Message}
if($code -cne 'LIVE_CLIENT_FAILED'){throw 'Actual child process did not reach the intentionally blocked HTTP step'}
if((ConvertTo-LiveSafeCampaignCode $code) -cne $code){throw 'Safe client exit code was hidden'}
"""
            self.ps(body.replace('DIRECTORY', directory.replace("'", "''"))
                        .replace('PYTHON_EXECUTABLE', sys.executable.replace("'", "''")))
            self.assertTrue(parsed.exists(), 'Real child must receive the Unicode JSON correctly')
            self.assertTrue(blocked.exists(), 'Real entrypoint must reach the offline HTTP seam')
            report = json.loads((root / 'http-report.json').read_text())
            self.assertEqual(report['status'], 'FAIL')
            self.assertEqual(report['failedCheck'], 'https-discovery')
            self.assertTrue(report['credentialCleanup'])
            self.assertEqual(report['records'], [])
            self.assertNotIn('synthetic_', (root / 'http-report.json').read_text())

    def test_client_start_and_safe_failure_codes_do_not_expose_paths(self):
        self.ps(r"""
function Get-Command($Name,$CommandType){return @([pscustomobject]@{Source='PRIVATE_UNAVAILABLE_EXECUTABLE'},[pscustomobject]@{Source='PRIVATE_FALLBACK_EXECUTABLE'})}
$code=$null;try{Invoke-LiveClient 'unused' @{}}catch{$code=$_.Exception.Message}
if($code -cne 'LIVE_CLIENT_START_FAILED'){throw 'Process start must produce a closed failure code'}
foreach($code in @('LIVE_CLIENT_START_FAILED','LIVE_CLIENT_INPUT_FAILED','LIVE_CLIENT_FAILED','LIVE_CLIENT_TIMEOUT','LIVE_CLIENT_REPORT_FAILED')){
 if((ConvertTo-LiveSafeCampaignCode $code) -cne $code){throw 'Safe client failure code hidden'}
}
if((ConvertTo-LiveSafeCampaignCode 'LIVE_CLIENT_FAILED_PRIVATE_PAYLOAD') -cne 'CAMPAIGN_FAILED'){throw 'Private suffix accepted'}
""")

    def test_scripts_parse_and_import_without_network(self):
        self.ps(r'''
foreach($name in @('Start-LiveRuntimeAcceptance.ps1','LiveRuntime.ps1','LiveConfiguration.ps1')) {
 $errors=$null
 [void][Management.Automation.Language.Parser]::ParseFile((Join-Path ./build/azure-mcp-acceptance $name),[ref]$null,[ref]$errors)
 if($errors.Count){throw ($errors|Out-String)}
}
''')

    def test_worker_archives_both_log_scopes_and_reads_fresh_application_evidence_without_shell(self):
        self.ps(r'''
$script:paths=[Collections.Generic.List[string]]::new()
$script:paths.Add('/home/LogFiles/old_docker.log');$script:paths.Add('/home/LogFiles/Application/spring.old.log')
$script:archived=[Collections.Generic.List[string]]::new();$script:started=$false;$script:wrongHash=$false;$script:skipMove=$false
function Get-LiveWorkerLogPaths {return $script:paths.ToArray()}
function Invoke-LiveScm($Command){
 foreach($shellToken in @(([string][char]39),([string][char]34),'&&','*',';')){if($Command.Contains($shellToken)){throw 'SCM is not a shell'}}
 $argv=$Command.Split(' ')
 switch($argv[0]){
  'mkdir' {return ''}
  'mv' {if(-not $script:skipMove){[void]$script:paths.Remove($argv[2]);$script:archived.Add($argv[3])};return ''}
  'sha256sum' {if($argv[1] -cne '/home/site/wwwroot/app.jar'){throw 'Wrong deployed artifact path'};return ($(if($script:wrongHash){'B'}else{'A'})*64)+'  /home/site/wwwroot/app.jar'}
  'tail' {if(-not $script:paths.Contains($argv[3])){throw 'Read targeted an archived path'};if($argv[3].Contains('/Application/')){return 'SHALE_JVM_STARTUP zoneId=UTC javaVersion=21.0.8 replicaId=offline' + "`nAI_CASE_SQL_READINESS disabled"};return 'Platform startup succeeded'}
  default {throw 'Unexpected remote operation'}
 }
}
function Invoke-CleanAzure($Arguments){
 if($Arguments[1] -cne 'start' -or $script:paths.Count -ne 0){throw 'Started before stale logs were archived'}
 $script:started=$true;$script:paths.Add('/home/LogFiles/new_docker.log');$script:paths.Add('/home/LogFiles/Application/spring.new.log')
}
function Wait-LiveHealth {}
$archive='acceptance-'+('a'*32)+'-before-dormant'
Start-LiveWorker $archive
$markers=Assert-LiveWorker ('A'*64) 'UTC'
if(-not $script:started -or $script:archived.Count -ne 2 -or -not $script:archived.Contains('/home/LogFiles/'+$archive+'/Application/spring.old.log') -or $markers -cnotcontains 'AI_CASE_SQL_READINESS disabled'){throw 'Fresh application evidence or archive preservation missing'}
$script:wrongHash=$true;$refused=$false;try{Assert-LiveWorker ('A'*64) 'UTC'}catch{$refused=$true};if(-not $refused){throw 'Wrong deployed artifact accepted'}
$script:skipMove=$true;$script:started=$false;$refused=$false
try{Start-LiveWorker $archive}catch{$refused=$true;if($_.Exception.Message -cne 'WORKER_LOG_ARCHIVE_UNCONFIRMED'){throw 'Archive refusal diagnostic changed'}}
if(-not $refused -or $script:started){throw 'Unconfirmed stale-log archive allowed startup'}
''')

    def test_worker_log_inventory_uses_fixed_vfs_scopes_and_refuses_unsafe_names(self):
        self.ps(r'''
$script:unsafe=$false;$script:missingApplication=$false
function Invoke-CleanAzure {return @{accessToken='offline-not-a-secret'}}
function Invoke-RestMethod($Uri){
 if($Uri -ceq 'https://shale-mcp-acceptance.scm.azurewebsites.net/api/vfs/LogFiles/'){
  if($script:unsafe){return ,@([pscustomobject]@{name='../escape_docker.log'})}
  return ,@([pscustomobject]@{name='today_docker.log'},[pscustomobject]@{name='ignore.txt'})
 }
 if($Uri -ceq 'https://shale-mcp-acceptance.scm.azurewebsites.net/api/vfs/LogFiles/Application/'){
  if($script:missingApplication){
   $response=[Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::NotFound)
   throw [Microsoft.PowerShell.Commands.HttpResponseException]::new('offline', $response)
  }
  return ,@([pscustomobject]@{name='spring.worker.log'})
 }
 throw 'Unexpected VFS target'
}
$paths=@(Get-LiveWorkerLogPaths)
if($paths.Count -ne 2 -or $paths -cnotcontains '/home/LogFiles/Application/spring.worker.log' -or $paths -cnotcontains '/home/LogFiles/today_docker.log'){throw 'Real JSON array response did not inventory both scopes'}
$script:missingApplication=$true;if(@(Get-LiveWorkerLogPaths).Count -ne 1){throw 'Absent initial application directory was not handled'}
$script:unsafe=$true;$refused=$false;try{Get-LiveWorkerLogPaths}catch{$refused=$true;if($_.Exception.Message -cne 'WORKER_LOG_NAME_REFUSED'){throw 'Unsafe inventory diagnostic'}}
if(-not $refused){throw 'Unsafe remote path accepted'}
''')

    def test_campaign_diagnostics_preserve_only_closed_failure_codes(self):
        self.ps(r'''
foreach($code in @('HEALTH_TIMEOUT','WORKER_READINESS_EVIDENCE_MISSING','SCM_COMMAND_FAILED','CAMPAIGN_HTTP_401','PRIVATE_SENTINEL','CAMPAIGN_HTTP_401_PRIVATE_SENTINEL')){
 $expected=if($code.Contains('PRIVATE_SENTINEL')){'CAMPAIGN_FAILED'}else{$code}
 try{throw $code}catch{$safe=Get-LiveSafeCampaignFailure $_}
 if($safe -cne $expected){throw 'Safe campaign code changed or leaked raw error'}
}
$response=[Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::Forbidden)
try{throw [Microsoft.PowerShell.Commands.HttpResponseException]::new('PRIVATE_SENTINEL',$response)}catch{$safe=Get-LiveSafeCampaignFailure $_}
if($safe -cne 'CAMPAIGN_HTTP_403'){throw 'HTTP status was masked or raw error leaked'}
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path ./build/azure-mcp-acceptance 'Start-LiveRuntimeAcceptance.ps1'),[ref]$null,[ref]$null)
$entryTry=@($ast.EndBlock.Statements | Where-Object {$_ -is [Management.Automation.Language.TryStatementAst]})[0]
$block=$entryTry.CatchClauses[0].Body.Extent.Text;$handler=[scriptblock]::Create($block.Substring(1,$block.Length-2))
$campaign=$true;$result=@{FailureCode='WORKER_READINESS_EVIDENCE_MISSING'};$operation='controlled-campaign';$run='unused'
$refused=$false
try{try{throw 'ACCEPTANCE_FAILED'}catch{. $handler}}catch{$refused=$true;if(-not $_.Exception.Message.Contains($result.FailureCode) -or $_.Exception.Message.Contains('PREFLIGHT_FAILED')){throw 'Operator message masked the campaign failure'}}
if(-not $refused){throw 'Failed campaign did not fail the operator command'}
''')

    def test_policy_reader_preserves_nullable_binary_version_and_refuses_bad_results(self):
        self.ps(r'''
$script:setupSql=[Collections.Generic.List[string]]::new();$script:disposed=$false
function Invoke-CleanCommand($Connection,$Sql,$Lines){$script:setupSql.Add($Sql)}
function New-CleanConnection {
 $connection=[pscustomobject]@{State='Closed'}
 $connection | Add-Member ScriptMethod Open {$this.State='Open'}
 $connection | Add-Member ScriptMethod Dispose {$script:disposed=$true}
 $connection | Add-Member ScriptMethod CreateCommand {
  $cmd=[pscustomobject]@{CommandText='';CommandTimeout=0}
  $cmd | Add-Member ScriptMethod Dispose {}
  $cmd | Add-Member ScriptMethod ExecuteReader {
   if($this.CommandText -cne 'EXEC dbo.AiGetTenantPolicyV1;'){throw 'Policy getter transaction boundary changed'}
   return ,($script:policyTable.CreateDataReader())
  }
  return $cmd
 }
 return $connection
}
$credential=[pscredential]::new('mcpacc_runtime',(ConvertTo-SecureString 'offline-only' -AsPlainText -Force))
foreach($mode in @('absent','empty-off','empty-on','present','bad-length','duplicate','missing')){
 $script:disposed=$false;$script:setupSql.Clear()
 $script:policyTable=[Data.DataTable]::new()
 [void]$script:policyTable.Columns.Add('Enabled',[bool]);[void]$script:policyTable.Columns.Add('RowVer',[byte[]])
 if($mode -cne 'missing'){
  $row=$script:policyTable.NewRow();$row.Enabled=($mode -ceq 'empty-on')
  if($mode -ceq 'absent'){$row.RowVer=[DBNull]::Value}else{$row.RowVer=[byte[]]::new($(if($mode -ceq 'bad-length'){7}elseif($mode -cin @('empty-off','empty-on')){0}else{8}))}
  [void]$script:policyTable.Rows.Add($row)
  if($mode -ceq 'duplicate'){$script:policyTable.ImportRow($row)}
 }
 $refused=$false
 try{$version=Get-LiveTenantPolicyVersion 7 71 $credential}catch{$refused=$true}
 if($refused -ne ($mode -cin @('empty-on','bad-length','duplicate','missing'))){throw 'Policy result validation changed'}
 if(-not $refused -and $version -cne $(if($mode -cin @('absent','empty-off')){'NULL'}else{'0x0000000000000000'})){throw 'Binary/null version changed'}
 if(-not $script:disposed -or -not $script:setupSql[0].Contains('ShaleMcpAcceptance') -or -not $script:setupSql[1].Contains("@key=N'PrincipalUserId',@value=71")){throw 'Policy read skipped target/context/disposal'}
}
''')

    def test_tenant_activation_and_shutdown_use_procedure_only_policy_read(self):
        self.ps(r'''
$script:tenantSql=[Collections.Generic.List[string]]::new()
function Invoke-LiveOwner {}
function Get-LiveTenantPolicyVersion {return '0x0000000000000001'}
function Invoke-CleanSource($Sql,$Credential){$script:tenantSql.Add($Sql)}
foreach($enabled in @($true,$false)){
 $script:tenantSql.Clear()
 Set-LiveSqlControls $enabled $null @{'mcpacc_runtime'=$null}
 if($script:tenantSql.Count -ne 2){throw 'Both synthetic tenants must receive the policy operation'}
 foreach($sql in $script:tenantSql){
  if($sql.Contains('FROM dbo.TenantAiIntegrationPolicy') -or -not $sql.Contains('DECLARE @v varbinary(8)=0x0000000000000001')){throw 'Runtime crossed the procedure-only policy boundary'}
  if(-not $sql.Contains('@ExpectedRowVer=@v') -or -not $sql.Contains("@key=N'PrincipalUserId'")){throw 'Tenant actor or optimistic concurrency removed'}
 }
}
''')

    def test_before_activation_cleanup_verifies_sql_off_without_mutating_it(self):
        self.ps(r'''
function Set-LiveSqlControls {throw 'Pre-activation cleanup must not mutate SQL'}
function Set-LiveAppControls {}
function Get-LiveSettings {$s=@{};foreach($key in $script:LiveControls){$s[$key]='false'};$s['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED']='false';return $s}
function Invoke-CleanAzure {return @{id=$script:LiveAppId;state='Stopped'}}
foreach($off in @($true,$false)){
 $script:off=$off
 function Assert-LiveSqlOff {if(-not $script:off){throw 'SQL_OFF_NOT_VERIFIED'}}
 $r=Stop-LiveAcceptance $null @{} 'unused' -VerifySqlOnly
 if($r.SqlOff -ne $off -or -not $r.AppOff -or -not $r.ApplicationStopped){throw 'Read-only cleanup falsely certified or skipped independent channels'}
}
''')

    def test_independent_cleanup_survives_each_failed_channel(self):
        # A failed SQL shutdown must never suppress app OFF or Azure stop.
        self.ps(r'''
foreach($fail in @('none','sql','settings','stop')) {
 $script:fail=$fail;$script:trace=[Collections.Generic.List[string]]::new()
 function Set-LiveSqlControls {$script:trace.Add('sql');if($script:fail -eq 'sql'){throw 'secret SQL text'}}
 function Assert-LiveSqlOff {}
 function Set-LiveAppControls {$script:trace.Add('settings');if($script:fail -eq 'settings'){throw 'secret app settings'}}
 function Get-LiveSettings {$s=@{};foreach($key in $script:LiveControls){$s[$key]='false'};$s['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED']='false';return $s}
 function Invoke-CleanAzure($Arguments) {
  if($Arguments[1] -eq 'stop'){$script:trace.Add('stop');if($script:fail -eq 'stop'){throw 'secret Azure error'}}
  return @{id=$script:LiveAppId;state='Stopped'}
 }
 $r=Stop-LiveAcceptance $null @{} 'unused'
 if(($script:trace -join ',') -cne 'sql,settings,stop'){throw 'Cleanup was short-circuited'}
 if($r.SqlOff -ne ($fail -ne 'sql') -or $r.AppOff -ne ($fail -ne 'settings') -or $r.ApplicationStopped -ne ($fail -ne 'stop')){throw 'Cleanup falsely certified'}
}
''')

    def test_sql_shutdown_attempts_all_switches_and_both_tenants(self):
        self.ps(r'''
$script:ownerCalls=0;$script:tenantCalls=0;$script:productSql=''
function Invoke-LiveOwner($Sql,$Owner) {$script:ownerCalls++;if($Sql.Contains('UPDATE dbo.AiProductCapability')){$script:productSql=$Sql};throw 'simulated single control failure'}
function Get-LiveTenantPolicyVersion {return 'NULL'}
function Invoke-CleanSource($Sql,$Credential) {$script:tenantCalls++;throw 'simulated tenant failure'}
$refused=$false;try{Set-LiveSqlControls $false $null @{'mcpacc_runtime'=$null}}catch{$refused=$true}
if(-not $refused -or $script:ownerCalls -ne 5 -or $script:tenantCalls -ne 2){throw 'SQL cleanup short-circuited'}
$sql=$script:productSql
if($sql.IndexOf('BEGIN TRANSACTION') -ge $sql.IndexOf('UPDATE dbo.AiProductCapability') -or $sql.IndexOf('INSERT dbo.GlobalControlPlaneAuditLog') -le $sql.IndexOf('UPDATE dbo.AiProductCapability') -or -not $sql.EndsWith('COMMIT;') -or -not $sql.Contains('AI_DISABLED') -or -not $sql.Contains('sp_getapplock')){throw 'Product control lost atomic audit or lock'}
''')

    def test_approval_decline_and_every_failure_path_report_and_cleanup(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            # Mutation seams are local mocks; even deployment is never sent to az.
            self.ps(r'''
$script:failure='';$script:trace=[Collections.Generic.List[string]]::new()
function Hit($Name){$script:trace.Add($Name);if($script:failure -ceq $Name){throw 'PRIVATE_SENTINEL'}}
function Read-Host {Hit 'approval';if($script:failure -eq 'decline'){return 'no'};return 'RUN_SHALE_MCP_ACCEPTANCE'}
function Assert-LiveAzureConfiguration {Hit 'preflight'}
function Assert-LiveSettings {}
function Get-LiveSettings {return @{}}
function Assert-LiveSqlOff {}
function Assert-LiveSynthetic {}
function Assert-LiveJar {}
function Invoke-CleanAzure($Arguments) {if($Arguments[1] -eq 'deploy'){Hit 'deploy'};return @{state='Stopped'}}
function Start-LiveWorker {Hit 'start'}
function Assert-LiveWorker($Hash,$JavaZone,[switch]$Ready){Hit 'worker';if($Ready){return 'AI_CASE_SQL_READINESS ready'};return 'AI_CASE_SQL_READINESS disabled'}
function Invoke-WebRequest {return @{StatusCode=404}}
function Assert-CleanResources {}
function Get-LiveBinding {Hit 'binding'}
function Invoke-LiveRls {Hit 'rls'}
function Invoke-LiveNativeChecks {Hit 'native'}
function Get-LiveAuditBaseline {return @{Access=0;Phi=0}}
function Set-LiveSqlControls {Hit 'sql-on'}
function Set-LiveAppControls {Hit 'app-on'}
function Invoke-LiveClient {Hit 'client';return @{disclosures=8;overviews=6}}
function Assert-LiveAudits {Hit 'audits'}
function Stop-LiveAcceptance($Owner,$Credentials,$Run,[switch]$VerifySqlOnly) {$script:verifySqlOnly=[bool]$VerifySqlOnly;Hit 'cleanup';return @{SqlOff=($script:failure -ne 'cleanup-fail');AppOff=$true;ApplicationStopped=$true}}
foreach($failure in @('decline','preflight','deploy','start','worker','binding','rls','native','sql-on','app-on','client','audits','cleanup-fail','none')) {
 $script:failure=$failure;$script:trace.Clear()
 $dir=Join-Path 'DIRECTORY' $failure;[void][IO.Directory]::CreateDirectory($dir)
 $ctx=@{Run=$dir;Hash=('A'*64);Version='1.0.0';Id='offline';Jar='unused';Zones=@{Java='UTC'};Evidence=@{Directory='unused';JournalHash=('B'*64)};Credentials=@{};Humans=@{};Owner=$null;Inventory=@();Specs=@()}
 $r=Invoke-LiveCampaign $ctx
 if(($r.Status -ceq 'PASS') -ne ($failure -ceq 'none')){throw "False campaign result: $failure"}
 if($failure -ceq 'decline') {if($script:trace.Contains('deploy') -or $script:trace.Contains('cleanup')){throw 'Unapproved mutation'}}
 elseif(-not $script:trace.Contains('cleanup')){throw "Cleanup absent after $failure"}
 if($failure -cne 'decline'){$beforeActivation=$failure -cin @('preflight','deploy','start','worker','binding','rls','native');if($script:verifySqlOnly -ne $beforeActivation){throw 'Partial activation or dormant failure used the wrong SQL cleanup mode'}}
 if([IO.File]::ReadAllText((Join-Path $dir 'report.json')).Contains('PRIVATE_SENTINEL')){throw 'Sensitive failure leaked'}
}
'''.replace('DIRECTORY', directory))

    def test_runtime_settings_refuse_wrong_database_identity_and_override(self):
        self.ps(r'''
$s=@{'WEBSITES_CONTAINER_START_TIME_LIMIT'='1800';'SPRING_PROFILES_ACTIVE'='azure';'shale.ai.oauth.identity'='PRODUCTION';'shale.ai.oauth.public-origin'=$script:LiveOrigin;'shale.ai.oauth.browser-origin'=$script:LiveOrigin;'shale.ai.mcp.allowed-origins'=$script:LiveOrigin;'shale.ai.data.database'='ShaleMcpAcceptance';'shale.ai.data.contract-version'='V2';'shale.ai.cimd.contract-version'='DCR_V3';'shale.ai.acceptance.slow-verification'='true';'SHALE_AUTH_TOKEN_SECRET'=('x'*40);'SHALE_AUTH_SESSION_BINDING_CUTOVER_AT'='2026-10-01T00:00:00Z'}
foreach($k in $script:LiveControls){$s[$k]='false'};$s['SHALE_AI_CIMD_METADATAADVERTISEMENTENABLED']='false'
$prefixes=@{'SHALE_APP'='auth';'SHALE_RT'='runtime';'shale.ai.data'='predecessor';'shale.ai.data.verifier'='verifier';'shale.ai.oauth.bootstrap'='bootstrap'}
foreach($key in @('case','reviewer','publisher','approval','onboarding','browser','issuer','metadata','resolver','neutral','dcr-endpoint','dcr-metadata')){$prefixes['shale.ai.cimd.runtime.'+$key]=$key.Replace('-','_')}
$c=@{}
foreach($p in $prefixes.Keys){
 $name='mcpacc_'+$prefixes[$p];$c[$name]=[pscredential]::new($name,(ConvertTo-SecureString ('offline-secret-'+$name) -AsPlainText -Force))
 $ordinary=$p.StartsWith('SHALE_');$u=if($ordinary){$p+'_DB_USER'}else{$p+'.username'};$pw=if($ordinary){$p+'_DB_PASSWORD'}else{$p+'.password'};$url=if($ordinary){$p+'_DB_URL'}else{$p+'.jdbc-url'}
 $s[$u]=$name;$s[$pw]=$c[$name].GetNetworkCredential().Password;$s[$url]='jdbc:sqlserver://shale.database.windows.net:1433;databaseName=ShaleMcpAcceptance'
}
$s=ConvertTo-LiveEnvironmentSettings $s
Assert-LiveSettings $s $c
foreach($test in @(@('SHALE_APP_DB_URL','jdbc:sqlserver://shale.database.windows.net;databaseName=Shale'),@('SHALE_AI_CIMD_RUNTIME_CASE_USERNAME','mcpacc_runtime'),@('SHALE_AI_DATA_PASSWORD','wrong'),@('JAVA_TOOL_OPTIONS','-Dshale.ai.enabled=true'),@('SHALE_AI_ENABLED','true'),@('SPRING_PROFILES_ACTIVE','azure,dev'),@('SHALE_AI_CIMD_CONTRACTVERSION','EXTERNAL_V2'),@('SHALE_AI_MCP_ENABLED','true'))){
 $copy=$s.Clone();$copy[$test[0]]=$test[1];$refused=$false
 try{Assert-LiveSettings $copy $c}catch{$refused=$true}
 if(-not $refused){throw ('Unsafe configuration accepted: '+$test[0])}
}
''')

    def test_incomplete_or_tampered_sql_history_is_never_adopted(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$d='DIRECTORY'
[IO.File]::WriteAllText((Join-Path $d 'report.json'),'{}')
$refused=$false;try{Read-LiveSqlEvidence $d}catch{$refused=$true};if(-not $refused){throw 'Incomplete SQL accepted'}
[IO.File]::WriteAllText((Join-Path $d 'report.json'),'{{"Status":"complete","Completed":51,"Total":51,"Application":"Stopped","Activation":"OFF"}}'.Replace('{{','{').Replace('}}','}'))
New-CleanEvent (Join-Path $d 'journal.jsonl') @{Kind='complete';Completed=51}
$refused=$false;try{Read-LiveSqlEvidence $d}catch{$refused=$true};if(-not $refused){throw 'Missing Stage21 accepted'}
$path=Join-Path $d 'journal.jsonl';$original=[IO.File]::ReadAllText($path);[IO.File]::WriteAllText($path,$original.Replace('complete','tampered'))
$refused=$false;try{Read-LiveSqlEvidence $d}catch{$refused=$true};if(-not $refused){throw 'Tampering accepted'}
'''.replace('DIRECTORY', directory))

    def test_complete_sql_history_preserves_nonzero_verified_finding_column_counts(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$d='DIRECTORY';$rendered=Join-Path $d 'rendered-synthetic';[void][IO.Directory]::CreateDirectory($rendered)
$stages=@(Get-Content ./build/azure-mcp-acceptance/stages.json -Raw|ConvertFrom-Json)
$manifest=@();$n=0
foreach($stage in $stages){
 $n++;$file=Join-Path $rendered ($stage.name+'.sql');[IO.File]::WriteAllText($file,('SELECT '+$n+';'))
 $hash=(Get-FileHash $file).Hash;$manifest+=[pscustomobject]@{Stage=$stage.name;SHA256=$hash}
 # PASS's last integer is a count of verified zero-valued columns. Stage 31 legitimately has 2.
 $findings=if($stage.name -ceq '31-verifier-catalogs'){2}else{0}
 New-CleanEvent (Join-Path $d 'journal.jsonl') @{Kind='stage-complete';Stage=$stage.name;SHA256=$hash;Completed=$n;Result=('PASS:'+@($stage.sources).Count+':'+$findings)}
}
$manifest|Export-Csv (Join-Path $rendered 'sha256.csv') -NoTypeInformation
New-CleanEvent (Join-Path $d 'journal.jsonl') @{Kind='complete';Completed=51;Application='Stopped';Activation='OFF'}
[IO.File]::WriteAllText((Join-Path $d 'report.json'),'{{"Status":"complete","Completed":51,"Total":51,"Application":"Stopped","Activation":"OFF"}}'.Replace('{{','{').Replace('}}','}'))
$before=(Get-FileHash (Join-Path $d 'journal.jsonl')).Hash
$result=Read-LiveSqlEvidence $d
if($result.Files.Count -ne 51 -or (Get-FileHash (Join-Path $d 'journal.jsonl')).Hash -cne $before){throw 'Valid existing SQL evidence rejected or modified'}
'''.replace('DIRECTORY', directory))

    def test_native_checks_cannot_execute_retained_sql_or_installers(self):
        self.ps(r'''
$script:seen=[Collections.Generic.List[string]]::new()
function Invoke-CleanStage($Stage,$Sql,$Credential){$script:seen.Add($Sql);return 'PASS:1:0'}
$specs=@(Get-Content ./build/azure-mcp-acceptance/stages.json -Raw|ConvertFrom-Json)
$c=@{};foreach($spec in $specs){if($spec.PSObject.Properties.Name -contains 'user'){$c[$spec.user]=$null}}
Invoke-LiveNativeChecks $specs $c
if($script:seen.Count -ne 19){throw 'Required native checks missing'}
$bad=[pscustomobject]@{name='31-bad';user='mcpacc_verifier';sources=@('docs/sql/ai-integration/universal-pr4a-proof.sql')}
$refused=$false;try{Invoke-LiveNativeChecks @($bad) $c}catch{$refused=$true};if(-not $refused){throw 'Installer path accepted'}
''')

    def test_jar_requires_reviewed_hash_build_and_no_fixture_classes(self):
        import hashlib
        import zipfile
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            jar = Path(directory) / 'candidate.jar'
            def write(fixture=False):
                with zipfile.ZipFile(jar, 'w') as z:
                    z.writestr('BOOT-INF/classes/com/shale/server/ShaleServerApplication.class', b'offline')
                    z.writestr('BOOT-INF/classes/com/shale/server/oauth/CimdRuntimeConfiguration.class', b'offline')
                    z.writestr('META-INF/build-info.properties', 'build.artifact=shale-server\nbuild.version=1.2.3\n')
                    if fixture:
                        z.writestr('BOOT-INF/classes/LiveMetadata.class', b'offline')
                return hashlib.sha256(jar.read_bytes()).hexdigest()
            digest = write()
            self.ps(f"Assert-LiveJar '{jar}' '{digest}' '1.2.3'")
            self.ps(f"$refused=$false;try{{Assert-LiveJar '{jar}' '{digest}' 'wrong'}}catch{{$refused=$true}};if(-not $refused){{throw 'Wrong build accepted'}}")
            digest = write(True)
            self.ps(f"$refused=$false;try{{Assert-LiveJar '{jar}' '{digest}' '1.2.3'}}catch{{$refused=$true}};if(-not $refused){{throw 'Fixture accepted'}}")

    def test_jar_server_1_0_138_root_metadata_preserves_artifact_hash_version_and_class_guards(self):
        # The shipped executable keeps build-info at the archive root; classes stay under BOOT-INF.
        import hashlib
        import zipfile
        classes = (
            'BOOT-INF/classes/com/shale/server/ShaleServerApplication.class',
            'BOOT-INF/classes/com/shale/server/oauth/CimdRuntimeConfiguration.class',
        )
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            jar = Path(directory) / 'shale-server-1.0.138.jar'
            cases = [
                ('root-metadata', classes, 'shale-server', '1.0.138', 'META-INF/build-info.properties', False, None),
                ('wrong-hash', classes, 'shale-server', '1.0.138', 'META-INF/build-info.properties', True, 'EXACT_JAR_REQUIRED'),
                ('wrong-artifact', classes, 'shale-desktop', '1.0.138', 'META-INF/build-info.properties', False, 'BUILD_INFO_MISMATCH'),
                ('wrong-version', classes, 'shale-server', '1.0.137', 'META-INF/build-info.properties', False, 'BUILD_INFO_MISMATCH'),
                ('missing-application-class', classes[1:], 'shale-server', '1.0.138', 'META-INF/build-info.properties', False, 'EXECUTABLE_SERVER_JAR_REQUIRED'),
                ('missing-runtime-class', classes[:1], 'shale-server', '1.0.138', 'META-INF/build-info.properties', False, 'EXECUTABLE_SERVER_JAR_REQUIRED'),
                ('missing-root-metadata', classes, 'shale-server', '1.0.138', 'BOOT-INF/classes/META-INF/build-info.properties', False, 'EXECUTABLE_SERVER_JAR_REQUIRED'),
            ]
            for name, entries, artifact, version, metadata, wrong_hash, expected_error in cases:
                with self.subTest(name=name):
                    with zipfile.ZipFile(jar, 'w') as z:
                        for entry in entries:
                            z.writestr(entry, b'offline synthetic class placeholder')
                        z.writestr(metadata, f'build.artifact={artifact}\nbuild.version={version}\n')
                    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
                    if wrong_hash:
                        digest = ('0' if digest[0] != '0' else '1') + digest[1:]
                    if expected_error is None:
                        self.ps(f"Assert-LiveJar '{jar}' '{digest}' '1.0.138'")
                    else:
                        self.ps(f"$refused=$false;try{{Assert-LiveJar '{jar}' '{digest}' '1.0.138'}}"
                                f"catch{{$refused=$true;if($_.Exception.Message -cne '{expected_error}'){{throw 'Unexpected rejection'}}}};"
                                "if(-not $refused){throw 'Invalid JAR accepted'}")

    def test_live_client_failure_always_scrubs_and_revokes(self):
        with tempfile.TemporaryDirectory() as directory:
            a = live.Acceptance(directory, {})
            a.web = ['PRIVATE_WEB_SENTINEL']
            a.families = [('dcr.synthetic', {'access_token': 'PRIVATE_ACCESS_SENTINEL'})]
            async def fail():
                a.current = 'oauth-s256-state-code-replay'
                raise RuntimeError('PRIVATE_EXCEPTION_SENTINEL')
            a.execute = fail
            calls = []
            a.ok = lambda *args, **kwargs: calls.append(args)
            self.assertEqual(asyncio.run(a.run_all()), 1)
            self.assertEqual([c[0] for c in calls], ['/oauth/mcp/revoke', '/api/auth/logout'])
            report = (Path(directory) / 'http-report.json').read_text()
            self.assertNotIn('PRIVATE_', report)
            self.assertEqual(json.loads(report)['status'], 'FAIL')

    def test_live_client_cleanup_failure_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            a = live.Acceptance(directory, {})
            async def success():
                a.web.append('synthetic-token')
            a.execute = success
            def fail(*args, **kwargs):
                raise OSError('secret error')
            a.ok = fail
            self.assertEqual(asyncio.run(a.run_all()), 1)
            self.assertFalse(json.loads((Path(directory) / 'http-report.json').read_text())['credentialCleanup'])

    def test_callback_and_local_paths_cannot_substitute_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            a = live.Acceptance(directory, {})
            for path in ('https://production.invalid', '//production.invalid', 'mcp'):
                with self.assertRaises(AssertionError):
                    a.request(path)

    def test_single_approval_and_no_destructive_entrypoint(self):
        helper = (TOOL / 'LiveRuntime.ps1').read_text()
        entry = (TOOL / 'Start-LiveRuntimeAcceptance.ps1').read_text()
        self.assertEqual(helper.count("Read-Host 'Type RUN_SHALE_MCP_ACCEPTANCE"), 1)
        self.assertNotIn('Read-Host', helper.split('function Set-LiveSqlControls')[1].split('function Assert-LiveSqlOff')[0])
        for forbidden in ('CleanSlateReset', 'Start-CleanSlateAcceptance', 'Prepare-Packet.ps1', 'ALTER ROLE', 'CREATE USER', 'DROP DATABASE'):
            self.assertNotIn(forbidden, helper + entry)
        self.assertIn('Invoke-LiveNativeChecks $specs $credentials', entry)
        self.assertIn('finally {', helper)
        self.assertIn('Stop-LiveAcceptance $Context.Owner', helper)


if __name__ == '__main__':
    unittest.main()
