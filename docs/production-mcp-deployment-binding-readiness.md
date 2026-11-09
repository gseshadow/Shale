# Production MCP immutable deployment binding readiness

**Startup diagnostic preparation — 2026-11-09:** accepted operator discovery now records Linux /
Microsoft OpenJDK 21.0.13, one production replica, OS `Etc/UTC`, unset `TZ`/`WEBSITE_TIME_ZONE`, SQL
clock UTC, and an existing empty `AiCaseDataDeployment` at `shale.database.windows.net/Shale`.
All three dedicated identities passed production verification. The running JVM zone remains
UNVERIFIED. The [startup diagnostic implementation and release requirements](production-jvm-startup-diagnostic.md)
provide a separately governed future JAR route while all controls stay OFF. This supersedes the
historical statement that the repository has no narrow diagnostic implementation; it does not
claim the diagnostic is deployed, waive any binding gate or authorize deployment/SQL/settings changes.


Prepared 2026-11-09 against `codex/latest` at `9671ac5be4ccd0ee5895697f986b9e46ef08cc57`.
**Installation BLOCKED pending the confirmations below.** All SQL is an unexecuted proposal.
No production SQL connection, secret retrieval, SQL execution, JAR deployment or AI/MCP/OAuth
enablement occurred during preparation.

Production SQL identity verification is operator-reported **PASS for all three dedicated contained
users**. Accept that prerequisite; do not provision users again or repeat identity verification.
Completed migrations remain completed. Historical pending-identity statements in older records are
superseded for that exact gate only. Identity PASS does not establish complete live V2 attestation,
native acceptance, artifact/configuration parity or activation approval.

## Exact existing schema and binding contract

Authority: `CREATE TABLE dbo.AiCaseDataDeployment` in
`docs/sql/ai-integration/phase3b8-confinement.sql`; historical V1 has the same table shape.

| Position | Column | Required SQL definition |
| --- | --- | --- |
| 1 | `Id` | `tinyint NOT NULL PRIMARY KEY CHECK(Id=1)` |
| 2 | `Issuer` | `nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL` |
| 3 | `Resource` | `nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL` |
| 4 | `Contract` | `varchar(12) COLLATE Latin1_General_100_BIN2 NOT NULL CHECK(Contract='case-read-v1' AND DATALENGTH(Contract)=12)` |
| 5 | `JavaTimeZone` | `varchar(100) NOT NULL`; inherits destination database collation |
| 6 | `SqlTimeZone` | `sysname NOT NULL` (`nvarchar(128)`); inherits destination database collation |

There is no tenant ID, database-name column, enablement flag, timestamp, RowVer or audit column.
There are no binding-column defaults. PK plus enabled/trusted checks permit at most one row with
Id=1; installation requires exactly one. The enabled `dbo.AiDataImmutableDeployment` AFTER
UPDATE,DELETE trigger throws 59100: binding changes require governed pool drain and a new migration.
Preserve its exact reviewed definition and receipt-bound metadata. Never test it with production
mutations, disable it, truncate/drop/recreate the table, repair receipts or use MERGE/upsert.
Even an identical existing row is only read, never updated.

The runtime role has explicit SELECT/INSERT/UPDATE/DELETE/ALTER/CONTROL denials on the table.
The independent catalog verifier retains reviewed SELECT access. No application identity receives
INSERT permission. The already authorized dbo operator performs the eventual insert. Database/server
binding comes from destination checks and live attestation, not an invented column. A tuple in another
database proves nothing about this deployment. No schema, grant, identity or migration changes belong
to this package. V2 is the confinement version; `case-read-v1` remains the data/audit contract.

`AiCaseDataAttestation.verify()` requires exact issuer/resource, Java `ZoneId` equality to
`ZoneId.systemDefault()`, SQL wall-time mapping at 28 instants, independent identities, catalog and
source/module receipts. Binding installation is only one prerequisite to complete attestation.

## Canonical URLs and actual time zones

Use the [read-only operator checklist](production-mcp-binding-operator-discovery.md) for the supplied
`Shale/shale-api` Azure app and `shale.database.windows.net/Shale` database. It includes executable
PowerShell/Azure CLI/SSMS checks and distinguishes observed topology from approved OAuth identity.

