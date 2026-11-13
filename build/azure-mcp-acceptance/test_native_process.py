"""Real native-process tests using pwsh children only. NEVER invoke SQLCMD/SQL.
The state-machine tests mock the process seam; these exercise actual pipe/argv behavior.
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


class NativeProcessTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='native-process-offline-', dir=ROOT / 'work')
        self.directory = Path(self.tmp.name)
        self.pwsh = shutil.which('pwsh')
        if not self.pwsh:
            self.fail('PowerShell required; an unexecuted process check is not a pass')

    def tearDown(self):
        self.tmp.cleanup()

    def ps(self, code, input_text=None):
        result = subprocess.run([self.pwsh, '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', code],
                                input=input_text, text=True, capture_output=True, timeout=30, cwd=ROOT)
        self.assertEqual(0, result.returncode, result.stderr)
        return result

    def test_real_child_drains_both_pipes_and_preserves_exit_with_native_error_preference(self):
        child = self.directory / 'child with spaces.ps1'
        child.write_text("""
param([string]$Literal)
[Console]::Out.Write('Password: ')
[Console]::Out.WriteLine($Literal)
[Console]::Out.WriteLine(('x' * 131072))
[Console]::Error.Write('Password: ')
[Console]::Error.WriteLine('Msg 1934, Level 16, State 1, Server PRIVATE_NEVER_PRINT')
[Console]::Error.WriteLine(('y' * 131072))
exit 16
""")
        value = 'spaces "quotes" $literal ; & tokens'
        code = "$ErrorActionPreference='Stop'; $PSNativeCommandUseErrorActionPreference=$true; . " + quote(TOOL / 'Supervision.ps1')
        code += '; $r=& ' + quote(TOOL / 'Invoke-NativeSqlcmd.ps1') + ' -Executable ' + quote(self.pwsh)
        code += ' -Arguments ' + ','.join(quote(a) for a in ('-NoLogo', '-NoProfile', '-File', child, value))
        code += '; [pscustomobject]@{Exit=$r.ExitCode; Code=$r.FailureCode; Numbers=@(Get-SafeSqlErrorNumbers $r.Lines); Prompt=$r.PasswordPromptObserved; LiteralOK=$r.Lines[0].StartsWith(' + quote(value) + '); OutLength=$r.Lines[0].Length; ErrLength=$r.Lines[1].Length} | ConvertTo-Json -Compress'
        result = self.ps(code)
        data = json.loads(result.stdout)
        self.assertEqual(16, data['Exit'])
        self.assertEqual('', data['Code'])
        self.assertEqual([1934], data['Numbers'])
        self.assertTrue(data['LiteralOK'])
        self.assertTrue(data['Prompt'])
        self.assertGreater(data['OutLength'], 131072)
        self.assertGreater(data['ErrLength'], 131072)
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)

    def test_console_requirement_refuses_redirected_input_without_starting_child(self):
        marker = self.directory / 'must-not-exist'
        child = self.directory / 'no-run.ps1'
        child.write_text('[IO.File]::WriteAllText(' + quote(marker) + ",'unexpected')")
        code = '$r=& ' + quote(TOOL / 'Invoke-NativeSqlcmd.ps1') + ' -RequireConsole -Executable ' + quote(self.pwsh)
        code += ' -Arguments ' + ','.join(quote(a) for a in ('-NoLogo', '-NoProfile', '-File', child))
        code += '; $r | ConvertTo-Json -Compress'
        data = json.loads(self.ps(code, input_text='public sentinel\n').stdout)
        self.assertEqual('CONSOLE_REQUIRED', data['FailureCode'])
        self.assertIsNone(data['ExitCode'])
        self.assertFalse(marker.exists())

    def test_native_child_inherits_standard_input_without_a_password_pipe(self):
        child = self.directory / 'stdin.ps1'
        child.write_text("if ([Console]::ReadLine() -ceq 'PUBLIC_SENTINEL') { [Console]::WriteLine('INHERITED_INPUT'); exit 0 }; exit 9")
        code = '$r=& ' + quote(TOOL / 'Invoke-NativeSqlcmd.ps1') + ' -Executable ' + quote(self.pwsh)
        code += ' -Arguments ' + ','.join(quote(a) for a in ('-NoLogo', '-NoProfile', '-File', child))
        code += "; [pscustomobject]@{Exit=$r.ExitCode; Inherited=($r.Lines[0].Trim() -ceq 'INHERITED_INPUT')} | ConvertTo-Json -Compress"
        data = json.loads(self.ps(code, input_text='PUBLIC_SENTINEL\n').stdout)
        self.assertEqual(0, data['Exit'])
        self.assertTrue(data['Inherited'])

    def test_native_start_failure_never_prints_ready_or_raw_exception(self):
        code = '$r=& ' + quote(TOOL / 'Invoke-NativeSqlcmd.ps1') + ' -Executable ' + quote(self.directory / 'PRIVATE_NEVER_PRINT.exe') + " -Arguments @('dummy'); $r | ConvertTo-Json -Compress"
        result = self.ps(code)
        data = json.loads(result.stdout)
        self.assertEqual('PROCESS_START_OR_INTERRUPTED', data['FailureCode'])
        self.assertIsNone(data['ExitCode'])
        self.assertFalse(data['PasswordInputReadyShown'])
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        self.assertNotIn('PASSWORD INPUT READY:', result.stdout + result.stderr)

    @unittest.skipIf(os.name == 'nt', 'Windows equivalent is Test-WindowsConsoleCapture.ps1')
    def test_live_no_newline_prompt_is_visible_before_native_child_reads_hidden_input(self):
        import pty
        import select
        import time
        child = self.directory / 'hidden-input.ps1'
        child.write_text("""
