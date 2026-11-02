# Native Azure contained identity / read-only operator packet

**PENDING WINDOWS OWNER VALIDATION OF THE TIMEOUT-ONLY CHANGE.**
Executable baseline requiring validation: `5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6`.
The earlier Windows result at `f767c1e97da22cb9c3406104c36d85f35474e094` does not
validate this delta. No updated live launcher is published yet; the retained launcher
below is historical and must not be run or bypassed. Both native gates remain OPEN;
#1929 stays draft/unmerged. No Azure or production access occurred during this update.

Prepared 2026-10-29 from `e129f2733bf7175425299a12ae97efdae3f23d22` (merged #1928).
Updated 2026-11-02 for the 600-second V2 baseline and pending Windows owner validation.
The second owner fixture was deleted with cleanup CONFIRMED; never reuse it. See the
[offline login investigation](native-azure-owner-login-followup.md).
Current evidence is in [the acceptance record](native-azure-readonly-acceptance.md).
This packet creates an empty disposable **Azure SQL Database** (EngineEdition 5), imports the
existing private authoritative fixture, and runs the existing native gate with V2 explicitly selected.
SQL Server, Managed Instance and a production database are not substitutes.

No authorized Azure resource, Azure identity or database credential is available in the task environment.
The following commands are for the resource owner. Run from the repository root in Windows PowerShell 5.1 (.NET Framework 4.8) or PowerShell 7,
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
$ExecutableHead = '5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6'
$AllowedEvidence = @(
    'docs/testing/native-azure-readonly-operator-runbook.md',
    'docs/testing/native-azure-readonly-acceptance.md',
    'docs/testing/native-azure-v2-install-followup.md',
    'docs/testing/native-azure-platform-catalog-followup.md'
)
git merge-base --is-ancestor $ExecutableHead HEAD
if ($LASTEXITCODE -ne 0) { throw 'Timeout-only executable baseline required' }
$ChangedSinceValidation = @(git diff --name-only $ExecutableHead HEAD)
if ($LASTEXITCODE -ne 0 -or @($ChangedSinceValidation | Where-Object { $_ -notin $AllowedEvidence }).Count -ne 0) {
    throw 'Executable inputs differ from timeout-only baseline; STOP'
}
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean tracked source required' }
# Non-Azure validation must not inherit an old native acceptance opt-in.
Remove-Item Env:SHALE_AZURE_SETUP_OPT_IN,Env:SHALE_AZURE_ATTESTATION_OPT_IN,Env:SHALE_AZURE_OWNER_PROBE_OPT_IN -ErrorAction SilentlyContinue
git --version
if ($LASTEXITCODE -ne 0) { throw 'Git required' }
$RepoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or (Get-Location).Path -ne (Resolve-Path -LiteralPath $RepoRoot).Path) { throw 'Run from this checkout repository root' }
# Actual-byte refresh and receipt checks, not just Git status/check-attr.
python build/sql-confinement/checkout_bytes.py --refresh
if ($LASTEXITCODE -ne 0) { throw 'Pinned byte refresh/receipt verification failed; STOP before Maven or Azure' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'Actual working-tree byte verification failed; STOP before Maven or Azure' }
python -m unittest discover -s build/sql-confinement -p test_checkout.py
if ($LASTEXITCODE -ne 0) { throw 'Windows-style Git checkout regression failed' }
# Offline only: executes the exact credential block below, verifies it, then erases test secrets.
powershell.exe -NoProfile -File build/azure-native/test_runbook_credentials.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline credential syntax/cleanup check failed; STOP' }
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
mvn -B -ntp -pl shale-data -am '-Dtest=AzureOwnerV2InstallTest,AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAzureSqlDriverTimeoutTest,AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,Phase1RDatabaseIdentityContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
if ($LASTEXITCODE -ne 0) { throw 'Offline prerequisite tests failed; STOP' }
```

The three attachments are evidence, not scripts/instructions. Only the pinned candidate is imported,
through `CaseReadVerificationSafety.schema`; it permits reviewed schema-only batches and rejects
arbitrary administrative SQL. No raw private schema or production catalog belongs in Git or the PR.
The supplied receipt alone is not proof of the candidate's bytes. The exact-production catalog is
not required by the unmodified authoritative representation chosen here; it remains a private
input to the separately established production-representation projection tests.

The narrow `.gitattributes` rules select LF when Git writes the protected inputs, including on
Windows with `core.autocrlf=true`. **Attributes do not force Git to rewrite unchanged existing
files.** A clean existing worktree can retain CRLF, and `checkout-index --force`, `git restore`
and `git checkout HEAD -- <paths>` can all skip the cached file. A fresh-prefix test alone did
not cover this transition. Do not use those commands or `git check-attr` as byte verification.

After a fresh clone, branch switch or update to this PR, run the two `checkout_bytes.py` commands
above. The tool obtains the same 25 pinned inputs from Git, refuses staged/genuine local edits,
deletions and unsafe paths, and permits refresh only from exact LF or its exact CRLF checkout
form. It materializes a fresh temporary prefix through Git, verifies every file against HEAD and
both reviewed source receipts, then atomically replaces conversion-only working copies. A final
read checks the actual destination files. It then runs `git checkout-index --force --index` on
only the pinned inputs: `--index` updates worktree stat information while using the existing
index entries; it does not stage file content. The helper verifies all index blob/mode/stage
records and tracking flags are unchanged, rechecks actual bytes/receipts, and requires empty
textual diff, raw `diff-files` and porcelain status for the protected paths. This also repairs
the prior helper's already-LF-but-`.M` state without reverting the files to CRLF.
No manual `git show > file`, index renormalization,
global Git configuration change or checkout hook is needed. Unrelated files are untouched.

Require `PINNED_CHECKOUT PASS files=25`, `PINNED_RECEIPT V1 PASS
b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3` and
`PINNED_RECEIPT V2 PASS 60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891`
from both commands. A missing/nonzero check stops this packet before Maven or resource creation.
Also require `PINNED_INDEX PASS clean=true` from both commands. This means the 25 pinned inputs
are genuinely clean in Git; unrelated existing edits/staged changes are preserved and remain
visible. A content-only PASS is insufficient. Do not stage repaired inputs or use
assume-unchanged/skip-worktree flags to hide `.M`; do not alter index blobs or reviewed receipts.
Ordinary `update-index --refresh` / `--really-refresh` alone can still report `needs update`
after the old atomic replacement. Use the approved helper, not a manual index workaround.
The verifier reuses the regression's raw-byte calculation: zero only the embedded digest field,
then SHA-256; no newline/module normalization. Existing SQL/resource blobs and receipts are unchanged.
Do not manually convert the installer or replace its receipt with the CRLF-derived hash.
Python 3.9+ and Git are required. The tool and transition regressions make no Azure connection.

The clean focused build must run from the reviewed head with no tracked edits. Record
`git rev-parse HEAD` and the fresh Surefire counts for each selector. Windows owner
validation for this timeout-only revision follows the minimum checklist below. The older
Phase1R-only local gate is retained for reference; its unchanged eight-test owner evidence
can carry forward from the prior validated baseline. No Azure opt-in is
needed for those checks. A local edition-injection pass is dispatch/receipt evidence,
not live Azure identity acceptance. Review the physical-incarnation receipt behavior:
restore or service-tier changes invalidate it; never rewrite a receipt to bypass drift.

For those local regressions only, use a **new dedicated loopback SQL Server**, not an
ordinary development/shared/production instance. The test creates and drops only its
nonce-named synthetic databases; it uses the existing local fixture connection helper.
It cannot accept a remote hostname or Azure URL:

```powershell
$LocalCredential = Get-Credential -UserName 'sa' -Message 'Dedicated loopback test SQL Server only'
try {
    $env:SHALE_PHASE1R_IDENTITY_OPT_IN = 'LOCAL_DISPOSABLE_PHASE1R_IDENTITY'
    $env:SHALE_PHASE1R_IDENTITY_PORT = Read-Host 'Dedicated loopback SQL port (1024-65535)'
    $env:SHALE_PHASE1R_IDENTITY_PASSWORD = $LocalCredential.GetNetworkCredential().Password
    mvn -B -ntp -pl shale-data -am '-Dtest=Phase1RDatabaseIdentityContractTest,Phase1RDatabaseIdentityLocalSqlServerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) { throw 'Local Phase1R identity regression failed; STOP' }
}
finally {
    Remove-Item Env:SHALE_PHASE1R_IDENTITY_OPT_IN,Env:SHALE_PHASE1R_IDENTITY_PORT,Env:SHALE_PHASE1R_IDENTITY_PASSWORD -ErrorAction SilentlyContinue
    $LocalCredential = $null
}
```

Require 2 offline contract tests and 6 local SQL tests, zero failures/errors/skips.
Local SQL edition injection tests batch dispatch, receipt binding and rollback; it does
not turn the local instance into Azure or close either native gate.

## V2 install local validation — required before any new packet review

On a new dedicated loopback SQL Server only, with the hash-verified private candidate
already selected in section 1, run the following offline gate. No Azure opt-in is used.
The local helper retains its existing self-signed loopback TLS exception; this does not
validate Azure TLS. Synthetic cancellation tests also run without the private candidate,
but the two complete-installer tests must not be skipped for Windows validation.

```powershell
$LocalCredential = Get-Credential -UserName 'sa' -Message 'New dedicated loopback SQL fixture only'
try {
    $env:SHALE_V2_INSTALL_OPT_IN = 'LOCAL_DISPOSABLE_V2_INSTALL'
    $env:SHALE_V2_INSTALL_PORT = Read-Host 'Dedicated loopback SQL port (1024-65535)'
    $env:SHALE_V2_INSTALL_PASSWORD = $LocalCredential.GetNetworkCredential().Password
    mvn -B -ntp -pl shale-data -am '-Dtest=AzureOwnerV2InstallTest,AzureOwnerV2InstallLocalSqlServerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) { throw 'Local V2 execution/rollback validation failed; STOP' }
}
finally {
    Remove-Item Env:SHALE_V2_INSTALL_OPT_IN,Env:SHALE_V2_INSTALL_PORT,Env:SHALE_V2_INSTALL_PASSWORD -ErrorAction SilentlyContinue
    $LocalCredential = $null
}
```

Require every selected test executed, zero failures/errors/skips, complete-installer success
and cumulative verifier success, real cancellation rollback, and owned fixture cleanup.
For this timeout-only revision, follow the minimum checklist below for the affected and
critical suites; the unchanged Phase1R-only local gate need not be repeated. Record exact
HEAD and fresh Surefire counts. No timing result here
predicts Azure S0 timing. Validation alone does not authorize another live attempt.

## Minimum Windows validation for the timeout-only change

Validate executable baseline `5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6` or a clean descendant with only the four
existing evidence Markdown files changed. Section 1 pins this baseline for **validation**,
not live execution. Record exact HEAD and require no tracked edits. No Azure opt-in,
Azure resource, production connection, production catalog input or deployment is needed.

1. Run section 1 completely: actual-byte refresh/check, checkout regressions, same-host
   offline PowerShell credential/fence parsing check, private input hash and clean focused
   Maven build. Require every selected focused test executed with zero failures/errors/skips.
   This includes all **5 `AzureOwnerV2InstallTest` tests**, checking the 600-second migration
   setting, 880-second outer cap, existing five-second close fallback, unchanged 20-second
   rollback checks, fixed warning sanitization and exact installer-byte recovery. No test
   needs to wait ten minutes to verify a configured ten-minute ceiling.
2. Supply the private authoritative schema-only candidate through
   `SHALE_CASE_VERIFY_SCHEMA`; its exact SHA-256 must remain
   `4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
   Section 1 verifies it and sets `SHALE_CASE_VERIFY_SCHEMA_SHA256`. Do not attach the
   candidate, its path, credentials or raw SQL output to shared evidence. The receipt alone
   is insufficient. The exact-production catalog is unnecessary for this delta.
