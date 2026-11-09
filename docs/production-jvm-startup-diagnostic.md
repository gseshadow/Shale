# Production JVM startup diagnostic

Prepared 2026-11-09 on `codex/latest` baseline `259180cf1c6685f19954fb13352b0cd24fc76a4e`.
This change prepares a dormant server release. **No deployment, restart, Azure setting change,
production SQL, secret retrieval or AI/MCP/OAuth activation is authorized or performed here.**

## Accepted discovery and remaining evidence

Operator-reported completed production discovery:

- App Service `Shale/shale-api`: Linux, Microsoft OpenJDK 21.0.13, one replica observed.
- OS zone `Etc/UTC`; `TZ` and `WEBSITE_TIME_ZONE` unset.
- SQL server `shale.database.windows.net`, database `Shale`; SQL clock UTC.
- `dbo.AiCaseDataDeployment` exists with zero rows.
- All three dedicated SQL identities passed verification; do not repeat provisioning or verification.

These facts do not establish the running JVM default. Its exact
`ZoneId.systemDefault().getId()` remains **UNVERIFIED** until fresh evidence from the deployed
application covers every current production replica. Do not infer `UTC` or `Etc/UTC`, normalize an
alias, select a SQL mapping, or insert a binding based on OS/SQL clocks. Other binding/activation gates,
including approved effective OAuth origin and 28-sample SQL mapping, retain their existing scope.

## Implementation and ordinary startup/logging review

`com.shale.server.diagnostics.JvmStartupDiagnostic` is an unconditional Spring Boot
`ApplicationRunner` discovered by the existing server component scan. Boot invokes it in the actual
Shale process after context initialization and before ApplicationReadyEvent. It reads the default
zone at that point, Java's `Runtime.version().toString()`, and only `System.getenv("WEBSITE_INSTANCE_ID")`.
No child Java process, controller, HTTP mapping, actuator, JMX, agent, SQL or network operation exists.
It has no AI/OAuth/MCP bean/configuration dependency. Repeated calls to the same runner emit at most
one event; a replacement application process emits a fresh event.

The single INFO message payload has exactly three fields, in this order:

```text
SHALE_JVM_STARTUP zoneId=<exact-zone-id> javaVersion=<runtime-version> replicaId=<App-Service-instance-id>
```

Existing Boot logging adds its ordinary timestamp/level/process/logger prefix. Example only:

```text
SHALE_JVM_STARTUP zoneId=Etc/UTC javaVersion=21.0.13+6-LTS replicaId=0123456789abcdef
```

No version/zone/replica value in that example is a production getter result. Java version includes
runtime build information; there is no system-property lookup/dump, command-line dump, environment
enumeration, credential, connection string, exception text or additional diagnostic field.
Each value is at most 128 characters and permits only its narrow ASCII token alphabet. Missing,
blank, overlong or malformed values are replaced completely with `unavailable`, never truncated or
echoed. Replica allows letters/digits/underscore/hyphen; zone additionally permits plus/dot/slash/colon;
version additionally permits dot/plus. This prevents newline, whitespace, delimiter and terminal-control
injection. A denied replica read yields `unavailable`. Unexpected runtime failure is suppressed without
exception logging so this observation cannot block ordinary startup; missing evidence still blocks
readiness. Fatal JVM errors are not swallowed.

Repository logging defaults already enable INFO for `com.shale`; no logging configuration changes
are needed. Existing effective logger levels, filters, appenders and collection/retention must deliver
this logger's INFO message to the approved private operator log destination. Logging disabled or
filtered out is missing evidence, not proof of a zone. Do not enable global DEBUG/TRACE or MCP payload
logging. Keep `logging.level.io.modelcontextprotocol.modern.JsonRpc=OFF`.

The runner changes no time-zone, auth, session, RLS, OAuth, MCP, AI, port or ordinary configuration.
It has no database dependency and cannot require AI tables, identities, secrets or a binding while
all controls are OFF. The marker proves a startup-time observation only: it is not application health,
SQL readiness, attestation, or proof against a later programmatic time-zone change. Review any later
change to runtime timezone behavior and collect fresh governed evidence.

Audit compatibility: this is nonsecret operational metadata, with no PHI/sensitive application read,
actor/tenant action or domain/admin mutation. It intentionally creates no PHI, integration, entity-action
or session-security audit row. Existing audit schema and required business transaction audits remain
unchanged; no audit migration or deferred audit enhancement is needed.

