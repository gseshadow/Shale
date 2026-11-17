# Automated live MCP acceptance (operator only)

From the reviewed PR checkout in an interactive **PowerShell 7.4+** console, run this one command:

```powershell
.\build\azure-mcp-acceptance\Start-LiveRuntimeAcceptance.ps1
```

The command defaults to the successful existing SQL run
`work/clean-slate-06a67de3fd7340378f9e0db9d6934d89` (51/51 including Stage 21).
Keep its complete directory, rendered packets, private input copies and hash-chained journal in place.
The configuration step reuses their existing 17 contained-user credentials. This successor reads
the evidence; it never resets SQL, reinstalls schema, replays a migration,
provisions users, regrants rights, regenerates receipts, changes binding or edits old evidence.
The historical clean-slate command must **not** be used for this phase.

The only destinations compiled into the runner are subscription
`cd334e90-1e97-4b68-b4e3-80bf7b438546`, resource group `Shale`, Linux App Service
`shale-mcp-acceptance`, and `shale.database.windows.net` / `ShaleMcpAcceptance`.
Production `Shale`, shared plan/server changes and real tenants/data are outside the command.
No Azure deployment or live SQL is executed by Codex. An operator runs this command locally.

## Before running

Use a private console without PowerShell transcripts, command/input logging or debug tracing.
Have Azure CLI already signed into that subscription and Python with the existing MCP harness's
`mcp` **2.3.0** and `httpx2` dependencies installed. Use the established reviewed client environment,
not the older pre-2026 MCP v1 client. The command does not install tools or dependencies.
PowerShell's SqlClient transport is reused from the successful SQL runner: fresh unpooled encrypted,
certificate-validated connections, fixed database/server, seven required SET options, full result
consumption and rollback/disconnect on failure. SQLCMD is not needed.

Keep the app stopped. Use an exclusive maintenance window: the local runner/reset lock cannot
prevent another operator or machine changing settings, running SQL or starting the app.
Do not run a clean-slate/SQL installer or another acceptance campaign concurrently.

Have the reviewed executable server JAR, its **independently recorded SHA-256** and build version.
The command prompts for these inputs, copies the JAR into the protected new run, checks the
hash and packaged build-info, refuses known fixture classes and checks the remote JAR hash
on both dormant and enabled workers. A supplied hash identifies exact reviewed bytes; this is
not a reproducible-build/signature certificate or proof linking arbitrary bytes to a Git commit.
Use the artifact from the separately reviewed server build; never use test fixture providers.

The runner now prepares the existing acceptance app even when its application settings are empty.
After the read-only SQL/native prerequisites pass, it provisions the following acceptance-only settings
and startup configuration, with every AI/MCP control OFF and the app stopped. No configuration command
starts/deploys the app or changes SQL. The existing explicit campaign approval follows preparation.
The state written and checked is:

- Linux Java 21 stack, one worker, HTTPS only, TLS >= 1.2, FTPS disabled; startup exactly
  `java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure`.
- `SPRING_PROFILES_ACTIVE=azure`; `shale.ai.oauth.identity=PRODUCTION` (runtime identity grammar,
  with the acceptance destination). Case `V2`, CIMD `DCR_V3`, database `ShaleMcpAcceptance`.
