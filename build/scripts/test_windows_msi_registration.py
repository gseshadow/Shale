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
        package=next(root.iter(m.tag('Package')))
        self.assertEqual('perUser',package.get('InstallScope'))
        self.assertIsNone(package.get('InstallPrivileges'))
        self.assertEqual('rollback',actions['ShaleRegistrationRollbackInstall'].get('Execute'))
        self.assertEqual('deferred',actions['ShaleRegistrationInstall'].get('Execute'))
        self.assertTrue(all(actions[name].get('Impersonate')=='no' for name in m.IDS))
        source=self.wxs.read_text(encoding='utf-8')
        self.assertIn('[UserSID]',source); self.assertIn('[LocalAppDataFolder]Shale',source); self.assertIn('NOT UPGRADINGPRODUCTCODE',source)
    def test_removes_incompatible_package_privilege_attribute(self):
        self.wxs.write_text(f'''<Wix xmlns="{m.NS}"><Product Id="*"><Package InstallScope="perUser" InstallPrivileges="elevated"/><InstallExecuteSequence/></Product></Wix>''',encoding='utf-8')
        m.mutate(self.wxs,self.script); m.validate(self.wxs)
        package=next(ET.parse(self.wxs).getroot().iter(m.tag('Package')))
        self.assertEqual('perUser',package.get('InstallScope'))
        self.assertNotIn('InstallPrivileges',package.attrib)
    def test_template_mutation_preserves_jpackage_preprocessor_instructions(self):
        self.wxs.write_text(f'''<?xml version="1.0"?>
<Wix xmlns="{m.NS}"><?include overrides.wxi?><Product Id="*">
<?if $(var.JpInstallScope) = "perUser"?><Package InstallScope="perUser"/><?endif?>
<InstallExecuteSequence/></Product></Wix>''', encoding='utf-8')
        m.mutate(self.wxs, self.script)
        source = self.wxs.read_text(encoding='utf-8')
        self.assertIn('<?include overrides.wxi?>', source)
        self.assertIn('<?if $(var.JpInstallScope) = "perUser"?>', source)
        self.assertIn('<?endif?>', source)
    def test_duplicate_mutation_and_missing_product_fail_closed(self):
        m.mutate(self.wxs,self.script)
        with self.assertRaisesRegex(ValueError,'already exist'): m.mutate(self.wxs,self.script)
        self.wxs.write_text(f'<Wix xmlns="{m.NS}"/>',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'expected one Product'): m.mutate(self.wxs,self.script)
    def test_validator_rejects_forbidden_scheduler_primitive(self):
        m.mutate(self.wxs,self.script); text=self.wxs.read_text(encoding='utf-8').replace('</Product>','<Property Id="BAD" Value="schtasks"/></Product>'); self.wxs.write_text(text,encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'forbidden'): m.validate(self.wxs)
    def test_validator_rejects_scope_privilege_and_action_regressions(self):
        m.mutate(self.wxs,self.script)
        for attribute,value,message in (
                ('InstallScope','perMachine','per-user scope'),
                ('InstallPrivileges','elevated','InstallPrivileges')):
            root=ET.parse(self.wxs); package=next(root.getroot().iter(m.tag('Package')))
            package.set(attribute,value); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
            with self.assertRaisesRegex(ValueError,message): m.validate(self.wxs)
            self.wxs.write_text(fixture(),encoding='utf-8'); m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); actions={n.get('Id'):n for n in root.getroot().iter(m.tag('CustomAction'))}
        actions['ShaleRegistrationInstall'].set('Impersonate','yes'); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'not fail-closed/elevated'): m.validate(self.wxs)
    def test_validator_rejects_missing_rollback_or_exact_uninstall_sequence(self):
        m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); sequence=next(root.getroot().iter(m.tag('InstallExecuteSequence')))
        rollback=next(n for n in sequence.findall(m.tag('Custom')) if n.get('Action')=='ShaleRegistrationRollbackInstall')
        sequence.remove(rollback); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'sequence contract missing'): m.validate(self.wxs)
    def test_registration_script_preserves_owner_uuid_exact_cleanup_and_rollback(self):
        source=Path(__file__).with_name('windows-installation-registration.ps1').read_text(encoding='utf-8')
        self.assertIn('ProfileList\\$OwnerSid',source)
        self.assertIn("$id = (Get-ItemProperty -LiteralPath \"$state\\$stateName\"",source)
        self.assertIn("Remove-Item -LiteralPath \"$registrations\\$id\"",source)
        self.assertIn("if ($saved.existed -eq 0)",source)
        self.assertIn("protected-state-mismatch",source)
if __name__=='__main__': unittest.main()
