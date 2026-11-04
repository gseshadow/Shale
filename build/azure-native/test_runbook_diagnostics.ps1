#requires -Version 5.1
# Offline only: execute the exact allowlist parser and report-failure boundary with synthetic XML.
$ErrorActionPreference = 'Stop'
$Runbook = Join-Path $PSScriptRoot '../../docs/testing/native-azure-readonly-operator-runbook.md'
$Text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Runbook).Path)
$Parser = [regex]::Match($Text, '(?s)# BEGIN OFFLINE-TESTED CATALOG FAILURE PARSER\r?\n(.*?)# END OFFLINE-TESTED CATALOG FAILURE PARSER')
$Boundary = [regex]::Match($Text, '(?s)# BEGIN OFFLINE-TESTED CATALOG REPORT FAILURE\r?\n(.*?)# END OFFLINE-TESTED CATALOG REPORT FAILURE')
if (-not $Parser.Success -or -not $Boundary.Success) { throw 'Required diagnostic parser/boundary missing' }
. ([scriptblock]::Create($Parser.Groups[1].Value))
$ReportFailure = [scriptblock]::Create($Boundary.Groups[1].Value)
function Assert-NativeReport { throw 'Unexpected success path in offline failure test' }
$Prefix = 'Catalog component diagnostic failed before a complete comparison; stage='
$Suffix = '; discard fixture; no retry/adoption'
$Fallback = "${Prefix}REPORT_WRITE$Suffix"
$Stages = @('OWNER_TARGET_PREFLIGHT','VERIFIER_TARGET_PREFLIGHT','OWNER_COMPONENT_READ','VERIFIER_COMPONENT_READ','REPORT_WRITE')
$Names = @('objects_and_modules','columns','constraints','foreign_key_columns',
    'indexes','security_predicates','object_permissions','triggers','integration_role_owners',
    'tenant_filter_definition','runtime_verifier_permissions','execution_and_schema_owners','dependencies',
    'runtime_role_owner','verifier_role_owner','database_options','unicode_ranges','unicode_lower','word_transitions')
$Phases = @('STATEMENT_CREATE','QUERY_TIMEOUT_SETUP','BATCH_EXECUTE','BEFORE_FIRST_RESULT')
$Canaries = @('private driver message','SELECT private_sql_text','jdbc:sqlserver://private-target.invalid',
    'private database/server','user=private_user;password=private_password','SID=0x70726976617465','token=private_token')
