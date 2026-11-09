# Read-only production binding discovery checklist

**Startup diagnostic preparation — 2026-11-09:** accepted operator discovery now records Linux /
Microsoft OpenJDK 21.0.13, one production replica, OS `Etc/UTC`, unset `TZ`/`WEBSITE_TIME_ZONE`, SQL
clock UTC, and an existing empty `AiCaseDataDeployment` at `shale.database.windows.net/Shale`.
All three dedicated identities passed production verification. The running JVM zone remains
UNVERIFIED. The [startup diagnostic implementation and release requirements](production-jvm-startup-diagnostic.md)
provide a separately governed future JAR route while all controls stay OFF. This supersedes the
historical statement that the repository has no narrow diagnostic implementation; it does not
claim the diagnostic is deployed, waive any binding gate or authorize deployment/SQL/settings changes.


Target supplied by the operator: App Service **shale-api**, resource group **Shale**, SQL server
**shale.database.windows.net**, database **Shale**. These identifiers are supplied facts; the values
returned below are live evidence only after an authorized operator runs the checks. No commands
below have been run against production during development. No binding insert/change is authorized.

Keep **all** AI/MCP/OAuth controls OFF, including case-data composition. Do not restart, deploy,
change settings, install a diagnostic agent, enable JMX/SCM access or alter Azure/SQL permissions.
Use an existing authorized Azure session and SSMS connection. Do not fetch production secrets.

## 1. Record Azure target, hostnames and replicas

PowerShell with an existing signed-in Azure CLI session:

```powershell
$ErrorActionPreference = 'Stop'
function Read-AzureJson {
    param([string[]]$CliArgs)
    $json = & az @CliArgs --only-show-errors --output json
    if ($LASTEXITCODE -ne 0) { throw 'Azure read failed; STOP.' }
    $json | ConvertFrom-Json
}
$account = Read-AzureJson -CliArgs @('account','show','--query','{subscription:id,tenant:tenantId,name:name}')
$account | ConvertTo-Json
$site = Read-AzureJson -CliArgs @('webapp','show','--resource-group','Shale','--name','shale-api',
    '--query','{id:id,state:state,kind:kind,httpsOnly:httpsOnly,defaultHostName:defaultHostName,hostNames:hostNames,enabledHostNames:enabledHostNames,hostNameSslStates:hostNameSslStates,serverFarmId:serverFarmId}')
$site | ConvertTo-Json -Depth 8
$instances = @(Read-AzureJson -CliArgs @('webapp','list-instances','--resource-group','Shale',
    '--name','shale-api','--query','[].{instance:name,state:state}'))
$instances | ConvertTo-Json
$slots = @(Read-AzureJson -CliArgs @('webapp','deployment','slot','list','--resource-group','Shale',
    '--name','shale-api','--query','[].{name:name,state:state,defaultHostName:defaultHostName}'))
$slots | ConvertTo-Json
Read-AzureJson -CliArgs @('webapp','traffic-routing','show','--resource-group','Shale','--name','shale-api') |
    ConvertTo-Json -Depth 5
```

Record subscription/tenant, site ID, production vs slot traffic ownership, UTC observation time,
default/custom hostnames, HTTPS/TLS states and all instance IDs. Do not assume a hostname is the
canonical OAuth origin because it appears in this list. Exclude SCM hostnames. If Azure CLI denies
the read, stop; do not grant access or obtain publishing credentials.

**Do not run** `az webapp config appsettings list`, its REST `config/appsettings/list` equivalent,
`listPublishingCredentials`, full process/environment/property dumps, or Key Vault secret commands.
An Azure CLI `--query` on the appsettings-list command filters only after the service returned **all
settings including secrets**; it is not a safe selective read. There is no App Service REST GET of
one setting that avoids that list response.

## 2. Verify public ingress and the effective OAuth origin

Inventory possible Azure proxy resources in the supplied resource group:

```powershell
Read-AzureJson -CliArgs @('resource','list','--resource-group','Shale',
    '--query',"[?type=='Microsoft.Cdn/profiles' || type=='Microsoft.Network/frontDoors' || type=='Microsoft.Network/applicationGateways' || type=='Microsoft.Network/trafficManagerProfiles' || type=='Microsoft.ApiManagement/service'].{id:id,name:name,type:type}") |
    ConvertTo-Json -Depth 5
```

This inventory is incomplete for proxies in other resource groups/subscriptions or outside Azure.
Request the ingress owner's **nonsecret** route evidence, not credentials. If a Front Door Standard/
Premium profile is identified, the following exact commands read its routing metadata; fill only
names returned by the inventory and subsequent lists:

