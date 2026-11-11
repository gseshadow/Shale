# PR 4E: Case disclosure deadline investigation

Stacked on #1953, base `442191e847322a0bfbaafe10291195e455841cc7`.

**Status: BLOCKED. No deadline-safe complete runtime acceptance and no production change.**
The existing full-verification composition exceeds the current deadline on the approved disposable
boxed profile. This draft records measurements and tests the proposed existing lifecycle boundary;
it does not turn an isolated fast SQL proof control into accepted runtime readiness.
CIMD remains OFF, unadvertised and unwired from HTTP/browser providers.

## Contracts and test impact

Reviewed repository rules, schema/tenancy/RLS/system guidance, the
[PR 4D handoff](universal-mcp-pr4d-catalog.md), [PR 4A proof](universal-mcp-pr4a-proof.md),
[original confinement contract](phase3b7-sql-confinement-contract.md),
[V2 addendum](phase3b8-confinement-v2.md), runtime/attestor, request-owned proof, JDBC read/drain/close,
immutable disclosure buffer, invocation deadlines and existing MCP composition/deadline wrappers.
Inspected AiCimdRuntimeTest, AiCaseDataAccessTest, IntegrationCaseReadTest, actual runtime/catalog and
Case/proof/lifecycle fixtures before adding the focused test. Searches included runtime constructors,
verify/lease/authenticate/reader calls, source/catalog receipts, SQL lock and invocation fields,
revocation/approval decisions, committed audit/resource joins and final handoff permit checks.

Only a new explicitly selected SQL diagnostic/control class and this handoff are added. All production
Java, historical SQL/generators/resources, verifiers/receipts, pools, deadlines and bindings remain
unchanged. Existing configuration, source, confinement and server-route tests remain applicable;
the selector's security-data/core/data/server checks and default critical suite provide regression
coverage. No all-tests or unrelated historical SQL campaign is needed for this investigation.

## Which boundary establishes which invariant

| Stage | Required checks and ownership | Current implementation |
| --- | --- | --- |
| Startup/rebuild/rotation | Complete dedicated configuration/TLS, nine actual distinct identities versus four forbidden identities, source/schema/receipt/binding/platform, effective grants/columns/roles/ownership/graphs, system and cross-database authority | AiCimdRuntime.open runs full AiCimdRuntimeAttestation; any failure closes pools |
| Acquisition | Fresh neutral autocommit lease, expected actual identity/context, allowed capability; uncertain completion or bad restoration evicts connection | Every runtime lease currently invokes full nine-identity/catalog verification before borrowing; close checks transaction state and restores context |
| Admission | Original monotonic deadline; exact digest and original live WEB/family/application/publication/approval/revision/scope/expiry; proof-derived tenant/user and bound invocation | AiCimdDataAuthenticateV1 owns its transaction and creates no Case success audit |
| Data admission/read | Fresh proof and live authority again under ordered locks, quota reservation independently committed, Case/RLS/tenant projection, PHI + READ/RETURNED audit committed together, completion/drain/close before encoding | Unchanged namespaced Search/Overview procedures; Java never opens an ambient transaction |
| Final disclosure | Fresh proof/live authorization and revision/expiry checks after all SQL waits; committed read and active reservation; single-use handoff, quota release; DB UTC sample and commit; drain/close and conservative elapsed permit subtraction; original Java deadline | AiCimdDataAuthorizeCaseHandoffV1 plus AiCaseDataAccess; no further SQL/network wait after the successful internal handoff |
| Failure | Retained exact proof, separate bounded failure/release accounting; no sensitive read replay or partial bytes | Existing failure/cleanup roots; current full verification also runs on cleanup leases |

The original Case architecture and McpAiCaseData run complete attestation at startup and admission;
they then use fresh SQL proof at protected phases. That lifecycle independently verifies **business
authorization**, not a catalog/DCL revision token. Its invocation record is explicitly not an
authorization cache. AiCapabilityV1, tenant and authority-row locks serialize the documented business
mutators. AiSchemaV1 is used by cooperative installers, not by arbitrary ALTER/GRANT/role/server or
other-database changes. A JDBC lease, certificate-valid TLS session or readonly receipt does not
freeze those catalogs. No existing request-owned verified catalog epoch/fence was found.

Moving full verification to admission only would therefore be a security change, even with no TTL:
a request-local positive result would survive an unexpected catalog/grant mutation between read and
handoff. The current separate per-lease checks also do not claim atomic protection against an
unrestricted SQL administrator racing the tiny check/use interval. This draft must not expand that
interval across the entire proof/read/disclosure lifecycle or claim the existing SQL proof supplies
a missing catalog fence.

## Measurements and the reproduced failure

