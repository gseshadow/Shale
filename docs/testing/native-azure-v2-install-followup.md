# V2_INSTALL timeout investigation — 2026-11-02

Current status: the unchanged V2 installer completed under 600 seconds in the subsequent owner
attempt. The old timeout-only launcher is consumed. The next delta is the
[read-only 19-component diagnostic](native-azure-catalog-component-diagnostic.md), pending its
Windows validation; earlier readiness and validation statements below retain only their historical scope.

## Timeout-only Windows validation complete — 2026-11-02

**READY FOR ONE FRESH DISPOSABLE AZURE TIMEOUT-ONLY OWNER ATTEMPT.**
Windows owner validation is complete at PR head
`5150145ae91aed80f5b204c418daf861b2d8a561`, a descendant of validated executable
baseline `5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6`. Git comparison confirms that only
the four existing evidence Markdown files differ between those commits. The following
results are owner-observed and reported in this review; private Surefire XML and the
private candidate were not independently inspected or rerun here. Counts overlap and
must not be added as unique tests.

| Check | Owner-observed result |
| --- | --- |
| Focused Windows validation | 38 passed; 0 failures, 0 errors, 0 skips |
| V2 helper / dedicated local SQL gate | 12 passed; 0 failures, 0 errors, 0 skips |
| Selector-generated affected suite | PASS; detailed counts not supplied |
| Critical reactor, as reported by owner | 65 passed; 0 failures, 0 errors, 0 skips |
| `git diff --check` | PASS |
| Pinned checkout / index | 25 files PASS; `clean=true` |
| V1 receipt | PASS: `b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3` |
| V2 receipt | PASS: `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891` |
| Dedicated local fixture | Removed; `V2_INSTALL_LOCAL_CLEANUP CONFIRMED` |
| Final tracked state | No tracked changes; only untracked Python `__pycache__` directories |

The reported 12/0/0/0 local gate clears the timeout-only checklist's five-helper/seven-local
SQL prerequisite, including its private-authoritative complete-installer coverage and
cancellation/rollback checks. Prior Phase1R local identity evidence carries forward for
unchanged behavior. Local results do not predict Azure S0 timing or establish native
Azure acceptance. Earlier Linux results remain historical validation, not new Windows counts.

The executable delta from parent `5df0357b5d167580d7202e3a82a68aef373d0880` changes
only the owner V2 execution ceiling to **600 seconds** and the enclosing setup cap to
**880 seconds**, preserving the previous 280-second allowance outside V2. The existing
five-second cancellation/physical-close allowance, 30-second login, 120,000-ms connected
socket/read bound, and 20-second ordinary/rollback/absence checks remain unchanged.
SQL, modules, hashes, receipts, Phase1R, credentials and authority boundaries are unchanged.
No new diagnostic, optimization or timeout adjustment is included in this evidence update.

