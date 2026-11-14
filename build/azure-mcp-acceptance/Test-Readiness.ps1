#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RenderedDirectory,
    [string]$AuthoritativeFixture,
    [string]$Stage = 'All',
    [string[]]$GroupStages,
    [switch]$PreparedOnly,
    [string]$SqlcmdExecutable
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
if (-not $RenderedDirectory) { $RenderedDirectory = Read-Host 'Exact reviewed rendered directory (never select latest automatically)' }
if (-not $AuthoritativeFixture) { $AuthoritativeFixture = Read-Host 'Unchanged authoritative fixture path' }
$dir = Assert-PrivatePath $RenderedDirectory
$stages = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
if ($Stage -cne 'All' -and $Stage -cnotin @($stages.name)) { throw 'STOP: unknown stage.' }
if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Algorithm SHA256).Hash -cne 'C7F3374BEE53A4EC3566B5A6645AF7DC250669504F69BDD1028A66F666B0C5F5') { throw 'STOP: reviewed stage manifest changed.' }
$names = @($stages.name)
# Strict callers retain whole-packet integrity checks. Fast-track validates only
# the next contiguous group; future private inputs/files are not prerequisites yet.
$groupCheck=$PSBoundParameters.ContainsKey('GroupStages')
if ($groupCheck) {
    if ($Stage -cne 'All' -or -not $GroupStages) { throw 'STOP: select one nonempty readiness group.' }
    $position=[array]::IndexOf($names,$GroupStages[0])
    if ($position -lt 18 -or $position+$GroupStages.Count -gt $names.Count) { throw 'STOP: invalid readiness group.' }
    for ($i=0; $i -lt $GroupStages.Count; $i++) {
        if ($GroupStages[$i] -cne $names[$position+$i]) { throw 'STOP: readiness group must follow manifest order.' }
    }
}
$checkedNames=if ($groupCheck) { @($GroupStages) } else { @($names)+@('reconcile') }
if ($names.Count -ne 51 -or $names[17] -cne '15b-foundation-users' -or $names[18] -cne '15c-seed' -or $names[19] -cne '16-binding-preflight') { throw 'STOP: stage ordering changed; review required.' }
if ($AuthoritativeFixture) {
    if ((Get-FileHash -LiteralPath $AuthoritativeFixture -Algorithm SHA256).Hash -cne '4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F') { throw 'STOP: fixture fingerprint mismatch.' }
} else { throw 'STOP: unchanged authoritative fixture path required for offline fingerprint check.' }
$tools = if ($PreparedOnly) { @() } else { @('java','javac') }
if ($SqlcmdExecutable) {
    if (-not [IO.Path]::IsPathRooted($SqlcmdExecutable) -or -not (Test-Path -LiteralPath $SqlcmdExecutable -PathType Leaf)) { throw 'STOP: absolute SQLCMD executable path required.' }
} else { $tools += 'sqlcmd' }
foreach ($tool in $tools) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "STOP: missing local prerequisite: $tool." }
}
$manifest = @(Import-Csv -LiteralPath (Join-Path $dir 'sha256.csv'))
if ($manifest.Count -ne 52) { throw 'STOP: incomplete manifest; prepare a new packet.' }
$owner = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/owner.sql'))
$target = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/target.sql'))
foreach ($name in $checkedNames) {
    [void](Get-StageHash $dir $name)
    $sql = [IO.File]::ReadAllText((Join-Path $dir ($name + '.sql')))
    $code = [regex]::Replace($sql, "(?s)/\*.*?\*/|--[^\r\n]*|'(?:''|[^'])*'", ' ')
    $spec = $stages | Where-Object name -CEQ $name
    $fence = $target + "`n" + $owner + "`nGO`n"
    if ($spec -and ($spec.PSObject.Properties.Name -contains 'user')) { $fence = $target + "`nGO`n" }
    if (-not $sql.StartsWith(":on error exit`n" + $fence) -or $sql.Contains('$(') -or $code -match '(?im)^\s*:(?!on error exit)|^\s*!|^\s*USE\s+') { throw 'STOP: guard/directive integrity failure.' }
}
function Assert-StageInputs([string]$Stage) {
$sql = [IO.File]::ReadAllText((Join-Path $dir ($Stage + '.sql')))
if ($sql -match 'STOP: provide the unchanged|STOP: independent fresh-fixture') { throw 'STOP: unresolved fixture/Phase1R prerequisite.' }
if ($Stage -ceq '15c-seed') {
    if ($sql -notmatch '@SeedApproved\s+bit\s*=\s*1') { throw 'STOP: seed approval absent.' }
    $values = @([regex]::Matches($sql, '@(?:A71|A72|B81|B82)\s+nvarchar\(255\)\s*=\s*N?''(\$2[aby]\$(?:0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53})''') | ForEach-Object { $_.Groups[1].Value })
    if ($values.Count -ne 4 -or @($values | Select-Object -Unique).Count -ne 4) { throw 'STOP: four distinct correctly shaped synthetic BCrypt inputs required.' }
    foreach ($setting in @('ANSI_NULLS ON','ANSI_PADDING ON','ANSI_WARNINGS ON','ARITHABORT ON','CONCAT_NULL_YIELDS_NULL ON','QUOTED_IDENTIFIER ON','NUMERIC_ROUNDABORT OFF')) {
        if (-not $sql.Contains("SET $setting;")) { throw 'STOP: seed session setup absent.' }
    }
}
if ($Stage -match '^(16|17|18)-') {
    foreach ($flag in @('Approved','OffConfirmed','EvidenceConfirmed')) {
        if ($sql -notmatch "@Bind$flag\s+bit\s*=\s*1") { throw 'STOP: pre-Stage-16 worker/JVM/timezone evidence and explicit binding approvals required.' }
    }
    foreach ($zone in @('Java','Sql')) { if ($sql -notmatch "@Bind${zone}Zone\s+nvarchar\(max\)\s*=\s*N'[^']+'") { throw 'STOP: reviewed worker/SQL zones missing.' } }
    $pairs = [regex]::Matches($sql, "\(\s*'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})'\s*,\s*'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})'\s*\)")
    $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($year in 2025..2028) { foreach ($month in @(1,3,4,7,10,11,12)) { [void]$expected.Add(('{0}-{1:00}-15T00:00:00' -f $year,$month)) } }
    if ($pairs.Count -ne 28) { throw 'STOP: exactly 28 worker wall-time pairs required.' }
    foreach ($pair in $pairs) {
        if (-not $expected.Remove($pair.Groups[1].Value)) { throw 'STOP: missing/duplicate/unexpected worker sample instant.' }
        $wall = [datetime]::MinValue
        if (-not [datetime]::TryParseExact($pair.Groups[2].Value, "yyyy-MM-dd'T'HH:mm:ss", [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$wall)) { throw 'STOP: malformed worker wall-time sample.' }
    }
}
if ($Stage -ceq '28-contained-users') {
    if ($sql -notmatch '@ProvisionApproved\s+bit\s*=\s*1') { throw 'STOP: contained-user provisioning approval absent.' }
    $secrets = @([regex]::Matches($sql, "\(N'mcpacc_[a-z_]+',N'[A-Za-z0-9]+',N'((?:''|[^'])*)'\)") | ForEach-Object { $_.Groups[1].Value.Replace("''", "'") })
    $foundation = [IO.File]::ReadAllText((Join-Path $dir '15b-foundation-users.sql'))
    $oldSecrets = @([regex]::Matches($foundation, "\(N'mcpacc_[a-z_]+',(?:N'[A-Za-z0-9]+'|NULL),N'((?:''|[^'])*)'\)") | ForEach-Object { $_.Groups[1].Value.Replace("''", "'") })
    if ($oldSecrets.Count -ne 5) { throw 'STOP: retain all five existing approved foundation inputs; do not regenerate them.' }
    if (@($oldSecrets | Where-Object { $_ -cin $secrets }).Count) { throw 'STOP: capability credentials must be independent of foundation credentials.' }
    if ($secrets.Count -ne 12 -or @($secrets | Select-Object -Unique).Count -ne 12 -or @($secrets | Where-Object Length -LT 20).Count) { throw 'STOP: twelve independent contained-user inputs required.' }
}
}
# Dependencies for the next group must exist before its dispatch. Strict callers
# still check the entire packet. Live grants/identities remain SQL verification gates.
$checkedSpecs=if ($groupCheck) { @($stages | Where-Object name -CIn $GroupStages) } else { $stages }
foreach ($stageSpec in $checkedSpecs) {
    foreach ($source in $stageSpec.sources) {
        if ($source -notmatch ':') { if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "../../$source"))) { throw 'STOP: source dependency missing.' } }
    }
}
if ($groupCheck) {
    foreach ($name in $GroupStages) {
        try { Assert-StageInputs $name }
        catch {
            # Closed classification: no native process has started for this group.
            $_.Exception.Data['AcceptancePrerequisiteStage']=$name
            throw
        }
    }
} elseif ($Stage -ceq 'All') {
    $blocked = @()
    foreach ($item in $stages[18..50]) {
        try { Assert-StageInputs $item.name; Write-Output "OFFLINE READY: $($item.name)" }
        catch { $blocked += $item.name; Write-Output "BLOCKED: $($item.name); $($_.Exception.Message)" }
    }
    if ($blocked.Count) { throw "STOP: $($blocked.Count) remaining stages have offline blockers. No SQL connection occurred." }
} else { Assert-StageInputs $Stage }
if ($groupCheck) { Write-Output "OFFLINE READY GROUP: $($GroupStages -join ','); hashes and destination fences verified. Live state/approval remain required." }
else { Write-Output "OFFLINE READY: $Stage; 51 stage hashes and reconciliation hash verified. Live state/approvals remain required." }
