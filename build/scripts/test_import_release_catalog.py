import importlib.util, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
SCRIPT=Path(__file__).with_name("import_release_catalog.py")
SPEC=importlib.util.spec_from_file_location("import_release_catalog",SCRIPT);mod=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(mod)
class Response:
    def __enter__(self):return self
    def __exit__(self,*_):return False
    def read(self):return b'{"version":"1.2.3","outcome":"CREATED"}'
class ImportReleaseCatalogTest(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory();self.p=Path(self.t.name)/"m.json"
    def tearDown(self):self.t.cleanup()
    def write(self,value):self.p.write_text(json.dumps(value),encoding="utf-8")
    def test_missing_notes_is_non_fatal_and_does_not_call_control_plane(self):
        self.write({"version":"1.2.3"})
        with patch.object(mod.urllib.request,"urlopen") as call:self.assertFalse(mod.import_catalog(self.p,None,None));call.assert_not_called()
    def test_structured_notes_are_posted_to_versioned_authoritative_endpoint(self):
        notes={"version":"1.2.3","title":"Title","summary":"Summary","groups":{"New":["A"],"Improvements":[],"Fixes":[]}}
        self.write({"version":"1.2.3","releaseNotes":notes})
        with patch.object(mod.urllib.request,"urlopen",return_value=Response()) as call:
            self.assertTrue(mod.import_catalog(self.p,"https://control.test/","x"*32));request=call.call_args.args[0]
            self.assertEqual("https://control.test/api/control-plane/application-releases/1.2.3/import",request.full_url)
            self.assertEqual(notes,json.loads(request.data))
    def test_version_mismatch_and_missing_authorization_fail_closed(self):
        self.write({"version":"1.2.3","releaseNotes":{"version":"1.2.4"}})
        with self.assertRaisesRegex(ValueError,"version"):mod.import_catalog(self.p,"https://control.test","x"*32)
        self.write({"version":"1.2.3","releaseNotes":{"version":"1.2.3"}})
        with self.assertRaisesRegex(ValueError,"required"):mod.import_catalog(self.p,None,None)
if __name__=="__main__":unittest.main()