3. Run the existing V2 local-validation block above on one **new dedicated loopback SQL
   Server** with that candidate available. Require **12 tests executed / 0 failures / 0
   errors / 0 skips**: five helper tests plus all seven local SQL tests, including complete
   reviewed-installer success/cumulative verification and cancellation before the real V2
   receipt with rollback/absence verification. Keep the local tests' existing time bounds.
   No native Azure or S0 performance claim follows from local timing. Destroy the dedicated
   local fixture on success or failure and report `V2_INSTALL_LOCAL_CLEANUP CONFIRMED` only
   after independently confirming it was removed. A skipped complete-installer test fails
   this prerequisite.
4. Remove the local V2 opt-in/credentials as in its existing `finally` block, then run the
   selector's affected suite and critical reactor below. The only acceptable affected skip
   is the intentionally unopted `AiCaseDataAzureSqlDisposableSetupTest`; all other selected
   tests must execute. Critical tests require zero failures/errors/skips. Do not set an Azure
   opt-in just to eliminate that expected affected-suite skip.

```powershell
python build/test-selection/select_tests.py --base 5df0357b5d167580d7202e3a82a68aef373d0880 --head HEAD --format json --output work/azure-native/timeout-selection.json --plan-output work/azure-native/timeout-plan.json
if ($LASTEXITCODE -ne 0) { throw 'Timeout-only affected selection failed; STOP' }
python build/test-selection/run_selection.py work/azure-native/timeout-plan.json --command affected
if ($LASTEXITCODE -ne 0) { throw 'Timeout-only affected suite failed; STOP' }
mvn -B -ntp test
if ($LASTEXITCODE -ne 0) { throw 'Critical reactor failed; STOP' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace verification failed; STOP' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'Final pinned bytes/index/receipts failed; STOP' }
git status --short
git rev-parse HEAD
```

