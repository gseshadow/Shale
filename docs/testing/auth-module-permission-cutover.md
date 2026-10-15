# Authentication module permission cutover — 2026-10-15

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

## Caller review and bounded decision

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
