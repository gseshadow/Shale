import io
import tempfile
import unittest
from pathlib import Path

from windows_jpackage_resource import (
    WINDOWS_MSI_MAIN_RESOURCE,
    extract_selected_template,
    select_windows_msi_main,
)


class WindowsJpackageResourceTest(unittest.TestCase):
    def test_unrelated_main_templates_do_not_change_exact_windows_msi_selection(self):
        candidates = [
            "other.module/example/main.wxs",
            WINDOWS_MSI_MAIN_RESOURCE,
            "jdk.jpackage/jdk/jpackage/internal/resources/not-msi/main.wxs",
        ]
        self.assertEqual(WINDOWS_MSI_MAIN_RESOURCE, select_windows_msi_main(candidates))

    def test_filename_alone_is_not_authoritative(self):
        with self.assertRaisesRegex(ValueError, "cardinality"):
            select_windows_msi_main(["jdk.jpackage/unrelated/main.wxs"], io.StringIO())

    def test_zero_windows_msi_candidates_fails_with_all_discovered_paths(self):
        diagnostic = io.StringIO()
        candidates = ["alpha/main.wxs", "beta/main.wxs"]
        with self.assertRaisesRegex(ValueError, "cardinality"):
            select_windows_msi_main(candidates, diagnostic)
        output = diagnostic.getvalue()
        self.assertIn("expected=1 found=0", output)
        for candidate in candidates:
            self.assertIn(f"candidate={candidate}", output)

    def test_duplicate_exact_windows_msi_candidates_fail_closed(self):
        diagnostic = io.StringIO()
        candidates = [WINDOWS_MSI_MAIN_RESOURCE, WINDOWS_MSI_MAIN_RESOURCE]
        with self.assertRaisesRegex(ValueError, "cardinality"):
            select_windows_msi_main(candidates, diagnostic)
        self.assertIn("expected=1 found=2", diagnostic.getvalue())

    def test_copy_is_byte_accurate_and_retains_preprocessor_directives(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root.joinpath(*WINDOWS_MSI_MAIN_RESOURCE.parts)
            source.parent.mkdir(parents=True)
            content = b'<?if $(var.JpIsSystemWide) = "yes" ?>\r\n<Product />\r\n<?endif ?>\r\n'
            source.write_bytes(content)
            destination = root / "resources" / "main.wxs"

            extract_selected_template(root, destination)

            self.assertEqual(content, destination.read_bytes())


if __name__ == "__main__":
    unittest.main()
