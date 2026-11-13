# Issue 1969: remaining-stage review

Reviewed against `codex/latest` `b3ab7c0c` (#1968). No live SQL, Azure changes,
private operator inputs, BCrypt values, DPAPI store or acceptance evidence was accessed.
The reported 18/51 checkpoint is retained, not rediscovered by executing prior stages.
Operator commands: [supervised installer guide](../../build/azure-mcp-acceptance/README.md).

## Remaining stage audit

The manifest still contains the same ordered 51 names and sources, including 15a/15b/15c.
All remaining sources and their include closure were reviewed for session requirements,
version ordering, output behavior, roles/permissions, secret/approval inputs and identities.
No historical installer, receipt, module, source fingerprint, role, transaction or guard changed.

| Stage(s) | Finding and disposition |
| --- | --- |
| 15c-seed | Demonstrable Msg 1934 cause: new ODBC SQLCMD connection defaults QUOTED_IDENTIFIER OFF. Seed DML reaches indexed/filtered/computed paths. Render all seven SETs plus GO before unchanged seed source, within approved bytes. Seed still requires empty tables and four independently supplied BCrypt values; transaction/rollback/count checks unchanged. Require new hash approval and independent rollback evidence before any later execution. |
| 16/17/18 binding | Expanded includes execute V2 XML catalog verification on fresh connections. Same complete SET preamble now precedes each source. Unchanged input/target/28-instant validator and immutable single-insert transaction remain authoritative. Missing worker JVM/timezone evidence is a hard STOP before 16; no placeholder zone or app startup. 17 still has separate execution approval. Require exact BindingStatus and zero ConfinementCatalogDriftFindingCount. |
| 19 PR3 preparation | Authority inventory precedes the mutating supplement. XML exists in the catalog chain/dynamic module paths; full session preamble required. This is NOT a read-only stage. Existing binding, neutral dbo, OFF capability and predecessor receipts are prerequisites. Confinement findings must be zero; inventory counts are informational. |
| 20 PR3 | Preserve install → application verify → confinement verify. XML/session preamble before all three unchanged sources. Required named application, confinement and registry finding counts must be present and zero. No catalog re-sealing as recovery. |
| 21 proof / 22 OAuth | XML receipt/catalog paths require the same session setup. Their own assertions throw on drift; source-completion records prove every source returned through its final target/OFF fence. Historical proof/OAuth versions are checked here, before successor installs. |
| 23 reviewer / 24 onboarding | Create exact metadata/accessor and original-WEB-session role contracts after PR3/proof/OAuth. No role fallback or broad grants added. Session preamble covers DDL/indexed paths consistently; existing source assertions remain authoritative. |
| 25 C1 / 26 C2 | C1 XML and successor catalog/module paths need complete session state. Preserve successor order and historical receipt comparisons. C2 creates AiDcrEndpointV3 after C1; do not substitute AiDcrRegistrarV2. Final verification uses DCR_V3 resources, not frozen predecessor checks against successor metadata. |
| 28 contained users | Twelve role-specific contained identities only after roles exist. Require private provisioning approval, 12 unique sufficiently long SQL literals, and independence from the five retained foundation secrets. SQL still rejects existing users/missing roles; no adoption, regrant, server login or secret generation. |
| 30 final owner | Stage-0 platform inventory, DCR_V3 registry/authorization catalogs, then OFF/synthetic/17-user final assertions. XML preamble before each source. Seven specific platform finding counts plus registry finding count must be zero; final preparation status is required. |
| 31 verifier / 32 reviewer | XML catalog batches run under exact independent mcpacc_verifier / mcpacc_reviewer logins, with the unchanged five-second query cap. The reviewer retains its receipt-accessor EXECUTE contract. Required Case/registry findings in 31 must be zero; 32 succeeds only through unchanged catalog assertions and its completion record. No dbo fallback. |
| 33–49 identity checks (17 stages) | Authenticate separately as each exact contained principal. Preserve own-user/public/sole-role token, contained-authentication/SID and permission-denial checks. Require exact identity status and completion. These are authentication/token checks, not full enabled runtime acceptance. |

All remaining rendered sources receive the same seven SET/GO preamble, avoiding reliance on
client defaults or a previous process. Source-completion records are added after each original
final guard. All previously completed rendered stages are unchanged. Offline golden hashes for
all 18 default/synthetic stages were generated with the #1968 renderer and are regression tested;
these are not hashes of operator secrets, real fixture exports or actual accepted private packets.
The operator must independently compare their completed rendered hashes during continuation.

## Supervision and recovery architecture

`Test-Readiness.ps1` is read-only/offline. `Start-SupervisedAcceptance.ps1` owns a foreground
prompt/state machine and calls the existing `Invoke-Stage.ps1`, which retains fixed destination,
SQL authentication, TLS validation, SHA-256 approval, `-x -X1 -b -V 16`, login/query timeouts and
independent-principal checks. Supervised output adds only formatting flags for strict parsing.
No generic SQL path/target/argument escape hatch or credential API is introduced.

Each source must emit its exact ordered completion record; required statuses and named finding
results are checked. Every observed finding column must contain numeric zero. Raw stdout/stderr
is captured in memory and never persisted or printed; only allowlisted result/exit summaries are
recorded. Failure/timeout/incomplete results leave a pending/failed stage, with no automatic retry.
The existing manual wrapper's default preview and explicit execution behavior remain supported.

The separate hash-approved reconciliation script checks target/owner/OFF fences, five contained
foundation identities, object/module/receipt/principal/permission/RLS/index/column metadata and
binding tuple digests, plus tenant/user/Case counts. It does not emit definitions, credentials,
password hashes or row contents. Fresh connections bracket every stage and reconcile resume.
The ledger is append-only and hash chained; an exclusive local lock prevents concurrent runners.
It is not a signed audit trail or a database concurrency lock. Exclude other schema/control/data
maintenance and keep the app stopped throughout the approved window.

Unknown commit outcome requires independent stage-specific read-only verification, explicit
committed/rolled-back classification and exact live snapshot agreement. A rollback must match
pre-execution state; seed disposition also checks the expected counts. Recovery only records the
decision and exits. This human review is intentional: a generic metadata digest cannot prove a
transaction committed correctly or rolled back all data effects. Partial/unknown remains blocked.

Audit compatibility: this local operational workflow adds no application/API mutation or PHI read.
Counts and catalog reads are operational verification; existing governed seed/identity/binding/DDL
behavior is unchanged. Operator approval/stage records stay private, outside application tables.
No new ad-hoc tenant/PHI audit row, audit schema or audit allowlist is introduced. Binding audit
vocabulary remains the separate architecture gap documented in the existing binding readiness
packet. Existing source-owned transactional audits remain authoritative.

## Remaining risks / unverified behavior

- No Azure SQL compilation/execution or Windows ODBC interactive-password integration was run.
  Native errors/driver prompt differences, permission visibility, S0 duration/lock limits and the
  historical restricted catalog failure remain possible. They cause STOP; do not increase timeouts,
  grant broader permissions, disable TLS checks or switch SQLCMD variants as an automatic repair.
- Offline shape/source checks cannot prove live roles, credentials, fixture equivalence, effective
  permissions, worker configuration, all data contents or true independent approval. Keep retained
  independent 18-stage evidence and review each exact hash. Never approve an unknown transaction
  solely because counts or a digest match.
- Worker JVM/timezone evidence is still absent in the supplied checkpoint. The installer cannot
  cross Stage 16 until separately authorized evidence has been reviewed; it cannot produce that
  evidence while the app remains stopped. No such authorization is inferred here.
- SQL preparation is not complete native/runtime/stock-client acceptance or activation approval.
  Shared B1/server resource risks and existing read-only/owner capacity windows remain unchanged.

## Test impact and local verification

Before editing, reviewed `test_packet.py` and `test_phase1r_evidence.py` plus repository-wide
references to Invoke-Stage, Prepare-Packet, 15c-seed and the previous three-stage SET contract.
Renderer changes update the existing session-scope assertions; wrapper argument/hash/identity
refusal tests remain in force. `test_supervised.py` covers readiness blockers, all 18 completed
synthetic hashes, all 33 remaining supervised transitions, exact contained users, approvals,
wrong target/hash, nonzero findings, malformed results, process failure/timeout, interrupted
commit/rollback recovery, no replay/retry, secret suppression, ledger corruption/tampering and
linked paths. The reconciliation SQL remains unexecuted; its native semantics remain unverified.

Verification commands (workspace-local PowerShell 7.4/JDK 21/Maven 3.9.11; session proxy/CA for Maven):

```text
python -m unittest discover -s build/azure-mcp-acceptance -p 'test_*.py' -v
python build/test-selection/select_tests.py --base b3ab7c0c --head HEAD --format json
mvn test
git diff --check
```

The SQL packet documentation selects the existing security-data Maven area and the binding/external
Python checks. Acceptance-installer build paths also produce an informational full-suite suggestion;
this is not an additional completion gate. Direct offline acceptance tests cover the changed executable contracts; the
critical reactor protects existing application/default-OFF behavior. No application production
code, historical SQL installer, module resource, manifest order or receipt was modified.

Results: **55 offline acceptance tests PASS**; **160 selector-affected Maven tests PASS**; the two selector Python checks pass **11 tests**;
**283 critical-reactor tests PASS**, zero failures,
errors or skips. PowerShell AST parsing and Git whitespace checks PASS. The initial Maven
download failure was resolved using the session proxy and existing CA trust, without repository
build changes. No native SQL or Windows ODBC interactive integration result is claimed.
