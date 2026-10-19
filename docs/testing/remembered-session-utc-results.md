# Focused verification and review record

The Windows host/state and database in the request were not accessed. All SQL execution below
used a newly started local SQL Server 2022 container on `127.0.0.1:14439`, fresh
`ShaleSessionUtcTest_<random>` databases and synthetic identities. Original Windows state,
credentials, timestamps, manifests, startup overlay and failure evidence remain untouched here.

## Environment and reproduction

* Linux x64, Temurin JDK 21.0.12.1+1, Maven 3.9.11; Microsoft JDBC 12.6.1.jre11.
* SQL Server image `mcr.microsoft.com/mssql/server:2022-latest`, digest
  `sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
* Real repository migrations for UserSessions (all check constraints/FKs and tenant RLS), remembered
  credentials and its narrow resolver/object grants. Current-source fixtures also install the real
  V1/V2 module migration and grant the auth principal V2 execution, with explicit runtime module DENYs.
  The predicate matches the existing auth SQL fixture's dbo/ShaleAdmins exceptions; neither runtime
  identity belongs to those authorities. Pinned standalone fixtures do not install V1/V2 modules. Minimal synthetic Users/clients/instances
  prerequisite tables; separate non-dbo authentication/runtime logins. Runtime context is stamped
  before every owner operation; cross-tenant session visibility is checked. Credential validation
  itself is a synthetic AuthServicePort fixture, not the full bcrypt/password/lockout subsystem.
* Production desktop/auth controllers, session service, JWT implementation, SQL durable/remembered
  stores, JDBC driver, real SQL statements and constraints. Both DispatcherServlet tests and an
  actual loopback Tomcat + Java HttpClient lifecycle ran. This is not full Spring production-profile
  composition, Windows desktop/DPAPI, deployment TLS/pools or installed artifact acceptance.

The initial stale tracking ref e5c6229f and the pin both predate the remembered-store UTC/V2 correction.
After PR metadata exposed that stale ref, an explicit refspec refreshed origin/codex/latest to
684ab9d638e326ec52451a834fb85158def10197. Its current remembered store is already UTC-correct and is
retained unchanged, including V2 bounds/timeout/sanitization. The application fix addresses the remaining
DAO/projection conversions; the old remembered source exists only under the additive compatibility
folder. All final current-source checks use the refreshed base and real V2 fixture.

Against the initial old source, `mvn -pl shale-server -am -Dtest=RememberedSessionSqlTest
-Duser.timezone=America/Denver -Dsurefire.failIfNoSpecifiedTests=false test` reproduced restore HTTP
500. The test inspected the actual controller operation's SQLException chain and recorded
`SQLState=23000 vendorCode=547 constraint=CK_UserSessions_Refresh`. A separate legacy binding
probe independently asserted that exact constraint and rollback. Neither test inferred the name
from error 547 alone. The same lifecycle with unchanged application classes built from pin
`59d83748`, through serving Tomcat and SQL/JDBC, also reproduced HTTP 500 at the pinned line 46.

## Test impact and executed checks

Initial and refreshed-base test-impact review covered `SqlRememberCredentialStoreTest`, `RememberedDesktopSessionTest`,
`DurableSessionAuthTest`, `ServerAuthSessionLogoutTest`, `SessionManagementContractTest`,
`UserSessionDaoContractTest`, `SessionInvalidationPhase8AContractTest`,
`DesktopRememberCredentialMigrationContractTest`, `DesktopSessionControllerTest` and
`AuthControllerTest`. Repository-wide searches covered Instant bindings/reads, constructors,
SQL placeholders, field lists and the old exact `setTimestamp` source expectation.

Mapping:

| Changed production boundary | Checks |
| --- | --- |
| UtcJdbcTimestamp | Fresh UTC Calendar per bind/read, both ResultSet overloads, null preservation; real-driver stored UTC fields across JVM-zone changes and DST |
| Pinned compatibility SqlRememberCredentialStore; unchanged current V2 store | Existing UTC/full-rotation, owner-scoped creation, V2 bounds/narrow lookup tests; real enrollment, rotation, replay, installation mismatch, expiry and rollback |
| UserSessionDao | Existing owner/default/account eligibility contracts; real creation, read, refresh old-JTI rejection, unchanged absolute deadline, logout/revocation and RLS |
| SessionManagementService | Existing tenant/audit/invalidation contracts; real self-list timestamp projection; since binding uses the same tested UTC boundary |
| Additive compatibility launcher | PowerShell parsing, original identity LF/CRLF, wrong database/port/pin refusal, inventory/hash/path/duplicate/extra-file refusal, real argument transport; exact pin compilation and class-origin checks |

Focused Maven command (with the disposable SQL password supplied **only in the process environment**):

```text
mvn -pl shale-server -am -Dtest=RememberedSessionSqlTest,UtcJdbcTimestampTest,SqlRememberCredentialStoreTest,UserSessionDaoContractTest,DesktopRememberCredentialMigrationContractTest,RememberedDesktopSessionTest,DurableSessionAuthTest,SessionManagementContractTest,ServerAuthSessionLogoutTest,DesktopSessionControllerTest,AuthControllerTest -Duser.timezone=America/Denver -Dsurefire.failIfNoSpecifiedTests=false test
```

The initial focused command passed. Final expanded checks passed **39 tests** (7 data + 32 server) and also ran the relevant
`SessionInvalidationPhase8AContractTest#boundValidationAlsoRequiresCurrentAccountEligibility+adminRevocationIsExactTenantSessionAuditedBeforeCommitAndPublishedAfterCommit`
methods. The serving lifecycle covers enrollment → restore → old credential 401 → refresh → old JTI
401 → logout → remembered restore 401, and asserts both issuance UTC and preserved absolute expiry.
A plain short-lived desktop enrollment also succeeds. Separate expired credential/deadline-equality
checks reject. DST probes cover both sides of the March 8 gap and both November 1 01:30 folds,
writing in UTC/Denver and reading after switching JVM default zones. Zone changes occur only in
isolated test code and are restored in finally; application code never forces a global timezone.

