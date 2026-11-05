# Dormant production compatibility: one read-only operator packet

**PREPARED, NOT EXECUTED.** Candidate source is
`600723b92fe34731e1d6d25e598fc9e3cbfc2770` on `codex/latest` (runtime code unchanged
from `1f12091aef3275721c1e35d6ba00a2ecb80c5173`). Use the exact packet commit in the
handoff, a clean checkout and a trusted Microsoft ODBC sqlcmd installation. This packet performs
no build, deployment, migration, repair, grant change, timeout change in application code, capability
activation or Phase1R/Phase3B.8 work. The SQL session's lock wait is bounded at five seconds.
The October 20 retirement remains accepted at its reported scope; no retirement procedure is called.

The [compatibility audit](../../dormant-server-release-compatibility.md) owns release scope.
This packet closes evidence gaps, not AI acceptance. It does not authorize its own production execution.
It uses Brian's existing SQL Server Authentication access, validated TLS and existing Azure CLI access.
No new SQL/Azure identity, privilege, secret, firewall or login is provisioned. If access is missing,
stop UNKNOWN; never repair permissions as part of inventory.

## A. Exact SQL operator sequence (PowerShell 5.1 or 7)

Prepared commands only; none was executed during authoring. From the repository root in an existing
trusted PowerShell console, after checking out the exact packet commit reported in the handoff:

```powershell
$ErrorActionPreference = 'Stop'
$packet = (Resolve-Path 'docs\operations\dormant-server-readonly').Path
$collector = Join-Path $packet 'collect.ps1'
$sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\SQLCMD.EXE'
if ($env:SQLCMDINI -or $env:SQLCMDPASSWORD) { throw 'STOP: use a clean console without SQLCMDINI/SQLCMDPASSWORD.' }
New-Item -ItemType Directory -Path 'work\dormant-readonly' -Force | Out-Null
& $collector -Mode Sql -SqlcmdPath $sqlcmd |
    Set-Content -LiteralPath 'work\dormant-readonly\sql.json' -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw 'STOP: SQL_TRANSPORT UNKNOWN; no retry or permission repair.' }
Get-Content -LiteralPath 'work\dormant-readonly\sql.json'
```

The collector requires one explicit absolute executable file, then checks its nonconnecting `-?`
identity for the Microsoft ODBC banner and rejects Go. It does not discover executables through PATH:
multiple installed sqlcmd binaries therefore cannot turn `.Source` into an array and fail parameter
binding. If ODBC is installed elsewhere, supply that one trusted absolute file path; no automatic
selection, retry or fallback occurs. Missing/invalid/Go selection stops before the password prompt.
Its **one connected invocation** is
exactly the following argument sequence; the collector supplies `SQLCMDPASSWORD` **only to that child**
after a masked SecureString prompt. Do not run this line separately or set a global password variable:

```text
sqlcmd.exe -S "tcp:shale.database.windows.net,1433" -d "Shale" -U "brian" -N -b -V 16 -l 5 -t 30 -h -1 -W -w 512 -x -i "<absolute packet path>\catalog.sql"
```

