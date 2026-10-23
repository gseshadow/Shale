"""A skipped integration class is not a compatibility pass."""
from pathlib import Path
import xml.etree.ElementTree as ET

required = {
    "OAuthAuthorizationFlowTest", "OAuthCodeExchangePolicyTest", "OAuthRevocationPolicyTest",
    "OAuthFilterIsolationTest", "AiAccessContractTest", "IntegrationAccessContractTest",
    "IntegrationCaseReadTest", "AiSecurityLocalSqlServerTest", "AuthControllerTest", "RememberedDesktopSessionTest",
    "OpenApiDocumentationTest", "ShaleServerServiceConfigurationTest",
}
found = {}
for path in Path(".").glob("shale-*/target/surefire-reports/TEST-*.xml"):
    suite = ET.parse(path).getroot()
    name = suite.attrib["name"].split(".")[-1]
    if name in required:
        counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        assert counts["tests"] > 0 and all(counts[key] == 0 for key in ("failures", "errors", "skipped")), (name, counts)
        found[name] = counts
assert required <= found.keys(), ("Missing test receipts", required - found.keys())
text = "\n".join(f"{name}: {found[name]}" for name in sorted(found))
Path("work/mcp-evidence/focused-receipts.txt").write_text(text + "\n")
print(text)
