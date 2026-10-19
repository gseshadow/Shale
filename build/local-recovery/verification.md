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

## Windows JDK 21 selector startup correction

Fix base: `987d91a9e01725c5743a49313d148c101b2c3dc6`, current `codex/latest`
at investigation. Brian reports PS5.1 launcher regression PASS at
`80353a0263dd35640d82388daa4e4211c1e95f5a` and provisioning success for
`SandboxState-1887-B` / `ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c`.
The reported relay stack reaches Windows PipeImpl AF_UNIX socket deletion. It
contains no rejected pathname; no exact production-profile path is asserted.
The README links the inspected JDK 21.0.8 sources and explains property precedence.

Documentation reviewed: AGENTS, complete codex-prompt-rules, system-overview,
tenancy-and-rls, local-recovery README/verification, owning auth permission-cutover
runbook, OPERATOR and remaining-acceptance. Pre-edit test-impact search covered
LocalBootstrap/LoopbackGuard, Sandbox.launch, java.io.tmpdir, tool-classes,
build-manifest/Assert-BuildManifest, ContractChecks, RelayChecks and test_launcher.
API live HttpInvalidationPublisher and desktop NegotiateClient/LiveBusClient use
HttpClient selectors; API NIO startup shares the same child-launch configuration.
No application production symbol, SQL resource, business mutation, sensitive read,
or audit seam changes. Existing application audit schema remains unchanged; tooling
integrity receipts are private developer evidence, not business audit rows.

Changed-file/test mapping:

* Sandbox / StartupPaths: explicit JVM socket directory and child TEMP/TMP for every
  launch; StartupChecks exercises selector/wakeup/close plus actual loopback
  HttpServer and HttpClient. LocalBootstrap validates the configuration before
  loading the application. LoopbackGuard permission checks are unchanged (the
  class allows a recording subclass solely for focused path observation).
* sandbox.ps1: additive UpdateStartup and explicitly selected receipt-bound overlay;
  test_launcher retains prior PS5.1-array and tamper tests and adds receipt
  state/revision/original-manifest binding, class/source tampering and inventory
  rejection. Original manifest bytes must remain unchanged.
* StartupChecks: native Windows additionally reproduces the legacy rejection only
  against disposable scratch sockets, prints the actual rejected path and requires
  corrected AF_UNIX cleanup under owned tmp. Linux does not simulate Windows
  acceptance. API/JavaFX/DPAPI/Windows restart are not launched by these checks.

Executed in this change, Linux / Temurin JDK 21.0.12.1 / PowerShell Core 7.4.6:

* Pinned application checkout `59d8374858764788c621f6f8d986773b58ab79c4`:
  `JAVA_HOME=/workspace/.tools/jdk-21 /workspace/.tools/bin/mvn -B -pl shale-desktop,shale-server -am -Dmaven.test.skip=true compile`
  — PASS, compilation only; no application test suite.
* `javac -proc:none -cp <pinned application classes and dependencies> -d <scratch classes> build/local-recovery/*.java build/local-recovery/substitutes/*.java`
  — PASS. Initial compilation against stale cached application targets failed;
  those targets were not treated as validation of the pinned revision. An offline
  current-tree Maven compile lacked ICU/phone dependencies; the isolated pinned
  compile above supersedes it for this tooling's actual application contract.
* `java -Djava.security.manager=allow -cp <scratch classes and dependencies> StartupChecks`
  — PASS on Linux: actual EPollSelectorImpl, guarded HTTP exchange and isolation
  denials. Initial restricted execution could not bind loopback sockets; the
  authorized focused rerun with socket access passed. `native Windows coverage=false`.
* `pwsh -NoLogo -NoProfile -File build/local-recovery/test_launcher.ps1`
  with task-local XDG directories — PASS on Core 7.4.6, including simulated PS5.1
  array shape. This does not certify native Windows PowerShell execution.

Native Adoptium Windows 21.0.8.9 execution and the actual provisioned-state update /
Relay restart remain **UNVERIFIED here**. UpdateStartup requires the native checks
to pass on Brian's Windows host before producing a usable receipt. Preserve any
refusal/evidence; do not rewrite manifests, remove the guard or replay New.
No supplied Windows state, database, credential, manifest or failure evidence was
accessed or changed. No Azure operation, retirement execution, provisioning or
unrelated broad suite ran. Manual recovery remains NOT MEASURED.

Final focused integration/review:

* A disposable synthetic fixture used the real original startup sources/classes,
  a new synthetic manifest and the pinned application dependencies. AST-loaded
  launcher helpers ran the real reviewed clean-HEAD check, original inventory
  verification, compiler, StartupChecks subprocesses, receipt creation and receipt
  verification — PASS under Core/Linux. Original manifest digest remained equal;
  a repeated attempt and then tampered original tooling were refused. No SQL or
  private export was used. The AST harness explicitly bound PSScriptRoot to the
  actual launcher directory; an initial harness-only missing script-directory
  failure was retained in a separate fixture and not reused. This helper exercise
  is not a claim of native PS5.1 or full provisioned-state launcher execution.