`McpOAuthProperties` and roadmap section **Canonical operator configuration** identify the sole
source as approved effective `shale.ai.oauth.public-origin`. It has **no checked-in default**.
Production requires canonical HTTPS with lowercase host, no trailing slash, userinfo, path, query,
fragment, explicit default port or local/reserved test destination. Exact derivation:

```text
Issuer   = <approved public-origin>/oauth/mcp
Resource = <approved public-origin>/mcp
```

These are rules, **not confirmed production URLs**. The historical API hostname, desktop packaged
URL, Host/Forwarded headers, DNS reachability and synthetic fixtures are not deployment approval.
No independent issuer/resource override exists. Obtain the approved deployment record and effective
nonsecret property at its actual precedence: startup arguments, system properties, environment or
packaged profile. Never dump App Settings, environment, process arguments or property maps containing
secrets. Do not retrieve Key Vault references or secret values.

Startup scripts forward `JAVA_OPTS`; neither scripts nor azure/prod properties pin `user.timezone`,
`TZ` or `WEBSITE_TIME_ZONE`. Deployment-level injection is unknown. Java 21 is documented, but the
production JDK build and default zone are not established by source. The development container's zone
is irrelevant. Do not change production timezone settings to make verification pass.

For **each running replica/slot**, establish `ZoneId.systemDefault().getId()` using an existing
approved, narrowly scoped diagnostic. Do not use `jcmd VM.system_properties`, even filtered: it
retrieves all JVM properties and can expose secrets. Ordinary jcmd has no getter-evaluation command.
A missing getter result, programmatic default override, or replica disagreement is STOP, not UTC evidence.
Review only relevant nonsecret timezone startup settings and JDK build. This packet requires no JAR
deployment or restart. An isolated new JVM cannot prove another process's effective default.

The data-free helper prints its own Java zone/JDK and the 28 expected SQL table-variable rows:

```bash
java build/deployment-binding/BindingTimeZoneProbe.java
```

Run with the same JDK and verified timezone inputs; compare its zone to running-replica evidence.
An offline `-Duser.timezone=<observed-zone>` run can generate the matrix, but that argument does not
establish production configuration. The helper never contacts SQL or a network.

`SqlTimeZone` is an existing SQL `AT TIME ZONE` mapping name, **not** a session/database-wide timezone
setting. `discover.sql` reads `sys.time_zone_info` and SQL local/UTC clock observations. A current
offset cannot establish historical/DST mapping. Select the corresponding existing SQL name and
compare all 28 Java samples: 2025–2028, months 1/3/4/7/10/11/12, day 15 at 00:00 UTC. These exactly
match current attestation, not exhaustive future DST proof. Retain compatible JDK/SQL timezone-rule
evidence; subsequent rule/configuration changes require review and fresh attestation.

## Exact operator confirmations still required

| Value/evidence | Current finding |
| --- | --- |
| Exact logical server from `SERVERPROPERTY('ServerName')` and `DB_NAME()` | Historical DNS/database: `shale.database.windows.net` / `Shale`; exact live strings and approved target/slot unconfirmed here. Server property may differ from DNS spelling. |
| Approved effective `shale.ai.oauth.public-origin`, configuration revision/provenance and PRODUCTION identity | UNKNOWN; no approved production MCP origin checked in. |
| Exact derived `Issuer` and `Resource` | UNKNOWN until approved origin confirmed; never substitute historical API URL. |
| Running Java zone on every replica, JDK build, relevant effective timezone settings | UNKNOWN; no live runtime evidence supplied. |
| Existing SQL mapping name and 28 expected Java wall times | UNKNOWN; do not assume UTC or infer from a single offset. |
| Binding count and all six existing values | UNKNOWN; development must not query production. |
| Current V2 source/catalog verification and installed-change record | Require existing-state evidence; never replay completed migrations. Missing V2 objects/receipts mean STOP for separate review. |
| Effective app controls OFF, AI pools absent/drained, SQL global/tenant OFF, configuration/DDL freeze | Require current bounded nonsecret operator evidence. |
| Exact packet/inputs hashes, change ticket, execution approver/window | Required for separately authorized initial insert; independent of accepted identity PASS. |

## Read-only operator checks and unexecuted installer