UTC verification command:

```text
mvn -pl shale-server -am -Dtest=RememberedSessionSqlTest,UtcJdbcTimestampTest,SqlRememberCredentialStoreTest,DesktopRememberCredentialMigrationContractTest -Duser.timezone=UTC -Dsurefire.failIfNoSpecifiedTests=false test
```

PASS. The Denver-only legacy-failure probe is intentionally skipped in UTC. The serving lifecycle,
expiry and DST/JVM-zone probes execute in both timezone runs.

Additional executed checks:

* `mvn -f <isolated-pin-checkout>/pom.xml -pl shale-server -am -DskipTests install` and
  server `dependency:build-classpath` — PASS; no pinned source edits.
* `javac --release 21 -proc:none -implicit:none -sourcepath <empty> -cp <pinned server classes +
  pinned dependency classpath> -d <additive classes> <four corrected sources + SessionUpdateChecks.java>`
  — PASS. `SessionUpdateChecks <classes>` passes in UTC and America/Denver and verifies exact class origins.
* Test-class `RememberedSessionSqlTest.main` supports `legacy` (expect serving restore 500) and `fixed`
  (full lifecycle). Run from shale-server with `target/test-classes`, pinned server classes and pinned
  test/runtime dependency classpath; prepend additive classes for `fixed`. The committed standalone entrypoint reproduced the unmodified pin in `legacy` mode and
  passed the additive compatibility lifecycle in `fixed` mode. Both exited cleanly after server destruction.
* `pwsh -NoLogo -NoProfile -File build/session-utc-update/test-update.ps1` — PASS under Core 7.4.6/Linux,
  with task-local XDG directories; no private export/state or SQL accessed by launcher tests.
* Git diff whitespace checks — PASS. Source comparison verifies only the intended UTC changes in
  the two current persistence classes and the separate pinned remembered-store copy; no SQL constraint/default/predicate, revocation expression,
  client pending protocol or user-facing date semantics changed.

