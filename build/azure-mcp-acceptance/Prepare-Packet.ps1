#Requires -Version 7.0
<# Offline preparation only. No Azure, SQLCMD, JDBC, secret generation or application startup. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PacketDirectory,
    [string]$AuthoritativeFixture
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$templates = Join-Path $repo 'docs/sql/ai-integration/azure-acceptance'
$packet = [IO.Path]::GetFullPath($PacketDirectory)
if ($packet -eq $repo -or -not $packet.StartsWith((Join-Path $repo 'work') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a private directory under this checkout work/; never a shared or tracked output directory.'
}
[void][IO.Directory]::CreateDirectory($packet)
foreach ($name in @('normalize.sql','identities-foundation.sql','identities.sql','seed.sql')) {
    $dest = Join-Path $packet $name
    if (-not (Test-Path -LiteralPath $dest)) { Copy-Item -LiteralPath (Join-Path $templates $name) -Destination $dest }
}
$bindingInputs = Join-Path $packet 'binding-inputs.sql'
if (-not (Test-Path -LiteralPath $bindingInputs)) {
    Copy-Item -LiteralPath (Join-Path $templates 'binding-inputs.sql') -Destination $bindingInputs
}
$phaseInputs = Join-Path $packet 'phase1r.json'
if (-not (Test-Path -LiteralPath $phaseInputs)) {
    [IO.File]::WriteAllText($phaseInputs, '{"ReviewedStrictHash":null,"ReviewedOverlayHash":null,"ReviewedOtherPredicatesHash":null}')
}
$fixture = Join-Path $packet 'fixture.sql'
if ($AuthoritativeFixture) {
    $hash = (Get-FileHash -LiteralPath $AuthoritativeFixture -Algorithm SHA256).Hash
    if ($hash -cne '4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F') { throw 'STOP: authoritative fixture hash mismatch.' }
    $classes = Join-Path $packet ('classes-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($classes)
    & javac -encoding UTF-8 -d $classes (Join-Path $repo 'shale-data/src/test/java/com/shale/data/verification/CaseReadVerificationSafety.java') (Join-Path $PSScriptRoot 'ExportFixture.java')
    if ($LASTEXITCODE -ne 0) { throw 'STOP: offline fixture exporter compilation failed.' }
    # CREATE_NEW in exporter; re-render into a new file, never silently trust/edit a prior export.
    $newFixture = Join-Path $packet ('fixture-' + [guid]::NewGuid().ToString('N') + '.sql')
    & java -cp $classes com.shale.data.verification.ExportFixture $AuthoritativeFixture $newFixture
    if ($LASTEXITCODE -ne 0) { throw 'STOP: authoritative DDL parser refused fixture.' }
    Move-Item -LiteralPath $newFixture -Destination $fixture -Force
} else {
    # Never consume a manually supplied or stale fixture.sql without rechecking the private source.
    [IO.File]::WriteAllText($fixture, "THROW 59807,'STOP: provide the unchanged hash-gated authoritative fixture to Prepare-Packet.',1;`n")
}
function Expand-Sql([string]$path, [int]$depth = 0) {
    if ($depth -gt 10) { throw 'STOP: cyclic SQL include.' }
    $text = [IO.File]::ReadAllText($path)
    $text = [regex]::Replace($text, '(?m)^:r (.+)\r?$', {
        param($match)
        $include = $match.Groups[1].Value.Trim()
        if ($include -eq 'docs/sql/ai-integration/deployment-binding/inputs.sql') {
            return (Expand-Sql $bindingInputs ($depth+1)) + "`n" + [IO.File]::ReadAllText((Join-Path $templates 'binding-target.sql'))
        }
        if ($include -notmatch '^docs/sql/[a-zA-Z0-9/_-]+\.sql$') { throw 'STOP: unexpected SQL include.' }
        return Expand-Sql (Join-Path $repo $include) ($depth+1)
    })
    # Only existing include/on-error directives are supported; refuse reconnect/shell/variables.
    $text = [regex]::Replace($text, '(?m)^:on error exit\r?$', '')
    $code = [regex]::Replace($text, "(?s)/\*.*?\*/|--[^\r\n]*|'(?:''|[^'])*'", ' ')
    if ($text -match '(?m)^\s*[:!]' -or $code -match '(?im)^\s*USE\s+') { throw 'STOP: unreviewed SQLCMD directive or database switch.' }
    return $text
}
$target = [IO.File]::ReadAllText((Join-Path $templates 'target.sql'))
$owner = [IO.File]::ReadAllText((Join-Path $templates 'owner.sql'))
$fence = $target + "`n" + $owner + "`nGO`n"
$rendered = Join-Path $packet ('rendered-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($rendered)
$manifest = @()
foreach ($stage in (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)) {
    $stageFence = $fence
    if ($stage.PSObject.Properties.Name -contains 'user') { $stageFence = $target + "`nGO`n" }
    $sql = ":on error exit`n" + $stageFence
    foreach ($source in $stage.sources) {
        if ($source.StartsWith('private:')) {
            $body = Expand-Sql (Join-Path $packet $source.Substring(8))
        } elseif ($source.StartsWith('identity:')) {
            $expectedUser = $stage.user
            $expectedRole = $source.Substring(9)
            $body = [IO.File]::ReadAllText((Join-Path $templates 'identity-check.sql'))
            $body = $body.Replace('__USER__', $expectedUser).Replace('__ROLE__', $expectedRole)
        } elseif ($source.StartsWith('phase1r:')) {
            $mode = $source.Substring(8)
            $reviewed = Get-Content -LiteralPath $phaseInputs -Raw | ConvertFrom-Json -AsHashtable
            $body = [IO.File]::ReadAllText((Join-Path $repo 'docs/sql/phase1r/engine.sql'))
            $ready = $true
            foreach ($key in @('ReviewedStrictHash','ReviewedOverlayHash','ReviewedOtherPredicatesHash')) {
                if (-not $reviewed.ContainsKey($key) -or $reviewed[$key] -notmatch '^[a-fA-F0-9]{64}$') { $ready = $false; break }
                $body = $body.Replace('$(' + $key + ')', [string]$reviewed[$key])
            }
            if ($ready) {
                $body = $body.Replace('$(ExpectedDatabase)', 'ShaleMcpAcceptance').Replace('$(ReviewedCollation)', 'SQL_Latin1_General_CP1_CI_AS')
                $body = "DECLARE @Phase1RMode varchar(10)='$mode';`n" + $body
            } else { $body = "THROW 59807,'STOP: independent fresh-fixture Phase1R hash review required.',1;" }
        } else { $body = Expand-Sql (Join-Path $repo $source) }
        # Bound each source on both sides. No app/tenant context is carried between source batches.
        $sql += $body + "`nGO`n" + $stageFence
    }
    # SQLCMD substitution is disabled on execution, so unresolved placeholders must never be shipped.
    if ($sql.Contains('$(')) { throw 'STOP: unresolved SQLCMD input.' }
    $file = Join-Path $rendered ($stage.name + '.sql')
    [IO.File]::WriteAllText($file, $sql, [Text.UTF8Encoding]::new($false))
    $manifest += [pscustomobject]@{ Stage=$stage.name; SHA256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash }
}
$manifest | Export-Csv -LiteralPath (Join-Path $rendered 'sha256.csv') -NoTypeInformation
Write-Output "Prepared offline: $rendered"
Write-Output 'STOP at blocked templates. Review rendered SQL and hashes; no SQL connection or execution occurred.'