**Local launcher correction:** the earlier `SQLCMDPASSWORD` plus `-X1` combination disabled the
environment mechanism needed for authentication. Its reported `SQL_TRANSPORT UNKNOWN` is a launcher
failure, not production compatibility evidence. Per the
[official Microsoft sqlcmd contract](https://learn.microsoft.com/en-us/sql/tools/sqlcmd/sqlcmd-utility?view=sql-server-ver17#-x1),
`-X` suppresses environment variables; Microsoft documents `SQLCMDPASSWORD` as the alternative to
command-line `-P`. The corrected invocation omits both `-X` and `-X1` and retains lowercase `-x`.
The child environment explicitly removes `SQLCMDINI` before password injection/process start.
A guard rejects all colon-prefixed sqlcmd commands, shell escapes and bare editor/control commands
in the raw input file before the password prompt. Static tests also reject those constructs in the
reviewed catalog.sql. No input needs the shell/startup protection formerly supplied by `-X1`.
Password lifetime, sanitization, connection bounds and no-retry behavior are preserved.

There is no `-P`, `-C`, integrated/Entra authentication, SQLCMD substitution, include, startup script,
application RPC, application-table SELECT or application row count. The collector captures stdout/stderr
in memory, validates the bounded fact grammar, and prints only sanitized JSON. Driver/SQL errors become
`SQL_TRANSPORT UNKNOWN`; raw errors are discarded. A 90-second child-process bound stops a stalled
invocation without retry. Local sanitized JSON files are the only outputs; do not use a transcript or debug dump.

The SQL guard requires the exact database, SQL authentication, original login brian, EngineEdition 5,
and dbo/CONTROL DATABASE/VIEW DEFINITION visibility from the **existing** operator connection.
Metadata invisibility cannot become an empty PASS. Missing access stops before impersonation.
`EXECUTE AS USER`/`REVERT` are session-only effective-permission checks copied from the existing
postcheck approach; they are not application-procedure execution. No application procedure or RLS
function is invoked. They do not require application credentials. If the existing operator cannot
impersonate the two named users, the connection fails closed as UNKNOWN; do not grant impersonation.
Every successful impersonation restores its caller on both normal and exception paths; disconnect also
ends its session. The script uses no temporary tables, table-variable INSERTs, dynamic SQL or DDL/DML.

## What is checked, and what existing records must establish

Stage A is **exactly two** procedures in the current installer, not an illustrative subset:
LookupAuthenticationCredentialV1 and ResolveDesktopRememberCredentialV2. The packet checks both
source bodies, all four parameters, effective dbo owner execution and dbo schema ownership, all six
object EXECUTE ACLs (dbo grantor, exact G/D states, no additional object ACLs), and the original
static/unresolved wrapper-dependency prohibition. Auth must effectively execute both; runtime must
execute neither. Runtime-role DENYs are checked explicitly, and runtime membership/non-bypass checks
are separate. No old resolver EXECUTE grant or direct auth Users SELECT is required or restored.

[contract.json](contract.json) records exact SHA-256 of each source CREATE batch's UTF-16LE bytes in
both LF and CRLF transport representations. Only those two exact raw hashes are accepted; there is
no whitespace trimming, CREATE/ALTER rewriting or observed-hash adoption. SET options/owner/parameter
checks remain separate. A different visible body fails; an existing invisible body returns UNKNOWN.
No password/credential/data hash is selected.

Session/remembered checks derive from the real Phase 7A/8A/October 5 migrations: exact types, nullability,
identity and datetime2 scale for all **15 UserSessions + 9 SessionSecurityAuditLog + 11 remembered**
columns; effective dbo table ownership; exact trusted tenant/session FK mappings and delete actions;
exact enabled/schema-bound sec.TenantFilter FILTER/AFTER INSERT/AFTER UPDATE bindings for both session
tables; strict inline predicate shape/ownership; and no remembered-table RLS, per the existing contract.
Runtime requires session SELECT/INSERT/UPDATE, session-security INSERT, remembered object
SELECT/INSERT/UPDATE/DELETE and the used Users projections/ApplicationInstances SELECT. Stage B's
password-hash denial is compatible with the column-specific projection check.

Three full catalog/semantic matters deliberately require the **existing reviewed record**:

| Required fact | Smallest independent evidence |
| --- | --- |
| STRICT_PREDICATE_REVIEW | Existing authoritative definition/approval for sec.fn_FilterByTenant, including its intended dbo/ShaleAdmins exception and ordinary non-bypass mapping; compare the emitted definition hash. The production body is not defined by a repository installer, so no synthetic fixture body is substituted. |
| SESSION_CATALOG_REVIEW | Existing Phase 7A/8A/remembered schema-verification record covering UTC defaults, PK/unique/index definitions, all lifecycle CHECK definitions (including USER_REVOKED), and their trusted/enabled state. The emitted digest covers these exact three tables' columns/defaults/checks/indexes; FK/RLS/permissions are checked separately. |
| ORDINARY_API_REVIEW | Existing source-to-schema/grant acceptance/change inventory for all retained ordinary API routes, including Case/audit, Tasks/policy, Contacts/Organizations/Users, notifications, release catalog/import/global audit, instance/heartbeat/admin-read audit and the transitive DAO dependencies. Compare the emitted source-scoped catalog/ACL digest or review the original metadata privately against that record. Do not substitute table existence, health, a version number or successful login for complete route parity. |

The ordinary inventory's 72 source-referenced object names and 29 owning/helper DAO files are explicit in
contract.json. The inventory includes columns/defaults/computed expressions, keys/indexes/checks,
FKS, existing predicates/policy state, scoped module/trigger hashes/owners and database ACL/role metadata.
It does not declare every method of a shared desktop DAO to be a newly required API operation.
The reviewed record must map the current API's actual operations to that inventory and cover any
transitive/dynamic dependency not represented by a literal dbo name. The digest includes physical
catalog IDs in ACL metadata and is **database-specific**, not a synthetic-source fingerprint.
No AI verifier/installer/receipt or three-FILTER preflight is executed.

The existing scripts cannot be blindly included: module-postcheck declares/inserts a table variable;
Stage B checks create/drop temporary state; Phase 7A verification includes a UserSessions data count;
other RLS verifiers create users/write synthetic rows; remembered verifiers retain old-resolver/auth
SELECT expectations. The new packet adapts only their metadata/effective checks to the narrower
no-DDL/no-DML/no-data/output contract. It does not weaken or replace their original migration guards.
Stage B installation is not a release prerequisite and is neither applied nor rolled back here.

## B. Sanitized output contract

`sql.json` and `azure.json` have only a fixed source SHA and an array of facts:

```json
{"sourceSha":"600723b92fe34731e1d6d25e598fc9e3cbfc2770","facts":[{"name":"STAGE_A_ACL","status":"PASS","findingCount":0,"hash":"-"}]}
```

Every status is PASS, FAIL or UNKNOWN. Names are fixed packet categories, not values from production.
`findingCount` is a bounded nonnegative metadata-finding count, never an application-row count.
The three ARTIFACT_VERSION fields are bounded numeric version components in that same integer field.
`hash` is `-`, a 40-hex source SHA or a 64-hex **module/catalog/artifact** SHA-256. There are no identities,
SIDs, URLs, connection strings, settings values, credential hashes, PHI, tokens or exception text in output.

For a fully compatible visible database, the structural/effective SQL facts in contract.json are
PASS with zero findings. The two module facts include one of the corresponding exact expected hashes.
SQL_COMPLETE must be PASS. STRICT_PREDICATE_REVIEW, SESSION_CATALOG_REVIEW and ORDINARY_API_REVIEW
initially remain UNKNOWN with their observed digest until independent existing-record review resolves
them. Hash equality alone is not approval to adopt a newly observed catalog as its own baseline.
Missing/error/duplicate/malformed/truncated output, missing visibility or absent fact is UNKNOWN/STOP.
A visible missing/incompatible prerequisite is FAIL. Neither condition authorizes a repair.

## B.1 Bounded localization of the two reported failures

The two reported six-count failures do **not** establish a cause. This refinement adds 67
fixed facts alongside the unchanged `SESSION_RLS` and `RUNTIME_NONBYPASS` statements.
It changes no successful fact or review digest, and does not resolve or investigate the three
review-only UNKNOWNs. For this handoff use **only sequence A / Mode Sql**, once, with the
same secure ODBC launcher. Do not run sequences C or D, Azure inventory, application procedures,
installers, repair, Phase1R or Phase3B.8. No production/Azure execution occurred during development.

The exact names are enumerated in `contract.json` under `localizedSqlFacts`. The SQL collector
accepts only fixed SQL fact names, requires all localized facts when SQL_COMPLETE is present,
and requires `hash: "-"` for every localized fact. Malformed, duplicate, unexpected or incomplete
completed output is discarded as SQL_TRANSPORT UNKNOWN; no raw output or errors are printed.
The TLS/ODBC invocation, child-only SQLCMDPASSWORD handling, time bounds and no-retry behavior
are unchanged. The legacy Azure/decision modes are unchanged and outside this refinement.

For each prefix formed by `SESSION_RLS_` + **USERSESSIONS** or **SESSIONAUDIT** + `_` +
**FILTER**, **BLOCK_AFTER_INSERT** or **BLOCK_AFTER_UPDATE**, eight suffixes are emitted:

| Suffix | PASS means / FAIL identifies |
| --- | --- |
| PRESENT | A predicate cohort exists for that target/slot; missing target or empty cohort fails. |
| POLICY_SCHEMA | All cohort predicates use exactly sec.TenantFilter. |
| POLICY_OWNER | sec schema and effective policy owner are dbo. |
| ENABLED | Every cohort policy is enabled. |
| SCHEMA_BOUND | Every cohort policy is schema-bound. |
| TYPE_OPERATION | Every cohort matches the aggregate's exact type and operation description, including its literal FILTER operation comparison. NULL operation fails this literal comparison. |
| FUNCTION_DEFINITION | Every binding expression matches the aggregate's existing bracket/space-normalized sec.fn_FilterByTenant(ShaleClientId) comparison. No function body is read or newly accepted by this fact. |
| EXTRA_PREDICATES | There is at most one predicate in this cohort; findingCount counts excess predicates. |

FILTER cohorts select predicates by FILTER type; block cohorts select by the named operation,
independently of type. Thus a wrong type within a block slot fails TYPE_OPERATION. A predicate
with a wrong operation can leave its expected cohort absent and appear in another cohort or
UNEXPECTED_PREDICATES. No arbitrary pairing of a missing slot with a different predicate is inferred.
When a cohort is empty, its six property checks are UNKNOWN, PRESENT is FAIL, and its extras
count is zero. An unavailable binding expression is UNKNOWN. A known incompatible property
takes FAIL precedence over unknown properties within that same fact.

Each target also has `SESSION_RLS_USERSESSIONS_FILTER_OPERATION_NULL` or
`SESSION_RLS_SESSIONAUDIT_FILTER_OPERATION_NULL`, and the corresponding
`SESSION_RLS_USERSESSIONS_UNEXPECTED_PREDICATES` or
`SESSION_RLS_SESSIONAUDIT_UNEXPECTED_PREDICATES`.
FILTER_OPERATION_NULL is PASS when FILTER predicates exist and all operation descriptions are NULL;
absent FILTER is UNKNOWN. It makes the native NULL FILTER operation representation visible as a
sanitized fact, separately from the unchanged aggregate's literal comparison. It does not declare
the aggregate PASS. UNEXPECTED_PREDICATES counts bindings outside canonical FILTER/NULL and
BLOCK/AFTER INSERT or AFTER UPDATE shapes. Duplicates are counted by the per-cohort extras checks.
RLS dimensions overlap: do not sum these findingCounts to reconstruct the aggregate.

The 15 runtime names have prefix `RUNTIME_NONBYPASS_` and these exact suffixes:

```text
DB_OWNER
SHALEADMINS
CONTROL_DATABASE
ALTER_ANY_USER
ALTER_ANY_ROLE
ALTER_ANY_SCHEMA
ALTER_ANY_SECURITY_POLICY
IMPERSONATE_DBO
ALTER_DBO_SCHEMA
RUNTIME_ROLE_REQUIRED
OWNER_PRINCIPALS
FIXED_DATABASE_ROLES
IMPERSONATE_SCOPED_PRINCIPALS
PROTECTED_OBJECT_AUTHORITY
ELEVATED_OBJECT_EXECUTE
```

They run inside the existing shale_runtime impersonation, using the same membership functions,
owner/fixed-role token joins, scoped-principal exclusions and protected/elevated snapshots as the
aggregate. PROTECTED_OBJECT_AUTHORITY preserves the aggregate's full @AuthObjects scope (all
non-system U/V/P/FN/IF/TF objects), rather than narrowing it to named authentication tables.
ELEVATED_OBJECT_EXECUTE preserves its module snapshot and V1/V2 exclusions. Scoped impersonation
preserves the original inclusion of dbo, so that category can overlap IMPERSONATE_DBO.
PASS requires forbidden authority/membership to be absent; RUNTIME_ROLE_REQUIRED instead requires
membership in shale_runtime_role. Missing ShaleAdmins is treated as zero membership, as before.
NULL permission/membership results are UNKNOWN, never PASS. Empty scoped inventories have zero
findings, matching the original aggregate. Multi-object categories emit only summed findings and
unknown counts; known failures take precedence over UNKNOWN. No discovered names, identifiers,
definitions or hashes leave these checks. These are authority categories, not an object inventory.

