# Native Azure identity / read-only acceptance — 2026-10-29

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

## Packet verification for the timeout-only publication

Reviewer checks on this four-file documentation update:

- `python build/sql-confinement/checkout_bytes.py`: PASS, 25 files, clean index, both receipts.
- `python -m unittest discover -s build/sql-confinement -p test_checkout.py`: 11 passed.
- `pwsh -NoProfile -File build/azure-native/test_runbook_credentials.ps1` using PowerShell
  7.4.6 on Linux with task-local XDG directories: all PowerShell fences parsed; two fresh
  credential rounds, five identities, no output and partial-generation cleanup passed.
- Temporary offline launcher guard harness: eight cases passed (valid baseline reached
  the intercepted first Azure cmdlet; ancestry, executable drift, tracked edits, pinned
  bytes, private hash, inherited values and prior scope each stopped before that boundary).
  Git/Python/private-hash results were mocked; no Azure cmdlet contacted Azure.
- Static packet comparison: sections 2–5 are byte-identical to validated head `5150145…`;
  the launcher differs only in its baseline pin. Live-section SHA-256 remains
  `60e11e30133675c1a48b260463eee25aed69740c3bd3d1700389705a5e7050d5`.
  All relative file links resolve; `git diff --check` passes.

The PowerShell checks are offline Linux checks, not Windows or live Azure acceptance.
The existing same-host Windows check in section 1 remains mandatory before launch.
Maven/SQL suites were not rerun for this documentation-only publication; owner-observed
Windows results above provide that evidence. No Java/SQL/runtime or audit schema changed.

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

## Packet verification for this evidence update

Static checks PASS: all four live PowerShell sections (2–5), including credential
generation, native Maven selectors and cleanup, are identical to the Windows-validated
baseline packet. The launcher's existing main try/finally sequence is also unchanged.
New guards pin the executable baseline, restrict descendant changes to four Markdown
files, require clean tracked source and pinned bytes/index/receipts, recheck the private
candidate, and verify the live-section SHA-256
`60e11e30133675c1a48b260463eee25aed69740c3bd3d1700389705a5e7050d5`
over LF-normalized extracted blocks joined with one LF. Static review confirms explicit
V2, exact IPv4 endpoints, unchanged TLS, report refusal, setup-failure ordering and exact
owned cleanup.

PowerShell parsing and mocked launcher execution were **not rerun in this review**:
PowerShell is unavailable in the task runtime. Section 1 still requires the existing
offline credential/fence check in the same Windows host before launch. Maven/SQL tests
were not rerun for this four-file documentation update; the exact-head owner results
above are the validation evidence. No new runtime Java/SQL behavior is introduced.
The shell Git fetch failed because its configured proxy was unavailable; files and PR
identity were read at the exact head through the GitHub connector. The stale attached
checkout was not modified or used as evidence for this head.

## Historical investigation and consumed classifications

All BLOCKED/pending or READY statements below describe earlier review states and are
superseded by the exact-baseline diagnostic readiness above. They do not authorize
additional attempts. Historical agent test skips are preserved as historical results;
the Windows owner results above supply the previously missing complete-installer evidence.

## Current classification after V2_INSTALL timeout — 2026-11-02

**BLOCKED: no further live attempt authorized.** The single attempt at
`f82e2842bef2f4b732566e7a7a255cf30b5d0105` stopped in V2_INSTALL after all earlier
setup phases returned. Owner reports mandatory `AZURE_NATIVE_CLEANUP CONFIRMED`;
no fixture reuse or native read-only gate. The historical readiness below was consumed.
[Current bounded diagnosis, rollback review and Windows prerequisites](native-azure-v2-install-followup.md)
supersede earlier readiness statements. New executable changes require Windows owner
validation and subsequent exact-packet review. Both native gates remain OPEN; keep
#1929 draft/unmerged. No Azure or production access occurred in this investigation.


## Windows owner validation and retry classification — 2026-11-02

Owner-observed on Windows at exact head `c5d13ea4d93f0b0ca732244506d7b0f5d1b96047`;
reported by the owner, not independently rerun by this agent:

