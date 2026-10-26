# Phase 3B.7 SQL confinement implementation — scoped evidence

Date: 2026-10-26. Base: `06f30fa083c1e0e2753fff5d26d9beea991d7be1` (`codex/latest`, merged #1921).
This implements the approved [contract](../architecture/phase3b7-sql-confinement-contract.md).
The two #1919 SQL failures and serving-expiry failure are reproduced against the replacement boundary.
The broader [3B.7 OPEN acceptance ledger](phase3b7-adversarial-acceptance.md) remains paused and unchanged;
this record does **not** mark Phase 3B.7 COMPLETE.

| Classification | Result |
| --- | --- |
| SQL confinement | FIXED for the scoped restricted-login attacks and protected operations below |
| Expiry serving | FIXED for the positively observed blocked final-gate drill through both clients |

## Candidate and provenance

New additive migration: [`phase3b7-confinement.sql`](../sql/ai-integration/phase3b7-confinement.sql).
The [SQL runbook](../sql/ai-integration/README.md#phase-3b7-confined-case-data-candidate) owns configuration,
principal provisioning, verifier prerequisites and containment rollback. Historical migrations are unedited.
The cumulative `verify.sql` dispatches to the latest read-only confinement verifier without replacing old receipts.

The supplied UTF-16 `shale-schema-source.sql` was read without modification and verified before decoding:
SHA-256 `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`, 728862 bytes.
The private derivation retains all 96 authoritative tables, five functions, three schemas, 208 indexes
and 1142 ALTER TABLE batches, with only the reviewed Users/auditLog strict and Statuses overlay filters.
Derived SHA-256: `40a665caefb3203a6c364d0caac3fadcc1a58d24a66a994f48e058b73ae6f243`.
Candidate SHA-256: `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
Neither source nor derived SQL is committed. The reproducible preparation script verifies all three hashes.

All SQL execution used an owned, disposable SQL Server container on `127.0.0.1:14339`, image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, synthetic rows and fresh distinct
SQL logins. No production/Azure SQL or external database was used.

## Boundary and effective authority

`DataSources.aiCaseData()` has an independent lazy Hikari pool, required `shale.ai.data.jdbc-url`,
`.username` and `.password`, protected TLS settings, separate pool metrics/name and independent close/reset.
There is no ordinary runtime/auth/bootstrap fallback. Packaged handlers remain OFF. Enabled isolated test
composition performs live attestation before readiness and each request; runtime/auth/bootstrap connections
are used only for SID comparison, never protected Case reads. Operator roles, setup ownership, fixed roles,
server permissions and indirect routes are also rejected. No startup installer or automatic grant exists.

| Principal | Effective application authority |
| --- | --- |
| AI login | Own SQL user + `public` + `AiCaseDataRuntime` only; six exact application EXECUTEs |
| `AiCaseDataRuntime` | No direct Case/join/PHI/control/credential/invocation SELECT or DML; no legacy/private EXECUTE, ownership, grant option, impersonation or DDL |
| Separate catalog verifier | Read-only definition/dependency/reference-data visibility; separately provisioned server metadata visibility; never the AI data login |
| Ordinary runtime/bootstrap/operator | Existing separately governed identity and behavior; never substituted for the AI data login |

Base SQL public CONNECT/metadata and non-admin default TSQL endpoint rights are explicitly bounded.
Attestation checks actual login tokens/SIDs, database, complete effective object/column/server authority,
all module bodies/owners/call graph, RLS/triggers, portable Case shape, source/catalog receipts and SQL/Java
wall-time mapping. Actual tests reject column grants, broad roles, specific-login impersonation, empty schema
ownership, public views, changed modules, wrong database and reused ordinary SID.

`ProofDigest` defensively copies the existing SHA-256 bytes, has no byte getter, serialization or proof
value equality/hash, exposes only scoped JDBC binding, and clears binding/request buffers in finally.
A separate redacted request owner retains it through cancellation, failure accounting, quota release and
final gate; `VerifiedIntegrationContext` contains no credential bytes. Failure cleanup shares one independent
three-second deadline. Application JDBC bind logging is suppressed; privileged tracing/dumps remain an
explicit operational credential boundary. Raw ACCESS is never sent to SQL.

| Public procedure | Implemented and exercised behavior |
| --- | --- |
| `AiDataAuthenticateV1` | Strict 32-byte ingress, fresh live proof/lineage, canonical deployment, bounded invocation/context metadata; no Case read |
| `AiDataSearchCasesV1` | Fresh proof/scope, durable quota admission, literal normalized query, page/probe, 0–10 children, atomic integration success and post-commit minimized result |
| `AiDataGetCaseOverviewV1` | Fresh proof/scope/quota, exactly eight semantic fields, proof-derived PHI + parent + one child + final live gate in one read transaction |
| `AiDataRecordCaseFailureV1` | Closed failure enums; retained exact proof ownership only; no ALLOWED_READ, Case query or PHI capability; idempotent READ/TERMINAL accounting |
| `AiDataFinishCaseQuotaV1` | Full proof/invocation/lineage binding, concurrency release only; no refund/inventory |
| `AiDataAuthorizeCaseHandoffV1` | Fresh data-free one-shot gate, fixed locks before UTC sample, earliest deadline and conservative remaining lifetime; no transaction during network output |

Only the tiny private provisional lineage locator executes as owner; caller-executed static modules retain
RLS and explicit derived tenant predicates. The locator/private helpers cannot be invoked by the AI login.
The invocation record is immutable ownership/monotonic lifecycle state, never a positive authority cache.

## Direct and behavioral results

| Proof | Observed result |
| --- | --- |
| #1919 context switch: restricted login stamps B/82, direct B Case SELECT | Statement denied, SQL 229 |
| #1919 foreign PHI: restricted login stamps A/72, inserts B/82 history | Statement denied, SQL 229; no rollback-only success probe |
| Direct joined-table/control/credential reads and PHI/integration-audit writes | Denied as actual AI login; no exposed digest |
| All 42 legacy role procedures and private helpers | No effective EXECUTE; direct execution statements denied before binding |
| Arbitrary incoming context with valid A proof | A read/audit only; B remains CASE_UNAVAILABLE; incoming context restored |
| Valid search/Overview | Correct minimized output, quota charges, integration audit and required PHI audit |
| NULL/0/31/33/64-byte, unknown, foreign/revoked/expired proof or wrong invocation | Fail closed; retained revoked proof supports only bounded historical failure/release |
| Suppressed PHI / failed parent / failed child | Entire success group rolls back, no output, admission charge retained and concurrency released |
| Physical A1 → A2 → B1 → A1 reuse / read-only context poison | Actual healthy SPID reused; identities isolated; poisoned JDBC physical connection UUID replaced |
| Executable Strategy B search parity | Every page 0–9, ordering/ties/probe/window, literal escaping, normalization, tenant/deleted/closed policy, nulls, Unicode and local timestamp match ordinary DAO bytes |
| Executable Overview parity | Same eight fields, nullable/unassigned/closed behavior, status/practice/user joins and local timestamp; foreign/missing/deleted identical |
| PHI wall-time mapping | SQL/Java agree across DST boundaries in New York, London and Sydney |
| Expiry serving, internal SQL and both independent MCP clients | Exact gate SPID lock wait observed; database expiry crossed before unlock; same gate samples DB UTC afterward; DENIED/FORBIDDEN, no protected client output |

Committed ALLOWED_READ/PHI records remain after the denied post-commit expiry handoff: they describe server
access, not delivery. The expiry observer captures only stored-procedure statement completion on the exact
SPID, never RPC parameters or a `sql_text` action.

The maintained OAuth code/consent/PKCE flow issues each client credential. TypeScript
`@modelcontextprotocol/client` 2.4.0 and Python `mcp` 2.3.0 use protocol `2026-07-28`, verify the unchanged
two tool schemas, bounded search, eight-field Overview, identical text/structured content and foreign errors.
The old shared-runtime-only synthetic serving configurations now explicitly fail startup; valid serving
proof moved to the authoritative confined fixture. Retired Java Case-port operation-count assertions are
replaced by actual protected SQL/audit proofs. Fine-grained handler/schema tests remain.

## Commands and outcomes

All Maven commands use the repository's cross-module `-am` form and the managed Maven/JDK 21 launcher.
The two real SQL commands require the explicit disposable opt-in, loopback credentials and private hash-gated
schema path; absence/skips are not SQL acceptance.

| Command / selected group | Result |
| --- | --- |
| `mvn -B -pl shale-data -am -Dtest=AiCaseDataLocalSqlServerTest,AiCaseDataMigrationContractTest,AiCaseDataAccessTest,ProofDigestTest,DataSourcesAiCaseDataTest -Dsurefire.failIfNoSpecifiedTests=false test` | 23 PASS, 0 failures/errors/skips; 15 real SQL tests |
| `mvn -B -pl shale-server -am -Dtest=AuthoritativeCaseMcpLocalSqlServerTest,OAuthAuthorizationFlowTest -Dsurefire.failIfNoSpecifiedTests=false test` | 19 PASS, 0 failures/errors/skips; both client expiry drills |
| Targeted actual-statement denial and unknown/malformed-proof follow-up | 2 PASS, 0 failures/errors/skips |
| Focused ordinary WEB/DESKTOP/REST, datasource/PHI and session/DAO regressions across seven modules | 70 PASS, 0 failures/errors/skips |
| `python3 build/test-selection/select_tests.py --base 06f30fa083c1e0e2753fff5d26d9beea991d7be1 --head HEAD` and its standard affected command | 192 PASS, 2 expected opt-in client skips; no failures/errors; SQL-gated classes not counted as proof |
| `mvn -B test` critical reactor, once | 215 PASS, 0 failures/errors/skips; all seven modules SUCCESS |
| `python3 -m unittest discover -s build/test-selection -p 'test_*.py'` | 27 PASS |
| Deterministic generator, authoritative fixture hash reproduction, syntax/resource/source receipts and Git diff checks | PASS |

An initial affected-selector run with **all** SQL opt-ins enabled hit its 360-second cap inside the unchanged
90-test `AiSecurityLocalSqlServerTest`. The new 15-test confinement class had already passed in that run.
The process was stopped under the anti-hang rule; it is not reported as a passing legacy SQL run. The normal
selector environment then passed as above. The focused new SQL/client proofs execute separately with opt-in;
the entire legacy SQL suite and broader Phase 3B.7 matrix were not completed or used to claim acceptance.

Changed-file-to-test review maps pool/config/server composition to datasource/config/session tests;
proof/invocation/reader to holder/fault/real-SQL tests; MCP wiring/deadlines/logging to handler/transport/
deadline/client tests; SQL/build/receipts to static migration plus actual permissions/parity/atomicity tests.
Ordinary Java Case query owners and MCP JSON schemas remain unchanged. There is no full-suite escalation run.

No real tenant/integration enabled. No production/Azure SQL. No deployment. No installer/version publication.