* `python build/test-selection/select_tests.py --base 987d91a9e01725c5743a49313d148c101b2c3dc6 --head HEAD --format markdown --plan-output <scratch>/startup-selection.json`
  — PASS; no affected Maven modules/test classes. Unknown tooling paths suggested
  critical/full suites, excluded by the user's explicit scope. Focused compilation,
  Java runtime checks, launcher/receipt checks and update-helper integration cover
  the actual changed contracts. No unrelated suite result is implied.
* `git diff 987d91a9e01725c5743a49313d148c101b2c3dc6 HEAD --check` — PASS.
  Final reference search found the only shared child launcher updated, both
  new-source compilation lists present, explicit overlay selection in parent and
  child, and no outside-state guard exemption. Old application artifacts and
  original manifest entries are never overwritten by UpdateStartup.

## Single-PC continuation and reported Windows results — 2026-10-19

This update supersedes the earlier independent-timer/second-machine restart
prerequisite above. The [single-PC sequence](single-pc-recovery.md) separates untimed
Windows persistence after service restart from uninterrupted timed SQL/application
recovery. The old manifest-bound manual-exercise.ps1 is retained unchanged and is
not the payload to execute. The revised remaining-acceptance §3 block is extracted
into a new private evidence file. SQL guards and runner transport remain unchanged.

Brian reports at `2a74cd122e87b15fb995722451e026faa212abb8`: Stage and native
PowerShell launcher PASS; API started; checked synthetic password login PASS;
ordinary close/reopen automatic restore PASS; explicit logout then close/reopen
remained at login with no restore attempt PASS; Phase Verify application/API and
local-live routing PASS. These observations supersede earlier pending statements
for those specific source/overlay Windows paths. They were not rerun here. They do
not establish Windows reboot persistence, full manual recovery, installed 1.0.136
or production serving artifact acceptance. Full manual recovery: **NOT MEASURED**.

Scope/test-impact review: documentation and its embedded exercise payload only.
Reviewed existing controlled-retirement Python contracts, operator helper/ODBC
contracts, sandbox launcher/receipt checks and session-update launcher/receipt
selection. No Java, PowerShell launcher, retirement SQL, schema, permission body,
application audit seam or readiness file changed. The revised payload still uses
the existing helpers, secure prompts, controlled Apply/Rollback and saved full hashes;
it adds an explicit local-only decision/evidence gate, exact existing DB/port binding,
a retained Apply-attempt marker, retired-state outcome and local scope in its result.
An unsuccessful retired-state observation does not become a passed recovery result.
Audit compatibility uses the existing transactional USER/UPDATED profile workflow;
private operator records are not application audit events. No audit migration.

Executed for this documentation change on Linux:

* `python3 -m unittest discover -s build/auth-verification -p 'test_controlled_retirement.py'`
  — 4 PASS; static controlled-route/approval/window/nine-row/exact-rollback contracts.
  No SQL connection, rehearsal or completed Windows check was repeated.
* `work/single-pc/pwsh/pwsh -NoLogo -NoProfile -File work/single-pc/check.ps1`
  under task-local XDG directories — PASS: PowerShell Core 7.4.6 parses all 31
  command/payload blocks in the continuation and related instructions, and every
  single-PC operator block contains one command line. A private synthetic harness
  exercised the extracted payload with real saved-marker/hash helpers and mocked
  prompts/SQL: success, failed trial still reaching guarded recovery, wrong rollback
  hash, ambiguous Apply marker and wrong target refusal all PASS. The harness only
  localizes the Windows output root and loads helpers before installing its mocks;
  it is not actual SQL, Windows, DPAPI or manual timing acceptance. Its first attempt
  reloaded helpers over the mocks and stopped at missing Windows SQLCMD help; that
  harness error was corrected before the counted synthetic runs.
* `python3 work/single-pc/check_docs.py` — PASS: relative links/fences, revised payload
  extraction, timer-before-acknowledgement, single Apply/Rollback and saved-hash guards,
  plus unchanged SQL/launcher files. Its first link scan matched a PowerShell cast as
  a Markdown link; excluding fenced code fixed that checker-only false positive.
* `git diff --check` — PASS. Broad Maven suites, provisioning, native launcher checks,
  Phase Verify and all reported completed application checks were not repeated.

These are documentation/payload validation results only. The explicit manual incident
command uses retained local context, original markers and independent saved hashes;
no deadline can disable guarded rollback. A failed drill never becomes a success by
running that incident command. Actual native Windows exercise execution remains
outstanding, and manual recovery remains **NOT MEASURED**.

`python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown --plan-output work/single-pc/selection.json`
— PASS after committing the changes, base `3dd1131e`. It selects security-data from
the documentation path and flags the local tooling Markdown paths as unknown.
Its suggested broad/critical/full Maven suites were not run: this change modifies
only instructions and their payload, with no application/security-data implementation
change, and the task explicitly excludes repeating completed checks or unrelated
preparation. Focused SQL contract, syntax, extraction and synthetic orchestration
checks above verify the changed surface. No Maven-suite pass is claimed.
