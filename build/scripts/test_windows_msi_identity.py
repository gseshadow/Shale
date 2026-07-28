import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("identity", Path(__file__).with_name("windows_msi_identity.py"))
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)

def wix(shortcuts):
    return '<?xml version="1.0"?><Wix xmlns="%s"><Fragment><DirectoryRef Id="ProgramMenuFolder">%s</DirectoryRef></Fragment></Wix>' % (m.NS, shortcuts)
GOOD='<Component><Shortcut Id="menu" Name="Shale" Advertise="no" WorkingDirectory="INSTALLDIR" Target="[#shaleexe]"/></Component>'

class IdentityMutationTest(unittest.TestCase):
    def file(self, body):
        p=Path(tempfile.mkdtemp())/'bundle.wxf'; p.write_text(wix(body), encoding='utf-8'); return p
    def test_inserts_expected_child(self):
        p=self.file(GOOD); m.mutate(p); m.validate(p); self.assertIn(m.APP_ID,p.read_text())
    def test_missing_and_duplicate_fail(self):
        with self.assertRaises(ValueError): m.mutate(self.file(''))
        with self.assertRaises(ValueError): m.mutate(self.file(GOOD+GOOD))
    def test_wrong_target_and_desktop_are_excluded(self):
        with self.assertRaises(ValueError): m.mutate(self.file(GOOD.replace('[#shaleexe]','Shale.exe')))
        p=Path(tempfile.mkdtemp())/'x.wxf'; p.write_text(wix(GOOD).replace('ProgramMenuFolder','DesktopFolder')); self.assertRaises(ValueError,m.mutate,p)
    def test_conflicting_property_fails(self):
        bad=GOOD.replace('/>','><ShortcutProperty Key="System.AppUserModel.ID" Value="wrong"/></Shortcut>')
        with self.assertRaises(ValueError): m.mutate(self.file(bad))

if __name__ == '__main__': unittest.main()
