"""Record selected versions and fail on baseline drift; never choose dependency versions."""
import re
from pathlib import Path

ROOT = Path("work/mcp-evidence")
def graph(name):
    text = (ROOT / name).read_text()
    selected = {}
    for line in text.splitlines():
        if "(omitted for" in line:
            continue
        match = re.search(r"([\w.-]+):([\w.-]+):jar:([^\s]+):(compile|runtime|test|provided)", line)
        if match:
            group, artifact, version, scope = match.groups()
            selected[(group, artifact)] = (version, scope)
    return text, selected

baseline_text, baseline = graph("baseline-tree.txt")
candidate_text, candidate = graph("candidate-tree.txt")
expected = {
    ("org.springframework.boot", "spring-boot"): "4.1.1",
    ("org.springframework", "spring-core"): "7.0.9",
    ("org.springframework", "spring-webmvc"): "7.0.9",
    ("org.springframework.security", "spring-security-core"): "7.1.1",
    ("org.apache.tomcat.embed", "tomcat-embed-core"): "11.0.24",
    ("tools.jackson.core", "jackson-databind"): "3.1.5",
}
for key, version in expected.items():
    assert candidate.get(key, (None,))[0] == version, (key, candidate.get(key), version)
for key, value in baseline.items():
    if key[0].startswith(("org.springframework", "org.apache.tomcat", "tools.jackson", "io.projectreactor")):
        assert candidate.get(key) == value, ("Baseline drift", key, value, candidate.get(key))
assert not any(group.startswith("javax.servlet") for group, _ in candidate), "javax.servlet is prohibited"
servlet = candidate.get(("jakarta.servlet", "jakarta.servlet-api"))
assert servlet is None or servlet[0] == "6.1.0", servlet
# Embedded Tomcat also supplies the Servlet API: actual 6.1 constants are asserted in the Java test.
assert candidate[("io.modelcontextprotocol.sdk", "mcp-core")] == ("2.2.0-M1", "test")
assert candidate[("io.modelcontextprotocol.sdk", "mcp-json-jackson3")] == ("2.2.0-M1", "test")
assert ("io.modelcontextprotocol.sdk", "mcp-json-jackson2") not in candidate
# Existing Jackson 2 consumers can remain, but the probe must neither add nor replace that graph.
jackson2 = lambda graph: {key: value for key, value in graph.items() if key[0].startswith("com.fasterxml.jackson")}
assert jackson2(candidate) == jackson2(baseline), ("Jackson 2 graph changed", jackson2(baseline), jackson2(candidate))
prefixes = ("io.modelcontextprotocol", "org.springframework", "jakarta.servlet", "org.apache.tomcat",
            "tools.jackson", "com.fasterxml.jackson", "io.projectreactor", "org.reactivestreams",
            "com.networknt", "org.slf4j", "ch.qos.logback", "org.apache.logging", "io.netty")
report = "\n".join(f"{g}:{a}:{v}:{s}" for (g,a),(v,s) in sorted(candidate.items()) if g.startswith(prefixes))
(ROOT / "resolved-versions.txt").write_text(report + "\n")
print(report)
print("Baseline framework and Jackson 2 graph preserved. Full trees retain version mediation/evictions.")