Packet: `docs/sql/ai-integration/deployment-binding/`. Run SQLCMD entrypoints from the reviewed
repository root because `:r` paths are root-relative. Require SQLCMD mode, `:on error exit`, validated
TLS, bounded timeouts and immediate exit-code checks. Use an already authorized operator and secure
interactive password prompt: no `-P`, `-C`, new grant or production secret retrieval.

```text
sqlcmd -S <approved-server> -d <approved-database> -U <existing-operator> -N -b -V 16 -l 5 -t 120 -i docs/sql/ai-integration/deployment-binding/discover.sql
```

This is a future operator command, not development execution authorization. After **each** invocation
check `$LASTEXITCODE` in PowerShell or `$?` in bash and stop on nonzero. Run other entrypoints with
the same options, replacing only the `-i` path. Never use a runner that continues after errors.

- `discover.sql`: fresh dbo/autocommit/NULL application context and EngineEdition 5; reports schema,
  constraints, PK, immutable trigger, V2 receipts, binding count/tuple and SQL zones. Missing evidence
  is STOP. It reads no tenant/Case/credential rows.
- `inputs.sql`: NULL/0 defaults refuse installation. Fill only in a separate reviewed operator copy;
  keep `@BindApproved=0` for read-only checks. Use doubled quotes for SQL literals, never SQLCMD
  string substitution. Inputs remain `nvarchar(max)` until checked to prevent silent truncation.
- `preflight.sql`: reuses unchanged `phase3b8-confinement-verify.sql` for receipt-bound schema,
  constraints, immutable trigger/owners, RLS, dependencies, role grants and reference data. It pins
  the packaged V2 source digest independently, checks exact destination/derivation/lengths, SQL OFF
  gates, all wall-time samples and byte-exact existing tuple (including trailing spaces/case).
  It reports EMPTY_READY_FOR_SEPARATE_INSTALL_AUTHORIZATION or EXACT_EXISTING_BINDING_NO_CHANGE.
  It is not full runtime identity/effective-permission attestation. Missing visibility fails closed.
- `install.sql`: **proposal only**; requires separate approval bit and OFF/evidence acknowledgements.
  One XACT_ABORT transaction takes TABLOCKX/HOLDLOCK (five-second lock timeout), repeats the same
  verifier/assertions and inserts only if empty. A matching existing row is a no-op; conflicting or
  extra rows fail. No UPDATE, DELETE, schema, identity, permission, audit or capability writes exist.
- `postcheck.sql`: read-only repeat of preflight plus exactly one committed matching row. Uses the
  same confirmed inputs; no installation approval bit required.

The full verifier uses table variables for analysis; these are not persistent mutations. Discovery
can succeed while readiness fails. Never edit historical verifiers or regenerate/relabel receipts.

## Eventual separately authorized execution sequence

1. Freeze reviewed commit/packet hashes. Retain completed migration receipts and accepted three-user
   identity PASS. Review target, approved origin and every running JVM zone. Keep OAuth composition,
   authorization and exchange, AI enabled, MCP enabled, search, overview, **and** case-data-composition
   all false. Confirm AI pools absent/drained; freeze concurrent configuration/DDL changes. Preserve
   ordinary identities, signing/session settings and CORS.
2. Run **discover**, check exit status and retain bounded nonsecret evidence. Missing table/receipt
   or conflicting existing tuple means STOP. No provisioning or migration replay/repair follows.
3. Generate 28 samples offline from verified Java zone/JDK. Populate operator-copy inputs with exact
   server/database/origin/derived URLs/zones, samples and OFF/evidence acknowledgements. Review origin
   against existing `McpOAuthProperties` rules. Keep installation approval zero. Record input hashes.
4. Run **preflight**. Stop on failure/unknown/missing prerequisite/mapping disagreement. An exact
   existing binding needs no install: retain it and run read-only postcheck.
5. For an empty table only, obtain separate authorization for this exact insert/inputs/window as the
   final execution gate. Set only `@BindApproved=1` in the approved copy, rereview its hash, run
   **install.sql** once and check exit status immediately.
6. From a **fresh connection**, run **postcheck.sql**, then existing read-only cumulative
   `docs/sql/ai-integration/verify.sql`. Check each exit status. Retain exactly one matching tuple,
   preserved catalog/source/trigger/constraints and SQL global/tenant plus effective app OFF evidence.