Offline validation uses synthetic in-memory catalog rows, fixed-name/transport rejection tests,
and a byte-preservation guard over all original SQL statements. It never connects to SQL or Azure.
The final bounded operator run remains necessary to establish the production causes. No result
from this refinement authorizes a repair or changes the aggregate acceptance gates.

## C. Separate read-only Azure/App Service sequence

Use the existing Azure CLI account/subscription; do not run login, account set, deployment, restart,
configuration set, publishing-profile/credential retrieval or ARM writes. Discover the unique existing
resource by the documented API hostname; no resource-group/subscription/site ID is guessed:

```powershell
$appIds = @(& az webapp list --only-show-errors --output tsv --query "[?defaultHostName=='shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net'].id" 2>$null)
if ($LASTEXITCODE -ne 0 -or $appIds.Count -ne 1 -or [string]::IsNullOrWhiteSpace($appIds[0])) {
    throw 'STOP: AZURE_TARGET UNKNOWN; identify the existing resource from authorized records.'
}
& $collector -Mode Azure -AppResourceId $appIds[0] |
    Set-Content -LiteralPath 'work\dormant-readonly\azure.json' -Encoding UTF8
Get-Content -LiteralPath 'work\dormant-readonly\azure.json'
```

The collector captures `az webapp show`, `az webapp config show` and
`az webapp config appsettings list` into memory. The last command is a read operation despite its
underlying ARM list-action transport. It inspects only presence/format/mapping and false values;
**never run those unfiltered commands to the console**. Raw objects and settings are not saved.
Primary _DB_ keys precede documented aliases, matching Config.java. Auth/runtime must map to the
reviewed principals and Shale destination; secrets are checked only for presence (signing secret also
for minimum length), ISO UTC cutover/positive TTL/profile/CORS are checked without exporting values.
An unresolved Key Vault reference is UNKNOWN, not a resolved secret or proof of effective configuration.
CORS, original cutover, signing-secret consumer continuity and alias/override precedence also need
existing-record review. No new secret is required or provisioned.

