"""Fast-track contracts. Every SQLCMD transport is mocked; owned synthetic
packets only. No uploaded fixture, private input, credential or Azure access.
"""
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import unittest

import test_supervised as supervised
from test_packet import ROOT, TOOL, STAGES
from test_supervised import quote


class FastTrackTest(unittest.TestCase):
    def setUp(self):
        self.fixture_case = supervised.SupervisedTest()
        self.fixture_case.setUp()
        for name in ('packet', 'rendered', 'fixture', 'client'):
            setattr(self, name, getattr(self.fixture_case, name))
        # Approve ONLY synthetic fixtures, including the pre-16 evidence matrix.
        binding = (self.packet / 'binding-inputs.sql').read_text()
        for flag in ('Approved', 'OffConfirmed', 'EvidenceConfirmed'):
            binding = binding.replace('@Bind' + flag + ' bit=0', '@Bind' + flag + ' bit=1')
        for zone in ('Java', 'Sql'):
            binding = binding.replace('@Bind' + zone + 'Zone nvarchar(max)=NULL', '@Bind' + zone + "Zone nvarchar(max)=N'UTC'")
        binding += '\nINSERT @BindWallTimes VALUES\n' + ',\n'.join(
            f"('{y}-{m:02}-15T00:00:00','{y}-{m:02}-15T00:00:00')"
            for y in range(2025, 2029) for m in (1, 3, 4, 7, 10, 11, 12)) + ';\n'
        (self.packet / 'binding-inputs.sql').write_text(binding)
        for file, count in (('identities.sql', 12), ('identities-foundation.sql', 5)):
            text = (self.packet / file).read_text().replace('@ProvisionApproved bit=0', '@ProvisionApproved bit=1')
            numbers = iter(range(count))
            text = re.sub(r"(\(N'mcpacc_[a-z_]+',(?:N'[A-Za-z0-9]+'|NULL),)NULL(\))",
                          lambda m: m[1] + "N'Synthetic" + str(count) + 'SecretForUser' + str(next(numbers)) + "Only'" + m[2], text)
            (self.packet / file).write_text(text)
        self.render()
        self.calls = self.packet / 'fast-calls.jsonl'
        self.approvals = self.packet / 'fast-approvals'
        self.transport = self.packet / 'fast-mock.ps1'

    def tearDown(self):
        self.fixture_case.tearDown()

    def render(self):
        self.fixture_case.ps('& ' + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet))
        self.rendered = max(self.packet.glob('rendered-*'), key=lambda p: p.stat().st_mtime_ns)

    def run_fast(self, mode='pass', success=True):
        self.transport.write_text(r'''
param($Executable,[string[]]$Arguments,[switch]$RequireConsole,$WatchdogSeconds)
if ($Arguments -contains '-?') {
 return [pscustomobject]@{ExitCode=0; FailureCode=''; Lines=@('Microsoft (R) SQL Server Command Line Tool','Version 18.4.0001.1 NT')}
}
[IO.File]::AppendAllText(CALLS,(ConvertTo-Json -InputObject @($Arguments) -Compress)+"`n")
if ($Executable -cne CLIENT) { throw 'Wrong executable' }
$files=$Arguments[[array]::IndexOf($Arguments,'-i')+1].Split(',')
$lines=[Collections.Generic.List[string]]::new(); $exit=0
$specs=@(Get-Content -LiteralPath STAGEFILE -Raw | ConvertFrom-Json)
foreach ($file in $files) {
 $name=[IO.Path]::GetFileNameWithoutExtension($file)
 if ($name -eq 'checkpoint18') {
  if (MODE -in @('wrong-db','unexpected-state')) { $lines.Add('Msg 59810, Level 16, State 1, PRIVATE_NEVER_PRINT'); $exit=16; break }
  $lines.Add('FAST_CHECKPOINT_OK:18'); continue
 }
 if ($name.EndsWith('-begin')) {
  # Real -r1 sends PRINT to stderr, so machine boundaries MUST be SELECTs.
  $control=[IO.File]::ReadAllText($file)
  if (-not $control.Contains('SELECT CAST(') -or -not $control.Contains("N'ShaleMcpAcceptance'")) { throw 'Control fence missing' }
  $stage=$name.Substring(0,$name.Length-6); $lines.Add('  FAST_STAGE_BEGIN:'+$stage+'  '); continue
 }
 if ($name.EndsWith('-done')) { $stage=$name.Substring(0,$name.Length-5); $lines.Add('FAST_STAGE_DONE:'+$stage); continue }
 $spec=$specs | Where-Object name -CEQ $name
 if (-not $spec) { throw 'Unknown stage dispatched' }
 if (MODE -eq 'sql-fail' -and $name -ceq '20-pr3') { $lines.Add('Msg 50001, Level 16, State 1, PRIVATE_NEVER_PRINT'); $exit=16; break }
 switch ($name) {
  '15c-seed' { $lines.Add('SYNTHETIC_ROWS_ONLY_NO_AUTHORITY') }
  '16-binding-preflight' { $lines.Add('EMPTY_READY_FOR_SEPARATE_INSTALL_AUTHORIZATION') }
  '17-binding-install' { $lines.Add('INSTALLED_ONE_BINDING') }
  '18-binding-postcheck' { $lines.Add('BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED') }
  '30-owner-final' { $lines.Add('INSTALLED_OFF_PENDING_RESTRICTED_NATIVE_AND_APP_ACCEPTANCE') }
  default { if ($name -match '-identity-') { $lines.Add('CONTAINED_PRINCIPAL_AND_TOKEN_CHECK_ONLY') } }
 }
 $findings=switch ($name) {
  {$_ -cin @('16-binding-preflight','17-binding-install','18-binding-postcheck','19-pr3-prepare')} {'ConfinementCatalogDriftFindingCount'}
  '20-pr3' {'ApplicationCatalogDriftFindingCount';'ConfinementCatalogDriftFindingCount';'RegistryConfinementFindingCount'}
  '30-owner-final' {'ExternalDataSourceFindingCount';'ExternalTableFindingCount';'ScopedCredentialFindingCount';'SynonymFindingCount';'CrossDatabaseDependencyFindingCount';'OwnershipRouteFindingCount';'PublicAuthorityFindingCount';'RegistryConfinementFindingCount'}
  '31-verifier-catalogs' {'ConfinementCatalogDriftFindingCount';'RegistryConfinementFindingCount'}
 }
 foreach ($finding in $findings) { $lines.Add($finding);$lines.Add('----------------');$lines.Add('0') }
 for ($n=1;$n -le $spec.sources.Count;$n++) {
  if (MODE -ne 'ambiguous' -or $name -cne '15c-seed') { $lines.Add("ACCEPTANCE_SOURCE_OK:${name}:$n") }
 }
}
if (MODE -eq 'interrupted') { return [pscustomobject]@{ExitCode=$null;FailureCode='PROCESS_INTERRUPTED';Lines=@();PasswordPromptObserved=$true;PasswordInputReadyShown=$true} }
if (MODE -eq 'between-groups') { [IO.File]::AppendAllText(CLIENT,'changed synthetic executable') }
if (MODE -eq 'zero-exit-error') { $lines.Add('Msg 50002, Level 16, State 1, PRIVATE_NEVER_PRINT') }
[pscustomobject]@{ExitCode=$exit;FailureCode='';Lines=@($lines);PasswordPromptObserved=(MODE -ne 'missing-prompt');PasswordInputReadyShown=(MODE -ne 'missing-prompt')}
'''.replace('CALLS', quote(self.calls)).replace('CLIENT', quote(self.client)).replace('STAGEFILE', quote(TOOL / 'stages.json')).replace('MODE', quote(mode)))
        pre = "function global:Join-Path {param($Path,$ChildPath); if ($ChildPath -ceq 'Invoke-NativeSqlcmd.ps1') { " + quote(self.transport) + " } else { Microsoft.PowerShell.Management\\Join-Path $Path $ChildPath } }; "
        pre += "function global:Get-FileHash {param($LiteralPath,$Algorithm); if ($LiteralPath -eq " + quote(self.fixture) + ") { [pscustomobject]@{Hash='4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F'} } else { Microsoft.PowerShell.Utility\\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm } }; "
        pre += "function global:Read-Host {param($Prompt); if ($Prompt -notlike 'Approve this entire*') {throw 'Unexpected repeated prompt'}; [IO.File]::AppendAllText(" + quote(self.approvals) + ",'approval\n'); " + ("'NO'" if mode == 'refuse' else "'FAST_TRACK_SHALE_MCP_ACCEPTANCE'") + ' }; '
        command = pre + '& ' + quote(TOOL / 'Start-FastTrackAcceptance.ps1')
        for name, value in [('RenderedDirectory', self.rendered), ('AuthoritativeFixture', self.fixture), ('SqlcmdExecutable', self.client), ('OperatorUser', 'offline_owner')]:
            command += ' -' + name + ' ' + quote(value)
        result = subprocess.run(['pwsh', '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', command],
                                text=True, capture_output=True, timeout=90, cwd=ROOT)
        if (result.returncode == 0) != success:
            self.fail(f'Unexpected exit {result.returncode}: {result.stdout}\n{result.stderr}')
        self.assertNotIn('PRIVATE_NEVER_PRINT', result.stdout + result.stderr)
        self.assertNotIn('Synthetic12SecretForUser', result.stdout + result.stderr)
        return result

    def report(self):
        path = max(self.packet.glob('fast-track-*/report.json'), key=lambda p: p.stat().st_mtime_ns)
        return json.loads(path.read_text())

    def test_complete_remaining_stages_one_approval_absolute_client_and_twenty_logins(self):
        inputs = {p: p.read_bytes() for p in self.packet.glob('*.sql')}
        result = self.run_fast()
        report = self.report()
        self.assertEqual('COMPLETE', report['Outcome'])
        self.assertEqual(51, report['CompletedCount'])
        self.assertEqual([s['name'] for s in STAGES[18:]], report['CompletedStages'])
        self.assertEqual(1, self.approvals.read_text().count('approval'))
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        self.assertEqual(20, len(calls))
        for args in calls:
            self.assertEqual('ShaleMcpAcceptance', args[args.index('-d')+1])
            self.assertEqual('tcp:shale.database.windows.net,1433', args[args.index('-S')+1])
            self.assertEqual('11', args[args.index('-V')+1])
            for flag in ('-N','-b','-x','-X1'):
                self.assertIn(flag, args)
            for flag in ('-P','-C','-E','-G'):
                self.assertNotIn(flag, args)
            self.assertNotIn('Shale', args)
        self.assertEqual(inputs, {p: p.read_bytes() for p in inputs})
        before = self.calls.read_text()
        self.run_fast()  # completed runs never replay
        self.assertEqual(before, self.calls.read_text())
        self.assertIn('outcome=COMPLETE', result.stdout)

    def test_verified_group_resumes_without_replaying_owner_stages(self):
        self.run_fast('between-groups', success=False)
        self.assertEqual(32, self.report()['CompletedCount'])
        self.assertEqual(1, len(self.calls.read_text().splitlines()))
        self.run_fast()
        self.assertEqual(51, self.report()['CompletedCount'])
        self.assertEqual('31-verifier-catalogs', self.report()['CompletedStages'][0])
        self.assertEqual(20, len(self.calls.read_text().splitlines()))

    def test_unconfirmed_native_password_prompt_blocks_later_connections(self):
        self.run_fast('missing-prompt', success=False)
        self.assertEqual(1, len(self.calls.read_text().splitlines()))
        self.assertEqual('PASSWORD_PROMPT_NOT_CONFIRMED', self.report()['FailureCode'])
        self.run_fast(success=False)
        self.assertEqual(1, len(self.calls.read_text().splitlines()))

    def test_sql_error_even_with_zero_exit_blocks_later_connections(self):
        self.run_fast('zero-exit-error', success=False)
        self.assertEqual(0, self.report()['ExitCode'])
        self.assertEqual([50002], self.report()['SqlErrorNumbers'])
        self.assertEqual('SQL_ERROR', self.report()['FailureCode'])
        self.assertEqual(1, len(self.calls.read_text().splitlines()))

    def test_existing_strict_progress_is_not_silently_imported(self):
        (self.packet / 'supervised.jsonl').write_text('{}\n{}\n')
        self.run_fast(success=False)
        self.assertFalse(self.calls.exists())
        self.assertFalse(self.approvals.exists())

    def test_truncated_fast_history_blocks_sql_and_approval(self):
        (self.packet / 'fast-track.jsonl').write_text('{"Payload":"incomplete')
        self.run_fast(success=False)
        self.assertFalse(self.calls.exists())
        self.assertFalse(self.approvals.exists())

    def test_refused_approval_never_executes_sql(self):
        self.run_fast('refuse', success=False)
        self.assertFalse(self.calls.exists())
        self.assertFalse((self.packet / 'fast-track.jsonl').exists())

    def test_failure_preserves_completed_prefix_stops_and_never_retries(self):
        self.run_fast('sql-fail', success=False)
        report = self.report()
        self.assertEqual('20-pr3', report['FailureStage'])
        self.assertEqual(23, report['CompletedCount'])
        self.assertEqual([50001], report['SqlErrorNumbers'])
        self.assertEqual(16, report['ExitCode'])
        self.assertEqual(1, len(self.calls.read_text().splitlines()))
        before = self.calls.read_text()
        self.run_fast(success=False)
        self.assertEqual(before, self.calls.read_text())

    def test_interruption_and_ambiguous_results_stop_with_uncertain_group(self):
        self.run_fast('interrupted', success=False)
        self.assertEqual('PROCESS_INTERRUPTED', self.report()['FailureCode'])
        self.assertEqual(18, self.report()['CompletedCount'])
        before = self.calls.read_text()
        self.run_fast(success=False)
        self.assertEqual(before, self.calls.read_text())

    def test_ambiguous_results_do_not_become_completed_stages(self):
        self.run_fast('ambiguous', success=False)
        report = self.report()
        self.assertEqual(18, report['CompletedCount'])
        self.assertEqual(14, len(report['AttemptedGroupStages']))
        self.assertEqual('INCOMPLETE_RESULTS', report['FailureCode'])

    def test_unexpected_database_checkpoint_stops_before_seed(self):
        self.run_fast('unexpected-state', success=False)
        self.assertEqual(18, self.report()['CompletedCount'])
        self.assertEqual([59810], self.report()['SqlErrorNumbers'])

    def test_missing_binding_evidence_blocks_before_any_approval_or_sql(self):
        path = self.packet / 'binding-inputs.sql'
        path.write_text(path.read_text().replace('@BindEvidenceConfirmed bit=1', '@BindEvidenceConfirmed bit=0'))
        self.render()
        self.run_fast(success=False)
        self.assertFalse(self.calls.exists())
        self.assertFalse(self.approvals.exists())

    def test_tampered_target_or_hash_is_refused_before_sql(self):
        path = self.rendered / '15c-seed.sql'
        path.write_text(path.read_text().replace("N'ShaleMcpAcceptance'", "N'Shale'", 1))
        manifest = self.rendered / 'sha256.csv'
        with manifest.open() as stream:
            rows = list(csv.DictReader(stream))
        for row in rows:
            if row['Stage'] == '15c-seed':
                row['SHA256'] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
        with manifest.open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['Stage', 'SHA256']); writer.writeheader(); writer.writerows(rows)
        self.run_fast(success=False)
        self.assertFalse(self.calls.exists())

    def test_reconcile_accepts_sqlcmd_bom_padding_crlf_but_not_bad_records(self):
        canonical = 'ACCEPTANCE_SNAPSHOT:' + 'A' * 64 + ':0:0:0'
        for value in (canonical, '\ufeffAcceptanceResult\r\n-----\r\n  ' + canonical + '   \r\n', ' \ufeff' + canonical.lower().replace('acceptance_snapshot', 'ACCEPTANCE_SNAPSHOT') + '\r\n'):
            result = self.fixture_case.parse([value], 'reconcile', 1)
            self.assertEqual(canonical, result.stdout.strip())
        for value in (canonical + '\n' + canonical, canonical[:-3], canonical.replace(':0:0:0', ':9999999999999999999:0:0'), 'prefix ' + canonical):
            self.fixture_case.parse([value], 'reconcile', 1, success=False)


if __name__ == '__main__':
    unittest.main(verbosity=2)
