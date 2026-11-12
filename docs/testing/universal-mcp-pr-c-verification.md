# PR C dependency review verification

Baseline: `3f044f8dd422190d910a1553ee3ef886b259a7e0` (`codex/latest`, merged
#1955/#1956). Date: November 12, 2026 (America/Denver).

**DCR implementation and acceptance are blocked and unverified.** This change
only documents the [specific security dependency and smallest necessary
correction](../architecture/universal-mcp-pr-c.md). It changes no executable,
SQL, resource, configuration, feature gate, receipt or deployment binding.
No production service or SQL endpoint was contacted.

## Executed checks

The existing generation tests ran against the unchanged baseline artifacts:

```text
python -m unittest discover -s build/sql-confinement -p 'test_cimd*_v1.py' -v
# 7 tests passed, 0 failures/errors/skips.
python -m unittest discover -s build/sql-confinement -p test_registry_v1.py -v
# 3 tests passed, 0 failures/errors/skips.
```

These tests check registry, proof and OAuth generation, immutable predecessor
preservation and separate credential namespaces. They do not execute SQL or
prove DCR functionality.

Existing Java configuration/metadata/client tests passed: **21 tests, zero
failures/errors/skips**. The command was:

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-server -am \
  -Dtest=OAuthMetadataTest,OAuthConfigurationTest,CimdConnectionConfigurationTest,CimdClientIdentifierTest,OAuthClientMappingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Maven here is the SHA-512-verified Apache Maven 3.9.11 binary under `work/tools`;
`JAVA_HOME` selects the SHA-256-verified Temurin JDK 21.0.13+9 downloaded from its
official release. `MAVEN_USER_HOME` and the dependency repository are task-local.
The settings file uses the inherited environment proxy, and JVM TLS uses
`/etc/ssl/certs/java/cacerts`. Initial attempts using Maven without explicit proxy
settings and the image's incomplete JDK failed; both setup issues were corrected
before this successful run. No build file or dependency version was changed.

The additional maintained filter-isolation test passed: **1 test, zero
failures/errors/skips**, bringing executed Java coverage to **22 tests**:

```text
mvn -s work/maven-settings.xml -B -Dmaven.repo.local=/workspace/Shale/work/m2 \
  -pl shale-server -am -Dtest=OAuthFilterIsolationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

This test exercises the existing filter chain using servlet requests, including
the blocked registration route and credential/forwarded-header isolation. It is
not a stock-client HTTP connection or SQL test.

Final documentation checks:

```text
python build/test-selection/select_tests.py \
  --base 3f044f8dd422190d910a1553ee3ef886b259a7e0 --head HEAD \
  --format json --output work/pr-c-selection.json \
  --plan-output work/pr-c-selection-plan.json
git diff --check
git diff --exit-code 3f044f8dd422190d910a1553ee3ef886b259a7e0 \
  -- shale-server/src/main shale-data/src/main docs/sql build/sql-confinement
```

The selector returned no affected areas, modules, modified tests, escalation or
full-suite recommendation; `selector_tests_required=false`. Only these two new
documentation files are committed. Diff/unchanged-source checks passed. The
selector's generic `mvn test` critical command was not run: there is no behavior
change or demonstrated regression justifying a broader campaign for this report.
The targeted baseline checks above passed; required DCR acceptance remains open.

## Required acceptance not executed

| Requirement | PR C result |
| --- | --- |
| Fresh unmodified DCR-capable client with only MCP URL | Not executed; no working DCR path exists. Client name/version: not applicable. |
| Automatic RFC 7591 registration | Not implemented. No registration HTTP evidence. |
| Existing Shale browser login, tenant-admin approval, separate explicit consent | Not executed for DCR. |
| Token issuance and real MCP Case disclosure | Not executed for DCR. |
| Refresh, revocation and application/approval invalidation | Not executed for DCR. |
| Cross-tenant SQL isolation | Not executed in this environment. |
| Fresh Hermes/CIMD interoperability regression | Not executed. Prior PR B evidence is not a PR C result. |
| Manual-client mapping/configuration regression | Existing Java tests passed; real SQL/HTTP acceptance unexecuted. |

The approved private `authoritative-candidate.sql` is absent, including the
previously recorded attachment location. Its required SHA-256 is
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
No substitute schema, fixture mutation, owner-seeded DCR credentials or skipped
SQL test is counted as acceptance. No sanitized SQL success evidence exists for
this run because no SQL acceptance test executed.

## Remaining implementation and deployment requirements

Implement and verify the narrow source-aware registry/lifecycle correction,
then durable bounded RFC 7591 registration with distributed abuse controls and
`none`/S256 plus supported confidential authentication. Only then wire and
advertise an explicitly enabled development composition. Run every acceptance
row above, with matching sanitized HTTP/SQL evidence and exact stock-client
version, in a fresh nonce environment using the unchanged private fixture.

Production requirements remain open, including the existing binding,
infrastructure, egress and operator/tenant gates. This report does not relax
them. All production MCP/OAuth/CIMD/DCR controls stay OFF. Do not deploy, activate,
advertise production DCR or merge automatically.