All eight controls from contract.json must be established false. The automatic check requires explicit
App Settings; independently reviewed packaged defaults can resolve missing explicit settings below. Both canonical Spring
property spelling and its verified uppercase underscore spelling are recognized; conflicting matching
spellings fail. Missing explicit settings initially remain UNKNOWN, not an assumption about an unidentified JAR's
defaults. JSON/JVM/startup/external-file overrides require the separate effective-configuration review.

If existing Azure access permits SCM GET, the collector uses an in-memory existing-account ARM bearer
(no publishing password, no redirect, validated TLS) for **GET only**:

- `/api/vfs/site/wwwroot/shale-server.jar`: at most 256 MiB buffered; emit disk SHA-256 and bounded
  build-info version if present. No JAR is executed, uploaded or retained on disk.
- `/api/deployments/latest`: emit a bounded commit identifier if available; no raw deployment log.

No compatible SCM access/path/build metadata means UNKNOWN. No credential fallback or new permission
is attempted. ARTIFACT_ON_DISK is not proof those bytes are loaded by the running JVM; latest deployment
metadata is not proof of current artifact identity. ACTIVE_ARTIFACT_REVIEW remains UNKNOWN until an
existing deployment/active-process record maps actual running source/build/version to that same hash.
A timestamp or version alone cannot do this.
EFFECTIVE_CONFIG_REVIEW also remains UNKNOWN: App Settings do not expose the running JVM's complete
Spring precedence/profiles, inherited JVM arguments, external config files or resolved secret consumers.
This source adds no public effective-config/readiness endpoint. No endpoint or SSH/Kudu command runner
is created or invoked to manufacture this proof. Use the existing current operator record; otherwise
stop specifically for missing active-artifact/effective-config evidence.