| Check | Owner-observed result |
| --- | --- |
| Reviewed checkout / receipt validation | PASS |
| Focused Phase1R / Azure prerequisite gate | PASS |
| Selector-generated affected / critical execution | PASS |
| Critical reactor | All seven modules SUCCESS |
| Shale Server | 65 tests; 0 failures, 0 errors, 0 skips |
| Dedicated identity fixture | New localhost-only SQL Server 2022 Developer |
| `Phase1RDatabaseIdentityContractTest` | 2 passed |
| `Phase1RDatabaseIdentityLocalSqlServerTest` | 6 passed |
| Combined Phase1R identity gate | 8 passed; 0 failures, 0 errors, 0 skips; Maven BUILD SUCCESS |
| Local fixture cleanup | Disposable SQL container removed; `PHASE1R_LOCAL_CLEANUP CONFIRMED` |
| Azure / production access during this validation | None |

**READY FOR ONE FRESH DISPOSABLE AZURE OWNER ATTEMPT.** The Windows prerequisite
blocker is cleared and the next exact live packet has been reviewed against this head's
probe, setup, read-only selectors and safety contracts. Use the
[reviewed operator packet](native-azure-readonly-operator-runbook.md), including its
mandatory cleanup launcher. Fresh nonce/resource group/server/database and independent
secrets only; exact workstation IPv4 firewall; no failed-fixture reuse/adoption.
TLS verification and all Java deadlines remain unchanged; V2 is explicit.

This is readiness to attempt, not native acceptance. Native identity/read-only and
disposable mutation/fault gates remain **OPEN**. PR #1929 stays **draft and unmerged**.
Probe, setup or read-only failure stops downstream work and enters exact-scope cleanup.
No Azure or production access, deployment or activation was performed by this agent.
Local edition injection is SQL dispatch/receipt evidence, not native Azure metadata evidence.

Packet verification for this documentation update: PowerShell 7.4.6 on Linux parsed every
PowerShell fence and passed the existing `test_runbook_credentials.ps1` check (two fresh
rounds, five independent identities, partial-generation cleanup). A temporary offline
mock harness executed the exact launcher and extracted section blocks in 12 scenarios:
success; partial resource creation failure; failure, skipped report and missing report at
each of probe/setup/read-only; and cleanup failure. Downstream work stopped at the failed
stage, exact owned cleanup ran, secrets were erased, and cleanup failure produced no false
confirmation. All 12 passed; Azure cmdlets and Maven were mocked, with no Az module loaded
or external connection. These checks do not replace the same-host Windows offline check
in section 1. The marked credential block and native Maven selectors remain unchanged;
only these three evidence Markdown files change. No Java/SQL/receipt or audit schema changed.

## Historical attempts and preparation evidence

Historical catalog follow-up (2026-11-02): owner probe and shared OWNER_PREFLIGHT passed on
`da7933eacc358080014dfba2558473d31c349a47`. Setup then failed in
**PHASE1R_AND_V2_PREREQUISITE**, not platform detection: its inherited preparation
executed the SQL Server/MI-only `sys.database_recovery_status` receipt query. The owner
reconciled the full stack and confirmed deletion (`cleanup=CONFIRMED`, production
access/change false). Never reuse it. The [catalog investigation and scoped identity fix](native-azure-platform-catalog-followup.md)
records the Azure physical-incarnation binding and boxed receipt compatibility.
At that point Windows owner validation was pending and another attempt was BLOCKED.
The owner validation/readiness record above supersedes that blocker; both native gates
remain OPEN and #1929 stays draft and unmerged. Earlier timeout evidence below
remains historical and does not describe this latest failure.

Earlier login follow-up (2026-11-02): the **second owner attempt failed at OWNER_CONNECT** on
`ccf59e0fda6efc2e703b6e06454afc5619d5bc64`: 1 setup test / 0 failures / 1 error / 0 skips,
approximately 15.46 seconds, SQLServerException / SocketTimeoutException during JDBC
`sendLogon`, before a connection was returned. No OWNER_PREFLIGHT, Stage 0, import,
migration or Phase1R/V2 work ran; no setup-sequence file was created. This differs from
the original Stage 0 failure. The owner confirmed resource-group deletion afterward:
`cleanup=CONFIRMED`, `productionAccess=false`, `productionChange=false`. Never reuse that fixture.

