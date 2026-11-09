"""Offline safety/packaging checks only. Never connects to SQL or executes SQL."""
import pathlib
import re
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKET = ROOT / "docs/sql/ai-integration/deployment-binding"


def expand(path):
    text = path.read_text(encoding="utf-8")
    return re.sub(r"(?m)^:r (.+)$", lambda m: expand(ROOT / m[1].strip()), text)


def code(text):
    return re.sub(r"--[^\n]*|/\*.*?\*/", "", text, flags=re.S)


class DeploymentBindingPacketTest(unittest.TestCase):
    def test_read_only_entrypoints_never_write_durable_objects(self):
        for name in ("discover.sql", "preflight.sql", "postcheck.sql"):
            sql = code(expand(PACKET / name))
            self.assertNotRegex(sql, r"(?i)\b(?:UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|GRANT|DENY|REVOKE)\s+(?:dbo\.|TABLE|ROLE|USER|LOGIN|OBJECT|SECURITY)")
            for target in re.findall(r"(?i)\bINSERT\s+(?:INTO\s+)?([^\s(]+)", sql):
                self.assertTrue(target.startswith("@"), f"durable read-only insert: {target}")

    def test_install_has_one_explicit_insert_and_no_replacement_path(self):
        sql = code(expand(PACKET / "install.sql"))
        self.assertEqual(1, len(re.findall(r"(?i)\bINSERT\s+dbo\.", sql)))
        self.assertNotRegex(sql, r"(?i)\b(?:UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|GRANT|DENY|REVOKE)\s+(?:dbo\.|TABLE|ROLE|USER|LOGIN|OBJECT|SECURITY)")
        self.assertIn("VALUES(1,@BindIssuer,@BindResource,'case-read-v1'", sql)
        self.assertLess(sql.index("TABLOCKX,HOLDLOCK"), sql.index("Existing binding conflicts"))
        self.assertLess(sql.index("Existing binding conflicts"), sql.index("INSERT dbo.AiCaseDataDeployment"))
        self.assertIn("IF @BindExistingCount=0", sql)
        self.assertIn("SET XACT_ABORT ON", sql)
        self.assertIn("IF XACT_STATE()<>0 ROLLBACK TRANSACTION", sql)
        self.assertIn("ISNULL(@BindApproved,0)<>1", sql)
        self.assertLess(sql.rindex("Existing binding conflicts"), sql.index("COMMIT TRANSACTION"))
        self.assertIn("EXACT_EXISTING_BINDING_NO_CHANGE", sql)
        self.assertNotRegex(sql, r"(?m)^GO\s*$")

    def test_unconfirmed_inputs_fail_closed_and_full_verifier_is_reused(self):
        inputs = (PACKET / "inputs.sql").read_text()
        for field in ("Server", "Database", "Origin", "Issuer", "Resource", "JavaZone", "SqlZone"):
            self.assertIn(f"@Bind{field} nvarchar(max)=NULL", inputs)
        for field in ("Approved", "OffConfirmed", "EvidenceConfirmed"):
            self.assertIn(f"@Bind{field} bit=0", inputs)
        for name in ("preflight.sql", "install.sql"):
            sql = expand(PACKET / name)
            self.assertIn((ROOT / "docs/sql/ai-integration/phase3b8-confinement-verify.sql").read_text(), sql)
            self.assertIn("CONVERT(varbinary(max),Issuer)", sql)
            self.assertIn("sys.time_zone_info", sql)
            self.assertIn("Unconfirmed destination", sql)
            self.assertIn("SQL capabilities must remain OFF", sql)
        manifest = dict(line.split("=", 1) for line in (ROOT / "shale-data/src/main/resources/ai/confinement-v2-modules.properties").read_text().splitlines() if "=" in line)
        self.assertIn("0x" + manifest["Source"], (PACKET / "validate.sql").read_text())
        self.assertIn("ISNULL(@BindOffConfirmed,0)<>1", (PACKET / "validate.sql").read_text())
        self.assertIn("ISNULL(@BindEvidenceConfirmed,0)<>1", (PACKET / "validate.sql").read_text())

    def test_operator_commands_never_fetch_secret_collections_or_mutate_azure(self):
        document = (ROOT / "docs/production-mcp-binding-operator-discovery.md").read_text()
        commands = "\n".join(re.findall(r"```(?:powershell|bash|sql)\n(.*?)```", document, re.S))
        self.assertNotRegex(commands, r"(?i)appsettings|listPublishingCredentials|VM.system_properties|/proc/")
        self.assertNotRegex(commands, r"(?i)\baz\s+.*\b(?:set|update|restart|deploy|create|delete)\b")
        self.assertNotRegex(commands, r"(?i)\b(?:INSERT|UPDATE|DELETE|MERGE)\s+dbo\.")
        self.assertIn("not the running application's", document)

    def test_probe_matches_attestation_samples_under_non_utc_default(self):
        source = str(ROOT / "build/deployment-binding/BindingTimeZoneProbe.java")
        for zone, january, july in (("America/Denver", "2025-01-14T17:00:00", "2025-07-14T18:00:00"),
                                    ("Asia/Kolkata", "2025-01-15T05:30:00", "2025-07-15T05:30:00")):
            result = subprocess.run(["java", "-Duser.timezone=" + zone, source], check=True, text=True, capture_output=True)
            self.assertIn("JavaTimeZone=" + zone, result.stdout)
            self.assertEqual(28, result.stdout.count("INSERT @BindWallTimes"))
            self.assertIn(january, result.stdout)
            self.assertIn(july, result.stdout)
        attestation = (ROOT / "shale-data/src/main/java/com/shale/data/security/ai/AiCaseDataAttestation.java").read_text()
        self.assertIn("year=2025;year<=2028", attestation)
        self.assertIn("List.of(1,3,4,7,10,11,12)", attestation)


if __name__ == "__main__":
    unittest.main()
