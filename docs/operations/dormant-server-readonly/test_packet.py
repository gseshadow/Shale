"""Offline safety/source guards. Never imports or executes PowerShell, SQL or Azure."""
import hashlib
import json
from pathlib import Path
import re
import sqlite3
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

    def test_localization_preserves_every_original_sql_byte(self):
        original=re.sub(r'-- BEGIN LOCALIZED SESSION_RLS.*?-- END LOCALIZED SESSION_RLS\n\n','',self.sql,flags=re.S)
        original=re.sub(r'-- BEGIN LOCALIZED RUNTIME_NONBYPASS.*?-- END LOCALIZED RUNTIME_NONBYPASS\n','',original,flags=re.S)
        self.assertEqual('a24a67dbaa6ccf19de39334bb950538993d6425871d932b17eaa37eef9052ca3',
                         hashlib.sha256(original.encode()).hexdigest())

    def test_exact_localized_fact_names_and_output_allowlist(self):
        expected={f'SESSION_RLS_{table}_{slot}_{metric}'
            for table in ('USERSESSIONS','SESSIONAUDIT')
            for slot in ('FILTER','BLOCK_AFTER_INSERT','BLOCK_AFTER_UPDATE')
            for metric in ('PRESENT','POLICY_SCHEMA','POLICY_OWNER','ENABLED','SCHEMA_BOUND',
                           'TYPE_OPERATION','FUNCTION_DEFINITION','EXTRA_PREDICATES')}
        expected|={f'SESSION_RLS_{table}_{metric}' for table in ('USERSESSIONS','SESSIONAUDIT')
                    for metric in ('FILTER_OPERATION_NULL','UNEXPECTED_PREDICATES')}
        expected|={'RUNTIME_NONBYPASS_'+n for n in ('DB_OWNER','SHALEADMINS','CONTROL_DATABASE',
            'ALTER_ANY_USER','ALTER_ANY_ROLE','ALTER_ANY_SCHEMA','ALTER_ANY_SECURITY_POLICY',
            'IMPERSONATE_DBO','ALTER_DBO_SCHEMA','RUNTIME_ROLE_REQUIRED','OWNER_PRINCIPALS',
            'FIXED_DATABASE_ROLES','IMPERSONATE_SCOPED_PRINCIPALS','PROTECTED_OBJECT_AUTHORITY',
            'ELEVATED_OBJECT_EXECUTE')}
        self.assertEqual(67,len(expected))
        self.assertEqual(expected,set(self.contract['localizedSqlFacts']))
        self.assertEqual(67,len(self.contract['localizedSqlFacts']))
        for name in expected:
            self.assertRegex(name,r'^[A-Z][A-Z0-9_]{0,63}$')
            self.assertEqual(1,len(re.findall(r"(?:'|FACT\|)"+name+r"(?:\||')",self.sql)),name)
        self.assertIn("$name -cnotin (@($contract.requiredSqlFacts)+@($contract.localizedSqlFacts)+@('SQL_COMPLETE'))",self.ps)
        self.assertIn("$name -cin @($contract.localizedSqlFacts) -and $hash -cne '-'",self.ps)
        self.assertIn("if(-not $seen.ContainsKey($name)){throw 'Missing SQL fact.'}",self.ps)
        # Exercise the real transport regex plus fixed allowlist, without launching anything.
        grammar=re.search(r"\$line.Trim\(\) -cnotmatch '([^']+)'",self.ps).group(1)
        allowed=set(self.contract['requiredSqlFacts'])|expected|{'SQL_COMPLETE'}
        def accepted(line):
            m=re.fullmatch(grammar,line)
            return bool(m and m[1] in allowed and (m[1] not in expected or m[4]=='-'))
        for name in expected:
            for status,count in [('PASS',0),('FAIL',1),('UNKNOWN',1)]:
                self.assertTrue(accepted(f'FACT|{name}|{status}|{count}|-'))
        for raw in ('Msg 50000, Level 16, State 1','secret=private','FACT|PRODUCTION_USER|PASS|0|-',
                    'FACT|SESSION_RLS|FAIL|1|object_id=123',
                    'FACT|RUNTIME_NONBYPASS_DB_OWNER|FAIL|1|'+'A'*64):
            self.assertFalse(accepted(raw),raw)

    def rls_fixture(self, mutation=None):
        # Run the actual relational diagnostic core against synthetic catalog rows.
        # SQLite adapts only dialect functions; no SQL Server/ODBC connection exists.
        db=sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.execute("ATTACH DATABASE ':memory:' AS sys")
        db.executescript('''
          CREATE TABLE sys.security_predicates(security_predicate_id,object_id,target_object_id,
            predicate_type_desc,operation_desc,predicate_definition);
          CREATE TABLE sys.security_policies(object_id,schema_id,name,principal_id,is_enabled,is_schema_bound);
          CREATE TABLE sys.schemas(schema_id,name,principal_id);
          INSERT INTO sys.schemas VALUES(1,'sec',1);
          INSERT INTO sys.security_policies VALUES(10,1,'TenantFilter',NULL,1,1);
        ''')
        db.create_function('OBJECT_ID',1,lambda n:{'dbo.UserSessions':20,'dbo.SessionSecurityAuditLog':21}.get(n))
        db.create_function('DATABASE_PRINCIPAL_ID',1,lambda n:1 if n=='dbo' else None)
        for target in (20,21):
            for i,(kind,op) in enumerate([('FILTER',None),('BLOCK','AFTER INSERT'),('BLOCK','AFTER UPDATE')]):
                db.execute('INSERT INTO sys.security_predicates VALUES(?,?,?,?,?,?)',
                  (target*10+i,10,target,kind,op,'[sec].[fn_FilterByTenant]([ShaleClientId])'))
        if mutation:db.executescript(mutation)
        core=self.sql.split('-- BEGIN LOCALIZED SESSION_RLS',1)[1]
        core=core[core.index(';WITH Expected'):core.index("SELECT CONVERT(varchar(300),CONCAT('FACT|',FactName")]
        core=re.sub(r'--[^\n]*','',core).replace('COUNT_BIG','COUNT')
        core=re.sub(r"\bN'","'",core)
        core=core.replace('AS (SELECT * FROM (VALUES','AS (VALUES')
        core=core.replace(')v(i,t,k,o)),', '),').replace(')v(i,n,m)),', '),')
        core=core.replace("'dbo.'+e.T","'dbo.'||e.T")
        return {name:(unknown,bad) for name,unknown,bad in db.execute(core+'SELECT FactName,UnknownCount,FindingCount FROM Results')}

    def test_rls_faults_are_localized_without_guessing_slot_assignment(self):
        clean=self.rls_fixture()
        self.assertEqual(48,len(clean))
        self.assertEqual({'SESSION_RLS_USERSESSIONS_FILTER_TYPE_OPERATION',
                          'SESSION_RLS_SESSIONAUDIT_FILTER_TYPE_OPERATION'},
                         {n for n,(u,b) in clean.items() if b})
        fixtures=[
          ('PRESENT','DELETE FROM sys.security_predicates WHERE security_predicate_id=201;'),
          ('POLICY_SCHEMA',"UPDATE sys.security_policies SET name='synthetic_wrong';"),
          ('POLICY_OWNER','UPDATE sys.schemas SET principal_id=2;'),
          ('ENABLED','UPDATE sys.security_policies SET is_enabled=0;'),
          ('SCHEMA_BOUND','UPDATE sys.security_policies SET is_schema_bound=0;'),
          ('TYPE_OPERATION',"UPDATE sys.security_predicates SET predicate_type_desc='FILTER' WHERE security_predicate_id=201;"),
          ('FUNCTION_DEFINITION',"UPDATE sys.security_predicates SET predicate_definition='synthetic_wrong' WHERE security_predicate_id=201;"),
          ('EXTRA_PREDICATES',"INSERT INTO sys.security_predicates SELECT 999,object_id,target_object_id,predicate_type_desc,operation_desc,predicate_definition FROM sys.security_predicates WHERE security_predicate_id=201;")]
        for metric,mutation in fixtures:
            with self.subTest(metric=metric):
                result=self.rls_fixture(mutation)
                self.assertGreater(result['SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_'+metric][1],0)
        missing=self.rls_fixture('DELETE FROM sys.security_predicates WHERE security_predicate_id=201;')
        self.assertEqual((1,0),missing['SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_ENABLED'])
        invisible=self.rls_fixture('UPDATE sys.security_predicates SET predicate_definition=NULL WHERE security_predicate_id=201;')
        self.assertEqual((1,0),invisible['SESSION_RLS_USERSESSIONS_BLOCK_AFTER_INSERT_FUNCTION_DEFINITION'])

    def test_runtime_diagnostics_stay_inside_impersonation_and_use_original_scopes(self):
        start=self.sql.index('-- BEGIN LOCALIZED RUNTIME_NONBYPASS')
        end=self.sql.index('-- END LOCALIZED RUNTIME_NONBYPASS')
        self.assertLess(self.sql.index("EXECUTE AS USER=N'shale_runtime'"),start)
        self.assertNotIn('REVERT',self.sql[start:end])
        local=self.sql[start:end]
        for snapshot in ('Owners','FixedRoles','Principals','AuthObjects','Elevated'):
            self.assertIn('OPENJSON(@'+snapshot+')',local)
        self.assertIn("('RUNTIME_NONBYPASS_RUNTIME_ROLE_REQUIRED',IS_MEMBER(N'shale_runtime_role'),1)",local)
        self.assertIn('WHEN v.p IS NULL THEN \'UNKNOWN\'',local)
        self.assertIn('COALESCE(SUM(CASE WHEN p IS NULL THEN 1 ELSE 0 END),0)',local)
        self.assertNotRegex(local,r'(?i)ERROR_(?:MESSAGE|NUMBER)|PRINT|FOR JSON|HASHBYTES')

    def test_runtime_scalar_categories_preserve_null_and_required_membership(self):
        local=self.sql.split('-- BEGIN LOCALIZED RUNTIME_NONBYPASS',1)[1]
        values=local.split('FROM(VALUES',1)[1].split(')v(n,p,required);',1)[0]
        values=re.sub(r"\bN'","'",values).replace('@HasShaleAdmins',':has_admins')
        db=sqlite3.connect(':memory:');self.addCleanup(db.close)
        members={'db_owner':0,'ShaleAdmins':0,'shale_runtime_role':1}
        permissions={}
        db.create_function('IS_MEMBER',1,lambda name:members.get(name))
        db.create_function('DB_NAME',0,lambda:'synthetic')
        db.create_function('HAS_PERMS_BY_NAME',3,lambda name,scope,permission:permissions.get((name,scope,permission),0))
        # Use the emitted CASE expressions, including UNKNOWN and findingCount semantics.
        projection=local[local.index("CASE WHEN v.p IS NULL"):local.index(",'|-'))")]
        query='WITH v(n,p,required) AS (VALUES '+values+') SELECT n,'+projection.replace("END,'|',CASE",'END,CASE')+' FROM v'
        def run(has_admins=1):
            return {n:(status,count) for n,status,count in db.execute(query,{'has_admins':has_admins})}
        self.assertEqual({('PASS',0)},set(run().values()))
        for role in members:
            old=members[role];members[role]=None
            with self.subTest(role=role):self.assertEqual(1,sum(s=='UNKNOWN' for s,c in run().values()))
            members[role]=old
        members['ShaleAdmins']=None
        self.assertEqual(('PASS',0),run(0)['RUNTIME_NONBYPASS_SHALEADMINS'])
        members.update({'ShaleAdmins':1,'db_owner':1,'shale_runtime_role':0})
        for permission in ('CONTROL','ALTER ANY USER','ALTER ANY ROLE'):
            permissions[('synthetic','DATABASE',permission)]=1
        # Six synthetic violations demonstrate localization; they assert no production cause.
        self.assertEqual(6,sum(s=='FAIL' for s,c in run().values()))
        permissions[('dbo','USER','IMPERSONATE')]=None
        self.assertEqual(('UNKNOWN',1),run()['RUNTIME_NONBYPASS_IMPERSONATE_DBO'])

if __name__=='__main__':unittest.main()