[Console]::Error.Write('Password: ')
$word=[Text.StringBuilder]::new()
do { $key=[Console]::ReadKey($true); if ($key.Key -ne [ConsoleKey]::Enter) { [void]$word.Append($key.KeyChar) } } while ($key.Key -ne [ConsoleKey]::Enter)
if ($word.ToString() -ceq 'OFFLINE') { [Console]::WriteLine('PUBLIC_INPUT_OK'); exit 0 }; exit 9
""")
        code = '$r=& ' + quote(TOOL / 'Invoke-NativeSqlcmd.ps1') + ' -RequireConsole -Executable ' + quote(self.pwsh)
        code += ' -Arguments ' + ','.join(quote(a) for a in ('-NoLogo', '-NoProfile', '-File', child))
        code += "; if ($r.ExitCode -eq 0 -and $r.PasswordInputReadyShown -and $r.Lines[0].Trim() -ceq 'PUBLIC_INPUT_OK') { 'CONSOLE_PIPE_PASS' } else { exit 1 }"
        master, slave = pty.openpty()
        process = subprocess.Popen([self.pwsh, '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', code],
                                   stdin=slave, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=ROOT)
        os.close(slave)
        output = b''
        try:
            deadline = time.monotonic() + 10
            while b'PASSWORD INPUT READY:' not in output and time.monotonic() < deadline:
                readable, _, _ = select.select([process.stdout], [], [], 0.2)
                if readable:
                    chunk = os.read(process.stdout.fileno(), 4096)
                    if not chunk:
                        break
                    output += chunk
            self.assertIn(b'PASSWORD INPUT READY:', output, 'A no-newline native prompt must be surfaced before asking for input')
            os.write(master, b'OFFLINE\r')
            stdout, stderr = process.communicate(timeout=10)
            output += stdout
            self.assertEqual(0, process.returncode, stderr.decode(errors='replace'))
            self.assertIn(b'CONSOLE_PIPE_PASS', output)
            terminal_output = b''
            while select.select([master], [], [], 0)[0]:
                try:
                    chunk = os.read(master, 4096)
                except OSError:  # PTY EOF after the last slave closes
                    break
                if not chunk:
                    break
                terminal_output += chunk
            self.assertNotIn(b'OFFLINE', output + stderr + terminal_output,
                             'Public fixture input must not echo on either captured pipes or the terminal')
        finally:
            if process.poll() is None:
                # Close the public fixture's terminal too; no SQL process exists.
                os.close(master)
                process.kill(); process.wait()
            else:
                os.close(master)

    def test_odbc_probe_can_only_use_its_owned_loopback_non_sql_listener(self):
        source = (TOOL / 'Test-WindowsOdbcPrompt.ps1').read_text()
        self.assertIn('[Net.IPAddress]::Loopback, 0', source)
        self.assertIn('$listener.LocalEndpoint.Port', source)
        self.assertIn('tcp:127.0.0.1,$port', source)
        self.assertIn('HIDDEN_INPUT_CONFIRMED', source)
        self.assertNotIn('database.windows.net', source)
        self.assertNotIn('ShaleMcpAcceptance', source)
        for flag in ("'-Q'", "'-i'", "'-P'", "'-C'", "'-E'", "'-G'"):
            self.assertNotIn(flag, source)

    def test_safe_error_numbers_and_hash_remain_compatible_with_old_ledgers(self):
        text = 'existing ledger payload\nwith Unicode é'
        lines = ['Msg 1934, Level 16, State 1, Server PRIVATE', 'Msg 1934, Level 16, State 2,',
                 'Msg 50000, Level 16, State 1', 'secret=123456789', 'Msg 9876 without SQL header',
                 'Msg 9999999999, Level 16, State 1, overflow']
        code = '. ' + quote(TOOL / 'Supervision.ps1')
        code += '; [pscustomobject]@{Hash=(Get-TextSHA256 ' + quote(text) + '); Numbers=@(Get-SafeSqlErrorNumbers (ConvertFrom-Json ' + quote(json.dumps(lines)) + '))} | ConvertTo-Json -Compress'
        result = self.ps(code)
        data = json.loads(result.stdout)
        self.assertEqual(hashlib.sha256(text.encode()).hexdigest().upper(), data['Hash'])
        self.assertEqual([1934, 50000], data['Numbers'])
        self.assertNotIn('PRIVATE', result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
