# Authentication module permission cutover — 2026-10-15

**Latest blocker:** the operator identified three `sp_login_*` EXECUTE grants after #1872.
The [current assessment and one bounded dbo query](#identified-1872-blocker-three-legacy-login-executes--2026-10-15)
keep installation blocked pending deployed definitions/authority and accounting/caller evidence.

**Review candidate; deployment BLOCKED.** Base `7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0`
was fetched from live `codex/latest`; merged #1868, #1869 and #1870 were verified with GitHub metadata
and Git ancestry. Separate branch: `codex/auth-module-permission-cutover`.
The [Case security runbook](case-read-local-security-verification.md) retains the original schema evidence,
Case verification procedure and historical execution records. This document owns the permission candidate.
No Azure connection, live grant/migration, client release, configuration change, merge or deployment is
part of this task. No three-table FILTER or other predicate is added/removed. Original schema export,
Case harness and accepted R4 reports remain unchanged; no substitute export is generated.

## Attributed operator evidence

Reported by the user in the October 15 task, not executed by Codex against Windows/Azure:

```text
PASS credential_identity
PASS remembered_rotation
PASS runtime_isolation
Exit code 0
```

This closes the requested **native Windows synthetic auth SQL retest** after #1870, including its
reported successful exit. Tool versions, JVM timezone and exact Windows source/artifact hashes were not
supplied; none is invented. It does not establish this new permission candidate's Windows execution,
installed application compatibility, complete production-schema parity or security acceptance.

User-run Azure Shale metadata/effective-permission evidence:

| Observation | Consequence |
| --- | --- |
| Database collation `SQL_Latin1_General_CP1_CI_AS` | Case-insensitive identifiers resolve AuditLog/auditLog; exact email comparisons retain this collation. No collation/index/data rewrite. |
| `shale_app`: no db_owner/ShaleAdmins; Users SELECT, old resolver EXECUTE, database CONNECT | Current capability supports older password/remembered consumers. Catalog names do not establish actual server/desktop configuration mapping. |
| `shale_runtime`: no db_owner/ShaleAdmins; member `shale_runtime_role` | Both principal and role need an explicit module fence. |
| Runtime and role: dbo schema CRUD; runtime database EXECUTE, role dbo schema EXECUTE | Installing dbo owner-executed procedures alone exposes them to runtime. An object REVOKE does not remove inherited EXECUTE. |
| Runtime effective Users.password_hash SELECT/UPDATE and auditLog SELECT/INSERT/UPDATE/DELETE | Hash disclosure and mutable audit history are real permission findings. Read FILTERs alone would not repair either. |
| V1/V2 absent; old resolver caller-executed, dbo-owned | New-name installation can preserve all old object definitions/ACLs and their grantors/states. |
| Users/auditLog/Statuses have no RLS predicates | Confirmed gap persists; this task must not attach the three FILTERs. |

**Actual deployed server and desktop auth/runtime credential configuration is still UNCONFIRMED.**
No independently approved security-owner acceptance of dbo module authority/internal hash access is implied.

## 58307 investigation after #1871 — 2026-10-15

**Operator-reported evidence, not a Codex Azure/Windows execution:** the expanded native Windows
fixture produced all three PASS lines and exit 0; prior-state capture succeeded with exit 0 and is
retained privately. Azure preflight stopped at 58307. On the actual `shale_app` connection, database
CONTROL, ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, dbo schema ALTER/EXECUTE, dbo user IMPERSONATE and
database EXECUTE all returned 0. Auth has Users SELECT, old resolver EXECUTE, CONNECT and an explicit
database VIEW DEFINITION DENY, without db_owner/ShaleAdmins. **Stage A has not been installed; employees
still use desktop 1.0.136.** Preserve that captured state and the original schema; neither is regenerated
or committed here. The expanded Windows report supersedes the pending #1871 fixture retest for that
reported revision only; this diagnostic revision has not been run natively on Windows by Codex. These
observations do not enumerate effective column permissions or every token path.

**Disposable result:** the reported auth grants **including VIEW DEFINITION DENY** pass preflight and
the existing install/postcheck/restrict/rollback fixture on SQL Server 2022. DENY alone does not reproduce
58307. No Azure root cause or extra live permission is established; no live permission change is proposed.
The new output names the decision inputs so the operator can distinguish an actual extra capability from
UNKNOWN evidence. This is a diagnostic/tooling change, not security acceptance or cutover authorization.

Every auth condition formerly combined in the 58307 OR list now contributes to one shared diagnostic
relation and shared fail-closed decision. It checks:

| ConditionName | Authority tested / reported securable |
| --- | --- |
| role / fixed-role | db_owner, ShaleAdmins, every fixed database role in the effective token except public |
| database / database-permission | ALTER ANY USER/SCHEMA/SECURITY POLICY/ROLE, CONTROL, EXECUTE; every fn_my_permissions database capability outside CONNECT and the two existing column-encryption metadata exceptions |
| dbo-schema / dbo-user / impersonate-user | dbo ALTER/EXECUTE, dbo IMPERSONATE and other impersonatable database users; roles are not USER securables |
| ownership | schema and explicit object owners matched against the complete effective token |
| grant-option | every W-state permission matched to the token, including class/major/minor IDs, entity/column where applicable, grantee and original grantor |
| column | every dbo-inventoried column UPDATE, and every non-Users column SELECT |
| object | ALTER/CONTROL/INSERT/UPDATE/DELETE, non-Users SELECT and EXECUTE outside the three existing resolver/V1/V2 exceptions; includes callable elevated/signed/encrypted modules without printing bodies |

Token output includes public and nested roles. Ownership, grant-option, principal, object and column
inventories are captured under dbo before impersonation; filtered caller catalogs are not proof of absence.
Database VIEW DEFINITION's raw effective value and matching token ACLs are context, not a requirement to
grant metadata visibility. Effective 1 is GRANTED, 0 is NOT_GRANTED, SQL NULL is UNKNOWN. Any GRANTED guard
input or UNKNOWN stops with 58307. No unknown is coerced to a safe zero or mislabeled a grant. The database
user test still does not replace actual-login/server-token review. Serialize metadata changes during probes.

Output is metadata only: no application query, hash, SID, secret or module body. It contains at most 200
finding details and 200 token rows, grouped finding counts, total counts and explicit truncation flags.
Truncation leaves the investigation incomplete; retain private output and narrow the affected condition in
a separately reviewed read-only follow-up before drawing conclusions. No broad permissions should be
removed to make the count disappear. Dbo-known absence of the optional ShaleAdmins role is distinguished
from a NULL membership result for an existing role.

### Next bounded operator query and exact retest commands

From repository root, on the **existing approved dbo metadata connection**, run this read-only diagnostic
before proposing permission changes. It does not require new security-approval acknowledgements and does
not include capture, installation or restriction scripts:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/diagnose-auth.sql -v ExpectedDatabase=Shale -o $privateAuthDiagnosticOutput
$authDiagnosticExit = $LASTEXITCODE
# A nonzero exit with 58307 is expected when GRANTED/UNKNOWN findings exist. Stop and review them.
if ($authDiagnosticExit -ne 0) { throw 'Stop: review bounded auth diagnostics; do not change permissions or install' }
```

`diagnose-auth.sql` is the bounded read-only query artifact: only SET, temporary catalog snapshots,
EXECUTE AS/REVERT and metadata/effective-permission queries. It works before Stage A. It reports the same
auth inputs as preflight and restores dbo before reporting/throwing. Preserve the prior-state capture as-is;
do not rerun capture merely to replace the original failure evidence. Share only approved condition,
securable, permission, outcome and truncation metadata, never retained private captures/module definitions.
If all inputs are zero, compare the exact script revision, selected database and connection identity with
the failed run; do not assume that permission state or original-login authority is unchanged.

After reviewing the diagnostic, retest only the read-only preflight with the **previously evidenced**
review acknowledgements (do not assert unapproved decisions):

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightRetestOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight still blocked' }
```

No Stage A/B, release or permission-change command is authorized by this investigation. The native Windows
synthetic retest uses the launcher commands in the Windows section below; use a dedicated local disposable SQL
instance, never Azure. Installer, restriction and rollback paths include the same preflight. Existing
module postchecks already reject NULL EXECUTE with fail-closed COALESCE; read postchecks reject NULL forbidden
column access and NULL required writes. The expanded fixture exercises these paths under the metadata DENY.

## Caller review and bounded decision

### Identified #1872 blocker: three legacy login EXECUTEs — 2026-10-15

**Operator-reported Azure result:** `shale_app` has database VIEW DEFINITION DENY; the effective token
contains `public` and `shale_app`. The diagnostic reports 3299 NOT_GRANTED, 3 GRANTED and zero UNKNOWN.
The three findings are object EXECUTE on `dbo.sp_login_get_hash`, `dbo.sp_login_mark_failure` and
`dbo.sp_login_mark_success`; SQLCMD exits 16 / error 58307. This identifies the guard inputs, not their
definitions, grantors, execution authority or safety. It supersedes the earlier unresolved-condition
statement above. **Stage A remains NOT installed, desktop 1.0.136 remains in use, installation BLOCKED.**
Codex has made no Azure connection or live change.

Fetched live `codex/latest` at `cfc62956b209265d7c56829b5e8e1af3773c99c1` (merged #1872), with complete
available Git history; separate task branch `codex/1872-legacy-auth-evidence`. The initial sandboxed
Git fetch could not reach its proxy. A GitHub API fetch verified the complete base tree and signed commit;
the subsequent approved shell fetch/unshallow confirmed that same live base and supplied history.

| Evidence reviewed | Finding and limit |
| --- | --- |
| Current tracked repository and available history, exact searches for all three module names | No definitions or callers found before this investigation. The schema reference names `failed_attempts`, `lock_until` and `last_login`, but supplies no accounting algorithm or module authority. No original schema export is available in this task workspace; none was regenerated or inferred. |
| Release commit `c2c77830` (`Release Shale 1.0.136`), `shale-data/.../auth/AuthServiceImpl.java` | Direct parameterized `SELECT TOP 1` from Users by email with `is_deleted=0`; `password_hash AS pw_hash`; bcrypt verification in Java. No lockout check, failure increment, success reset or last-login update in this path. Its retry counter is connection retry only. |
| Released `DesktopUiAuthService.login`, shared auth adapter and server auth controller | Desktop delegates to the shared auth service, then stages the separate server credential exchange. No `sp_login_*` call is present. This is source evidence; installed binary hashes, actual configured identities and external clients/jobs remain unverified. |
| Current `AuthServiceImpl` and `LookupAuthenticationCredentialV1` | New bounded credential lookup plus Java bcrypt; no new lockout/accounting algorithm. Neither current nor released source proves that deployed modules/triggers/external consumers are unused. |
| Ownership/execution/permission authority of the three Azure procedures | **Unknown.** dbo schema/name does not establish explicit object owner, CALLER versus OWNER/named execution, signatures, chaining, parameter semantics or grantor/state. Even CALLER modules can reach same-owner data through ownership chaining. |

**Decision:** these are unreviewed executable capabilities. Evidence does not yet support classifying
them as necessary narrowly scoped legacy capabilities, obsolete grants, or unsafe implementations.
Do not whitelist by name, revoke them, edit their bodies, or relax 58307. No production permission or
authentication correction is justified yet. Preserve deployed lockout/failure/success behavior by making
no change to it. Repository absence is not permission-removal evidence.

#### One bounded read-only deployed-evidence query

On the existing approved **dbo metadata connection**, from repository root, the operator can run:

```powershell
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/inspect-legacy-login.sql -v ExpectedDatabase=Shale -o $privateLegacyLoginEvidence
if ($LASTEXITCODE -ne 0) { throw 'Stop: legacy module evidence incomplete; installation remains blocked' }
```

This single script performs only catalog reads, session SET and temporary metadata tables; it does not
impersonate, invoke modules, query application rows, alter permissions, capture a replacement prior state,
or install anything. Full Unicode definitions and SHA-256 hashes are private review evidence. Parameter
types/directions/lengths are returned, never parameter default values. Module owners (explicit, schema,
effective), CALLER/OWNER/named execution, SET flags, signer identities/thumbprints without keys/signature
bytes, direct inbound/outbound dependency metadata, related owners/triggers, complete database-principal
and nested-role context, and exact applicable permission class/major/minor IDs, G/W/D/R states,
grantors and grantees are included. ACLs cover database, related schemas/objects/columns, USER/type
authority and all explicit grants to auth/runtime, module owners/execution identities/signers and their
nested roles, including authority beyond direct dependencies. Fixed-role and dbo authority is not
limited to explicit ACL rows. Server/login authority still needs the existing actual-connection review.

Bounds are explicit: three bodies of at most 1 MiB each, 200 dependency records/related objects/signatures/
triggers, 6303 parameter rows, 1000 principals/membership rows each and 5000 relevant permission rows.
Over-limit evidence stops with 58330 rather than truncating. Missing/encrypted/non-T-SQL modules report
their status then stop with 58331. SQLCMD must retain complete `-y 0 -Y 0 -u` output. Serialize catalog
changes during collection. An exit 0 means collection succeeded, **not preflight approval**. Definitions
may contain sensitive embedded literals; review/redact privately before sharing. No application rows,
credential values from application storage, SIDs, key material or parameter defaults are returned.

#### Evidence/action required before any correction

1. Review those deployed bodies/parameters/SET flags, explicit/effective owners, execution identities,
   signatures and exact ACL/grantors. Trace every dependency, synonym, dynamic SQL, trigger and external
   caller; catalog dependencies are not a complete dynamic/external call graph. Obtain separately bounded
   private evidence for unresolved/transitive bodies or trigger behavior rather than assuming absence.
2. Establish installed 1.0.136 artifact/configuration mapping and jobs/older consumers. Verify how credentials
   are selected and how failed attempts, threshold, duration, expiry, deleted/removed users, successful login,
   last-login updates and concurrent attempts are accounted for. No live authentication execution is requested
   by this metadata step. Obtain approved behavior evidence; do not invent a lockout policy from names.
3. Classify each module. If necessary and safe, a subsequent correction must pin independently reviewed
   deployed definitions/SET semantics, parameters, owners, execution/signature authority, exact ACL states/
   grantors and permitted caller/role closure; reject drift, broad or grant-option authority and unexpected
   dependencies. Reproduce the **actual reviewed behavior** in disposable SQL, including lockout boundaries,
   concurrent failure/success accounting and released-client compatibility, before permitting a capability.
   If obsolete or unsafe, obtain a separately reviewed consumer migration/drain and exact-state permission/
   module correction with rollback. No blanket REVOKE or guessed GRANT replay is acceptable.
4. Keep the original private prior-state capture and original schema export unchanged. Supplement them with
   this timestamped evidence; never overwrite them. Any later authorized change requires its own exact
   delta, independent baseline hashes/signature and grantor/state constraints, drift refusal, failure atomicity
   and exact rollback. Recheck deployed metadata and actual auth/runtime connections before Stage A.

Stage B still requires complete caller inventory and compatible installed-fleet/job drain, including these
three modules if used. A Stage B table SELECT fence alone must not be claimed to block a same-owner procedure's
internal read or update. Any admitted legacy path requires an explicit Stage B disposition and regression tests.
The current candidate remains blocked with these grants; none of its staged scripts, prior-state capture,
schema export or RLS gates has been changed. The existing rollback commands do not authorize modifying these
three deployed procedures. **This revision's rollback is reverting its query/test/documentation commit;
there is no database delta to undo.**

#### Test impact and verification boundary

Pre-edit review covered `AuthCredentialLookupTest`, `AuthUserLifecycleSecurityTest`,
`PreTenantAuthenticationModuleContractTest`, `AuthPermissionCutoverFixture`,
`PreTenantAuthenticationLocalSqlServerTest`, `SqlRememberCredentialStoreTest`, remembered migration,
User creation/removal/projection tests, the auth launcher tests and the selector's security-data inventory.
Only the fixture, new metadata script and this runbook change. Shared login, bcrypt, account lifecycle,
password-management SQL, historical installers, capture and RLS definitions are unchanged.

The disposable fixture creates all three exact names with explicitly **synthetic** parameterized bodies,
grants each EXECUTE as dbo, and calls them under the metadata-denied auth principal. It requires diagnostic,
preflight and installer to reject all three at 58307 before module installation; harmless-looking names/bodies
are never adopted. It exercises the new dbo collector, full definitions/hash/parameters/owners/ACL grantors,
missing-module rejection, execution/definition drift, and certificate signer authority beyond dependencies.
It then removes only its synthetic probes and compares exact original permissions/memberships/predicates
before the existing staged roundtrip and credential/remembered/isolation checks. These probes reproduce the
grant blocker and collector behavior; **Azure module behavior/lockout reproduction remains NOT RUN until
the deployed definitions and behavioral evidence arrive.** Synthetic counts vary with fixture schema and
are not asserted equal to Azure's 3299 negative checks. Native Windows execution of this revision remains
NOT RUN. No Case SQL or Azure execution is part of this revision.

Audit compatibility: private security metadata collection adds no business mutation, application sensitive
view or audit schema. Existing application/security audit transactions remain authoritative. Retain approved
metadata privately in operator change control; do not log bodies, credentials or SQL into application audits.

Linux verification for this revision uses the same SQL Server 2022 digest recorded below, a new task-owned
container bound only to `127.0.0.1:14333`, generated synthetic credentials, and random nonce/SID-guarded
databases/logins. No operator-retained resource or original export was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/User tests | 38 PASS. |
| Selector affected security-data/server suite | 54 PASS; no full-suite escalation. These preserve auth/lifecycle and neighboring transaction/audit/session contracts selected for SQL security tooling. |
| Critical `mvn test` | 116 PASS. |
| Expanded disposable SQL fixture and final public quiet launcher | All three scenario PASS lines, exit 0. Exact three GRANTED / zero UNKNOWN; all three entry points reject 58307. Missing/encrypted bodies reject 58331; oversized evidence rejects 58330 before output. Original ACLs/memberships/predicates and staged rollback checks pass. |
| Real SQLCMD `-b -V 16` | Three exact EXECUTEs report GRANTED and exit 16 / 58307; collector exits 0; a missing module exits 16 / 58331. Fresh independently nonce-guarded synthetic database removed. |
| Python auth / selector / unchanged Case launcher tests | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| Changed-file/test review, SQL metadata/include/scope/alias, runbook links/fences, Git whitespace | PASS; only the collector, fixture and owning runbook changed. |

Commands executed from repository root (`mvn` uses the existing `/workspace/.tools/bin/mvn` proxy/CA/JDK
wrapper; `work/` helpers are private local orchestration, not application configuration):

```bash
/workspace/.tools/bin/mvn -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 work/local_sql.py test
python3 work/sqlcmd_probe.py
python3 work/local_sql.py launcher
PATH=/workspace/.tools/bin:$PATH python3 build/test-selection/select_tests.py --base cfc62956b209265d7c56829b5e8e1af3773c99c1 --head HEAD --format markdown --plan-output work/committed-selection.json --run
git diff --check cfc62956b209265d7c56829b5e8e1af3773c99c1 HEAD
```

The public Windows launcher procedure below reproduces the committed Java fixture; the local helpers
only supply private synthetic credentials and test real SQLCMD. An initial offline Maven dependency miss
(`libphonenumber`) was resolved through the configured proxy; no build change was needed. Passing local
checks validates this evidence tooling and continued blocking, **not the deployed procedures or their
lockout behavior**. Installation remains BLOCKED; security acceptance OPEN.

Repository-wide credential-field/SQL/call-site search at this base found:

- `AuthServiceImpl.login` uses V1 through `DataSources.auth()` for shared desktop/server password checks;
  BCrypt remains in Java. No inline SELECT fallback. Server desktop exchange repeats this verification.
- `SqlRememberCredentialStore.lookup` uses V2 on auth, then principal-initialized runtime performs the
  serializable owner/session/JTI revalidation and rotation. Creation/deletion also use runtime.
- `UserDao` directory/detail/management projections list noncredential fields explicitly. User creation
  inserts `password_hash`/`password_alg`; administrative reset updates both and revokes sessions on the
  same connection. These writes must remain available. No blanket runtime Users SELECT/UPDATE denial.
- Minimized/shared Case projections, User directory/Team/detail, selectors and session/profile readers
  consume explicit User identity/display/eligibility fields. Their original projections are preserved.
- Older installed desktops can still execute the former direct auth SELECT. External jobs, custom
  modules, installed source versions and actual configuration cannot be inventoried from this checkout.

Stage A adds object EXECUTE to `shale_app` and object DENY EXECUTE to **both** `shale_runtime` and
`shale_runtime_role` for V1/V2. One outer transaction encloses the unchanged #1868 installer, its nested
transaction, permissions and effective postchecks. Newly privileged modules are never committed with
an exposure window. Do not run the historical module installer alone in this permission profile.
No existing broad grant is revoked or rewritten. Explicit module ACL shape and exact expected permission/member deltas are checked before commit; original module definitions/owners are preserved too. No role gains dbo/ShaleAdmins/ALTER/CONTROL/impersonation.

Stage B is separately gated on compatible fleet/job drain and approved caller inventory. It creates
new deny-only roles, requiring their names to be absent:

| New role / members | Exact restriction |
| --- | --- |
| `ShaleAuthTableFenceV1` / shale_app | DENY Users SELECT, remembered-table SELECT and old resolver EXECUTE. New V1/V2 remain executable through their confined owner scope. |
| `ShaleRuntimeHashFenceV1` / shale_runtime and shale_runtime_role | DENY SELECT on Users.password_hash only. Other User projections, INSERT and password_hash/password_alg UPDATE remain. |

SQL Server column GRANT can override table DENY. Stage B snapshots all User/remembered column names as
dbo, checks every column as auth and aborts/rolls back if any remains readable. It never silently revokes
that column grant. Runtime's column DENY is tested against a direct column GRANT as well as inherited
schema CRUD. This is not a restriction on every legacy credential-related column or a repository-wide
least-privilege rewrite. Remaining credential fields and alternate elevated modules require inventory.
Stage A intentionally retains old auth SELECT/resolver compatibility; Stage B intentionally ends it.
Do not run Stage B merely because the synthetic auth test passed.

### Verification of this diagnostic revision

Fetched live base `bfca3e3346aa1b4abeb357788484d655b9087a7e` (merged #1871); isolated branch
`codex/diagnose-1871-auth-preflight`. Codex executed Linux/local disposable checks only, using the same
SQL Server 2022 image digest recorded below, a newly created task-labelled container bound to
127.0.0.1:14333, random nonce/SID-guarded fixture databases/logins and private synthetic credentials.
No retained operator resource, prior capture, original schema or Azure connection was used.

| Check | Result |
| --- | --- |
| Focused auth/module/store/migration/User tests | 38 PASS, zero failures/errors/skips. |
| Committed-base selector affected security-data/server suite | 54 PASS, one explicit opt-in SQL skip; modified fixture compiled. No full-suite escalation. |
| Critical `mvn test` | 116 PASS, zero failures/errors/skips. |
| Expanded disposable Java SQL and final quiet launcher | All three PASS lines, exit 0; reported CONNECT/Users SELECT/old resolver EXECUTE/VIEW DEFINITION DENY retained through staged roundtrip and exact ACL/member/predicate comparisons. |
| Negative diagnostic probes | Database/schema EXECUTE, fixed/ShaleAdmins membership, column UPDATE/non-Users SELECT, public/nested inheritance, W state, schema ownership, user impersonation and elevated module EXECUTE each reject 58307 and name a GRANTED condition. Unsupported permission probe returns real SQL NULL, reports UNKNOWN and fails the shared decision. |
| Real SQLCMD `-b -V 16` standalone diagnostic | Baseline exit 0 with metadata DENY shown; injected Users.email UPDATE reports GRANTED and exits 16 on 58307. Disposable injection removed. |
| Python auth / selector / unchanged Case launcher | 17 PASS plus one native-Windows skip / 24 PASS / 4 PASS. No Case SQL. |
| SQL include, privacy/scope, alias, changed-file and Git checks | PASS. No capture/historical SQL/schema/RLS/Case-tooling changes; new diagnostics have no permission mutations, module definitions, hashes or application SELECTs. |

The first offline Maven attempts lacked cached dependencies (libphonenumber and later jna-platform);
configured-proxy retries passed without build-file changes. Early fixture development exposed a W-state
test cleanup requirement, a result-decoder collision with grouped condition counts and the fact that
missing securables under denied metadata can return 0 rather than NULL. The final UNKNOWN test uses an
unsupported permission to obtain a real NULL; no missing object is asserted to be denied by that test.
Dbo inventories, explicit prerequisites and serialized metadata changes remain essential. These failed
attempts are not counted as acceptance. Existing SQL postchecks passed under the DENY without guard removal.

Exact local commands (the private `work/` helpers supply only task-local credentials and expand the
committed SQL; reproduce the Java coverage using the documented public quiet launcher):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base bfca3e3346aa1b4abeb357788484d655b9087a7e --head HEAD --format markdown --plan-output work/auth-58307/selection.json
/workspace/.tools/bin/mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
/workspace/.tools/bin/mvn test
python3 work/auth-58307/run_fixture.py
python3 work/auth-58307/check_sqlcmd.py
python3 work/auth-58307/run_launcher.py
git diff --check bfca3e3346aa1b4abeb357788484d655b9087a7e HEAD
```

Audit compatibility: diagnostics read only security metadata and add no application/business mutation or
sensitive application view, so no application audit event or schema migration is introduced. Retain
approved diagnostic evidence in private operator change control. **The Azure 58307 cause remains
unresolved until the bounded diagnostic is run there by the operator. Deployment BLOCKED; security
acceptance OPEN; desktop 1.0.136 compatibility and the original prior state remain preserved.**

## Exact operator preflight and staged next steps

These are **future separately authorized operator actions**, not instructions to connect during this task.
Use a reviewed checkout and an existing approved dbo-capable migration connection. Never place passwords,
JDBC URLs, tokens or hashes of application credentials in command arguments/output. `sqlcmd` authentication
uses the operator's existing approved mechanism; do not invent a server/login or reuse local credentials.
Run from repository root, one connection per script, SQLCMD mode, stop on first error (`-b -V 16`).
An SSMS runner that continues GO batches after failure is unsuitable. Retain complete private output with
no display truncation; definitions/SIDs/ACL metadata are change-control evidence, not public chat output.

1. **Resolve configuration first.** Privately inspect effective server and each desktop configuration's
   URL/user precedence (including aliases/system properties/packaged sources), without sharing values.
   On each already-approved actual auth/runtime connection run the SELECT-only identity/token/effective
   catalog handoff in the Case runbook. Establish same intended database and distinct actual principals
   matching `shale_app`/`shale_runtime`; verify original/effective login and nested/public/fixed/server
   authority. Repeat for jobs and supported older builds. Different mappings block these fixed-identity
   scripts; do not substitute guessed names. Record exact compatible server/MSI/source hashes and fleet.
2. **Security owner reviews** the bounded dbo modules, internal exact-email hash capability and offline
   hash-attack risk, duplicate-email rejection (including inactive/NULL-owner duplicates), module owners,
   signing, dependencies and plans/scanned work. Review complete existing module definitions, runtime
   escalation/impersonation/server authority and any elevated wrapper/dynamic SQL paths. Preflight rejects
   executable elevated, signed or encrypted user modules outside V1/V2; static/deferred caller wrappers
   are rejected by module postchecks because same-owner chaining can skip a nested procedure DENY; unexpected authority needs separate disposition.
3. **Metadata/effective preflight.** Only after steps 1–2 are evidenced, run:

   ```powershell
   sqlcmd <approved connection options> -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v ExpectedDatabase="Shale" ReviewedIdentityMapping="CONFIRMED" SecurityOwnerApproval="APPROVED" -o <private-preflight-output>
   if ($LASTEXITCODE -ne 0) { throw 'Stop: auth cutover preflight failed' }
   ```

   The angle-bracket fields are operator-selected local paths/approved connection options, not literal
   arguments. Preflight changes only session execution context/SET options and temporary metadata tables; no permanent setting or grant changes. It requires dbo metadata visibility, no ambient
   transaction, expected database/collation/owners/principals/old resolver and zero three-table predicates.
   It tests actual database-user effective permissions; server/login-token review in step 1 is still
   required, particularly for sysadmin/CONTROL SERVER not represented by database-user impersonation.
4. **Retain prior evidence before authorization.** Export the full SELECT-only catalog handoff and
   exact old resolver definition/hash/owner, principal SIDs/role closure and every applicable permission
   row including class/major/minor IDs, grantor, grantee and G/W/D/R state. Preserve original grantor
   principals. Run `capture.sql` on the approved metadata connection and save its complete output, then
   close it (its #temp tables are session-local). Capture new-name absence separately. Store source,
   script hashes and exact client rollback artifacts privately. Missing/truncated metadata blocks work.
5. **Stage A, later authorization:** run `install.sql` with the same `-v` values and stop flags, saving
   complete output. It captures all prior database permissions/memberships/predicates/known module
   definitions before mutation, then asserts every original permission/grantor/state and membership
   remains and predicates are identical. Save the two emitted `DefinitionHash` values independently
   for Stage B/rollback. A preexisting module name stops; there is no overwrite/no-op/adoption path.
   On error the runner must close its connection so the open transaction rolls back; never continue.
6. **Actual-connection postchecks, before clients:** as actual runtime, verify both new module EXECUTE
   effective permissions are 0 and invalid-input calls receive permission error 229; as actual auth,
   verify both are 1 and run approved authentication smoke checks without collecting hashes/results.
   Verify caller identity/context restoration on success/failure. Reinspect owners/definition hashes,
   exact ACLs/grantors, original old resolver, memberships and all predicates. Database-user impersonation
   is useful but does not replace original-login checks on real connections. Stage A retains direct auth
   Users SELECT and old resolver EXECUTE; record this transitional exposure explicitly.
7. **Compatible releases, later authorization:** modules/permissions precede server and desktop artifacts.
   Test password login (A/B users), second credential exchange, enrollment, /me, issuance/refresh/logout,
   checked/unchecked remembered login, Windows restart/ordinary close, rotation/replay/outage Retry,
   revocation/disabled-account rejection, DPAPI unavailable behavior, user switching and pooled context.
   Verify User directory/Team/detail/assignments/history and creation/reset/revocation. Retain the exact
   deployed artifact hashes and minimum compatible fleet/drain evidence. No release is made by this PR.
8. **Stage B, only after full inventory/drain and separate authorization:** run `restrict-reads.sql` with
   the same values plus `CompatibleFleetDrained="CONFIRMED" CallerInventoryApproved="APPROVED"`
   `LookupDefinitionHash="<saved Stage A hex>" RememberDefinitionHash="<saved Stage A hex>"`.
   Save complete prior evidence and emitted `FenceSid` values. Failures roll back both roles/memberships.
   Rerun actual-connection checks: auth direct User/remembered reads and old resolver denied; V1/V2 work;
   runtime both modules and hash SELECT denied; original noncredential projections and credential writes
   work; runtime audit INSERT remains. Confirm no prior permission/grantor/state/member/predicate changed.
9. **Separate next decisions:** keep targeted three-FILTER/candidate-schema work deferred until installed
   compatibility, actual permission checks and owner approval pass. Later prepare original/derived/candidate
   hashes/manifests from the unchanged export and run the unchanged Case harness. No synthetic auth pass
   substitutes for Case SQL or deployed RLS/grant/performance/security acceptance.

Use `module-postcheck.sql` and, after Stage B, `fence-postcheck.sql` plus `read-postcheck.sql` as repeatable metadata/effective checks
on the approved dbo connection. Their checks are supplemented by actual calls above; empty filtered rows
are not permission-denial evidence. A repeat mutation script fails closed; inspect/reconcile rather than
replaying it. Serialize operator changes during capture/install/rollback; no concurrent permission editing.

### Operator command sequence (after each preceding approval/evidence gate)

Use the existing approved connection arguments as a PowerShell array `$approvedSqlcmdOptions`; no
connection or secret values are provided by this PR. Define private output paths and use the saved
hashes/SIDs from the prior successful stage. The explicit `-y 0 -Y 0 -u` flags preserve complete Unicode
catalog/definition output. Do not add `-W` or `-h` with `-y 0`; SQLCMD rejects those combinations.
The acknowledgement values are recorded decisions, not ways to bypass missing evidence.

```powershell
$review = @('ExpectedDatabase=Shale', 'ReviewedIdentityMapping=CONFIRMED', 'SecurityOwnerApproval=APPROVED')
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/preflight.sql -v @review -o $privatePreflightOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: preflight failed' }
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/capture.sql -o $privatePriorCatalogOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: prior capture failed' }
# Stage A requires its own authorization after reviewing the capture above.
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/install.sql -v @review -o $privateInstallOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation failed' }
# Save $lookupDefinitionHash / $rememberDefinitionHash from that successful output.
# Release acceptance and complete old-client/job drain occur BEFORE Stage B authorization.
$readCutover = @review + @('CompatibleFleetDrained=CONFIRMED', 'CallerInventoryApproved=APPROVED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/restrict-reads.sql -v @readCutover -o $privateReadCutoverOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read restriction failed' }
# Save $authFenceSid / $runtimeFenceSid from that successful output.
```

Rollback commands, only after the compatibility/risk decisions below:

```powershell
$readRollback = @review + @('RollbackCompatibilityReviewed=CONFIRMED', "AuthFenceSid=$authFenceSid", "RuntimeFenceSid=$runtimeFenceSid")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-reads.sql -v @readRollback -o $privateReadRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: read rollback failed' }
$installRollback = @review + @('NewModuleConsumersDrained=CONFIRMED', 'RollbackCompatibilityReviewed=CONFIRMED', "LookupDefinitionHash=$lookupDefinitionHash", "RememberDefinitionHash=$rememberDefinitionHash")
sqlcmd @approvedSqlcmdOptions -b -V 16 -y 0 -Y 0 -u -i docs/sql/auth/permission-cutover/rollback-install.sql -v @installRollback -o $privateInstallRollbackOutput
if ($LASTEXITCODE -ne 0) { throw 'Stop: installation rollback failed' }
```

Use only independently saved values, never query current state to invent a missing expected identity.
The scripts fail closed on missing/empty values. SQLCMD variables are trusted operator inputs: retain the
literal database/principal profile and use emitted hex hash/SID values, never arbitrary text/SQL.

For the candidate's **new Windows synthetic tests**, use the existing dedicated local SQL instance and
fresh guarded terminal described in the Case runbook, then:

```powershell
python -m unittest discover -s build/auth-verification -p 'test_*.py'
if ($LASTEXITCODE -ne 0) { throw 'Auth launcher tests failed' }
python build/auth-verification/run.py --opt-in LOCAL_DISPOSABLE_AUTH --port 14333 --maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'
if ($LASTEXITCODE -ne 0) { throw 'Candidate SQL remains unverified; retain fixed failure/detail pairs' }
```

All three PASS lines plus exit 0 now include the permission fixture under setup, followed by the existing
credential, remembered-rotation and pooled isolation checks. Earlier user PASS results certify their
original run only. Leave retained earlier resources/container intact; new test runs create fresh random
resources and use the existing exact nonce/SID cleanup guards. Do not weaken environment guards.

## Exact rollback

Keep additive modules while any consumer depends on them. Rollback never disables TenantFilter, edits shared
functions, restores a broad historical grant script, rewrites data, deletes audit history or rotates secrets.
Durable-session signing/cutoff/max-TTL constraints in the App Service guide remain independent requirements.

- **Failed batch:** stop/close the single SQLCMD connection. Its outstanding transaction rolls back. Confirm
  new-name absence or unchanged prior stage and compare the saved complete catalog snapshots.
- **Stage B rollback:** review which installed artifacts will depend on restored direct access and document
  the reopened hash-read risk. Run `rollback-reads.sql` with preflight variables, separately saved
  `AuthFenceSid` and `RuntimeFenceSid`, and `RollbackCompatibilityReviewed="CONFIRMED"`. It validates exact
  SIDs, dbo role owners, three exact membership rows, four DENY rows and original dbo grantor, no role
  nesting/extra grants or grantor dependencies, then removes only those newly created memberships/roles.
  Existing grants (including other grantors/W/column R exceptions) were never rewritten and remain exact.
  Compare all prior permission/member/predicate rows to the saved Stage A baseline before client rollback.
- **Stage A rollback:** after Stage B rollback and every V1/V2 consumer drained/rolled back to known compatible
  artifacts, run `rollback-install.sql` with preflight values, both saved Stage A definition hashes,
  `NewModuleConsumersDrained="CONFIRMED"` and `RollbackCompatibilityReviewed="CONFIRMED"`.
  It rejects changed definitions/owners/ACL/grantors, signatures and static dependencies. It removes only
  the two originally absent procedures and their six newly created ACL rows. Old resolver and every old
  permission/grantor/state/member remain. Dynamic/external dependency drain is an operator requirement.
  Verify restored older password/remembered flows and exact baseline catalogs. Unknown drift means stop
  for exact-state review; no CASCADE, broad REVOKE, grantor substitution or guessed GRANT replay.

If another separately authorized change has attached a Users FILTER, these scripts stop at preflight.
Coordinate that change's own compatibility/rollback disposition; never disable the entire policy.

## Audit compatibility and explicitly retained backlog

This permission change adds no business-data view or application mutation and no new audit schema/event.
Existing PHI/entity-action/session-security transactions remain authoritative; migration catalog evidence
belongs in operator change control, without credentials, emails, hashes, SQL payloads or raw logs in app audit.

**Audit permission remediation remains OPEN:** reported runtime auditLog SELECT/INSERT/UPDATE/DELETE means
history is **not database-enforced append-only**. This candidate preserves those permissions, including
INSERT needed by required Overview audit. A separate reviewed scope must inventory PHI/legacy writers and
administrative tools, preserve historical viewer behavior, establish active-admin authority below the UI,
reduce audit UPDATE/DELETE and ordinary SELECT compatibly, decide strict write BLOCKs and global Status
immutability, and verify failure/rollback. Audit-list SQL/bound-value stderr logging remains an independent
privacy finding. Shared desktop credentials/mutable SESSION_CONTEXT and broader schema CRUD/EXECUTE remain
trust-boundary debt; do not call this a full least-privilege/security acceptance.

## Verification record

Executed on Linux, Python 3.12.14, Maven 3.9.11, Temurin JDK 21.0.12.1 and the existing Microsoft
JDBC 12.6.1.jre11. `mvn` below denotes `/workspace/.tools/bin/mvn` with its existing proxy/CA settings;
no repository build/dependency configuration changed. SQL Server 2022 uses task-owned image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, published only to
127.0.0.1:14333. A fresh random database/logins and original nonce/login SIDs guard the Java fixture;
separate SQLCMD databases use an independently generated exact nonce. No Azure destination/credentials,
original schema input, Case SQL execution or operator-owned retained resource is used.

Pre-edit inventory covered existing auth/module/lifecycle, remembered migration/store/UTC diagnostics,
User create/reset/removal/projections, runtime, server exchange and desktop lifecycle tests. No existing
production caller or DTO expectation changes. The new helper executes the actual candidate scripts within
the existing opt-in harness; the unchanged quiet launcher still exposes only three PASS scenario lines.

| Check | Final result / boundary |
| --- | --- |
| Focused auth/module/store/User projection/password tests | 38 PASS, one explicit opt-in SQL skip. |
| Additional existing server/desktop/UI auth/session compatibility | 47 PASS. Synthetic regression evidence, not installed-client acceptance. |
| Committed base-to-head selector / affected suite | 54 PASS, one explicit opt-in SQL skip. Security-data plus compiled modified helper/harness; no full-suite escalation. |
| Critical reactor `mvn -B test` | 116 PASS, zero failures/errors/skips. |
| Auth launcher Python | 17 PASS, one explicit native-Windows skip. |
| Unchanged Case launcher / selector Python | 4 PASS / 24 PASS; no Case SQL. |
| Final disposable Java SQL / real quiet launcher | All three scenarios PASS; exact cleanup, exit 0. New broad-grant/cutover/rollback checks execute under setup before application scenarios. |
| Real SQLCMD `-b -V 16` | PASS: error-stop atomic installation failure, install/restrict/rollback roundtrip, exact catalog equality and nonce cleanup. |
| Documentation/scope/Git checks | PASS: relative links/fences, SQL includes, no bare RowCount aliases, exact unchanged Case tooling/original auth SQL/R4 ledger and whitespace. |

The fixture reproduces runtime direct database EXECUTE and both principal/role dbo CRUD plus role schema
EXECUTE. It proves installer-only and REVOKE-only exposure, both final module denials (229), public and
future-role-member inheritance, and same-owner wrapper bypass followed by explicit dependency rejection.
It tests missing configuration/fleet acknowledgements, installation postcheck failure with zero committed
modules, Stage B write-capability failure with neither role retained, column-GRANT behavior, exact original
G/W/D/R/non-dbo-grantor preservation, wrong SID/hash and changed-ACL rollback refusal, and complete staged
roundtrip. Auth direct User/credential reads and old resolver fail after Stage B; new modules/password and
remembered rotation work. Runtime noncredential projection and actual parameter-bound password updates
remain usable; broad audit SELECT/UPDATE/DELETE are deliberately still available. Every rejected stage
asserts dbo caller restoration and no open transaction. Synthetic Users RLS is added only after these
checks, preserving the existing Case/source-schema boundary.

Initial offline runs missed existing dependencies, resolved through the configured proxy without POM
changes. An overlapping Maven attempt produced a transient compile failure and was discarded; counted
final runs are sequential. The first critical attempt was blocked when an existing desktop test opened
a loopback HTTP socket; the unchanged suite passed with approved local socket access. Early candidate
SQL runs exposed an unsupported permission-name check, catalog/state collation comparison and THROW/
REVERT transaction behavior; these were corrected and rerun rather than weakening guards. Initial private
SQLCMD setup had file-access/header-flag errors; final real wrapper execution passed with complete output.
No failing attempt is counted as acceptance. Existing historical all-tests advisories remain unresolved;
no new all-tests run was required or claimed.

Exact commands executed (private helpers are task scratch tools, not application configuration; dedicated
local SQL credentials were supplied only through private prompts/child environment):

```bash
python3 -m unittest discover -s build/auth-verification -p 'test_*.py'
python3 -m unittest discover -s build/case-read-verification -p 'test_*.py'
python3 -m unittest discover -s build/test-selection -p 'test_select_tests.py'
mvn -o -pl shale-data,shale-server -am -Dtest=AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,AuthVerificationSupportTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest,UserDaoCreateUserTest,UserManagementRemovalContractTest,CaseSummaryUserDetailContractTest,PreTenantAuthenticationLocalSqlServerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dtest=AuthServiceAdapterTest,AuthControllerTest,DesktopSessionControllerTest,DurableSessionAuthTest,RememberedDesktopSessionTest,SessionControllerTest,ServerAuthSessionLogoutTest,DesktopSessionEnrollmentLifecycleTest,DesktopSessionEnrollmentClientTest,DesktopRuntimeSessionLifecycleTest,LoginRememberedSignInContractTest,AuthenticatedSessionLifecycleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 --head HEAD --format markdown --plan-output work/auth-cutover/selection.json
mvn -o -pl shale-data,shale-server -am -Dtest=com.shale.data.auth.AuthCredentialLookupTest,com.shale.data.auth.AuthUserLifecycleSecurityTest,com.shale.data.auth.PreTenantAuthenticationModuleContractTest,com.shale.data.dao.CaseAggregateTransactionTest,com.shale.data.dao.CaseLifecycleAuditContractTest,com.shale.data.dao.ContactAggregateMutationContractTest,com.shale.data.dao.ContactMutationContractTest,com.shale.data.dao.EntityActionAuditEventTest,com.shale.data.dao.EntityActionAuditMigrationContractTest,com.shale.data.dao.RequestLookupRlsPhase2MigrationTest,com.shale.server.runtime.AuthPermissionCutoverFixture,com.shale.server.runtime.PreTenantAuthenticationLocalSqlServerTest,com.shale.server.runtime.RequestScopedDbSessionProviderTest,com.shale.server.runtime.ServerSessionSkeletonTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B test
python3 work/auth-cutover/run_sql.py
python3 work/auth-cutover/run_sqlcmd.py
python3 work/auth-cutover/run_launcher.py
python3 work/auth-cutover/check_docs.py
git diff --check 7b1a0a5f04d40ff2389cfd14f6a60b9888f364c0 HEAD
```

The public Windows launcher procedure above reproduces the committed opt-in Java fixture; private helpers
only supply task-local credentials/capture fixed output or exercise real SQLCMD with fresh synthetic setup.
Passing disposable SQL does not close actual configured identity mapping, independent security-owner
approval, installed fleet/drain, deployed parity/permissions, Case SQL, broader audit/admin remediation
or other R4/security/host/device gates. **Deployment remains BLOCKED; security acceptance OPEN.**
