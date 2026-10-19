# Local sandbox verification

Implementation base: `6a0cbd025445a6ee8d79755f54b54b72cf595b2f` (`codex/latest`, merged
#1885). Application source: `59d8374858764788c621f6f8d986773b58ab79c4`; unchanged direct
Users login / old remembered resolver, with the explicit local substitutes in README.
Export SHA256 matched the supplied value; original bytes remain outside Git, unchanged.

Reviewed before implementation: AGENTS/prompt rules, database-schema, tenancy-and-rls,
system-overview; owning auth-module-permission-cutover runbook, OPERATOR, remaining
acceptance, captured legacy resources/guards; Config/DataSources, DesktopConfig/API
origin, MainApp/SceneRouter, AppPaths/identity/protected storage, updater, LiveBus clients,
server composition/controllers, durable/remembered stores and UserDao profile/audit seam.
Test-impact inspection covered DesktopApiOriginTest, desktop enrollment/lifecycle/recovery
contracts, ControlledRetirementCompatibilityLocalSqlServerTest and its fixture, auth
credential/user lifecycle contracts, User/session migration/DAO/audit tests. Application
production symbols are unchanged; new tooling has focused executable checks.

Executed on Linux, 2026-10-19:

* JDK 21/Maven application build: `mvn -B -pl shale-desktop,shale-server -am -DskipTests install`
  at the pinned application source — PASS. Dependencies copied privately with
  `dependency:copy-dependencies -DincludeScope=runtime`; classes/jars are snapshotted.
  This compiled existing tests but did not run broad suites.
* `javac -cp <private pinned desktop/server classpaths> -d <private tool-classes>
  build/local-recovery/*.java build/local-recovery/substitutes/*.java` — PASS.
* `Sandbox provision <private-state> build/local-recovery` — PASS against a fresh
  dedicated loopback SQL Server 2022 Developer container, image digest
  `sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
  All 96 application tables plus the local ownership table, enabled exported RLS,
  canonical legacy bodies, fresh mapped local SQL logins and synthetic seeds installed.
  The unchanged read-only legacy baseline accepted the pre-Stage-A state. It is not
  another Apply/Rollback rehearsal. No Azure/production connection was made.
* Real API launched on loopback through LocalBootstrap, prod bearer-only profile.
  `Sandbox verify` — PASS: actual AuthServiceImpl/Hikari/JDBC/bcrypt correct/wrong
  password; User directory/detail; profile transaction and existing audit; HTTP login,
  /me, Users list/detail, refresh/old-JTI rejection, logout/revocation, other-tenant user
  switch; real desktop enrollment/remembered client with old SQL resolver, atomic
  rotation, old-credential replay rejection and logout. No mock server or memory SQL store.
* Real JavaFX MainApp launched under private Xvfb: password login, HTTP second exchange
  (200), main shell, Team directory and User detail — PASS. GUI First Name pencil/save
  persisted `Local GUI`; a separate local-admin query verified User 1/tenant 11 and its
  additional USER/UPDATED audit with actor 1. Screenshots/logs remain private scratch.
  Linux correctly reports machine-wide identity and Windows protected storage unavailable.
* `RelayChecks <private-state>` — PASS using the real desktop NegotiateClient and
  LiveBusClient, private TLS certificate, join acknowledgements and two-tenant routing
  through LocalRelay. MainApp also logged a joined `client-11` live group. This is local
  substitute coverage, not Azure PubSub/Functions validation.
* `ContractChecks <private-state> build/local-recovery` — PASS: every original function,
  trigger, view and RLS policy definition preserved, all tables retained; three canonical
  CRLF bodies; production database/principal exclusions; remote DNS/IP, unexpected local
  port, updater execution, outside-state writes, guard replacement and HttpClient context
  URL rejection; exact-local JDBC adapter and disabled updater.
* `pwsh -NoLogo -NoProfile -File build/local-recovery/test_launcher.ps1` on Core 7.4.6 —
  PASS: syntax, fresh-directory refusal, artifact/config/tooling tamper detection and
  secure child-only secret transport. Core results do not certify Windows PS5.1 argv,
  ACLs, DPAPI or GUI/restart behavior.

Two failed initial provisioning attempts are retained, not replayed/adopted/cleaned:
filtered-index session settings and restricted-user metadata visibility exposed the
local setup corrections. Every new attempt used fresh resources. No existing/failed
operator rehearsal database or private production packet was accessed. Subsequent
application-startup corrections supplied the mandatory session-binding cutover setting,
the local TLS adapter, correct API override key and HttpClient context guard. Only
owned application processes were restarted; no retirement SQL operation was replayed.

Manual recovery **NOT MEASURED**. No Windows PowerShell 5.1, DPAPI, Windows restart,
installed 1.0.136, production serving configuration, real cloud live delivery or known-job
execution is claimed. Passing Windows/SQLCMD rehearsal at `041fa1ed...` and SQL-only
0.743829 seconds remain operator-attributed prior evidence; neither was rerun or relabeled.
The existing independent-timer/restart arrangement and all approval decisions in §3/§4
remain operator prerequisites. No automatic readiness update or manual time was generated.

Audit compatibility: synthetic database/security setup is private developer evidence,
not an application business audit. The selected profile workflow uses the existing
transactional USER/UPDATED EntityActionAuditLog with original tenant/actor context and
allowlisted metadata. No new audit schema or event, sensitive values, timeline surrogate,
production mutation or post-commit audit path was introduced.
