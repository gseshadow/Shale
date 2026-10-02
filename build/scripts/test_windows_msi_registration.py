import importlib.util, tempfile, unittest
from pathlib import Path
import xml.etree.ElementTree as ET
spec=importlib.util.spec_from_file_location("registration",Path(__file__).with_name("windows_msi_registration.py")); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def fixture(scope=m.JPACKAGE_SCOPE, privileges=None):
    privilege = '' if privileges is None else f' InstallPrivileges="{privileges}"'
    return f'''<Wix xmlns="{m.NS}"><Product Id="*"><Package InstallScope="{scope}"{privilege}/><InstallExecuteSequence/></Product></Wix>'''

DARK_PRODUCT = {
    'Id':'{600556C4-4D9C-332A-8728-FE3138D5161A}', 'Language':'1033',
    'Manufacturer':'Get Downing', 'Name':'Shale',
    'UpgradeCode':'{E66C3164-CAC0-3DA4-BBF7-AD0299059575}', 'Version':'1.0.128'
}
class RegistrationMutationTest(unittest.TestCase):
    def setUp(self):
        self.directory=Path(tempfile.mkdtemp()); self.wxs=self.directory/'main.wxs'; self.wxs.write_text(fixture(),encoding='utf-8')
        self.script=self.directory/'registration.ps1'; self.script.write_text("$Mode=$env:SHALE_REG_MODE\n",encoding='utf-8')
    def test_injects_elevated_fail_closed_rollback_aware_lifecycle(self):
        m.mutate(self.wxs,self.script); m.validate_template(self.wxs); root=ET.parse(self.wxs).getroot()
        actions={n.get('Id'):n for n in root.iter(m.tag('CustomAction'))}
        package=next(root.iter(m.tag('Package')))
        self.assertEqual(m.JPACKAGE_SCOPE,package.get('InstallScope'))
        self.assertIsNone(package.get('InstallPrivileges'))
        self.assertEqual('rollback',actions['ShaleRegistrationRollbackInstall'].get('Execute'))
        self.assertEqual('deferred',actions['ShaleRegistrationInstall'].get('Execute'))
        self.assertTrue(all(actions[name].get('Impersonate')=='no' for name in m.IDS))
        source=self.wxs.read_text(encoding='utf-8')
        self.assertIn('[UserSID]',source); self.assertIn('[LocalAppDataFolder]Shale',source); self.assertIn('NOT UPGRADINGPRODUCTCODE',source)
        self.assertEqual(set(m.IDS), {n.get('Property') for n in actions.values() if n.get('Id','').startswith('SetShaleRegistration')})
        self.assertTrue(all(len(actions['Set'+name].get('Value')) == 254 for name in m.IDS))
        self.assertTrue(all(len(actions['Set'+name].get('Value')) <= m.TARGET_MAX for name in m.IDS))
        payload=next(n for n in root.iter(m.tag('Property')) if n.get('Id') == m.PAYLOAD_PROPERTY)
        self.assertEqual(m.encoded_script(self.script), payload.get('Value'))
    def test_removes_incompatible_package_privilege_attribute(self):
        self.wxs.write_text(fixture(privileges='elevated'),encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'template contract violation'): m.validate_template(self.wxs)
        m.mutate(self.wxs,self.script); m.validate_template(self.wxs)
        package=next(ET.parse(self.wxs).getroot().iter(m.tag('Package')))
        self.assertEqual(m.JPACKAGE_SCOPE,package.get('InstallScope'))
        self.assertNotIn('InstallPrivileges',package.attrib)
    def test_template_mutation_preserves_jpackage_preprocessor_instructions(self):
        self.wxs.write_text(f'''<?xml version="1.0"?>
<Wix xmlns="{m.NS}"><?include overrides.wxi?><Product Id="*">
<Package InstallScope="$(var.JpInstallScope)"/>
<InstallExecuteSequence/></Product></Wix>''', encoding='utf-8')
        m.mutate(self.wxs, self.script)
        source = self.wxs.read_text(encoding='utf-8')
        self.assertIn('<?include overrides.wxi?>', source)
        self.assertIn('InstallScope="$(var.JpInstallScope)"', source)
    def test_duplicate_mutation_and_missing_product_fail_closed(self):
        m.mutate(self.wxs,self.script)
        with self.assertRaisesRegex(ValueError,'already exist'): m.mutate(self.wxs,self.script)
        self.wxs.write_text(f'<Wix xmlns="{m.NS}"/>',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'expected one Product'): m.mutate(self.wxs,self.script)
    def test_validator_rejects_forbidden_scheduler_primitive(self):
        m.mutate(self.wxs,self.script); text=self.wxs.read_text(encoding='utf-8').replace('</Product>','<Property Id="BAD" Value="schtasks"/></Product>'); self.wxs.write_text(text,encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'template contract violation.*forbidden'): m.validate_template(self.wxs)
    def test_template_validator_rejects_literal_scope_privilege_and_action_regressions(self):
        m.mutate(self.wxs,self.script)
        for attribute,value,message in (
                ('InstallScope','perMachine','template contract violation'),
                ('InstallScope','perUser','template contract violation'),
                ('InstallPrivileges','elevated','template contract violation'),
                ('InstallPrivileges','limited','template contract violation')):
            root=ET.parse(self.wxs); package=next(root.getroot().iter(m.tag('Package')))
            package.set(attribute,value); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
            with self.assertRaisesRegex(ValueError,message): m.validate_template(self.wxs)
            self.wxs.write_text(fixture(),encoding='utf-8'); m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); package=next(root.getroot().iter(m.tag('Package')))
        del package.attrib['InstallScope']; root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'template contract violation'): m.validate_template(self.wxs)
        self.wxs.write_text(fixture(),encoding='utf-8'); m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); actions={n.get('Id'):n for n in root.getroot().iter(m.tag('CustomAction'))}
        actions['ShaleRegistrationInstall'].set('Impersonate','yes'); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'template contract violation.*not fail-closed/elevated'): m.validate_template(self.wxs)
    def dark_style_final(self):
        m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); product=next(root.getroot().iter(m.tag('Product')))
        product.attrib.update(DARK_PRODUCT)
        package=next(root.getroot().iter(m.tag('Package')))
        package.attrib.clear(); package.attrib.update({
            'Compressed':'yes', 'Description':'Shale Desktop',
            'InstallPrivileges':'limited', 'InstallerVersion':'200',
            'Languages':'1033', 'Manufacturer':'Get Downing', 'Platform':'x64'})
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        return root, package

    def test_final_validator_accepts_real_dark_limited_representation_without_scope(self):
        _,package=self.dark_style_final()
        self.assertNotIn('InstallScope',package.attrib)
        m.validate_final(self.wxs)

    def test_final_validator_rejects_elevated_unexpected_privilege_and_machine_scope(self):
        root,package=self.dark_style_final()
        for attribute,value,pattern in (
                ('InstallPrivileges','elevated','InstallPrivileges must be limited'),
                ('InstallPrivileges','custom','InstallPrivileges must be limited'),
                ('InstallScope','perMachine','InstallScope must be perUser')):
            package.set(attribute,value); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
            with self.assertRaisesRegex(ValueError,pattern): m.validate_final(self.wxs)
            package.attrib.pop('InstallScope',None); package.set('InstallPrivileges','limited')
        package.attrib.pop('InstallPrivileges')
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'InstallPrivileges must be limited.*<absent>'): m.validate_final(self.wxs)

    def test_final_validator_rejects_clear_allusers_and_contradictory_per_user_properties(self):
        root,_=self.dark_style_final(); product=next(root.getroot().iter(m.tag('Product')))
        for properties,pattern in (
                ({'ALLUSERS':'1'},'ALLUSERS indicates'),
                ({'MSIINSTALLPERUSER':'1'},'MSIINSTALLPERUSER is contradictory'),
                ({'ALLUSERS':'2'},'ALLUSERS=2 is ambiguous'),
                ({'ALLUSERS':'2','MSIINSTALLPERUSER':'0'},'MSIINSTALLPERUSER is contradictory')):
            nodes=[]
            for key,value in properties.items(): nodes.append(ET.SubElement(product,m.tag('Property'),{'Id':key,'Value':value}))
            root.write(self.wxs,encoding='utf-8',xml_declaration=True)
            with self.assertRaisesRegex(ValueError,pattern): m.validate_final(self.wxs)
            for node in nodes: product.remove(node)

    def test_final_validator_accepts_explicit_non_machine_msi_property_pair(self):
        root,_=self.dark_style_final(); product=next(root.getroot().iter(m.tag('Product')))
        ET.SubElement(product,m.tag('Property'),{'Id':'ALLUSERS','Value':'2'})
        ET.SubElement(product,m.tag('Property'),{'Id':'MSIINSTALLPERUSER','Value':'1'})
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        m.validate_final(self.wxs)

    def test_final_registration_action_and_sequence_contract_is_unchanged(self):
        root,_=self.dark_style_final(); sequence=next(root.getroot().iter(m.tag('InstallExecuteSequence')))
        sequence.remove(next(n for n in sequence.findall(m.tag('Custom')) if n.get('Action')=='ShaleRegistrationRollbackInstall'))
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'final MSI contract violation.*sequence missing'): m.validate_final(self.wxs)

    def test_final_target_limit_remains_fail_closed(self):
        root,_=self.dark_style_final(); actions={n.get('Id'):n for n in root.getroot().iter(m.tag('CustomAction'))}
        actions['SetShaleRegistrationCommit'].set('Value',actions['SetShaleRegistrationCommit'].get('Value')+'XX')
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,r'final MSI contract violation.*Target overflow.*length=256'):
            m.validate_final(self.wxs)
    def test_target_limit_is_fail_closed_without_losing_required_action_data(self):
        m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); actions={n.get('Id'):n for n in root.getroot().iter(m.tag('CustomAction'))}
        expected=(f'[{m.PAYLOAD_PROPERTY}]','[UserSID]','[INSTALLDIR]','[LocalAppDataFolder]Shale')
        for name in m.IDS:
            target=actions['Set'+name].get('Value')
            self.assertLessEqual(len(target),255)
            self.assertTrue(all(field in target for field in expected))
        actions['SetShaleRegistrationCommit'].set('Value',actions['SetShaleRegistrationCommit'].get('Value')+'XX')
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,r'CustomAction Target overflow: SetShaleRegistrationCommit length=256 limit=255'):
            m.validate_template(self.wxs)
    def test_validator_rejects_incomplete_setter_or_missing_private_payload(self):
        m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); actions={n.get('Id'):n for n in root.getroot().iter(m.tag('CustomAction'))}
        actions['SetShaleRegistrationRollbackInstall'].set('Value',actions['SetShaleRegistrationRollbackInstall'].get('Value').replace('[UserSID]',''))
        root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,r'incomplete action data.*missing \[UserSID\]'): m.validate_template(self.wxs)
    def test_validator_rejects_missing_rollback_or_exact_uninstall_sequence(self):
        m.mutate(self.wxs,self.script)
        root=ET.parse(self.wxs); sequence=next(root.getroot().iter(m.tag('InstallExecuteSequence')))
        rollback=next(n for n in sequence.findall(m.tag('Custom')) if n.get('Action')=='ShaleRegistrationRollbackInstall')
        sequence.remove(rollback); root.write(self.wxs,encoding='utf-8',xml_declaration=True)
        with self.assertRaisesRegex(ValueError,'template contract violation.*sequence missing'): m.validate_template(self.wxs)
    def test_registration_script_preserves_owner_uuid_exact_cleanup_and_rollback(self):
        source=Path(__file__).with_name('windows-installation-registration.ps1').read_text(encoding='utf-8')
        self.assertIn('ProfileList\\$OwnerSid',source)
        self.assertIn("$id = (Get-ItemProperty -LiteralPath \"$state\\$stateName\"",source)
        self.assertIn("Remove-Item -LiteralPath \"$registrations\\$id\"",source)
        self.assertIn("if ($saved.existed -eq 0)",source)
        self.assertIn("protected-state-mismatch",source)
if __name__=='__main__': unittest.main()