```powershell
$profile = '<observed-profile-name>'
Read-AzureJson -CliArgs @('afd','custom-domain','list','--resource-group','Shale','--profile-name',$profile,
    '--query','[].{name:name,hostName:hostName,validation:domainValidationState,provisioning:provisioningState}') | ConvertTo-Json -Depth 5
Read-AzureJson -CliArgs @('afd','endpoint','list','--resource-group','Shale','--profile-name',$profile,
    '--query','[].{name:name,hostName:hostName,enabled:enabledState}') | ConvertTo-Json -Depth 5
$endpoint = '<observed-endpoint-name>'
Read-AzureJson -CliArgs @('afd','route','list','--resource-group','Shale','--profile-name',$profile,'--endpoint-name',$endpoint,
    '--query','[].{name:name,enabled:enabledState,domains:customDomains,patterns:patternsToMatch,protocols:supportedProtocols,redirect:httpsRedirect,forwarding:forwardingProtocol,originGroup:originGroup,ruleSets:ruleSets,originPath:originPath}') | ConvertTo-Json -Depth 8
$originGroup = '<observed-origin-group-name-from-route-id>'
Read-AzureJson -CliArgs @('afd','origin','list','--resource-group','Shale','--profile-name',$profile,'--origin-group-name',$originGroup,
    '--query','[].{name:name,hostName:hostName,hostHeader:originHostHeader,httpsPort:httpsPort,enabled:enabledState}') | ConvertTo-Json -Depth 5
```

For other proxy types, associated rule sets, redirects, host rewrites or path-prefix routing, retain
the ingress owner's existing read-only nonsecret configuration evidence. Do not infer omitted rules.
Require proof of the external HTTPS hostname and routes for `/oauth/mcp` and `/mcp` to this exact
production app. An internal originHostHeader or App Service default hostname need not be the public
hostname. An unreviewed path prefix, multiple plausible origins, incomplete proxy visibility, or
unknown slot traffic remains a blocker.

Retrieve **only** the already deployed effective `shale.ai.oauth.public-origin` and identity through
an existing approved allowlisted configuration diagnostic, if one exists. This repository supplies
no such production diagnostic while composition is OFF. Do not enable OAuth metadata/actuator or
dump configuration to obtain it. An operator can also supply a nonsecret release/configuration
record with precedence evidence, but none has been supplied for this task.

**Evidence rule:** verified hostname/TLS/proxy topology establishes a *candidate ingress origin*.
It does not select or approve the application's canonical OAuth identity. With no approved effective
property/record, record `public-origin = UNVERIFIED` and stop before installation. Do not create a
new setting. Once approved, exact strings are `origin + '/oauth/mcp'` and `origin + '/mcp'`, validated
by existing `McpOAuthProperties` rules: canonical production HTTPS origin with no trailing slash,
path/query/fragment/userinfo/default-port/host-case aliases. Never derive from request headers.

## 3. Obtain the effective zone from every running production JVM

For a Linux app where SSH is **already available and authorized**, open one session for **each**
instance ID returned in step 1:

```powershell
$instanceId = '<exact-observed-instance-id>'
az webapp ssh --resource-group Shale --name shale-api --instance $instanceId
if ($LASTEXITCODE -ne 0) { throw 'Replica SSH unavailable; STOP.' }
```

Inside each SSH session, run only these Linux metadata commands:

```bash
printf 'Replica=%s\n' "$WEBSITE_INSTANCE_ID"
date -u '+ObservedAt=%Y-%m-%dT%H:%M:%SZ'
ps -eo pid=,comm= | awk '$2 == "java" { print "JavaPid=" $1 }'
command -v jcmd
# Repeat for each actual server JVM PID; substitute the observed numeric PID.
for server_pid in $(ps -eo pid=,comm= | awk '$2 == "java" { print $1 }'); do
    jcmd "$server_pid" VM.version || break
done
```

Record instance ID/PID/JDK version. Do not print process arguments. Match IDs to the complete
production replica list. Repeat list-instances after collection; if replicas changed, evidence must
cover the new set. SSH failure, absent diagnostic tooling or non-Linux hosting is a blocker for
this route; do not install tools or switch platform as part of discovery.

If Python 3 is already present in that SSH session, this allowlisted environment read can supply
**corroborating** nonsecret configuration evidence without an App Settings list call:

```bash
python3 - <<'PY'
import os, re
for key in ('SHALE_AI_OAUTH_PUBLICORIGIN', 'SHALE_AI_OAUTH_PUBLIC_ORIGIN'):
    value = os.environ.get(key)
    if value is None:
        print(key + '=ABSENT')
    elif re.fullmatch(r'https://[a-z0-9][a-z0-9.-]*(?::[1-9][0-9]{0,4})?', value):
        print(key + '=' + value)
    else:
        print(key + '=INVALID_OR_UNVERIFIED_REDACTED')
identity = os.environ.get('SHALE_AI_OAUTH_IDENTITY')
print('SHALE_AI_OAUTH_IDENTITY=' + (identity if identity in ('PRODUCTION', 'DEVELOPMENT', 'TEST') else 'ABSENT_OR_UNVERIFIED'))
PY
```

Spring's standard environment spelling removes the dash: `SHALE_AI_OAUTH_PUBLICORIGIN`.
The alternate spelling is collected only as a possible alias, not asserted to bind. This command
reads only named nonsecret keys and suppresses malformed URL payloads. SSH environment is not
automatically the JVM environment; command-line/system-property/profile precedence and process
inheritance still need evidence. Do not read `/proc/<PID>/environ` or raw command lines. An observed
value here is not yet approved/effective configuration. No JDK startup or full config dump is needed.

