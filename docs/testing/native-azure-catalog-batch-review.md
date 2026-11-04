# Catalog batch authority and execution review

**Post-merge status — 2026-11-04:** #1929 merged at
`1f12091aef3275721c1e35d6ba00a2ecb80c5173`. The
[final diagnostic closeout](native-azure-diagnostic-closeout.md) stops further Azure diagnosis;
earlier retry, pending-validation and merge-hold instructions below are historical.
Native acceptance remains OPEN. See the [ordinary dormant release gate](../dormant-server-release-compatibility.md);
no SQL, Azure, deployment or activation is authorized by this status correction.

**Final operator disposition — 2026-11-04: Azure-specific diagnosis stopped.**
The [closeout and deployment decision](native-azure-diagnostic-closeout.md) supersedes all
retry/readiness-to-run instructions below. Full disposable Azure setup repeatedly passed through V2,
binding, cumulative verification and OFF check. The remaining failure is
`VERIFIER_COMPONENT_READ; component=0:before_first_component; phase=BATCH_EXECUTE`.
The unchanged batch passes locally with exactly six verifier grants and denied application SELECT;
no permission defect/change is established or justified. The 5000-ms socket / 20-second query
preemption hypothesis remains an inference only. Launcher and tiny-probe Windows validation passed;
the tiny probe at `871919c263b650bd7dc805fef16cee7ddac35769` did NOT run in Azure.
Only production `shale` was discovered; the operator declined a dedicated nonproduction server/probe.
Native catalog/read-only and mutation/fault acceptance remain OPEN; #1929 merged into codex/latest on 2026-11-04; native acceptance remains OPEN.
No production SQL/permissions/deployment or AI/MCP/OAuth enablement occurred. Historical packets and
pending-at-the-time Windows statements below retain their original dates/scopes; they are not next steps.

Reviewed against executable `bec7ecbac86a2026bc16dac7a2e8963d7467dd4e` and packet
`69f4adc08a77b0a01c3adbaed91985c90555d11c`. No Azure executed or authorized.

## Findings

The preserved `VERIFIER_COMPONENT_READ; component=0; phase=BATCH_EXECUTE` locates the JDBC call,
not a particular SQL statement, permission denial or error category. The owner completed the same
batch, and verifier connection/preflight/statement creation/timeout setup passed. The exact batch
also passes locally under a database-confined restricted principal with only the six existing
verifier grants. No missing grant or production SQL defect is established. Do not broaden V2 authority.

The leading remaining hypothesis is a slow first response hitting the verifier's **5000-ms socket
timeout**, potentially before its **20-second query timeout**. The owner has a **120000-ms socket
timeout**. Neither setting changes. This is an inference, not a retrospective diagnosis: the public
exception intentionally discarded the original SQL error category and elapsed time. Compilation, permission, execution, query cancellation and socket failure can all surface during `execute()`.

The definite diagnostic defect is the launcher: its Maven invocation catch threw `REPORT_WRITE`
before reaching the safe XML parser. Under Windows PowerShell, native stderr with terminating error
preferences can take that route even when Surefire produced the real failure. The fix runs only the
native invocation in a child scope with nonterminating stderr handling, suppresses all streams,
captures the exit status, restores parent preferences by scope and always reaches the existing
allowlisted parser. A missing executable/no fresh report still yields sanitized `REPORT_WRITE`.
No raw exception, SQL, XML, credential, target, SID or token is forwarded.

## Exact statement/reference inventory

There are no application reads besides the three explicitly granted reference tables. Owned object
names in `@Owned` are filters/literals, not executable object references. In particular, the batch
does not SELECT `AiCaseDataDeployment`; its existing grant is unused here.