The [offline driver investigation and verification](native-azure-owner-login-followup.md)
explains the initial socket/login clamp, adds sanitized connection-failure markers, changes
only the owner login budget to the driver/Azure-recommended 30 seconds, explicitly establishes
the post-login 120000-ms guard, and adds a separate non-mutating owner probe to the
[operator packet](native-azure-readonly-operator-runbook.md). Focused direct JDK/JUnit checks
passed; Maven affected/critical verification was then blocked by dependency/network access.
Both native Azure gates remain OPEN. This historical prerequisite block is superseded
by the completed owner validation above; keep #1929 draft and unmerged.

The owner previously reported all non-Azure Windows security gates PASS: pinned refresh
25/25, clean index, both receipts, focused Azure/V1/V2 contracts, Phase1R local SQL, critical
reactor, package verification and selector-generated affected Maven batch. These earlier
observations do not verify the new follow-up. Historical evidence below retains its original
scope; the original unavailable-environment/cleanup rows describe agent preparation, while
the second real owner's confirmed cleanup is recorded above.

Base: `e129f2733bf7175425299a12ae97efdae3f23d22`, current `codex/latest`, merged #1928.
The #1928 local V2 acceptance retains its recorded scope. Its historical login timeout remains
`UNREPRODUCED HISTORICAL TRANSIENT`; no reproduction or new investigation occurred.

## Actual environment and evidence

The managed environment reported no secret bindings, runtime variables or outbound identities.
No Azure resource or database credential was supplied. No Azure login, resource discovery,
resource creation, Azure SQL connection or production connection was attempted. GitHub source
access is separate from Azure authorization.

| Required native evidence | This task's observation |
| --- | --- |
| Actual Azure edition / product version | Not observed; no connection |
| Disposable subscription / resource ID / database | None created or accessed |
| Contained AI/verifier/auth/runtime/bootstrap identities | None provisioned or tested live |
| Live SID/session authentication and exact tokens | Not collected |
| Denied-authority checks | Not executed; packet requires 33 denied statements and four identity substitutions |
| Portable Case/RLS hash | Expected `6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a`; native result pending |
| V2 source receipt | Expected `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`; native result pending |
| Destination-specific catalog receipt | Native validation pending; never copied/reissued |
| Native representation difference | Unknown; no Azure observation |
| Production access / production changes | No / no |
| Disposable cleanup | Not applicable; no resource created; owner confirmation required after execution |
| READ-ONLY NATIVE AZURE GATE | OPEN |
| DISPOSABLE AZURE MUTATION/FAULT GATE | OPEN, separate |

The private candidate independently matches SHA-256
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
Attachments are evidence, were neither executed nor committed, and cannot override the pasted request.

## Architecture, changes and test-impact review

Reviewed the repository rules, database schema/tenancy, canonical AI/MCP roadmap, Azure
attestation design/acceptance, Phase 3B.7 confinement/local acceptance, V2 design/acceptance,
production preflight, installers/prerequisite/verifiers and cumulative dispatch. Traced production
attestation, the native gate, V1/V2 fixtures and the full Boot readiness test.

| Changed contract | Coverage inspected / reason |
| --- | --- |
| Native gate and configuration | Existing native test extended; three offline safety cases reject production/implicit/alternate/TLS-override targets, reused identities/secrets, wrong version and binding before connection |
| Native authority/routing | `AiCaseDataAttestationTest`, V1/V2 actual-login fixtures and `McpProductionReadinessLocalSqlServerTest`; production attestation unchanged |
| Fixture import/Phase1R | `CaseReadVerificationSafetyTest`, existing authoritative V2 `beforeConfinement` and Phase1R runner; setup reuses those helpers, not local-only lifecycle methods |
| Receipts/representations | V1/V2 migration contracts and V2 Python projections/allowlist; no generator, SQL, hash or canonicalization change |
| Broader regressions | Existing security-data selector wildcard discovers the new tests; affected/critical reactors remain required but dependency-blocked |

The read-only native gate is now limited to a nonce-named disposable database, reserved synthetic
binding and five independent contained principals. It checks actual own-session authentication,
exact tokens, V2 source/catalog/module/RLS/permission closure, zero-row denied reads, dbo
impersonation refusal, cross-database rejection and credential-source alias rejection. It inventories
all application procedures: exactly six AI EXECUTEs, zero verifier EXECUTEs, no bootstrap EXECUTE
on those six. The existing 60-second timeout remains; production attestation must fit ten seconds.
No Case API is invoked; no DML/DDL fault is injected. DML/DDL/role/RLS denial uses existing live
effective-authority closure. An unexpectedly successful session impersonation is immediately
reverted and fails the test. Zero-row SELECTs cannot disclose application rows.