## Exact requirements for a separately authorized deployment

1. Review/merge the draft PR into `codex/latest`; pin the exact approved resulting source commit.
   Record JDK 21 and Maven versions. Run focused/affected/critical checks below, then build:

   ```bash
   mvn -pl shale-server -am clean package -DskipTests
   sha256sum shale-server/target/shale-server-1.0.138.jar
   ```

   Version is 1.0.138 at this baseline; use the exact versioned filename if the approved source has
   advanced. Deploy only the repackaged executable JAR, never `.jar.original`. Record its SHA-256,
   source and build provenance; verify the uploaded binary matches. No desktop/web rebuild or version
   bump is introduced by this diagnostic.
2. Retain the current compatible rollback JAR/hash and existing release/settings/start-command record.
   Use existing ordinary SQL/caller compatibility acceptance and change records; do not replay completed
   migrations or identity checks. This diagnostic requires **zero SQL/schema/grant/identity/binding
   changes and zero new secrets or Azure settings**. A missing ordinary release prerequisite is STOP,
   not permission to change production. Preserve the original session-binding cutover, signing secret,
   ordinary auth/runtime configuration, port, CORS, TLS, time-zone and startup command.
3. Require bounded nonsecret evidence that all eight effective controls remain false, including
   overrides, without dumping App Settings, process arguments, environments or property maps:

   ```properties
   shale.ai.enabled=false
   shale.ai.mcp.enabled=false
   shale.ai.mcp.case-data-composition-enabled=false
   shale.ai.mcp.search-cases-enabled=false
   shale.ai.mcp.get-case-overview-enabled=false
   shale.ai.oauth.composition-enabled=false
   shale.ai.oauth.authorization-enabled=false
   shale.ai.oauth.exchange-enabled=false
   ```

   Keep PRODUCTION identity, readiness disabled, AI pool absent and SQL global/tenant/integration
   authority OFF. Preserve existing values; do not add a public origin or AI/verifier/bootstrap
   credentials merely to start this diagnostic. If existing settings cannot meet these requirements,
   stop for separate review rather than changing them under this task.
4. An authorized operator must approve the exact artifact, existing production target
   `Shale/shale-api`, change window, rollback and normal deployment/restart operation. The current
   running JVM cannot execute code absent from its deployed JAR; **a new server JAR deployment and
   application process restart/replacement are required**. No SSH evaluation or separate Java probe
   can substitute. This PR alone supplies no production getter evidence. One replica was observed;
   account for normal interruption/replacement during the future release window.
5. Use the existing private log destination with INFO collection available before startup. No new
   endpoint, logging setting change, public log share, agent or debugger is required. If current log
   collection is inadequate, stop and separately scope that change. After authorized release, check
   ordinary health/DB health and the established release-specific auth/session/remembered-client smoke
   and all-OFF/no-disclosure checks in [ordinary release compatibility](dormant-server-release-compatibility.md).
   Retain accepted prior smoke at its scope; it is not a test result for a new binary.
6. Collect the bounded per-replica evidence below and STOP. This closes only the startup-zone evidence
   item if valid; no binding installation, readiness composition, SQL, OAuth or MCP enablement follows.

## Identify the result for every production replica

During the separately authorized release, record the deployment's UTC window and exact artifact hash.
Use existing approved Azure access to list current production workers (read-only):

```bash
az webapp list-instances --resource-group Shale --name shale-api --query '[].{instance:name,state:state}' --only-show-errors --output json
```

In the existing authorized private App Service Log Stream or retained application logs, search for
the literal `SHALE_JVM_STARTUP ` marker and retain **only matching diagnostic entries** from the new
release window, with the normal log timestamp (converted to UTC using its logged offset) and artifact/deployment correlation. Do not export
surrounding logs, credentials, request payloads or configuration. Use the `replicaId` field to match
`WEBSITE_INSTANCE_ID` to the worker inventory; if Azure presents a different identifier form, require
existing platform mapping evidence rather than assuming equality or falling back to hostname.
Retain a row per active worker: replica ID, UTC log timestamp, exact zone ID, exact Java version and
source/artifact hash from the release record. The three payload fields remain unchanged.

