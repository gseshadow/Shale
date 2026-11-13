"""Offline security/state-machine tests: SQLCMD is ALWAYS a PowerShell mock.
Synthetic packet/BCrypt-shaped strings only; never open the operator's packet.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_packet import ROOT, TOOL, SQL, XML_SESSION, STAGES


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


class SupervisedTest(unittest.TestCase):
    def setUp(self):
        (ROOT / 'work').mkdir(exist_ok=True)
        self.tmp = tempfile.TemporaryDirectory(prefix='supervised-test-', dir=ROOT / 'work')
        self.packet = Path(self.tmp.name)
        self.ps("& " + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet))
        seed = (SQL / 'seed.sql').read_text().replace('@SeedApproved bit=0', '@SeedApproved bit=1')
        for var, char in zip(('A71', 'A72', 'B81', 'B82'), 'abcd'):
            seed = seed.replace('@' + var + ' nvarchar(255)=NULL', '@' + var + " nvarchar(255)=N'$2b$12$" + char * 53 + "'")
        (self.packet / 'seed.sql').write_text(seed)
        self.ps("& " + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet))
        self.rendered = max(self.packet.glob('rendered-*'), key=lambda p: p.stat().st_mtime_ns)
        self.fixture = self.packet / 'synthetic-fixture-placeholder'
        self.fixture.write_text('Not an authoritative fixture; fingerprint is mocked, never imported.')
        self.client = self.packet / 'mock-client'
        self.client.write_text('Never executed')
        self.mock_transport = self.packet / 'mock-native-transport.ps1'
        self.mock_transport.write_text(r"""