**Standard `jcmd` has no command to evaluate `ZoneId.systemDefault().getId()`.** `VM.version`, OS
`date`, `TZ`, `WEBSITE_TIME_ZONE` and `-Duser.timezone` are not the requested getter's result. Do not
use `jcmd VM.system_properties`: it returns all JVM properties and can expose secrets even if piped
through a filter. A new `java`/JShell process observes its own default, not the running application's.

Only if a **pre-existing, approved per-process diagnostic** can return the single expression
`java.time.ZoneId.systemDefault().getId()` from that JVM, use its established command and collect
exact return value with instance ID/PID/time. Do not invent an endpoint, attach a new agent, enable
JMX, or deploy code. No such diagnostic is established by this repository. Consequently the exact
running zone **remains a blocker** until the operator identifies an existing safe diagnostic or
separately authorizes a future diagnostic change. Identical environment defaults are not a waiver.

## 4. Read SQL state and evaluate the mapping

In SSMS, use an **existing approved**, certificate-validated operator connection to
`tcp:shale.database.windows.net,1433`, database `Shale`; do not save/retrieve a password in scripts.
Run this single read-only batch (no SQLCMD mode required):

```sql
SET NOCOUNT ON;
IF DB_NAME()<>N'Shale' OR ISNULL(CONVERT(int,SERVERPROPERTY('EngineEdition')),0)<>5
 THROW 59310,'Wrong production database/platform; STOP.',1;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 THROW 59310,'Existing complete operator visibility required; do not add grants.',1;
IF @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalEmail') IS NOT NULL
 THROW 59310,'Use a fresh operator session; STOP.',1;
SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) AS LogicalServer,
 DB_NAME() AS DatabaseName,SYSDATETIMEOFFSET() AS SqlClock,SYSUTCDATETIME() AS UtcClock;
SELECT name,current_utc_offset,is_currently_dst FROM sys.time_zone_info ORDER BY name;
IF OBJECT_ID(N'dbo.AiCaseDataDeployment',N'U') IS NULL
 SELECT 'MISSING_BINDING_TABLE_STOP' AS BindingStatus;
ELSE
 EXEC(N'SELECT COUNT_BIG(*) AS BindingCount FROM dbo.AiCaseDataDeployment;
 SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment;');
```

An existing tuple is verified stored state, not proof that it matches the deployment. Never change
it. An empty table supplies no SQL mapping. `sys.time_zone_info` supplies supported names/current
offsets, not an approved mapping; SQL clock UTC is not evidence that `SqlTimeZone` must be UTC.

After the running Java zone is verified, generate the 28 expected samples offline with the same
JDK and verified zone using `build/deployment-binding/BindingTimeZoneProbe.java`. For a candidate
existing SQL name chosen by the operator, execute only this read-only SSMS comparison:

```sql
DECLARE @CandidateSqlZone sysname=N'<existing-name-from-sys.time_zone_info>';
IF NOT EXISTS(SELECT 1 FROM sys.time_zone_info WHERE name=@CandidateSqlZone)
 THROW 59310,'Unknown SQL mapping; STOP.',1;
SELECT DATEFROMPARTS(y.YearNumber,m.MonthNumber,15) AS UtcSample,
 CONVERT(datetime2(7),(CONVERT(datetime2(7),DATEFROMPARTS(y.YearNumber,m.MonthNumber,15))
 AT TIME ZONE 'UTC') AT TIME ZONE @CandidateSqlZone) AS SqlWallTime
FROM (VALUES(2025),(2026),(2027),(2028)) y(YearNumber)
CROSS JOIN (VALUES(1),(3),(4),(7),(10),(11),(12)) m(MonthNumber)
ORDER BY y.YearNumber,m.MonthNumber;
```

Compare **all 28** values with Java, not just today's offset. Record mapping/rule provenance and
operator approval. Similar offsets or multiple matching names are inferred candidates until approved.
If no verified JVM zone, mapping evidence or approval exists, leave `SqlTimeZone = UNVERIFIED`.

## 5. Return bounded evidence and stop

Collect only: target/subscription/site/slot; UTC observation time; ingress hostname/TLS/route evidence;
effective approved public-origin and provenance **or UNVERIFIED**; derived issuer/resource only if
confirmed; complete replica ID/PID/JDK/getter results **or UNVERIFIED**; SQL server/database; existing
binding count/tuple; supported candidate SQL name/28 comparisons/approval **or UNVERIFIED**; and
existing nonsecret all-controls-OFF/change-freeze evidence. Do not repeat accepted identity checks.

No installation can follow while any required value remains unverified. Return this evidence for
review and future separate authorization. The [binding readiness package](production-mcp-deployment-binding-readiness.md)
contains the eventual guarded insert and recovery sequence; discovery does not authorize it.
