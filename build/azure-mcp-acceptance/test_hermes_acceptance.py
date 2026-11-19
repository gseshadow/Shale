"""Offline tests: no Azure, SQL, OAuth, or model calls."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml
import zipfile
ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).parent
spec = importlib.util.spec_from_file_location("profile_builder", TOOL / "prepare-hermes-profile.py")
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)

build_spec=importlib.util.spec_from_file_location("browser_builder", TOOL / "prepare-hermes-browser.py")
browser_builder=importlib.util.module_from_spec(build_spec)
build_spec.loader.exec_module(browser_builder)

summary_spec=importlib.util.spec_from_file_location("client_summary", TOOL / "summarize-hermes-client.py")
client_summary=importlib.util.module_from_spec(summary_spec)
summary_spec.loader.exec_module(client_summary)

class HermesAcceptanceTest(unittest.TestCase):
    def test_client_summary_keeps_only_closed_events_and_expiry_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            run=Path(tmp);private=run/'hermes-private';(private/'logs').mkdir(parents=True);(private/'mcp-tokens').mkdir()
            (private/'logs/agent.log').write_text('2026-11-19 16:09:04 legacy handshake rejected SENSITIVE_CODE SENSITIVE_URL\nSENSITIVE_PROMPT\n2026-11-19 16:10:00 initial connection failed SENSITIVE_TOKEN',encoding='utf-8')
            (private/'mcp-tokens/shale-acceptance.json').write_text(json.dumps({'access_token':'SENSITIVE_TOKEN','refresh_token':'SENSITIVE_REFRESH','expires_in':300,'expires_at':1900000000,'client_id':'SENSITIVE_CLIENT'}))
            result=client_summary.summarize(run)
            self.assertNotIn('SENSITIVE',json.dumps(result));self.assertEqual(['LEGACY_TO_DISCOVER','CONNECT_RETRY'],[x['Code'] for x in result['Events']])
            self.assertEqual('2026-11-19 16:09:04',result['Events'][0]['LogTime']);self.assertEqual(300,result['TokenMetadata']['expires_in']);self.assertTrue(result['TokenMetadata']['RefreshPresent'])
            self.assertEqual({'FileModifiedUtc','RefreshPresent','expires_in','expires_at'},set(result['TokenMetadata']))
    def test_client_summary_cli_writes_only_a_sanitized_supplement(self):
        with tempfile.TemporaryDirectory() as tmp:
            run=Path(tmp);original=run/'report.json';original.write_text('{"Status":"FAIL"}',encoding='utf-8')
            result=subprocess.run([__import__('sys').executable,'-I',str(TOOL/'summarize-hermes-client.py'),str(run)],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr);self.assertEqual('',result.stdout)
            self.assertEqual('{"Status":"FAIL"}',original.read_text())
            self.assertFalse(json.loads((run/'client-summary.json').read_text())['ProfilePresent'])
    def test_empty_profile_summary_does_not_invent_authentication(self):
        with tempfile.TemporaryDirectory() as tmp:
            result=client_summary.summarize(Path(tmp));self.assertFalse(result['ProfilePresent']);self.assertEqual({},result['TokenMetadata']);self.assertEqual([],result['Events'])
    def test_builder_selects_versioned_maven_artifact_and_rejects_missing_runtime(self):
        with tempfile.TemporaryDirectory() as tmp:
            target=Path(tmp)
            (target/'shale-server.jar').write_bytes(b'stale-unversioned-copy')
            candidate=target/'shale-server-1.0.138.jar'
            entries=['BOOT-INF/classes/com/shale/server/ShaleServerApplication.class',
                     'BOOT-INF/classes/com/shale/server/oauth/CimdRuntimeConfiguration.class',
                     'BOOT-INF/classes/com/shale/server/config/AcceptanceBrowserConfiguration.class',
                     'META-INF/build-info.properties']
            with zipfile.ZipFile(candidate,'w') as jar:
                for name in entries: jar.writestr(name,b'synthetic')
            self.assertEqual(candidate,browser_builder.server_artifact(target,'1.0.138'))
            with zipfile.ZipFile(candidate,'w') as jar: jar.writestr(entries[0],b'synthetic')
            with self.assertRaisesRegex(RuntimeError,'CURRENT_EXECUTABLE_SERVER_REQUIRED'):
                browser_builder.server_artifact(target,'1.0.138')
    def test_local_model_profile_excludes_credentials_and_other_servers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); source = root / "source.yaml"
            source.write_text(yaml.safe_dump({"model": {"default": "qwen3-4b", "provider": "custom", "base_url": "http://127.0.0.1:1234/v1", "api_key": "synthetic-excluded"}, "mcp_servers": {"other": {}}, "secret": "synthetic-excluded"}), encoding="utf-8")
            profile.prepare(source, root / "private")
            actual = yaml.safe_load((root / "private/config.yaml").read_text())
            self.assertNotIn("api_key", actual["model"])
            self.assertNotIn("mcp_servers", actual)
            self.assertNotIn("synthetic-excluded", str(actual))
    def test_remote_or_credential_bearing_model_endpoints_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for endpoint in ("https://provider.invalid/v1", "http://user:password@localhost/v1", "http://localhost/v1?key=secret"):
                source=root / "source.yaml"
                source.write_text(yaml.safe_dump({"model": {"default": "qwen", "base_url": endpoint}}))
                with self.assertRaisesRegex(RuntimeError, "LOOPBACK_MODEL_REQUIRED"):
                    profile.prepare(source, root / "private")
                self.assertFalse((root / "private").exists())
    def campaign(self, phrase, complete=False, interrupt=False, affinity_rejected=False):
        with tempfile.TemporaryDirectory() as tmp:
            run=Path(tmp).as_posix()
            script = f"""
