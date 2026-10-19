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

Final integration and scope review:

* The complete `sandbox.ps1 -Phase New` entry point ran under PowerShell Core 7.4.6,
  not merely the Java worker: fresh private state, isolated pinned worktree, Maven
  build/dependency snapshots, javac, local certificate, manual-block extraction,
  manifest and fresh SQL database/logins/seed/baseline — PASS.
* `sandbox.ps1 -Phase Api`, `-Phase Relay`, `-Phase Verify` and `-Phase SelfTest` also
  ran through the actual Core launcher — PASS at code revision
  `2811f86f` (full revision retained in the private state). The synthetic prompt harness
  supplied only test secrets; it replaced no SQL generation, provisioning, transport,
  application code or verification assertions. No real protected-store result is inferred.
  `Verify` additionally checks a nonempty other-tenant UserSessions fixture against
  the preserved RLS policy and runs actual desktop local-relay clients.
* Final late-TLS regression: the relay verifier now selects its dedicated trust context
  explicitly, independent of a previously initialized SQL TLS context. The full combined
  API/session/live verification then passed, including the actual Core launcher above.
* Final relay-disconnect handling was compiled and tested with a synthetic broken-output
  subscriber in `ContractChecks`; it removes that exact peer and keeps publish usable.
  The same routing behavior passed the actual live-client integration. Final
  `ContractChecks` and Core `test_launcher.ps1` both passed after this correction.
* `python build/test-selection/select_tests.py --base 6a0cbd025445a6ee8d79755f54b54b72cf595b2f --head HEAD --format markdown --plan-output <private selection.json>`
  — PASS. It selects security-data from the two acceptance-document links and treats
  new tooling as an unknown area. Its broad/critical/full suggestions were not run:
  the user's explicit scope excludes unrelated broad reruns, no application production
  symbol or retirement SQL changed, and focused real SQL/desktop/API/tooling checks cover
  the added contracts. This is not a claim that a broad suite passed.
* Final `git diff <base> HEAD --check` — PASS. Changed-file review maps every new Java
  tool to ContractChecks/RelayChecks/real provisioning and Verify; the PS wrapper to
  test_launcher plus real Core entry-point execution; documentation to reviewed scope.
  No changed retirement `.sql` file. Original `/workspace/Shale` checkout stayed clean.

All successful and failed task-owned local resources/evidence remain retained privately.
No timed manual Apply/Rollback exercise was started and no recovery number was emitted.


## PowerShell 5.1 manifest-array correction

Fix base: `d0bb63966934c451f24b6172f436dd461aa244e7` (current `origin/codex/latest`,
merged #1886). Brian's native Windows 5.1 failure at reviewed commit
`0f15b9a1217620e1734054e8ab303ccdd132bfac` is operator-provided evidence:
a captured `ConvertFrom-Json` result is `System.Object[]` with seven records, while
`@(Get-Content ... | ConvertFrom-Json)` contains one pipeline object.

`Assert-BuildManifest` now captures `Read-Json` before array materialization. Its
inventory count, per-record path access and exact SHA256 comparison are unchanged.
No existing manifest is rewritten or generated to accept an inventory mismatch.
The test loads the real shared JSON helpers by AST, without executing the operator
or launcher phases. It uses one immutable seven-record manifest and checks both
the host reader and an explicitly non-enumerated array result. Valid inventory
passes; added/deleted files fail the count check; same-count path replacement and
modification of every fixture artifact/config/tooling file are rejected. The
manifest digest is unchanged after all cases. Existing private-directory refusal
and child-only secure prompt checks remain.

Consumer/test-impact review: repository-wide searches for `build-manifest`,
`Assert-BuildManifest`, `Read-Json` and `ConvertFrom-Json` found this sole top-level
build-inventory array consumer. Retirement-operator packet/authorization/readiness
and invocation-validation retained-manifest readers consume object documents;
they do not use the defective pipeline-array wrapping. Those resources and their
SQL contracts are unchanged. Changed-file mapping: `sandbox.ps1` is covered by
`test_launcher.ps1`; README and this evidence note document commands and limits.
No application business mutation or audit seam changed; no audit migration needed.

Executed on Linux with PowerShell Core 7.4.6:

* `pwsh -NoLogo -NoProfile -File build/local-recovery/test_launcher.ps1` — PASS,
  using task-local XDG cache/config/data directories because the host home is read-only.
* The updated test against a scratch copy of the old launcher expression — expected
  FAIL, `Build inventory changed; stop`, at the simulated PS5.1 valid inventory.
* `python build/test-selection/select_tests.py --base d0bb63966934c451f24b6172f436dd461aa244e7 --head HEAD --format markdown --plan-output <task-work>/manifest-test-selection.json`
  — PASS. No affected Maven module/test contract was selected; the new tooling paths
  trigger unknown-area escalation. Suggested critical/full Maven runs were excluded
  by the user's explicit prohibition on unrelated broad reruns.
* `git diff --check` — PASS.

Native Windows PowerShell 5.1 remains **UNVERIFIED by this fix's execution**; run the
README's explicit `powershell.exe` offline command from the pinned fix checkout.
No Windows provisioning has been attempted. Prior Windows checkout/failure evidence,
private export, packets and SQL resources were not accessed or changed. No Azure
operation, provisioning, sandbox phase or broad application suite was run for this fix.
