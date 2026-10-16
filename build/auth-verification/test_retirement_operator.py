"""Operator orchestration/transport regressions; no real SQL, Azure or production actions."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / 'docs/sql/auth/permission-cutover/retirement-operator.ps1'
BEHAVIOR = Path(__file__).with_suffix('.ps1')
NATIVE = shutil.which('powershell.exe') if os.name == 'nt' else None
CORE = os.environ.get('SHALE_OPERATOR_TEST_PWSH') or shutil.which('pwsh')


class RetirementOperatorTest(unittest.TestCase):
    @unittest.skipUnless(NATIVE, 'Native Windows PowerShell 5.1 unavailable; never certified by Core')
    def test_native_behavior(self):
        result = subprocess.run([NATIVE, '-NoLogo', '-NoProfile', '-NonInteractive', '-File', str(BEHAVIOR)],
                                cwd=ROOT, capture_output=True, text=True, timeout=60)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('Desktop 5.1.', result.stdout, 'This must run on native Windows PS5.1')

    @unittest.skipUnless(CORE, 'Optional Core runtime unavailable')
    def test_core_behavior_is_separate_from_native(self):
        result = subprocess.run([CORE, '-NoLogo', '-NoProfile', '-File', str(BEHAVIOR)], cwd=ROOT,
                                capture_output=True, text=True, timeout=60)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('PASS retirement operator offline behavior', result.stdout)

    @unittest.skipUnless(CORE and os.name != 'nt', 'Portable fake executable transport uses POSIX test host')
    def test_real_process_transport_retains_full_output_exit_and_deadline(self):
        with tempfile.TemporaryDirectory(prefix='retirement transport ') as directory:
            work = Path(directory)
            fake = work / 'sqlcmd-stub'
            fake.write_text('''#!/usr/bin/env python3
import os, pathlib, sys, time
args = sys.argv[1:]
assert '-P' not in args
assert os.environ.get('SQLCMDPASSWORD') == 'SYNTHETIC_ONLY_PASSWORD'
assert 'SQLCMDINI' not in os.environ
assert '-b' in args and '-u' in args and '-N' in args and '-C' not in args
out = pathlib.Path(args[args.index('-o')+1])
out.write_text('LegacyRetirementCommitted 0x'+'AB'*32+' 0x'+'CD'*32+'\\n', encoding='utf-16')
sys.stdout.write('O'*131072); sys.stdout.flush()
sys.stderr.write('E'*131072); sys.stderr.flush()
if 'slow' in out.name: time.sleep(5)
sys.exit(16 if 'failure' in out.name else 0)
''', encoding='utf-8')
            fake.chmod(0o700)
            # JSON is parsed inside PowerShell, never interpolated as shell syntax.
            config = work / 'config.json'
            config.write_text(json.dumps(dict(runner=str(RUNNER), root=str(work), executable=str(fake))), encoding='utf-8')
            script = work / 'transport.ps1'
            script.write_text(r'''
param([string]$Config)
$ErrorActionPreference='Stop'
$c=Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
. $c.runner
$password=ConvertTo-SecureString 'SYNTHETIC_ONLY_PASSWORD' -AsPlainText -Force
$env:SQLCMDINI='MUST_NOT_REACH_CHILD'
try {
 foreach ($name in @('success','failure','slow')) {
  $log=Join-Path $c.root $name
  $deadline=$null
  if($name -eq 'slow'){$deadline=[datetime]::UtcNow.AddSeconds(1)}
  $caught=$false
  try { $null=Invoke-PacketSql $c.root 'sql.invalid' 'Shale' 'testuser' $password 'unused.sql' @('ExpectedDatabase=Shale') $log $c.executable $deadline }
  catch { $caught=$true }
  if (($name -eq 'success') -eq $caught) { throw "Wrong process result: $name" }
  $r=Read-Json ($log+'.result.json')
  if($name -eq 'failure' -and $r.ExitCode -ne 16){throw 'Native nonzero exit lost'}
  if($name -eq 'slow' -and -not $r.DeadlineExceeded){throw 'Deadline did not interrupt'}
  if((Get-Item -LiteralPath ($log+'.stdout.bin')).Length -ne 131072 -or (Get-Item -LiteralPath ($log+'.stderr.bin')).Length -ne 131072){throw 'Full console output not retained'}
  $before='0x'+('AB'*32)
  if((Get-SavedHashes ($log+'.sqlcmd.txt') 'Apply' $before) -ne ('0x'+('CD'*32))){throw 'Unicode SQLCMD commit identity lost'}
 }
 if($env:SQLCMDINI -ne 'MUST_NOT_REACH_CHILD' -or (Test-Path Env:SQLCMDPASSWORD)){throw 'Parent environment was changed'}
 Write-Output 'PASS actual subprocess output/nonzero/deadline/password boundary'
} finally {$password.Dispose(); Remove-Item Env:SQLCMDINI}
''', encoding='utf-8')
            result = subprocess.run([CORE, '-NoLogo', '-NoProfile', '-File', str(script), '-Config', str(config)],
                                    cwd=ROOT, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('PASS actual subprocess', result.stdout)
            self.assertNotIn('SYNTHETIC_ONLY_PASSWORD', result.stdout + result.stderr)

    def test_verifier_invokes_no_legacy_or_application_module(self):
        import re
        source = (RUNNER.parent / 'operator-postcheck.sql').read_text(encoding='utf-8')
        self.assertNotRegex(source, r'(?i)\bEXEC(?:UTE)?\s+(?:dbo\.|sp_login_|Lookup|Resolve)')
        self.assertIn('LegacyAfterHash', source)
        self.assertIn('THROW 58501', source)
        self.assertIn("COALESCE(HAS_PERMS_BY_NAME(N'dbo.Users',N'OBJECT',N'SELECT'),0)<>1", source)
        self.assertEqual(6, len(re.findall(r'^:r ', source, re.M)))


if __name__ == '__main__':
    unittest.main()