Retain fresh Surefire XML privately; report exact HEAD, per-class executed/failure/error/skip
counts, protected-byte/receipt results, same-host PowerShell result and confirmed local
fixture destruction. The prior eight-test Phase1R local identity result may carry forward:
the executable delta changes no Phase1R code, SQL or identity semantics. No full-suite,
new diagnostic, query rewrite, new fault injection or additional timeout change is required.
Linux results are 23 focused passed, 147 affected passed plus one intentional Azure skip,
and 228 critical passed; they do not replace this Windows validation.

**After these Windows results pass**, review them and the exact candidate packet, then publish
one exact live launcher pinned to `5d3fea8a8e19c4a58e445dbe6eb8bce2574b7cb6` with only the four evidence files
allowed to differ. Retain the existing live-section digest, credential block, TLS, exact
workstation IPv4 firewall, probe → setup → read-only ordering and mandatory exact-scope
cleanup on success or failure. Until that review, the older launcher below remains withheld
for this executable revision; changing its pin now would falsely imply Windows validation.

The next live attempt asks only whether the unchanged installer succeeds with the
**600-second V2 migration ceiling** (existing five-second cancellation/close allowance).
Record actual V2 `elapsedMillis` from the existing success markers and continue the existing
read-only gate only after setup passes. On any non-deadline failure, stop and report its
specific sanitized phase/category/SQL state/code without claiming a 600-second expiration;
if the existing evidence cannot identify the cause, say so. On deadline expiration stop
and leave performance work separate. Always destroy the fresh disposable scope and confirm
absence. No retry, failed-fixture reuse, production access or deployment.

