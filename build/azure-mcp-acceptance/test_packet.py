"""Offline packaging/refusal tests. SQLCMD is always a mock; no SQL/Azure access."""
import json
import csv
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / 'build/azure-mcp-acceptance'
SQL = ROOT / 'docs/sql/ai-integration/azure-acceptance'
STAGES = json.loads((TOOL / 'stages.json').read_text())
XML_STAGES = ('13-v2-prerequisite', '14-v2-install', '15-v2-verify')
XML_SESSION = ''.join('SET ' + name + ' ' + value + ';\n' for name, value in (
    ('ANSI_NULLS', 'ON'), ('ANSI_PADDING', 'ON'), ('ANSI_WARNINGS', 'ON'), ('ARITHABORT', 'ON'),
    ('CONCAT_NULL_YIELDS_NULL', 'ON'), ('QUOTED_IDENTIFIER', 'ON'), ('NUMERIC_ROUNDABORT', 'OFF'),
)) + 'GO\n'


class PacketTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        for tool in ('pwsh', 'javac', 'java'):
            if not shutil.which(tool):
                raise RuntimeError(f'{tool} required for offline validation; not a passing skip')
        (ROOT / 'work').mkdir(exist_ok=True)
        cls.private = tempfile.TemporaryDirectory(prefix='packet-offline-', dir=ROOT / 'work')
        cls.packet = Path(cls.private.name)
        cls.run_ps('Prepare-Packet.ps1', '-PacketDirectory', str(cls.packet))
        cls.rendered = next(cls.packet.glob('rendered-*'))

    @classmethod
    def tearDownClass(cls):
        cls.private.cleanup()

    @staticmethod
    def run_ps(script, *args, success=True, env=None):
        # A PowerShell function wins over every native SQLCMD on Windows and Linux.
        # Test failures can therefore never accidentally reach an installed real client.
        quoted = lambda value: "'" + str(value).replace("'", "''") + "'"
        command = "function global:sqlcmd { if ($env:OFFLINE_SQLCMD_ARGS) { [IO.File]::WriteAllText($env:OFFLINE_SQLCMD_ARGS, (ConvertTo-Json -InputObject @($args) -Compress)) }; if ($env:OFFLINE_SQLCMD_MARKER) { [IO.File]::WriteAllText($env:OFFLINE_SQLCMD_MARKER,'called') }; throw 'Offline tests prohibit SQLCMD' }; & " + quoted(TOOL / script)
        command += ' ' + ' '.join(arg if arg.startswith('-') else quoted(arg) for arg in args)
        result = subprocess.run(['pwsh', '-NoLogo', '-NoProfile', '-NonInteractive', '-Command', command],
                                cwd=ROOT, text=True, capture_output=True, env=env)
        if (result.returncode == 0) != success:
            raise AssertionError(f'Unexpected exit {result.returncode}: {result.stdout}\n{result.stderr}')
        return result

    def test_every_rendered_stage_is_fenced_and_no_includes_or_variables_remain(self):
        files = list(self.rendered.glob('*.sql'))
        self.assertEqual(len(STAGES), len(files))
        for file in files:
            sql = file.read_text()
            self.assertTrue(sql.startswith(':on error exit\n'))
            self.assertIn("N'ShaleMcpAcceptance'", sql[:2000])
            self.assertIn("SERVERPROPERTY('EngineEdition')", sql[:2000])
            self.assertIn("N'shale.database.windows.net'", sql[:2000])
            self.assertIn("Latin1_General", sql[:2000])
            self.assertNotRegex(sql, r'(?im)^\s*:(?!on error exit)')
            self.assertNotIn('$(', sql)
        self.assertIn('STOP: provide the unchanged hash-gated authoritative fixture', (self.rendered / '01-fixture.sql').read_text())
        self.assertIn('STOP: independent fresh-fixture Phase1R hash review', (self.rendered / '10-phase1r-preflight.sql').read_text())

    def test_source_order_and_version_selected_catalogs(self):
        names = [x['name'] for x in STAGES]
        for before, after in [('01-fixture','02-phase3b1'),('09-normalize','10-phase1r-preflight'),
                              ('12-phase1r-postcheck','13-v2-prerequisite'),('15b-foundation-users','16-binding-preflight'),
                              ('18-binding-postcheck','19-pr3-prepare'),('25-c1','26-c2'),('28-contained-users','31-verifier-catalogs')]:
            self.assertLess(names.index(before), names.index(after))
        self.assertIn('registry-dcr-v3-catalog.sql', str(STAGES))
        self.assertEqual('mcpacc_reviewer', next(s for s in STAGES if s['name']=='32-reviewer-catalog')['user'])
        for stage in STAGES:
            for source in stage['sources']:
                if ':' not in source:
                    self.assertTrue((ROOT / source).exists(), source)

    def test_xml_catalog_stages_set_all_required_options_before_each_unchanged_source(self):
        for stage in STAGES:
            sql = (self.rendered / (stage['name'] + '.sql')).read_text()
            if stage['name'] not in XML_STAGES:
                self.assertNotIn(XML_SESSION, sql, 'Session patch must stay limited to stages 13-15')
                continue
            self.assertEqual(len(stage['sources']), sql.count(XML_SESSION))
            for path in stage['sources']:
                source = (ROOT / path).read_text()
                start = sql.index(source)
                self.assertTrue(sql[:start].endswith(XML_SESSION), path + ': seven SETs and GO must precede source')
                prefix = sql[:start - len(XML_SESSION)]
                self.assertIn('STOP: only ShaleMcpAcceptance is permitted.', prefix)
                self.assertIn('STOP: existing database-local owner visibility required.', prefix)
                self.assertTrue(prefix.endswith('GO\n'), 'Destination/owner guards must finish before session setup')
            # Strip only the new setup: the rest must be the previous fenced packet.
            previous = ':on error exit\n' + (SQL / 'target.sql').read_text() + '\n' + (SQL / 'owner.sql').read_text() + '\nGO\n'
            fence = previous[len(':on error exit\n'):]
            for path in stage['sources']:
                previous += (ROOT / path).read_text() + '\nGO\n' + fence
            self.assertEqual(previous, sql.replace(XML_SESSION, ''), 'Migration/module/fingerprint bytes must be unchanged')

    def test_xml_session_changes_require_new_hash_approval_and_preserve_sqlcmd_contract(self):
        output = self.packet / 'mock-xml-sqlcmd-arguments.json'
        env = {key: value for key, value in os.environ.items()
               if not re.match(r'^(SQLCMD|SQLCONNSTR_|SQLAZURECONNSTR_|SPRING_DATASOURCE|SHALE_(APP|RT|AUTH|SERVER|SUGGESTION))', key, re.I)}
        env['OFFLINE_SQLCMD_ARGS'] = str(output)
        with (self.rendered / 'sha256.csv').open() as stream:
            manifest = {row['Stage']: row['SHA256'] for row in csv.DictReader(stream)}
        for stage in XML_STAGES:
            sql = self.rendered / (stage + '.sql')
            previous = sql.read_text().replace(XML_SESSION, '').encode('utf-8')
            args = ('-RenderedDirectory', str(self.rendered), '-Stage', stage, '-OperatorUser', 'offline_test_owner',
                    '-Execute', '-Acknowledgement', 'EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE')
            if output.exists():
                output.unlink()
            self.run_ps('Invoke-Stage.ps1', *args, '-ApprovedSHA256', hashlib.sha256(previous).hexdigest(), success=False, env=env)
            self.assertFalse(output.exists(), 'Previously approved hash must not dispatch corrected SQL')
            current = hashlib.sha256(sql.read_bytes()).hexdigest()
            self.assertEqual(current.upper(), manifest[stage])
            self.run_ps('Invoke-Stage.ps1', *args, '-ApprovedSHA256', current, success=False, env=env)
            # SQLCMD mock records arguments then throws; it can never connect.
            actual = json.loads(output.read_text())
            seconds = '600' if stage == '14-v2-install' else '20'
            self.assertEqual(['-S', 'tcp:shale.database.windows.net,1433', '-d', 'ShaleMcpAcceptance',
                              '-U', 'offline_test_owner', '-N', '-b', '-V', '16', '-r', '1', '-l', '30',
                              '-t', seconds, '-x', '-X1', '-f', '65001', '-i', str(sql)], actual)

    def test_identity_templates_default_refuse_and_no_broad_roles(self):
        foundation = (SQL / 'identities-foundation.sql').read_text()
        capability = (SQL / 'identities.sql').read_text()
        users = re.findall(r"\(N'(mcpacc_[a-z_]+)',", foundation + capability)
        self.assertEqual(17, len(set(users)))
        for text in (foundation, capability):
            self.assertIn('@ProvisionApproved bit=0', text)
            self.assertNotRegex(text, r'(?i)CREATE LOGIN|FOR LOGIN|db_owner|db_datareader|db_datawriter|GRANT .*SCHEMA::')
        self.assertNotRegex(capability, r'(?im)^\s*GRANT ')
        seed = (SQL / 'seed.sql').read_text()
        self.assertIn('@SeedApproved bit=0', seed)
        self.assertNotRegex(seed, r'(?i)SET Enabled\s*=\s*1|INSERT (?:dbo\.)?(AiOAuthCredential|AiDelegationGrant|UserSessions)')

    def test_readonly_identity_stages_do_not_require_dbo_or_change_grants(self):
        for stage in STAGES:
            if 'user' in stage:
                text = (self.rendered / (stage['name']+'.sql')).read_text()
                self.assertNotIn('existing database-local owner visibility required', text)
                # Packaged catalog text includes quoted module definitions, not executed installers.
                if stage['name'].split('-')[1] == 'identity':
                    self.assertIn('authenticating_database_id=DB_ID()', text)
                    self.assertIn('ERROR_NUMBER()<>229', text)
                    self.assertNotIn('__USER__', text)
                    self.assertNotIn('__ROLE__', text)

    def test_preview_and_bad_authorization_never_start_sqlcmd(self):
        marker = self.packet / 'sqlcmd-was-called'
        env = dict(os.environ, OFFLINE_SQLCMD_MARKER=str(marker))
        args = ('-RenderedDirectory', str(self.rendered), '-Stage', '00-empty', '-OperatorUser', 'offline_test_owner')
        self.run_ps('Invoke-Stage.ps1', *args, env=env)
        self.run_ps('Invoke-Stage.ps1', *args, '-Execute', '-Acknowledgement', 'wrong', success=False, env=env)
        self.assertFalse(marker.exists())
        self.run_ps('Invoke-Stage.ps1', '-RenderedDirectory', str(self.rendered), '-Stage', '../../Shale', '-OperatorUser', 'offline', success=False, env=env)
        self.run_ps('Invoke-Stage.ps1', '-RenderedDirectory', str(self.rendered), '-Stage', '32-reviewer-catalog', '-OperatorUser', 'offline_owner', success=False, env=env)
        self.assertFalse(marker.exists())

    def test_exact_test_origin_is_proposed_without_approving_binding(self):
        origin = 'https://shale-mcp-acceptance.azurewebsites.net'
        proposal = (self.packet / 'binding-inputs.sql').read_text()
        self.assertIn("@BindOrigin nvarchar(max)=N'" + origin + "'", proposal)
        self.assertIn("@BindJavaZone nvarchar(max)=NULL", proposal)
        self.assertIn("@BindSqlZone nvarchar(max)=NULL", proposal)
        for field in ('Approved', 'OffConfirmed', 'EvidenceConfirmed'):
            self.assertIn('@Bind' + field + ' bit=0', proposal)
        for stage in ('16-binding-preflight', '17-binding-install', '18-binding-postcheck'):
            text = (self.rendered / (stage + '.sql')).read_text()
            self.assertIn('THROW 59809', text)
            self.assertIn("N'" + origin + "/oauth/mcp'", text)
            self.assertIn("N'" + origin + "/mcp'", text)
            self.assertIn('Existing binding conflicts', text)
            self.assertNotIn(':r ', text)

    def test_existing_private_binding_inputs_are_retained_with_new_target_guard(self):
        path = self.packet / 'binding-inputs.sql'
        original = path.read_text()
        stale = original.replace('https://shale-mcp-acceptance.azurewebsites.net', 'https://old-origin.example')
        try:
            path.write_text(stale)
            before = set(self.packet.glob('rendered-*'))
            self.run_ps('Prepare-Packet.ps1', '-PacketDirectory', str(self.packet))
            self.assertEqual(stale, path.read_text(), 'Private inputs must never be silently replaced')
            new = next(iter(set(self.packet.glob('rendered-*')) - before))
            rendered = (new / '17-binding-install.sql').read_text()
            self.assertIn("@BindOrigin nvarchar(max)=N'https://old-origin.example'", rendered)
            self.assertIn('THROW 59809', rendered)
            self.assertIn("ISNULL(@BindOrigin,N''))<>CONVERT(varbinary(max),N'https://shale-mcp-acceptance.azurewebsites.net')", rendered)
        finally:
            path.write_text(original)

    def test_future_execution_arguments_only_name_acceptance_and_preserve_tls(self):
        # Mocked process dispatch only: no native SQLCMD executable or SQL connection.
        output = self.packet / 'mock-sqlcmd-arguments.json'
        env = {key: value for key, value in os.environ.items()
               if not re.match(r'^(SQLCMD|SQLCONNSTR_|SQLAZURECONNSTR_|SPRING_DATASOURCE|SHALE_(APP|RT|AUTH|SERVER|SUGGESTION))', key, re.I)}
        env['OFFLINE_SQLCMD_ARGS'] = str(output)
        sql = self.rendered / '00-empty.sql'
        self.run_ps('Invoke-Stage.ps1', '-RenderedDirectory', str(self.rendered), '-Stage', '00-empty',
                    '-OperatorUser', 'offline_test_owner', '-Execute',
                    '-ApprovedSHA256', hashlib.sha256(sql.read_bytes()).hexdigest(),
                    '-Acknowledgement', 'EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE', success=False, env=env)
        args = json.loads(output.read_text())
        self.assertEqual('tcp:shale.database.windows.net,1433', args[args.index('-S')+1])
        self.assertEqual('ShaleMcpAcceptance', args[args.index('-d')+1])
        for flag in ('-N', '-b', '-x', '-X1'):
            self.assertIn(flag, args)
        for flag in ('-C', '-P', '-E', '-G'):
            self.assertNotIn(flag, args)
        self.assertNotIn('Shale', args)
        self.assertNotIn('Shale_Copy', args)
        self.assertNotIn('auth-invocation-test', args)

    def test_untrusted_inputs_and_stale_fixture_are_refused(self):
        bad = self.packet / 'not-the-authoritative-fixture.sql'
        bad.write_text('CREATE TABLE UnsafeSubstitute(Id int);')
        self.run_ps('Prepare-Packet.ps1', '-PacketDirectory', str(self.packet), '-AuthoritativeFixture', str(bad), success=False)
        (self.packet / 'binding-inputs.sql').write_text(':connect bad-host\n')
        try:
            self.run_ps('Prepare-Packet.ps1', '-PacketDirectory', str(self.packet), success=False)
        finally:
            (self.packet / 'binding-inputs.sql').write_text((SQL / 'binding-inputs.sql').read_text())
        (self.packet / 'fixture.sql').write_text('DO NOT TRUST AN OLD EXPORT')
        self.run_ps('Prepare-Packet.ps1', '-PacketDirectory', str(self.packet))
        self.assertNotIn('DO NOT TRUST', (self.packet / 'fixture.sql').read_text())

    def test_existing_parser_and_exporter_compile_and_preserve_module_bytes(self):
        classes = self.packet / 'java-test'
        classes.mkdir(exist_ok=True)
        source = self.packet / 'ExporterOfflineTest.java'
        source.write_text('''package com.shale.data.verification;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class ExporterOfflineTest {
 public static void main(String[] args) throws Exception {
  // Unit input only, never the authoritative fixture or a runnable acceptance substitute.
  String batch="CREATE FUNCTION [sec].[test] () RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS n;";
  String sql=ExportFixture.render(List.of("SET ANSI_NULLS ON;",batch));
  String hex=sql.substring(sql.indexOf("0x")+2,sql.indexOf("); EXEC"));
  if(!batch.equals(new String(HexFormat.of().parseHex(hex),StandardCharsets.UTF_16LE)))throw new AssertionError("Definition bytes changed");
  if(!sql.contains("SET ANSI_NULLS ON;\\nGO\\n"))throw new AssertionError("Session SET changed");
  try{CaseReadVerificationSafety.ddl("USE Shale;\\nGO\\n");throw new AssertionError("USE accepted");}catch(IllegalStateException expected){}
 }
}''')
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes),
                        str(ROOT / 'shale-data/src/test/java/com/shale/data/verification/CaseReadVerificationSafety.java'),
                        str(TOOL / 'ExportFixture.java'), str(source)], check=True, capture_output=True)
        subprocess.run(['java', '-cp', str(classes), 'com.shale.data.verification.ExporterOfflineTest'], check=True, capture_output=True)
        # CLI hash check refuses unit data before producing any SQL export.
        output = self.packet / 'invalid-export.sql'
        result = subprocess.run(['java', '-cp', str(classes), 'com.shale.data.verification.ExportFixture', str(source), str(output)], capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
