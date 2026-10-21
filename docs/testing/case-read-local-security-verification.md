# Minimized Case reads: disposable local SQL verification

## Phase 1R candidate — 2026-10-21

**Candidate implemented; synthetic acceptance recorded below; authoritative Case SQL acceptance BLOCKED.**
Base: live `origin/codex/latest` at `73c23475a4001dfcd36ec8a06c91d85cd1bad6c3`, isolated branch
`codex/phase1r-case-read-rls`. The original checkout and unrelated work were preserved. October 20
operator-reported retirement/API/installed-desktop acceptance remains accepted at its original scope;
no retirement, recovery, rehearsal, desktop or phone/browser check was repeated.

### Implementation and review

[Phase 1R SQL entry points and rollback](../sql/phase1r/README.md) add only these FILTERs to existing
enabled, schema-bound `sec.TenantFilter`: `dbo.Users` and actual **`dbo.auditLog`** use
`sec.fn_FilterByTenant(ShaleClientId)`; `dbo.Statuses` uses
`sec.fn_FilterByTenantOrGlobal(ShaleClientId)`. No BLOCK, function, grant, row, history, column or index
change is part of the candidate. A schema extended-property receipt records only candidate-owned additions,
full before/after predicate fingerprints and database/policy/function identities. All-matching pre-existing
coverage is a true no-op without a receipt. Repeated application preserves the original receipt. Scoped
rollback preserves pre-existing matching predicates and refuses catalog/function/receipt drift.

Preflight/apply/postcheck/rollback share one transaction engine, fail-fast guards and an exclusive transaction
application lock. Review pins exact function definitions and all unrelated predicate IDs/definitions;
unknown/disabled/conflicting target state, owner changes, wrong collation/identifiers or changed inventories
stop. No state is repaired or normalized. Fixed ADD batches are deferred with `sp_executesql` to avoid SQL
Server's eager duplicate-predicate binding even in a skipped branch. No arbitrary dynamic SQL is accepted.
Coordinated exclusion of other administrative DDL remains necessary; this lock covers these entry points.

Reviewed AGENTS/prompt rules, database-schema/tenancy, roadmap §§2.5/9 and audit/service model, this runbook's
assessment/dependencies, auth cutover Stage A/B, App Service order and R4/current deployment acceptance.
Source review confirmed current shared password callers use V1 with BCrypt and **no SELECT fallback**;
remembered lookup uses V2 before runtime-qualified serializable rotation; verified JWT identity supplies
context for durable sessions. Stage A/B guards deliberately require the pre-FILTER baseline, so their old
preflight must not be replayed after this candidate. Existing runtime stamps both mutable context keys on
every borrow and closes a failed lease. Minimized Case reads retain explicit tenant/actor/join predicates,
pre-page filtering, same-transaction required Overview append/commit and the summary-web exemption.

Test-impact review covered existing migration/auth/session/Case JDBC/controller tests, real SQL fixtures,
importer/runner guards and pool initialization. New tests reuse existing nonce/SID cleanup and auth fixture
patterns without invoking `AuthPermissionCutoverFixture` or any old operator/retirement packet. The synthetic
fixture installs current V1/V2 definitions with explicit confined grants/denials in a new database; that is
functional compatibility evidence, **not Stage A/B deployment or production grant acceptance**.

One existing harness defect was proven using SQL Server 2022 metadata: an exact FILTER is rendered as
`([sec].[fn_FilterByTenant]([ShaleClientId]))`, while the old normalized comparison expected no outer pair.
The only Case harness change accepts both exact equivalent catalog spellings. Function, column, enabled
policy and single-FILTER expectations and every runtime scenario remain intact. Real SQL regression checks
prove the wrapped strict match passes and wrong function/column fail. No importer extension or fixed
security requirement was weakened. The original authoritative artifact is unchanged.

### Offline artifact and evidence classification

| Evidence class | Current result / precise limit |
| --- | --- |
| Authoritative source | Prior supplied schema-only `shale-schema-source.sql`, 728,862 bytes, UTF-16LE; recorded SHA-256 `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`. **Absent from this workspace** after search of available workspace/tmp inputs. Original bytes were not read, regenerated or modified in this task. Historical schema findings remain attributed. |
| Derived authoritative schema | **BLOCKED / not generated; hash unavailable.** Must retain real constraints, computed definitions/`dbo.ToBitNV`, ownership/dependencies, collations and transitive FK/type/function inventory listed in the existing dependency table. |
| Candidate authoritative schema | **BLOCKED / not generated; hash unavailable.** Candidate migration source is committed separately; its Git hash is not a candidate-schema hash. No synthetic schema is substituted. |
| Synthetic real SQL | New loopback SQL Server 2022 database and synthetic Users/auditLog/Statuses; real existing session/remembered foundation migrations, current V1/V2 modules, BCrypt, Hikari, production HTTP controllers/JWT/DAOs. Migration/rollback, auth, session and raw read isolation scenarios below. Does not establish authoritative Case schema or installed/deployed callers. |
| Injected failures | Test-only THROW after first actual ADD / first DROP proves atomic apply/rollback; malformed receipts/owner/policy/conflict injections prove rejection. A JDBC wrapper fails the second context setup; lease closes and the next borrow overwrites both keys. These are controlled injections, not observed production failures. |
| Static inspection / doubles | Case query/audit/overlay and existing JDBC/controller regressions, importer/launcher safety, documentation review. These do not establish SQL execution of Case joins/paging/required audits. |
| Unexecuted authoritative Case path | **BLOCKED:** full A/B/NULL Case reads, actor joins, overlay precedence, paging/hasMore, successful audit commit, real denied audit INSERT and injected pre-commit/lost-ack Case scenarios have no new authoritative SQL execution result. Opt-in test skip is not a pass. |

The auth fixture proves password NULL-context bootstrap for A/B, unavailable/deleted/removed/NULL-tenant/
orphan/ambiguous/wrong-password rejection, caller restoration after successful and deliberately failing
owner-module work, separate non-bypass runtime, raw A/B/NULL Users/audit visibility and Statuses global/current
visibility, same-SPID A1/A2/B1/A1 reuse and setup-failure recovery. Production login/`/me`/refresh/logout and
remembered enrollment/restore run through MockMvc and real SQL with session/JTI and remembered-secret rotation,
old-secret/old-JTI replay rejection and durable revocation. MockMvc is in-process HTTP execution, not a serving
network API, installed desktop, browser or production acceptance. Auth pool context remains NULL; ordinary
runtime cannot execute the bootstrap modules, impersonate dbo, alter them/policy or read password hashes.

The authoritative Case audit distinction is preserved: `audit_insert_denied` must exercise an actual SQL
permission DENY with zero durable audit delta and no returned Case; `commit_lost_ack` invokes real commit
then injects SQLState 08007, withholds the DTO and measures a committed audit row. A rollback attempt does
not undo a committed row. Neither Case scenario ran here; passing JDBC doubles are synthetic only. No audit
rows are deleted to hide uncertainty and no automatic replay is introduced.

### Executed checks

Local toolchain: Python 3.12.14, Maven 3.9.11, Temurin JDK 21.0.12.1; actual SQL Server **16.0.4305.5** (2022), image pinned to
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
The existing `/workspace/.tools/bin/mvn` wrapper supplies this environment's JDK/proxy/CA settings;
no POM, release version or application configuration changes. Initial offline dependency misses were
resolved through the configured proxy. Development-time candidate failures (catalog parentheses and eager
DDL binding) are corrected failures, not passing acceptance evidence.

| Executed check | Result |
| --- | --- |
| New launcher protocol Python tests | 3 PASS. Missing scenarios/nonzero exit/cleanup failure cannot pass; arbitrary child output is discarded. |
| Existing auth Python tests | 35 PASS, 8 explicit unavailable native Windows/PowerShell/ODBC skips (43 total). No operator packet or retirement/recovery run. |
| Existing Case launcher / selector Python tests | 4 PASS / 25 PASS, including new Phase 1R security routing. |
| Initial focused Java | 54 PASS, 1 inherited failure, 1 Case SQL skip (56 total); server unexecuted after data-module failure. |
| Unchanged-base failure reproduction | `UserSessionMigrationContractTest`: 2 PASS, same 1 failure at base `73c23475`. Its blanket forbidden `nvarchar(max)` assertion rejects the migration's existing SQL variable. Neither test nor migration changed. |
| Remaining focused Java | 86 PASS, 2 explicit SQL opt-in skips (88 total). Current auth/module/session, Case JDBC/paging/audit doubles, launcher safety and HTTP/context contracts. |
| Selector affected suite | 54 PASS, 2 SQL opt-in skips (56 total). Security-data contracts plus modified Case/Phase 1R tests; helper compiles but has no JUnit methods. |
| Critical reactor | `mvn test`: **116 PASS**, zero failures/errors/skips, BUILD SUCCESS. |
| New real SQL fixture and real public quiet launcher | All six required PASS lines, cleanup successful, exit 0. Synthetic real SQL plus labelled injected migration/setup failures; no authoritative Case result. |
| SQLCMD entry points | Real `sqlcmd -b -V 16`: preflight, apply, repeat apply, postcheck, rollback, repeat rollback PASS; original full predicate hash restored and exact nonce cleanup PASS in a separate new synthetic database. |
| Scope / docs / whitespace | Full diff reviewed; four Markdown files, 77 relative links/anchors and fences checked; `git diff --check` passes. |

Exact commands from this worktree (`python` is Python 3.12.14):

```bash
python -m unittest discover -s build/phase1r-verification -p 'test_*.py'
python -m unittest discover -s build/auth-verification -p 'test_*.py'
python -m unittest discover -s build/case-read-verification -p 'test_*.py'
python -m unittest discover -s build/test-selection -p 'test_select_tests.py'
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=Phase1RLocalSqlServerTest,CaseReadLocalSqlServerTest,CaseReadVerificationSafetyTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserSessionMigrationContractTest,UserSessionDaoContractTest,RequestLookupRlsPhase2MigrationTest,MinimizedCaseReadJdbcTest,CaseSearchPagingJdbcTest,CaseServiceAdapterTest,AuthControllerTest,DesktopSessionControllerTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
# On the unchanged detached base worktree, to attribute the inherited failure:
/workspace/.tools/bin/mvn -o -pl shale-data -am -Dtest=UserSessionMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
# Remaining focused tests, allowing the server module to execute independently:
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=Phase1RLocalSqlServerTest,CaseReadLocalSqlServerTest,CaseReadVerificationSafetyTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserSessionDaoContractTest,RequestLookupRlsPhase2MigrationTest,MinimizedCaseReadJdbcTest,CaseSearchPagingJdbcTest,CaseServiceAdapterTest,AuthControllerTest,DesktopSessionControllerTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
PATH=/workspace/.tools/bin:$PATH python build/test-selection/select_tests.py --base 73c23475a4001dfcd36ec8a06c91d85cd1bad6c3 --head HEAD --format markdown --plan-output work/selection.json --run
python work/run_sql.py
python work/run_launcher.py
python work/test_sqlcmd.py
python work/check_docs.py
git diff --check
git diff --check 73c23475a4001dfcd36ec8a06c91d85cd1bad6c3 HEAD
```

The selector `--run` executed its emitted security-data Maven command and then critical `mvn test`;
its exact argv are retained in the local plan. Ownership routing for new SQL/tool paths was added with
one selector regression; that metadata change recommends the historical full suite informationally only.
No blanket all-tests or visual suite ran. Existing full-suite advisories (12 data failures, three
ApiExceptionHandlerTest failures, legacy application-release OpenAPI binding) retain their prior attribution;
they were not rerun or repaired. The separately reproduced session-migration assertion above is an additional
explicit inherited failure, not a Phase 1R regression or an all-tests pass.

Private `work/` helpers are local orchestration, not committed artifacts: `run_sql.py` supplies only new
synthetic admin credentials to the exact focused opt-in JUnit command; `run_launcher.py` supplies the two
private prompts to the real public launcher without replacing its validation/Maven/report paths. Both
capture/discard unrestricted output. `test_sqlcmd.py` copies only the candidate SQL into the task-owned
container and exercises the actual SQLCMD includes with independently labelled synthetic definitions;
it records only fixed outcomes and the SQL version. An initial copied-file readability failure preceded
SQL execution and was corrected only in that disposable container. Public reproduction uses
`python3 build/phase1r-verification/run.py --opt-in LOCAL_DISPOSABLE_PHASE1R --port 14333` with an installed
Maven (or `--maven /absolute/path/to/mvn`). Each run creates fresh nonce/SID-guarded resources. No prior
operator resource or private capture was adopted. The original Case importer remains unchanged.

### Remaining activation and rollback gates

SQL-before-client compatibility is conditional. Existing additive V1/V2 modules with Stage A fences must
precede dependent API **and direct-JDBC desktop** callers. Accept the actual compatible deployed API/JAR,
installed V1/V2 callers/jobs and intended auth/runtime identity mappings, then drain incompatible old callers
before restrictive Stage B or Users FILTER activation. Existing Stage A/B scripts reject newly present
three-table predicates: complete any missing separately approved stages before RLS; do not remove their guards.
Sanitized existing deployment/catalog evidence must establish module definitions/owners, effective fences,
non-bypass runtime, compatible versions, old-caller drain/coordination and operational monitoring/sign-off.
These facts are unknown here. Do not infer them from retirement, release 1.0.137 metadata or local passes.
No privileged runtime, direct SQL bypass, auth fallback or new permission workaround is permitted.

Before any later activation, close authoritative derived/candidate acceptance and review identifier collation
against Case consumers' `AuditLog` spelling, deployed policy/grants and representative plans. Source/column
collation does not establish database identifier collation. The fixture's `SQL_Latin1_General_CP1_CI_AS` is
synthetic evidence only. Preserve actual source ownership/computed/constraint dependencies; do not transcode
or simplify a missing source into apparent authoritative coverage.

Prepare rollback with the original review inputs and untouched receipt, then invoke
[`rollback.sql`](../sql/phase1r/rollback.sql) as documented. It removes **only recorded candidate additions**;
no receipt means no removal; drift blocks action for review. Rollback of FILTERs does not remove auth modules
needed by current callers or restore compatibility with retired direct-SELECT paths. It does not reverse
unrelated permissions, application deployments or already committed audit history.

