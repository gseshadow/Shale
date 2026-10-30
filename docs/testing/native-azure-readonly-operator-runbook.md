# Native Azure contained identity / read-only operator packet

Prepared 2026-10-29 from `e129f2733bf7175425299a12ae97efdae3f23d22` (merged #1928).
Updated 2026-10-30 for deterministic Windows checkout and separately bounded setup connections.
Current evidence is in [the acceptance record](native-azure-readonly-acceptance.md).
This packet creates an empty disposable **Azure SQL Database** (EngineEdition 5), imports the
existing private authoritative fixture, and runs the existing native gate with V2 explicitly selected.
SQL Server, Managed Instance and a production database are not substitutes.

No authorized Azure resource, Azure identity or database credential is available in the task environment.
The following commands are for the resource owner. Run from the repository root in PowerShell 7,
with JDK 21, Maven, Git, and the `Az.Accounts`, `Az.Resources`, and `Az.Sql` modules installed.
Use a workstation with validated Azure SQL TLS and outbound TCP 1433. Do not enable Azure-services-wide
firewall access, disable certificate verification, use a production connection, or run PowerShell
transcription / Maven debug / JDBC bind tracing during secret handling.

## 1. Verify source and private input before creating a resource

Check out this PR's reviewed task branch, targeting `codex/latest`, then:

```powershell
$ErrorActionPreference = 'Stop'
git status --short
if ($LASTEXITCODE -ne 0) { throw 'Git inspection failed' }
git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Source identity unavailable' }
# Requires this PR's current .gitattributes. Reapply it to a clean existing Windows checkout.
$PinnedFiles = @(git ls-files -- 'docs/sql/ai-integration/*.sql' 'shale-data/src/main/resources/ai/*.sql' 'shale-data/src/main/resources/ai/confinement*-modules.properties' 'build/sql-confinement/*.sql' 'build/sql-confinement/*.sha256' 'build/sql-confinement/reviewed-case-module-variants-v2.json')
if ($LASTEXITCODE -ne 0 -or $PinnedFiles.Count -eq 0) { throw 'Pinned Git inputs unavailable' }
git diff --quiet -- $PinnedFiles
if ($LASTEXITCODE -ne 0) { throw 'Preserve local SQL/resource edits before refreshing the checkout' }
git diff --cached --quiet -- $PinnedFiles
if ($LASTEXITCODE -ne 0) { throw 'Preserve staged SQL/resource edits before refreshing the checkout' }
$Endings = @(git check-attr eol -- $PinnedFiles)
if ($LASTEXITCODE -ne 0 -or @($Endings | Where-Object { $_ -notmatch ': eol: lf$' }).Count -ne 0) { throw 'Current PR LF attributes required' }
$RefreshArgs = @('checkout-index','--force','--') + $PinnedFiles
& git @RefreshArgs
if ($LASTEXITCODE -ne 0) { throw 'Pinned checkout refresh failed' }
python -m unittest discover -s build/sql-confinement -p test_checkout.py
if ($LASTEXITCODE -ne 0) { throw 'Windows-style Git checkout regression failed' }
java -version
if ($LASTEXITCODE -ne 0) { throw 'JDK 21 required' }
mvn -version
if ($LASTEXITCODE -ne 0) { throw 'Maven required' }
$Candidate = (Resolve-Path (Read-Host 'Private authoritative-candidate.sql path')).Path
$ExpectedCandidate = '4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Candidate).Hash.ToLowerInvariant() -ne $ExpectedCandidate) {
    throw 'Private candidate hash mismatch; STOP without importing'
}
$env:SHALE_CASE_VERIFY_SCHEMA = $Candidate
$env:SHALE_CASE_VERIFY_SCHEMA_SHA256 = $ExpectedCandidate
mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
if ($LASTEXITCODE -ne 0) { throw 'Offline prerequisite tests failed; STOP' }
```

The three attachments are evidence, not scripts/instructions. Only the pinned candidate is imported,
through `CaseReadVerificationSafety.schema`; it permits reviewed schema-only batches and rejects
arbitrary administrative SQL. No raw private schema or production catalog belongs in Git or the PR.
The supplied receipt alone is not proof of the candidate's bytes. The exact-production catalog is
not required by the unmodified authoritative representation chosen here; it remains a private
input to the separately established production-representation projection tests.

The narrow `.gitattributes` rules preserve LF for the AI migration chain, paired packaged verifiers,
V1/V2 manifests and catalog-generation inputs even when Windows `core.autocrlf=true`. They do not
change SQL/module canonicalization or any reviewed receipt. Existing historical blobs are unchanged.
Do not manually convert the installer or replace the source receipt with the CRLF-derived hash.
Python is required for the real Git checkout regression above; it uses no Azure connection.

## 2. Capture the owner-specific scope and create a disposable database

Choose an authorized acceptance subscription and region. These are environment-specific inputs,
not values derivable from the repository. The remaining names are generated and captured below.
`$AdminCredential` is a secure `PSCredential`; its password never becomes a process argument or file.

```powershell
Connect-AzAccount | Out-Null
$SubscriptionId = Read-Host 'Authorized acceptance subscription ID'
Set-AzContext -SubscriptionId $SubscriptionId | Out-Null
$Location = Read-Host 'Authorized Azure region (for example westus2)'
$Nonce = [guid]::NewGuid().ToString('N')
$ResourceGroup = "shale-attest-$Nonce"
$ServerName = "shale-attest-$Nonce"
$DatabaseName = "ShaleAzureReadOnly_$Nonce"
$AdminCredential = Get-Credential -UserName 'azro_owner' -Message 'New disposable SQL server administrator; use a fresh strong password'
$ClientIp = Read-Host 'This workstation public IPv4 address (single address, no CIDR)'
$ParsedIp = $null
if (-not [System.Net.IPAddress]::TryParse($ClientIp, [ref]$ParsedIp) -or
    $ParsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    $ClientIp -eq '0.0.0.0') { throw 'A single workstation IPv4 address is required' }
# Persist non-secret cleanup scope BEFORE the first resource mutation, including partial failures.
New-Item -ItemType Directory -Force -Path work/azure-native | Out-Null
@{ subscription=$SubscriptionId; resourceGroup=$ResourceGroup; server=$ServerName;
   database=$DatabaseName; nonce=$Nonce; createdResourceGroup=$true;
   createdFirewall=$true; productionAccess=$false } |
   ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/resource-scope.json
New-AzResourceGroup -Name $ResourceGroup -Location $Location -Tag @{ Purpose='ShaleNativeReadOnly'; Nonce=$Nonce } | Out-Null
New-AzSqlServer -ResourceGroupName $ResourceGroup -ServerName $ServerName -Location $Location -SqlAdministratorCredentials $AdminCredential -MinimalTlsVersion '1.2' -PublicNetworkAccess Enabled | Out-Null
New-AzSqlServerFirewallRule -ResourceGroupName $ResourceGroup -ServerName $ServerName -FirewallRuleName "acceptance-$Nonce" -StartIpAddress $ClientIp -EndIpAddress $ClientIp | Out-Null
New-AzSqlDatabase -ResourceGroupName $ResourceGroup -ServerName $ServerName -DatabaseName $DatabaseName -RequestedServiceObjectiveName 'S0' -CollationName 'SQL_Latin1_General_CP1_CI_AS' | Out-Null
$SqlServer = Get-AzSqlServer -ResourceGroupName $ResourceGroup -ServerName $ServerName
$Fqdn = $SqlServer.FullyQualifiedDomainName
$ResourceId = (Get-AzSqlDatabase -ResourceGroupName $ResourceGroup -ServerName $ServerName -DatabaseName $DatabaseName).ResourceId
New-Item -ItemType Directory -Force -Path work/azure-native | Out-Null
@{ subscription=$SubscriptionId; resourceGroup=$ResourceGroup; server=$ServerName;
   database=$DatabaseName; resourceId=$ResourceId; nonce=$Nonce;
   createdResourceGroup=$true; createdFirewall=$true; productionAccess=$false } |
   ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/resource-scope.json
```

An **existing owned disposable logical server** may instead be used if its owner authorizes this
acceptance, it is not `shale.database.windows.net`, and its firewall/secret consumers are controlled.
Skip creation of the resource group/server only; capture its actual resource group, server, FQDN and
administrator credential into the same variables. Generate a fresh `$Nonce` and `$DatabaseName` as
above and run `New-AzSqlDatabase` once. Never adopt an existing database, even one that looks empty.
Write the scope record before database creation with `createdResourceGroup=$false` and
`createdFirewall=$true` only if this packet adds the exact nonce firewall rule; otherwise set it false. Use
the existing-server cleanup below. An existing database would require a separate reviewed fixture
provenance plan; this packet deliberately refuses it. New S0 resources incur charges until deleted.

## 3. Supply independent contained credentials in child-process environment only

These variables are the exact test interface. The server admin is used only for setup, then removed
before the read-only Maven process. Generated secrets are cryptographically random, independent,
and used only by this acceptance. They are neither printed nor persisted. Keep this PowerShell
session open until cleanup; a new session requires independently retained approved secret access.

```powershell
$env:SHALE_AZURE_JDBC_URL = "jdbc:sqlserver://${Fqdn}:1433;databaseName=$DatabaseName"
$env:SHALE_AZURE_DATABASE = $DatabaseName
$env:SHALE_AZURE_CONTRACT_VERSION = 'V2'
$env:SHALE_AZURE_ISSUER = 'https://shale.test/oauth/mcp'
$env:SHALE_AZURE_RESOURCE = 'https://shale.test/mcp'
$env:SHALE_AZURE_OWNED_DATABASE_ACK = $DatabaseName
$env:SHALE_AZURE_OWNER_USERNAME = $AdminCredential.UserName
$env:SHALE_AZURE_OWNER_PASSWORD = $AdminCredential.GetNetworkCredential().Password
foreach ($Identity in @('AI','VERIFIER','AUTH','RUNTIME','BOOTSTRAP')) {
    [Environment]::SetEnvironmentVariable("SHALE_AZURE_${Identity}_USERNAME", "azro_$($Identity.ToLowerInvariant())", 'Process')
    $Secret = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)) + 'aB9!'
    [Environment]::SetEnvironmentVariable("SHALE_AZURE_${Identity}_PASSWORD", $Secret, 'Process')
}
$Secret = $null
Remove-Item work/azure-native/setup-sequence.txt -ErrorAction SilentlyContinue
$env:SHALE_AZURE_SETUP_OPT_IN = 'CREATE_OWNED_DISPOSABLE_AZURE_FIXTURE'
mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlDisposableSetupTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test
$SetupExit = $LASTEXITCODE
Remove-Item Env:SHALE_AZURE_OWNER_USERNAME,Env:SHALE_AZURE_OWNER_PASSWORD,Env:SHALE_AZURE_SETUP_OPT_IN -ErrorAction SilentlyContinue
$AdminCredential = $null
if ($SetupExit -ne 0) { throw 'Setup failed; STOP, preserve private diagnostics and clean up the exact owned resources' }
```

Owner setup has its own datasource: validated TLS, explicit disposable database, SQL credentials
outside the URL, 15-second login, 120,000-millisecond socket read, 20-second JDBC statement query
timeout and 5-second cancellation acknowledgement timeout. The total JUnit cap remains 300 seconds.
These are distinct bounds, not a promise to allow a statement to run for 120 seconds. The read-only
identity datasource stays at 3-second login / 5,000-millisecond socket timeout, and the runtime
attestation budget remains ten seconds. No retry or unbounded timeout is added.

Expect `AZURE_SETUP phase=OWNER_CONNECT status=BEGIN/PASS` and a sanitized bounds/driver-version
line. JDBC `Connection.getNetworkTimeout()` must report 120000 immediately after connection and
before each phase; a mismatch stops before the phase's work. Subsequent markers identify
OWNER_PREFLIGHT, STAGE0_BEFORE, SCHEMA_IMPORT, PREREQUISITE_MIGRATIONS,
PHASE1R_AND_V2_PREREQUISITE, V2_INSTALL, CONTAINED_USERS, DEPLOYMENT_BINDING,
CUMULATIVE_VERIFY, STAGE0_AFTER and OFF_CHECK, with BEGIN and PASS/FAIL plus elapsed milliseconds.
No SQL body, schema, connection URL, credential or SID is printed. Quiet logging does not suppress
these direct test markers. Record the exact last phase and driver/bounds line privately on failure.

The first owner-reported failure maps to line 34 of commit `23f96ed69d6548dd3073318cd7be0c9923070dbc`:
the **initial Stage 0** script, before schema import/migrations. Offline driver introspection proves
that the old later `setSocketTimeout(120000)` setter does override 5000 in the pinned 12.6.1.jre11
driver's merged properties. The historical five-second live failure's cause is not established;
the new connected-socket guard and phase markers are required evidence, not a claimed Azure fix.

Require **one executed setup test, zero failures/errors/skips**, plus fresh
`work/azure-native/setup-sequence.txt`. Delete any stale output files before a new attempt. A setup
failure may leave partial objects in this exact disposable database: delete it and start with a new
nonce. No retry/adoption, receipt replacement, module rewrite or canonicalization change is permitted.
This includes the owner-reported Stage 0 timeout: absence of `setup-sequence.txt` is not proof that
the database is reusable. Confirm deletion through section 5, clear stale variables/evidence, then
repeat resource creation with a fresh nonce and independent secrets. Do not rerun setup on that DB.

The setup executes these exact contained-user statements with separately generated SQL-escaped
password literals inside JDBC memory. The notation below explains the statements; **do not paste
passwords into an SQL file, SQLCMD `-v`/`-P`, shell history, Maven properties or a command argument**:

```sql
CREATE USER [azro_ai] WITH PASSWORD = '<independent in-memory AI secret>';
CREATE USER [azro_verifier] WITH PASSWORD = '<independent in-memory verifier secret>';
CREATE USER [azro_auth] WITH PASSWORD = '<independent in-memory auth secret>';
CREATE USER [azro_runtime] WITH PASSWORD = '<independent in-memory runtime secret>';
CREATE USER [azro_bootstrap] WITH PASSWORD = '<independent in-memory bootstrap secret>';
ALTER ROLE AiCaseDataRuntime ADD MEMBER [azro_ai];
ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER [azro_verifier];
ALTER ROLE AiIntegrationRuntime ADD MEMBER [azro_runtime];
ALTER ROLE AiIntegrationBootstrap ADD MEMBER [azro_bootstrap];
```

There are no corresponding server logins/master users. AUTH needs only a genuine independent
ordinary-session identity for SID comparison; it is not an ordinary-auth functional smoke test.
The owner is not one of the five comparison identities. The verifier receives only its existing
role: database VIEW DEFINITION, dependency inspection and SELECT on the four reviewed reference
tables. It never receives CONTROL DATABASE/state/server roles or protected-row SELECT.

Exact setup order (only on the new disposable database):

1. Positive edition 5 / dbo / explicit database / no existing custom objects or users.
2. Existing owner Stage 0 script; all seven finding counts zero, including scoped credentials.
3. Pinned schema-only private authoritative candidate.
4. `phase3b1.sql`, `phase3b2b.sql`, `phase3b2c1.sql`, `phase3b2c2.sql`, `revocation.sql`, `phase3b2d.sql`, `phase3b3.sql`.
5. Reuse `AiCaseDataV2AuthoritativeLocalSqlServerTest.beforeConfinement`: remove only the fixture's
   three candidate filters, assert the V2 prerequisite refuses their absence, run guarded Phase1R
   PREFLIGHT/APPLY/POSTCHECK, require its receipt, then run the V2 prerequisite. Its reviewed inputs
   come solely from this new hash-gated fixture, never unreviewed live adoption.
6. Unmodified `phase3b8-confinement.sql`, the contained identities above, and one immutable
   `case-read-v1` deployment binding using reserved test issuer/resource and JVM UTC / SQL UTC.
7. Cumulative `verify.sql`; repeat owner Stage 0 after setup; assert enabled capability count and
   tenant/integration/session/credential counts zero.

The seven prerequisite migrations are **already installed in production and must never be rerun
there**. This clean-room sequence has no production target. No application starts or capability
switches are enabled; no synthetic user/Case/integration rows are necessary for this structural gate.

## 4. Run READ-ONLY NATIVE AZURE GATE

```powershell
Remove-Item work/azure-native/read-only.properties -ErrorAction SilentlyContinue
$env:SHALE_AZURE_ATTESTATION_OPT_IN = 'AZURE_SQL_DATABASE_READ_ONLY'
mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlDatabaseReadOnlyTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test
$ReadOnlyExit = $LASTEXITCODE
Remove-Item Env:SHALE_AZURE_ATTESTATION_OPT_IN
if ($ReadOnlyExit -ne 0) { throw 'Native Azure gate did not pass; STOP and clean up' }
Get-Content work/azure-native/read-only.properties
```

Require the fresh Surefire report for this selector to show **1 executed / 0 failures / 0 errors /
0 skips**. Maven exit zero with no selected test or an opt-in skip is not acceptance. Require fresh
`read-only.properties`, all five contained identities with distinct SIDs and original own-session
database authentication, exact tokens, and V2 catalog/source/effective-authority PASS. The test
records the actual product version, edition, logical server and measured attestation milliseconds;
attestation must finish within the unchanged ten-second protected-request budget.

Expect **33 denied statements**: nine protected zero-row SELECTs, dbo impersonation, and an unsupported
master cross-database zero-row read for each of AI/verifier/bootstrap. Only specific SQL authority /
Azure cross-database refusal codes count; connection/syntax failures do not. Four credential-source
substitutions must fail attestation without SQL mutations. The complete verifier procedure inventory
must show exactly six AI EXECUTEs, zero verifier application EXECUTEs, and no bootstrap EXECUTE on
those six APIs. The production attestation independently checks DML/DDL/role/RLS/ownership/grant-option
and column authority, all objects, public/inherited routes, the reviewed modules and Case/RLS shape.
Positive EXECUTE authority is checked without invoking a Case procedure or creating audit rows.

The cumulative/package verifier checks the reviewed Case/RLS hash
`6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a`, source receipt
`60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`, exact Phase1R predicates,
module hashes/settings/owners/dependencies, triggers, schema binding and active-column order.
The installed confinement catalog receipt is destination-specific and is verified in place.
It is not interchangeable with the portable Case/RLS hash and must never be copied to another DB.
No newly observed Azure text variant is automatically accepted. On a difference, preserve private
diagnostics and record BLOCKED pending individual contract review, without changing expected hashes.

Owner Stage 0 supplies complete scoped-credential absence evidence using owner CONTROL; a filtered
verifier catalog cannot supply it. Cross-database rejection plus route/permission closure does not
prove the absence of reused secrets elsewhere: record that the generated secrets have no other
consumers and no other-database provisioning. Do not probe production or random remote databases.

**DISPOSABLE AZURE MUTATION/FAULT GATE stays OPEN.** Creating extra grants/roles, external sources,
credentials or altered RLS/modules as faults is separate future work. This packet performs only
clean-room fixture construction followed by non-mutating acceptance. It proves neither Case read
behavior with issued credentials nor production App Service/ingress/pilot readiness.

## 5. Delete the exact owned scope, then erase process secrets

Run even after a failed setup/test. The scope file contains no passwords and permits cleanup from
a new owner session. For the newly created acceptance resource group:

```powershell
$Scope = Get-Content work/azure-native/resource-scope.json -Raw | ConvertFrom-Json
Set-AzContext -SubscriptionId $Scope.subscription | Out-Null
if (-not $Scope.createdResourceGroup -or $Scope.resourceGroup -ne "shale-attest-$($Scope.nonce)") { throw 'Use the existing-server cleanup instead' }
$Group = Get-AzResourceGroup -Name $Scope.resourceGroup
if ($Group.Tags.Purpose -ne 'ShaleNativeReadOnly' -or $Group.Tags.Nonce -ne $Scope.nonce) { throw 'Owned resource tags mismatch' }
Remove-AzResourceGroup -Name $Scope.resourceGroup -Force | Out-Null
if (Get-AzResourceGroup -Name $Scope.resourceGroup -ErrorAction SilentlyContinue) { throw 'Cleanup not confirmed' }
Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*' | Remove-Item
Remove-Item Env:SHALE_CASE_VERIFY_SCHEMA,Env:SHALE_CASE_VERIFY_SCHEMA_SHA256 -ErrorAction SilentlyContinue
@{ resourceGroup=$Scope.resourceGroup; cleanup='CONFIRMED'; productionAccess=$false; productionChange=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/cleanup.json
```

For an **existing disposable server**, do not delete its resource group/server:

```powershell
$Scope = Get-Content work/azure-native/resource-scope.json -Raw | ConvertFrom-Json
Set-AzContext -SubscriptionId $Scope.subscription | Out-Null
if ($Scope.createdResourceGroup -or $Scope.database -ne "ShaleAzureReadOnly_$($Scope.nonce)" -or $Scope.server -eq 'shale') { throw 'Owned scope mismatch' }
Remove-AzSqlDatabase -ResourceGroupName $Scope.resourceGroup -ServerName $Scope.server -DatabaseName $Scope.database -Force | Out-Null
if (Get-AzSqlDatabase -ResourceGroupName $Scope.resourceGroup -ServerName $Scope.server -DatabaseName $Scope.database -ErrorAction SilentlyContinue) { throw 'Database cleanup not confirmed' }
if ($Scope.createdFirewall) {
    Remove-AzSqlServerFirewallRule -ResourceGroupName $Scope.resourceGroup -ServerName $Scope.server -FirewallRuleName "acceptance-$($Scope.nonce)" -ErrorAction SilentlyContinue
    if (Get-AzSqlServerFirewallRule -ResourceGroupName $Scope.resourceGroup -ServerName $Scope.server -FirewallRuleName "acceptance-$($Scope.nonce)" -ErrorAction SilentlyContinue) { throw 'Firewall cleanup not confirmed' }
}
Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*' | Remove-Item
Remove-Item Env:SHALE_CASE_VERIFY_SCHEMA,Env:SHALE_CASE_VERIFY_SCHEMA_SHA256 -ErrorAction SilentlyContinue
@{ database=$Scope.database; cleanup='CONFIRMED'; productionAccess=$false; productionChange=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/cleanup.json
```

Keep raw Surefire logs, resource records and all attachments private. Review sanitized counts,
platform/version, effective authority, receipts, any representation finding, resource scope,
secret-consumer separation and confirmed cleanup into the acceptance record. Never commit raw
SIDs, connection strings, passwords, access tokens or schema/module artifacts. Only after all
required live/operator evidence passes may the record use
`NATIVE AZURE IDENTITY / READ-ONLY ACCEPTANCE COMPLETE`. This never closes the mutation/fault gate
or authorizes production SQL, identities, JAR deployment, AI/MCP/OAuth activation or a pilot.