$ErrorActionPreference='Stop'
. ./build/azure-mcp-acceptance/HermesAcceptance.ps1
$script:LiveAppId='acceptance';$script:Events=[Collections.Generic.List[string]]::new()
function Read-Host {{ '{phrase}' }}
function New-CleanEvent {{ param($p,$e) }}
function Get-LiveSafeCampaignFailure {{ param($e) 'PREFLIGHT_FAILED' }}
function Get-LiveCampaignDiagnostic {{ param($e) @{{Kind='TEST';Message=$e.Exception.Message}} }}
function Assert-LiveAzureConfiguration {{ $script:Events.Add('preflight'); throw 'TEST_PREFLIGHT_STOP' }}
function Stop-LiveAcceptance {{ param($owner,$creds,$run,[switch]$VerifySqlOnly) $script:Events.Add('cleanup'); @{{SqlOff=$true;AppOff=$true;ApplicationStopped=$true}} }}
$context=@{{Run='{run}';Inventory=@();Hash='synthetic';Version='1';Evidence=@{{JournalHash='synthetic'}}}}
$result=Invoke-HermesAcceptanceWindow $context
@{{Approved=$result.Approved;Status=$result.Status;Failure=$result.Failure;Diagnostic=$result.FailureDiagnostic;Events=$script:Events.ToArray()}} | ConvertTo-Json -Compress
"""
            if complete:
                script=script.replace("$result=Invoke-HermesAcceptanceWindow $context", """
