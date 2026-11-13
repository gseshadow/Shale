# Shared Azure acceptance — next operator steps

**18/51 continuation:** the [supervised local installer](../../build/azure-mcp-acceptance/README.md) now provides offline readiness,
per-stage approvals and an append-only recovery ledger. Stage 15c requires a newly reviewed hash;
completed stages stay completed. The pre-Stage-16 worker/JVM/timezone gate remains mandatory.
This is preparation only, not authorization to retry SQL or start/deploy the app.


**Preparation only, November 13, 2026.** Reviewed merged PR #1963 from `codex/latest`
`17840f66`. No Azure discovery, start/restart, deployment, SQL connection, migration, user creation,
firewall change or activation ran for this review. Production `shale-api` and database `Shale`
are off-limits. These commands are prepared for an authorized operator, not executed here.

**Next:** review the read-only test-app/shared-metadata commands in section 2 and local SQLCMD
selection in section 4; retain the reported successful current-target connection as evidence.
Leave the test app stopped and all controls OFF. Complete fixture/owner/stage approvals before
SQL execution; defer worker zone and immutable binding until the separately authorized runtime
window. Runtime commands below are conditional instructions, not a start/deploy request.

## 1. Actual scope and risk boundaries

Operator-confirmed: subscription `cd334e90-1e97-4b68-b4e3-80bf7b438546`, group `Shale`,
Linux test App Service `shale-mcp-acceptance` **Stopped**, existing shared Linux B1 plan
`shale-api-plan`, SQL `shale.database.windows.net` / `ShaleMcpAcceptance` Standard S0.
Collation remains `SQL_Latin1_General_CP1_CI_AS` per the preceding operator confirmation.

All IDs use prefix `/subscriptions/cd334e90-1e97-4b68-b4e3-80bf7b438546/resourceGroups/Shale/providers/`:

| Resource | Exact suffix / disposition |
| --- | --- |
| Test app | `Microsoft.Web/sites/shale-mcp-acceptance`; only app in this execution scope |
| Shared plan | `Microsoft.Web/serverfarms/shale-api-plan`; metadata only, never resize/restart/delete/configure |
| Shared SQL server | `Microsoft.Sql/servers/shale`; no firewall/security/admin/network changes |
| Test DB | `Microsoft.Sql/servers/shale/databases/ShaleMcpAcceptance`; sole later SQL target |
| HTTPS origin | `https://shale-mcp-acceptance.azurewebsites.net` |
| Immutable issuer / resource | origin + `/oauth/mcp` / origin + `/mcp` |

The choice to share plan/server is deliberate. No separate group, dedicated plan, new SQL server
or Static Web App is required for SQL installation and dormant runtime/time-zone checks. An S0
DB on the shared server has its own DTU limits when not pooled; verify absence of an elastic pool.

- B1 apps share worker CPU, memory, disk, networking and connection/SNAT capacity. Starting the test
  app, JVM heap/GC, building/extracting files and concurrent calls can degrade production even with
  separate app settings/processes. Plan scaling/configuration has a wider blast radius. Use an
  owner-approved window, measured headroom and explicit abort thresholds; begin with serial probes.
  The runbook's 2/4-call scenarios need separate shared-capacity approval; no stress campaign.
- SQL server firewall/public/VNet/private-endpoint rules apply to access paths for both DBs.
  An existing broad rule increases exposure but grants no SQL permissions by itself. Use only an
  existing approved route; no “allow Azure services,” IP additions or security-policy changes.
  Server/Entra administrators, DNS, platform/network failures, quotas and regional dependencies remain
  shared. Contained users do not protect against server administrators.
- Pin exact app/DB IDs, use independent test credentials/JWT keys and the 17 existing contained-role
  contracts. Never copy production settings/secrets, access its SQL to prove denials, query its app,
  swap slots/traffic or delete shared resources. Obtain health signals from existing owner monitoring,
  not new requests to production. Stop test work if shared capacity/owner thresholds are exceeded.

This configuration can establish native SQL and single-test-app behavior at the recorded load.
It cannot establish dedicated worker/server/network/admin isolation, a production capacity/SLA,
production-schema safety or multiworker acceptance. Sharing is recorded, not an outstanding demand
for separate infrastructure; each live operation still needs its own authorization.

