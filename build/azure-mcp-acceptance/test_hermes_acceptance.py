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

class HermesAcceptanceTest(unittest.TestCase):
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
    def campaign(self, phrase, complete=False):
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
function Invoke-CleanAzure { param($a) @{state='Stopped'} }
function Start-LiveWorker { param($a) };function Assert-LiveWorker { param($a,$b,[switch]$Ready) if($Ready){'ready'}else{'AI_CASE_SQL_READINESS disabled'} }
function Invoke-WebRequest { param($Uri,$Method,$ContentType,$Body,[switch]$SkipHttpErrorCheck,$MaximumRedirection,$TimeoutSec,$Headers) if($Uri.EndsWith('/login')){@{StatusCode=200;Content='<div id="root">'}}else{@{StatusCode=404}} }
function Get-LiveBinding { param($a,$b) };function Invoke-LiveRls { param($a) }
function Invoke-LiveNativeChecks { param($a,$b,$c) };function Get-LiveAuditBaseline { param($a) @{Access=1;Phi=1} }
function Set-LiveSqlControls { param($enabled,$a,$b) $script:Events.Add('sql-enabled') }
function Set-LiveAppControls { param($enabled,$run) $script:Events.Add('app-enabled') }
function hermes { $global:LASTEXITCODE=0; $script:Events.Add('hermes-connect') }
function Get-Command { param($a,$CommandType) @{Source='not-executed.exe'};@{Source='not-executed.cmd'} }
function Start-Process { param($FilePath,$ArgumentList,[switch]$NoNewWindow,[switch]$PassThru) if($FilePath -cne 'not-executed.exe'){throw 'SINGLE_EXECUTABLE_REQUIRED'};$script:Events.Add('hermes-chat');$p=[pscustomobject]@{HasExited=$true;ExitCode=0};$p | Add-Member ScriptMethod WaitForExit { param($ms) $true };$p }
$script:LiveOrigin='https://acceptance.invalid';$context.Zones=@{Java='UTC'};$context.Id='synthetic'
$result=Invoke-HermesAcceptanceWindow $context
""")
            result=subprocess.run(['pwsh','-NoProfile','-NonInteractive','-Command',script],cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            return json.loads(result.stdout.splitlines()[-1])
    def test_approved_window_uses_hermes_without_automated_campaign_replay(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE', complete=True)
        self.assertEqual('WINDOW_COMPLETED',result['Status'], result)
        self.assertEqual(['preflight','sql-enabled','app-enabled','hermes-connect','hermes-chat','cleanup'],result['Events'])
    def test_declined_gate_cannot_execute_preflight_deployment_or_cleanup_mutations(self):
        result=self.campaign('no')
        self.assertFalse(result['Approved']);self.assertEqual([],result['Events']);self.assertEqual('FAIL',result['Status'])
    def test_postapproval_failure_always_attempts_off_and_stop(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE')
        self.assertTrue(result['Approved']);self.assertEqual(['preflight','cleanup'],result['Events']);self.assertEqual('FAIL',result['Status'])

if __name__=='__main__': unittest.main()