Audit-grant/append-only/admin-only and existing viewer logging gaps remain OPEN. FILTERs do not enforce
write BLOCKs, protect password columns or make global Statuses immutable. This task adds no application
audit schema/events: Overview retains its existing event/transaction and summaries retain the approved
exemption. Future AI integration/tool/grant/outcome attribution remains an independent follow-on activation
gate. REST and future MCP must share secured application/service boundaries; no AI database access.
Stage A/B completion, complete least privilege/RLS, monitoring readiness, production activation and MCP
readiness are **not claimed**. No production/Azure SQL, deployment, installer/version publication, MCP
activation or broad RLS/permission redesign occurred.


## Current authentication/security reconciliation — 2026-10-21

Historical reconciliation record, before the Phase 1R local work above; its production evidence scopes remain intact.

The [canonical API/MCP roadmap §2.5](../architecture/ai-integration-and-mcp.md#25-authentication-permissions-and-delivery-reconciliation--2026-10-21)
records the October 20 **operator-reported production retirement** of EXECUTE on the three legacy login
procedures for `shale_app`, runtime principal/role denials, and successful API login/identity/refresh/logout
plus installed-desktop automatic sign-in/user-detail checks. Do not repeat completed operator rehearsals
or treat the older retirement-BLOCKED statements below as current status for that exact delta.

The separate V1/V2 auth-module Stage A installation/fences, Stage B table/hash read restrictions,
subsequent desktop/API/SQL deployment details and monitoring closeout are not established by that report.
Current source is merged through release 1.0.137; merge/release metadata does not establish deployed parity.
Users/auditLog/Statuses read coverage, runtime hash/audit capabilities, write/admin restrictions and
Case SQL acceptance remain open as qualified in that ledger. Supplied offline schema confirmed the gap;
older missing-input statements are historical. No complete least-privilege, MCP or native-mobile acceptance.

Follow the [SQL/API/client release order](../azure-app-service-deployment.md#release-dependencies-and-order--2026-10-21).
The one next implementation phase is the roadmap's [Phase 1R prompt](../architecture/ai-integration-and-mcp.md#91-ready-to-run-codex-prompt--phase-1r),
a local three-FILTER candidate, without production changes. Earlier dated tests, local Windows/source
recovery limits and all accepted R4/session observations retain their scopes. This update executes no SQL.


October 12, 2026. **IMPLEMENTED, SQL EXECUTION UNVERIFIED.** This is runnable opt-in tooling, not
Azure acceptance, a new product endpoint, or a mock schema. The
[R2 contract](../architecture/shale-web-v2-first-read-contract.md#11-r2-minimized-tenant-wide-case-reads--2026-10-10)
owns policy/fields/audit; [R4](../../shale-web/docs/r4-desktop-acceptance.md) owns acceptance attribution.

## Entry points and evidence boundary

- [Runner](../../build/case-read-verification/run.py): explicit opt-in, reviewed DDL hash/provenance,
  local TCP port and privately entered dedicated local administrator credentials. No JDBC URL input.
- [SQL harness](../../shale-data/src/test/java/com/shale/data/verification/CaseReadLocalSqlServerTest.java):
  real Microsoft JDBC, production `CaseServicePort -> CaseServiceAdapter -> DaoCaseGateway ->
  CaseSummaryDao -> AuditLogDao.appendPhiWriteAudit(Connection,...)`. It constructs the public adapter
  with `CaseDao` solely for its existing provider; it never invokes broad detail or a mock gateway.
  Real HikariCP pool (one physical connection) and production `RuntimeSessionService` initialize both
  session keys on every borrow. No HTTP server or bearer resolver is started. Request-scoped principal
  resolution, browser failure display, HTTP bytes/no-store and durable WEB sessions remain independent gates.
- [Java safety/fault tests](../../shale-data/src/test/java/com/shale/data/verification/CaseReadVerificationSafetyTest.java)
  and [runner tests](../../build/case-read-verification/test_runner.py) execute without SQL. These are
  synthetic boundary tests, not real RLS/audit-fault evidence.

The test emits only fixed PASS/FAIL scenario names. The runner captures/discards all other Maven/driver
output and uses its own quiet Logback configuration. It does not write raw diagnostics, queries, payloads,
connection strings, tokens or passwords. Failures in Surefire have a fixed phase and exception class,
with no original cause/message. Never run with Maven debug or driver/payload logging to diagnose it.

## Required authoritative schema input (not supplied by this repository)

**Current blocker assessment (2026-10-14):** an authoritative offline export has now been supplied.
The [remediation assessment below](#rls-blocker-assessment--2026-10-14) supersedes the missing-input
explanation for the current task. The original execution record remains historical. SQL integration
is still **NOT RUN**; the supplied definitions confirm missing coverage and are not a passing baseline.

The repository contains incremental migrations and schema notes, **not complete CREATE definitions for
Cases, Users, CaseStatuses, AuditLog and the established strict tenant predicate**. The proposed June RLS
coverage script inspects/reuses that predicate; it cannot replace its definition or prove deployed state.
This harness consequently requires a reviewed **schema-only UTF-8 SQL definition snapshot** from an
already approved offline schema artifact. Do not retrieve production data/credentials or connect this
tool to Azure to obtain it. A backup/BACPAC containing rows is forbidden. Do not invent or relax schema,
constraints, grants or RLS to satisfy the harness. Missing definitions block SQL verification honestly.

Prepare `work/case-read-schema.sql` from that offline artifact using existing SSMS/SQL tooling:
script schema only, in dependency order, with keys, constraints, indexes, defaults, collations, computed
columns, RLS functions and enabled predicates. Include selected objects **and every transitive FK/type/
function dependency**. Exclude data, login/user/grant definitions and unrelated objects. Review the file
and record its source label/date/object inventory and SHA-256 locally; never commit the artifact or secrets.
An offline schema label/hash establishes what was tested; it does not establish deployed parity by itself.

The restricted importer accepts one DDL statement per `GO` batch: `CREATE SCHEMA`, `CREATE TABLE`,
schema-bound inline table-valued `CREATE FUNCTION`, `CREATE [UNIQUE] [NONCLUSTERED|CLUSTERED] INDEX`,
`CREATE SECURITY POLICY ... STATE=ON`, `ALTER TABLE` constraint definitions/checking, and individual
`SET ANSI_NULLS ON` / `SET QUOTED_IDENTIFIER ON`. Remove SSMS `USE`, CREATE DATABASE, sqlcmd directives,
GO repetition, data/seed DML, dynamic EXEC and multi-statement batches; separate statements with GO.
Preserve actual object definitions. Unsupported definitions fail closed, requiring a bounded importer
follow-up, not editing the authoritative definition. DDL executes as a newly created SQL login with
db_owner **only in this fresh database**; that membership is removed before runtime grants/tests.
The importer is deliberately not a general-purpose migration runner. No production migration is applied.

| Selected dependency | Why needed / exact authority reviewed |
| --- | --- |
| `ShaleClients`, `Users` including `IsRemoved`, removal metadata and active/deleted fields | Tenant/actor fixtures and production eligibility. Schema reference; `2026-08-03_users_management_completion.sql`, concurrency successor. Fixture columns are checked against actual imported metadata before INSERT. |
| `Cases`, `CaseStatuses`, `Statuses`, `PracticeAreas` | Production minimized projection and current-status ordering; full existing shapes/FKs/defaults/collations. `CaseSummaryDao.statusApplySql`, schema reference; April Status lifecycle/global/SystemKey and PracticeAreas overlay migrations; August Status lifecycle successor. Lifecycle values are lowercase `closed` or null, not a fabricated `INTAKE` key. |
| `CaseUsers`, `Roles` | Assignment filtering and semantic compatibility IDs from `RoleSemantics`: responsible attorney 4, legal assistant 11. September 3 Case Team phase 2 owns tenant/default/RowVer and trusted Case/User composite FKs. Role lookup definitions are transitive FK dependencies, not a new role policy. |
| `CaseDates`, `CaseDateTypes` | Assigned SQL ordering only. August 4 foundation; August 18 Title successor; later effective schema, including existing constraints/indexes/RLS. No dates are returned to the client. |
| `CaseDateSemanticRoles`, `CaseDateTypeSemanticRoleMappings` | Intake ordering and mapping FK. August 10 role foundation/admin successor; September 26 retirement preserves only active protected Intake. Synthetic global and tenant Intake mappings use existing representation. |
| `AuditLog` and its FK dependencies (including `ObjectTypes` if present) | Existing append/event: tenant, actor, Case type 1, ObjectId, `Case.Overview.Read`, FieldCode 4, exact `action=READ;screen=Case.Overview`, null date/boolean/int. April 15 tenant-column migration is necessary but does not create/verify RLS. Full field types/constraints/defaults must come from the snapshot. Optional ObjectTypes fixture requires metadata-confirmed Id/Name columns; different authoritative lookup shapes require fixture adaptation, never schema relaxation. |
| `sec.fn_FilterByTenant`, `sec.fn_FilterByTenantOrGlobal`, enabled `TenantFilter` and all selected predicate dependencies | Import exact authoritative definitions. Catalog checks require strict FILTER on Cases/Users/CaseUsers/CaseDates/AuditLog and overlay FILTER on Statuses/PracticeAreas/CaseDateTypes/mappings. Non-dbo reads then test actual enforcement, null context and global visibility; names/catalog alone cannot pass the gate. No predicate is synthesized or added by the harness. CaseStatuses identity/FKs and production Case-root/current-status join are preserved; no guessed CaseStatuses tenant column. |

The schema is final-state definitions reflecting these dependencies, **not blindly replayed historical
migrations**. Unrelated administrative audits/role definitions need inclusion only if retained FKs require
them. Metadata verifies fixture column existence; unprovided required columns/defaults or incompatible
constraints fail setup. Review/adapt synthetic fixture values to the authoritative contract in a follow-up;
do not add fake defaults, remove FKs or mark constraints untrusted. All imported non-marker tables must
contain zero rows (checked through partition metadata, so RLS cannot conceal imported data).

## Local setup and commands

Prerequisites: Python 3, JDK 21, installed Maven, unchanged app dependencies and a dedicated **local** SQL
Server 2022 Developer/Express instance with TCP SQL authentication. Existing Microsoft JDBC 12.6.1.jre11,
HikariCP and JUnit dependencies are reused; none is added to app POMs. A dedicated local administrator
must be able to CREATE DATABASE/LOGIN. Never use production credentials, even for a loopback server.

Use a fresh terminal with no SHALE_APP/RT/AUTH/SERVER/SUGGESTION, SPRING_DATASOURCE or Azure connection
variables. The runner and Java entry both reject them, including equivalent Java properties. Implicit
Java/Maven argument environment variables are rejected. The only connection is constructed as
`127.0.0.1:<port>`; database names are generated, not operator-selected. It refuses Azure engine editions,
existing database/login collisions, wrong DB_NAME and missing/mismatched ownership markers. Trusting a
self-signed SQL certificate is confined to this hard-coded local transport. No named instances, aliases,
remote Docker context, Azure addresses, linked servers or arbitrary URL overrides are supported.

For Windows local SQL Server, enable TCP on a dedicated instance and set its static port to **14333**
through SQL Server Configuration Manager, restart that local instance, and restrict listening/firewall
access to local use. Use its dedicated local SQL admin credentials at the runner's prompts. Do not set
application connection variables or run the API. Docker Desktop alternative (PowerShell; local daemon only):

```powershell
Remove-Item Env:DOCKER_HOST,Env:DOCKER_CONTEXT,Env:DOCKER_TLS,Env:DOCKER_TLS_VERIFY,Env:DOCKER_CERT_PATH -ErrorAction SilentlyContinue
$sqlSecret = Read-Host 'NEW disposable SQL admin password' -AsSecureString
$env:MSSQL_SA_PASSWORD = [System.Net.NetworkCredential]::new('', $sqlSecret).Password
docker --host npipe:////./pipe/docker_engine run --detach --name shale-case-read-sql --label shale.case-read-verifier=local-disposable --publish 127.0.0.1:14333:1433 --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD mcr.microsoft.com/mssql/server:2022-latest
if ($LASTEXITCODE -ne 0) { throw 'Disposable container setup failed' }
Remove-Item Env:MSSQL_SA_PASSWORD
```

Linux Docker alternative (dedicated name must not already exist; no mounts or persistent volume):

```bash
read -r -s -p 'NEW disposable SQL admin password: ' MSSQL_SA_PASSWORD
export MSSQL_SA_PASSWORD
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH \
  docker --host=unix:///var/run/docker.sock run --detach --name shale-case-read-sql \
  --label shale.case-read-verifier=local-disposable --publish 127.0.0.1:14333:1433 \
  --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_SA_PASSWORD \
  mcr.microsoft.com/mssql/server:2022-latest
unset MSSQL_SA_PASSWORD
```

Wait for local SQL readiness without dumping container logs/environment. The admin password is entered
again privately at the runner prompt (`sa` for this container). The floating image tag is an operator
setup convenience, not reproducible version evidence: record the pulled image ID/digest and SQL/JDK/
JDBC/Maven versions in the local evidence record. Pin a reviewed image digest for repeat verification.
Managed-cloud Docker must follow its existing proxy/CA configuration; never use host networking or
bypass its network policy to pull an image.

From repository root after reviewing the offline DDL file:

```powershell
$schemaHash = (Get-FileHash work/case-read-schema.sql -Algorithm SHA256).Hash.ToLowerInvariant()
python build/case-read-verification/run.py --opt-in LOCAL_DISPOSABLE_CASE_READS --port 14333 --schema work/case-read-schema.sql --schema-sha256 $schemaHash --schema-source 'Reviewed offline schema snapshot: record its date and identity locally'
if ($LASTEXITCODE -ne 0) { throw 'SQL verification remains unverified; inspect fixed scenario and prerequisites' }
```

```bash
schema_hash=$(sha256sum work/case-read-schema.sql | cut -d ' ' -f 1)
python3 build/case-read-verification/run.py --opt-in LOCAL_DISPOSABLE_CASE_READS --port 14333 \
  --schema work/case-read-schema.sql --schema-sha256 "$schema_hash" \
  --schema-source 'Reviewed offline schema snapshot: record its date and identity locally'
```

Hash computation alone is not schema review. `--maven /absolute/path/to/mvn` selects an installed wrapper
when Maven is not on PATH. No credential is accepted on the command line. For automation only, dedicated
`SHALE_CASE_VERIFY_ADMIN_USER` / `SHALE_CASE_VERIFY_ADMIN_PASSWORD` may come from a private local environment;
clear them afterward. The Python launcher supplies the other dedicated inputs to the opt-in JUnit test.

## Scenarios, real SQL versus injected transport

| Fixed scenario | Required outcome |
| --- | --- |
| setup / fixtures | Fresh GUID-named database; immediate nonce/source-hash marker; schema-only import; fresh login; scoped SELECT on nine read tables, INSERT AuditLog, test-only SELECT AuditLog; no db_owner/sysadmin/Case UPDATE/audit DELETE at runtime. Synthetic A/B tenants, active second actor, removed and inactive actors, 35 nonmatching prefix Cases, 27 matching Cases per tenant, global/tenant definitions and Intake mappings/dates, assignments, unassigned/closed/deleted Cases and literal Unicode/wildcard name. |
| pooled_context | Same `@@SPID` for A1 → A2 → B1 → A1, exact tenant/actor keys, real production initializer and non-dbo privileges; cleared runtime refuses borrow. |
| rls_enforcement | Raw unqualified strict-table reads reveal no foreign/global rows, null context returns no strict rows; overlays show global/current rows and exclude foreign rows for both tenants. Audit visibility is tested after persisted rows too. |
| search_assigned_overview | Actual SQL filters before offset/probe; exact 25/2 matching pages, truthful hasMore and empty third page despite foreign/deleted matches; assigned membership excludes unassigned/deleted and includes closed Cases; direct Overview hides foreign/deleted/missing while same-tenant unassigned is readable. Semantic relationships, global/tenant definitions and literal Unicode/metacharacters verified. |
| foreign_join_protection | Foreign PracticeArea identity is either rejected by authoritative FK or withheld by production join; original fixture restored. Required composite CaseUsers User/Tenant FK rejects foreign membership, transaction rolled back. No protection is disabled to create corruption. |
| actor_authority | Removed/inactive/foreign actors and null/mismatched tenant/actor session keys deny search (including blank), Assigned and Overview; no success audit. |
| overview_audit | Summary exemption adds no rows; each successful/reread Overview commits one exact server-owned context/representation; B sees only B audit history. Existing viewer READ classification remains covered by repository tests, not a new viewer UI claim. |
| audit_insert_denied | Real SQL DENY INSERT on **only the generated harness user**, required production append fails and no result returns; zero durable delta. Restore exact harness INSERT grant; one deliberate retry succeeds. No production permission or audit is touched. |
| failure_before_commit | Test-only Connection wrapper allows real SELECT/INSERT, throws instead of invoking commit; production returns AUDIT_UNAVAILABLE and attempts real rollback; separate admin connection measures zero durable delta. |
| commit_lost_ack | Wrapper first invokes **real JDBC commit**, then throws synthetic SQLState 08007 to model lost acknowledgement. Production withholds DTO and attempts rollback; separate connection measures one durable audit row retained. Real SQL executed; acknowledgement loss is injected, not an observed SQL Server network/driver failure. No automatic replay/delete. |
| cleanup | Close pool, revalidate exact DB/nonce/hash marker, drop only this run's generated database and generated login. No audit-history DELETE, general database cleanup, or name-prefix scan. |

Cleanup is automatic even after a failed scenario. If marker/identity verification fails, cleanup refuses;
it does not guess ownership. Keep the dedicated local instance isolated. On a dedicated disposable Docker
instance the operator can remove **the exact container they created**, after checking its label; do not
prune containers/volumes or target an existing similarly named resource:

```bash
docker --host=unix:///var/run/docker.sock inspect --format '{{index .Config.Labels "shale.case-read-verifier"}}' shale-case-read-sql
# Only when output is local-disposable and this is the container created above:
docker --host=unix:///var/run/docker.sock rm --force shale-case-read-sql
```

Windows uses the same inspect/rm commands with `--host npipe:////./pipe/docker_engine`. No volumes were
created by these commands. Never remove a shared/native SQL instance for cleanup. A crash before normal
cleanup on native SQL requires separate operator review of the run's database marker and principal;
there is intentionally no broad destructive orphan collector.

## Test impact, execution record and follow-up

Live base `70599324cf843b2dd4f51dfb44e6094502354e25` is #1863's merge. Git fetch failed at the managed
proxy; supported connector supplied source blobs/tree and signed merge payload. Imported blobs, full
tracked tree `4ee0a8f61d264c2dac55e0929b3788759918c94b` and exact signed commit hash were verified.
Later authorized `git fetch origin codex/latest` succeeded and independently confirmed the same live
`FETCH_HEAD`/remote-tracking base. Original worktree preserved; historical missing PNG/JPG evidence
entries remain sparse and unchanged.
No production symbols/contracts changed. Pre-edit inspection covered MinimizedCaseReadJdbcTest,
CaseSearchPagingJdbcTest, CaseServiceAdapterTest, CaseSummaryServerProjectionContractTest, minimized HTTP/
OpenAPI tests, RequestScopedDbSessionProviderTest, RuntimeSessionService, AuditLogDao/viewer and existing
SuggestionDaoSqlServerTest/FieldConfirmation integration-labelled doubles. The latter's name is not proof
it executes SQL. New safety/SQL tests belong to cases/security-data; the separate opt-in SQL test is never
added to critical defaults. Existing Maven dependencies are sufficient.

Executed on Maven **3.9.11**, Eclipse Adoptium JDK **21.0.12.1** (Linux); these are Codex tooling versions,
not invented versions for the user's Windows or hosted results:

| Executed check | Result |
| --- | --- |
| `python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'` | 4 PASS; configuration/hash/output boundary, no SQL. |
| Focused Maven command below | 34 PASS, 1 explicitly skipped local SQL test; compiled full harness. 29 existing production-adapter/JDBC tests plus 5 new safety/fault-wrapper tests are synthetic. |
| Selector affected command below | 61 PASS, 1 skipped local SQL test; cases/security-data/server checks. Helper source is compiled but is not itself a JUnit test. |
| `mvn test`, followed by `mvn clean test` | Initial task checkout failed desktop compilation (core packages unavailable); unchanged fetched-base `mvn test` PASS and clean task reactor PASS **116 tests**. No source defect was established; no unrelated production/build repair. |
| Informational `mvn -Pall-tests test` | FAILED: core 111 PASS; data 819 tests / **12 inherited failures** / 1 local-SQL skip / 0 errors. UI/updater/desktop/server unexecuted in this advisory run. Same twelve documented failing methods; no full-suite pass. |
| `python3 -m unittest build/test-selection/test_select_tests.py` | 24 PASS. Ownership paths added for this harness/security tooling; critical defaults unchanged. |
| Changed-file selector; Markdown relative links/anchors/fences/statuses/whitespace; `git diff --check` | PASS; five edited Markdown files / 226 relative links. Selector recommends informational full-suite because its ownership map changed; required focused/affected/critical results remain separate. Production/build/workflow blob review confirms 666 unchanged files. |

Commands use the installed `/workspace/.tools/bin/mvn` wrapper in this environment (no app toolchain/POM
changes or compiler-parameters flag). Operator equivalents from repository root:

```bash
mvn -pl shale-data -am \
  -Dtest=CaseReadVerificationSafetyTest,CaseReadLocalSqlServerTest,MinimizedCaseReadJdbcTest,CaseSearchPagingJdbcTest,CaseServiceAdapterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 70599324cf843b2dd4f51dfb44e6094502354e25 --head HEAD --format markdown
mvn -pl shale-data,shale-server -am \
  -Dtest=com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.data.verification.CaseReadLocalSqlServerTest,com.shale.data.verification.CaseReadVerificationSafety,com.shale.data.verification.CaseReadVerificationSafetyTest,com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn test
# A clean rebuild resolved the task checkout's transient critical compile failure:
mvn clean test
# Informational only; does not replace relevant checks:
mvn -Pall-tests test
```

SQL execution is **NOT RUN**: no approved
offline baseline DDL exists in this workspace. Docker is available through authorized local execution,
but starting an empty SQL container cannot establish production-schema verification. No substitute
schema was created and no local/remote SQL request, production fixture/grant or Azure fault was executed.
The JUnit SQL test's explicit skip is not a passing integration result.

Retain inherited advisories: R1/R2 all-tests data's 12 failures (downstream unexecuted), three
ApiExceptionHandlerTest failures, and the ordinary-build legacy application-release OpenAPI parameter
failure. This task does not repair or relabel them. Source inspection also found the existing
`AuditLogDao.listAuditLogEntries` writes SQL/bound values to stderr; this harness deliberately avoids
that viewer DAO method. Logging privacy needs a separately scoped follow-up; no production behavior
was changed to hide it or make verification pass.

Historical next bounded step at #1865: obtain/review an approved offline schema-only snapshot, run the exact launcher on
dedicated local SQL, record fixed scenario outcomes and version/schema/source identities, and isolate
any actual schema/RLS/DAO defects for follow-up. A passing run establishes local integration only.
Deployed Azure predicate/grant parity, representative plans/scanned-work/collation/concurrent paging,
successful audit viewer meaning, live browser expiry/revocation/uncertainty, host/CORS/logging/privacy/
error no-store and broader device/AT acceptance remain separate gates. Phase 2 OPEN; Phase 3 IN PROGRESS;
Phase 4 INCOMPLETE; appearance provisional. No merge/deployment authorized by this tooling.

## RLS blocker assessment — 2026-10-14

**ASSESSMENT REVIEWED AGAINST SOURCE; IMPLEMENTATION AND SQL VERIFICATION OPEN.** Task base:
`194f514e8ae0a211a4d778696b0996be7b1c67d2` (fetched `codex/latest`).
This assessment changes documentation only. No SQL connection, importer execution, fixture, fault,
migration, grant, configuration, production code, merge or deployment was performed. Attached-document
comments are evidence, not instructions to execute their SQL. Security acceptance stays open; the
[R4 ledger](../../shale-web/docs/r4-desktop-acceptance.md) retains all accepted user reports.

### Evidence, schema and contract decision

The user supplied authoritative schema-only SSMS export `shale-schema-source.sql`, 728,862 bytes,
UTF-16 LE with BOM/CRLF; embedded SSMS script date October 13, 2026 (timezone unspecified).
Original-byte SHA-256: `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`.
The export stays outside Git. A local decoded scratch copy was used only for inspection; neither
copy is a deliverable. This hash identifies evidence, not a deployed database/build or live grants.
The user-run Shale catalog result reports **no security predicates** for `dbo.Users`, `dbo.auditLog`
or `dbo.Statuses`. The supplied enabled, schema-bound `sec.TenantFilter` definition agrees: it attaches
neither FILTER nor BLOCK predicates to those tables. Predicate function existence does not protect
a table. This is a **confirmed RLS coverage gap**, not evidence of an API data leak.

| Object | Actual exported contract | Assessment |
| --- | --- | --- |
| `dbo.Users` | `id int` identity; `ShaleClientId int NULL`; persisted `email_norm=lower(email)`; nullable `is_deleted`; `IsRemoved`, removal metadata and RowVer present. Email/password hash are nvarchar(255), names nvarchar(50). Exported email_norm index is nonunique. | Runtime Users are strict tenant data. NULL ownership is not a global user permission. Legacy/null rows and ambiguous email matches need classification before changing authentication. Documentation's wider sizes and tenant-email migration are not proof of this snapshot's shape/indexes. |
| `dbo.auditLog` | Actual lower-case object spelling; `ShaleClientId int NOT NULL`, trusted tenant FK; nullable user/object IDs; `fieldCode int`; `stringValue varchar(max)` and `entryDate varchar(50)`. | Strict tenant history; no null-tenant audit rows in this shape. April 15 migration added a nullable column/index and deferred backfill; final export has evolved beyond it. No schema/data conversion is needed merely to attach a FILTER. Case-sensitive database name resolution must be checked: source/harness use `dbo.AuditLog`. Column collations alone do not establish database identifier collation. |
| `dbo.Statuses` | `ShaleClientId int NULL`; name nvarchar(50), color nvarchar(10), LifecycleKey nvarchar(32), SystemKey nvarchar(64); IsActive/IsDeleted. Lifecycle permits accepted/denied/closed or NULL; trusted tenant FK accepts globals. | Tenant-or-global lookup. Preserve NULL global rows, stored IDs and CaseStatuses history; no invented tenant column on CaseStatuses. |
| `sec.fn_FilterByTenant` | Permits `USER_NAME()=dbo`, `IS_MEMBER(ShaleAdmins)=1`, or row tenant equals TRY_CONVERT of session tenant. | Strict for a non-bypass runtime identity; NULL context/row does not match. Administrative bypass is function logic, not a universal SQL Server dbo exemption. |
| `sec.fn_FilterByTenantOrGlobal` | Same dbo/ShaleAdmins exceptions plus row tenant NULL or matching session tenant. | NULL context still sees global rows if SELECT is granted; global visibility is not anonymous API authorization. |
| `rls.fn_TenantMatch_Users` | Permits `USER_NAME()=shale_app` or matching tenant; no dbo/ShaleAdmins exception in this function. | Unattached historical auth exception, not current Users protection. Also unattached: `rls.fn_TenantMatch` and `rls.TenantFilter`. Do not infer their intended deployment from comments. |

PR [#1865](https://github.com/gseshadow/Shale/pull/1865) is merged. Its
`CaseReadLocalSqlServerTest.setup` requires exactly one enabled TenantFilter FILTER with
`sec.fn_FilterByTenant(ShaleClientId)` on Users/audit and
`sec.fn_FilterByTenantOrGlobal(ShaleClientId)` on Statuses. Its non-dbo scenarios then inspect actual
foreign-row exclusion, NULL context and global visibility. Those **runtime read semantics match**
the intended tenant contract and R2's accepted tenant-wide minimal reads; assignment remains a list
filter, not a general ACL. The exact function-name check chooses the established sec policy architecture;
it does not establish that all login paths are compatible, that write blocking exists, or that grants
match production. Keep the harness unchanged. Attaching the auth-exception function instead would fail
its existing check and would introduce a different contract requiring separate review.

The June [coverage proposal](../sql/proposed/2026-06-29_tenant_rls_coverage_proposed.sql) explicitly
**defers Users and auditLog**, and includes Statuses only in a broad proposed overlay migration.
It is not evidence of applied coverage and must not be run as the narrow repair. April Status global/
SystemKey migrations and August soft-delete/audit migration preserve overlays/history; the Users
management/concurrency migrations do not repair these three missing FILTERs.

Primary source anchors for review: [AuthServiceImpl](../../shale-data/src/main/java/com/shale/data/auth/AuthServiceImpl.java),
[DataSources](../../shale-data/src/main/java/com/shale/data/config/DataSources.java),
[RuntimeSessionService](../../shale-data/src/main/java/com/shale/data/runtime/RuntimeSessionService.java),
[remembered store](../../shale-server/src/main/java/com/shale/server/runtime/SqlRememberCredentialStore.java),
[remembered resolver migration](../sql/2026-10-06_desktop_remember_credentials_permissions.sql),
[UserSessionDao](../../shale-data/src/main/java/com/shale/data/dao/UserSessionDao.java),
[CaseSummaryDao](../../shale-data/src/main/java/com/shale/data/dao/CaseSummaryDao.java),
[UserDao](../../shale-data/src/main/java/com/shale/data/dao/UserDao.java),
[CaseDao](../../shale-data/src/main/java/com/shale/data/dao/CaseDao.java),
[AuditLogDao](../../shale-data/src/main/java/com/shale/data/dao/AuditLogDao.java),
[audit viewer](../../shale-ui/src/main/java/com/shale/ui/controller/AuditLogViewerController.java),
[harness](../../shale-data/src/test/java/com/shale/data/verification/CaseReadLocalSqlServerTest.java) and
[importer safety](../../shale-data/src/test/java/com/shale/data/verification/CaseReadVerificationSafety.java).

### Current protection and effective access paths

| Path / source owner | Existing application protection | Effect of strict/overlay RLS and remaining boundary |
| --- | --- | --- |
| `DataSources`, `Config`, server service configuration, `DesktopConfig` | Separate configurable auth and runtime Hikari pools. Actual SQL usernames come from configuration; comments/examples naming shale_app/shale_runtime are not effective identities or grants. | Establish actual mapping, database owner, roles, permissions and module execution context with the read-only queries below. Pools may target different databases or use the same principal; that must be ruled out. |
| `AuthServiceImpl.login`, shared by server login/desktop-session exchange and direct-JDBC desktop login | Auth pool connection, parameterized `TOP 1 Users WHERE email=? AND is_deleted=0`, BCrypt hash check; derives tenant/user from returned row. No tenant stamp, tenant equality, ORDER BY or explicit IsRemoved predicate in this query. Removal constraint implies deleted when removed, but a NULL/nonpositive tenant cannot initialize valid runtime authority. | Strict sec FILTER hides all Users from ordinary pre-tenant auth. It works only if the caller already has the documented function's bypass; assuming this or granting ShaleAdmins to make login work is unacceptable. Nonunique exported email index means uniqueness cannot be presumed; ambiguous identity handling is an auth prerequisite. |
| Remembered sign-in: `SqlRememberCredentialStore.lookup` -> `ResolveDesktopRememberCredential` | Auth pool calls a bounded hash/installation/expiry lookup; repository procedure joins same-tenant Users and excludes deleted/removed. Subsequent rotation uses tenant-initialized runtime, same-owner session/JTI checks and transaction. | Repository procedure has **no EXECUTE AS clause**. dbo ownership chaining can provide SELECT permission, but does not itself switch USER_NAME or bypass RLS. Strict Users FILTER can return no candidate before context exists. Export contains no procedure definition; obtain catalog evidence, do not conclude the live procedure is absent. |
| Server bearer/bootstrap: `BearerTokenServerSessionResolver`, `ServerAuthSessionService`, `SqlDurableSessionStore`, `UserSessionDao` | Verify JWT first; SQL durable-session operations open runtime using verified principal, explicitly qualify session/user tenant, exclude deleted/removed Users and verify session keys on create. Login issues the durable session only after credential lookup. | No circular tenant discovery in bound-token validation: signed identity supplies runtime context. Verify create/find/refresh/logout and /me under new Users coverage; public/global paths must never use these tables as unscoped domain access. |
| `RequestScopedDbSessionProvider`, `RuntimeSessionServiceConnectionProvider`, desktop `DesktopUiRuntimeBridge`/provider | Unauthenticated domain borrow denied. Desktop initializes after JDBC login/remembered restore; server scopes provider to verified principal. RuntimeSessionService stamps ShaleClientId and PrincipalUserId on **every borrow**, closes on initialization failure; clear denies future borrow. | Source does not set read_only=1. Mutable context is needed for pooled tenant/actor changes; do not add read_only based on examples. Prove A1/A2/B1/A1 switching and NULL/mismatched context locally. Global control-plane provider borrows runtime without tenant initialization, but is wired to global release/policy objects, not these three tables. |
| Minimized `CaseSummaryDao` reads | verifyMinimizedActor checks active/nonremoved same-tenant Users and both context keys. Case root is tenant-qualified/active before paging; Users joins require same tenant; Statuses join is tenant-or-global. Overview reads then appends audit on same connection and commits before DTO return. | Explicit predicates continue protecting these selected queries despite missing RLS; new FILTERs complement them. Missing/foreign/deleted Cases remain indistinguishable. No claim for every legacy API/desktop join. |
| `UserDao`, desktop directory/Team/assignment/settings/profile consumers | search/list/detail and management writes use tenant predicates; management rechecks current admin. `countActiveUsers` has only is_deleted=0 and relies on connection/RLS; no production caller was found in the searched UI/desktop sources. Numerous shared DAO joins consume Users by ID. | Strict RLS closes unqualified read coverage and hides legacy NULL users from ordinary runtime. Audit historical user displays, selectors, task/case assignments, sessions and user-management flows. RLS does not implement active state, admin authorization or protect password columns separately. |
| `CaseDao` Status selectors/settings and shared `CaseSummaryDao` desktop grids/search/Calendar/Documents/Reports | Effective selectors read global/current rows, resolve SystemKey tenant precedence **before** excluding inactive/deleted winners. History joins keep stored IDs even if inactive. Global Settings edit creates a tenant override; tenant updates qualify owner. Lifecycle remove/restore validates admin and commits entity-action audit on the same connection. | Overlay FILTER retains global defaults/history and masks foreign definitions, including legacy unqualified ID joins. FILTER allows targeting visible global rows if UPDATE/DELETE is granted: global read access must not be treated as global write permission. |
| `AuditLogDao` writers, `PhiAuditService`, desktop `PhiReadAuditService` | Both append overloads obtain tenant from SQL context. Connection-bound append throws; R2 requires one exact Case.Overview.Read/type1/code4/READ metadata event, commit before return, safe failure without DTO. Aggregate PHI writes on a supplied connection roll back on audit failure. Desktop screen reads dedupe/catch failure; non-connection PHI convenience paths can suppress failure. | Strict FILTER changes read visibility, **does not reject wrong-tenant INSERTs**. Preserve INSERT permission and existing failure contracts; do not claim desktop best-effort paths become fail-closed through RLS. AFTER INSERT/UPDATE blocks are a separate write decision below. No change to event fields, timestamps, sanitization or required audit policy. |
| Audit review: `AuditLogViewerController`, `AuditLogDao.listAuditLogEntries` | Viewer normally gates on AppState admin; DAO requires requested tenant equal connection context, explicit tenant predicate, cap500. DAO has no authoritative active-admin check. It logs SQL and bound values to stderr, as already reported by #1865. | Tenant FILTER preserves same-tenant administrator visibility but does not restrict ordinary SQL users within that tenant. Shared runtime SELECT grants and desktop-held credentials could allow non-UI reads. Actual grants unknown; future admin-read boundary/least-privilege work is required before claiming DB-enforced admin-only audit access. Logging privacy is a separate unresolved source finding. |

**Effective access cannot be inferred from this source inventory.** If an identity has direct SELECT
through object/schema/database grants, public/nested/fixed roles, ownership, or an elevated module,
these unprotected tables currently have no RLS tenant restriction on that path. Application WHERE clauses
protect only the queries that include them. db_owner/control/impersonation and dbo/ShaleAdmins bypass
also require review; app `Users.is_admin` is unrelated to SQL role ShaleAdmins. A tenant admin on ordinary
runtime remains tenant-bound. Do not put shared runtime/auth identities in that bypass role.

RLS here trusts SESSION_CONTEXT, not a signed end-user credential. Anyone able to use a shared JDBC
identity and stamp arbitrary context may choose a different tenant if grants permit. Desktop direct
JDBC/configuration and module/context-setting privileges are therefore part of the trust boundary;
even a passing non-dbo harness is not proof against hostile possession of shared SQL credentials.
No live credential exposure or direct SQL misuse was tested or established by this assessment.

### Bounded catalog handoff for unknowns (not executed)

An authorized operator should run these SELECT-only statements in Shale with existing approved access;
this task does not authorize a connection. Use an existing metadata-capable administrator for catalog
inventory, then separately the actual auth and runtime connections for current-identity/effective checks.
Never export connection configuration, passwords, token/credential hashes, User/audit rows or raw logs.
Record safe identities/permission names, counts and reviewed predicate semantics. TOP limits bound
returned catalog metadata, not an execution deadline. If a limit is reached, visibility is insufficient,
an object is hidden or a definition is NULL, report incomplete evidence; do not interpret that as absence.

```sql
-- Run on each approved auth/runtime connection, without EXECUTE AS or context mutation.
SELECT DB_NAME() AS DatabaseName, USER_NAME() AS DatabasePrincipal,
       ORIGINAL_LOGIN() AS OriginalLogin, SUSER_SNAME() AS EffectiveLogin,
       CONVERT(nvarchar(128), DATABASEPROPERTYEX(DB_NAME(), 'Collation')) AS DatabaseCollation,
       IS_MEMBER(N'ShaleAdmins') AS ShaleAdminsMember,
       IS_MEMBER(N'db_owner') AS DbOwnerMember,
       HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') AS CanViewDefinitions;
SELECT TOP (100) name, type, usage FROM sys.user_token ORDER BY name;
-- Login token may be limited/unavailable under some Azure identities; retain UNKNOWN then.
SELECT TOP (100) name, type, usage FROM sys.login_token ORDER BY name;
SELECT TOP (100) permission_name FROM sys.fn_my_permissions(NULL, 'DATABASE')
ORDER BY permission_name;
SELECT TOP (100) permission_name FROM sys.fn_my_permissions(N'dbo.Users', 'OBJECT')
ORDER BY permission_name;
SELECT TOP (100) permission_name FROM sys.fn_my_permissions(N'dbo.auditLog', 'OBJECT')
ORDER BY permission_name;
SELECT TOP (100) permission_name FROM sys.fn_my_permissions(N'dbo.Statuses', 'OBJECT')
ORDER BY permission_name;
SELECT TOP (100) permission_name
FROM sys.fn_my_permissions(N'dbo.ResolveDesktopRememberCredential', 'OBJECT')
ORDER BY permission_name;
```

```sql
-- Metadata-capable catalog connection. LEFT JOIN preserves tables with zero predicates.
SELECT TOP (50) s.name AS TableSchema, t.name AS TableName, c.is_nullable,
       ps.name AS PolicySchema, sp.name AS PolicyName, sp.is_enabled,
       p.predicate_type_desc, p.operation_desc, p.predicate_definition
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
JOIN sys.columns c ON c.object_id=t.object_id AND c.name=N'ShaleClientId'
LEFT JOIN sys.security_predicates p ON p.target_object_id=t.object_id
LEFT JOIN sys.security_policies sp ON sp.object_id=p.object_id
LEFT JOIN sys.schemas ps ON ps.schema_id=sp.schema_id
WHERE s.name=N'dbo' AND t.name IN (N'Users',N'auditLog',N'Statuses')
ORDER BY t.name, sp.name, p.security_predicate_id;
SELECT TOP (10) s.name AS FunctionSchema, o.name, m.is_schema_bound, m.definition
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
WHERE (s.name=N'sec' AND o.name IN (N'fn_FilterByTenant',N'fn_FilterByTenantOrGlobal'))
   OR (s.name=N'rls' AND o.name IN (N'fn_TenantMatch',N'fn_TenantMatch_Users',N'TenantFilter'))
ORDER BY s.name,o.name;
-- Static dependencies are discovery aids; dynamic SQL/external jobs are not fully represented.
SELECT TOP (200) OBJECT_SCHEMA_NAME(d.referencing_id) AS ModuleSchema,
       OBJECT_NAME(d.referencing_id) AS ModuleName, o.type_desc,
       m.execute_as_principal_id, USER_NAME(m.execute_as_principal_id) AS ExplicitExecutionPrincipal,
       COALESCE(o.principal_id,s.principal_id) AS EffectiveOwnerPrincipalId,
       d.referenced_entity_name
FROM sys.sql_expression_dependencies d JOIN sys.objects o ON o.object_id=d.referencing_id
JOIN sys.schemas s ON s.schema_id=o.schema_id
LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
WHERE d.referenced_id IN (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
ORDER BY ModuleSchema,ModuleName,d.referenced_entity_name;
-- execute_as=-2 means OWNER; NULL means caller, not dbo. Inspect owner separately.
SELECT TOP (10) o.name,m.execute_as_principal_id,
       USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS ModuleOwner,
       m.is_schema_bound
FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
WHERE s.name=N'dbo' AND o.name=N'ResolveDesktopRememberCredential';
SELECT TOP (1) name AS DatabaseName,SUSER_SNAME(owner_sid) AS DatabaseOwner
FROM sys.databases WHERE database_id=DB_ID();
SELECT TOP (100) i.name AS IndexName,i.is_unique,i.is_disabled,i.filter_definition,
       c.name AS ColumnName,ic.key_ordinal,ic.is_included_column
FROM sys.indexes i JOIN sys.index_columns ic
 ON ic.object_id=i.object_id AND ic.index_id=i.index_id
JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id=OBJECT_ID(N'dbo.Users') AND i.is_hypothetical=0
ORDER BY i.name,ic.key_ordinal,ic.index_column_id;
SELECT TOP (100) OBJECT_SCHEMA_NAME(cp.major_id) AS ModuleSchema,
       OBJECT_NAME(cp.major_id) AS ModuleName, cp.crypt_type_desc
FROM sys.crypt_properties cp
WHERE cp.major_id IN (SELECT d.referencing_id FROM sys.sql_expression_dependencies d
 WHERE d.referenced_id IN (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses')))
ORDER BY ModuleSchema,ModuleName;
```

```sql
-- Run per actual auth/runtime connection: token includes applicable roles/public.
-- Explicit permissions are supplementary; fixed roles/owners need effective checks above.
SELECT TOP (500) gr.name AS Grantee, grantor.name AS Grantor,
       p.class_desc,p.major_id,p.minor_id,p.permission_name,p.state_desc,
       CASE WHEN p.class=1 THEN OBJECT_SCHEMA_NAME(p.major_id)+N'.'+OBJECT_NAME(p.major_id)
            WHEN p.class=3 THEN SCHEMA_NAME(p.major_id) ELSE DB_NAME() END AS SecurableName
FROM sys.database_permissions p
JOIN sys.database_principals gr ON gr.principal_id=p.grantee_principal_id
LEFT JOIN sys.database_principals grantor ON grantor.principal_id=p.grantor_principal_id
WHERE p.grantee_principal_id IN (SELECT principal_id FROM sys.user_token)
 AND (p.class=0 OR (p.class=3 AND p.major_id IN (SCHEMA_ID(N'dbo'),SCHEMA_ID(N'sec'),SCHEMA_ID(N'rls')))
 OR (p.class=1 AND p.major_id IN (OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),
     OBJECT_ID(N'dbo.Statuses'),OBJECT_ID(N'dbo.ResolveDesktopRememberCredential'))))
ORDER BY gr.name,p.class,p.major_id,p.minor_id,p.permission_name;
-- Catalog principal/role closure: replace/add names only from approved actual-identity evidence.
WITH RolePaths AS (
 SELECT principal_id AS RootId,principal_id AS MemberId,0 AS Depth
 FROM sys.database_principals
 WHERE name IN (USER_NAME(),N'shale_app',N'shale_runtime',N'ShaleAdmins',N'public')
 UNION ALL
 SELECT r.RootId,rm.role_principal_id,r.Depth+1
 FROM RolePaths r JOIN sys.database_role_members rm ON rm.member_principal_id=r.MemberId
 WHERE r.Depth<16
)
SELECT TOP (200) USER_NAME(RootId) AS RootPrincipal,USER_NAME(MemberId) AS InheritedPrincipal,Depth
FROM RolePaths ORDER BY RootPrincipal,Depth,InheritedPrincipal OPTION (MAXRECURSION 16);
```

These checks establish only the examined connection/database. An administrator must additionally
inventory server-role/CONTROL SERVER/IMPERSONATE authority, database ownership, alternate schemas/modules,
module signing and external jobs with existing approved metadata access where applicable. A dbo
fn_my_permissions result cannot stand in for runtime. No GRANT/DENY, EXECUTE AS, sp_set_session_context,
business data SELECT, hash lookup or connection-secret retrieval belongs in this catalog handoff.

### Smallest compatible remediation and prerequisite order

**Recommended target:** reuse existing enabled `sec.TenantFilter`, add precisely the three missing
read FILTERs: Users/auditLog -> `sec.fn_FilterByTenant`; Statuses -> `sec.fn_FilterByTenantOrGlobal`.
Do not edit shared predicate functions, attach the unused rls shim, create a competing policy, make
Users global, or relax the harness/snapshot. Three FILTERs are the smallest **read-coverage** repair;
they are insufficient for authentication compatibility or database-enforced write/admin authorization.

1. **Close catalog/ownership prerequisites.** Confirm actual auth/runtime principals, databases,
   no runtime dbo/ShaleAdmins bypass or elevated role/alter-policy/impersonation authority, effective
   SELECT/INSERT/module access and database collation. Review nullable Users ownership, actual email
   uniqueness and all pre-context callers (including older installed desktops/jobs); no guessed backfill,
   identity reassignment or silent TOP1 ambiguity. The snapshot proves shape, not row distribution.
2. **Implement a bounded pre-tenant authentication seam first, in a separate reviewed change.** Prefer
   static parameterized database modules for exact credential identity lookup and remembered-credential
   resolution, with narrowly scoped, explicitly reviewed module execution authority. With this existing
   predicate, `EXECUTE AS OWNER` can provide the necessary dbo exception only when effective ownership
   resolves to dbo; test caller restoration and restrict EXECUTE/ALTER/IMPERSONATE. Ordinary ownership
   chaining and permission-only certificate signing do not automatically satisfy USER_NAME/IS_MEMBER.
   Reuse `AuthServicePort`/AuthServiceImpl/BCrypt and DTOs, never accept a client tenant claim as verified
   authority. Return only the existing authentication projection internally; no general enumeration,
   arbitrary SQL or password hash API/logging. Reject unavailable/invalid-tenant/ambiguous identities
   safely; retain active/removal checks. Review hash access because the current Java verifier consumes
   hashes. This is a proposed auth-module design, not an assertion that such a login module exists.
   Security owner review must accept this scoped pre-tenant capability, hash projection and ambiguous
   identity policy before activation; it is not permission for a general tenant-runtime RLS bypass.
3. **Stage compatible clients before Users FILTER.** Add modules and switch shared server/desktop
   auth callers; preserve current password login, desktop enrollment and remembered restore/rotation,
   session issuance/validation/refresh/logout, /me and post-login context initialization. Inventory/drain
   every older direct-SELECT desktop first. There is no secure transparent strict-filter rollout to an
   unknown ordinary pre-context caller. Do not temporarily grant broad bypass or modify the shared
   sec function to preserve old clients. If older-client coexistence is mandatory, stop and obtain a
   separate compatibility decision; do not silently adopt the unused shale_app-exception function.
4. **Prepare a narrow, fail-fast, idempotent migration later.** Require exact target objects/columns,
   reviewed predicate bodies and enabled schema-qualified policy. No-op only for matching predicates;
   reject unexpected/disabled/conflicting policies. Add the three missing FILTERs without data rewrite,
   index/nullability/ID/lookup-history changes. Record exact before/after definitions for scoped rollback.
   AuditLog INSERT remains needed even if SELECT is reduced. Do not run the broad June proposal.
5. **Decide write/admin controls explicitly before rollout.** FILTER does not prevent INSERT of foreign
   ownership or UPDATE changing ownership. Recommend strict AFTER INSERT/UPDATE blocks for Users/audit
   after writer review, retaining append-only audit grants (no runtime UPDATE/DELETE). Statuses needs
   distinct strict write semantics: AFTER INSERT/UPDATE and BEFORE UPDATE/DELETE protection if database
   enforcement of global immutability is required; overlay BLOCK would permit global changes, and only
   AFTER UPDATE would permit converting a visible global row into a tenant row. Keep approved dbo/admin
   maintenance separate. These blocks exceed the harness's read requirement and need scoped tests;
   they are not implied to exist or be verified. Tenant-admin audit review also needs an authoritative
   active-admin service/DAO/module seam and compatible grants: a shared desktop runtime SELECT plus a
   UI check does not prove admin-only DB access. Preserve the viewer while planning any grant reduction;
   do not revoke SELECT blindly or give every app admin SQL ShaleAdmins membership.

**Audit compatibility review:** no new user-visible read/mutation/event is created by this assessment.
The repair reuses existing audit schema/representation. Required R2 Overview delivery remains read +
connection-bound append + commit; success/reread produces one event, search/assigned retain accepted
summary exemption. Failures withhold all Case data; uncertain commit retains durable audit with no
automatic replay/delete. Review aggregate PHI/entity-action writers, nullable actor/system rows and
approved administrative tools before BLOCK/grant work. Desktop best-effort reads and legacy suppressed
writes remain separately scoped limitations. Authentication modules must not add ad-hoc PHI/Timeline
events or log credentials. Catalog/migration operator evidence is change-control evidence, not fabricated
application audit. Existing audit-list logging privacy and missing DAO admin recheck remain open.

### Verification, rollout and rollback gates

Before running SQL, prepare a dependency-complete **derived UTF-8 schema-only input**, hash and manifest
from the unchanged authoritative export. Record source and derived hashes separately. Full raw export
is not importer-ready: UTF-16 differs from the Java UTF-8 reader; scalar dbo.ToBitNV is unsupported;
`CREATE   FUNCTION` whitespace and SSMS SET ANSI_PADDING ON are outside the literal allowlist.
Retained computed expressions/defaults and all FK/type/function dependencies must be resolved, not
deleted to pass. Transcoding/format normalization or a narrow safety-reviewed importer enhancement must
preserve definitions. Record the source database identifier collation and the disposable database's
collation separately: the harness creates a database with the local server default, not an exported
CREATE DATABASE definition. Resolve a case-sensitive AuditLog name mismatch through a reviewed source/
tool compatibility change, never rename the authoritative table to conceal it. The current snapshot
must continue to report the three missing FILTERs. A later
candidate migration in an approved disposable clone produces a **separately labelled candidate final
schema**, never a falsified authoritative export. #1865's importer does not run migrations: plan separate
local candidate preparation/export or a narrowly reviewed test-tool workflow for it. No candidate DDL,
derived input or integration execution was produced here.

| Gate | Evidence required before rollout / closure |
| --- | --- |
| Auth prerequisite tests | Inspect/update existing AuthUserLifecycleSecurityTest's source-string assumption, auth adapter/controller and desktop lifecycle tests; add real local auth-principal tests for initial NULL context, valid/invalid/removed/NULL-tenant/ambiguous identities and pooled isolation. Prove module execution authority is confined and restored; no broad direct hash/table access after final grant cutover. |
| Remember/session/bootstrap | Local auth-principal remembered resolver plus runtime rotation/JTI/revocation tests; server login/desktop exchange/session issuance, bound validation/refresh/logout and /me; no circular tenant lookup, client tenant assertion or caller authority retained from modules. |
| Unchanged Case-read harness | Run original fixed scenarios on the labelled candidate schema using generated non-dbo identity with no bypass: A/B/NULL context, globals, pooling, paging/hasMore, joins/actor/deletion, exact audit and real denied INSERT. Commit wrappers remain injected transport evidence; inspect durable outcome independently. It does not cover auth modules, actual production grants or all desktop flows. |
| Writes/admin compatibility | If adding blocks, reject foreign/null ownership writes and global Status mutations; permit valid tenant transactions and approved global maintenance. Verify ordinary versus tenant-admin audit reads through the approved boundary; retained historical audit/Status/User displays, settings/Team/assignment/Calendar/Documents/Reports. No success claim from an empty RLS-filtered reader. |
| Runtime/build regression | Future implementation follows focused tests, selector affected modules/dependencies and critical Maven. Keep inherited full-suite advisories attributed and unresolved; inspect relevant migration-contract tests and runner safety tests without weakening assertions. |
| Deployment evidence | Separately authorized operator confirms reviewed migration/candidate/source parity, actual grants/roles/context, database identifier collation, compatible installed clients and representative query plans/latency. Local success does not accept deployed Azure. R4 sessions/host/no-store/CORS/privacy/device/AT gates remain separate. |

Future rollout order: catalog decisions -> additive auth modules -> shared auth implementation/server and
compatible desktop release -> old-client drain -> isolated candidate verification -> authorized targeted
policy change and any separately accepted blocks/grants -> sanitized operational smoke checks. Statuses/
audit FILTERs can be independently staged after their consumers pass, but Users/auth blocker stays open
until the full prerequisite chain closes. Do not deploy a partially verified set and call security accepted.
Stop on login/remembered-restore denial, missing global/history presentation, audit append failure,
context contamination or unexpected authority; withhold sensitive results, investigate safely.

Prepare/rehearse rollback before authorization: restore only this change's recorded added predicates,
module definitions and specific permission deltas; never disable the whole TenantFilter, alter shared
functions, remove preexisting protection or delete committed audit history. Removing only newly added
FILTERs reopens the confirmed coverage gap and requires explicit security disposition; it is not a green
acceptance state. Prefer rolling back a faulty caller while retaining compatible auth modules/policies.
Reverting to old direct-SELECT auth clients under strict Users FILTER will break login; coordinate database
and client rollback using the recorded compatibility matrix. Keep additive modules while dependent
clients remain. Durable-session cutoff/secret/legacy-token rollback constraints in the
[App Service guide](../azure-app-service-deployment.md#phase-7b-durable-api-sessions) still apply.

### Next implementation prompt

Repository `gseshadow/Shale`, PR base `codex/latest`: implement **only the pre-tenant authentication
prerequisite** for the reviewed three-table RLS plan. Read AGENTS.md, codex-prompt-rules and their routing,
this assessment, tenancy/schema and release/session owners completely as applicable. Use approved
operator catalog evidence to settle actual identities/owners/grants and email/NULL-tenant ambiguity;
if unavailable, prepare reviewable modules/callers with explicit deployment blockers, never assume grants.
Create a separate branch. Add bounded static credential lookup and remembered-credential identity
modules with reviewed execution authority; switch shared AuthServiceImpl and remembered resolver while
retaining Java BCrypt, auth ports/DTOs, server/desktop login/enrollment and durable session contracts.
Restrict module capability, sanitize output/logging, fail safely on invalid/ambiguous identities and prove
caller restoration. Do not set tenant from unverified client input or broaden shared sec predicates.
Inspect/update existing auth/source-contract, remembered migration/store, controller and desktop lifecycle
tests; add synthetic and opt-in local SQL coverage under actual proposed non-bypass auth/runtime roles.
Do not attach the three FILTERs yet; document older desktop drain and exact rollback/permission evidence
needed before that next migration. Keep CaseReadLocalSqlServerTest unchanged and the original export
unmodified/uncommitted. Do not label candidate schema as production. No live connection, production
credential, fault, configuration change, deployment or merge. Run focused/selected/critical relevant
checks, update owning readiness/runbook/roadmap, commit/push and open a PR to codex/latest. Record any
unrun SQL and preserve all R4 reports/security gates. Then propose the separately reviewed targeted RLS
migration and candidate-verification task once auth compatibility is demonstrated.

### Assessment review and documentation checks

Changed-file/source review covers eight Markdown owners only; no production or test contracts changed.
Reviewed the exported policy/functions/table shapes, Users/auth/session/remembered resolver, runtime
initializer, minimized/shared desktop Case/Status readers, PHI/required/entity-action audit and viewer
seams, June proposal and relevant incremental migrations against the plan. The review retained the
auth-before-context dependency, unused versus attached functions, FILTER versus BLOCK distinction,
global Status write limitation, dbo/ShaleAdmins exceptions, ambiguous email/nullable Users, module
ownership-chain limitation, unknown production grants and original-versus-candidate evidence boundary.
No independent human approval or SQL/security acceptance is claimed.

Executed documentation checks on this assessment:

```bash
python3 /workspace/work/check_rls_docs.py
python3 build/test-selection/select_tests.py --path architecture/database-schema.md \
  --path architecture/tenancy-and-rls.md \
  --path docs/testing/case-read-local-security-verification.md \
  --path docs/web-api-azure-readiness.md \
  --path docs/architecture/shale-web-v2-first-read-contract.md \
  --path docs/architecture/shale-web-v2-architecture-roadmap.md \
  --path docs/shale-web-deployment.md --path shale-web/docs/r4-desktop-acceptance.md \
  --format markdown --plan-output /workspace/work/rls-doc-selection.json
git diff --check
```

PASS: relative links/anchors, fences/whitespace, original export byte hash/encoding/size and offline
absence of the three policy attachments; accepted R4 report tables unchanged; Java/SQL/tooling/build/
configuration scope unchanged. Selector PASS: broad tenancy-document/web paths map to security-data
and server, no modified tests or full-suite escalation. Their runtime tests and generic critical Maven
command do not verify documentation-only assessment; Maven/npm/browser/SQL suites were not run.
Historical focused/critical passes, skipped local SQL and inherited full-suite advisories stay attributed
to their original tasks. This task establishes documentation/source review only. SQL integration and
security acceptance remain OPEN/NOT RUN.

## Pre-tenant authentication prerequisite implementation — 2026-10-14

**REVIEW CANDIDATE; SQL EXECUTION UNVERIFIED; DEPLOYMENT BLOCKED.** Fetched live
`origin/codex/latest` `59d8374858764788c621f6f8d986773b58ab79c4`, confirmed merged #1867 by
GitHub metadata and Git ancestry. Separate task worktree/branch `codex/pretenant-auth-prerequisite`
preserves the original checkout. This implements only the authentication prerequisite, not the
three-table FILTER migration. No live connection, production credential, fault, configuration change,
deployment or merge occurred. The original export is not present in this task's mounted files; its
previously accepted evidence/hash above remains attributed to #1867. No replacement export was made.
`CaseReadLocalSqlServerTest` and all accepted R4 reports remain unchanged. Security acceptance OPEN;
Phase 2 OPEN, Phase 3 IN PROGRESS, Phase 4 INCOMPLETE, appearance provisional.

### Bounded modules and execution-authority review

[Candidate SQL](../sql/2026-10-14_pretenant_authentication_modules.sql) creates two **new versioned**
dbo procedures. It leaves the historical `ResolveDesktopRememberCredential` and its grants intact:

| Module / consumer | Exact capability and failure policy |
| --- | --- |
| `dbo.LookupAuthenticationCredentialV1` / shared `AuthServiceImpl` | One exact email parameter, at most 255 UTF-16 units; static SQL captures at most two matching identities once. It returns the existing internal User/auth projection plus `IsRemoved` only when exactly one matching row exists, positive user/tenant with an existing ShaleClients identity, `is_deleted=0`, not removed and a populated hash. Java still verifies BCrypt and defensively rejects duplicate/null/nonpositive/lifecycle-invalid results before returning the unchanged User. Password and tenant selector are never SQL parameters. |
| `dbo.ResolveDesktopRememberCredentialV2` / `SqlRememberCredentialStore.lookup` | Exactly 32-byte opaque hash, installation UUID and existing time parameter; static, same-tenant Users/session joins. At most two raw credential candidates captured before joins (invalid ownership cannot hide ambiguity); only one positive owner with an existing ShaleClients identity and eligible user, unconsumed credential, unrevoked session and both deadlines later than supplied **and database** time can return tenant/user/session/email. No hash is returned. Java checks result cardinality/identity/session syntax. It remains only a candidate: authoritative serializable runtime rotation/JTI/revocation and explicit owner predicates are retained. |

The proposed authority is `WITH EXECUTE AS OWNER` **only with effective owner dbo**. Deployment preflight
requires dbo schema ownership, and both module bodies fail if `USER_NAME()` is not dbo. SQL Server's module
scope restores the caller automatically on success/error; neither caller executes impersonation, REVERT
or session-context mutation. No dynamic SQL, arbitrary tenant lookup, broad enumeration, general domain
read or mutation is exposed. Existing sec functions/policies are untouched. Ordinary permission chaining
or permission-only signing would not alone supply the existing predicate's dbo exception.

This is a source-reviewed proposal, **not independent security-owner approval or production evidence**.
The SQL installs no roles, memberships, GRANT/DENY/REVOKE, or base-table permission changes. The old
remembered resolver remains available for staged old/new server rollback. The versioned installer fails
on any preexisting target object; compare/review definitions rather than overwriting unknown authority.
Run batches on one connection with stop-on-first-error and rollback; continuing after a failed GO batch
is forbidden. Both modules must install/commit together before switching shared callers.

Password identity policy deliberately rejects **all duplicate email matches under the existing database
comparison/collation**, including an inactive/removed/NULL-tenant duplicate. No normalization, new email
index, backfill, reassignment, or silent TOP1 selection is introduced. The existing email_norm nonunique
index does not establish uniqueness or bounded scanned work. Returned rows are bounded; caller statement
timeouts are five seconds. Catalog/representative-plan review remains required. Password login retains
its prior `is_deleted=0` semantics; remembered lookup retains its nullable-deleted COALESCE semantics.
NULL/nonpositive tenants never become trusted runtime identities. Clients never supply a trusted tenant.
Driver messages/causes are removed at the pre-tenant lookup error seams; no credentials/hash/email/SQL
or sensitive values are logged there. Other preexisting logging findings remain separately open.

Audit compatibility uses the existing schema and lifecycle: lookup and routine credential rotation are
security plumbing, not new PHI/business views or domain/admin mutations. No ad-hoc PHI, Timeline, entity
or per-login audit is appropriate. Existing durable issuance/logout/revocation security records and
transaction-bound audits remain authoritative and unchanged; no audit migration is needed.

### Explicit deployment blockers and compatibility sequence

1. Authorized operator evidence is still **UNKNOWN**: actual auth/runtime databases/principals, dbo
   database/schema/table/module ownership, nested/public/fixed/server roles, effective permissions,
   signing/impersonation/ALTER/CONTROL authority and identifier/email collation. Run the bounded operator
   catalog handoff above separately as actual auth/runtime identities and metadata-capable operator;
   do not substitute dbo output for caller permissions. Inventory the new modules with
   `sys.sql_modules.execute_as_principal_id=-2` and effective object/schema owner dbo, both before/after
   deployment. Include new modules in dependency/permission inspection and retain exact definitions/hashes.
2. Security owner must explicitly accept the bounded dbo capability and **internal password-hash projection**,
   ambiguity rejection policy and caller-restoration evidence. Possession of shared desktop auth SQL
   credentials permits repeated exact-email hash lookup through this module; row bounds do not make that
   a password-verified DB boundary or defeat offline hash attacks. No new HTTP hash endpoint exists.
   Resolve duplicate email/NULL-owner impacts using authorized minimized evidence, not row exports.
3. Local SQL execution below and server/desktop runtime compatibility must pass before activation.
   Review production schema/types/defaults/FKs and plans separately: the synthetic fixture is not parity.
   Auth and runtime identities must be non-dbo/non-ShaleAdmins/non-elevated. Proposed local role names
   `ShalePreTenantAuthentication` and `ShaleTenantRuntime` describe a test permission profile, not deployed
   principals or a grant instruction. Map reviewed actual identities explicitly before any grant change.
4. Additive modules first, then approved **object EXECUTE only on both new modules** for the reviewed auth
   role. Capture every prior permission state/grantor/membership. Runtime must not execute these modules,
   ALTER/CONTROL them/the dbo schema, impersonate dbo or change RLS. Auth must have no general Users/hash
   or remembered-table SELECT after its compatible-client cutover. The migration makes no grants because
   inheritance/column grants/denials/public/fixed roles and existing caller needs are unknown. Do not blindly
   DENY/revoke SELECT: review each old client/control-plane dependency and permitted runtime projection.
5. Rebuild/deploy compatible server **and desktop** consumers after modules/EXECUTE are verified. Preserve
   password login, second server credential exchange, instance enrollment, checked/unchecked remembered
   sign-in, rotation/retry, ordinary-close preservation, explicit logout, revocation, refresh and /me.
   No token DTO/port/signing/cutoff/DPAPI contract changes. Missing module/EXECUTE fails safely; there is no
   direct-SELECT fallback. Existing Windows acceptance is historical and does not certify this build.
6. Inventory and drain **all** older installed direct-SELECT desktops, running processes, jobs and other
   pre-context callers before reducing their grants or attaching strict Users FILTER. Record exact minimum
   compatible artifact/version/source hashes and fleet completion; this PR does not invent a release number.
   Existing minimum-version safe drain does not itself upgrade old JDBC authentication. If coexistence is
   mandatory, obtain a separate compatibility decision; no shared RLS exception or SQL-admin bypass.
7. Only then prepare separately reviewed targeted FILTER/candidate work. Write BLOCKs, admin-only audit
   access, global Status immutability, direct-JDBC credential/context trust and deployed acceptance remain
   independently open. Do not interpret this prerequisite as closing those gates.

### Local verification and exact rollback

[Quiet auth launcher](../../build/auth-verification/run.py) and
[opt-in SQL test](../../shale-server/src/test/java/com/shale/server/runtime/PreTenantAuthenticationLocalSqlServerTest.java)
use an explicitly labelled **synthetic auth compatibility fixture**, never an authoritative/candidate Case
schema. Dedicated loopback SQL Server, fresh generated database/logins and exact nonce ownership marker;
no arbitrary JDBC URL/database/schema input, app connection variables, implicit Maven arguments or Azure
engine. Only the proposed role profile receives object grants inside that fresh DB. Auth has module EXECUTE
and no direct Users/credential-table SELECT; runtime has tenant reads/rotation permissions, no hash read,
module EXECUTE or dbo impersonation. A one-connection Hikari pool tests caller/NULL context restoration,
valid A/B password login, absent/ambiguous/disabled/removed/NULL-owner/wrong-password rejection, owner-guard
error restoration, remembered rotation/JTI/replay/installation/revocation/disabled/removed/NULL-owner/expiry,
and runtime A1→A2→B1→A1/NULL isolation. Test-only ownership alteration is confined to the disposable fixture;
it is not an observed production fault. This is not full-schema/HTTP/installed-Windows acceptance.

After setting up the dedicated local SQL instance using the existing safe setup guidance above, use an
installed JDK 21/Maven terminal with no application connection variables:

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333
```

The launcher privately prompts for dedicated **local** admin credentials and discards unrestricted Maven/
driver output; fixed scenario names only. Cleanup closes pools and checks the exact generated DB nonce
before dropping that DB and its two generated logins. If marking/ownership or cleanup fails, report the
fixed failure and require exact-resource operator review; never scan/drop by prefix. This fixture does not
modify the original export, Case harness or any shared instance data. No local SQL instance/credentials
were supplied here, so **AUTH SQL NOT RUN** and **CASE SQL NOT RUN**. SQL/production authority and installed
compatibility remain unverified regardless of synthetic test success.

Prepare rollback evidence before any later authorization. No database rollback was executed here:

1. Record unchanged old resolver definition/permissions, absence of both new modules, dbo ownership,
   exact installer hash, all proposed permission/member deltas, and previous compatible server/MSI hashes.
   If a new module name already existed, stop installation; this script cannot safely restore unknown code.
2. Before any FILTER change (none in this PR), roll server and desktop consumers back to the recorded
   previous durable-session-aware artifacts, finish/drain running new callers, and verify old password/
   remembered login under their recorded original permissions. Keep new modules while any client depends
   on them. Do not revert to pre-7B revocation-ignorant code or alter signing/cutoff; retain the App Service
   guide's maximum-TTL/emergency-secret rules if a separately authorized older rollback ever requires it.
3. For each **new** EXECUTE grant whose captured previous state was absent, revoke precisely that grant
   from its recorded actual role. Example template (replace grantee only from approved evidence):

   ```sql
   REVOKE EXECUTE ON OBJECT::dbo.LookupAuthenticationCredentialV1 FROM [reviewed_auth_role];
   REVOKE EXECUTE ON OBJECT::dbo.ResolveDesktopRememberCredentialV2 FROM [reviewed_auth_role];
   ```

   If previous state was GRANT/DENY/WITH GRANT OPTION or permissions/memberships changed separately,
   restore the exact recorded state/grantor/member delta instead; do not run these templates blindly.
   This installer creates no production role or membership to drop. Do not revoke unrelated inherited
   grants or reset all auth/runtime permissions.
4. Once dependency drain and identity/definition hashes prove these are exactly this change's new objects:

   ```sql
   DROP PROCEDURE dbo.ResolveDesktopRememberCredentialV2;
   DROP PROCEDURE dbo.LookupAuthenticationCredentialV1;
   ```

   Leave `dbo.ResolveDesktopRememberCredential`, Users, sessions, remembered credentials, audit history,
   every existing predicate/function/policy and original schema export untouched. Prefer retaining harmless
   additive modules over stranding clients. If a later Users FILTER exists, **stop this old-client rollback**;
   coordinate its separate exact-predicate disposition first, never disable the whole policy.

The next targeted RLS migration/candidate-verification implementation prompt is deliberately **DEFERRED**
until auth SQL and compatible installed callers are sufficiently demonstrated. The current prerequisite
prompt above remains historical/source authority. Required next evidence: passing local auth authority/
restoration/rotation, approved actual catalog/grant/identity decisions, compatible fleet/drain matrix.
Then the separately reviewed task may add only the three accepted FILTERs and prepare labelled derived/
candidate hashes/manifests for the unchanged Case harness; it must retain all existing security/host/R4 gates.

### Prerequisite test impact and executed checks

Pre-edit reference inventory covered AuthServiceImpl/password projection/lifecycle, AuthServiceAdapter,
remembered procedure/migrations/store/bindings, server auth/desktop exchange/durable lifecycle/controllers,
runtime initialization, desktop enrollment/restore/logout/revocation and UI lifecycle tests. Existing
AuthUserLifecycleSecurityTest's direct-SELECT assumption now points to the module's lifecycle contract;
SqlRememberCredentialStoreTest uses V2 and rejects malformed/ambiguous identities before runtime borrow.
AuthCredentialLookupTest executes the shared implementation through JDBC doubles with real Java BCrypt;
PreTenantAuthenticationModuleContractTest protects static/bounded/owner/ambiguity/no-grant scope. New tests
are owned by security-data/server; no opt-in SQL execution was added to critical defaults. Selector smoke
covers these changed contracts plus existing tenant/audit/request-context and auth HTTP dependencies.
Additional desktop/server compatibility checks protect unchanged enrollment, remembered and durable flows.
No user-visible/domain/admin mutation or new audit event is introduced; established audit integration is
retained without a migration.

Successful commands use scratch Maven 3.9.9/JDK 21.0.2, existing Microsoft JDBC 12.6.1.jre11. Initial missing
Maven/compiler, local-repository access and scratch-JDK CA trust errors were resolved without changing
repository POM/dependency/runtime configuration. The successful command prefix was:

```bash
JAVA_HOME=/workspace/work/pretenant-review/jdk-21.0.2 \
MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
/workspace/work/pretenant-review/apache-maven-3.9.9/bin/mvn \
-s /workspace/work/pretenant-review/settings.xml
```

The scratch settings point to the inherited session proxy and a scratch local repository; no credentials
or application configuration are copied. Below, `mvn` means that exact prefix for this execution record,
or installed JDK 21/Maven in an operator terminal. No SQL opt-in/admin credentials were set.

```bash
mvn -pl shale-data,shale-server -am \
  -Dtest=AuthCredentialLookupTest,PreTenantAuthenticationModuleContractTest,AuthUserLifecycleSecurityTest,AuthServiceAdapterTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,PreTenantAuthenticationLocalSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn \
  -Dtest=AuthCredentialLookupTest,PreTenantAuthenticationModuleContractTest,AuthUserLifecycleSecurityTest,AuthServiceAdapterTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,PreTenantAuthenticationLocalSqlServerTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SessionControllerTest,ServerAuthSessionLogoutTest,RequestScopedDbSessionProviderTest,DesktopSessionEnrollmentLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopRuntimeSessionLifecycleTest,LoginRememberedSignInContractTest,AuthenticatedSessionLifecycleContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown
mvn -pl shale-data,shale-server -am \
  -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest,com.shale.server.runtime.SqlRememberCredentialStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
# Selector recommends this informationally because test-areas.json changed:
mvn -Pall-tests test
# Reproduced the advisory failures in a separate untouched 59d83748 worktree:
mvn -pl shale-data -am \
  -Dtest=AdministrativeReadAuditMigrationContractTest,ApplicationInstanceHeartbeatMigrationContractTest,ApplicationReleaseImportContractTest,CaseDaoCasesGridQueryTest,CaseDateTypeLifecycleCutoverContractTest,CaseDatesFinalRuntimeCleanupContractTest,CaseOverviewConfigurationContractTest,CaseSummaryReportsContractTest,ContactPhase2BAuditMigrationContractTest,FormConfigurationFoundationTest,SessionInvalidationPhase8AContractTest,UserSessionMigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Preserve existing CRLF source rather than rewriting it for whitespace checking:
git -c core.whitespace=cr-at-eol diff --check origin/codex/latest HEAD
```

| Executed check | Result and limits |
| --- | --- |
| Final focused modules/callers | 19 PASS; 1 explicit local auth SQL skip. All test sources compile; no SQL evidence. |
| Additional server/desktop/UI compatibility | 69 PASS; 1 explicit local auth SQL skip. Synthetic controllers/lifecycles, not installed Windows or live login. |
| Committed base-to-head selector / affected suite | 68 PASS; 1 local auth SQL skip. security-data/server/dependencies; no new-area or unrelated historical test activation. |
| Critical `mvn test` | 116 PASS. Does not execute opt-in SQL. |
| Auth launcher safety / selector units | 3 PASS / 24 PASS. No connections. |
| Informational all-tests | Core 111 PASS; data 827 tests, 12 failures, 0 errors, 1 Case SQL skip. UI/desktop/updater/server not executed after data failure. No full-suite pass. |
| Untouched live-base advisory reproduction | 51 tests, exactly the same 12 failing methods, 0 errors. The existing R1/R2/#1865 advisories remain inherited and unresolved; no unrelated fixes. |
| Optional generated inventory check | Stale on both task and untouched live base. Regeneration exposed repository-wide historical drift; generated broad changes were discarded. Narrow test-area ownership is updated; no unrelated inventory rewrite. |

Final changed-file review maps both production callers to the executing auth/store tests and unchanged
server/desktop/session regressions; SQL to module contracts and compiled opt-in harness; runner to safety
units; ownership map to selector units. Search finds no stale direct-SELECT auth or old resolver call in
current production/tests (historical migration remains intentionally unchanged). Added documentation links,
fences, scope/whitespace and exact unchanged Case harness/R4 ledger are checked separately. The original
export remains unmodified/uncommitted and absent from this mount; its historical hash is not recomputed here.
**AUTH SQL NOT RUN; CASE SQL NOT RUN; installed/production compatibility UNVERIFIED; deployment BLOCKED.**
No targeted RLS migration prompt or security acceptance is issued without the missing compatibility evidence.

## Windows local-launcher Maven retest — 2026-10-14

Operator evidence on Windows 11 / Python 3.12.10 / JDK 21.0.8 / Maven 3.9.11:
three existing auth launcher safety tests passed; the dedicated SQL container listens at
`127.0.0.1:14333`. After clearing the process-only `SHALE_AUTH_TOKEN_SECRET`, credentials were
prompted but the launcher reported `launcher_prerequisite`. `shutil.which("mvn")` found
`C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD`, while
`subprocess.run(["mvn", "-version"])` raised FileNotFoundError / winerror 2.
**AUTH SQL NOT RUN**; reaching credential prompts was not SQL verification.

Both Python launchers now resolve an absolute installed Maven path (Windows prefers `mvn.cmd`)
and quietly execute `-version` before credential prompts. Inspection confirmed the Case runner also
invoked a batch wrapper directly; it receives the equivalent fix. Native executables and POSIX Maven
use direct argument arrays with `shell=False`. Windows `.cmd`/`.bat` wrappers use the explicit system
`cmd.exe /d /s /v:off /c`, with every token quoted and the required outer command quotes.
AutoRun and delayed expansion are disabled. Credentials are passed only in the private child environment;
no credential is formatted into a command, logged, or copied into the parent process environment.
Command tokens containing quotes, `%`, `!`, `^`, `&`, `|`, `<`, `>`, NUL or line breaks are refused
instead of being interpreted; ordinary spaces and parentheses are supported. If an installed Maven or
quiet-config path contains those refused characters, use a dedicated path without them. `--maven`
on either launcher accepts one installed executable path, never command arguments.

Fixed tooling/environment outcomes are `AUTH_VERIFY FAIL <category>` or
`CASE_READ_VERIFY FAIL <category>`:

| Category | Meaning / operator action |
| --- | --- |
| `maven_missing` | Maven cannot be resolved. Install/add Maven to PATH or provide `--maven` with its exact path. No credentials prompted. |
| `maven_invocation` | Resolved Maven could not start, the quiet version probe failed (including unusable JDK), or Windows command tokens were unsafe. Check local tooling/path prerequisites; no raw diagnostic or value is exposed. Probe failures precede prompts; a later child-start failure uses the same category. |
| `prohibited_environment` | An application/auth/connection source or implicit Java/Maven argument variable is populated. Use a clean verification terminal; values and variable names are not printed. Both launchers reject `MAVEN_OPTS` as well as the other documented implicit sources. |

Other invalid local inputs keep their existing generic refusal; a Maven build/harness failure after
successful startup keeps the existing fixed build/scenario output. Local-only validation, explicit
opt-in, schema hash/provenance (Case), quiet diagnostics and exact nonce/resource cleanup are retained.
The schema export, Case SQL harness, production authentication/modules/RLS, dependencies and accepted
R4 evidence are unchanged. Audit compatibility retains the existing schema and audit flows; this launcher
change introduces no domain/admin mutation, sensitive application read, or new audit event.

From the repository root of the task branch in a fresh **PowerShell** terminal with the dedicated
local container already running and installed JDK 21/Maven available:

```powershell
# Process-only removal of the operator-confirmed inherited auth secret; no persistent configuration edit.
Remove-Item Env:SHALE_AUTH_TOKEN_SECRET -ErrorAction SilentlyContinue
python --version
java -version
mvn -version
python -m unittest discover -s build/auth-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed' }
python -m unittest discover -s build/case-read-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Case launcher safety tests failed' }
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333
if ($LASTEXITCODE -ne 0) { throw 'Auth SQL remains unverified; review fixed category/scenario and prerequisites' }
```

For an explicit-path retest of the operator's Maven installation, use this **instead of** the final
launcher command above (a second invocation creates another fresh synthetic fixture):

```powershell
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333 --maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'
if ($LASTEXITCODE -ne 0) { throw 'Auth SQL remains unverified; review fixed category/scenario and prerequisites' }
```

Enter only dedicated **local** admin credentials privately at the prompts (`sa` for the previously
created disposable container). Do not populate application connection variables or implicit Java/Maven
arguments to make the launcher pass. Expected auth results are exactly `AUTH_VERIFY PASS credential_identity`,
`AUTH_VERIFY PASS remembered_rotation` and `AUTH_VERIFY PASS runtime_isolation`, with exit code 0 after
successful exact-resource cleanup. Record only fixed outcomes, exit code and tool versions. Any cleanup
failure still requires exact-resource operator review; never scan/drop by prefix or prune containers.
The Case SQL retest still uses the existing reviewed-schema/hash/provenance commands above; this fix does
not supply, derive or modify its schema input and does not authorize SQL execution in this task.

The new cross-launcher regression suite lives in
[`test_maven.py`](../../build/auth-verification/test_maven.py). On Linux its Windows resolution/command
checks are **simulated**, and its native `cmd.exe` fake-wrapper test is explicitly skipped. On Windows
that test executes an actual temporary `.CMD` path with spaces and a synthetic private environment,
without Maven, Java or SQL; its POSIX child-process test is skipped instead. Passing that test proves
batch invocation only. Actual Maven-on-Windows and SQL scenario evidence require the operator commands
above. **Actual Windows execution UNVERIFIED; AUTH SQL NOT RUN; CASE SQL NOT RUN; security acceptance OPEN.**

### Launcher-fix local verification record

Base fetched live: `eefe3e8cc91dc471fec304e3cbdc4a24e5b268d2`; #1868 is merged by GitHub
metadata and Git ancestry. Separate branch/worktree `codex/windows-local-verification-maven` preserves
the original checkout. Pre-edit impact inventory reviewed both `test_runner.py` suites, the selector's
Windows Maven resolution/argv tests, CaseReadVerificationSafetyTest and the opt-in auth harness.
Changed-file review maps both Python launchers to the new cross-launcher process tests, existing guard
suites and owning operator instructions. Searches confirm no remaining bare-Maven invocation in these
launchers and no `shell=True`. The Case guard test additionally covers all implicit Java/Maven sources.

Executed on **Linux / Python 3.12.14 / Maven 3.9.11 / Eclipse Adoptium JDK 21.0.12.1** (Codex toolchain,
not the operator's Windows versions). `mvn` below means the preinstalled
`/workspace/.tools/bin/mvn` wrapper with its existing local settings/JDK; no repository dependencies or
build settings changed. No SQL opt-in or SQL admin credentials were set.

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
mvn -pl shale-data,shale-server -am \
  -Dtest=CaseReadVerificationSafetyTest,PreTenantAuthenticationLocalSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base eefe3e8cc91dc471fec304e3cbdc4a24e5b268d2 --head HEAD --format markdown --plan-output work/selection.json
mvn -pl shale-data,shale-server -am \
  -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B test
git diff --check eefe3e8cc91dc471fec304e3cbdc4a24e5b268d2 HEAD
```

| Check | Result and limits |
| --- | --- |
| Auth/cross-launcher Python discovery | 13 PASS, 1 explicit native-Windows skip. Simulated Windows resolution/quoting/injection checks for both launchers; actual POSIX child process; private credentials, quiet output, pre-prompt failure order and temp cleanup. |
| Existing Case Python safety | 4 PASS. Opt-in, local port, schema/hash/provenance, environment and fixed-output boundary. |
| Selector unit suite | 24 PASS. Existing Windows resolution and structured argv contracts retained. |
| Focused Java harness safety | 5 PASS; 1 explicit local auth SQL skip. Harness compiles; no SQL execution. |
| Selector affected suite | 54 PASS. Security-data auth, tenant/audit and request-context contracts plus module dependencies are selected because local security tooling changed. No full-suite escalation. |
| Critical reactor | 116 PASS; build success. Compilation and synthetic critical regressions across modules. |
| Scope / Markdown / Git checks | PASS: added relative links/fences, diff whitespace, unchanged Case SQL harness/SQL migrations/R4 ledger, original checkout clean. Original schema export is absent from this mount and was not read, regenerated or committed. |

The subprocess boundary receives an explicit environment snapshot. These checks do not
establish actual Windows Maven invocation, SQL scenario success, installed-client compatibility or
production authority. **Windows retest pending; AUTH SQL NOT RUN; CASE SQL NOT RUN; deployment BLOCKED;
security acceptance OPEN.** No live connection, deployment or merge occurred.

## First SQL auth failures after #1869 — 2026-10-14

Live base `30ca63c9d0b9ceefceb4ae3dd057b16376b42080` is merged #1869. The task branch
`codex/windows-auth-verification-diagnostics` starts at that exact base. Reviewed AGENTS, prompt rules,
database schema, tenancy/RLS architecture, this runbook, versioned auth modules, production store,
RuntimeSessionService, launcher and related tests. The original schema export is absent from this mount;
it has not been recreated or changed. Case harness/launcher, SQL modules, RLS gates and accepted reports
are unchanged. This section supplements historical NOT RUN records; it does not reattribute them.

### Reproduction and established scope

The operator's native Windows output was `remembered_rotation`, `cleanup`, then
`build_or_prerequisite`. A new task-owned loopback SQL Server 2022 Docker container reproduced the first
two failures with diagnostic-only instrumentation before changing identity assertions:

```text
AUTH_VERIFY FAIL remembered_rotation
AUTH_VERIFY DETAIL remembered_rotation jti_identity assertion
AUTH_VERIFY FAIL cleanup
AUTH_VERIFY DETAIL cleanup marker_identity guard
```

Rotation itself returned successfully; principal, expiry and replay checks passed before the JTI
assertion. Microsoft JDBC returns uppercase canonical UUID text whereas Java UUID.toString is lowercase.
The JTI assertion and independent cleanup nonce guard compared strings. They now parse canonical UUIDs
and compare the full UUID identity. Missing, malformed, different and duplicate marker rows still refuse
cleanup. The successful retest exercised every original authority/lifecycle/rotation/isolation assertion.
Cleanup still requires the creation flags, exact generated database and exactly one matching nonce;
login drops additionally verify each exact generated SQL login's SID captured immediately after creation.
No resource discovery or prefix deletion is added. Failure to mark/capture/verify retains resources.

Two independent reporting defects are fixed. Cleanup used to overwrite the original failure phase in
the final assertion; primary and cleanup failures now have separate immutable phase/step/category
records. The launcher used to append build_or_prerequisite to every SQL failure; it now reserves that
fallback for missing scenario evidence or a failing build without a known scenario failure. Cleanup-only
failure remains nonzero even after all three scenarios pass. Exact allowlisted events are deduplicated
and bounded; arbitrary lowercase child strings no longer qualify as safe output.

A separate non-UTC run established a production timestamp defect. Under America/Denver, rotation first
failed at `rotate`. After fixture seeds were explicitly bound as UTC, a UTC readback of the production
create result failed at `created_deadline`: default-calendar JDBC bindings stored a shifted datetime2
expiry. The resolver compares expiry to SYSUTCDATETIME, so local-wall-time values are incompatible.
SqlRememberCredentialStore now uses fresh UTC Calendars for **all nine timestamp writes and its expiry
read**, including resolver time, runtime revalidation, JTI/session update and credential update. This
scope changes no authority, SQL predicates, transaction boundaries, schema, grants or token contract.
It does not rewrite previously stored timestamps. Existing shifted rows cannot safely be inferred or
backfilled from this evidence; any repair requires separate evidence and scope. Fresh remembered
credentials must be checked through the normal login/enrollment path in later installed-client acceptance.

The fixture uses an exactly representable millisecond deadline and asserts exact Instant equality after
rotation, replacing the weaker millisecond projection comparison and avoiding datetime2(7) rounding of
arbitrary nanoseconds. Fixture session/negative-case timestamps are explicitly UTC. The fixture remains
synthetic: its table shapes support these actual production queries, but do not establish deployed
constraints, defaults, grants or complete-schema parity. No module or production identity defect was
established by the UUID failures. The UTC binding defect above is the sole production change.

Audit compatibility retains the existing schema and durable-session/security audit flows. This scoped
UTC correction and synthetic diagnostic tooling introduce no new domain/admin mutation or PHI view;
no ad-hoc audit event, audit migration or sensitive diagnostic payload is added.

### Closed diagnostics and operator action

`AUTH_VERIFY FAIL <phase>` is followed by `AUTH_VERIFY DETAIL <phase> <step> <category>`.
The primary event precedes any independent cleanup event. Details contain only enum/allowlist tokens;
no exception messages/classes, SQL states/codes, credentials, hashes, emails, connection strings, SQL,
payloads or unrestricted Maven/driver text are emitted. Do not enable debug/driver logging or share raw
Surefire/Maven output to diagnose a scenario. The final Surefire assertion contains only the same fixed
primary/cleanup summary, without original causes or suppressed exceptions.

| Phase / steps | Meaning |
| --- | --- |
| remembered_rotation: session_seed, create, created_deadline | Fixture session insertion, production credential creation, exact UTC expiry readback. |
| remembered_rotation: wrong_installation, rotate, principal, deadline, replay, jti_identity | Device rejection, eligible rotation, returned identity/deadline, old-hash replay rejection, exact session JTI verification. |
| remembered_rotation: revoke, revoked | Fixture revocation and revoked-session rejection. |
| remembered_rotation: disabled_seed/reject, removed_seed/reject, null_tenant_seed/reject, expired_seed/reject | Distinguishes each negative fixture insertion from its independent rejection assertion. |
| remembered_rotation: caller_restoration | Narrow auth caller remains non-bypass and context-free after resolver operations. |
| cleanup: pool_close, marker_identity, database_drop | Pools close; exact singleton database nonce must match before any database drop. |
| cleanup: auth_login_identity/drop, runtime_login_identity/drop | Exact original SID must match before dropping the respective generated login. |
| prerequisites: configuration; setup: resources/pools; credential_identity: credentials; runtime_isolation: isolation | Bounds failures outside remembered rotation. |

Categories are assertion, guard, sql_permission, sql_constraint, sql_timeout, sql_connection, sql_other
and unexpected. Wrapped SQL exceptions are inspected through at most eight causes; only fixed categories
are rendered. A category identifies a class of failure, not proof of its root cause. Keep both primary
and cleanup lines when reporting a failure. A build_or_prerequisite without known scenario failure is
build/prerequisite evidence only, not a SQL result.

### Retained resources from earlier failed cleanup

Leave the existing operator container running and intact. A new retest creates fresh random resources;
it neither adopts nor cleans resources from the failed run. Do not enumerate/drop databases by prefix,
prune containers, recreate the operator container, or treat a matching-looking name as ownership proof.
Earlier versions did not persist their expected nonce or login SIDs. If those independent original
creation identities are unavailable, leave the resources retained pending exact-resource operator review.
Reading a nonce from an unknown retained database and then accepting that same value is not verification.

Only an independently recorded exact prior database name plus expected nonce permits that database's
ownership check, requiring exactly one canonical UUID identity match. Missing/different/malformed/multiple
markers mean stop. Review only individually known resources; establish original SQL login identity from
trusted creation evidence/SID before any exact login removal, and check that it has no unrelated use.
Do not weaken guards or substitute names/prefixes for identity. This task provides no bulk cleanup or
automatic recovery of the previous operator run. Keep resource names/ownership evidence private; report
only the fixed outcome categories.

### Exact native Windows retest

From the PR branch repository root, use the existing dedicated SQL container on 127.0.0.1:14333 and a
fresh PowerShell terminal with installed Python/JDK 21/Maven. These commands do not remove any container.
Only the operator-confirmed inherited process secret is cleared; do not bypass other environment guards.

```powershell
Remove-Item Env:SHALE_AUTH_TOKEN_SECRET -ErrorAction SilentlyContinue
python --version
java -version
& 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD' -version
python -m unittest discover -s build/auth-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Auth launcher regression tests failed' }
& 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD' -pl shale-data,shale-server -am '-Dtest=AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,PreTenantAuthenticationModuleContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
if ($LASTEXITCODE -ne 0) { throw 'Focused auth regressions failed' }
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333 --maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'
if ($LASTEXITCODE -ne 0) { throw 'Auth SQL failed; retain both fixed failure/detail pairs' }
```

Enter only the dedicated local administrator credentials privately at the prompts. Successful completion
requires all three existing PASS scenario lines and exit code 0 after exact-resource cleanup. Record
only those fixed outcomes, tool versions, JVM timezone and exit code. Native Windows execution remains
an operator retest; Linux SQL results do not establish Windows/installed-client acceptance. Case SQL
remains NOT RUN; original schema/provenance commands and independent R4/security/deployment gates remain
in force. No Azure/live application connection, production database/configuration change, deployment or merge was performed.


### Executed verification and final impact review

Executed on Linux/Python 3.12.14, Maven 3.9.11, Temurin JDK 21.0.12.1 and SQL Server
16.0.4305.5 (2022 container image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`).
The operator's Windows/JDK versions remain a separate retest. `mvn` below denotes the existing
`/workspace/.tools/bin/mvn` toolchain wrapper/settings, with the inherited proxy and CA trust.
No repository dependency/build configuration changed. SQL runs captured/discarded unrestricted output;
only fixed outcomes were recorded. The new task-owned container was removed only after verifying its
captured exact container ID and task label; its private credential file was erased. The operator
container and any resources from the operator's earlier failed cleanup were not accessed or removed.

Pre-edit impact review covered the existing store binding/identity tests, complete migration contract,
module/auth/lifecycle tests, opt-in fixture, both launcher safety suites and downstream auth/session
contracts. The existing migration contract's default-calendar source expectation was obsolete under the
established UTC contract and now requires the UTC timestamp overload; schema/hash/ownership assertions
remain intact. New executing JDBC-double coverage verifies all nine UTC writes, the UTC expiry read,
returned identity/deadline and the joint commit under Denver and Kolkata defaults. Test-only diagnostic
coverage verifies primary plus cleanup attribution, cleanup-only failure, message/cause exclusion,
closed categories, canonical UUID cardinality and exact original login SID matching. Python coverage
checks unknown/injected tokens, duplicate/missing PASS lines, cleanup after PASS and fallback behavior.

Changed-file mapping: production store → SqlRememberCredentialStoreTest, migration contract and real
opt-in SQL; Java fixture/support → AuthVerificationSupportTest and real SQL; Python runner → existing
cross-launcher tests and extended test_runner; runbook → fixed vocabulary, commands and evidence checks.
The committed base-to-head selector maps changes to security-data/server plus directly modified tests:
auth eligibility/module authority, tenant/audit transactions, request context and auth controller are
relevant dependencies. It recommends **no full-suite escalation**; no unrelated historical tests or
CI workflows were enabled. Final searches found no default-calendar calls in the changed store, stale
string UUID guards, or old timestamp-binding expectations in the affected tests.

Exact successful local check commands (SQL credentials were supplied only in a private child environment):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
mvn -o -pl shale-data,shale-server -am -Dtest=AuthVerificationSupportTest,SqlRememberCredentialStoreTest,PreTenantAuthenticationLocalSqlServerTest,AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,DesktopRememberCredentialMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 30ca63c9d0b9ceefceb4ae3dd057b16376b42080 --head HEAD --format markdown --plan-output /workspace/work/auth-diagnosis/selection.json
mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.DesktopRememberCredentialMigrationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.controller.AuthControllerTest,com.shale.server.runtime.AuthVerificationSupport,com.shale.server.runtime.AuthVerificationSupportTest,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest,com.shale.server.runtime.SqlRememberCredentialStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B test
# Private task helpers invoked only our opt-in harness / real quiet launcher:
python /workspace/work/auth-diagnosis/run_harness.py semantic-uuid
python /workspace/work/auth-diagnosis/run_harness.py utc-store-denver denver
python /workspace/work/auth-diagnosis/run_launcher.py
git diff --check 30ca63c9d0b9ceefceb4ae3dd057b16376b42080 HEAD
```

The private helpers created no arbitrary URL/schema/resource inputs; they used the existing harness's
LOCAL_DISPOSABLE_AUTH, loopback port and dedicated local credentials. Native Windows reproduction uses
the exact public launcher commands above, not these private helpers. Initial default-calendar Denver
SQL failures and the pre-fix UUID/cleanup failures were diagnostic evidence, not passing verification.
An initial offline dependency miss was resolved through the configured proxy; an overlapping Maven
attempt returned no scenario evidence and was repeated sequentially. Only sequential runs are counted.

| Check | Result and limits |
| --- | --- |
| Auth/cross-launcher Python | 17 PASS, 1 explicit native-Windows skip; actual POSIX invocation, simulated Windows boundaries. |
| Existing Case launcher / selector units | 4 PASS / 24 PASS. No Case SQL. |
| Focused Java | 23 PASS, 1 explicit opt-in SQL skip; auth/modules, store, diagnostics, migration bindings. |
| Committed selector affected suite | 77 PASS, 1 explicit opt-in SQL skip; security-data/server and dependencies. |
| Critical reactor | 116 PASS, 0 failures/errors/skips; BUILD SUCCESS. |
| Disposable SQL, semantic UUID / UTC JVM | All three scenarios PASS; exact-resource cleanup and exit 0. |
| Disposable SQL, complete fix / America/Denver JVM | All three scenarios PASS; UTC readback, original guards, SID cleanup and exit 0. |
| Real quiet launcher / disposable SQL | Exactly the three allowed PASS lines, cleanup successful, exit 0. |
| Diff/scope/docs | PASS; Case harness/launcher, SQL modules/migrations, accepted R4 reports unchanged; original export absent/unmodified. |

**SYNTHETIC AUTH SQL PASS on Linux in UTC and America/Denver; native Windows retest pending; CASE SQL NOT
RUN; installed-client and production authority UNVERIFIED; deployment BLOCKED; security acceptance OPEN.**
The passing disposable fixture does not authorize targeted RLS attachment, production grants or cleanup
of unknown retained resources.
