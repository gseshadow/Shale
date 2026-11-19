"""Offline tests: no Azure, SQL, OAuth, or model calls."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml
ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).parent
spec = importlib.util.spec_from_file_location("profile_builder", TOOL / "prepare-hermes-profile.py")
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)

class HermesAcceptanceTest(unittest.TestCase):
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
    def campaign(self, phrase):
        with tempfile.TemporaryDirectory() as tmp:
            run=Path(tmp).as_posix()
            script = f"""
$ErrorActionPreference='Stop'
. ./build/azure-mcp-acceptance/HermesAcceptance.ps1
$script:LiveAppId='acceptance';$script:Events=[Collections.Generic.List[string]]::new()
function Read-Host {{ '{phrase}' }}
function New-CleanEvent {{ param($p,$e) }}
function Get-LiveSafeCampaignFailure {{ param($e) 'PREFLIGHT_FAILED' }}
function Get-LiveCampaignDiagnostic {{ param($e) @{{Kind='TEST'}} }}
function Assert-LiveAzureConfiguration {{ $script:Events.Add('preflight'); throw 'TEST_PREFLIGHT_STOP' }}
function Stop-LiveAcceptance {{ param($owner,$creds,$run,[switch]$VerifySqlOnly) $script:Events.Add('cleanup'); @{{SqlOff=$true;AppOff=$true;ApplicationStopped=$true}} }}
$context=@{{Run='{run}';Inventory=@();Hash='synthetic';Version='1';Evidence=@{{JournalHash='synthetic'}}}}
$result=Invoke-HermesAcceptanceWindow $context
@{{Approved=$result.Approved;Status=$result.Status;Events=$script:Events.ToArray()}} | ConvertTo-Json -Compress
"""
            result=subprocess.run(['pwsh','-NoProfile','-NonInteractive','-Command',script],cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            return json.loads(result.stdout.splitlines()[-1])
    def test_declined_gate_cannot_execute_preflight_deployment_or_cleanup_mutations(self):
        result=self.campaign('no')
        self.assertFalse(result['Approved']);self.assertEqual([],result['Events']);self.assertEqual('FAIL',result['Status'])
    def test_postapproval_failure_always_attempts_off_and_stop(self):
        result=self.campaign('CONNECT_SHALE_MCP_HERMES_ACCEPTANCE')
        self.assertTrue(result['Approved']);self.assertEqual(['preflight','cleanup'],result['Events']);self.assertEqual('FAIL',result['Status'])

if __name__=='__main__': unittest.main()
