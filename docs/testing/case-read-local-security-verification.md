# Minimized Case reads: disposable local SQL verification

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

Next bounded step: obtain/review an approved offline schema-only snapshot, run the exact launcher on
dedicated local SQL, record fixed scenario outcomes and version/schema/source identities, and isolate
any actual schema/RLS/DAO defects for follow-up. A passing run establishes local integration only.
Deployed Azure predicate/grant parity, representative plans/scanned-work/collation/concurrent paging,
successful audit viewer meaning, live browser expiry/revocation/uncertainty, host/CORS/logging/privacy/
error no-store and broader device/AT acceptance remain separate gates. Phase 2 OPEN; Phase 3 IN PROGRESS;
Phase 4 INCOMPLETE; appearance provisional. No merge/deployment authorized by this tooling.