A Static Web App is optional hosting, **not** a prerequisite for this OFF-only phase. Full browser/
stock Hermes acceptance still needs the real Shale login/consent UI, deep-link return, browser origin,
CORS/TLS and callbacks. The current API JAR does not establish that the React build is served at this
origin. Later select an approved existing isolated HTTPS frontend with reachable callbacks and verify
its exact origin. `azure`/`PRODUCTION` browser-origin grammar rejects localhost, loopback and .test
origins; a local development UI is not an eligible substitution. Do not use the production frontend or invent UI cohosting.
Until that path passes, browser/stock-client acceptance remains OPEN.

## 2. Next read-only Azure metadata commands (PowerShell 7)

Use an existing authorized Azure session. This reads only the test app and explicitly shared
metadata. It never lists App Settings, connection strings, publishing profiles or secrets.

```powershell
$ErrorActionPreference = 'Stop'
$Sub = 'cd334e90-1e97-4b68-b4e3-80bf7b438546'
$Prefix = "/subscriptions/$Sub/resourceGroups/Shale/providers/"
$TestAppId = $Prefix + 'Microsoft.Web/sites/shale-mcp-acceptance'
$SharedPlanId = $Prefix + 'Microsoft.Web/serverfarms/shale-api-plan'
function Read-AcceptanceAzureJson {
    param([string[]]$CliArgs)
    $json = & az @CliArgs --subscription $Sub --only-show-errors --output json
    if ($LASTEXITCODE -ne 0) { throw 'STOP: Azure metadata read failed' }
    $json | ConvertFrom-Json
}
$account = Read-AcceptanceAzureJson -CliArgs @('account','show','--query','{subscription:id,tenant:tenantId}')
if ($account.subscription -ne $Sub) { throw 'STOP: subscription mismatch' }
$account | ConvertTo-Json
$site = Read-AcceptanceAzureJson -CliArgs @('webapp','show','-g','Shale','-n','shale-mcp-acceptance',
    '--query','{id:id,state:state,kind:kind,httpsOnly:httpsOnly,hostNames:hostNames,plan:serverFarmId}')
if ($site.id -ine $TestAppId -or $site.plan -ine $SharedPlanId -or
    $site.hostNames -notcontains 'shale-mcp-acceptance.azurewebsites.net') { throw 'STOP: test app/plan/hostname mismatch' }
$site | ConvertTo-Json -Depth 4
Read-AcceptanceAzureJson -CliArgs @('webapp','config','show','-g','Shale','-n','shale-mcp-acceptance',
    '--query','{linuxFxVersion:linuxFxVersion,javaVersion:javaVersion,javaContainer:javaContainer,javaContainerVersion:javaContainerVersion,alwaysOn:alwaysOn}') | ConvertTo-Json
$plan = Read-AcceptanceAzureJson -CliArgs @('appservice','plan','show','-g','Shale','-n','shale-api-plan',
    '--query','{id:id,kind:kind,reserved:reserved,sku:sku,workers:numberOfWorkers,perSiteScaling:perSiteScaling}')
if ($plan.id -ine $SharedPlanId -or $plan.sku.name -ne 'B1' -or $plan.reserved -ne $true) { throw 'STOP: expected shared Linux B1' }
$plan | ConvertTo-Json -Depth 4
$db = Read-AcceptanceAzureJson -CliArgs @('sql','db','show','-g','Shale','-s','shale','-n','ShaleMcpAcceptance',
    '--query','{id:id,status:status,sku:sku,collation:collation,elasticPoolId:elasticPoolId}')
if ($db.id -ine ($Prefix + 'Microsoft.Sql/servers/shale/databases/ShaleMcpAcceptance') -or
    $db.status -ne 'Online' -or $db.sku.name -ne 'S0' -or $db.sku.tier -ne 'Standard' -or
    $db.collation -cne 'SQL_Latin1_General_CP1_CI_AS' -or $db.elasticPoolId) { throw 'STOP: SQL metadata mismatch' }
$db | ConvertTo-Json -Depth 4
```

Compare the returned tenant against approved subscription records; it was not supplied here.
Configured stack is not an observed running Java version. Stopped app/absent instances establishes
neither JVM zone nor ordinary app compatibility. Leave it stopped in this task. No command here
starts the app, installs Java, uploads a JAR, adjusts the plan or changes its settings.

## 3. Runtime/time-zone evidence — pause while stopped

Before any future start, the operator must separately approve test-app startup/possible ordinary
SQL connections, exact existing artifact and rollback hash, acceptance-only JDBC destinations,
compatible ordinary schema/users, independent secrets, existing network path, and shared B1 headroom.
All ten effective app controls from the [readiness plan](../universal-mcp-azure-readiness.md) must be
false, optional CIMD advertisement false, every installed SQL control zero. Do not start a copied
production configuration to discover its destination. The existing startup marker covers only eight
historical controls; CIMD/DCR need separate allowlisted effective-property evidence, not a settings dump.