## Reviewed live launcher — mandatory

**Historical launcher; withheld for the timeout-only revision. Do not execute.**
Its old executable pin is intentionally retained until the Windows checklist above passes
and a subsequent exact-packet review publishes the replacement. The following text and
launcher describe the prior baseline, not current readiness.

Complete section 1 first, including its same-host Windows offline credential/fence check.
Windows owner validation at `f767c1e97da22cb9c3406104c36d85f35474e094`
is complete; see the acceptance record. Run this exact launcher once from the repository
root in that same private session. It rechecks the baseline, clean tracked state, pinned
checkout bytes, private input and live-section digest before any resource creation.
Only the four listed evidence Markdown files may differ from the validated baseline.
Do not paste live sections individually or remove a guard.
It executes the first PowerShell block in each of sections 2, 3 and 4 in order and always
enters section 5 after recording the new scope, including resource/probe/setup/read-only failure.
A missing, skipped or failing selected test stops downstream work. Interrupting/closing the
host or losing power cannot run a finally block: recover immediately using the non-secret
scope record and section 5 from a new owner session; cleanup is mandatory before any new attempt.

```powershell
$ErrorActionPreference = 'Stop'
$ValidatedHead = 'f767c1e97da22cb9c3406104c36d85f35474e094'
$AllowedEvidence = @(
    'docs/testing/native-azure-readonly-operator-runbook.md',
    'docs/testing/native-azure-readonly-acceptance.md',
    'docs/testing/native-azure-v2-install-followup.md',
    'docs/testing/native-azure-platform-catalog-followup.md'
)
git merge-base --is-ancestor $ValidatedHead HEAD
if ($LASTEXITCODE -ne 0) { throw 'Windows-validated executable baseline required' }
$ChangedSinceValidation = @(git diff --name-only $ValidatedHead HEAD)
if ($LASTEXITCODE -ne 0 -or @($ChangedSinceValidation | Where-Object { $_ -notin $AllowedEvidence }).Count -ne 0) {
    throw 'Executable inputs changed since Windows validation; new validation/review required'
}
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean tracked source required' }
$RepoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or (Get-Location).Path -ne (Resolve-Path -LiteralPath $RepoRoot).Path) { throw 'Run from repository root' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'Pinned bytes/index/receipts required before Azure' }
if ([string]::IsNullOrWhiteSpace($env:SHALE_CASE_VERIFY_SCHEMA) -or
    $env:SHALE_CASE_VERIFY_SCHEMA_SHA256 -cne '4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f') {
    throw 'Complete section 1 private input verification first'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $env:SHALE_CASE_VERIFY_SCHEMA).Hash.ToLowerInvariant() -cne $env:SHALE_CASE_VERIFY_SCHEMA_SHA256) {
    throw 'Private input changed; STOP before resource creation'
}
if (@(Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*').Count -ne 0) {
    throw 'Use a fresh process without inherited SHALE_AZURE_* values; complete section 1 first'
}
if (Test-Path -LiteralPath work/azure-native/resource-scope.json) {
    throw 'Retain prior private evidence separately and confirm prior cleanup before a fresh packet'
}
$Packet = [IO.File]::ReadAllText((Resolve-Path docs/testing/native-azure-readonly-operator-runbook.md).Path)
$LiveBlocks = @{}
$LiveSources = @()
foreach ($Number in @(2,3,4,5)) {
    $Section = [regex]::Match($Packet, "(?ms)^## $Number\. .*?(?=^## |\z)")
    $FenceMarker = ([string][char]96) * 3
    $Fence = [regex]::Match($Section.Value, "(?s)${FenceMarker}powershell\r?\n(.*?)${FenceMarker}")
    if (-not $Section.Success -or -not $Fence.Success) { throw 'Reviewed live section missing' }
    $LiveSources += $Fence.Groups[1].Value.Replace("`r`n", "`n")
    $LiveBlocks[$Number] = [scriptblock]::Create($Fence.Groups[1].Value)
}
$Digest = [Security.Cryptography.SHA256]::Create()
try {
    $LiveDigest = [BitConverter]::ToString($Digest.ComputeHash([Text.Encoding]::UTF8.GetBytes(($LiveSources -join "`n")))).Replace('-', '').ToLowerInvariant()
}
finally { $Digest.Dispose() }
if ($LiveDigest -cne '60e11e30133675c1a48b260463eee25aed69740c3bd3d1700389705a5e7050d5') { throw 'Reviewed live sections changed; STOP before resource creation' }
$ScopeWritten = $false
$Nonce = $null
try {
    . $LiveBlocks[2]
    if ((Read-Host 'Allow exact IPv4 firewall propagation (up to five minutes); type READY to probe once') -cne 'READY') {
        throw 'Owner readiness not confirmed; clean up without probing'
    }
    . $LiveBlocks[3]
    . $LiveBlocks[4]
}
finally {
    try {
        if ($ScopeWritten) {
            $RecordedScope = Get-Content work/azure-native/resource-scope.json -Raw | ConvertFrom-Json
            if ($RecordedScope.nonce -cne $Nonce) { throw 'Cleanup scope changed; STOP for owner reconciliation' }
            . $LiveBlocks[5]
        }
    }
    finally {
        Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*' | Remove-Item
        Remove-Item Env:SHALE_CASE_VERIFY_SCHEMA,Env:SHALE_CASE_VERIFY_SCHEMA_SHA256 -ErrorAction SilentlyContinue
        $AdminCredential = $null
        $LocalCredential = $null
        $Bytes = $null
        $Secret = $null
        $rng = $null
    }
}
```

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
if (@(Get-AzResourceGroup -ErrorAction Stop | Where-Object ResourceGroupName -CEQ $ResourceGroup).Count -ne 0) {
    throw 'Generated resource group already exists; never adopt it'
}
New-Item -ItemType Directory -Force -Path work/azure-native | Out-Null
@{ subscription=$SubscriptionId; resourceGroup=$ResourceGroup; server=$ServerName;
   database=$DatabaseName; nonce=$Nonce; createdResourceGroup=$true;
   createdFirewall=$true; productionAccess=$false } |
   ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/resource-scope.json
$ScopeWritten = $true
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

Only a newly created resource group, logical server and database are permitted for this
attempt. No existing-server alternative, existing database, failed fixture or adoption is allowed.
New S0 resources incur charges until deleted. Cleanup includes any partial creation failure.

## 3. Supply independent contained credentials in child-process environment only

These variables are the exact test interface. The server admin is used only for setup, then removed
before the read-only Maven process. Generated secrets are cryptographically random, independent,
and used only by this acceptance. They are neither printed nor persisted. Keep this PowerShell
session open until cleanup; a new session requires independently retained approved secret access.
The instance `Create()` / `GetBytes(byte[])` API works on Windows PowerShell's .NET Framework;
the newer static `GetBytes(int)` overload is deliberately unused. Each identity gets a fresh
48-byte cryptographic draw. Byte buffers are cleared and local secret references released even
on failure; the RNG is disposed. Generation failure removes all five partial credential pairs.
The owner credential and setup opt-in are removed in `finally`, including launcher failures.
.NET strings and inherited environment/JDBC copies cannot be reliably zeroed; keep the session
private and erase its environment through section 5, then close it. No secret is printed, saved,
or supplied as a command argument.

Before supplying any credentials, run the offline check from section 1 in the **same Windows
PowerShell/.NET host** used for this packet. On PowerShell 7 use `pwsh -NoProfile -File
build/azure-native/test_runbook_credentials.ps1` instead of `powershell.exe`. It extracts and
executes the marked block below, checks independent 48-byte credentials, a fresh second run,
no output, local cleanup, and partial-generation failure cleanup, then removes its test environment.
It refuses inherited `SHALE_AZURE_*` values and never loads Az modules, JDBC, or a database.
A Linux PowerShell pass does not replace this Windows host check.

```powershell
function Assert-NativeReport([string]$Class) {
    $Report = "shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.$Class.xml"
    if (-not (Test-Path -LiteralPath $Report)) { throw 'Fresh selected Surefire report required; STOP' }
    [xml]$Result = Get-Content -LiteralPath $Report -Raw
    if ([int]$Result.testsuite.tests -ne 1 -or
        [int]$Result.testsuite.failures -ne 0 -or [int]$Result.testsuite.errors -ne 0 -or
        [int]$Result.testsuite.skipped -ne 0) { throw 'Require 1 test, 0 failures/errors/skips; STOP' }
}
$env:SHALE_AZURE_JDBC_URL = "jdbc:sqlserver://${Fqdn}:1433;databaseName=$DatabaseName"
$env:SHALE_AZURE_DATABASE = $DatabaseName
$env:SHALE_AZURE_CONTRACT_VERSION = 'V2'
$env:SHALE_AZURE_ISSUER = 'https://shale.test/oauth/mcp'
$env:SHALE_AZURE_RESOURCE = 'https://shale.test/mcp'
$env:SHALE_AZURE_OWNED_DATABASE_ACK = $DatabaseName
$SetupExit = $null
try {
    $env:SHALE_AZURE_OWNER_USERNAME = $AdminCredential.UserName
    $env:SHALE_AZURE_OWNER_PASSWORD = $AdminCredential.GetNetworkCredential().Password
    # BEGIN OFFLINE-TESTED CONTAINED CREDENTIALS
    $rng = $null
    $Bytes = $null
    $Secret = $null
    try {
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        foreach ($Identity in @('AI','VERIFIER','AUTH','RUNTIME','BOOTSTRAP')) {
            try {
                [Environment]::SetEnvironmentVariable(
                    "SHALE_AZURE_${Identity}_USERNAME",
                    "azro_$($Identity.ToLowerInvariant())",
                    'Process'
                )
                $Bytes = New-Object byte[] 48
                $rng.GetBytes($Bytes)
                $Secret = [Convert]::ToBase64String($Bytes) + 'aB9!'
                [Environment]::SetEnvironmentVariable(
                    "SHALE_AZURE_${Identity}_PASSWORD", $Secret, 'Process'
                )
            }
            finally {
                if ($null -ne $Bytes) { [Array]::Clear($Bytes, 0, $Bytes.Length) }
                $Bytes = $null
                $Secret = $null
            }
        }
    }
    catch {
        foreach ($Identity in @('AI','VERIFIER','AUTH','RUNTIME','BOOTSTRAP')) {
            [Environment]::SetEnvironmentVariable("SHALE_AZURE_${Identity}_USERNAME", $null, 'Process')
            [Environment]::SetEnvironmentVariable("SHALE_AZURE_${Identity}_PASSWORD", $null, 'Process')
        }
        throw 'Contained credential generation failed; STOP and clean up the owned scope'
    }
    finally {
        if ($null -ne $rng) { $rng.Dispose() }
        $rng = $null
    }
    # END OFFLINE-TESTED CONTAINED CREDENTIALS
    Remove-Item work/azure-native/setup-sequence.txt -ErrorAction SilentlyContinue
    # Probe only after creation/firewall propagation, before enabling mutating setup.
    $ProbeExit = $null
    Remove-Item shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlOwnerConnectivityProbeTest.xml -ErrorAction SilentlyContinue
    $env:SHALE_AZURE_OWNER_PROBE_OPT_IN = 'PROBE_OWNED_DISPOSABLE_AZURE_LOGIN'
    try {
        mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlOwnerConnectivityProbeTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Djava.util.logging.config.file=$((Resolve-Path build/azure-native/quiet-jdbc-logging.properties).Path)" "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test
        $ProbeExit = $LASTEXITCODE
    }
    finally { Remove-Item Env:SHALE_AZURE_OWNER_PROBE_OPT_IN -ErrorAction SilentlyContinue }
    if ($ProbeExit -ne 0) { throw 'Owner probe failed; STOP before setup and clean up the exact owned scope' }
    Remove-Item shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlDisposableSetupTest.xml -ErrorAction SilentlyContinue
    Assert-NativeReport 'AiCaseDataAzureSqlOwnerConnectivityProbeTest'
    $env:SHALE_AZURE_SETUP_OPT_IN = 'CREATE_OWNED_DISPOSABLE_AZURE_FIXTURE'
    mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlDisposableSetupTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Djava.util.logging.config.file=$((Resolve-Path build/azure-native/quiet-jdbc-logging.properties).Path)" "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test
    $SetupExit = $LASTEXITCODE
    if ($SetupExit -ne 0) { throw 'Setup failed; STOP and clean up exact owned scope' }
    Assert-NativeReport 'AiCaseDataAzureSqlDisposableSetupTest'
    if (-not (Test-Path work/azure-native/setup-sequence.txt)) { throw 'Fresh setup sequence required; STOP' }
}
finally {
    Remove-Item Env:SHALE_AZURE_OWNER_USERNAME,Env:SHALE_AZURE_OWNER_PASSWORD,Env:SHALE_AZURE_SETUP_OPT_IN,Env:SHALE_AZURE_OWNER_PROBE_OPT_IN -ErrorAction SilentlyContinue
    $AdminCredential = $null
}
if ($SetupExit -ne 0) { throw 'Setup failed; STOP, preserve private diagnostics and clean up the exact owned resources' }
```

Before invoking section 3, allow the exact-workstation firewall change to propagate (Azure
reports up to five minutes); successful control-plane resource creation alone does not prove
JDBC readiness. Do not widen the firewall, pin an IP, change the connection policy or disable TLS.
A TCP-1433-only check cannot prove TLS validation or database login. The narrow probe above makes
one datasource connection invocation with the same validated owner configuration as setup, then
reads only edition, current database/user and empty custom-object/principal counts. No private
schema input, Stage 0, application object creation/adoption, migrations, Phase1R, V2 installer,
contained users, binding or acceptance receipt is involved. It closes the connection before setup.
Require a fresh Surefire report: **1 executed / 0 failures / 0 errors / 0 skips**, OWNER_CONNECT and
OWNER_PREFLIGHT PASS and `AZURE_OWNER_PROBE status=PASS applicationObjectsCreated=false
nativeAcceptance=false`. This is connectivity evidence only; setup repeats all checks independently.
The probe's JUnit cap is 180 seconds. There is no automatic probe/setup retry. On any probe failure,
STOP and clean up the exact owned scope; never turn a failed probe into permission to start setup.

Owner setup has its own datasource: validated TLS, explicit disposable database, SQL credentials
outside the URL, **30-second login**, **120,000-millisecond connected socket read**, **20-second
JDBC statement query** and **5-second cancellation acknowledgement** bounds. The setup JUnit cap
is **880 seconds**, preserving its previous 280-second allowance outside V2; it is not a
substitute for JDBC I/O bounds. JDBC 12.6.1 clamps initial socket
reads to the smaller of remaining login time and the configured finite socket timeout, including
initial TLS/TDS traffic. It does not automatically restore the 120000-ms guard after login:
`ownerConnect` explicitly sets that guard on the established connection and verifies it before
metadata/preflight/SQL; every subsequent phase verifies it again. A setter failure or mismatch
closes the connection and refuses work. The 30-second choice matches the pinned driver default
and Azure's at-least-30-second guidance; it does not claim a newly provisioned S0 database must
be ready within 30 seconds. See the [source analysis and limits](native-azure-owner-login-followup.md).
Read-only identity datasource bounds remain 3-second login / 5000-ms socket and runtime
attestation remains ten seconds. No security/SQL authority deadline is extended; only the
enclosing setup cap accommodates the V2 migration-budget increase.

Expect `AZURE_SETUP phase=OWNER_CONNECT status=BEGIN` followed by PASS or FAIL, elapsedMillis
and configured loginSeconds/socketMillis/querySeconds/cancelSeconds, even if login fails. PASS
additionally records connectedSocketMillis=120000. FAIL records exception/root classes and a
five-character uppercase alphanumeric SQL state (otherwise unavailable) and numeric SQL error
code. No exception message/cause/suppressed chain is rethrown to Surefire. Driver JUL logging is
separate from Logback: the owner commands load `quiet-jdbc-logging.properties`; the harness refuses
to open an owner connection if driver loggers permit raw output. Do not enable JDBC tracing.
Subsequent markers identify OWNER_PREFLIGHT, STAGE0_BEFORE, SCHEMA_IMPORT,
PREREQUISITE_MIGRATIONS, PHASE1R_AND_V2_PREREQUISITE, V2_INSTALL, CONTAINED_USERS,
DEPLOYMENT_BINDING, CUMULATIVE_VERIFY, STAGE0_AFTER and OFF_CHECK, with BEGIN and PASS/FAIL
plus elapsed milliseconds. Connection diagnostics contain no URL, target names, credentials,
SQL/schema, SID or token. Retain only these sanitized fields for shared connection evidence;
keep all other raw harness/Surefire evidence private.

The diagnostic V2 wrapper uses migration-specific `Statement.setQueryTimeout(600)` and an independent
600-second install wall deadline, `Statement.cancel()`, and the unchanged physical-close fallback after
five more seconds. Ordinary owner statements remain bounded at 20 seconds. This is necessary
because the pinned adaptive driver stops its own
query timer at the first TDS response; NOWAIT warnings must not weaken execution bounds.
It validates the complete reviewed LF source hash, then inserts fixed severity-10 checkpoint
statements outside module bodies. Original installer/manifest/source receipts stay exact;
these diagnostic statements are a harness overlay, not a replacement installer or new receipt.
Only exact allowlisted warning tokens are interpreted. The last observed ordinal/category
and total elapsed time are evidence; zero means unobserved (including compilation/send),
not proof no work ran. Warnings may be unavailable after transport loss. SQL state is allowlisted,
error code numeric, and original driver messages/throwable chains are suppressed.
On install failure the harness explicitly rolls back in T-SQL, checks transaction closure and
absence of V2 objects/types/roles/index/receipts, then fails setup. UNCONFIRMED is never acceptance.
A timeout after COMMIT may leave a complete installation; mandatory scope deletion still applies.
See the [diagnosis and validation limits](native-azure-v2-install-followup.md).

The first owner-reported failure maps to line 34 of commit `23f96ed69d6548dd3073318cd7be0c9923070dbc`:
the **initial Stage 0** script, before schema import/migrations. Offline driver introspection proves
that the old later `setSocketTimeout(120000)` setter does override 5000 in the pinned 12.6.1.jre11
driver's merged properties. The historical five-second live failure's cause is not established;
the new connected-socket guard and phase markers are required evidence, not a claimed Azure fix.

Require **one executed setup test, zero failures/errors/skips**, plus fresh
`work/azure-native/setup-sequence.txt`. Delete any stale output files before a new attempt. A setup
failure may leave partial objects in this exact disposable database: delete it and start with a new
nonce. No retry/adoption, receipt replacement, module rewrite or canonicalization change is permitted.
This includes both the owner-reported Stage 0 timeout and the second OWNER_CONNECT timeout: absence of `setup-sequence.txt` is not proof that
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
Remove-Item shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlDatabaseReadOnlyTest.xml -ErrorAction SilentlyContinue
$env:SHALE_AZURE_ATTESTATION_OPT_IN = 'AZURE_SQL_DATABASE_READ_ONLY'
mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlDatabaseReadOnlyTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test
$ReadOnlyExit = $LASTEXITCODE
Remove-Item Env:SHALE_AZURE_ATTESTATION_OPT_IN
if ($ReadOnlyExit -ne 0) { throw 'Native Azure gate did not pass; STOP and clean up' }
Assert-NativeReport 'AiCaseDataAzureSqlDatabaseReadOnlyTest'
if (-not (Test-Path work/azure-native/read-only.properties)) { throw 'Fresh read-only evidence required; STOP' }
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
if ($Scope.nonce -cnotmatch '^[a-f0-9]{32}$' -or
    -not $Scope.createdResourceGroup -or
    $Scope.resourceGroup -cne "shale-attest-$($Scope.nonce)" -or
    $Scope.server -cne "shale-attest-$($Scope.nonce)" -or
    $Scope.database -cne "ShaleAzureReadOnly_$($Scope.nonce)" -or
    $Scope.productionAccess -ne $false) { throw 'Exact fresh owned scope required; reconcile privately' }
Set-AzContext -SubscriptionId $Scope.subscription -ErrorAction Stop | Out-Null
# Successful inventory reads distinguish absence from authentication/authorization/read errors.
$Group = @(Get-AzResourceGroup -ErrorAction Stop | Where-Object ResourceGroupName -CEQ $Scope.resourceGroup)
if ($Group.Count -gt 1) { throw 'Ambiguous owned resource group; cleanup not confirmed' }
if ($Group.Count -eq 1) {
    if ($Group[0].Tags.Purpose -cne 'ShaleNativeReadOnly' -or
        $Group[0].Tags.Nonce -cne $Scope.nonce) { throw 'Owned resource tags mismatch; cleanup not confirmed' }
    Remove-AzResourceGroup -Name $Scope.resourceGroup -Force -ErrorAction Stop | Out-Null
}
if (@(Get-AzResourceGroup -ErrorAction Stop | Where-Object ResourceGroupName -CEQ $Scope.resourceGroup).Count -ne 0) {
    throw 'Cleanup not confirmed; no further attempt'
}
Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*' | Remove-Item
Remove-Item Env:SHALE_CASE_VERIFY_SCHEMA,Env:SHALE_CASE_VERIFY_SCHEMA_SHA256 -ErrorAction SilentlyContinue
@{ resourceGroup=$Scope.resourceGroup; nonce=$Scope.nonce; cleanup='CONFIRMED';
   productionAccess=$false; productionChange=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 work/azure-native/cleanup.json
Write-Output 'AZURE_NATIVE_CLEANUP CONFIRMED'
```

A resource creation failure is also a failed attempt. The initial scope record is written
before the first mutation; deletion of its entire new group removes the exact database,
logical server and nonce firewall rule, including partial resources. Never clean a group
whose nonce/name/tags differ. A cleanup error remains BLOCKED until owner reconciliation
and confirmed deletion; it does not permit a retry. The launcher erases process secrets
even when cleanup itself fails. Close the private session afterward.

Keep raw Surefire logs, resource records and all attachments private. Review sanitized counts,
platform/version, effective authority, receipts, any representation finding, resource scope,
secret-consumer separation and confirmed cleanup into the acceptance record. Never commit raw
SIDs, connection strings, passwords, access tokens or schema/module artifacts. Only after all
required live/operator evidence passes may the record use
`NATIVE AZURE IDENTITY / READ-ONLY ACCEPTANCE COMPLETE`. This never closes the mutation/fault gate
or authorizes production SQL, identities, JAR deployment, AI/MCP/OAuth activation or a pilot.


