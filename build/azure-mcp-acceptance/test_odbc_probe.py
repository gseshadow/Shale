"""SQL-free client/probe tests. Any process seam is mocked; only an owned
loopback TCP listener can be reached. Never start SQLCMD or read private inputs.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_packet import ROOT, TOOL
from test_supervised import quote

GO_SHA = 'D9DBD1A8BD26213747B246DEBA1E13663A7E1DEF530CD961D7582B41A5E27852'


class OdbcProbeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='odbc-probe-offline-', dir=ROOT / 'work')
        self.directory = Path(self.tmp.name)
        self.pwsh = shutil.which('pwsh')
        if not self.pwsh:
            self.fail('PowerShell required; unexecuted checks are not a pass')

    def tearDown(self):
        self.tmp.cleanup()

    def ps(self, code, success=True):
        # A test must never fall back to an installed SQLCMD executable.
        code = "function global:sqlcmd { throw 'SQLCMD prohibited in offline tests' }; " + code
        result = subprocess.run([self.pwsh, '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', code],
                                text=True, capture_output=True, timeout=30, cwd=ROOT)
        if (result.returncode == 0) != success:
            self.fail(f'Unexpected exit {result.returncode}: {result.stdout}\n{result.stderr}')
        return result

    def test_exact_reported_hash_identifies_official_go_release_without_executing_it(self):
        code = '. ' + quote(TOOL / 'OdbcProbe.ps1') + '; Read-SqlcmdClientIdentity ' + quote(GO_SHA) + ' | ConvertTo-Json -Compress'
        value = json.loads(self.ps(code).stdout)
        self.assertEqual({'Family': 'GO_SQLCMD', 'Version': '1.10.0', 'Evidence': 'RELEASE_SHA256'}, value)

    def test_help_banners_are_classified_without_disclosing_raw_help(self):
        cases = [
            ('sqlcmd: Install/Create/Query SQL Server, Azure SQL, and Tools\nVersion: 1.10.0\nPRIVATE_NEVER_PRINT', 'GO_SQLCMD', '1.10.0'),
            ('Microsoft (R) SQL Server Command Line Tool\r\nVersion 18.4.0001.1 NT\r\nPRIVATE_NEVER_PRINT', 'ODBC_SQLCMD', '18.4.0001.1'),
            ('Unrecognized executable\nPRIVATE_NEVER_PRINT', 'UNKNOWN', ''),
        ]
        for text, family, version in cases:
            with self.subTest(family=family):
                code = '. ' + quote(TOOL / 'OdbcProbe.ps1') + '; Read-SqlcmdClientIdentity ' + quote('0' * 64)
                code += ' @(' + quote(text) + ') | ConvertTo-Json -Compress'
                result = self.ps(code)
                value = json.loads(result.stdout)
                self.assertEqual(family, value['Family'])
                self.assertEqual(version, value['Version'])
                self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)

    def native(self, **changes):
        value = dict(ExitCode=1, Started=True, HasExited=True, TimedOut=False, ElapsedMilliseconds=123,
                     OsErrorCode=None, StdoutCharacters=0, StderrCharacters=45, PasswordPromptObserved=True,
                     PasswordInputReadyShown=True, FailureCode='', Lines=['PRIVATE_NEVER_PRINT'])
        value.update(changes)
        return value

    def test_diagnostic_matrix_distinguishes_process_prompt_timeout_transport_and_harness(self):
        cases = [
            (self.native(), True, '', False, 'EXPECTED_PRELOGIN_FAILURE'),
            (self.native(PasswordPromptObserved=False, PasswordInputReadyShown=False), False, '', False, 'MISSING_PROMPT_PROCESS_EXIT'),
            (self.native(PasswordPromptObserved=False, PasswordInputReadyShown=False), True, '', False, 'MISSING_PROMPT_PROCESS_EXIT'),
            (self.native(), False, '', False, 'PROCESS_EXIT_BEFORE_LOOPBACK'),
            (self.native(PasswordInputReadyShown=False), True, '', False, 'PROMPT_OBSERVED_AFTER_PROCESS_EXIT'),
            (self.native(TimedOut=True, FailureCode='PROCESS_TIMEOUT', PasswordPromptObserved=False), False, '', False, 'TIMEOUT_BEFORE_PROMPT'),
            (self.native(TimedOut=True, FailureCode='PROCESS_TIMEOUT'), True, '', False, 'TIMEOUT_AFTER_PROMPT'),
            (self.native(Started=False, HasExited=False, ExitCode=None, FailureCode='PROCESS_START_FAILED', OsErrorCode=2), False, '', False, 'PROCESS_START_FAILURE'),
            (self.native(FailureCode='PROCESS_INTERRUPTED'), False, '', False, 'PROCESS_INTERRUPTED'),
            (self.native(FailureCode='PROCESS_CAPTURE_FAILED'), False, '', False, 'TRANSPORT_FAILURE'),
            (self.native(FailureCode='PRIVATE_NEVER_PRINT'), False, '', False, 'TRANSPORT_FAILURE'),
            (self.native(), False, 'LISTENER_START_FAILED', False, 'HARNESS_FAILURE'),
            (self.native(), True, 'ACCEPT_FAILED', False, 'HARNESS_FAILURE'),
            (self.native(), True, 'CLEANUP_FAILED', False, 'HARNESS_FAILURE'),
            (self.native(ExitCode=0), True, '', False, 'UNEXPECTED_SUCCESS'),
            (self.native(ExitCode=None), True, '', False, 'UNKNOWN_PROCESS_OUTCOME'),
            (self.native(ExitCode=0, PasswordPromptObserved=False), False, '', True, 'HELP_COMPLETED'),
        ]
        for native, accepted, harness, help_only, expected in cases:
            with self.subTest(expected=expected, accepted=accepted):
                code = '. ' + quote(TOOL / 'OdbcProbe.ps1') + '; $n=ConvertFrom-Json ' + quote(json.dumps(native))
                code += '; Get-OdbcProbeDiagnosis $n -ListenerAccepted:$' + str(accepted).lower() + ' -HarnessFailure ' + quote(harness)
                if help_only:
                    code += ' -HelpOnly'
                code += ' | ConvertTo-Json -Compress'
                result = self.ps(code)
                value = json.loads(result.stdout)
                self.assertEqual(expected, value['Status'])
                self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
                self.assertNotIn('Lines', value)

    def run_probe(self, mode, success=False):
        client = self.directory / 'mock-sqlcmd.exe'
        client.write_text('Not an executable; the native transport is always substituted in this test host.')
        calls = self.directory / ('calls-' + mode + '.jsonl')
        transport = self.directory / 'mock-transport.ps1'
        transport.write_text(r'''
param($Executable,[string[]]$Arguments,[switch]$RequireConsole,[int]$WatchdogSeconds)
[IO.File]::AppendAllText(CALLS, (ConvertTo-Json -InputObject @($Arguments) -Compress)+"`n")
$help=$Arguments -contains '-?'
$lines=@(); $exit=1; $prompt=$true; $ready=$true; $timeout=$false; $failure=''
if ($help) {
 if (MODE -eq 'go-help') { $lines=@('sqlcmd: Install/Create/Query SQL Server, Azure SQL, and Tools','Version: 1.10.0') }
 elseif (MODE -eq 'old-odbc-help') { $lines=@('Microsoft (R) SQL Server Command Line Tool','Version 16.0.0001.1 NT') }
 elseif (MODE -eq 'unknown-help') { $lines=@('PRIVATE_NEVER_PRINT') }
 else { $lines=@('Microsoft (R) SQL Server Command Line Tool','Version 18.4.0001.1 NT','PRIVATE_NEVER_PRINT') }
 $exit=0; $prompt=$false; $ready=$false
} else {
 if (MODE -eq 'transport-throws') { throw 'PRIVATE_NEVER_PRINT' }
 $destination=$Arguments[[array]::IndexOf($Arguments,'-S')+1]
 if ($destination -notmatch '^tcp:127\.0\.0\.1,([0-9]+)$') { throw 'Offline test refused non-loopback destination' }
 if (MODE -ne 'no-connect') {
  $client=[Net.Sockets.TcpClient]::new()
  try { $client.Connect([Net.IPAddress]::Loopback,[int]$Matches[1]) } finally { $client.Dispose() }
 }
 if (MODE -eq 'no-prompt') { $prompt=$false; $ready=$false }
 if (MODE -eq 'timeout') { $timeout=$true; $failure='PROCESS_TIMEOUT'; $prompt=$false; $ready=$false }
 $lines=@('PRIVATE_NEVER_PRINT')
}
[pscustomobject]@{ExitCode=$exit; Lines=$lines; Started=$true; HasExited=$true; TimedOut=$timeout; ElapsedMilliseconds=321L; OsErrorCode=$null; StdoutCharacters=0; StderrCharacters=20; PasswordPromptObserved=$prompt; PasswordInputReadyShown=$ready; FailureCode=$failure}
'''.replace('CALLS', quote(calls)).replace('MODE', quote(mode)))
        source = (TOOL / 'Test-WindowsOdbcPrompt.ps1').read_text()
        # Bypass ONLY the platform gate in this isolated scriptblock so Linux can
        # exercise the actual orchestration. Never patch the checked-in script.
        guard = next(line for line in source.splitlines() if line.startswith('if (-not [Runtime.InteropServices.RuntimeInformation]'))
        source = source.replace(guard, '# Platform-only gate replaced in offline test host', 1)
        source = source.replace('$PSScriptRoot', quote(TOOL))
        script = self.directory / 'probe-test-host.ps1'
        script.write_text(source)
        pre = "function global:Join-Path {param($Path,$ChildPath); if ($ChildPath -ceq 'Invoke-NativeSqlcmd.ps1') { " + quote(transport) + " } else { Microsoft.PowerShell.Management\\Join-Path $Path $ChildPath } }; "
        pre += "function global:Read-Host {param($Prompt); if ($Prompt -like '*HIDDEN_INPUT_CONFIRMED*') {'HIDDEN_INPUT_CONFIRMED'} else {'ODBC_OFFLINE_LOOPBACK_ONLY'} }; "
        if mode == 'go-hash':
            pre += "function global:Get-FileHash {param($LiteralPath,$Algorithm); [pscustomobject]@{Hash=" + quote(GO_SHA) + "} }; "
        result = self.ps(pre + '& ' + quote(script) + ' -SqlcmdExecutable ' + quote(client), success=success)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        recorded = [json.loads(line) for line in calls.read_text().splitlines()] if calls.exists() else []
        return result, recorded

    def test_wrong_known_client_is_rejected_before_help_process_or_listener(self):
        result, calls = self.run_probe('go-hash')
        self.assertIn('WRONG_CLIENT_GO_SQLCMD', result.stdout)
        self.assertIn('listener not started', result.stdout)
        self.assertEqual([], calls)

    def test_go_or_unknown_help_never_reaches_login_or_listener(self):
        for mode, expected in [('go-help','ODBC_CLIENT_NOT_CONFIRMED'), ('unknown-help','ODBC_CLIENT_NOT_CONFIRMED'), ('old-odbc-help','ODBC_VERSION_NOT_REVIEWED')]:
            with self.subTest(mode=mode):
                result, calls = self.run_probe(mode)
                self.assertIn(expected, result.stderr)
                self.assertEqual([['-X1', '-?']], calls)

    def test_probe_records_loopback_contact_and_uses_only_approved_literal_arguments(self):
        result, calls = self.run_probe('pass', success=True)
        self.assertIn('status=HELP_COMPLETED', result.stdout)
        self.assertIn('status=EXPECTED_PRELOGIN_FAILURE', result.stdout)
        self.assertIn('loopbackAccepted=True', result.stdout)
        self.assertIn('OFFLINE_ODBC_PROMPT_PASS', result.stdout)
        self.assertEqual(2, len(calls))
        self.assertEqual(['-X1', '-?'], calls[0])
        args = calls[1]
        self.assertTrue(args[args.index('-S')+1].startswith('tcp:127.0.0.1,'))
        for flag in ('-N','-b','-x','-X1','-f'):
            self.assertIn(flag, args)
        for flag in ('-P','-C','-E','-G','-i','-Q'):
            self.assertNotIn(flag, args)

    def test_transport_invocation_exception_is_a_safe_harness_failure(self):
        result, calls = self.run_probe('transport-throws')
        self.assertIn('status=HARNESS_FAILURE', result.stdout)
        self.assertIn('harness=TRANSPORT_CALL_FAILED', result.stdout)
        self.assertNotIn('OFFLINE_ODBC_PROMPT_PASS', result.stdout)
        self.assertEqual(2, len(calls))

    def test_missing_prompt_timeout_and_preconnection_exit_are_not_conflated_or_retried(self):
        for mode, status in [('no-prompt','MISSING_PROMPT_PROCESS_EXIT'), ('timeout','TIMEOUT_BEFORE_PROMPT'), ('no-connect','PROCESS_EXIT_BEFORE_LOOPBACK')]:
            with self.subTest(mode=mode):
                result, calls = self.run_probe(mode)
                self.assertIn('status=' + status, result.stdout)
                self.assertNotIn('OFFLINE_ODBC_PROMPT_PASS', result.stdout)
                self.assertEqual(2, len(calls), 'Help once, login once; no automatic retry')


if __name__ == '__main__':
    unittest.main(verbosity=2)