Once the operator has separately made **only the test app** available, use its existing approved
SSH/Kudu access. Do not enable access or retrieve publishing credentials as a workaround:

```powershell
# Future worker inspection only; requires an already running, separately approved test app.
az webapp ssh --subscription cd334e90-1e97-4b68-b4e3-80bf7b438546 --resource-group Shale --name shale-mcp-acceptance
if ($LASTEXITCODE -ne 0) { throw 'STOP: no approved test-worker shell' }
```

In that Linux **application worker/container**, not a local terminal or separate SCM/sidecar JVM:

```bash
set -euo pipefail
java -version
command -v jshell
command -v sha256sum
sha256sum /home/site/wwwroot/shale-server.jar
# Set this to one reviewed current test-app log path obtained from existing logging records.
# Print only the allowlisted startup marker; never cat/tail the whole log or process environment.
rg --only-matching 'SHALE_JVM_STARTUP zoneId=[A-Za-z0-9_+./:-]{1,128} javaVersion=[A-Za-z0-9_.+-]{1,128} replicaId=[A-Za-z0-9_-]{1,128}' "$TEST_STARTUP_LOG"
```

If `rg` is absent, extract only this marker from existing logs through the approved log viewer;
no package installation is implied. Associate its time/release/worker ID with the current test app
and artifact. Missing/old/`unavailable` marker means STOP before binding. The marker reports the
**running server JVM's** zone, unlike OS `date`, `TZ`, `WEBSITE_TIME_ZONE`, or a newly launched JVM.
Do not dump `ps` command lines, `/proc`, `jcmd VM.system_properties`, environments or startup arguments.

After recording that observed zone, use the existing [BindingTimeZoneProbe](../../build/deployment-binding/BindingTimeZoneProbe.java)
on the same JDK in that worker with the same approved effective time-zone inputs:

```bash
# Operator-supplied absolute path to the reviewed existing probe; no upload occurs in this task.
java "$REVIEWED_BINDING_PROBE"
```

Its `Observed JavaTimeZone` must equal the current `SHALE_JVM_STARTUP zoneId`, and JDK versions must
match. A mismatch means STOP and review, never force UTC or change JVM flags to pass. Retain all 28
sample rows; they are corroborating mapping evidence, not proof of another JVM's zone. No compiler
on the worker or unavailable source-launch support is a missing prerequisite, not permission to
install tools. Existing same-worker default `CimdMetadataService` cold egress probes must still pass
before binding (runbook section 3); a workstation/SCM probe or HTTP curl is not equivalent.

