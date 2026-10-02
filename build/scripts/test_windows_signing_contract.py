import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class WindowsSigningContractTest(unittest.TestCase):
    def test_local_mode_is_explicitly_optional_but_production_mode_is_fail_closed(self):
        script = (ROOT / "build/scripts/sign-windows-artifact.ps1").read_text()
        self.assertIn("SHALE_WINDOWS_SIGNING_REQUIRED -ne 'true'", script)
        for required in ("SHALE_SIGNTOOL_PATH", "SHALE_SIGN_TIMESTAMP_URL", "SHALE_SIGN_EXPECTED_SUBJECT"):
            self.assertIn(required, script)
        self.assertIn("TimeStamperCertificate", script)
        self.assertIn("SignerCertificate.Subject", script)
        self.assertIn("verify /pa /all", script)

    def test_executables_and_msi_are_signed_before_zip_hash_manifest_and_publication(self):
        release = (ROOT / "build/scripts/build-shale-release.bat").read_text()
        updater = (ROOT / "build/scripts/build-updater.bat").read_text()
        msi = (ROOT / "build/scripts/build-shale-windows-msi.bat").read_text()
        self.assertLess(updater.index("sign-windows-artifact.ps1"), updater.index("xcopy"))
        self.assertLess(release.index("sign-windows-artifact.ps1"), release.index("Compress-Archive"))
        self.assertLess(release.index("Compress-Archive"), release.index("build-shale-windows-msi.bat"))
        self.assertLess(msi.index("sign-windows-artifact.ps1"), msi.index("artifact-finalization"))
        orchestrator = (ROOT / "build/scripts/release.bat").read_text()
        self.assertLess(orchestrator.index("build-shale-release.bat"), orchestrator.index("update-manifest.bat"))

    def test_password_is_never_echoed(self):
        script = (ROOT / "build/scripts/sign-windows-artifact.ps1").read_text()
        self.assertNotIn("Write-Host $env:SHALE_SIGN_CERT_PASSWORD", script)

    def test_version_metadata_is_packaged_and_published_only_after_payload_success(self):
        release = (ROOT / "build/scripts/build-shale-release.bat").read_text()
        updater = (ROOT / "shale-updater/src/main/java/com/shale/updater/Main.java").read_text()
        self.assertLess(release.index("shale-installed-version.properties"), release.index("jpackage"))
        applied = updater.index("InstalledVersionMetadata.production")
        self.assertGreater(applied, updater.index("applyStagedUpdate"))
        self.assertGreater(applied, updater.index("replaceInstallDir"))
        self.assertLess(applied, updater.index("UpdateAttemptState.INSTALL_APPLIED", applied))

if __name__ == '__main__': unittest.main()
