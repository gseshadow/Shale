"""C1 generation/preservation checks; these do not substitute for real SQL acceptance."""
import hashlib
import re
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / 'docs/sql/ai-integration'
RES = ROOT / 'shale-data/src/main/resources/ai'
BUILD = ROOT / 'build/sql-confinement'


class ExternalV2GenerationTest(unittest.TestCase):
    def test_generation_is_current_and_cannot_rewrite_predecessors(self):
        paths = [p for p in SQL.glob('*.sql') if not p.name.startswith('universal-pr-c1-')]
        paths += [p for p in RES.glob('*.sql') if 'external' not in p.name]
        before = {p: hashlib.sha256(p.read_bytes()).digest() for p in paths}
        subprocess.run([sys.executable, str(BUILD / 'generate-external-v2.py'), '--check'], check=True)
        self.assertEqual(before, {p: hashlib.sha256(p.read_bytes()).digest() for p in paths})

    def test_delta_is_explicit_and_no_historical_receipt_is_written(self):
        install = (SQL / 'universal-pr-c1-external-v2.sql').read_text()
        # Inspect top-level installed definitions, excluding unchanged embedded verifiers.
        changed = re.findall(r"EXEC\(N'(ALTER PROCEDURE dbo\.\w+)", install)
        self.assertEqual({x.split('.')[-1] for x in changed},
                         {'AiCimdResolveCodeV1', 'AiCimdResolveRefreshV1', 'AiCimdResolveRevocationV1'})
        self.assertIn('ALTER TABLE dbo.AiExternalApplication DROP CONSTRAINT CK_AiExternalApplication_Source', install)
        for line in install.splitlines():
            if 'sp_addextendedproperty @name=' in line:
                self.assertIn('DcrCompatV2.', line)
        self.assertNotIn('sp_updateextendedproperty', install)
        self.assertNotRegex(install, r'(?:UPDATE|INSERT|DELETE) dbo\.(?:AiCaseDataDeployment|AiCimdOAuthReceiptV1|AiCimdProofReceiptV1)\b')
        self.assertNotIn('CREATE LOGIN', install)
        self.assertNotIn('CREATE USER', install)

    def test_registration_is_neutral_and_source_specific(self):
        text = (BUILD / 'external-v2-registration.sql').read_text()
        register = text.split('CREATE PROCEDURE dbo.AiDcrRegisterApplicationV2', 1)[1].split('\nGO\n', 1)[0]
        self.assertIn('CRYPT_GEN_RANDOM(32)', register)
        self.assertIn("N'dcr.'", register)
        self.assertIn("'DCR',@digest", register)
        self.assertIn('>=5000', register)
        self.assertIn('INSERT dbo.GlobalControlPlaneAuditLog', register)
        self.assertLess(register.index('INSERT dbo.GlobalControlPlaneAuditLog'), register.index('COMMIT TRANSACTION'))
        for table in ('AiTenantApplicationApproval', 'AiDelegationGrant', 'AiDelegatedSession', 'AiCimdCredentialV1', 'AiOAuthCredential'):
            self.assertNotIn('INSERT dbo.' + table, register)
        self.assertIn("INSERT dbo.AiExternalControlV2 VALUES(1,0)", text)

    def test_successor_checks_actual_delta_before_historical_projection(self):
        registry = (RES / 'registry-external-v2-catalog.sql').read_text()
        additive = (RES / 'external-authorization-v2-catalog.sql').read_text()
        for text in (registry, additive):
            self.assertIn('Reviewed C1 successor required; no fallback', text)
            self.assertIn('C1 reviewed module drift', text)
            self.assertIn('C1 catalog drift; never refresh receipts', text)
        self.assertLess(registry.index('C1 source constraint drift'), registry.index('THEN @C1ConstraintDefinition'))
        self.assertIn('THEN @C1ConstraintId ELSE o.object_id', registry)
        self.assertIn("name=N'Shale.UniversalMcpPR3'", registry)
        self.assertIn('Historical receipt/binding changed', additive)
        self.assertIn('CIMD proof catalog/source or predecessor drift', additive)
        self.assertIn('CIMD OAuth receipt drift', additive)
        self.assertNotIn('TRY BEGIN', registry)

    def test_new_roots_share_records_but_cimd_publish_still_rejects_dcr(self):
        modules = (BUILD / 'external-v2-modules.sql').read_text()
        self.assertIn('INSERT dbo.AiCimdFamilyV1', modules)
        self.assertIn('INSERT dbo.AiCimdCredentialV1', modules)
        self.assertNotIn('dbo.AiOAuthCredential', modules)
        self.assertNotIn('dbo.AiAuthorizationTransaction', modules)
        self.assertIn('p.RowVer=f.ApprovalVersion', modules)
        self.assertIn('ds.ScopeMask & @Scopes', modules)
        publish = modules.split('CREATE PROCEDURE dbo.AiExternalPublishMetadataV2', 1)[1].split('\nGO\n', 1)[0]
        self.assertIn("Source='CIMD'", publish)
        self.assertNotIn("Source='DCR'", publish)
        self.assertNotRegex(modules, r'EXEC dbo\.AiCimd(?:Data|OAuth|Validate|Issue|Redeem|Refresh|Revoke|Reserve)\w*V1\b')


if __name__ == '__main__':
    unittest.main()