function Assert-LiveAzureConfiguration { $script:Events.Add('preflight') }
function Assert-LiveSettings { param($a,$b) };function Get-LiveSettings { @{} }
function Assert-LiveSqlOff { param($x) };function Assert-LiveSynthetic { param($x) }
function Assert-LiveJar { param($a,$b,$c) };function Assert-CleanResources {}
function Invoke-CleanAzure { param($a) if($a[1] -ceq 'update'){$script:Events.Add('affinity-disabled')};@{state='Stopped';clientAffinityEnabled=$false} }
function Start-LiveWorker { param($a) };function Assert-LiveWorker { param($a,$b,[switch]$Ready) if($Ready){'ready'}else{'AI_CASE_SQL_READINESS disabled'} }
function Invoke-WebRequest { param($Uri,$Method,$ContentType,$Body,[switch]$SkipHttpErrorCheck,$MaximumRedirection,$TimeoutSec,$Headers) if($Uri.EndsWith('/login')){@{StatusCode=200;Content='<div id="root">';Headers=@{}}}elseif($script:Events.Contains('app-enabled')){if($Headers.Accept -cne 'application/json'){throw 'JSON_CHALLENGE_REQUIRED'};@{StatusCode=401;Headers=@{}}}else{@{StatusCode=404;Headers=@{}}} }
function Get-LiveBinding { param($a,$b) };function Invoke-LiveRls { param($a) }
function Invoke-LiveNativeChecks { param($a,$b,$c) };function Get-LiveAuditBaseline { param($a) @{Access=1;Phi=1} }
function Set-LiveSqlControls { param($enabled,$a,$b) $script:Events.Add('sql-enabled') }
function Set-LiveAppControls { param($enabled,$run) $script:Events.Add('app-enabled') }
function Invoke-HermesInteractiveProcess { param($Arguments,$TimeoutMilliseconds,$FailurePrefix) if($Arguments[0] -ceq 'mcp'){$script:Events.Add('hermes-connect')}else{$script:Events.Add('hermes-chat')} }
$script:LiveOrigin='https://acceptance.invalid';$context.Zones=@{Java='UTC'};$context.Id='synthetic'
$result=Invoke-HermesAcceptanceWindow $context
""")
            if affinity_rejected:
                script=script.replace("clientAffinityEnabled=$false", "clientAffinityEnabled=$true")
            if interrupt:
                script=script.replace("$script:LiveOrigin='https://acceptance.invalid'", "function Invoke-HermesInteractiveProcess { param($Arguments,$TimeoutMilliseconds,$FailurePrefix) exit 0 }; $script:LiveOrigin='https://acceptance.invalid'")
            result=subprocess.run(['pwsh','-NoProfile','-NonInteractive','-Command',script],cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            if interrupt:
                return json.loads((Path(tmp)/'report.json').read_text(encoding='utf-8'))
            return json.loads(result.stdout.splitlines()[-1])
    def ps(self, body):
        result=subprocess.run(['pwsh','-NoProfile','-NonInteractive','-Command',"$ErrorActionPreference='Stop'; . ./build/azure-mcp-acceptance/HermesProcess.ps1; "+body+"\nexit 0"],cwd=ROOT,capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        return result.stdout
    def test_interactive_process_watchdog_kills_stalled_connection_and_chooses_one_executable(self):
        self.ps("""
$script:killed=$false;$script:disposed=$false
function Get-Command { param($a,$CommandType) [pscustomobject]@{Source='first.exe'};[pscustomobject]@{Source='other.cmd'} }
function Start-Process {
 param($FilePath,$ArgumentList,[switch]$NoNewWindow,[switch]$PassThru)
 if($FilePath -cne 'first.exe'){throw 'SINGLE_EXECUTABLE_REQUIRED'}
 $p=[pscustomobject]@{HasExited=$false;ExitCode=0}
 $p|Add-Member ScriptMethod WaitForExit {param($milliseconds) $false}
 $p|Add-Member ScriptMethod Kill {param($tree) if(-not $tree){throw 'TREE_REQUIRED'};$script:killed=$true;$this.HasExited=$true}
 $p|Add-Member ScriptMethod Dispose {$script:disposed=$true}
 $p
}
try { Invoke-HermesInteractiveProcess @('mcp','add') 0 'HERMES_CONNECTION';throw 'TIMEOUT_REQUIRED' }
catch { if($_.Exception.Message -cne 'HERMES_CONNECTION_TIMEOUT'){throw} }
if(-not $script:killed -or -not $script:disposed){throw 'CHILD_CLEANUP_REQUIRED'}
""")
    def test_azure_transport_uses_installed_python_with_argument_list_not_batch_shell(self):
        self.ps("""
function Invoke-HermesCapturedProcess {
 param($StartInfo)
 if(-not $StartInfo.FileName.EndsWith('python.exe')){throw 'PYTHON_ENTRY_REQUIRED'}
 if($StartInfo.ArgumentList[0] -cne '-IBm' -or $StartInfo.ArgumentList[1] -cne 'azure.cli'){throw 'CLI_MODULE_REQUIRED'}
 if($StartInfo.ArgumentList[2] -cne 'account' -or $StartInfo.ArgumentList[3] -cne 'show'){throw 'ARGUMENTS_REQUIRED'}
 if($StartInfo.Environment['AZ_INSTALLER'] -cne 'MSI'){throw 'MSI_CONTEXT_REQUIRED'}
 'synthetic-json'
}
if((Invoke-HermesAzureRaw @('account','show')) -cne 'synthetic-json'){throw 'CAPTURE_REQUIRED'}
""")
    def test_captured_cli_transport_bounds_time_and_does_not_echo_stderr(self):
        self.ps("""