| Statement/component | References and operations | Existing authority assessment |
| --- | --- | --- |
| Preamble | `SET NOCOUNT`; declare `@Owned` with `sysname`/PK; five INSERT statements; declare local `nvarchar(max)` | Session-local variables and table-variable DML; no persistent CREATE TABLE/INSERT privilege needed. No tempdb/control grant needed. |
| 1 objects/modules | `sys.objects`, `sys.sql_modules`, `SCHEMA_ID('dbo')` | Database VIEW DEFINITION covers object/module visibility; reading definitions does not execute modules. |
| 2 columns | `sys.columns`, `sys.computed_columns`, `OBJECT_NAME` | Metadata visibility covered by database VIEW DEFINITION, including computed definitions. |
| 3 constraints | `sys.objects`, `sys.check_constraints`, `sys.default_constraints`, `sys.foreign_keys`, `OBJECT_NAME` | Same database metadata authority. |
| 4 FK columns | `sys.foreign_key_columns`, `OBJECT_NAME` | Same; no SELECT on the referenced application table is needed. |
| 5 indexes | `sys.indexes`, `sys.index_columns`, `OBJECT_NAME` | Same, including filter definitions; no application SELECT/ALTER INDEX required. |
| 6 security predicates | `sys.security_predicates`, `sys.security_policies`, `OBJECT_NAME` | VIEW DEFINITION on the policy suffices; the database grant covers it. ALTER ANY SECURITY POLICY is an alternative, not a required addition. No predicate-function execution grant is needed for metadata reads. |
| 7 object permissions | `sys.database_permissions`, `OBJECT_NAME` | VIEW DEFINITION; newer VIEW SECURITY DEFINITION is implied by database VIEW DEFINITION. No extra security grant is justified. |
| 8 triggers | `sys.triggers` | Database VIEW DEFINITION covers metadata. No trigger EXECUTE permission. |
| 9 integration role owners | `sys.database_principals` | Database metadata authority covers the role/principal definitions; no ALTER ANY ROLE/USER needed in this fixture. |
| 10 filter definition | `OBJECT_DEFINITION(OBJECT_ID('sec.fn_FilterByTenant'))` | VIEW DEFINITION covers the definition; does not call the filter function. |
| 11 runtime/verifier permissions | `sys.database_permissions`, `DATABASE_PRINCIPAL_ID`, `OBJECT_NAME` | Same security-definition coverage as component 7; local restricted equality test confirms it. |
| 12 execution/schema owners | `sys.objects`, `sys.schemas`, `sys.sql_modules` | Database metadata authority; owner/execute-as fields are read without impersonating their principals. |
| 13 dependencies | `sys.sql_expression_dependencies`, `OBJECT_NAME` | Requires database VIEW DEFINITION **and explicit SELECT** on this view. Both are already granted. No additional permission is missing. |
| 14 runtime role owner | `sys.database_principals` | Same as component 9. |
| 15 verifier role owner | `sys.database_principals` | Same as component 9; own role is also visible through membership. |
| 16 database options | `sys.databases`, `DB_ID()` | Only the connected database is selected. That row is visible to its connected user; no server-wide VIEW ANY DATABASE/STATE grant. Azure cross-database enumeration is not used. |
| 17 Unicode ranges | `dbo.AiDataUnicodeRange` | Existing object SELECT grant; VIEW DEFINITION alone would not suffice. |
| 18 Unicode lower | `dbo.AiDataUnicodeLower` | Existing object SELECT grant. |
| 19 word transitions | `dbo.AiDataWordTransition` | Existing object SELECT grant. |
| Each expression/emitter | `CONCAT`, `COALESCE`, `CONVERT`, `FOR XML PATH(...),TYPE`, XML `.value`, `DATALENGTH`, `HASHBYTES('SHA2_256',...)`; fixed ordinal/name SELECT | Built-in scalar/XML operations on already authorized inputs. No user-defined XML schema, application procedure/function execution, dynamic SQL, credential or external-source reference. |

This lists all 18 `sys.*` views and all three data tables referenced by the actual resource.
Metadata visibility is not data permission: hidden rows/NULL definitions must not be interpreted as
complete evidence, and explicit DENYs could alter effective visibility. None is established by the
fresh setup evidence. The local test checks no CONTROL/db_owner and denied application SELECT.