$Scratch = Join-Path ([IO.Path]::GetTempPath()) ('shale-diagnostic-offline-' + [guid]::NewGuid().ToString('N'))
$RelativeReport = 'shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AiCaseDataAzureSqlCatalogComponentTest.xml'
$ReportPath = Join-Path $Scratch $RelativeReport
[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($ReportPath))
$script:Checks = 0
function Assert-Case([string[]]$Messages, [string]$Expected) {
    $Result = @(Get-SafeCatalogFailure -Messages $Messages)
    if ($Result.Count -ne 1 -or $Result[0] -cne $Expected) { throw 'Allowlist parser returned unexpected output' }
    foreach ($Canary in $Canaries) {
        if ($Result[0].Contains($Canary)) { throw 'Allowlist parser exposed synthetic private evidence' }
    }
    # The real runbook reads these private message attributes and sanitizes again in its catch.
    $Elements = @($Messages | ForEach-Object { '<failure message="' + [Security.SecurityElement]::Escape($_) + '" />' })
    [IO.File]::WriteAllText($ReportPath, '<testsuite><testcase>' + ($Elements -join '') + '</testcase></testsuite>')
    $ComponentExit = 1
    $Caught = $null
    $Output = @()
    try { $Output = @(. $ReportFailure *>&1) }
    catch { $Caught = $_.Exception.Message }
    if ($Output.Count -ne 0 -or $Caught -cne $Expected) { throw 'Report failure boundary leaked output or lost fixed diagnostic context' }
    foreach ($Canary in $Canaries) {
        if ($Caught.Contains($Canary)) { throw 'Public diagnostic exposed synthetic private evidence' }
    }
    $script:Checks++
}
Push-Location $Scratch
try {
    foreach ($Stage in $Stages) { Assert-Case @("$Prefix$Stage$Suffix") "$Prefix$Stage$Suffix" }
    foreach ($Stage in @('OWNER_COMPONENT_READ','VERIFIER_COMPONENT_READ')) {
        $Zero = "$Prefix$Stage; component=0:before_first_component"
        Assert-Case @("$Zero$Suffix") "$Zero$Suffix"
        foreach ($Phase in $Phases) { Assert-Case @("$Zero; phase=$Phase$Suffix") "$Zero; phase=$Phase$Suffix" }
        for ($Index = 0; $Index -lt $Names.Count; $Index++) {
            $Message = "$Prefix$Stage; component=$($Index+1):$($Names[$Index])$Suffix"
            Assert-Case @($Message) $Message
            # No phase is permitted with a known component; wrong ordinal/name pairs refuse.
            Assert-Case @($Message.Replace($Suffix, "; phase=BATCH_EXECUTE$Suffix")) $Fallback
            Assert-Case @("$Prefix$Stage; component=$($Index+1):$($Names[($Index+1)%19])$Suffix") $Fallback
        }
        foreach ($Canary in $Canaries) {
            Assert-Case @("$Zero; phase=$Canary$Suffix") $Fallback
            Assert-Case @("$Prefix$Stage; component=1:$Canary$Suffix") $Fallback
            Assert-Case @("$Zero; phase=BATCH_EXECUTE$Suffix$Canary") $Fallback
            Assert-Case @("$Zero; phase=BATCH_EXECUTE$Suffix", $Canary) $Fallback
        }
        foreach ($Tail in @('; phase=UNKNOWN','; phase=batch_execute','; phase=BATCH_EXECUTE; phase=BATCH_EXECUTE')) {
            Assert-Case @("$Zero$Tail$Suffix") $Fallback
        }
        Assert-Case @("$Prefix$Stage; component=20:word_transitions$Suffix") $Fallback
        Assert-Case @("$Prefix$Stage; component=01:objects_and_modules$Suffix") $Fallback
    }
    Assert-Case @() $Fallback
    Assert-Case @("${Prefix}UNKNOWN$Suffix") $Fallback
    Assert-Case @("${Prefix}OWNER_TARGET_PREFLIGHT; component=0:before_first_component; phase=BATCH_EXECUTE$Suffix") $Fallback
    Assert-Case @("${Prefix}REPORT_WRITE; component=13:dependencies$Suffix") $Fallback
    Assert-Case @("${Prefix}VERIFIER_COMPONENT_READ; phase=BATCH_EXECUTE$Suffix") $Fallback
    Assert-Case @("${Prefix}VERIFIER_COMPONENT_READ; component=0:dependencies; phase=BATCH_EXECUTE$Suffix") $Fallback
    Assert-Case @("${Prefix}VERIFIER_COMPONENT_READ; component=0:before_first_component; phase=BATCH_EXECUTE$Suffix`n") $Fallback
    # Missing/malformed XML must also be collapsed without echoing its private contents.
    foreach ($Mode in @('missing','malformed')) {
        if ($Mode -eq 'missing') { Remove-Item -LiteralPath $ReportPath -Force }
        else { [IO.File]::WriteAllText($ReportPath, '<private SQL password SID token') }
        $ComponentExit = 1
        $Caught = $null
        try { . $ReportFailure }
        catch { $Caught = $_.Exception.Message }
        if ($Caught -cne $Fallback) { throw 'Missing/malformed report did not fail closed' }
        $script:Checks++
    }
}
finally {
    Pop-Location
    Remove-Item -LiteralPath $Scratch -Recurse -Force
}
Write-Output "RUNBOOK_DIAGNOSTICS PASS cases=$($script:Checks) azureExecuted=false"