$exe=Get-Command pwsh -CommandType Application | Select-Object -First 1 -ExpandProperty Source
$start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$exe
foreach($arg in @('-NoProfile','-NonInteractive','-Command','Start-Sleep -Seconds 5')){$start.ArgumentList.Add($arg)}
try { Invoke-HermesCapturedProcess $start 50;throw 'TIMEOUT_REQUIRED' }
catch { if($_.Exception.Message -cne 'HERMES_AZURE_TIMEOUT'){throw} }
$bad=[Diagnostics.ProcessStartInfo]::new();$bad.FileName=$exe
foreach($arg in @('-NoProfile','-NonInteractive','-Command',"[Console]::Error.WriteLine('SENSITIVE_STDERR_SENTINEL');exit 7")){$bad.ArgumentList.Add($arg)}
try { Invoke-HermesCapturedProcess $bad 10000;throw 'FAILURE_REQUIRED' }
catch { if($_.Exception.Message -cne 'HERMES_AZURE_FAILED'){throw} }
$ok=[Diagnostics.ProcessStartInfo]::new();$ok.FileName=$exe
foreach($arg in @('-NoProfile','-NonInteractive','-Command',"[Console]::Write('synthetic-json-output')")){$ok.ArgumentList.Add($arg)}
if((Invoke-HermesCapturedProcess $ok 10000) -cne 'synthetic-json-output'){throw 'CAPTURE_REQUIRED'}
""")
    def test_pipeline_exit_still_cleans_up_and_cannot_claim_window_completion(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE',complete=True,interrupt=True)
        self.assertEqual('FAIL',result['Status'])
        self.assertEqual('HERMES_WINDOW_INTERRUPTED',result['FailureCode'])
        self.assertTrue(result['Cleanup']['SqlOff'])
        self.assertTrue(result['Cleanup']['AppOff'])
        self.assertTrue(result['Cleanup']['ApplicationStopped'])
    def test_affinity_readback_failure_cleans_up_before_deployment_or_activation(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE',complete=True,affinity_rejected=True)
        self.assertEqual('FAIL',result['Status'])
        self.assertEqual('cookie-free-app-configuration',result['Failure'])
        self.assertEqual(['preflight','affinity-disabled','cleanup'],result['Events'])
    def test_cookie_headers_are_rejected_without_exposing_their_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            body="""
. ./build/azure-mcp-acceptance/HermesAcceptance.ps1
Assert-HermesCookieFreeResponse @{Headers=@{}}
foreach($name in @('Set-Cookie','set-cookie')) {
 try { Assert-HermesCookieFreeResponse @{Headers=@{$name=@('ARRAffinity=SENSITIVE_ROUTING_VALUE')}};throw 'COOKIE_REJECTION_REQUIRED' }
 catch {if($_.Exception.Message -cne 'MCP_COOKIE_RESPONSE_REFUSED'){throw}}
}
"""
            self.ps(body)
    def test_approved_window_uses_hermes_without_automated_campaign_replay(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE', complete=True)
        self.assertEqual('WINDOW_COMPLETED',result['Status'], result)
        self.assertEqual(['preflight','affinity-disabled','sql-enabled','app-enabled','hermes-connect','hermes-chat','cleanup'],result['Events'])
    def test_declined_gate_cannot_execute_preflight_deployment_or_cleanup_mutations(self):
        result=self.campaign('no')
        self.assertFalse(result['Approved']);self.assertEqual([],result['Events']);self.assertEqual('FAIL',result['Status'])
    def test_postapproval_failure_always_attempts_off_and_stop(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE')
        self.assertTrue(result['Approved']);self.assertEqual(['preflight','cleanup'],result['Events']);self.assertEqual('FAIL',result['Status'])

if __name__=='__main__': unittest.main()