## D. Final decision, after reviewing the existing evidence

The two inventory runs are one bounded SQL connection plus read-only Azure metadata/SCM reads.
They are observational, not a full dynamic authorization/RLS test or runtime smoke. Keep both outputs
from the same staffed review, with no intervening deployment/settings/SQL change. Any intervening change
invalidates the pairing; it is not a reason for automatic retry.

If existing private records close the five named review facts, make a **local sanitized review record**
using the following shape. The `recordSha256` identifies the independent existing record retained
privately (an existing sanitized non-secret record of at most 1 MiB, whose local file hash is checked);
`reviewedExistingRecord` is an explicit operator attribution, not a fact proven by this tool.
Never set PASS merely to clear an UNKNOWN. Do not upload private source records or secret values.

```json
{
  "sourceSha":"600723b92fe34731e1d6d25e598fc9e3cbfc2770",
  "sqlInventorySha256":"<SHA-256 of sql.json>",
  "azureInventorySha256":"<SHA-256 of azure.json>",
  "STRICT_PREDICATE_REVIEW":{"reviewedExistingRecord":"UNKNOWN","recordPath":"<existing sanitized non-secret record>","recordSha256":"-","expectedHash":"-"},
  "SESSION_CATALOG_REVIEW":{"reviewedExistingRecord":"UNKNOWN","recordPath":"<existing sanitized non-secret record>","recordSha256":"-","expectedHash":"-"},
  "ORDINARY_API_REVIEW":{"reviewedExistingRecord":"UNKNOWN","recordPath":"<existing sanitized non-secret record>","recordSha256":"-","expectedHash":"-"},
  "ACTIVE_ARTIFACT_REVIEW":{"reviewedExistingRecord":"UNKNOWN","recordPath":"<existing sanitized non-secret record>","recordSha256":"-","activeJarSha256":"-","scopeComplete":"UNKNOWN"},
  "EFFECTIVE_CONFIG_REVIEW":{"reviewedExistingRecord":"UNKNOWN","recordPath":"<existing sanitized non-secret record>","recordSha256":"-","activeJarSha256":"-","scopeComplete":"UNKNOWN","verifiedFacts":{"ORDINARY_DB_AUTH":"UNKNOWN","ORDINARY_DB_RUNTIME":"UNKNOWN","ORDINARY_SECRET_PRESENCE":"UNKNOWN","ORDINARY_SESSION_SETTINGS":"UNKNOWN","OFF_CONFIGURED":"UNKNOWN"}}
}
```

