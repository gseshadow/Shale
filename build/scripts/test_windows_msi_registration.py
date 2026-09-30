import importlib.util, tempfile, unittest
from pathlib import Path
import xml.etree.ElementTree as ET
spec=importlib.util.spec_from_file_location("registration",Path(__file__).with_name("windows_msi_registration.py")); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def fixture(): return f'''<Wix xmlns="{m.NS}"><Product Id="*"><Package InstallScope="perUser"/><InstallExecuteSequence/></Product></Wix>'''
class RegistrationMutationTest(unittest.TestCase):
    def setUp(self):
        self.directory=Path(tempfile.mkdtemp()); self.wxs=self.directory/'main.wxs'; self.wxs.write_text(fixture(),encoding='utf-8')
        self.script=self.directory/'registration.ps1'; self.script.write_text("$Mode=$env:SHALE_REG_MODE\n",encoding='utf-8')
    def test_injects_elevated_fail_closed_rollback_aware_lifecycle(self):
        m.mutate(self.wxs,self.script); m.validate(self.wxs); root=ET.parse(self.wxs).getroot()
        actions={n.get('Id'):n for n in root.iter(m.tag('CustomAction'))}
        self.assertEqual('rollback',actions['ShaleRegistrationRollbackInstall'].get('Execute'))
        self.assertEqual('deferred',actions['ShaleRegistrationInstall'].get('Execute'))
        self.assertTrue(all(actions[name].get('Impersonate')=='no' for name in m.IDS))
        source=self.wxs.read_text(encoding='utf-8')
        self.assertIn('[UserSID]',source); self.assertIn('[LocalAppDataFolder]Shale',source); self.assertIn('NOT UPGRADINGPRODUCTCODE',source)
    def test_duplicate_mutation_and_missing_product_fail_closed(self):
        m.mutate(self.wxs,self.script)
        with self.assertRaisesRegex(ValueError,'already exist'): m.mutate(self.wxs,self.script)
        self.wxs.write_text(f'<Wix xmlns="{m.NS}"/>',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'expected one Product'): m.mutate(self.wxs,self.script)
    def test_validator_rejects_forbidden_scheduler_primitive(self):
        m.mutate(self.wxs,self.script); text=self.wxs.read_text(encoding='utf-8').replace('</Product>','<Property Id="BAD" Value="schtasks"/></Product>'); self.wxs.write_text(text,encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'forbidden'): m.validate(self.wxs)
if __name__=='__main__': unittest.main()