Owner setup is a separate mutating opt-in with an exact database acknowledgement, edition/dbo and
empty-object/user guards. It imports the pinned candidate using the existing safety parser,
applies the seven prerequisites only to this fresh disposable database, reuses authoritative V2
Phase1R preparation, then installs V2 and provisions independent contained users. Owner Stage 0
runs before/after setup: verifier visibility cannot establish scoped-credential absence. No global
or tenant capability, registration, delegated session or OAuth credential is enabled/issued.

Audit compatibility: catalog/reference/identity reads and zero-row denial probes disclose no PHI;
fixture DDL is acceptance infrastructure, not an application business/admin operation. Existing
transactional PHI/integration audits and schema are unchanged. No new audit event or migration.

## Verification actually executed

| Check / selection | Result |
| --- | --- |
| JDK 21 `javac -proc:none` on the three Azure classes, attestation, V1/V2 migration and schema-safety tests, unchanged source paths and cached libraries | PASS, including real transitive fixture/production sources; no stubs |
| Cached JUnit Platform 1.10.3 / Jupiter 5.10.3: the three Azure classes plus `AiCaseDataAttestationTest`, `AiCaseDataMigrationContractTest`, `AiCaseDataV2MigrationContractTest`, `CaseReadVerificationSafetyTest` | 22 discovered, 20 successful, 0 failures, 2 assumption-aborted native/setup tests; no native acceptance |
| `SHALE_CASE_VERIFY_SCHEMA=<private candidate> SHALE_V2_PRODUCTION_CATALOG=<private catalog> python3 -m unittest discover -s build/sql-confinement -p 'test_v2.py'` | 4/4 PASS, no skips; portable projection, not Azure |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s build/test-selection -p 'test_*.py'` | 27/27 PASS |
| `mvn -B -ntp -o -pl shale-data -am -Dtest=AiCaseDataAttestationTest,AiCaseDataV2MigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | No tests executed: Spring Boot 4.1.1 BOM absent from cache |
| Same focused Maven command without `-o` | No tests executed: dependency download reports `Operation not permitted`; Git HTTP separately reports unreachable configured proxy |
| `python3 build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --output <private plan>` | PASS; selected cases/security-data, core/data/server, all three changed Azure tests |
| Selector's `selected_maven_batches`, invoked sequentially with `mvn -B -ntp -o` | Affected suite unexecuted: same missing Spring Boot 4.1.1 BOM |
| `mvn -B -ntp -o test` | Critical reactor unexecuted: same missing BOM |
| `git diff origin/codex/latest --check` and changed-file/test mapping | PASS; nine intended test/documentation files; no production Java/SQL changes |
| Native setup/read-only Maven selectors in runbook | Unexecuted: no authorized Azure scope/credentials |

The first direct JUnit launch used the repository root instead of Maven's `shale-data` working
directory and produced three file-path errors. Correcting only the launcher directory resolved
all three; no assertion/source change was made for those errors. Raw logs remain private outside
Git. Direct JDK/JUnit checks do not replace blocked Maven affected/critical reactors or live Azure.
The selector also includes Case contracts because these changes concern the confined Case boundary.

## Owner closeout

Execute the runbook. Retain private Surefire reports and fresh sanitized `setup-sequence.txt`,
`read-only.properties`, resource-scope and cleanup records. Record actual platform/version,
disposable scope, identities/effective authority, counts/denials, receipts and any representation
difference. Reconcile owner Stage 0 and independent secret-consumer evidence. Confirm deletion
of the exact owned scope/firewall rule before reporting cleanup. Never commit raw SIDs, secrets,
connection strings, tokens or schema/module artifacts.

Do not classify the gate complete on absent opt-in, skipped tests, owner-only/local-engine success,
or missing metadata visibility. No production Phase1R/V2 install, identity provisioning, JAR
deployment, AI/MCP/OAuth activation or pilot is authorized. The seven already-installed production
prerequisites must not be rerun. The PR remains unmerged.