The [packet's binding proposal](../sql/ai-integration/azure-acceptance/binding-inputs.sql) now supplies
only known server/database and exact test origin/issuer/resource. Zones remain NULL, approvals zero,
28 samples absent. The rendered packet additionally rejects any different origin/database tuple.
Private files from #1963 are not overwritten: compare/reconcile those proposals, then re-render with
the original hash-gated fixture and approve new hashes. Shared binding session/validate/install/
postcheck remains unchanged. An existing conflicting binding is STOP; no update/delete/resealing.

## 4. SQLCMD failure investigation without connecting

**Operator-reported follow-up:** both SSMS and **Go SQLCMD v1.10.0** successfully returned
`ShaleMcpAcceptance` from `SELECT DB_NAME() AS ConnectedDatabase;`. SQLCMD used SQL Server
Authentication, an explicit `ShaleMcpAcceptance` target and an interactive password prompt.
Executable: `C:\Program Files\SqlCmd\sqlcmd.exe`. No migrations, data or permission changes
were reported. These are accepted existing connectivity observations, not checks rerun in this PR.

Earlier Go SQLCMD reported `mssql: login error: Database 'Shale_Copy' ... is not currently available.
Please retry the connection later.`; a similar error named `auth-invocation-test`. Neither failure
named the current acceptance DB. SQLSTATE/error number were not reported. Same workstation appears
likely but is not independently verified; SSMS account/authentication mode and both clients' exact
TLS/certificate settings were not confirmed. Therefore the earlier cause is **undetermined**, and
those failures are **no longer reproducible against ShaleMcpAcceptance** based on the follow-up.
Do not infer an inactive DB, firewall, password, TLS defect or numeric error code from this message.
Do not retry or inspect either old database to explain it.

The evidence supports connectivity to the correct current target. It does not establish an empty
catalog, owner permissions, validated-TLS parity, migration compatibility or packet success. In
particular #1963 specifies **ODBC SQLCMD**, but its wrapper resolves `sqlcmd` through PowerShell/PATH:
with the reported installation that may be Go v1.10.0. This is a **tool-selection gap**, not a proven
cause of the earlier database-unavailable errors. Resolve an approved ODBC 17/18 executable and
verify its help/flags before installation; a Go client succeeding with one SELECT does not validate
ODBC-oriented `-X1/-x/-f` packet semantics. Go installation support would need a separate bounded
compatibility review, not a silent flag/authentication change in this reconciliation.

The older [Stage 0 record](../production-mcp-deployment-preflight.md#stage-0-read-only-inventory)
reports historical SQLCMD success on a different production scope, never a command/credential
source here. The historical PowerShell 5.1
[argument-serialization failure](auth-module-permission-cutover.md#odbc-argument-serialization-correction--2026-10-16)
was a separate tooling issue and does not diagnose this Azure error. Do not rerun either campaign.

These local-only PowerShell 7 commands do **not** connect:

```powershell
$PSVersionTable.PSVersion
Get-Command sqlcmd -All | Select-Object CommandType,Name,Source,Version
Get-OdbcDriver | Where-Object Name -Like '*SQL Server*' | Select-Object Name,Platform,Version
# Windows-only inventory above may omit driver version; retain its installed package version privately.
# The reported C:\Program Files\SqlCmd\sqlcmd.exe is Go v1.10.0, not the packet's ODBC client.
# Identify the approved ODBC executable's exact path; do not assume PATH selects it.
$SqlcmdExe = Read-Host 'Reviewed Microsoft ODBC SQLCMD executable path'
& $SqlcmdExe '-?'
# Help can return nonzero; inspect version/options only, not a connection-success signal.
```

For a future authorized packet invocation, explicitly bind the reviewed ODBC path in that private
PowerShell 7 session (no server/configuration change). Refuse an unexpected existing alias/function;
verify the ODBC help banner/version before choosing the path. This does not establish TLS success:

```powershell
# Only after selecting/approving the ODBC executable above; no SQL connection.
$ResolvedSqlcmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
if ($ResolvedSqlcmd -and $ResolvedSqlcmd.CommandType -in @('Alias','Function')) {
    throw 'STOP: unexpected SQLCMD command override; use a clean operator shell'
}
Set-Alias -Name sqlcmd -Value $SqlcmdExe -Scope Global
Get-Command sqlcmd | Select-Object Name,Definition
# Invoke-Stage later resolves this explicit alias; do not run -Execute in this task.
```

On non-Windows use local package/version inventory instead of `Get-OdbcDriver`. Compare:

| Failure class / evidence | Investigation and next decision (no connection now) |
| --- | --- |
| Local parsing/unknown option | Resolve Go vs ODBC client, executable/driver version, PowerShell 7 native argument handling and exact flags. #1963 expects ODBC support for `-N -b -V 16 -X1 -x -f 65001`; do not silently interchange variants. `-X1` ignores SQLCMD environment variables/startup scripts; relying on SQLCMDPASSWORD, SQLCMDUSER or SQLCMDSERVER is inconsistent with the explicit prompt/destination contract. Password is not supplied on the command line. |
| Login/authentication or database-open error | Privately compare exact server and explicit DB `ShaleMcpAcceptance`, SQL vs Entra/Windows auth, approved independent user and authorization. SSMS Entra/integrated login does not prove a SQL-auth `-U` credential works. Contained users need explicit `-d`; no master/production fallback or new grants/users. Authentication error alone is not a firewall diagnosis. |
| TLS chain/name/protocol error | Check certificate validation and encryption settings in both clients, FQDN/SNI, driver support, clock and approved OS CA/intermediate chain. SSMS may use a different driver/trust store or TrustServerCertificate setting; its success does not prove validated TLS. Keep FQDN, encryption and hostname validation. Review approved trust-chain repair only after evidence; never `-C`, disable encryption, use an IP/hostname override, or install an unverified certificate. |
| Timeout/network/route | Compare same workstation, DNS/VPN/proxy and endpoint/port with SSMS; distinguish pre-login from authentication timeout. Azure redirect connection policy can require additional outbound ports beyond 1433; a 1433 TCP pass is not an SQL/TLS/auth pass. Do not run network/SQL probes here or change client/server firewalls, Azure networking or connection policy. Obtain existing network-owner evidence for the approved route. |
| Input file/stage failure | Missing `-i` file, wrong root-relative includes, encoding or unresolved variables may fail locally or after a successful login; distinguish these from connection failure. Use the offline renderer and original fixture SHA-256; do not treat a partial stage as a retryable connection problem. |

Retain only sanitized error code/SQLSTATE, stage, executable/driver/PowerShell versions and timing;
no passwords, tokens, full connection strings, raw login traces or production details. If further root-cause work is needed, obtain the
missing sanitized failure facts before selecting a remedy; no retry of the resolved target check is required now. No TLS exception or authentication-mode
change is part of this PR. A later candidate-only connection diagnostic needs separate approval;
use the existing `00-empty` packet after client/route/identity review, not an ad-hoc production query.

## 5. Remaining gates before fixture/migrations

1. Verify the exact resource metadata above and independent acceptance-only owner identity; confirm
   no consumer/traffic relationship to production, plus shared-plan/server window, headroom/abort
   thresholds, recovery/retention and cleanup ownership. No shared resource is cleanup-owned.
2. Retain the supplied current-target connection PASS; resolve installer-client selection/flags and
   missing validated-TLS/auth/route parity evidence without weakening validation. Separately authorize
   candidate-only SQL connection, then `00-empty`/Stage 0 with complete existing dbo/CONTROL visibility.
   Prior confirmation of a fresh DB is not a live empty-schema/user/credential check. Failed/unknown
   checks STOP; no grant expansion or emptying an unexpected database.
3. Obtain unchanged private `authoritative-candidate.sql`, SHA-256
   `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`, and run the unchanged parser
   through offline preparation. Independently review source/packet hashes and synthetic seed/ordinary
   dependencies. Approval bits/NULL secrets stay blocked until their separate stages are authorized.
4. Separately authorize `01-fixture` and reviewed ordered foundation/Phase 1R/Case V2 stages, one at
   a time. Keep controls OFF and no serving app during normalization. No blanket loop/retry/adoption.
5. **Before stages 16–18 and successor CIMD/DCR_V3 installation:** independently approve test-app
   availability and OFF evidence, obtain actual worker/JDK/zone, 28 Java/SQL mapping samples and direct
   metadata egress. Origin is now supplied; running-JVM zone and SQL-zone compatibility remain OPEN.
   Initial fixture/Case preparation can stop at this binding boundary; do not guess it to finish C2.
6. Keep later restricted native catalog/provider, Boot/admission, browser/UI, stock Hermes CIMD/DCR,
   audit/revocation/retention/NAT and bounded concurrency checks OPEN until run. They are not reasons
   to activate anything during installation. The prior `VERIFIER_COMPONENT_READ` result is a separate
   catalog gate, not the explanation for an SQLCMD login failure.

Keep all MCP/OAuth/CIMD/DCR controls OFF. Do not deploy/start, connect to SQL, execute any stage,
create users or modify firewalls/production under this review. Cleanup needs a separate exact test-app/
DB decision; never resize/delete/restart the shared plan/server/group or modify production resources.

## 6. Offline review validation

| Check executed for this reconciliation | Result |
| --- | --- |
| `python build/azure-mcp-acceptance/test_packet.py` | 10 PASS, including exact rendered tuple, preserved old private inputs, default approval refusal and mocked sole-database/TLS flags |
| `python -m unittest discover -s build/deployment-binding -p test_packet.py` | 5 PASS |
| `python -m unittest discover -s build/sql-confinement -p test_external_v2.py` | 6 PASS |
| Repository selector's affected Maven suite | 160 PASS; zero failures/errors/skips |
| `mvn test` critical reactor | 283 PASS; zero failures/errors/skips |
| Markdown links/fences, PowerShell parser/Bash syntax, `git diff --check` | PASS; documentation commands were parsed, never executed |

PowerShell 7.4.6, Temurin JDK 21.0.13+9, Maven 3.9.11. Maven used
`-B -s /workspace/work/acceptance-tools/settings.xml
-Dmaven.repo.local=/workspace/work/acceptance-tools/m2` with the session CA truststore.
Selection: `python build/test-selection/select_tests.py --format json`, with `--path` for each
changed file; its `selected_maven_args` were executed. New packet tests intercept SQLCMD in a
PowerShell function, so their authorized-argument test cannot launch a real client or connect.
No production Java, original migrations, shared binding engine, roles/permissions or activation
defaults changed. Existing audit behavior is preserved; no audit event/schema was introduced.

These results verify preparation only. Operator-reported SSMS/Go connectivity is separately
attributed in section 4. No native empty-target/catalog/SQL-TLS test, current app JVM observation,
fixture install, SQL-user provisioning or enabled acceptance was performed by this review.
