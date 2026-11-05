"""Offline safety/source guards. Never imports or executes PowerShell, SQL or Azure."""
import hashlib
import json
from pathlib import Path
import re
import unittest

PACKET=Path(__file__).resolve().parent
ROOT=PACKET.parents[2]

class PacketSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql=(PACKET/'catalog.sql').read_text()
        cls.ps=(PACKET/'collect.ps1').read_text()
        cls.contract=json.loads((PACKET/'contract.json').read_text())

    def test_sql_has_no_mutation_includes_dynamic_or_application_execution(self):
        code=re.sub(r'--[^\n]*','',self.sql)
        code=re.sub(r"N?'(?:''|[^'])*'","''",code)
        self.assertNotRegex(code,r'(?i)\b(CREATE|ALTER|DROP|GRANT|DENY|REVOKE|INSERT|UPDATE|DELETE|MERGE|TRUNCATE|INTO)\b')
        code=re.sub(r"(?i)\bEXECUTE\s+AS\s+USER\s*=\s*''\s*;",'',code)
        self.assertNotRegex(code,r'(?i)\bEXEC(?:UTE)?\b')
        self.assertNotRegex(code,r'(?im)^\s*:\w|!!|\$\(')
        self.assertNotRegex(code,r'(?i)\b(?:FROM|JOIN)\s+(?:\[?dbo\]?\s*\.|\[?sec\]?\s*\.)')
        self.assertNotIn('sys.sp_set_session_context',self.sql)
        self.assertNotRegex(code,r'(?i)\b(?:BEGIN\s+TRAN|COMMIT|ROLLBACK)\b')

    def test_all_required_sql_facts_are_present_once_and_complete_is_last(self):
        facts=re.findall(r'FACT\|([A-Z_0-9]+)\|',self.sql)
        self.assertEqual({name for name in facts if facts.count(name)>1},{'SQL_TARGET','SQL_VISIBILITY'})
        self.assertEqual(2,facts.count('SQL_TARGET'));self.assertEqual(2,facts.count('SQL_VISIBILITY'))
        self.assertTrue(set(self.contract['requiredSqlFacts'])<=set(facts))
        self.assertEqual('SQL_COMPLETE',facts[-1])
        self.assertIn("'FACT|SQL_VISIBILITY|UNKNOWN|1|-'",self.sql)
        self.assertIn("USER_NAME()<>N'dbo'",self.sql)
        self.assertIn("ORIGINAL_LOGIN()<>N'brian'",self.sql)

    def test_exact_module_hashes_come_from_the_two_real_create_batches(self):
        source=(ROOT/'docs/sql/2026-10-14_pretenant_authentication_modules.sql').read_text()
        bodies={}
        for batch in re.split(r'^GO\s*$',source,flags=re.M):
            m=re.search(r'CREATE PROCEDURE dbo\.(\w+)',batch)
            if m:
                body=batch[batch.index('CREATE PROCEDURE'):].replace('\r\n','\n')
                bodies[m[1]]={'lf':hashlib.sha256(body.encode('utf-16le')).hexdigest().upper(),
                    'crlf':hashlib.sha256(body.replace('\n','\r\n').encode('utf-16le')).hexdigest().upper()}
        self.assertEqual(bodies,self.contract['modules'])
        for variants in bodies.values():
            for value in variants.values(): self.assertIn(value,self.sql)
        self.assertEqual(2,len(bodies))
        installer=(ROOT/'docs/sql/auth/permission-cutover/install.sql').read_text()
        self.assertEqual(6,len(re.findall(r'(?m)^(?:GRANT|DENY) EXECUTE ON OBJECT::',installer)))
        self.assertIn('ABS(6-',self.sql)
        self.assertIn('STAGE_A_WRAPPERS',self.sql)

    def test_complete_declared_off_controls_and_no_default_pass_for_effective_state(self):
        props=(ROOT/'shale-server/src/main/resources/application.properties').read_text()
        off=self.contract['offControls']; self.assertEqual(8,len(set(off)))
        for name in off:self.assertIn(name+'=false',props)
        self.assertIn("Fact 'EFFECTIVE_CONFIG_REVIEW' 'UNKNOWN' 1",self.ps)
        self.assertIn("Fact 'ACTIVE_ARTIFACT_REVIEW' 'UNKNOWN' 1",self.ps)
        self.assertIn("Fact 'OFF_CONFIGURED' $off",self.ps)
        self.assertIn("if($valid.Count -eq 0){$missing++}",self.ps)

    def test_transport_is_fixed_tls_password_child_only_and_errors_not_printed(self):
        self.assertIn('tcp:shale.database.windows.net,1433',self.ps)
        self.assertIn('-N -b -V 16 -l 5 -t 30',self.ps)
        self.assertNotRegex(self.ps,r'(?m)^\s*\$env:SQLCMDPASSWORD\s*=')
        self.assertIn("$start.EnvironmentVariables['SQLCMDPASSWORD']",self.ps)
        self.assertIn('ZeroFreeBSTR',self.ps)
        self.assertIn('$p.StandardError.ReadToEndAsync()',self.ps)
        self.assertIn('SQL_TRANSPORT',self.ps)
        self.assertNotRegex(self.ps,r'(?i)(Write-(?:Host|Output|Error|Warning|Verbose|Debug)|throw)\s+\$(?:raw|discard|stderr|settings|map|token|access|url|who|_)\b')
        self.assertNotIn('SkipCertificateCheck',self.ps)
        self.assertNotIn('trustServerCertificate=true',self.ps)

    def assert_password_environment_is_usable(self, source):
        arguments=re.search(r"(?m)^\s*\$start\.Arguments='([^']*)'",source).group(1)
        # Case matters: -x disables substitution; -X/-X1 suppress SQLCMDPASSWORD.
        self.assertRegex(arguments,r'(?:^|\s)-x(?:\s|$)')
        self.assertNotRegex(arguments,r'(?:^|\s)-X\S*')
        self.assertNotRegex(arguments,r'(?:^|\s)-P(?:\s|$)')
        self.assertIn("$start.EnvironmentVariables['SQLCMDPASSWORD']",source)
        self.assertLess(source.index("$start.EnvironmentVariables.Remove('SQLCMDINI')"),source.index('[void]$p.Start()'))
        self.assertIn("[void]$p.Start(); $start.EnvironmentVariables.Remove('SQLCMDPASSWORD')",source)

    def test_child_password_transport_does_not_disable_its_environment(self):
        self.assert_password_environment_is_usable(self.ps)

    def test_password_environment_guard_rejects_original_conflict(self):
        for switch in ('-X','-X1','-X 1'):
            with self.subTest(switch=switch),self.assertRaises(AssertionError):
                self.assert_password_environment_is_usable(self.ps.replace('-x -i','-x '+switch+' -i',1))

    def test_input_guard_rejects_sqlcmd_commands_before_password_prompt(self):
        pattern=re.search(r"\$unsafeSqlcmd='([^']+)'",self.ps).group(1)
        self.assertIsNone(re.search(pattern,self.sql),'The reviewed SQL must have no sqlcmd metacommands.')
        for command in (':r other.sql',':connect other-server',':setvar X Y',':out output.txt',
                        ':error error.txt','!! whoami','ED','EDIT','EXIT','QUIT','RESET','LIST','HELP',
                        '/* comment\n  :r hidden.sql\n*/','/* !! hidden command */'):
            with self.subTest(command=command):self.assertIsNotNone(re.search(pattern,command))
        self.assertIn('[IO.File]::ReadAllText($script) -match $unsafeSqlcmd',self.ps)
        self.assertLess(self.ps.index('$unsafeSqlcmd='),self.ps.index('$secure=Read-Host'))

    def test_explicit_single_binary_avoids_path_arrays_and_rejects_go_help(self):
        self.assertNotIn('Get-Command',self.ps)
        self.assertNotIn("$SqlcmdPath='sqlcmd.exe'",self.ps)
        self.assertIn('[IO.Path]::IsPathRooted($SqlcmdPath)',self.ps)
        self.assertIn('Get-Item -LiteralPath $SqlcmdPath',self.ps)
        self.assertIn('$candidates.Count -ne 1',self.ps)
        self.assertIn('$exe=[string]$candidates[0].FullName',self.ps)
        banner,go=re.search(r"\$help -notmatch '([^']+)' -or \$help -match '([^']+)'",self.ps).groups()
        accepted=lambda text:bool(re.search(banner,text)) and not re.search(go,text)
        self.assertTrue(accepted('Microsoft (R) SQL Server Command Line Tool\nVersion 18.4.0001.1 NT'))
        for text in ('Usage:\n  sqlcmd [command]\nVersion: 1.10.0',
                     'sqlcmd\n -? (usage)', 'go-sqlcmd version 1.10.0',
                     'Microsoft (R) SQL Server Command Line Tool\ngo-mssqldb'):
            with self.subTest(help=text):self.assertFalse(accepted(text))
        self.assertLess(self.ps.index('$help=(& $exe'),self.ps.index('$secure=Read-Host'))
        readme=(PACKET/'README.md').read_text()
        self.assertNotIn('(Get-Command sqlcmd.exe',readme)
        self.assertIn(r'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE',readme)

    def test_only_read_azure_operations_and_get_without_credential_fallback(self):
        for args in re.findall(r'AzJson @\(([^\n]*)\)',self.ps):
            self.assertTrue(any(x in args for x in ["'webapp','show'","'webapp','config','show'","'webapp','config','appsettings','list'","'account','get-access-token'"]),args)
        self.assertIn('$handler.AllowAutoRedirect=$false',self.ps)
        self.assertEqual(2,len(re.findall(r'GetByteArrayAsync\(',self.ps)))
        self.assertNotRegex(self.ps,r'(?i)\b(?:PutAsync|PostAsync|DeleteAsync|Invoke-Expression|Start-Job)\b')
        self.assertNotIn('publishing-credentials',self.ps)
        self.assertNotIn('publishing-profile',self.ps)
        self.assertNotIn('api/command',self.ps)

    def test_record_cannot_silently_override_failure_source_or_inventory(self):
        self.assertIn("$all[$name].status -eq 'UNKNOWN'",self.ps)
        self.assertNotIn("$all[$name].status -eq 'FAIL'",self.ps)
        self.assertIn('$record.sourceSha -ceq $source',self.ps)
        self.assertIn('$record.sqlInventorySha256 -ieq (Get-FileHash',self.ps)
        self.assertIn('$record.azureInventorySha256 -ieq (Get-FileHash',self.ps)
        self.assertIn("'scopeComplete') -ceq 'PASS'",self.ps)
        self.assertIn('(ReviewRecordMatches $proof)',self.ps)
        self.assertIn('Get-FileHash -LiteralPath $file.FullName',self.ps)
        self.assertIn("$all['ARTIFACT_ON_DISK'].status -eq 'PASS'",self.ps)
        self.assertIn("$all[$name].findingCount -ne 0",self.ps)
        self.assertIn('DORMANT_JAR_COMPATIBILITY BLOCKED',self.ps)

    def test_session_and_remember_counts_and_no_application_rows(self):
        self.assertIn('ABS(24-',self.sql)
        self.assertIn('ABS(11-',self.sql)
        self.assertIn("N'AFTER INSERT'",self.sql)
        self.assertIn("N'AFTER UPDATE'",self.sql)
        self.assertIn("N'sec.fn_filterbytenant(shaleclientid)'",self.sql)
        self.assertIn('SESSION_CATALOG_REVIEW|UNKNOWN',self.sql)
        self.assertIn('ORDINARY_API_REVIEW|UNKNOWN',self.sql)
        self.assertIn('STRICT_PREDICATE_REVIEW|UNKNOWN',self.sql)
        self.assertNotRegex(self.sql,r'(?i)\bFROM\s+dbo\.\w+')

if __name__=='__main__':unittest.main()
