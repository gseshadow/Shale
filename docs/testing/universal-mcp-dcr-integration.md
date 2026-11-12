# Completed MCP DCR integration into codex/latest

Reviewed and verified November 12, 2026 (UTC). Integration only; production OFF.

## Provenance and integration method

| Reference | Exact commit |
| --- | --- |
| Destination `codex/latest`, merged PR #1957/C1 | `f3fb48ff8646f3c0717779678ee20a206682d4ff` |
| Shared C1 implementation / merge base | `8aea2c4c22489e0f23b057ec479dcb01b53d31e2` |
| Accepted PR #1958/C2 head | `d930ddd2b72bfa341f0345fafb13c68264b2cee3` |
| Completed C2 source branch, merge of #1958 into C1 branch | `c50e186a64b4a63414b96ad057b8e19b19f07631` |
| Integration merge on fresh `codex/integrate-mcp-dcr` branch | `9266dcd0cb9c26d270e3b00e97fbcde2ca55089c` |

PR #1957 landed C1 in `codex/latest`. PR #1958 subsequently landed C2 in
`codex/universal-mcp-pr-c`, so its changes did not reach `codex/latest`.
Remote refs were fetched and checked before integration. No newer destination
changes existed beyond the C1 merge at that point.

Created the integration branch from the exact destination commit, then merged
the completed source branch with `git merge --no-ff --no-commit c50e186a` and
committed after inspecting the staged tree. There were no conflicts or manual
resolutions. Shared C1 history is retained without duplicating its changes.

## Exact tree and historical-artifact comparison

Both the accepted C2 head and completed source merge have tree
`358d324778744982433770c8694176f530e8c9da`. The integration merge has that same
full tree: zero file differences, including tests, generated SQL and acceptance
evidence. The binary patch from C1 implementation to accepted C2 is byte-identical
to the patch from destination to integration merge: 70 paths, 4,178 insertions,
33 deletions, 2,562,841 binary-patch bytes.

```text
git merge-base f3fb48ff c50e186a                    # 8aea2c4c...
git diff --exit-code d930ddd2 c50e186a             # empty
git diff --exit-code d930ddd2 9266dcd0             # empty
git diff --binary 8aea2c4c d930ddd2                # equals next patch byte-for-byte
git diff --binary f3fb48ff 9266dcd0
```

SHA-256 comparison of all 280 pre-existing tracked artifacts under `docs/sql`,
`shale-data/src/main/resources/ai`, `build/sql-confinement` and
`build/deployment-binding` against the destination found zero changes. This
includes historical C1 artifacts, receipts and immutable deployment bindings.
New C2 successor artifacts are additions, exactly as accepted in #1958.

The sole additional difference from the accepted C2 tree in the final PR is
this integration report. No implementation, test, control, evidence or historical
documentation was edited. No additional behavior or audit/schema contract is
introduced; existing transactional registration, tenant approval, consent,
credential and Case audits remain as accepted.

## Targeted verification executed for this integration

The selector identified `cases`, `security-data` and `server`. The focused set
below covers the actual C2 delta: registry/store delegation, versioned attestation,
Case composition, registration validation, metadata/gates, source-aware browser
and credential routing, original CIMD retrieval and manual-client mappings.
All-OFF Boot uses synthetic loopback ordinary database destinations and allocates
no AI pool. No production connection is used.

```bash
JAVA_HOME=/workspace/Shale/work/tools/jdk-21.0.13+9 \
work/tools/apache-maven-3.9.11/bin/mvn -o -s work/maven-settings.xml -B \
  -Dmaven.repo.local=/workspace/Shale/work/m2 -pl shale-server -am \
  -Dtest=DcrRegistrationEndpointTest,DcrConnectionConfigurationTest,DcrApplicationMetadataTest,OAuthMetadataTest,CimdConnectionConfigurationTest,AiDcrApplicationStoreTest,CimdBrowserBindingTest,CimdClientAuthenticationTest,OAuthClientMappingTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,CimdMetadataTest,CimdRetrievalTest,McpCaseCompositionTest,AiCaseDataAttestationTest,AiCaseDataAccessTest,McpProductionOffBootTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

JAVA_HOME=/workspace/Shale/work/tools/jdk-21.0.13+9 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
work/tools/apache-maven-3.9.11/bin/mvn -s work/maven-settings.xml -B \
  -Dmaven.repo.local=/workspace/Shale/work/m2 -pl shale-server -am -DskipTests package

PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/sql-confinement -p test_dcr_v3.py -v
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/sql-confinement -p test_external_v2.py -v
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s build/test-selection -p test_select_tests.py
python build/test-selection/select_tests.py --base f3fb48ff --head HEAD --format json

# From shale-web:
npm test -- src/OAuthConsent.test.tsx
VITE_SHALE_API_BASE_URL=https://shale.test npm run build
```

Results: Java **104 passed across 17 classes**, zero failures/errors/skips;
four-module Maven compilation/test and executable server packaging passed.
Generation **9 passed** (C2 source remains
`086cf01d99097a2a5a43d9e5a77fdf5a28443916c1795559e55aa2d8e97f0a13`), selector
**32 passed**, consent-page **19 passed**, web typecheck/build passed. Diff
whitespace checks passed. No broad affected/critical/historical campaign was
repeated: this merge introduces no behavior beyond the already accepted tree.

The first packaging attempt in offline mode lacked the cached JAR plugin. The
online attempt then exposed the downloaded JDK's missing proxy CA trust. The
final command explicitly uses the existing environment CA store; TLS verification
remains enabled. No dependency versions, POMs or source were changed to address
these local build-environment prerequisites.

## Reused acceptance and remaining deployment gates

The unchanged [C1 SQL acceptance](universal-mcp-pr-c1-verification.md) and
[C2 verification/evidence](universal-mcp-pr-c2-verification.md) remain authoritative
for their recorded scope. In particular, reuse the four successful C2 real SQL
scenarios and unmodified Hermes `74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5`
URL-only DCR acceptance in two fresh profiles at `https://shale.test/mcp`, plus
the existing CIMD/manual compatibility evidence. These were not rerun or
represented as new integration SQL/browser executions. No SQL fixture import
or database connection was needed for this integration.

Native Azure acceptance, explicit V3 role/composition review, production ingress
attribution/rate tuning, lifetime capacity policy, direct CIMD egress and existing
release/operator gates remain unchanged. Development-only production activation
guards are retained. No deployment, production SQL connection, production
configuration change, activation or automatic PR merge is performed. All
production controls remain OFF.
