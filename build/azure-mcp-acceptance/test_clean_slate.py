"""Offline only: no az invocation, SqlConnection.Open, fixture secrets or SQL execution."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).parent


class CleanSlateTest(unittest.TestCase):
    def ps(self, body):
        result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command',
            "$ErrorActionPreference='Stop'; . ./build/azure-mcp-acceptance/CleanSlate.ps1; " + body + "\nexit 0"],
            cwd=ROOT, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        return result.stdout

    def test_lexer_preserves_definitions_and_ignores_embedded_go(self):
        # A module's exact definition must survive batching; GO inside SQL literals is data.
        self.ps(r'''
$sql="SET ANSI_NULLS ON;`nGO`nCREATE PROC dbo.p AS SELECT N'alpha`nGO`nomega';`n/* nested /* GO */ end */`nGO`nSELECT 1;`n"
$b=[CleanSql]::Batches($sql)
if($b.Count -ne 3 -or $b[1] -cne "CREATE PROC dbo.p AS SELECT N'alpha`nGO`nomega';`n/* nested /* GO */ end */`n") {throw 'Definition bytes changed'}
foreach($bad in @('GO 2',':connect other','! shell',"SELECT 'unterminated")) {
 $refused=$false;try{[void][CleanSql]::Batches($bad)}catch{$refused=$true};if(-not $refused){throw 'Unsafe directive accepted'}
}
''')

    def test_reader_propagates_errors_after_success_rows_and_preserves_safe_diagnostics(self):
        # Neither an earlier SELECT nor an installer marker can hide a later driver error.
        self.ps(r'''
Add-Type -Path ./build/azure-mcp-acceptance/CleanSqlTestReader.cs
foreach($readError in @($true,$false)) {
 $lines=[Collections.Generic.List[string]]::new()
 $reader=[CleanSqlTestReader]::new($readError)
 $refused=$false;try{[CleanSql]::Drain($reader,$lines)}catch{$refused=$true}
 if(-not $refused -or -not $lines.Contains('ACCEPTANCE_SOURCE_OK:21-proof:1')){throw 'Late reader error lost or earlier diagnostics unavailable'}
}
''')

    def test_every_clean_source_has_marker_and_lexes_without_sql(self):
        # Validate all 51 stages, including dynamic module payloads, without a database.
        with tempfile.TemporaryDirectory(dir=ROOT / 'work', prefix='clean-packet-test-') as packet:
            self.ps(f"& ./build/azure-mcp-acceptance/Prepare-Packet.ps1 -PacketDirectory '{packet}' -CleanSlate | Out-Null; " + r'''
$dir=(Get-ChildItem 'PACKET' -Directory -Filter rendered-*).FullName
$stages=@(Get-Content ./build/azure-mcp-acceptance/stages.json -Raw|ConvertFrom-Json)
if($stages.Count -ne 51){throw 'Manifest count'}
foreach($s in $stages){
 $sql=[IO.File]::ReadAllText((Join-Path $dir ($s.name+'.sql')))
 $parts=Get-CleanSourceParts $sql $s
 if($parts.Count -ne $s.sources.Count){throw 'Source boundary mismatch'}
 foreach($part in $parts){[void][CleanSql]::Batches($part);if(-not $part.Contains('SET QUOTED_IDENTIFIER ON;')){throw 'SET option missing'}}
}
'''.replace('PACKET', packet))

    def test_late_error_and_open_transaction_close_without_next_batch(self):
        # Mock the transport, not SQL: no physical database can be reached.
        self.ps(r'''
$script:trace=[Collections.Generic.List[string]]::new()
$script:closed=0
function New-CleanConnection($Credential) {
 $c=[pscustomobject]@{State='Closed'}
 $c|Add-Member ScriptMethod Open {$this.State='Open'}
 $c|Add-Member ScriptMethod Dispose {$script:closed++;$this.State='Closed'}
 return $c
}
function Invoke-CleanCommand($Connection,$Sql,$Lines) {
 $script:trace.Add($Sql)
 if($Sql -eq "SELECT 'marker';`n"){$Lines.Add('ACCEPTANCE_SOURCE_OK:21-proof:1');throw 'private secret and definition'}
}
$refused=$false;try{Invoke-CleanSource "SELECT 'marker';`nGO`nSELECT 'must not run';" $null}catch{$refused=$true;if($_.Exception.Message.Contains('private')){throw 'Secret leaked'}}
if(-not $refused -or $script:closed -ne 1 -or ($script:trace -join '').Contains('must not run')){throw 'Late failure did not stop/close'}
if(-not ($script:trace -join '').Contains('IF XACT_STATE()<>0 ROLLBACK;')){throw 'Rollback cleanup absent'}
$script:trace.Clear()
function Invoke-CleanCommand($Connection,$Sql,$Lines) {
 $script:trace.Add($Sql)
 if($Sql.StartsWith('IF @@TRANCOUNT<>0')){throw 'transaction still open'}
}
$refused=$false;try{Invoke-CleanSource 'SELECT 1;' $null}catch{$refused=$true}
if(-not $refused -or $script:closed -ne 2){throw 'Uncommitted source accepted'}
''')

    def test_success_uses_two_physical_connections_and_validates_both_markers(self):
        self.ps(r'''
$script:opened=0;$script:closed=0
function New-CleanConnection($Credential) {
 $c=[pscustomobject]@{State='Closed'}
 $c|Add-Member ScriptMethod Open {$script:opened++;$this.State='Open'}
 $c|Add-Member ScriptMethod Dispose {$script:closed++;$this.State='Closed'}
 return $c
}
function Invoke-CleanCommand($Connection,$Sql,$Lines) {
 foreach($m in [regex]::Matches($Sql,"SELECT '(ACCEPTANCE_SOURCE_OK:[^']+)'")){$Lines.Add($m.Groups[1].Value)}
}
$s=[pscustomobject]@{name='21-proof';sources=@('installer','verifier')}
$sql="SELECT 'ACCEPTANCE_SOURCE_OK:21-proof:1' AS AcceptanceResult;`nGO`nSELECT 'ACCEPTANCE_SOURCE_OK:21-proof:2' AS AcceptanceResult;`nGO`n"
$result=Invoke-CleanStage $s $sql $null
if($result -cne 'PASS:2:0' -or $script:opened -ne 2 -or $script:closed -ne 2){throw 'Success did not require two fresh closed connections'}
''')

    def test_stage21_requires_fresh_independent_verifier(self):
        # Installer success cannot become stage success if objects vanish before verification.
        self.ps(r'''
$script:calls=0
function Invoke-CleanSource($Sql,$Credential) {
 $script:calls++
 if($script:calls -eq 1){return @('AcceptanceResult','---','ACCEPTANCE_SOURCE_OK:21-proof:1','')}
 throw 'verifier saw rolled-back objects'
}
$s=[pscustomobject]@{name='21-proof';sources=@('installer','verifier')}
$sql="SELECT 'ACCEPTANCE_SOURCE_OK:21-proof:1' AS AcceptanceResult;`nGO`nSELECT 'ACCEPTANCE_SOURCE_OK:21-proof:2' AS AcceptanceResult;`nGO`n"
$refused=$false;try{Invoke-CleanStage $s $sql $null}catch{$refused=$true}
if(-not $refused -or $script:calls -ne 2){throw 'Installer-only success accepted'}
$script:calls=0
function Invoke-CleanSource($Sql,$Credential){$script:calls++;throw 'installer error'}
try{Invoke-CleanStage $s $sql $null}catch{}
if($script:calls -ne 1){throw 'Verifier executed after installer error'}
''')

    def test_new_journal_refuses_tampering_and_preserves_historical_bytes(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$p='DIRECTORY/new.jsonl';$old='DIRECTORY/old.jsonl'
[IO.File]::WriteAllText($old,'historical failed/pending evidence')
$hash=(Get-FileHash $old).Hash
New-CleanEvent $p @{Kind='approved'};New-CleanEvent $p @{Kind='stage-pending';Stage='00-empty'}
[IO.File]::AppendAllText($p,'truncated')
$bytes=[IO.File]::ReadAllText($p)
$refused=$false;try{New-CleanEvent $p @{Kind='complete'}}catch{$refused=$true}
if(-not $refused -or [IO.File]::ReadAllText($p) -cne $bytes -or (Get-FileHash $old).Hash -cne $hash){throw 'Evidence modified or corruption accepted'}
'''.replace('DIRECTORY', directory))

    def test_phase1r_never_adopts_failed_comparison_or_stale_hashes(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$p='DIRECTORY/comparison.json';$inputs='DIRECTORY/phase1r.json'
[IO.File]::WriteAllText($inputs,'old-incarnation')
[IO.File]::WriteAllText($p,'{"outcome":"FAIL","discrepancies":["predicate mismatch"]}')
$refused=$false;try{Set-CleanPhase1R $p $inputs}catch{$refused=$true}
if(-not $refused -or [IO.File]::ReadAllText($inputs) -cne 'old-incarnation'){throw 'Failed baseline adopted'}
$report=@{outcome='PASS';discrepancies=@();observedFunctionHashes=@{fn_FilterByTenant=('A'*64);fn_FilterByTenantOrGlobal=('B'*64)};observedOtherPredicatesHash=('C'*64)}
[IO.File]::WriteAllText($p,($report|ConvertTo-Json))
Set-CleanPhase1R $p $inputs
$values=Get-Content $inputs -Raw|ConvertFrom-Json
if($values.ReviewedOtherPredicatesHash -cne ('C'*64)){throw 'Fresh verified hashes not installed'}
'''.replace('DIRECTORY', directory))

    def test_missing_passwords_prompt_securely_only_in_new_copies(self):
        # Whole-run approval replaces provisioning checkpoints; original private inputs stay intact.
        with tempfile.TemporaryDirectory(dir=ROOT / 'work') as directory:
            self.ps(r'''
$run='DIRECTORY/new';[void][IO.Directory]::CreateDirectory($run)
$script:prompts=0
function Get-Credential($UserName,$Message){
 $script:prompts++
 return [pscredential]::new($UserName,(ConvertTo-SecureString ("Private'PasswordFor:"+$UserName) -AsPlainText -Force))
}
foreach($file in @('identities-foundation.sql','identities.sql')){
 Copy-Item (Join-Path ./docs/sql/ai-integration/azure-acceptance $file) (Join-Path $run $file)
}
$old=(Get-FileHash ./docs/sql/ai-integration/azure-acceptance/identities.sql).Hash
$c=Get-CleanIdentityCredentials $run
if($c.Count -ne 17 -or $script:prompts -ne 17){throw 'Missing secure credential prompts'}
$c=Get-CleanIdentityCredentials $run
if($c.Count -ne 17 -or $script:prompts -ne 17){throw 'Repeated password prompt despite reusable private credentials'}
$sql=[IO.File]::ReadAllText((Join-Path $run 'identities.sql'))
if(-not $sql.Contains("Private''PasswordFor:mcpacc_case")){throw 'Private SQL literal escape lost'}
if((Get-FileHash ./docs/sql/ai-integration/azure-acceptance/identities.sql).Hash -cne $old){throw 'Original template changed'}
'''.replace('DIRECTORY', directory))

    def test_connection_construction_pins_target_without_password_in_connection_string(self):
        # Constructing a closed connection is offline; never call Open in this test.
        self.ps(r'''
$c=New-CleanConnection ([pscredential]::new('offline-owner',(ConvertTo-SecureString 'SyntheticOfflineSecretOnly' -AsPlainText -Force)))
if($c.Database -cne 'ShaleMcpAcceptance' -or $c.DataSource -cne 'tcp:shale.database.windows.net,1433' -or $c.ConnectionString.Contains('SyntheticOfflineSecretOnly')){throw 'Credential or target contract failed'}
if($c.State -cne 'Closed' -or -not $c.Credential.Password.IsReadOnly()){throw 'Unsafe connection construction'}
$c.Dispose()
''')

    def test_fixed_targets_and_single_destructive_approval(self):
        # Security contract: no configurable connection destination or password argv path.
        helper = (TOOL / 'CleanSlate.ps1').read_text()
        entry = (TOOL / 'Start-CleanSlateAcceptance.ps1').read_text()
        reset = (TOOL / 'CleanSlateReset.sql').read_text()
        self.assertIn("$builder['Initial Catalog']='ShaleMcpAcceptance'", helper)
        self.assertIn("$builder['Data Source']='tcp:shale.database.windows.net,1433'", helper)
        self.assertIn("$builder['Pooling']=$false", helper)
        self.assertIn('SqlCredential', helper)
        self.assertEqual(1, entry.count("Read-Host 'Type RESET_AND_REBUILD"))
        self.assertLess(entry.index("Read-Host 'Type RESET_AND_REBUILD"), entry.index("$stageName='reset'"))
        self.assertNotIn('Start-FastTrackAcceptance.ps1', entry)
        self.assertNotIn('Get-FastTrackHistory', entry)
        self.assertNotIn('Remove-Item', entry)
        self.assertIn('RESET_IDENTITY_REFUSED', reset)
        self.assertIn('RESET_UNSUPPORTED_FEATURE', reset)
        self.assertIn('IF XACT_STATE()<>0 ROLLBACK', reset)
        self.assertNotRegex(reset, r'(?i)\b(?:DROP|CREATE|ALTER)\s+DATABASE\b|\bUSE\s+\[')
        self.assertLess(reset.index('RESET_UNSUPPORTED_FEATURE'), reset.index('BEGIN TRANSACTION'))

    def test_resource_identity_refuses_wrong_database_and_running_app(self):
        self.ps(r'''
function Invoke-CleanAzure($Arguments){
 if($Arguments[0] -ceq 'sql'){return [pscustomobject]@{id="$script:CleanResource/Microsoft.Sql/servers/shale/databases/Shale";name='Shale';collation='SQL_Latin1_General_CP1_CI_AS'}}
 throw 'app should not be reached'
}
$refused=$false;try{Assert-CleanResources}catch{$refused=$true};if(-not $refused){throw 'Wrong identity accepted'}
function Invoke-CleanAzure($Arguments){
 if($Arguments[0] -ceq 'sql'){return [pscustomobject]@{id="$script:CleanResource/Microsoft.Sql/servers/shale/databases/ShaleMcpAcceptance";name='ShaleMcpAcceptance';collation='SQL_Latin1_General_CP1_CI_AS'}}
 return [pscustomobject]@{id="$script:CleanResource/Microsoft.Web/sites/shale-mcp-acceptance";name='shale-mcp-acceptance';state='Running'}
}
$refused=$false;try{Assert-CleanResources}catch{$refused=$true};if(-not $refused){throw 'Running app accepted'}
''')


if __name__ == '__main__':
    unittest.main()
