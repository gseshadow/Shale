import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "build/scripts/validate-installed-windows.ps1"
RUNBOOK = ROOT / "docs/testing/windows-installed-registration-phase13f.md"


class InstalledWindowsValidationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SCRIPT.read_text(encoding="utf-8")

    def test_report_has_heading_and_operator_fields(self):
        self.assertIn("Phase 13F Installed-Windows Acceptance", self.source)
        for field in (
            "Windows user", "Current user SID", "Shale install root discovered",
            "Authoritative registration root", "Number of registration records found",
            "Matching installation UUID", "Registration schema version", "Registered owner SID",
            "Owner SID correctness", "Registered install root", "Install-root correctness",
            "Registered support root", "Support-root correctness", "Registration ACL result",
            "Production registration-reader classification", "Installed-version metadata path",
            "Installed-version metadata schema", "Installed-version metadata version",
            "Installed-version metadata channel", "Runtime/application version",
            "Updater executable path", "Updater path existence", "Phase 12 attempt-store path",
            "Phase 13B execution-lock path", "Update evidence/log path",
            "Shale.exe Authenticode status", "ShaleUpdater.exe Authenticode status",
            "MSI Authenticode status", "Expected publisher result",
            "Overall fresh-install acceptance result",
        ):
            self.assertIn("'" + field + "'", self.source, field)
        self.assertIn("[ValidateSet('PASS','FAIL','NOT RUN','INFO')]", self.source)

    def test_script_is_read_only_and_does_not_contain_fixture_or_lifecycle_mutators(self):
        for forbidden in ("New-Item", "Set-Item", "Set-Acl", "Remove-Item", "New-ItemProperty",
                          "Set-ItemProperty", "schtasks", "Start-Process", "msiexec"):
            self.assertNotRegex(self.source, rf"(?im)^\s*{re.escape(forbidden)}\b", forbidden)

    def test_matching_is_owner_and_profile_root_based_and_never_first_record_based(self):
        for required in ("ProfileList\\$currentSid", "ownerSid -eq $currentSid",
                         "Canonical-Path $_.Values.installRoot", "$matches.Count -gt 1",
                         "ambiguously matched", "Group-Object"):
            self.assertIn(required, self.source)
        self.assertNotRegex(self.source, r"(?i)records\s*\[\s*0\s*\]")

    def test_acl_reads_the_64_bit_view_and_compares_only_sids(self):
        for required in ("RegistryView]::Registry64", "GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])",
                         "S-1-5-18", "S-1-5-32-544", "AreAccessRulesProtected", "$rule.IsInherited",
                         "RegistryRights]::FullControl", "RegistryRights]::ReadKey", "$writeMask",
                         "InheritanceFlags]::ContainerInherit", "PropagationFlags]::None"):
            self.assertIn(required, self.source)
        self.assertNotIn("IdentityReference.Translate", self.source)
        self.assertNotIn("NT AUTHORITY\\SYSTEM", self.source)
        self.assertNotIn("BUILTIN\\Administrators", self.source)
        self.assertNotIn("'ACL could not be evaluated'", self.source)

    def test_production_reader_diagnostic_uses_the_installed_java_runtime(self):
        for required in ("runtime\\bin\\java.exe", "app\\*", "app\\lib\\*",
                         "com.shale.core.update.WindowsInstallationRegistrationDiagnostic",
                         "--installation-id", "classification", "-ceq 'VALID'", "factsMatch"):
            self.assertIn(required, self.source)
        self.assertNotIn("the existing Java reader has no installed command-line entry point", self.source)

    def test_optional_signing_and_unsigned_developer_behavior_are_explicit(self):
        self.assertIn("if ($MsiPath)", self.source)
        self.assertIn("if (-not $ExpectedPublisher)", self.source)
        self.assertIn("production-signing acceptance NOT RUN", self.source)
        self.assertIn("$signature.Status -eq 'Valid'", self.source)

    def test_report_does_not_expose_application_or_authentication_identity_fields(self):
        for forbidden in ("tenant", "email", "jwt", "jti", "session identifier", "credential", "phi"):
            self.assertNotIn(forbidden, self.source.lower(), forbidden)

    def test_documented_invocations_are_single_physical_lines(self):
        lines = RUNBOOK.read_text(encoding="utf-8").splitlines()
        commands = [line for line in lines if line.startswith("powershell -NoProfile")]
        self.assertEqual(2, len(commands))
        self.assertTrue(all("`" not in command and "validate-installed-windows.ps1" in command for command in commands))


if __name__ == "__main__":
    unittest.main()