- All ten [app controls](../../docs/universal-mcp-azure-readiness.md#3-required-azure-configuration-and-acceptance)
  explicitly `false`; `shale.ai.cimd.metadata-advertisement-enabled=false`.
- Public/browser/allowed MCP origin exactly `https://shale-mcp-acceptance.azurewebsites.net`.
  Use that same origin for browser CORS if separately testing the web UI; no wildcard origins.
- Existing dedicated `SHALE_APP_DB_{URL,USER,PASSWORD}` (`mcpacc_auth`) and
  `SHALE_RT_DB_{URL,USER,PASSWORD}` (`mcpacc_runtime`). AI/predecessor/verifier/bootstrap and all
  twelve `shale.ai.cimd.runtime.<key>.{jdbc-url,username,password}` sources map to their exact
  `mcpacc_*` users in the successful run. Runtime setting keys are listed in
  [the runtime architecture](../../docs/architecture/universal-mcp-azure-runtime.md).
- Azure setting names use Spring Boot's environment mapping: replace dots with underscores,
  remove hyphens, uppercase. For example, `shale.ai.mcp.search-cases-enabled` becomes
  `SHALE_AI_MCP_SEARCHCASESENABLED`, and `shale.ai.cimd.runtime.case.jdbc-url` becomes
  `SHALE_AI_CIMD_RUNTIME_CASE_JDBCURL`. All 77 generated settings (including the 51 JDBC
  settings for 17 identities, ten controls, advertisement and logging) use this mapping;
  the established uppercase ordinary-auth/runtime aliases retain their names. Generation rejects
  collisions. Readback, conflict checks, activation, OFF verification and cleanup use the same
  Azure names. Legacy dotted keys and unreviewed AI aliases are refused before writes; the runner
  does not silently migrate or delete existing conflicting settings.
- Destination-only JDBC URL `jdbc:sqlserver://shale.database.windows.net:1433;databaseName=ShaleMcpAcceptance`
  (port and trailing semicolon optional), with no security overrides or embedded credentials.
  Existing Java providers enforce TLS. The runner intentionally rejects alternate URL spellings,
  aliases, connection-string entries and Java/Spring property-source overrides rather than guess precedence.
- Synthetic-only JWT secret (>=32 characters) and explicit session binding cutoff. No production secret
  may be reused. Azure setting values must be inspectable by the operator's authenticated CLI;
  unresolved Key Vault-reference strings cannot be compared to private contained credentials and fail closed.

Have existing acceptance database-owner credentials and the synthetic passwords matching seed users
`admin@example.invalid` (71/7), `badmin@example.invalid` (81/8), and `a72@example.invalid` (72/7).
These are securely requested using `Get-Credential`; no password is a command parameter.
The 17 dedicated contained credentials are read from the successful run's private inputs, with secure
prompts only for missing secrets. Nothing changes those input files or reprovisions SQL identities.
Human passwords/tokens go to the child over redirected stdin and remain in memory, never argv/env/files.
Credentials must belong exclusively to this acceptance fixture. Do not enter production credentials.

The separate synthetic JWT HMAC key is generated with 64 cryptographically random bytes; the
ordinary WEB token TTL is explicitly 3,600 seconds. The session-binding cutoff is fixed at
`2000-01-01T00:00:00Z`, so this fixture accepts no legacy unbound session tokens. No extra OAuth
client secret/signing key is needed for the public DCR flow; SQL credentials and user passwords
are reused, not generated or rotated.

The generated key is retained at `work/live-mcp-configuration/<SQL-journal-hash>.auth.json`, separate
from the successful SQL evidence. Windows encrypts it with DPAPI CurrentUser on that workstation;
other platforms prompt once for a retained, acceptance-only passphrase (20+ characters), deriving
an AES-256-GCM key with PBKDF2-SHA256/210,000 iterations and a random salt. Authenticated encryption
binds material to this app and SQL journal. The directory/file have owner-only ACLs or 0700/0600 modes;
the encryption passphrase/key is never saved. Store paths and new private run directories are Git-ignored.
Reruns with the same successful SQL journal decrypt/reuse the retained key, including after a partial
configuration failure. A wrong passphrase, missing Windows user/machine protection, corrupt store or
insecure permissions fails closed; the runner never silently replaces retained material. Preserve it.

Azure CLI reads app settings and connection strings using fixed `--name shale-mcp-acceptance
--resource-group Shale` arguments, compatible with this installation. Secret-bearing writes use a
fixed-target ARM REST PUT with an Entra bearer and JSON body held only in memory: no `--settings`
password arguments, plaintext request files, CLI debug payloads or secret console/report output.
Startup uses app-only ARM PATCH requests to set Java 21, the existing JAR startup command, TLS/FTPS,
HTTPS and the exact acceptance CORS origin; no plan/server/database resource is modified. Configuration
failure independently attempts app controls OFF and stop and journals their observed status.

Existing conflicting settings (including an unknown JWT secret, wrong database/identity, aliases or
ON controls) are refused before writes. This preserves previously configured material instead of
adopting unverified keys or silently rotating it. Empty or matching partial settings are supported.
Unrelated settings are preserved and existing connection-string entries still require separate review.
After configuration, all required settings/startup fields are read back and SQL is confirmed still OFF.

The command needs existing Kudu **Microsoft Entra bearer** access to the acceptance SCM endpoint and
permission to deploy/start/stop/update acceptance app controls. Publishing-password fallback is absent.
Kudu `/api/command` must support the fixed hash/log commands on this Linux app. Existing container
stdout files under `/home/LogFiles/*docker.log` must contain the application's startup markers.
Missing SCM support, logs, native permissions or prerequisites is a FAIL; no broadening of privileges,
TLS relaxation, fake worker/provider or alternate target is attempted.

## One approval, then automation

Before approval the command validates the SQL completion report, every ordered stage-complete event
and stage-file fingerprint, journal chain, exact JAR/build, Azure resources/startup/configuration,
OFF controls, exact synthetic base rows, immutable binding and its 28 Java/SQL samples, restricted
principal/catalog checks and fresh-session RLS isolation/neutral denial. Only current repository
read-only catalog and identity scripts run; retained rendered SQL is hashed but never executed.
It does not invoke the historical installer, even Stage 21. The secure configuration preparation
described above runs next, and the existing campaign approval is presented only after its readback passes.

Review the displayed fixed target, JAR hash and build. Type **`RUN_SHALE_MCP_ACCEPTANCE` once**.
That single approval authorizes this entire bounded synthetic campaign and its cleanup. There are
no per-stage approval prompts, unattended approval switches or reset/recovery overrides. Missing input
and credential prompts collect prerequisites; they do not authorize deployment. Declining retains the prepared acceptance configuration OFF/stopped; no deployment, application
startup or SQL mutation occurs. All post-approval paths, including repeated preflight failure, enter cleanup.

The runner then:

1. Rechecks the prerequisites/evidence and deploys only that JAR with restart disabled to
   `/home/site/wwwroot/shale-server.jar`. A deployment that unexpectedly starts the app fails.
2. Starts dormant, verifies DB health, deployed hash, actual worker JVM Java 21/zone matching the
   unchanged binding, disabled readiness and closed MCP/OAuth POST routes. Stops again.
3. Revalidates native identity/catalog/binding/RLS prerequisites. Enables only the acceptance SQL
   product/proof/OAuth/external/DCR switches and policies for synthetic tenants 7 and 8; enables
   the ten app controls with CIMD advertisement false. Starts and requires a **fresh**
   `AI_CASE_SQL_READINESS ready` marker, worker zone and exact deployed JAR hash.
4. Runs the Python MCP SDK using the existing authoritative harness's schema/result assertions.
   Verifies HTTPS discovery and protected resource, no anonymous/random/WEB/REFRESH bearer access,
   SDK initialization/protocol/tool discovery, normal WEB login, public DCR, ordinary-user and
   cross-tenant approval denial, separate tenant approval/consent, S256/state binding, wrong verifier
   and consumed-code replay rejection, permitted Case search/overview, cross-tenant/deleted/missing
   Case denial and tenant-argument spoof rejection, refresh/resource binding and rotation,
   credential/family revocation, real expiry and tenant-approval revocation/isolation.
5. Waits for an independent **unrevoked** ACCESS token's natural five-minute lifetime and proves
   it is then denied. It never updates credential dates, changes TTLs or disables constraints.
   A separate fresh, proven-usable family tests approval revocation to avoid confusing it with expiry.
6. Compares new successful disclosures with committed READ/ALLOWED_READ, RETURNED resources,
   exact tenant/Case context, consumed handoffs, released quotas and Overview PHI audit increments;
   requires negative access-audit persistence, unchanged base fixture/binding, then shuts down.

Private reports retain the concrete failed phase/check. Expected denied responses are strict HTTP
status/error assertions: a network exception, arbitrary 500 or broken initialization cannot count as
successful denial. HTTP requests use validated TLS, no ambient proxy or automatic redirects, fixed
origin, deadlines and bounded response size. No callback listener is necessary: authorization redirects
are validated in memory, never followed with a WEB bearer. This is automated HTTP authorization/consent,
not browser UI automation. The child has a 15-minute watchdog and process-tree termination on timeout.

Azure ARM rejections report a closed operation/status/code in the console and preflight report,
for example `ACCEPTANCE_AZURE_SETTINGS_HTTP_400_BAD_REQUEST`. Only allowlisted Azure codes
are retained; unknown or malformed bodies yield `HTTP_ERROR` with the HTTP status. Non-HTTP
failures keep a generic safe code. No response message/body, setting value, URL, header, token or
exception text is included. Configuration failure still independently attempts app controls OFF
and app stop and records whether each was confirmed. There is no automatic retry.

App-setting readback preserves JSON strings exactly, including the session cutoff timestamp and
unrelated date-like values. PowerShell's default JSON parser converts timestamps to date objects;
that conversion previously caused a false readback mismatch after successful Azure writes. The
settings-only parser avoids conversion on PowerShell 7.4 and later. Comparisons remain exact;
no timestamp normalization, setting repair, credential rotation or approval bypass is introduced.
This is operational configuration validation only; existing journal/Azure evidence and audit schemas
remain unchanged. Preserve failed run evidence and use a new operator-approved run after correction.

## Cleanup and evidence

Every approved run finishes through `finally`: revoke issued families/logout original WEB sessions
when the client can run cleanup; independently attempt all five SQL switches and both synthetic tenant
policies OFF, all app/advertisement controls OFF, and Azure app stop. Each SQL switch/policy is attempted
even after another fails. App OFF/stop attempts still run if SQL fails. Fresh reads must confirm SQL OFF,
all app controls OFF and `Stopped`. **Cleanup uncertainty makes the entire report FAIL.**
Historical schema, binding, users, base records, existing credentials and evidence remain; new audited
synthetic approvals, registrations, sessions, credential hashes, access evidence and revocations persist.
No reset/deletion follows a failure. A repeated campaign creates a new DCR client/run; it does not resume
or erase the previous run. Inspect a failure before running again.

New private `work/live-mcp-<guid>/` (owner-only ACL/0700) contains:

- `report.json`: overall PASS/FAIL, approval, failed phase, exact JAR/build, original SQL journal hash,
  per-step status, allowlisted startup markers, independently confirmed cleanup booleans.
- `http-report.json`: individual PASS checks, failed check, protocol/client versions, disclosure and
  Overview counts and credential cleanup. Written incrementally; abrupt child failure preserves the prefix.
- `journal.jsonl`: new hash-chained approval/completion events; no mutation of the successful SQL journal. Configuration events include setting names and
  OFF/stopped outcomes, never values; the generated JWT key is retained only in the separate encrypted store.
- `prior-evidence.json`: fingerprints of every historical evidence/input file; checked again after the run.
- Exact JAR, control booleans, and `sha256.json` for final run files. No raw SQL results, driver exceptions,
  HTTP bodies, patient values, secret settings, plaintext passwords or OAuth tokens are exported.

Before each startup, existing raw container logs are moved, without deletion, into remote
`/home/LogFiles/acceptance-<run-id>-before-dormant/` or `...-dormant/`. This preserves evidence and excludes
stale startup markers. The enabled run's raw logs remain at the original remote log path; the report
retains only closed startup markers. Preserve both local run and remote log archives under the existing
acceptance evidence policy. Do not publish the private packet or raw logs to a PR.

If PowerShell is force-killed, the machine loses power, or Azure/SQL cannot be reached, client-side
`finally` cannot guarantee remote shutdown. Ordinary exceptions, startup/deployment failure and child
timeout are covered. Normal Ctrl+C invokes PowerShell cleanup, but confirm the final report; hard-kill
and native Windows console behavior need operator validation. If `Cleanup` is missing/false, use the
same acceptance-only operator credentials to stop this App Service and restore its ten controls and
five SQL switches plus tenant 7/8 policies OFF through the reviewed seams. Preserve evidence and obtain
current OFF/stopped observations before another run. Do not infer shutdown from a missing process/log,
retry deployment, reset SQL or change a binding to recover.

PASS is scoped to this automated DCR/OAuth/MCP/runtime campaign. It does not certify production,
CIMD remote metadata/JWKS/worker egress, actual browser UI/CORS/callback UX, an independent stock-client
onboarding campaign, quotas/load/NAT fairness or all adversarial audit-fault cases. Those remain separate
items in the [broader acceptance runbook](../../docs/testing/universal-mcp-azure-acceptance-runbook.md).
None of them is silently counted as PASS, and the runner's PASS never enables production.

## Audit compatibility and offline test impact

No Java runtime, SQL schema, receipt, migration, RLS, grant or binding contract changes. Sensitive Case
reads retain the existing PHI/access/resource/handoff audit boundary and are checked against durable SQL.
Tenant policy changes use `AiSetTenantPolicyV1`, which owns its same-transaction entity-action audit and
revocation. The fixed acceptance-owner product toggle uses the existing global audit shape, exclusive
`AiCapabilityV1` lock and atomic product-change/audit transaction, without granting an operator role to
the web application. It is a scoped acceptance administrative seam. The four operational SQL switches
and Azure app lifecycle/settings changes have no tenant PHI audit vocabulary: local hash-chained run
and Azure operational records cover those actions; no fabricated domain event or new audit table.

Configuration successor test impact: `LiveConfiguration.ps1`, the entrypoint, settings read/write and
CLI argument paths map to `test_live_configuration.py` and the existing `test_live_runtime.py` parser,
settings and cleanup tests. Existing MCP driver/SQL preparation contracts stay intact.

Pre-edit inventory: reviewed `test_clean_slate.py`, `test_supervised.py`, `test_fast_track.py`,
`Test-Readiness.ps1`, clean-slate journal/transport tests and the authoritative/acceptance Python MCP
harnesses. New files map to `test_live_runtime.py`; the only existing harness change places its executable
main behind the usual import guard so assertions can be reused without launching historical fixtures.
Its schema/result logic remains unchanged. Existing SQL-preparation tests must continue passing.

Run offline only (no Azure/SQL/native client process in these tests):

```powershell
python -m unittest discover -s build/azure-mcp-acceptance -p 'test_live_*.py' -v
python -m unittest discover -s build/azure-mcp-acceptance -p 'test_*.py'
python build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format json
mvn test
```

Fault-injection tests exercise all campaign phases, single approval/decline, independent cleanup
failures, secret-safe FAIL reports, JAR/hash/build/fixture rejection, SQL journal refusal, configuration
substitution and fixed native read-only source admission. No GitHub Actions testing is added.
Live Azure acceptance is intentionally **NOT RUN by Codex**; only the approving operator can produce
its PASS/FAIL evidence. Missing private successful-run files in a Codex workspace are not rebuilt.

Implementation verification (2026-11-16): 16 new offline regressions PASS; all 150 existing acceptance
regressions verified PASS across the broad run and corrected-tooling packet/supervised reruns;
critical Maven reactor `mvn test` **283 PASS**, zero failures/errors/skips. The first broad run lacked
`javac` on PATH; selecting the existing JDK 21 resolved its packet setup error and three supervised
prerequisite failures (13 packet and 26 supervised tests subsequently PASS). No production/test
expectations were weakened. The repository selector maps these operator-tool paths as unknown and
selects no affected Maven suite; its full-suite suggestion is informational. Diff and relative-link
checks PASS. Azure/SQL/Windows live runtime execution remains operator-only and NOT RUN here.

Configuration successor audit review: preparing Azure settings/startup and retaining a synthetic JWT key
are operational actions recorded in the new private hash-chained journal and Azure activity records.
No tenant/domain or PHI mutation occurs during preparation, so no SQL audit row/schema is introduced.
The approved campaign retains its existing SQL policy/product/read audit semantics.

Configuration successor verification (2026-11-16): `python -m unittest discover -s
build/azure-mcp-acceptance -p 'test_live_*.py' -v` — **26 PASS** (10 configuration regressions and
16 existing live-driver/runtime regressions). `mvn test` via the existing local JDK/Maven wrapper —
**283 PASS**, zero failures/errors/skips. Selector executed against `origin/codex/latest`; no affected
Maven area maps to these operator scripts, and the all-tests suggestion remains informational.
Diff whitespace and relative-link checks PASS. Tests use mock Azure/REST/SQL seams and real portable
AES-GCM encryption on synthetic material. Windows DPAPI and live Azure behavior remain operator-only;
the runner verifies encrypted-store readback before any settings upload. No Azure configuration,
deployment, activation or database mutation was executed by Codex.