Use the [exact reviewed live launcher](native-azure-readonly-operator-runbook.md#reviewed-live-launcher--mandatory)
after section 1, including its same-host offline PowerShell check. The launcher pins
`5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6`, permits only the four evidence Markdown
files to differ, and requires clean tracked source, actual pinned bytes/index/receipts,
the hash-verified private candidate and unchanged live-section digest before creation.
All four live PowerShell sections (2–5), the credential block and the launcher's main
try/finally remain unchanged from the validated packet. Probe → setup → read-only ordering,
refusal of missing/skipped/failed selected reports, validated TLS, exact-workstation IPv4
firewall, independent secrets and mandatory exact-scope cleanup remain in force.

The sole live question is: **Does unchanged `phase3b8-confinement.sql` complete successfully
with the 600-second V2 migration ceiling?** On success record actual `elapsedMillis` from
existing `AZURE_V2` and `AZURE_SETUP phase=V2_INSTALL status=PASS` markers, then continue
the existing read-only native Azure gate only after the complete setup gate passes.
On any different failure stop and report the existing sanitized phase/category/SQL state/code;
do not relabel it as a 600-second expiration or tune another bound. If those markers cannot
distinguish the cause, report it as unconfirmed. Deadline expiration stops the attempt;
performance investigation is separate work.

This readiness permits **one** new nonce/resource group/server/database and fresh independent
secrets. All previous readiness is consumed; all failed fixtures remain forbidden. Every
outcome requires exact-scope destruction and successful absence confirmation. Cleanup failure
remains BLOCKED until owner reconciliation and confirmed deletion. No retry, reuse, adoption,
receipt repair, production access or deployment. Both native gates remain **OPEN**; #1929
stays **draft and unmerged**. No Azure resource or production connection was made in this review.
Audit integration uses the unchanged existing contracts; no audit event/schema change is introduced.

## Historical validation and investigations

All pending, withheld, READY and BLOCKED classifications below describe earlier states.
The timeout-only classification above supersedes them and authorizes only this one fresh attempt.

## Windows owner validation and diagnostic readiness — 2026-11-02

**READY FOR ONE FRESH DISPOSABLE AZURE DIAGNOSTIC OWNER ATTEMPT.**
The owner reports Windows validation at exact executable baseline
`f767c1e97da22cb9c3406104c36d85f35474e094`; these results are owner-observed,
not independently rerun by this reviewer.

| Check | Owner-observed result |
| --- | --- |
| Focused/offline prerequisites | 37 passed; 0 failures, 0 errors, 0 skips |
| Dedicated loopback V2 gate: `AzureOwnerV2InstallTest` + `AzureOwnerV2InstallLocalSqlServerTest` | 11 passed; 0 failures, 0 errors, 0 skips |
| Complete V2 execution coverage | All 7 local SQL tests executed, including both private-authoritative complete-installer tests |
| Phase1R identity gate | 8 passed; 0 failures, 0 errors, 0 skips |
| Selector-generated affected execution | PASS |
| Critical reactor | PASS |
| Local cleanup | Dedicated loopback SQL Server removed; `V2_INSTALL_LOCAL_CLEANUP CONFIRMED` |
| Azure opt-in / Azure resource creation during Windows validation | None |

The missing Windows/private complete-installer prerequisite is cleared. Exact-head source
and packet review confirms probe → setup → read-only ordering, refusal of missing/skipped/
failed selected reports, and mandatory exact-scope cleanup on success or failure. The
[reviewed launcher](native-azure-readonly-operator-runbook.md#reviewed-live-launcher--mandatory)
pins this executable baseline and permits differences only in these four evidence/runbook
Markdown files. Its live sections are digest-pinned before resource creation. No Java,
SQL, packaged resource, receipt, driver configuration or credential-generation block changes
are part of this evidence update.

This readiness is limited to one fresh diagnostic attempt; it does not predict S0 timing
or close either native gate. Previous attempt readiness was consumed and previous failed
fixtures remain forbidden. Use a new nonce/resource group/server/database, exact-workstation
IPv4 firewall only, unchanged TLS validation, explicit V2 and independent contained
credentials. Any probe/setup failure stops downstream read-only work and enters cleanup;
no retry, reuse, adoption, receipt repair or timeout increase is permitted. Cleanup failure
is BLOCKED pending confirmed deletion. Both native gates remain **OPEN**; PR #1929 stays
**draft and unmerged**. No Azure or production access occurred during this review; no
production SQL, deployment, enablement or pilot is authorized.

## Historical investigation and consumed classifications

All BLOCKED/pending or READY statements below describe earlier review states and are
superseded by the exact-baseline diagnostic readiness above. They do not authorize
additional attempts. Historical agent test skips are preserved as historical results;
the Windows owner results above supply the previously missing complete-installer evidence.

**BLOCKED for another live attempt.** PR #1929 remains draft and unmerged; both native
identity/read-only and mutation/fault gates remain OPEN. No Azure or production connection,
change, deployment or enablement occurred in this investigation. Windows owner validation
of the executable changes and subsequent exact-packet review are pending. The previous
one-attempt readiness is consumed, not continuing authorization to retry.

## Owner evidence and bounded diagnosis

The owner reports one new disposable attempt using the reviewed launcher at
`f82e2842bef2f4b732566e7a7a255cf30b5d0105`. Setup produced one test, zero assertion
failures, one error, zero skips; `shale-data` failed and downstream native read-only did
not run. Mandatory cleanup completed with `AZURE_NATIVE_CLEANUP CONFIRMED`. No reuse.
This report is owner evidence, not an independently observed Azure execution.

At that exact head, setup line 41 calls the complete `phase3b8-confinement.sql` in
`V2_INSTALL`. Its direct sequential ordering and exception propagation prove that
OWNER_CONNECT, OWNER_PREFLIGHT, STAGE0_BEFORE, SCHEMA_IMPORT, PREREQUISITE_MIGRATIONS,
and PHASE1R_AND_V2_PREREQUISITE all **returned successfully**. This includes guarded
Phase1R PREFLIGHT/APPLY/POSTCHECK and the V2 prerequisite. It does not certify those
facts remained unchanged later or grant native acceptance. This failure is neither
OWNER_CONNECT nor the previous recovery-catalog problem. Users/deployment/cumulative
verification/final Stage 0/OFF checks did not run.

`execute` delegates to `AiCaseDataLocalSqlServerTest.sql`: one `createStatement`,
`setQueryTimeout(20)`, one `execute(sql)`, followed by result draining. There is no GO
split, `addBatch`, `executeBatch`, per-substage timeout or per-substage JDBC statement.
The entire 521,357-byte / 4,471-line installer is submitted as one T-SQL batch.
The owner's exception identifies a statement timeout; it does **not** locate the
substatement or distinguish compilation/CPU, metadata waits, lock waits, resource
throttling or other execution costs. No Azure execution plan/wait/resource evidence
was supplied. Do not label a particular catalog query or S0 capacity as the proven cause.

Pinned Microsoft JDBC 12.6.1 source adds a significant qualification: with default adaptive
response buffering, `TDSCommand.startResponse(boolean)` starts its query timer after
sending the request and cancels it after the **first response packet**, rather than after
all results finish. Thus it is one configured 20-second JDBC statement setting, **not a
proven 20-second whole-migration wall bound**. Early INFO/DONE packets can change which
work is covered. This distinction also means NOWAIT diagnostics alone would weaken the
intended bound. The new owner-only execution watchdog prevents that regression.

## Expense and Azure compatibility review

| Portion | Evidence / plausible S0 cost | Disposition |
| --- | --- | --- |
| Authoritative Case/RLS catalog | Eleven ordered XML serialization appends, exact module SHA-256/variant checks, metadata functions, dependency/parameter/settings inventory and final SHA-256 | Potential metadata scans, sorting, XML materialization and scalar metadata lookups. No expensive query proven. Preserve all categories, order and canonical identities. |
| AI catalog | Initial disclosure inventory and final cumulative inventory; existing-install branch separately recalculates its catalog | Repeated module text, constraints, indexes, permissions, dependencies and reference-row XML serialization into nvarchar(max), then hashing; plausible CPU/memory cost. Existing-install replay branch should not execute on this fresh fixture. |
| Security metadata | Policies/predicates, database principals/permissions, ownership and database flags | Database-scoped catalogs are Azure-compatible. Metadata visibility/locking can cost time; no incomplete-visibility bypass, permission widening or catalog omission is acceptable. |
| DDL | Roles, tables, table type, unique/ordinary indexes, constraints, RLS extension and 13 dynamically compiled modules | Compilation, schema locks and log work. Fresh fixture tables should be empty before authority population, so large business-row index rebuild is not expected. No supplied evidence excludes compilation or schema waits. |
| Unicode/reference population | **4,203 individual INSERT statements**: 2,310 range rows, 1,433 lower rows, 460 DFA transitions | Thousands of statements add compilation/execution/log overhead despite few rows and one transaction. Concrete optimization candidate if profiling identifies this substage; batching values would require separately reviewed semantics/receipt work, not an unreviewed text rewrite. |
| Other | Transaction-owned exclusive application lock with explicit 3,000-ms wait; extended-property receipts; half-megabyte batch; sorting/reference hashing | App lock contention should ordinarily raise the explicit lock-unavailable error. Query/cancellation can still race. The large batch may consume compilation time before any checkpoint becomes visible. |

Catalog predicates such as `OBJECT_NAME(id) IN (...)`, scalar `COL_NAME`/`OBJECT_SCHEMA_NAME`
and repeated XML concatenation are plausible optimization targets, but no plan here proves
an inefficient access path. Changing name-based filtering to ID joins can also change
schema inclusion, ordering and destination-specific hashes. Do not rewrite these queries
without equality/adversarial tests over the exact authoritative and production representations.

The reviewed batch uses database-scoped catalogs and supported Azure DDL/RLS/role/extended-
property/application-lock features. No `sys.database_recovery_status`, server catalog,
CREATE LOGIN, USE/cross-database execution, filesystem access or unsupported edition fallback
was found in installer execution. The 13 hex-encoded module definitions remain unchanged;
no privileged module is called merely to profile installation. This is source review, not
native Azure compatibility acceptance. RLS schema dependencies, strict collation/module
settings and complete visibility still fail closed. There is no retry, dynamic plan hint,
service-tier change, authority expansion or skipped hash check.

## Transaction safety

The installer requires a fresh dbo session with `@@TRANCOUNT=0`, enables XACT_ABORT,
and opens one explicit transaction. All new roles, DDL, indexes, constraints, reference
rows, module definitions, RLS/grants and both V2 extended-property receipts are in that
transaction. The fresh-install COMMIT is after both receipts. The only other COMMIT is
the pre-existing, fully verified replay branch; there is no intermediate commit or external
side effect. SQL Server/Azure support transactional operations used here. Consequently
there is no source path that commits a subset of the V2 installation.

Ordinary catchable SQL errors execute `IF XACT_STATE()<>0 ROLLBACK` and rethrow. A client
attention/query cancel is not reliably caught by T-SQL TRY/CATCH. XACT_ABORT alone must
not be treated as proof of the post-attention state. The owner uses a physical
SQLServerDataSource connection in try-with-resources, not a returned pooled lease.
Termination of its SQL session rolls back an outstanding transaction, but is not an
independently observed rollback acknowledgement for this deleted Azure fixture.

A timeout/transport failure can race **after COMMIT**: a whole installation may already
be committed even when the client reports failure. An unconditional assertion that every
failed call leaves no V2 objects would be false. Atomicity excludes a partially committed
V2; it does not exclude an entirely committed V2 with a lost completion response. The
mandatory confirmed destruction of the failed Azure scope is therefore still essential.
Earlier setup migrations have their own committed boundaries; V2 rollback is not whole-
fixture rollback. No failed fixture is ever adopted, repaired or rerun.

Reference semantics: [TRY/CATCH attention exclusions](https://learn.microsoft.com/en-us/sql/t-sql/language-elements/try-catch-transact-sql),
[XACT_ABORT](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-xact-abort-transact-sql),
and the pinned driver's `SQLServerConnection.rollback()` / `TDSCommand.startResponse()`
in the [12.6.1 source artifact](https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.6.1.jre11/mssql-jdbc-12.6.1.jre11-sources.jar).

The new harness attempts explicit T-SQL rollback after V2 failure, checks `@@TRANCOUNT=0`
and `XACT_STATE()=0`, then verifies absence of V2 objects, table type, roles, owner index
and receipts. JDBC `Connection.rollback()` is unsuitable here: the pinned driver refuses
it in JDBC autocommit mode even if the batch opened a T-SQL transaction. Only successful
checks emit `rollback=CONFIRMED`. Transport loss, verification failure or a completed
commit emits UNCONFIRMED, fails setup and requires destruction. Neither state allows reuse.
Representative real-driver tests prove active-transaction rollback and the post-commit
uncertainty distinction locally. Complete authoritative-installer cancellation remains
unverified here without the private candidate; Windows must execute that test.

## Implementation and bounds

`AzureOwnerV2Install` is a **test-source disposable owner helper**. It checks the full raw
LF file SHA-256 `7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8`, then
adds fixed severity-10 NOWAIT warnings at reviewed statement boundaries. The source file,
V2 source receipt `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`, all
V1/V2 modules, manifests, packaged verifiers and Phase1R SQL remain byte-identical. A
regression removes every inserted checkpoint and compares the exact original bytes.
The overlay is diagnostic harness SQL; it is not a replacement reviewed source receipt.

Markers cover Case modules/catalog appends, AI catalog appends, DDL, each 256-reference-row
interval, each of 13 module creations, RLS/grants, receipts and final commit. Only exact
allowlisted warning strings become ordinal/category evidence; no SQLWarning/SQLException
text, schema/target name, SID, token, password or private input is printed. Ordinals identify
the interval after the last observed checkpoint, not proof that interval finished. Missing
warning evidence reports zero/UNOBSERVED; compilation/send or transport loss can make all
checkpoints unavailable. Total elapsed time, fixed failure category, validated SQL state
and numeric SQL code are retained. Setup's outer failure also removes resource-close
exception chains before Surefire can print private details. Quiet JDBC logging remains required.

| Bound | Current behavior / decision |
| --- | --- |
| Owner login | 30 seconds, unchanged; connection establishment only |
| Connected socket/read | Explicit 120,000 ms verified after login and before each phase, unchanged |
| Ordinary statements | Datasource default and shared helper explicit query timeout 20 seconds, unchanged |
| V2 migration execution | Explicit `setQueryTimeout(20)` retained; independent 20-second wall watchdog added because first-response driver timing and NOWAIT are insufficient |
| Cancellation | Five-second acknowledgement allowance retained; watchdog attempts Statement.cancel, then physically closes if execution has not exited by 25 seconds |
| Cleanup verification | Separate bounded 20-second rollback/transaction/absence statements; failed/closed connection yields UNCONFIRMED |
| Setup / probe JUnit | 300 / 180 seconds, unchanged |
| Read-only/runtime | Existing 3-second login, 5,000-ms socket and ten-second attestation behavior untouched |

The inherited 20 seconds was a general verification/executor setting, not an S0 migration
budget derived from measurement. It is not established as suitable for this complete
installer, and increasing socket/login limits would not fix that distinction. No timeout
increase is included. The local-only reference-population experiment uses a 60-second
query ceiling to allow measurement; it does not alter the owner migration bound.

Splitting into independently committed phases is rejected: it would abandon atomicity.
Separate bounded commands under one retained transaction are technically possible, but
this batch shares table/scalar variables, has TRY/CATCH and replay control flow, and pins
its source receipt. A naive semicolon/GO split is unsafe. A reviewed redesigned installer
would need explicit state transfer, one final commit, cumulative budget, rollback on every
boundary and unchanged catalog/module/confined semantics. None is justified as the first
response to an unlocated timeout. After substage evidence: correct a demonstrated inefficient
query first; consider reviewed same-transaction batching if compilation/reference inserts
dominate; consider a finite, owner-only migration ceiling only if legitimate bounded S0
migration work remains. Any such executable change needs its own tests and Windows validation.

## Test impact and results

Reviewed repository rules, schema/tenancy architecture, AI/confinement V1/V2/Azure contracts,
native runbook/evidence, shared SQL executor, owner datasource/driver/safety tests, migration
receipt tests, authoritative fixtures, Phase1R identity tests and selector ownership before
editing. No production Java/SQL changes. Audit compatibility: diagnostics are data-free test
metadata; no new PHI/entity audit schema or event is introduced. Existing transactional
application audits, authority boundaries and OFF defaults remain authoritative.

| Final verification | Actual result |
| --- | --- |
| Focused owner/driver/safety/attestation/V1/V2/Phase1R/new execution checks | 50 discovered, **48 passed**, zero failures/errors, **2 skipped** for missing private authoritative input |
| New local V2 gate (included above) | 7 discovered, **5 passed**, zero failures/errors; complete-install success and complete-install cancellation are the **2 unexecuted** tests |
| Existing Phase1R identity contracts + dedicated SQL identity gate (included above) | 2 + 6 = **8 passed**, zero failures/errors/skips |
| Selector affected cases/security-data, core/data/server | 154 discovered, **151 passed**, zero failures/errors; 3 skips: intentionally unopted native Azure setup and the same 2 private-input checks |
| Critical `mvn -B -ntp test` | **228 passed**, zero failures/errors/skips; all seven reactor modules SUCCESS |
| Checkout regressions | **11/11 passed** |
| Exact working-tree/index/source validation | All **25 inputs PASS**, pinned index clean, original V1 and V2 receipts PASS |
| PowerShell 7.4.6 Linux: existing credential/fence checker | PASS; all PowerShell fences parsed, two fresh five-identity rounds and partial-generation cleanup; no Az commands loaded/executed |
| Protected-file comparison, relative links, `git diff --check` | PASS; no production Java, SQL, architecture, packaged resource, driver configuration or credential block changes |
| Local cleanup | **V2_INSTALL_LOCAL_CLEANUP CONFIRMED**; dedicated container removed and private temporary credential file deleted |
| Windows / private complete installer / native Azure | **Pending / unverified / not executed**, respectively |

The exact Unicode population completed and rolled back on the fresh local fixture. No
absolute/local timing is used to select an Azure timeout. The container was loopback-only
SQL Server 2022 Developer, image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
Each test created a fresh nonce database and dropped it; failed databases were not reused.

The first local constrained run exposed the shared fixture's inherited login-clamped
read timeout, and inspection exposed adaptive first-response timing. Final tests explicitly
establish the connected 120,000-ms guard and exercise the independent deadline. Both
the final focused and affected executions passed those real cancellation cases. This is
local driver/transaction evidence, not a causal claim about the owner's Azure failure.

Exact commands (local fixture variables were supplied privately in process environment,
never in Maven arguments; no Azure opt-in was supplied):

```sh
mvn -B -ntp -pl shale-data -am \
  -Dtest=AzureOwnerV2InstallTest,AzureOwnerV2InstallLocalSqlServerTest,AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAzureSqlDriverTimeoutTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,Phase1RDatabaseIdentityContractTest,Phase1RDatabaseIdentityLocalSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py \
  --base f82e2842bef2f4b732566e7a7a255cf30b5d0105 --head HEAD \
  --format markdown --output work/investigation/selection.md \
  --plan-output work/investigation/selection.json
# Executed the plan's exact selected_maven_args via Maven, then:
mvn -B -ntp test
python3 -m unittest discover -s build/sql-confinement -p test_checkout.py
python3 build/sql-confinement/checkout_bytes.py
pwsh -NoLogo -NoProfile -File build/azure-native/test_runbook_credentials.ps1
git diff f82e2842bef2f4b732566e7a7a255cf30b5d0105 --check
```

Selected cases/security-data protect Case authority, RLS/audits, protected reads and
owner failure handling; no full-suite escalation was recommended. Every changed Java
input maps to that selection and directly affected execution tests. The final follow-up
only records results and clarifies blocked runbook prose; executable Java is unchanged
from the focused/affected/critical runs. Missing private-input/full-installer and Windows
evidence must not be credited from synthetic tests or local timing.

## Windows owner requirements and next classification

Use section 1 of the [blocked runbook](native-azure-readonly-operator-runbook.md) offline,
record the exact new HEAD, clean tracked state, pinned 25-file byte/index checks and both
original receipts. Run the updated focused selector, new local V2 gate with the hash-verified
private authoritative candidate (**all seven local V2 tests, zero skips**), existing 2+6
Phase1R contract/local identity tests, selector-emitted affected suite and critical reactor.
The complete-install success must pass cumulative verify; the constrained real installer
must leave no V2 after rollback. Remove the dedicated local fixture and record cleanup.
Run every PowerShell fence/credential check in the same Windows PowerShell host intended
for an eventual packet. Do not load Azure opt-ins or create Azure resources for validation.

The live launcher now has an explicit STOP before live work. After Windows evidence is
reviewed, review and pin a new exact live packet separately. Only then classify whether one
fresh disposable diagnostic attempt is appropriate. No live retry classification is made
here; both native gates remain OPEN, production access/change is prohibited, and TLS,
exact-workstation firewall, independent identities, confinement/Phase1R receipts and no-
failed-fixture-reuse remain mandatory.