## Known base failure and limits

An expanded neighboring-contract run also executed the whole
`SessionInvalidationPhase8AContractTest`. Its account disable/removal/reset source assertion
`count("revokeAllSessions(con,") >= 4` fails: the unchanged `UserDao` has three direct calls.
The **same failure was reproduced on an isolated clean checkout of base e5c6229f**, using
`mvn -pl shale-data -am -Dtest=SessionInvalidationPhase8AContractTest
-Dsurefire.failIfNoSpecifiedTests=false test`. Neither file is modified by this fix. That assertion
was not weakened and no account-administration change was included. The refreshed current base
also retains this >=4 expectation and the three calls; no result for its whole historical suite is inferred. Its affected eligibility and
transaction/audit methods are run separately. Do not describe the entire historical suite as green.

No root critical reactor, full suite, retirement execution or unrelated broad suite was run, honoring
the user's explicit scope. The selector is used for impact review, not permission to broaden that scope.

Native Windows PowerShell 5.1, Windows 11 Adoptium 21.0.8+9, real DPAPI save/restore, full existing-state
Stage/Api/Desktop launch, the original private export, and the user's actual database rows were not
available here. Native artifact-update execution remains **UNVERIFIED**, even though isolated pin
compilation/linkage and API/JDBC behavior pass. The original Windows constraint name remains inferred
from a matching named reproduction, not claimed as observed in its sanitized trace. The continuation
instructions stop on every mismatch and do not automatically retry, re-provision, delete credentials,
rewrite timestamps, weaken constraints, connect to Azure, deploy or execute retirement.

Selector review: `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD
--format markdown --plan-output work/session-test-selection.json` — PASS. It selects security-data,
server and settings-team plus informational full escalation for unknown launcher/helper paths. Case,
contact, entity-audit, lookup-RLS and Settings UI tests in that broad mapping do not exercise the
changed timestamp boundary; those and root/full suites were excluded under the explicit no-unrelated-
broad-suites instruction. Focused authentication, context and boundary tests cover the affected scope.

Relevant selector follow-up: `mvn -pl shale-server -am
-Dtest=AuthUserLifecycleSecurityTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest
-Dsurefire.failIfNoSpecifiedTests=false test` — PASS, 12 tests (2 data + 10 server).

## Refreshed current-base final verification

Target base is **684ab9d638e326ec52451a834fb85158def10197**, explicitly fetched with
`git fetch origin refs/heads/codex/latest:refs/remotes/origin/codex/latest` and merged into the PR.
Current remembered-store source is byte-identical to that base; no resolver downgrade or removal of
its existing UTC correction occurs. The compatibility-only remembered source is separately reviewed
against pin 59d83748 and compiled ahead of that pin's dependencies. Current tests install the real
V1/V2 module SQL in disposable fixtures; standalone pinned tests use only the legacy resolver fixture.

On this current base, the combined focused command listed above plus the two affected invalidation
methods and AuthUserLifecycleSecurityTest/RequestScopedDbSessionProviderTest/ServerSessionSkeletonTest
passes **54 tests** (9 data + 45 server) under America/Denver. The current-base UTC command also passes
(4 data + 10 server reported, with the one Denver-only legacy probe skipped). Raw SQL ExpiresAt fields
are explicitly checked as UTC, preventing a misleading local-write/local-read round trip. A focused
five-test current-base Denver rerun including this assertion passes. Fresh compatibility compilation
and class-origin/UTC checks pass using the separate legacy source. Windows limits remain unchanged.

Final standalone reruns with the separated compatibility source: `RememberedSessionSqlTest legacy`
under America/Denver reproduces the serving API 500; `fixed` under America/Denver and UTC passes
the complete serving lifecycle, including raw SQL expiry assertions. These pinned modes explicitly
do not install V1/V2 modules. The offline class-origin checks load without initializing application
logging/static initializers and pass in both zones. Launcher Core helper checks pass after final review.