7. STOP. Installed binding is not readiness/activation. Complete live V2 attestation, native/live
   acceptance, ingress/OAuth/operational/legal/security/tenant approvals and any JAR/configuration
   changes remain separate gates. Never run disposable-only Azure JUnit runners against production.

## Failure recovery, rollback and audit compatibility

On preflight/lock-timeout/constraint/catalog/mapping failure, STOP and retain a sanitized stage/error
number. Do not broaden permissions, increase timeouts as a repair or change timezone/configuration.
Errors roll back the transaction. Connection loss before commit normally rolls back; a lost commit
acknowledgement is **UNKNOWN**. Reconnect and run read-only discovery/postcheck before any authorized
retry. An exact match remains a no-op. A conflict is never overwritten.

Before commit, transaction rollback is the only reversal. After commit this packet has **no row
rollback**. Keep controls OFF and pools absent/drained, retain binding/invocation/quota/audit history
and receipts, investigate and require a separately reviewed additive migration with governed pool
drain for an incorrect committed binding. Never disable the trigger or delete the row. JAR rollback
does not authorize row changes. No SQL uninstall, broad grant or shared-runtime fallback is provided.

Audit review: operator metadata reads and one non-tenant deployment-configuration insert introduce
no PHI/Case read, OAuth issuance or tenant business mutation. Existing audit allowlists define no
deployment-binding entity. Do not invent a tenant/entity type or ad-hoc application audit row. Retain
operator/change-ticket/packet hash/before-after tuple and approved database auditing evidence outside
application tables. A requested in-database binding event needs separate audit architecture review.
Existing PHI/integration/entity-action audit, RLS, quota/proof, identity and permission contracts remain
unchanged; no audit schema or event vocabulary is modified.

## Development verification limits

Pre-edit impact inventory: `AiCaseDataAttestationTest`, V1/V2 migration contract tests,
`AiCaseDataLocalSqlServerTest`, Azure acceptance safety/disposable tests, `OAuthConfigurationTest`
and `McpProductionOffBootTest`. Existing production behavior/fixtures remain unchanged. New offline
`build/deployment-binding/test_packet.py` checks SQLCMD include closure/read-only entrypoints,
one guarded durable INSERT/no replacement, fail-closed defaults, exact V2 source pin, and Java helper
output under Denver DST and Kolkata non-hour offsets. These are packaging/contract checks, not SQL
execution/production acceptance. No local SQL mutation fixture is run. Live SQL compilation,
concurrency and commit semantics remain unverified until separately authorized operator execution.

Validation on 2026-11-09:

| Command | Result |
| --- | --- |
| `python -m unittest discover -s build/deployment-binding -p test_packet.py -v` | 5 PASS |
| `python -m unittest discover -s build/test-selection -p test_select_tests.py` | 32 PASS |
| `mvn -pl shale-data,shale-server -am -Dtest=AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,AiCaseDataAttestationTest,OAuthConfigurationTest,McpProductionOffBootTest -Dsurefire.failIfNoSpecifiedTests=false test` | 20 PASS |
| `python build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format json` followed by its `selected_maven_args` | security-data suite: 142 PASS |
| `mvn test` | critical reactor: 228 PASS |
| `git diff --check` | PASS |

Maven execution used workspace-installed Maven 3.9.11/JDK 21.0.13, the session proxy settings file,
workspace local repository and existing system CA trust (`-s work/maven-settings.xml`,
`-Dmaven.repo.local=/workspace/Shale/work/m2`,
`JAVA_HOME=/workspace/Shale/work/jdk21`,
`MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts`). The initial bundled compiler,
default unwritable Maven cache and missing proxy/CA configuration were corrected without repository
build changes. SQL opt-ins were absent/removed; the selected local SQL class ran zero fixture tests,
which is not SQL acceptance. The selector's full-suite recommendation from selector infrastructure
changes is informational; no runtime production behavior changed. SQL files were inspected/tested as
text only. Changed-file review maps packet/helper to the new Python checks, selector mapping to its
32 tests, and unchanged attestation/OAuth/default-OFF/security contracts to the focused/affected suites.