List instances again after collection. Evidence must cover the final complete set of serving workers
and any production-serving slots. Inventory actual slots explicitly if used; do not equate staging
logs with production. New/replaced/scaled workers require their own fresh startup marker. Repeated
historical entries for a recycled worker are normal; correlate timestamp and artifact to the current
process rather than counting all retained entries as duplicate startup emissions. A slot swap without
a new process requires trustworthy existing process/artifact correlation; otherwise evidence is missing.

STOP on a missing marker, `unavailable`, an unmatched/missing replica, unknown artifact/process
correlation, wrong JDK, inconsistent zones/JDK builds across workers or an unsuccessful ordinary/OFF
smoke. A marker may precede a later startup failure; require successful startup/health too. Do not
turn on features, change zones, enable diagnostics endpoints, list secrets/settings, rerun migrations
or silently repair the discrepancy. Retain the blocker for review. A later recycle or runtime/rules
change can invalidate old evidence and requires appropriate refresh.

## Local validation and test impact

Pre-edit references inspected: `McpProductionOffBootTest`, `ShaleServerServiceConfigurationTest`,
`OpenApiDocumentationTest` and the opt-in SQL production-readiness test. No existing startup diagnostic
or server time-zone setter was found. The new runner maps to `JvmStartupDiagnosticTest` (exact payload,
missing/unsafe/overlong fields, current JVM values, one INFO event/no exception and unchanged zone).
The existing `McpProductionOffBootTest` now asserts one exact startup entry during real azure/prod Boot
with all eight switches OFF; it retains ordinary auth/session bean checks, health, protected-route 401,
disabled-route 404 and absent AI pool. It uses synthetic local configuration, never production SQL.
The selector's server suite covers neighboring API/OAuth wiring; the critical reactor protects ordinary
cross-module behavior. Opt-in SQL tests must stay unenabled.

Commands/results are recorded in the draft PR after execution. Environment-specific Maven/JDK/cache
settings are workspace-only and do not alter repository build or runtime configuration.

Validation on 2026-11-09 used Maven 3.9.11 and Temurin OpenJDK 21.0.13+9-LTS in this isolated Linux
workspace. Microsoft OpenJDK production behavior is not claimed as executed. Maven was invoked with
`-B -ntp -s work/maven-settings.xml`, workspace-local cache, `JAVA_HOME=/workspace/Shale/work/tools/jdk21`
and `MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts`. Initial validation stopped
before compilation because the supplied runtime lacked a Java 21 compiler; a workspace-local JDK
resolved that setup issue without repository build changes.

| Command | Result |
| --- | --- |
| `mvn -pl shale-server -am -Dtest=JvmStartupDiagnosticTest,McpProductionOffBootTest -Dsurefire.failIfNoSpecifiedTests=false test` | 5 PASS, zero failures/errors/skips |
| `python build/test-selection/select_tests.py --base 259180cf1c6685f19954fb13352b0cd24fc76a4e --head HEAD --format json` | server area; no escalation/full suite |
| Selector's exact `selected_maven_args` (below) | 49 PASS, zero failures/errors/skips |
| `mvn test` | 228 PASS, zero failures/errors/skips across critical reactor |
| `git diff 259180cf HEAD --check` and changed-document relative-link checks | PASS |

The affected command selected by the repository was:

```bash
mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest,com.shale.server.diagnostics.JvmStartupDiagnosticTest,com.shale.server.mcp.McpCaseCompositionTest,com.shale.server.mcp.McpProductionOffBootTest,com.shale.server.oauth.OAuthAuthorizationFlowTest,com.shale.server.oauth.OAuthClientMappingTest,com.shale.server.oauth.OAuthCodeExchangePolicyTest,com.shale.server.oauth.OAuthConfigurationTest,com.shale.server.oauth.OAuthFilterIsolationTest,com.shale.server.oauth.OAuthMetadataTest,com.shale.server.oauth.OAuthRevocationPolicyTest,com.shale.server.oauth.PinnedClientAssertionTest,com.shale.server.oauth.ShaleOAuthHumanTest -Dsurefire.failIfNoSpecifiedTests=false test
```

SQL fixture opt-ins remained absent; the selected `OAuthAuthorizationFlowTest` has no enabled SQL
fixture executions in this run. This is no new SQL/native acceptance evidence. No full-suite escalation
was recommended. Executable-JAR build/hash evidence is retained in the draft PR; production upload/hash,
private log collection and per-replica getter results remain unexecuted operator release requirements.