`expectedHash` must come from independent existing definition/catalog evidence or a private review
against that evidence, not from copying the just-observed value and declaring success.
ACTIVE_ARTIFACT_REVIEW scope includes current deployed source/build/version/hash/active-byte mapping.
EFFECTIVE_CONFIG_REVIEW scope includes current target, ordinary credentials'
configured principals and destination (including existing proof of the actual auth/runtime pool identity,
not catalog names alone), resolved secret presence, original session boundary, preserved
TTL/CORS, azure-only profile, external/JVM/startup overrides, **all eight effective false values**, readiness
disabled, and preserved SQL global/tenant/integration OFF posture. It requires no AI identity or secret.
The verifiedFacts entries permit that same independently checked record to resolve opaque Key Vault,
packaged-default or alias evidence only when explicitly established. They cannot override an observed FAIL.
If the available records cannot establish this scope, retain UNKNOWN. The collector cannot independently
authenticate an operator's private-record attribution; it only checks consistency and refuses missing
facts, FAIL overrides and mismatched source/inventory/artifact hashes.

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'work\dormant-readonly\sql.json','work\dormant-readonly\azure.json'
# Review existing records privately. Do not fill a PASS template without evidence.
& $collector -Mode Decide -SqlResult 'work\dormant-readonly\sql.json' -AzureResult 'work\dormant-readonly\azure.json'
# Only when independently established, the same local-only decision can consume that review record:
# & $collector -Mode Decide -SqlResult 'work\dormant-readonly\sql.json' -AzureResult 'work\dormant-readonly\azure.json' -ReviewedEvidence '<private sanitized review JSON>'
```

Exact final success output, and only when **every REQUIRED fact** is established:

```text
DORMANT_JAR_COMPATIBILITY PASS
```

Otherwise the output is exactly the blocked marker followed by one or more fixed unresolved categories:

```text
DORMANT_JAR_COMPATIBILITY BLOCKED
UNRESOLVED STAGE_A_ACL
UNRESOLVED EFFECTIVE_CONFIG_REVIEW
```

The example categories are illustrative, not a production result. Missing output is BLOCKED, never PASS.
A PASS closes only the ordinary compatibility gate: next comes the separately authorized exact JDK 21
build/artifact SHA-256/rollback/configuration gate, then authorized deployment and ordinary/OFF smoke.
No build/deploy action is selected or run by the packet. AI acceptance, readiness activation and pilot
remain blocked; Stage B, Phase1R/V2 and all migration/retirement installers remain untouched.

## Preparation validation and limits

Only local offline safety/source checks are appropriate for authoring this packet. The offline guards
check source hashes, complete fact coverage and absence of SQL mutations/application execution/data
reads, unsafe Azure verbs and raw secret output. No production, Azure, sqlcmd, PowerShell collector,
SQL fixture, build, health/login/session smoke or packet mode was executed during preparation.
Executed preparation checks: twelve offline guards PASS, including negative controls for `-X`, `-X1`
and `-X 1` with the child-password mechanism, unsafe input and ambiguous discovery; current selector finds no affected Maven area
and no full-suite escalation. Native PowerShell parsing/ODBC execution and Azure/SCM behavior remain unexecuted; static checks do
not certify those runtime outcomes. Any first operator error is UNKNOWN/STOP, not permission repair,
automatic retry, installer execution or capability activation.