Local JDK 21/Maven 3.9.11, SQL Server 2022 pinned to the same approved image/build as PR 4D,
loopback TLS with certificate validation, actual restricted logins, approved authoritative fixture
SHA-256 `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
No HTTP endpoint, Azure resource or production SQL is involved. Startup/fixture construction is
excluded from the request measurements; cleanup is included in the explicitly labeled failure total.
These are observed local diagnostic samples, not representative-load percentiles or a hardware-
independent impossibility proof.

| Measurement | Milliseconds | Meaning |
| --- | ---: | --- |
| Full runtime admission | 6,271 | Complete full lease verification plus authentication |
| Strict complete-request diagnostic failure, including cleanup | 18,487 | Original 8-second monotonic deadline supplied before authentication; no protected buffer returned |
| Full nine-identity attestation profile | 7,347 | Independent profile, including connection/session/effective-permission work |
| Signed V2 gate within that profile | 4,997 | From execution through result drainage/statement close; clock-only JDBC decorator, no proof/SQL parameter capture |
| Isolated audited SQL proof/read/handoff control | 330 | Final control, actual Case bytes and committed audits; NOT full runtime acceptance |
| Concurrent isolated tenant controls | 376 / 379 | Two different tenant proofs; NOT full runtime concurrency acceptance |
| Unaffected tenant after A revocation | 48 | Isolated B control; no changed A authority is reused |

The positive diagnostic retains a positive assertion for actual Case bytes and committed audits. It
fails on the current runtime; no assertion was relaxed to manufacture a success. Authentication
calculates the SQL request deadline before acquiring its lease. SQL clamps proof lifetime to ten
seconds, while the diagnostic retains the original eight-second monotonic request/read bound.
A second approximately six-second full verification before the read already consumes the remaining
budget; another before final handoff cannot fix it. Successful startup does not confer a new proof
lifetime. A fresh eight-second budget after admission would discard elapsed request work and is not
an acceptable fix.

A statistics-only development probe additionally measured 7,535 ms full / 5,159 ms gate. Its SQL
statistics had nested parse/execution samples (1,712 ms parse CPU and 12,165 ms execution CPU summed
over 413 overlapping reports); those sums are not additive wall time. No single dominant removable
query was established. The final diagnostic keeps only clock instrumentation; no tracing capability,
plan/source/parameter capture or statistics setting is added to production.

The nominal three-second cleanup path is also not a complete connection-acquisition bound here:
its datasource can first run the same full attestor. The 18,487-ms failure total is not a passing
request or cleanup deadline. Deadline propagation/abort could reduce failed-request latency, but
cannot turn this composition into a successful disclosure or replace verification.

## Focused controls and security consequence

The three explicitly selected final control methods passed with zero failures/errors/skips.
They distinguish:

- A real restricted proof/read/handoff positive control, with actual returned Case ID, committed
  PHI/READ/RETURNED audit, consumed handoff and released, nonrefunded row-cost reservation.
- A deliberately isolated counterexample to the proposed admission-only shortcut: add a column
  SELECT grant after committed read/encoding, observe the same fresh proof handoff, and require the
  full attestor to reject that state. The unsafe datasource is test-only and never a runtime adapter.
- Current runtime refusal of expired admission, unexpected effective column permission and a new
  Case trigger, with no proof ledger/Case success audit and permanent runtime closure after drift.
- Concurrent isolated same-API reads across two tenants; revocation after committed read denies
  final handoff and preserves audit history, denies subsequent A proof and preserves B; elapsed
  Java disclosure deadline withholds bytes and leaves handoff unconsumed.

SQL lifecycle control success demonstrates the existing proof/audit transactions have enough local
headroom. It does not justify omitting required catalog checks or establish runtime concurrency
acceptance. No production shortcut, stale grant, TTL/cache, changed quota/audit, longer proof/read
budget or altered transaction boundary is supplied.

## Decision and architectural alternatives

There is no demonstrated safe small runtime change using the existing lifecycle boundary. Preserve
enforcement and leave complete serving readiness blocked. This is a statement about the measured
implementation and available verified boundary, not a proof that future SQL/Java optimization can
never meet the deadline.

A next scoped design should choose and verify one of these before changing runtime admission:

1. **Optimize complete verification without changing its invariant.** Profile/review a separately
   versioned verifier or execution strategy that materially lowers the entire per-phase cost,
   preserves every historical verifier/receipt and every current effective-authority/catalog check,
   and proves equivalence with targeted mutations. Benchmark a whole original-deadline request,
   concurrent requests and bounded failure cleanup. Faster hardware alone is not acceptance evidence.
2. **Introduce a separately verified catalog-change fence.** Establish complete, current catalog and
   identity evidence at admission and a cheap fresh, unforgeable, request/session-bound revision at
   every protected boundary. Every relevant database/server/system/cross-database DDL/DCL, role,
   ownership, signature, receipt and binding change must atomically invalidate or block the active
   lease before protected use. Validate missing/disabled guards, concurrent mutation, rollback,
   rotation and reconstruction. Mere application locks, change notifications, periodic polling or
   comparing a stale receipt cannot establish this invariant. Keep fresh SQL proof/revocation and
   final disclosure checks; do not cache business authorization.

The second option needs a new catalog lifecycle/provisioning/security contract across scopes that
PR 4D verifies; it is not an incidental request-local boolean. Neither option authorizes production
changes, broad runtime permissions, TRUSTWORTHY, unrestricted impersonation, old receipt resealing,
CIMD activation, HTTP/browser/DCR/Settings/stock-client work or automatic merge.

## Audit compatibility and remaining dependencies

No new audit schema/event/allowlist is introduced. Positive isolated controls must verify existing
atomic PHI, integration READ/RETURNED and reservation/handoff facts. ALLOWED_READ records server
access, not network delivery; post-read revocation/deadline failure retains that history and uses the
existing terminal failure accounting. Catalog/profile observations contain only timings/stage names,
not Case payloads, proof bytes, credentials, external metadata or SQL exception text. Catalog read-only
verification itself has no PHI/business mutation audit event. Disposable drift DDL is owner-only test
fault injection and is restored or discarded with the nonce database.

Native Azure SQL Database remains independently unaccepted and explicitly refused by the boxed V2
catalog configuration. Both native restricted catalog/read-only and disposable mutation/fault gates
remain OPEN. Any new fence or optimized verifier needs its own contained-user/catalog design and
independent native acceptance; SQL Server results do not transfer. Existing native diagnostic stop
and production boundaries remain unchanged.

Before HTTP integration: accepted complete deadline-safe runtime/audited handoff and failure cleanup,
concurrent workload/drift/revocation acceptance, separately accepted Azure contract, and the existing
provider routing, metadata/original-WEB lifecycle, source/artifact/configuration/binding/ingress/
logging/operator/legal/tenant gates. No live routing or advertisement is introduced here.

## Final verification and reproduction

Before/after result: **no production change was applied**, so there is no passing after-runtime timing.
The unchanged PR 4D positive diagnostic was explicitly rerun and still failed closed. The new stricter
complete-request diagnostic also failed before any protected buffer was returned. Its positive Case
and audit assertions remain positive requirements; they are not replaced by an expected-failure
assertion or counted as passing controls. Production readiness remains unverified/blocked.

| Final evidence | Result |
| --- | --- |
| Existing catalogCaseDisclosureBudgetDiagnostic | 1 ERROR (IntegrationReadException), required positive acceptance still FAILS |
| New completeAuditedDisclosureWithinOriginalDeadlines | 1 ERROR during measured baseline, required positive acceptance still FAILS |
| catalogCostProfile development runs | PASS; overlapping timing probes, not request acceptance |
| Three final explicitly selected SQL controls | 3 PASS, zero failures/errors/skips |
| AiCimdRuntimeTest / AiCaseDataAccessTest / IntegrationCaseReadTest | 52 PASS |
| Selector security-data/core/data/server suite | 162 PASS |
| Default critical reactor | 254 PASS |
| Existing SQL/resources/confinement build inputs | All 82 files byte-identical to #1953 |
| Independent certificate-validated cleanup | Zero user databases, nonce logins, V2 signers and master certificates |

All fixture hashes were checked before and after execution. The dedicated container and generated
passwords, TLS private keys and private truststores were removed after independent cleanup inspection.
No native Azure, HTTP/browser, representative runtime load or unrelated historical SQL suite ran.
Controls are intentionally selected by method because the reused fixture inherits historical tests;
no run of the entire opt-in class is claimed. The default regression commands omit all SQL opt-ins.
A development audit assertion incorrectly named the existing child FK; it was corrected to the
verified EventId schema before the final three controls passed. No product SQL/schema changed.

Normalized commands (same executed selections; local JDK/Maven repository/TLS wrappers omitted):

```sh
# Actual authoritative fixture harness/TLS and approved hash are required for SQL commands.
# Required full-runtime positives currently fail; keep them as failing acceptance gates.
mvn -pl shale-data -am '-Dtest=AiCimdDeadlineSqlServerTest#completeAuditedDisclosureWithinOriginalDeadlines' -Dsurefire.failIfNoSpecifiedTests=false test
SHALE_CIMD_CASE_BUDGET_OPT_IN=LOCAL_DISPOSABLE_CIMD_BUDGET mvn -pl shale-data -am '-Dtest=AiCimdCatalogV2SqlServerTest#catalogCaseDisclosureBudgetDiagnostic' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-data -am '-Dtest=AiCimdDeadlineSqlServerTest#catalogCostProfile' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-data -am '-Dtest=AiCimdDeadlineSqlServerTest#liveProofBoundaryDoesNotFencePermissionDrift+runtimeRefusesExpiredAdmissionAndPermissionOrCatalogDrift+proofControlsPreserveConcurrencyRevocationAndDeadline' -Dsurefire.failIfNoSpecifiedTests=false test
# No SQL opt-ins for these regression commands.
mvn -pl shale-data -am -Dtest=AiCimdRuntimeTest,AiCaseDataAccessTest,IntegrationCaseReadTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 build/test-selection/select_tests.py --base 442191e847322a0bfbaafe10291195e455841cc7 --head HEAD --plan-output work/pr4e/selection.json
# Execute the emitted selected_maven_batches (security-data and core/data/server dependencies).
mvn test
git diff --check 442191e847322a0bfbaafe10291195e455841cc7
```