param([string]$Executable, [string[]]$Arguments, [switch]$RequireConsole)
try {
 $lines=@(& sqlcmd @Arguments 2>&1)
 [pscustomobject]@{ExitCode=$LASTEXITCODE; Lines=$lines; PasswordPromptObserved=$global:offlinePromptConfirmed; PasswordInputReadyShown=$global:offlinePromptConfirmed; FailureCode=''}
} catch { [pscustomobject]@{ExitCode=$null; Lines=@(); FailureCode='PROCESS_START_OR_INTERRUPTED'} }
""")

    def tearDown(self):
        self.tmp.cleanup()

    def ps(self, command, prelude='', success=True):
        # This is always defined, including parser-only and refusal tests.
        transport = ''
        if hasattr(self, 'mock_transport'):
            # Substitute ONLY the native process seam in this isolated test host.
            # The production wrapper's hashes, destination/flags and approval gates
            # still execute. The real native transport has separate process tests.
            transport = "function global:Join-Path { param($Path,$ChildPath); if ($ChildPath -ceq 'Invoke-NativeSqlcmd.ps1') { " + quote(self.mock_transport) + " } else { Microsoft.PowerShell.Management\\Join-Path -Path $Path -ChildPath $ChildPath } }; "
        code = transport + "function global:sqlcmd { throw 'OFFLINE SQLCMD BLOCKED' }; " + prelude + '; ' + command
        result = subprocess.run(['pwsh', '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', code],
                                cwd=ROOT, text=True, capture_output=True, env=os.environ.copy(), timeout=60)
        if (result.returncode == 0) != success:
            self.fail(f'Unexpected exit {result.returncode}: {result.stdout}\n{result.stderr}')
        return result

    def readiness(self, stage='15c-seed', success=True, extra=''):
        # Only the fingerprint of this synthetic sentinel is mocked.
        pre = "function global:Get-FileHash { param($LiteralPath,$Algorithm); if ($LiteralPath -eq " + quote(self.fixture) + ") { [pscustomobject]@{Hash='4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F'} } else { Microsoft.PowerShell.Utility\\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm } }; " + extra
        return self.ps('& ' + quote(TOOL / 'Test-Readiness.ps1') + ' -RenderedDirectory ' + quote(self.rendered)
                       + ' -AuthoritativeFixture ' + quote(self.fixture) + ' -Stage ' + quote(stage), pre, success)

    def test_readiness_seed_and_future_blockers_are_offline(self):
        self.readiness()
        result = self.readiness('All', success=False)
        self.assertIn('16-binding-preflight', result.stdout)
        self.assertIn('28-contained-users', result.stdout)
        self.assertNotIn('$2b$', result.stdout + result.stderr)

    def test_readiness_hash_guard_fixture_and_seed_fail_closed(self):
        stage = self.rendered / '15c-seed.sql'
        stage.write_text(stage.read_text() + '-- drift')
        self.readiness(success=False)
        self.ps('& ' + quote(TOOL / 'Test-Readiness.ps1') + ' -RenderedDirectory ' + quote(self.rendered)
                + ' -AuthoritativeFixture ' + quote(self.fixture), success=False)

    def parse(self, lines, stage='15c-seed', parts=1, success=True):
        return self.ps('. ' + quote(TOOL / 'Supervision.ps1') + '; Read-SafeResult (ConvertFrom-Json '
                       + quote(json.dumps(lines)) + ') ' + quote(stage) + ' ' + str(parts), success=success)

    def test_wrong_target_is_refused_even_with_rehashed_manifest(self):
        file = self.rendered / '15c-seed.sql'
        file.write_text(file.read_text().replace("N'ShaleMcpAcceptance'", "N'Shale'", 1))
        import csv
        manifest = self.rendered / 'sha256.csv'
        with manifest.open() as stream:
            rows = list(csv.DictReader(stream))
        for row in rows:
            if row['Stage'] == '15c-seed':
                row['SHA256'] = hashlib.sha256(file.read_bytes()).hexdigest().upper()
        with manifest.open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['Stage', 'SHA256']); writer.writeheader(); writer.writerows(rows)
        self.assertIn('guard/directive', self.readiness(success=False).stderr)

    def test_approval_and_missing_secret_dependencies_are_reported_without_values(self):
        # A fresh default seed has disabled approval and NULLs, not execution-ready.
        (self.packet / 'seed.sql').write_text((SQL / 'seed.sql').read_text())
        self.ps('& ' + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet))
        self.rendered = max(self.packet.glob('rendered-*'), key=lambda p: p.stat().st_mtime_ns)
        self.assertIn('seed approval absent', self.readiness(success=False).stderr)
        self.assertIn('provisioning approval absent', self.readiness('28-contained-users', success=False).stderr)

    def test_continuation_never_changes_existing_inputs_when_fixture_missing(self):
        inputs = {p: p.read_bytes() for p in self.packet.iterdir() if p.is_file()}
        self.ps('& ' + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet) + ' -Continuation', success=False)
        self.assertEqual(inputs, {p: p.read_bytes() for p in inputs})

    def test_status_markers_and_finding_counts(self):
        valid = ['SYNTHETIC_ROWS_ONLY_NO_AUTHORITY', 'ACCEPTANCE_SOURCE_OK:15c-seed:1']
        self.assertIn('PASS:1:0', self.parse(valid).stdout)
        for bad in ([], valid[:1], valid + valid, ['ACCEPTANCE_SOURCE_OK:wrong:1'],
                    valid + ['BadFindingCount', '---------------', '1'],
                    valid + ['BadFindingCount', '---------------', 'NULL'],
                    valid + ['BadFindingCount', '---------------', '0', '1'],
                    valid + ['BadFindingCount|Other', '---------------|----', '0|3', '1|4'],
                    valid + ['BadFindingCount'], valid + ['Msg 1934, Level 16']):
            self.parse(bad, success=False)
        self.parse(['ACCEPTANCE_SOURCE_OK:31-verifier-catalogs:1',
                    'ACCEPTANCE_SOURCE_OK:31-verifier-catalogs:2'], '31-verifier-catalogs', 2, False)
        self.parse(valid + ['BadFindingCount|Other', '---------------|----', '0|3'])

    def test_all_18_completed_synthetic_stage_bytes_match_issue_1968_baseline(self):
        # Generated from the unchanged #1968 renderer with default synthetic inputs.
        expected = {
            "00-empty": "30E5B300B2389EBD628B70E4A7C3900B63128DB7919DF70E857217CD3EEB9548",
            "01-fixture": "50121F2944E76ACF9610C5AED6A1AFE239C9F51F35797A04944B0825C2261542",
            "02-phase3b1": "0FF2A0FA6D73D2F7F7874A59BFEA0E3249A5057702FF84064D6A1549CCE02417",
            "03-phase3b2b": "7CBED0B77C65EAA6288DAE966100B55CF8C6C01DE74F1192D641BF43CD3BE7D9",
            "04-phase3b2c1": "7C34328B523DB450EEBCCA170975FE0D56213A6F5C13B37A349ED08638BA0534",
            "05-phase3b2c2": "638B58C791A647CAB19538F30ADD5A3652085803AE69C3CF16D5DF90A17484DB",
            "06-revocation": "EEC627DF2EE01253571390C5317267F6C85A27C833301CAD626BAFB0C5514D52",
            "07-phase3b2d": "09EFE0D0203C61DB657C3550A33CF03077D65056449FB6E66AB65B5DB84958B5",
            "08-phase3b3": "58311E085BBF98495CB21F206B88870D0362048234C45DDE1A9C10C98D132A3B",
            "09-normalize": "19956BA9DB6723089D5550D357B3BA412958818AE37BA9B25D54788A92AC2BAA",
            "10-phase1r-preflight": "C3EBCCFEB9F4A5CDE7BEDAB65ECE93ABFC4230970D96E4E137F94A04D2D685C9",
            "11-phase1r-apply": "C3EBCCFEB9F4A5CDE7BEDAB65ECE93ABFC4230970D96E4E137F94A04D2D685C9",
            "12-phase1r-postcheck": "C3EBCCFEB9F4A5CDE7BEDAB65ECE93ABFC4230970D96E4E137F94A04D2D685C9",
            "13-v2-prerequisite": "A18FF6E909CBAC2A3BECA336C588544B405EAE3611559514E1D84BBF465E7237",
            "14-v2-install": "17926DD56BE588FF0585F81CEFB95080DEE7BF6C00AA349B182C1C47F12808E3",
            "15-v2-verify": "A0DBD5B566C78ED36781DE45BF415307B8FBA21E9594BD7BDFF50AC898308814",
            "15a-browser-auth": "C4B7E13A7C2D49174473A7429AC9A56D0455E88E2099E207EC40F29421448CB8",
            "15b-foundation-users": "BC93F1075BF6DA7D17BE09239EB61EBE213B58C9E995F8ECC70B241E47BAFCCA"
        }
        # The historical auth source is outside the LF-pinned AI subtree. Build
        # its unchanged legacy fence from raw bytes so Windows checkout CRLF is
        # preserved rather than changing module bytes or weakening the assertion.
        fence = (SQL / 'target.sql').read_bytes() + b'\n' + (SQL / 'owner.sql').read_bytes() + b'\nGO\n'
        auth = (ROOT / 'docs/sql/2026-10-14_pretenant_authentication_modules.sql').read_bytes()
        expected['15a-browser-auth'] = hashlib.sha256(b':on error exit\n' + fence + auth + b'\nGO\n' + fence).hexdigest().upper()
        for name, digest in expected.items():
            self.assertEqual(digest, hashlib.sha256((self.rendered / (name + ".sql")).read_bytes()).hexdigest().upper(), name)

    def test_seed_preamble_changes_only_remaining_stages(self):
        seed = (self.rendered / '15c-seed.sql').read_text()
        body = seed.index('/* Synthetic rows')
        # Includes expand to the same guarded source, after the seven options.
        self.assertLess(seed.index(XML_SESSION), body)
        self.assertEqual(1, seed.count(XML_SESSION))
        self.assertIn('BEGIN TRANSACTION;', seed)
        self.assertIn('ROLLBACK;', seed)
        # Completed stages 15a/15b retain no new session/markers.
        for name in ('15a-browser-auth', '15b-foundation-users'):
            text = (self.rendered / (name + '.sql')).read_text()
            self.assertNotIn(XML_SESSION, text)
            self.assertNotIn('ACCEPTANCE_SOURCE_OK', text)

    def driver(self, mode='ok', recovery=None, success=False):
        pre = r'''
$global:offlinePromptConfirmed=(MODE -ne 'missing-prompt')
$global:count=0; $global:seeded=(Test-Path -LiteralPath (Join-Path DIRECTORY 'mock-seeded')); $global:stageIndex=18
$global:specs=@(Get-Content -LiteralPath STAGEFILE -Raw | ConvertFrom-Json)
function global:Get-FileHash {
 param($LiteralPath,$Algorithm)
 if ($LiteralPath -eq FIXTURE) { [pscustomobject]@{Hash='4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F'} }
 else { Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath $LiteralPath -Algorithm $Algorithm }
}
function global:Get-Command { param($Name,$CommandType,$ErrorAction); [pscustomobject]@{Source=CLIENT} }
function global:Read-Host {
 param($Prompt)
 if ($Prompt -like '*SHA256*') {
  $name=if ($Prompt -like '*reconciliation*') {'reconcile'} else {$global:specs[$global:stageIndex].name}
  if (MODE -eq 'bad-hash' -and $name -eq '15c-seed') { return ('0'*64) }
  return (Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath (Join-Path DIRECTORY ($name+'.sql')) -Algorithm SHA256).Hash
 }
 if ($Prompt -like '*Exact independently verified ACCEPTANCE_SNAPSHOT*') { return ('ACCEPTANCE_SNAPSHOT:'+('A'*64)+':'+$(if ($global:seeded) {'2:4:3'} else {'0:0:0'})) }
 if ($Prompt -like '*type * (anything else stops)*') {
  if (MODE -eq 'refuse') { return 'NO' }
  if ($Prompt -like '*type CONTINUE*' -and MODE -ne 'all') { return 'STOP' }
  return [regex]::Match($Prompt,'; type (.+) \(anything else stops\)').Groups[1].Value
 }
 throw 'Unexpected prompt'
}
function global:sqlcmd {
 $global:count++
 $file=$args[[array]::IndexOf($args,'-i')+1]
 [IO.File]::AppendAllText((Join-Path DIRECTORY 'mock-calls'),[IO.Path]::GetFileName($file)+"`n")
 if ($file -like '*reconcile.sql') {
  if (MODE -eq 'postcheck-sql-error' -and $global:seeded) { $global:LASTEXITCODE=16; return 'Msg 50001, Level 16, State 1, SECRET_POSTCHECK NEVER_PRINT' }
  if (MODE -eq 'interrupt-after-commit' -and $global:seeded) { throw 'PRIVATE_INTERRUPTION NEVER_PRINT' }
  $counts=if ($global:seeded) {'2:4:3'} else {'0:0:0'}
  $global:LASTEXITCODE=0
  return ('ACCEPTANCE_SNAPSHOT:'+('A'*64)+':'+$counts)
 }
 if (MODE -eq 'sql-error') { $global:LASTEXITCODE=16; 'Msg 1934, Level 16, State 1, Server PRIVATE'; return 'SECRET_BODY password_hash NEVER_PRINT' }
 if (MODE -eq 'sql-error-zero') { $global:LASTEXITCODE=0; return 'Msg 1934, Level 16, State 1, Server SECRET_BODY NEVER_PRINT' }
 if (MODE -eq 'fail') { $global:LASTEXITCODE=16; return 'SECRET_PASSWORD $2b$12$ NEVER_PRINT' }
 if (MODE -eq 'timeout') { throw 'SECRET_TIMEOUT NEVER_PRINT' }
 $global:seeded=$true; [IO.File]::WriteAllText((Join-Path DIRECTORY 'mock-seeded'),'synthetic committed state'); $global:LASTEXITCODE=0
 if (MODE -eq 'finding') { 'UnexpectedFindingCount'; '----------------'; '1' }
 if (MODE -eq 'malformed') { return 'SECRET_AMBIGUOUS NEVER_PRINT' }
 $name=[IO.Path]::GetFileNameWithoutExtension($file)
 $spec=$global:specs[$global:stageIndex]
 $global:stageIndex++
 switch ($name) {
  '15c-seed' {'SYNTHETIC_ROWS_ONLY_NO_AUTHORITY'}
  '16-binding-preflight' {'EMPTY_READY_FOR_SEPARATE_INSTALL_AUTHORIZATION'}
  '17-binding-install' {'INSTALLED_ONE_BINDING'}
  '18-binding-postcheck' {'BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED'}
  '30-owner-final' {'INSTALLED_OFF_PENDING_RESTRICTED_NATIVE_AND_APP_ACCEPTANCE'}
  default {if ($name -match '-identity-') {'CONTAINED_PRINCIPAL_AND_TOKEN_CHECK_ONLY'}}
 }
 $findings=switch ($name) {
  { $_ -cin @('16-binding-preflight','17-binding-install','18-binding-postcheck','19-pr3-prepare') } { 'ConfinementCatalogDriftFindingCount' }
  '20-pr3' { 'ApplicationCatalogDriftFindingCount'; 'ConfinementCatalogDriftFindingCount'; 'RegistryConfinementFindingCount' }
  '30-owner-final' { 'ExternalDataSourceFindingCount'; 'ExternalTableFindingCount'; 'ScopedCredentialFindingCount'; 'SynonymFindingCount'; 'CrossDatabaseDependencyFindingCount'; 'OwnershipRouteFindingCount'; 'PublicAuthorityFindingCount'; 'RegistryConfinementFindingCount' }
  '31-verifier-catalogs' { 'ConfinementCatalogDriftFindingCount'; 'RegistryConfinementFindingCount' }
 }
 foreach ($finding in $findings) { $finding; '----------------'; '0' }
 for ($n=1; $n -le $spec.sources.Count; $n++) { "ACCEPTANCE_SOURCE_OK:${name}:$n" }

}
'''.replace('STAGEFILE', quote(TOOL / 'stages.json')).replace('FIXTURE', quote(self.fixture)).replace('CLIENT', quote(self.client)).replace('DIRECTORY', quote(self.rendered)).replace('MODE', quote(mode))
        command = '& ' + quote(TOOL / 'Start-SupervisedAcceptance.ps1') + ' -RenderedDirectory ' + quote(self.rendered)
        command += ' -AuthoritativeFixture ' + quote(self.fixture) + ' -OperatorUser offline_owner'
        if recovery:
            command += ' -Recovery ' + recovery
        return self.ps(command, pre, success)

    def records(self):
        return [json.loads(json.loads(line)['Payload']) for line in (self.packet / 'supervised.jsonl').read_text().splitlines()]

    def test_success_records_once_and_resume_preserves_jvm_gate(self):
        result = self.driver()
        self.assertIn('Verified 19/51', result.stdout)
        self.assertEqual(['baseline', 'pending', 'verified'], [r['Kind'] for r in self.records()])
        # New mock connection reports empty tables, unlike recorded seeded state.
        result = self.driver()
        self.assertIn('pre-Stage-16 worker/JVM/timezone', result.stderr)
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))

    def test_declined_transition_or_bad_hash_never_executes_stage(self):
        for mode in ('refuse', 'bad-hash'):
            self.driver(mode)
        calls = self.rendered / 'mock-calls'
        self.assertNotIn('15c-seed.sql', calls.read_text() if calls.exists() else '')

    def test_failure_and_interruption_no_retry_and_explicit_rollback_recovery(self):
        result = self.driver('fail')
        self.assertNotIn('NEVER_PRINT', result.stdout + result.stderr)
        self.assertEqual('failed', self.records()[-1]['Kind'])
        self.assertEqual(16, self.records()[-1]['ExitCode'])
        self.driver('fail')
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))
        self.driver(recovery='VerifiedRolledBack', success=True)
        self.assertEqual('rolledback', self.records()[-1]['Kind'])
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))
        self.driver()  # fresh explicit approval, never an automatic retry
        self.assertEqual('verified', self.records()[-1]['Kind'])

    def test_unconfirmed_native_prompt_refuses_even_valid_snapshot_before_import(self):
        result = self.driver('missing-prompt')
        self.assertIn('category=NATIVE_PROMPT_UNCONFIRMED', result.stdout)
        self.assertFalse((self.packet / 'supervised.jsonl').exists())
        self.assertNotIn('15c-seed.sql', (self.rendered / 'mock-calls').read_text())

    def test_sql_error_diagnostics_retain_only_number_exit_and_stage(self):
        result = self.driver('sql-error')
        self.assertIn('stage=15c-seed exit=16 SQL-errors=[1934]', result.stdout)
        record = self.records()[-1]
        self.assertEqual([1934], record['SqlErrorNumbers'])
        self.assertEqual('SQL_ERROR', record['FailureCode'])
        self.assertEqual(16, record['ExitCode'])
        self.assertEqual('failed', record['Kind'])
        combined = result.stdout + result.stderr + (self.packet / 'supervised.jsonl').read_text()
        self.assertNotIn('NEVER_PRINT', combined)
        self.assertNotIn('password_hash', combined)
        self.driver()  # unresolved error never retries or advances
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))

    def test_postcheck_error_preserves_both_exits_and_uncertain_stage_outcome(self):
        result = self.driver('postcheck-sql-error')
        record = self.records()[-1]
        self.assertEqual('15c-seed', record['Stage'])
        self.assertEqual('failed', record['Kind'])
        self.assertEqual(0, record['ExitCode'])
        self.assertEqual(16, record['VerificationExitCode'])
        self.assertEqual([50001], record['SqlErrorNumbers'])
        self.assertEqual('POSTCHECK_SQL_ERROR', record['FailureCode'])
        self.assertNotIn('NEVER_PRINT', result.stdout + result.stderr + (self.packet / 'supervised.jsonl').read_text())
        self.driver()
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))

    def test_sql_error_header_stops_even_with_zero_native_exit(self):
        self.driver('sql-error-zero')
        record = self.records()[-1]
        self.assertEqual(0, record['ExitCode'])
        self.assertEqual([1934], record['SqlErrorNumbers'])
        self.assertEqual('failed', record['Kind'])

    def test_verified_rollback_allows_new_independently_approved_hash_without_erasing_history(self):
        self.driver('fail')
        old_hash = self.records()[-1]['SHA256']
        self.driver(recovery='VerifiedRolledBack', success=True)
        # Represents an independently reviewed, newly rendered source correction.
        file = self.rendered / '15c-seed.sql'
        file.write_text(file.read_text() + '\n-- reviewed correction in a new rendering\n')
        import csv
        manifest = self.rendered / 'sha256.csv'
        with manifest.open() as stream:
            rows = list(csv.DictReader(stream))
        new_hash = hashlib.sha256(file.read_bytes()).hexdigest().upper()
        for row in rows:
            if row['Stage'] == '15c-seed':
                row['SHA256'] = new_hash
        with manifest.open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['Stage', 'SHA256']); writer.writeheader(); writer.writerows(rows)
        self.driver()  # requires a fresh hash and execution approval
        records = self.records()
        self.assertEqual('verified', records[-1]['Kind'])
        self.assertEqual(old_hash, records[1]['SHA256'])
        self.assertEqual(new_hash, records[-1]['SHA256'])
        self.assertNotEqual(old_hash, new_hash)

    def test_malformed_stage_output_is_redacted_and_never_advances(self):
        result = self.driver('malformed')
        self.assertNotIn('NEVER_PRINT', result.stdout + result.stderr)
        self.assertEqual('failed', self.records()[-1]['Kind'])
        self.assertEqual(0, self.records()[-1]['ExitCode'])

    def test_timeout_and_malformed_output_stay_pending_and_redacted(self):
        result = self.driver('timeout')
        self.assertNotIn('NEVER_PRINT', result.stdout + result.stderr)
        self.assertEqual('failed', self.records()[-1]['Kind'])

    def test_entire_remaining_sequence_requires_approvals_and_selects_actual_principals(self):
        # Synthetic prerequisites only; every SQL invocation remains a mock.
        binding = (self.packet / 'binding-inputs.sql').read_text()
        for flag in ('Approved', 'OffConfirmed', 'EvidenceConfirmed'):
            binding = binding.replace('@Bind' + flag + ' bit=0', '@Bind' + flag + ' bit=1')
        binding = binding.replace('@BindJavaZone nvarchar(max)=NULL', "@BindJavaZone nvarchar(max)=N'UTC'")
        binding = binding.replace('@BindSqlZone nvarchar(max)=NULL', "@BindSqlZone nvarchar(max)=N'UTC'")
        binding += "\nINSERT @BindWallTimes VALUES\n" + ',\n'.join(
            f"('{year}-{month:02}-15T00:00:00','{year}-{month:02}-15T00:00:00')"
            for year in range(2025, 2029) for month in (1, 3, 4, 7, 10, 11, 12)) + ';\n'
        (self.packet / 'binding-inputs.sql').write_text(binding)
        identities = (self.packet / 'identities.sql').read_text().replace('@ProvisionApproved bit=0', '@ProvisionApproved bit=1')
        import re
        index = iter(range(12))
        identities = re.sub(r"(\(N'mcpacc_[a-z_]+',N'[A-Za-z0-9]+',)NULL(\))",
                            lambda m: m[1] + "N'OfflineSecretForUser" + str(next(index)) + "Only'" + m[2], identities)
        (self.packet / 'identities.sql').write_text(identities)
        foundation = (self.packet / 'identities-foundation.sql').read_text()
        index = iter(range(5))
        foundation = re.sub(r"(\(N'mcpacc_[a-z_]+',(?:N'[A-Za-z0-9]+'|NULL),)NULL(\))",
                            lambda m: m[1] + "N'ExistingFoundationSecret" + str(next(index)) + "Only'" + m[2], foundation)
        (self.packet / 'identities-foundation.sql').write_text(foundation)
        self.ps('& ' + quote(TOOL / 'Prepare-Packet.ps1') + ' -PacketDirectory ' + quote(self.packet))
        self.rendered = max(self.packet.glob('rendered-*'), key=lambda p: p.stat().st_mtime_ns)
        result = self.driver('all', success=True)
        self.assertIn('51/51 SQL preparation stages verified', result.stdout)
        records = self.records()
        self.assertEqual([s['name'] for s in STAGES[18:]], [r['Stage'] for r in records if r['Kind'] == 'verified'])
        for stage in STAGES[18:]:
            user = stage.get('user', 'offline_owner')
            self.assertIn('login=' + user, result.stdout)
        self.assertNotIn('OfflineSecretForUser', result.stdout + result.stderr)

    def test_interruption_after_commit_can_be_independently_recovered_without_replay(self):
        result = self.driver('interrupt-after-commit')
        self.assertNotIn('NEVER_PRINT', result.stdout + result.stderr)
        self.assertEqual('failed', self.records()[-1]['Kind'])
        self.driver()  # live state does not authorize a retry
        self.driver(recovery='VerifiedCommitted', success=True)
        self.assertEqual('verified', self.records()[-1]['Kind'])
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))

    def test_nonzero_finding_in_successful_process_blocks_advancement(self):
        self.driver('finding')
        self.assertEqual('failed', self.records()[-1]['Kind'])
        self.assertEqual(0, self.records()[-1]['ExitCode'])
        self.driver()
        self.assertEqual(1, (self.rendered / 'mock-calls').read_text().count('15c-seed.sql'))

    def test_hash_chain_tampering_refuses_before_any_connection(self):
        self.driver('bad-hash')  # baseline only, no seed execution
        ledger = self.packet / 'supervised.jsonl'
        ledger.write_text(ledger.read_text().replace('IMPORTED_18', 'TAMPERED_18'))
        calls = (self.rendered / 'mock-calls').read_text()
        self.driver()
        self.assertEqual(calls, (self.rendered / 'mock-calls').read_text())

    @unittest.skipIf(os.name == 'nt', 'symlink creation requires Windows developer privilege')
    def test_linked_private_path_is_refused(self):
        # Target remains synthetic and within the temporary test packet.
        linked = self.packet / 'linked-rendering'
        linked.symlink_to(self.rendered, target_is_directory=True)
        self.rendered = linked
        self.assertIn('linked private path refused', self.readiness(success=False).stderr)

    def test_existing_ledger_without_new_diagnostic_fields_remains_resumable(self):
        self.driver('bad-hash')  # imports baseline, never dispatches seed
        ledger = self.packet / 'supervised.jsonl'
        outer = json.loads(ledger.read_text())
        record = json.loads(outer['Payload'])
        for field in ('SqlErrorNumbers', 'FailureCode', 'VerificationExitCode'):
            record.pop(field)
        payload = json.dumps(record, separators=(',', ':'))
        digest = hashlib.sha256(('0' * 64 + payload).encode()).hexdigest().upper()
        ledger.write_text(json.dumps({'Payload': payload, 'Hash': digest}) + '\n')
        result = self.driver('bad-hash')
        self.assertIn('independent hash approval mismatch', result.stderr)
        self.assertNotIn('ledger corrupt', result.stderr)
        self.assertEqual(1, len(self.records()))
        self.assertNotIn('15c-seed.sql', (self.rendered / 'mock-calls').read_text())

    def test_missing_ledger_newline_is_an_interruption_and_never_connects(self):
        self.driver('bad-hash')
        ledger = self.packet / 'supervised.jsonl'
        ledger.write_text(ledger.read_text().rstrip('\n'))
        before = (self.rendered / 'mock-calls').read_text()
        result = self.driver()
        self.assertIn('missing final newline', result.stderr)
        self.assertEqual(before, (self.rendered / 'mock-calls').read_text())

    def test_corrupt_ledger_never_connects(self):
        (self.packet / 'supervised.jsonl').write_text('{truncated')
        self.driver()
        self.assertFalse((self.rendered / 'mock-calls').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