Microsoft's Azure-applicable metadata guidance permits database-scoped VIEW DEFINITION. SQL Server
2022's narrower VIEW SECURITY DEFINITION is **implied by** VIEW DEFINITION, not an extra grant to add.
The SQL expression dependency view is the notable explicit-SELECT exception, already addressed.

## Compilation, buffering and timeouts

A batch is not a client-visible sequence of independently submitted requests. Parsing/compilation
can reject a later statement before an earlier SELECT is delivered; statement-level/deferred
compilation also exists, so the reverse inference is unsafe. With pinned JDBC 12.6.1, a local batch
`SELECT 1; SELECT nonexistent_probe_column FROM sys.objects` throws engine error 207 from `execute()`
before returning the first result. A later denied application SELECT, however, returned the first
result and then error 229 during traversal. Do not assert that every later permission denial is
eagerly checked, or that component 13 is responsible just because it has a special SELECT grant.

The driver defaults to adaptive buffering, not guaranteed one-statement-at-a-time execution.
`setQueryTimeout(20)` bounds the post-send wait for the server reply; server compilation, waits and
execution before the first response consume that window. A local `WAITFOR` before SELECT proves
20-second query cancellation can be thrown by `execute()` (HY008). A separate connection with the
existing verifier-like 5000-ms socket bound fails the same call before an eight-second WAITFOR
completes (08S01), despite the unchanged 20-second statement limit. Socket failure closes the
connection and can preempt query cancellation. No timeout is increased in existing code.

## Local evidence and limits

`AzureCatalogAuthorityLocalSqlServerTest` creates tiny nonce-named loopback SQL Server databases,
not the private schema or full V2 fixture. It copies exactly the installer's six grant statements;
uses `EXECUTE AS USER` for a WITHOUT LOGIN principal whose only custom role is the verifier role;
asserts no db_owner/CONTROL, implied VIEW SECURITY DEFINITION, denied application SELECT, visible
security predicate and dependency; and compares all 19 unchanged length/hash rows with the owner.
This is database-authority evidence, not proof of Azure contained authentication or S0 timing.

The existing full local fixtures use a catalog login granted server VIEW ANY DEFINITION; owner
installer tests and JDBC mocks did not establish this narrower database-only batch behavior.
The new fixture needs a synthetic FK, trigger and one row per reference table: empty XML scalar
expressions can produce NULL metadata, correctly rejected by the unchanged collector. That separate
missing-fixture condition appears after a component is exposed and does not explain live BATCH_EXECUTE.

Five real SQL Server 2022 tests passed with JDK 21/JDBC 12.6.1/cached JUnit: exact batch authority,
later compile error, later permission denial, 20-second query cancellation, and 5-second socket
failure. All tiny databases were removed after each test. No production SQL, grant, receipt,
catalog expression, datasource or timeout changed. Audit compatibility: synthetic local metadata
and test rows only; no PHI/entity mutation or application audit migration.

## No more full fixture for this investigation

No further 3–4 minute Azure fixture is needed to investigate metadata authority or these execution
semantics. The destroyed fixture is not adopted/recreated. There is no permission change to deploy.
Do not alter/split the catalog expressions merely to guess a cure for an unclassified timeout.

If an Azure-specific uncertainty must still be resolved, separately authorize **one tiny probe**:

1. Use a fresh nonce database on a dedicated nonproduction test server, with independently generated
   contained test-user credentials and exact owned-resource cleanup. No production target or full
   schema import, prerequisite migration, Phase1R, V2 installation, binding or receipt.
2. Create only the tiny synthetic objects/reference columns used by the local test. Copy the six
   existing grants into a probe role; add only the fresh probe user. No additional metadata/data
   permission. Seed one FK/trigger/reference row so empty categories do not cause unrelated NULLs.
