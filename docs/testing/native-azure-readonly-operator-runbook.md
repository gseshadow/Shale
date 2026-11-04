# Native Azure contained identity / read-only operator packet

**BEFORE-FIRST PHASE DELTA: Windows validation and separate packet refresh pending. DO NOT RUN AZURE.**
The fixed phase diagnostic and allowlisted section 3 parser change executable inputs. The previous
baseline pins below intentionally reject this delta. Use only the [offline delta tests](native-azure-catalog-component-diagnostic.md#required-offline-windows-validation); do not create/relabel markers or execute section 1/live fences yet.
The updated sections 2/3/5 digest is `3b3a5eef6faff948959ca81714e93c5e35716cb39588f94061b3b07d89f5e03a`.

**2026-11-04: LOCALIZATION BASELINE REFRESH. NO AZURE AUTHORIZATION.**
The owner reports native Windows localization validation PASS at
`8d6bfa11b31a0334e16cc99c81cac3f35bfada48`: 15 tests, zero failures/errors/skips,
five Python guards, and `COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS`.
The preceding disposable attempt passed owner setup, V2 installation (~36.9 seconds), cumulative
verification and cleanup (`AZURE_NATIVE_CLEANUP CONFIRMED`), then localized the diagnostic failure
to `VERIFIER_COMPONENT_READ`; no comparison TSV was created. This is evidence only.

Section 1 verifies the preserved 15-test report from that same-host validation and creates fresh
reader/localization evidence bound to the validated executable `codeHead`. It then checks this exact
final packet without rerunning Maven, setup, Phase1R, V2 or timeout/datasource rehearsals.
The live marker records the exact reviewed packet commit as `sourceHead`, the validated localization
commit as `codeHead`, and boolean `azureAuthorized=false`. Neither marker authorizes Azure.
Do not restore, overwrite, reuse or relabel consumed/earlier markers. See the
[current evidence protocol](native-azure-catalog-component-diagnostic.md#localization-packet-refresh--2026-11-04).
Do not execute the live launcher until separately authorized. Both native acceptance gates remain OPEN.
The sole purpose of any separately authorized next disposable diagnostic is to identify the fixed
verifier component ordinal/name. Full native attestation section 4 remains excluded.

Run from `C:\Eclipse\Workspace\shale-parent` in a fresh private Windows PowerShell 5.1
(.NET Framework 4.8) or PowerShell 7 session with JDK 21, Maven, Git, Python, and the
`Az.Accounts`, `Az.Resources`, and `Az.Sql` modules. Complete section 1 in that same session first.
The existing launcher creates independent credentials at runtime. Never paste them into Git/chat,
enable transcription/debug/JDBC tracing, reuse a failed fixture, or use production.
Validated TLS, outbound TCP 1433, exact-workstation IPv4 firewall and mandatory cleanup still apply.

## 1. Validate the diagnostic-only delta on Windows

Set `$ReviewedPacketHead` to the **final commit SHA from the review handoff**, check out that
commit cleanly, and execute this complete fence in a fresh Windows session. A terminating guard
failure invalidates the sequence: stop, resolve the guard, then rerun the entire offline fence.
Never paste downstream commands manually. Do not overwrite/relabel old validation markers.
Preserve the original 15-test Surefire XML from the owner's completed validation at the executable
baseline on this Windows host. This fence verifies it and creates a new test-only marker; it does
not relabel an old marker or rerun tests. If that original report is unavailable, stop and recover
fresh localization-only evidence with the focused class; never substitute a report from another head.
Only same-source packet guards, pinned-byte/receipt checks and same-host credential/fence checks
run here. No Maven or database rehearsal is needed. Keep both markers local and private.

```powershell
$ErrorActionPreference = 'Stop'
$ExecutableHead = '8d6bfa11b31a0334e16cc99c81cac3f35bfada48'
$AllowedEvidence = @(
    'docs/testing/native-azure-readonly-operator-runbook.md',
    'docs/testing/native-azure-readonly-acceptance.md',
    'docs/testing/native-azure-catalog-component-diagnostic.md',
    'build/azure-native/test_catalog_components.py',
    'build/azure-native/test_runbook_launcher.ps1'
)
# Set this to the final packet SHA from the review handoff, never an inferred moving branch.
if ($ReviewedPacketHead -cnotmatch '^[a-f0-9]{40}$') { throw 'Set the exact reviewed final packet commit first' }
$ValidationPath = 'work/azure-native/catalog-diagnostic-validation.json'
if (Test-Path -LiteralPath $ValidationPath) { throw 'Existing live marker forbidden; retain prior evidence separately, never reuse it' }
if (Test-Path -LiteralPath work/azure-native/resource-scope.json) { throw 'Prior scope must be reconciled and cleanup confirmed first' }
$ValidatedSourceHead = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $ValidatedSourceHead -cne $ReviewedPacketHead) { throw 'Exact final reviewed packet checkout required' }
git merge-base --is-ancestor $ExecutableHead HEAD
if ($LASTEXITCODE -ne 0) { throw 'Reviewed diagnostic code required' }
$Changed = @(git diff --name-only $ExecutableHead HEAD)
if ($LASTEXITCODE -ne 0 -or @($Changed | Where-Object { $_ -notin $AllowedEvidence }).Count -ne 0) { throw 'Executable inputs changed; new review required' }
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean tracked source required' }
$RepoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or (Get-Location).Path -ne (Resolve-Path -LiteralPath $RepoRoot).Path) { throw 'Run from repository root' }
if (@(Get-ChildItem Env: | Where-Object Name -Like 'SHALE_AZURE_*').Count -ne 0) { throw 'Fresh process without inherited Azure settings required' }
if ($env:OS -cne 'Windows_NT') { throw 'Native Windows host required' }
$ReaderValidationPath = 'work/azure-native/catalog-reader-windows-validation.json'
if (Test-Path -LiteralPath $ReaderValidationPath) { throw 'Existing reader marker forbidden; never overwrite or relabel earlier evidence' }
$ReaderReportPath = 'shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml'
if (-not (Test-Path -LiteralPath $ReaderReportPath -PathType Leaf)) { throw 'Original same-host localization report required' }
$ReportDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderReportPath).Hash.ToLowerInvariant()
[xml]$ReaderReport = Get-Content -LiteralPath $ReaderReportPath -Raw
if ($ReaderReport.testsuite.name -cne 'com.shale.data.security.ai.AzureCatalogComponentsTest' -or
    $ReaderReport.testsuite.tests -cne '15' -or $ReaderReport.testsuite.failures -cne '0' -or
    $ReaderReport.testsuite.errors -cne '0' -or $ReaderReport.testsuite.skipped -cne '0' -or
    @($ReaderReport.testsuite.testcase).Count -ne 15 -or
    @($ReaderReport.SelectNodes('//testcase/failure | //testcase/error | //testcase/skipped')).Count -ne 0) {
    throw 'Require original localization evidence: 15 tests, zero failures/errors/skips'
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderReportPath).Hash.ToLowerInvariant() -cne $ReportDigest) { throw 'Localization report changed during verification' }
New-Item -ItemType Directory -Force work/azure-native | Out-Null
@{ codeHead=$ExecutableHead; sourceHead=$ExecutableHead; hostName=[Environment]::MachineName;
   result='PASS'; scope='CATALOG_READER_DELTA_ONLY'; tests=15; failures=0; errors=0;
   skipped=0; launcherGuards=5; reportSha256=$ReportDigest;
   validationMarker='COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS'; azureAuthorized=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $ReaderValidationPath
function Assert-ReaderValidation([string]$Baseline) {
    if (-not (Test-Path -LiteralPath $ReaderValidationPath -PathType Leaf)) { throw 'Passed Windows reader marker required; no reuse of earlier markers' }
    $Reader = Get-Content -LiteralPath $ReaderValidationPath -Raw | ConvertFrom-Json
    if ($Reader.codeHead -cne $Baseline -or $Reader.sourceHead -cne $Baseline -or
        $Reader.hostName -cne [Environment]::MachineName -or $Reader.result -cne 'PASS' -or
        $Reader.scope -cne 'CATALOG_READER_DELTA_ONLY' -or
        $Reader.validationMarker -cne 'COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS' -or
        $Reader.reportSha256 -cnotmatch '^[a-f0-9]{64}$' -or
        ($Reader.tests -isnot [int] -and $Reader.tests -isnot [long]) -or $Reader.tests -ne 15 -or
        ($Reader.failures -isnot [int] -and $Reader.failures -isnot [long]) -or $Reader.failures -ne 0 -or
        ($Reader.errors -isnot [int] -and $Reader.errors -isnot [long]) -or $Reader.errors -ne 0 -or
        ($Reader.skipped -isnot [int] -and $Reader.skipped -isnot [long]) -or $Reader.skipped -ne 0 -or
        ($Reader.launcherGuards -isnot [int] -and $Reader.launcherGuards -isnot [long]) -or $Reader.launcherGuards -ne 5 -or
        $Reader.azureAuthorized -isnot [bool] -or $Reader.azureAuthorized -ne $false) {
        throw 'Exact same-host reader PASS (15 tests, zero failures/errors/skips, 5 guards, no Azure authorization) required'
    }
}
Assert-ReaderValidation $ExecutableHead
$ReaderDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash.ToLowerInvariant()
python build/sql-confinement/checkout_bytes.py --refresh
if ($LASTEXITCODE -ne 0) { throw 'Pinned byte refresh failed' }
python build/sql-confinement/checkout_bytes.py
if ($LASTEXITCODE -ne 0) { throw 'Pinned bytes/index/receipts failed' }
python -m unittest discover -s build/azure-native -p test_catalog_components.py
if ($LASTEXITCODE -ne 0) { throw 'Catalog diagnostic launcher guards failed; no manual continuation' }
# Run the credential/fence check in this exact PowerShell edition and Windows host.
$PowerShellExe = (Get-Process -Id $PID).Path
& $PowerShellExe -NoProfile -File build/azure-native/test_runbook_credentials.ps1
if ($LASTEXITCODE -ne 0) { throw 'Same-host offline PowerShell credential/fence checks failed; no manual continuation' }
& $PowerShellExe -NoProfile -File build/azure-native/test_runbook_launcher.ps1
if ($LASTEXITCODE -ne 0) { throw 'Same-host offline launcher cancellation/ordering checks failed; no manual continuation' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Diff whitespace check failed' }
$Candidate = (Resolve-Path (Read-Host 'Private authoritative-candidate.sql path')).Path
$ExpectedCandidate = '4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $Candidate).Hash.ToLowerInvariant() -cne $ExpectedCandidate) { throw 'Private candidate hash mismatch' }
$env:SHALE_CASE_VERIFY_SCHEMA = $Candidate
$env:SHALE_CASE_VERIFY_SCHEMA_SHA256 = $ExpectedCandidate
Assert-ReaderValidation $ExecutableHead
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderReportPath).Hash.ToLowerInvariant() -cne $ReportDigest) { throw 'Localization report changed during packet validation' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash.ToLowerInvariant() -cne $ReaderDigest) { throw 'Reader evidence changed during packet validation' }
$FinalHead = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $FinalHead -cne $ReviewedPacketHead) { throw 'Packet head changed during validation' }
$TrackedState = @(git status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $TrackedState.Count -ne 0) { throw 'Clean final packet required' }
New-Item -ItemType Directory -Force work/azure-native | Out-Null
@{ codeHead=$ExecutableHead; sourceHead=$ValidatedSourceHead; hostName=[Environment]::MachineName;
   result='PASS'; scope='CATALOG_COMPONENT_PACKET_ONLY'; readerValidationSha256=$ReaderDigest;
   launcherGuards=5; azureAuthorized=$false } |
    ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $ValidationPath
Write-Output 'CATALOG_DIAGNOSTIC_WINDOWS_VALIDATION PASS azureAuthorized=false'
```

The live marker remains `azureAuthorized=false`. The original reader marker is hash-bound to it;
both are rechecked and consumed only after all section 2 input/read-only preflight succeeds,
immediately before the recovery record and first Azure resource mutation. A preflight cancellation
or refusal preserves both markers and writes no scope. This packet supplies the fresh evidence
protocol for the Windows-validated localization baseline; never restore an earlier marker.
Keep the private schema local; its supplied digest never substitutes for checking actual bytes.

## Reviewed live launcher — mandatory

**DO NOT RUN: separate explicit owner authorization is still required after packet review.**
The launcher pins the diagnostic code, requires the same-host validation marker and unchanged
source head, and consumes both markers at the section 2 mutation boundary. It runs sections 2 and 3, then
always section 5 once the new scope is recorded. Section 3 performs the unchanged probe/setup,
then the separate read-only component test while the owner credential is still available.
The full native attestation in section 4 is deliberately not selected for this diagnostic attempt.
On any diagnostic failure expose only a fixed category (and approved component metadata), then clean up. On 19 matches,
record the TSV and clean up; investigate `confinement-v2-catalog.sql` execution separately.
A missing, skipped or failing selected test stops downstream work. Host interruption cannot run
finally: recover using the private non-secret scope record and section 5, confirm deletion, and
never reuse the fixture or automatically retry. Cleanup failure remains BLOCKED until reconciled.

```powershell
$ErrorActionPreference = 'Stop'
$ValidatedHead = '8d6bfa11b31a0334e16cc99c81cac3f35bfada48'
$AllowedEvidence = @(
    'docs/testing/native-azure-readonly-operator-runbook.md',
    'docs/testing/native-azure-readonly-acceptance.md',
    'docs/testing/native-azure-catalog-component-diagnostic.md',
    'build/azure-native/test_catalog_components.py',
    'build/azure-native/test_runbook_launcher.ps1'
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
$ValidationPath = 'work/azure-native/catalog-diagnostic-validation.json'
if (-not (Test-Path -LiteralPath $ValidationPath)) { throw 'Complete section 1 Windows validation first' }
$ReaderValidationPath = 'work/azure-native/catalog-reader-windows-validation.json'
function Assert-ReaderValidation([string]$Baseline) {
    if (-not (Test-Path -LiteralPath $ReaderValidationPath -PathType Leaf)) { throw 'Passed Windows reader marker required; no reuse of earlier markers' }
    $Reader = Get-Content -LiteralPath $ReaderValidationPath -Raw | ConvertFrom-Json
    if ($Reader.codeHead -cne $Baseline -or $Reader.sourceHead -cne $Baseline -or
        $Reader.hostName -cne [Environment]::MachineName -or $Reader.result -cne 'PASS' -or
        $Reader.scope -cne 'CATALOG_READER_DELTA_ONLY' -or
        $Reader.validationMarker -cne 'COMPONENT_LOCALIZATION_WINDOWS_VALIDATION PASS' -or
        $Reader.reportSha256 -cnotmatch '^[a-f0-9]{64}$' -or
        ($Reader.tests -isnot [int] -and $Reader.tests -isnot [long]) -or $Reader.tests -ne 15 -or
        ($Reader.failures -isnot [int] -and $Reader.failures -isnot [long]) -or $Reader.failures -ne 0 -or
        ($Reader.errors -isnot [int] -and $Reader.errors -isnot [long]) -or $Reader.errors -ne 0 -or
        ($Reader.skipped -isnot [int] -and $Reader.skipped -isnot [long]) -or $Reader.skipped -ne 0 -or
        ($Reader.launcherGuards -isnot [int] -and $Reader.launcherGuards -isnot [long]) -or $Reader.launcherGuards -ne 5 -or
        $Reader.azureAuthorized -isnot [bool] -or $Reader.azureAuthorized -ne $false) {
        throw 'Exact same-host reader PASS (15 tests, zero failures/errors/skips, 5 guards, no Azure authorization) required'
    }
}
Assert-ReaderValidation $ValidatedHead
$Validation = Get-Content -LiteralPath $ValidationPath -Raw | ConvertFrom-Json
$CurrentHead = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $Validation.result -cne 'PASS' -or
    $Validation.codeHead -cne $ValidatedHead -or $Validation.sourceHead -cne $CurrentHead -or
    $Validation.hostName -cne [Environment]::MachineName -or
    $Validation.scope -cne 'CATALOG_COMPONENT_PACKET_ONLY' -or
    $Validation.launcherGuards -ne 5 -or $Validation.azureAuthorized -isnot [bool] -or
    $Validation.azureAuthorized -ne $false -or
    $Validation.readerValidationSha256 -cne (Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash.ToLowerInvariant()) { throw 'Same-host validation of this exact source required' }
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
foreach ($Number in @(2,3,5)) {
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
if ($LiveDigest -cne '3b3a5eef6faff948959ca81714e93c5e35716cb39588f94061b3b07d89f5e03a') { throw 'Reviewed live sections changed; STOP before resource creation' }
# A future run requires separate owner authorization; packet validation never grants it.
if ((Read-Host 'Only after separate explicit owner authorization, type AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT') -cne 'AUTHORIZE ONE DISPOSABLE CATALOG ATTEMPT') {
    throw 'Azure not authorized; STOP without mutation or manual continuation'
}
# Bind both markers across operator input; section 2 consumes them only at the mutation boundary.
$LiveReaderDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash
$LiveMarkerDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $ValidationPath).Hash
$ScopeWritten = $false
$Nonce = $null
try {
    . $LiveBlocks[2]
    if ((Read-Host 'Allow exact IPv4 firewall propagation (up to five minutes); type READY to probe once') -cne 'READY') {
        throw 'Owner readiness not confirmed; clean up without probing'
    }
    . $LiveBlocks[3]
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
`$AdminCredential` is a secure `PSCredential`, entered through console-only `Read-Host -AsSecureString`.
No password is echoed, written to disk, supplied in process arguments, Maven properties, SQLCMD
variables, logs or Git. The local `Get-Credential` override below avoids the Windows dialog.
Login/context selection, nonempty available region, credential entry, strict single IPv4 validation
and the fresh-name collision check all precede consumption. Directory preparation also precedes it;
only the non-secret recovery record separates consumption from the first Azure mutation.
A failed/cancelled input leaves both markers intact and no resource scope. Run section 2 only
through the reviewed launcher so terminating failures retain the existing `finally` cleanup.

```powershell
function Get-Credential {
    param(
        [string]$UserName,
        [string]$Message
    )
    if ($Message) { Write-Host $Message }
    if (-not $UserName) { $UserName = Read-Host 'Username' }
    $SecurePassword = Read-Host "Password for $UserName" -AsSecureString
    return [System.Management.Automation.PSCredential]::new($UserName,$SecurePassword)
}
$Login = Connect-AzAccount -ErrorAction Stop
if ($null -eq $Login) { throw 'Azure login cancelled' }
$SubscriptionId = Read-Host 'Authorized acceptance subscription ID'
if ([string]::IsNullOrWhiteSpace($SubscriptionId)) { throw 'An authorized acceptance subscription is required' }
$Context = Set-AzContext -SubscriptionId $SubscriptionId -ErrorAction Stop
if ($null -eq $Context -or $Context.Subscription.Id -cne $SubscriptionId) { throw 'Authorized subscription context selection failed' }
$Location = Read-Host 'Authorized Azure region (for example westus2)'
if ([string]::IsNullOrWhiteSpace($Location)) { throw 'An authorized Azure region is required' }
if (@(Get-AzLocation -ErrorAction Stop | Where-Object Location -CEQ $Location).Count -ne 1) { throw 'Select an available Azure region' }
$Nonce = [guid]::NewGuid().ToString('N')
$ResourceGroup = "shale-attest-$Nonce"
$ServerName = "shale-attest-$Nonce"
$DatabaseName = "ShaleAzureReadOnly_$Nonce"
$AdminCredential = Get-Credential -UserName 'azro_owner' -Message 'New disposable SQL server administrator; use a fresh strong password'
if ($null -eq $AdminCredential -or $AdminCredential.Password.Length -eq 0) { throw 'Console credential entry cancelled or empty' }
$ClientIp = Read-Host 'This workstation public IPv4 address (single address, no CIDR)'
$ParsedIp = $null
if ($ClientIp -cnotmatch '^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$' -or
    -not [System.Net.IPAddress]::TryParse($ClientIp, [ref]$ParsedIp) -or
    $ParsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    $ClientIp -eq '0.0.0.0') { throw 'A single workstation IPv4 address is required' }
# Persist non-secret cleanup scope BEFORE the first resource mutation, including partial failures.
if (@(Get-AzResourceGroup -ErrorAction Stop | Where-Object ResourceGroupName -CEQ $ResourceGroup).Count -ne 0) {
    throw 'Generated resource group already exists; never adopt it'
}
New-Item -ItemType Directory -Force -Path work/azure-native | Out-Null
# All operator input and read-only preflight succeeded. Never restore consumed evidence.
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $ReaderValidationPath).Hash -cne $LiveReaderDigest -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $ValidationPath).Hash -cne $LiveMarkerDigest) {
    throw 'Validation evidence changed during operator input; STOP before mutation'
}
Remove-Item -LiteralPath $ReaderValidationPath -ErrorAction Stop
Remove-Item -LiteralPath $ValidationPath -ErrorAction Stop
# Only the non-secret recovery record separates consumption from the first Azure mutation.
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
    # Setup is complete. No mutating setup opt-in remains during the separate diagnostic.
    Remove-Item Env:SHALE_AZURE_SETUP_OPT_IN -ErrorAction SilentlyContinue
    Remove-Item work/azure-native/catalog-components.tsv -ErrorAction SilentlyContinue
    Remove-Item shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlCatalogComponentTest.xml -ErrorAction SilentlyContinue
    # BEGIN OFFLINE-TESTED CATALOG FAILURE PARSER
    function Get-SafeCatalogFailure([string[]]$Messages) {
        $Fallback = 'Catalog component diagnostic failed before a complete comparison; stage=REPORT_WRITE; discard fixture; no retry/adoption'
        if ($Messages.Count -ne 1) { return $Fallback }
        $ComponentNames = @('objects_and_modules','columns','constraints','foreign_key_columns',
            'indexes','security_predicates','object_permissions','triggers','integration_role_owners',
            'tenant_filter_definition','runtime_verifier_permissions','execution_and_schema_owners','dependencies',
            'runtime_role_owner','verifier_role_owner','database_options','unicode_ranges','unicode_lower','word_transitions')
        $Phases = @('STATEMENT_CREATE','QUERY_TIMEOUT_SETUP','BATCH_EXECUTE','BEFORE_FIRST_RESULT')
        foreach ($Fixed in @('OWNER_TARGET_PREFLIGHT','VERIFIER_TARGET_PREFLIGHT','OWNER_COMPONENT_READ','VERIFIER_COMPONENT_READ','REPORT_WRITE')) {
            $Labels = @('') # Preserve legacy stage-only diagnostics too.
            if ($Fixed -cin @('OWNER_COMPONENT_READ','VERIFIER_COMPONENT_READ')) {
                $Labels += '; component=0:before_first_component'
                foreach ($Phase in $Phases) { $Labels += "; component=0:before_first_component; phase=$Phase" }
                for ($Index = 0; $Index -lt $ComponentNames.Count; $Index++) {
                    $Labels += "; component=$($Index+1):$($ComponentNames[$Index])"
                }
            }
            foreach ($Label in $Labels) {
                $SafeMessage = "Catalog component diagnostic failed before a complete comparison; stage=$Fixed$Label; discard fixture; no retry/adoption"
                if ($Messages -ccontains $SafeMessage) { return $SafeMessage }
            }
        }
        return $Fallback
    }
    # END OFFLINE-TESTED CATALOG FAILURE PARSER
    $env:SHALE_AZURE_CATALOG_COMPONENT_OPT_IN = 'AZURE_SQL_DATABASE_CATALOG_COMPONENTS'
    $ComponentExit = $null
    try {
        mvn -B -ntp -pl shale-data -am '-Dtest=AiCaseDataAzureSqlCatalogComponentTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Duser.timezone=UTC' "-Djava.util.logging.config.file=$((Resolve-Path build/azure-native/quiet-jdbc-logging.properties).Path)" "-Dlogback.configurationFile=$((Resolve-Path build/case-read-verification/quiet-logback.xml).Path)" test *> $null
        $ComponentExit = $LASTEXITCODE
    }
    catch { throw 'Catalog component diagnostic failed; stage=REPORT_WRITE; discard fixture; no retry/adoption' }
    finally { Remove-Item Env:SHALE_AZURE_CATALOG_COMPONENT_OPT_IN -ErrorAction SilentlyContinue }
    # BEGIN OFFLINE-TESTED CATALOG REPORT FAILURE
    try {
        $ComponentReport = 'shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlCatalogComponentTest.xml'
        if ($ComponentExit -ne 0) {
            # Read privately; never echo XML, stack traces, driver output or exception chains.
            $SafeFailure = Get-SafeCatalogFailure -Messages @()
            if (Test-Path -LiteralPath $ComponentReport) {
                [xml]$FailureReport = Get-Content -LiteralPath $ComponentReport -Raw
                $Messages = @($FailureReport.SelectNodes('//testcase/failure | //testcase/error') | ForEach-Object { $_.GetAttribute('message') })
                $SafeFailure = Get-SafeCatalogFailure -Messages $Messages
            }
            throw $SafeFailure
        }
        Assert-NativeReport 'AiCaseDataAzureSqlCatalogComponentTest'
        $Names = @('objects_and_modules','columns','constraints','foreign_key_columns',
            'indexes','security_predicates','object_permissions','triggers','integration_role_owners',
            'tenant_filter_definition','runtime_verifier_permissions','execution_and_schema_owners','dependencies',
            'runtime_role_owner','verifier_role_owner','database_options','unicode_ranges','unicode_lower','word_transitions')
        $Lines = @(Get-Content -LiteralPath work/azure-native/catalog-components.tsv)
        if ($Lines.Count -ne 20 -or $Lines[0] -cne "ordinal`tname`townerBytes`tverifierBytes`townerSha256`tverifierSha256`tequal") { throw 'REPORT_WRITE' }
        for ($Index = 0; $Index -lt 19; $Index++) {
            $Fields = $Lines[$Index+1].Split([char]9)
            if ($Fields.Count -ne 7 -or $Fields[0] -cne [string]($Index+1) -or $Fields[1] -cne $Names[$Index] -or
                $Fields[2] -cnotmatch '^(0|[1-9][0-9]{0,18})$' -or $Fields[3] -cnotmatch '^(0|[1-9][0-9]{0,18})$' -or
                $Fields[4] -cnotmatch '^[a-f0-9]{64}$' -or $Fields[5] -cnotmatch '^[a-f0-9]{64}$' -or
                $Fields[6] -cne 'true' -or $Fields[2] -cne $Fields[3] -or $Fields[4] -cne $Fields[5]) { throw 'REPORT_WRITE' }
        }
        # Emit exactly the seven approved columns, once, only after complete successful validation.
        $Lines | Write-Output
    }
    catch {
        throw (Get-SafeCatalogFailure -Messages @($_.Exception.Message))
    }
    # END OFFLINE-TESTED CATALOG REPORT FAILURE
}
finally {
    Remove-Item Env:SHALE_AZURE_OWNER_USERNAME,Env:SHALE_AZURE_OWNER_PASSWORD,Env:SHALE_AZURE_SETUP_OPT_IN,Env:SHALE_AZURE_OWNER_PROBE_OPT_IN,Env:SHALE_AZURE_CATALOG_COMPONENT_OPT_IN -ErrorAction SilentlyContinue
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

Historical full-gate instructions, not selected by the current diagnostic launcher. Do not run
this section in the component-only attempt, even if all 19 components match.

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



