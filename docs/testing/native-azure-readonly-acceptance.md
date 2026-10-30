# Native Azure identity / read-only acceptance — 2026-10-29

Current [stat-cache follow-up](native-azure-index-stat-followup.md): Windows byte/receipt and
focused Maven validation passed, but clean Git status after the repaired workflow still needs
operator confirmation. Classification remains **BLOCKED**. Native Azure gates remain OPEN.

Latest checkout follow-up: **BLOCKED**, with the real existing-worktree failure reproduced and
[a guarded byte/receipt refresh](native-azure-existing-worktree-checkout.md). The owner confirmed
the failed Azure resource group was deleted before this testing; no Azure connection was retried.

Current follow-up (2026-10-30): **BLOCKED** for operator retry readiness; see
[owner Stage 0 failure, effective-driver proof and Windows correction](native-azure-setup-timeout-followup.md).
The original preparation evidence below is historical. Both native Azure gates remain OPEN.

**OPERATOR ACTION REQUIRED.** Native acceptance has not executed. The repository packet is
prepared; the owner must supply an authorized disposable Azure scope and execute the
[exact PowerShell / Maven runbook](native-azure-readonly-operator-runbook.md). Standard Maven
focused/affected/critical verification also remains unverified in this environment.

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