3. Before any owner batch warms the cache, submit fixed tiny checks as the restricted user: current
   database row; security policies/predicates; database permissions/principals; dependencies; then
   the exact component-1 expression over synthetic owned objects. Keep existing 5-second socket and
   20-second query bounds. Submit each check once; no retry/fallback/permission adaptation.
4. Record only fixed check ID, PASS/fixed error category, elapsed milliseconds and whether the
   connection closed. Distinguish known permission/binding error codes, query cancellation and socket
   timeout internally without printing exception messages, SQL, identifiers, causes or suppressed
   exceptions. Preserve privately only bounded sanitized results. A tiny successful permission probe
   cannot prove the full prior schema's performance; it can eliminate a claimed Azure permission gap.
5. Drop the exact new database/user scope in finally and confirm absence. Do not run the old live
   launcher, restore its markers, or escalate permissions based on failure. Preparation is not approval.

## Verification and handoff

Local checks: five real SQL Server tests pass; five Python catalog guards pass; network-disabled
PowerShell 7.4 on Linux passes `RUNBOOK_DIAGNOSTICS PASS cases=208 azureExecuted=false`, the
unchanged credential fence and the unchanged 18-scenario launcher fence. The diagnostic script
executes the actual invocation and parser/catch blocks with synthetic reports, not a copied parser.
Native Windows PowerShell 5.1/7 execution of the revised invocation fence remains required; the
Linux run is supplemental. No full Maven, Phase1R, V2 or Azure setup rehearsal is needed.

For the revised launcher on the owner's Windows host, run:

```powershell
python -m unittest discover -s build/azure-native -p test_catalog_components.py
$PowerShellExe = (Get-Process -Id $PID).Path
foreach ($Test in @('test_runbook_diagnostics.ps1','test_runbook_credentials.ps1','test_runbook_launcher.ps1')) {
    & $PowerShellExe -NoProfile -File (Join-Path 'build/azure-native' $Test)
    if ($LASTEXITCODE -ne 0) { throw 'Offline invocation/parser/fence check failed' }
}
```

The new local SQL test is independently runnable with a dedicated loopback SQL Server owner,
`SHALE_CATALOG_SQL_OPT_IN=LOCAL_CATALOG_AUTHORITY`, `SHALE_CATALOG_SQL_PORT` and
`SHALE_CATALOG_SQL_PASSWORD` in the process environment, selecting only
`AzureCatalogAuthorityLocalSqlServerTest` through Maven. It requires no private candidate and
refuses Azure editions. It creates/drops nonce test databases only. Existing Maven dependency
resolution remains unavailable here; executed SQL/JUnit checks used the cached real dependency
closure with JDK 21. The 20-second WAITFOR timeout test takes about twenty seconds by design.

New live sections 2/3/5 digest (only the diagnostic invocation changes section 3):
`02b611cdc6546c40c9431003c273df7b315e62e161cb5c976da534100dc4f021`.
No executable baseline or consumed marker is updated/reused. The previous protocol deliberately
refuses the changed diagnostic script/new SQL test; packet refresh is separate reviewed work.

## Sources

- [Metadata visibility configuration](https://learn.microsoft.com/en-us/sql/relational-databases/security/metadata-visibility-configuration)
- [GRANT database permissions and implication hierarchy](https://learn.microsoft.com/en-us/sql/t-sql/statements/grant-database-permissions-transact-sql)
- [sys.database_permissions](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-permissions-transact-sql)
- [sys.sql_expression_dependencies](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-sql-expression-dependencies-transact-sql)
- [sys.security_predicates](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-security-predicates-transact-sql)
- [sys.security_policies](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-security-policies-transact-sql)
- [sys.databases](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-databases-transact-sql)
- [SET NOEXEC and compilation/execution](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-noexec-transact-sql)
- [JDBC timeout semantics](https://learn.microsoft.com/en-us/sql/connect/jdbc/understand-timeouts)
- [Adaptive buffering](https://learn.microsoft.com/en-us/sql/connect/jdbc/using-adaptive-buffering)
