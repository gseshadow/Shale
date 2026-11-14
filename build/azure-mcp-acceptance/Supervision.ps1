# Shared offline validation and strict output interpretation; no connection on import.
Set-StrictMode -Version Latest
# These APIs also work on PowerShell 7.0 / .NET Core 3.1. The previous
# HashData/ToHexString/OperatingSystem.IsWindows calls required newer .NET.
function Get-TextSHA256([string]$Text) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).Replace('-', '') }
    finally { $algorithm.Dispose() }
}
function Get-SafeSqlErrorNumbers([object[]]$Lines) {
    $text = ($Lines | ForEach-Object { [string]$_ }) -join "`n"
    $numbers = [Collections.Generic.HashSet[int]]::new()
    foreach ($match in [regex]::Matches($text, '(?im)^\s*(?:Password: ?)?Msg ([1-9][0-9]{0,9}),\s*Level [0-9]{1,2},\s*State [0-9]{1,3}(?:,|\s*$)')) {
        $number = 0
        if ([int]::TryParse($match.Groups[1].Value, [ref]$number)) { [void]$numbers.Add($number) }
        if ($numbers.Count -ge 32) { break }
    }
    return @($numbers | Sort-Object)
}
function Write-SafeStageDiagnostic($Result) {
    $exitText = if ($null -eq $Result.ExitCode) { 'UNKNOWN' } else { [string][int]$Result.ExitCode }
    $numbers = @($Result.SqlErrorNumbers) -join ','
    Write-Host "STOP: stage=$($Result.Stage) exit=$exitText SQL-errors=[$numbers] category=$($Result.FailureCode); independent reconciliation required."
}
function Assert-PrivatePath([string]$Path) {
    $repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $full = [IO.Path]::GetFullPath($Path)
    $comparison = if ([Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    if (-not $full.StartsWith((Join-Path $repo 'work') + [IO.Path]::DirectorySeparatorChar, $comparison)) { throw 'STOP: private work/ path required.' }
    # Refuse symlink/junction escape, including ancestors.
    $current = $full
    while ($current -and $current -ne $repo) {
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'STOP: linked private path refused.' }
        $current = [IO.Path]::GetDirectoryName($current)
    }
    return $full
}
function Get-StageHash([string]$Directory, [string]$Name) {
    $file = Assert-PrivatePath (Join-Path $Directory ($Name + '.sql'))
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
    [void](Assert-PrivatePath (Join-Path $Directory 'sha256.csv'))
    $rows = @(Import-Csv -LiteralPath (Join-Path $Directory 'sha256.csv') | Where-Object Stage -CEQ $Name)
    if ($rows.Count -ne 1 -or $rows[0].SHA256 -cne $hash) { throw 'STOP: stage manifest/hash mismatch.' }
    return $hash
}
function Read-SafeResult([object[]]$Lines, [string]$Stage, [int]$Parts) {
    $text = ($Lines | ForEach-Object { [string]$_ }) -join "`n"
    if ($text -match '(?im)^\s*(Msg \d+|Sqlcmd:|STOP:)' -or $text.Length -gt 16000000) { throw 'STOP: SQL output error or overflow; raw output suppressed.' }
    if ($Stage -ceq 'reconcile') {
        # SQLCMD streams may include CRLF, indentation or a leading UTF-8 BOM.
        # Parse complete bounded records, never arbitrary substrings or SQL text.
        $records=@()
        foreach ($line in ($text -split '\r?\n')) {
            $candidate=$line.Trim().TrimStart([char]0xFEFF).Trim()
            if ($candidate -cmatch '^ACCEPTANCE_SNAPSHOT:([A-Fa-f0-9]{64}):([0-9]{1,19}):([0-9]{1,19}):([0-9]{1,19})$') {
                $digest=$Matches[1].ToUpperInvariant(); $counts=@()
                foreach ($part in 2..4) {
                    $number=0L
                    if (-not [long]::TryParse($Matches[$part],[ref]$number)) { throw 'STOP: invalid reconciliation count.' }
                    $counts += $number.ToString([Globalization.CultureInfo]::InvariantCulture)
                }
                $records += 'ACCEPTANCE_SNAPSHOT:' + $digest + ':' + ($counts -join ':')
            } elseif ($candidate.Contains('ACCEPTANCE_SNAPSHOT:')) { throw 'STOP: malformed or truncated reconciliation record.' }
        }
        if ($records.Count -ne 1) { throw 'STOP: incomplete or duplicate reconciliation output.' }
        return $records[0]
    }
    $markers = @([regex]::Matches($text, '(?m)^ACCEPTANCE_SOURCE_OK:([^\r\n]+)\s*$') | ForEach-Object { $_.Groups[1].Value.Trim() })
    if ($markers.Count -ne $Parts) { throw 'STOP: incomplete source results.' }
    for ($i=1; $i -le $Parts; $i++) {
        if ($markers[$i-1] -cne "${Stage}:$i") { throw 'STOP: unexpected source result/order.' }
    }
    # SQLCMD supervised output uses pipe-delimited headers. Every finding column
    # must have exactly one numeric zero row; ordinary inventory counts are distinct.
    $rows = @($text -split '\r?\n')
    $findings = 0
    $findingNames = @()
    for ($i=0; $i -lt $rows.Count; $i++) {
        $columns = @($rows[$i].Split('|') | ForEach-Object Trim)
        $indices = @(); for ($j=0; $j -lt $columns.Count; $j++) { if ($columns[$j] -match 'FindingCount$') { $indices += $j } }
        if (-not $indices.Count) { continue }
        if ($i+2 -ge $rows.Count -or $rows[$i+1] -notmatch '^[-| ]+$') { throw 'STOP: ambiguous finding header.' }
        $values = @($rows[$i+2].Split('|') | ForEach-Object Trim)
        if ($values.Count -ne $columns.Count) { throw 'STOP: incomplete finding row.' }
        if ($i+3 -lt $rows.Count) {
            $nextValues = @($rows[$i+3].Split('|') | ForEach-Object Trim)
            if ($nextValues.Count -eq $columns.Count) {
                foreach ($j in $indices) { if ($nextValues[$j] -match '^(?:[+-]?[0-9]+|NULL)$') { throw 'STOP: multiple finding rows are ambiguous.' } }
            }
        }
        foreach ($j in $indices) { if ($values[$j] -cne '0') { throw 'STOP: nonzero or malformed finding count.' }; $findings++; $findingNames += $columns[$j] }
    }
    $expectedFindings = switch ($Stage) {
        { $_ -cin @('16-binding-preflight','17-binding-install','18-binding-postcheck','19-pr3-prepare') } { 'ConfinementCatalogDriftFindingCount' }
        '20-pr3' { 'ApplicationCatalogDriftFindingCount'; 'ConfinementCatalogDriftFindingCount'; 'RegistryConfinementFindingCount' }
        '30-owner-final' { 'ExternalDataSourceFindingCount'; 'ExternalTableFindingCount'; 'ScopedCredentialFindingCount'; 'SynonymFindingCount'; 'CrossDatabaseDependencyFindingCount'; 'OwnershipRouteFindingCount'; 'PublicAuthorityFindingCount'; 'RegistryConfinementFindingCount' }
        '31-verifier-catalogs' { 'ConfinementCatalogDriftFindingCount'; 'RegistryConfinementFindingCount' }
    }
    foreach ($expected in $expectedFindings) { if ($expected -cnotin $findingNames) { throw 'STOP: mandatory named finding result absent.' } }
    $required = switch ($Stage) {
        '15c-seed' {'SYNTHETIC_ROWS_ONLY_NO_AUTHORITY'}
        '16-binding-preflight' {'EMPTY_READY_FOR_SEPARATE_INSTALL_AUTHORIZATION|EXACT_EXISTING_BINDING_NO_CHANGE'}
        '17-binding-install' {'INSTALLED_ONE_BINDING|EXACT_EXISTING_BINDING_NO_CHANGE'}
        '18-binding-postcheck' {'BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED'}
        '30-owner-final' {'INSTALLED_OFF_PENDING_RESTRICTED_NATIVE_AND_APP_ACCEPTANCE'}
        default { if ($Stage -match '-identity-') { 'CONTAINED_PRINCIPAL_AND_TOKEN_CHECK_ONLY' } }
    }
    if ($required -and $text -notmatch "(?m)^($required)\s*$") { throw 'STOP: mandatory status absent.' }
    return "PASS:$Parts`:$findings"
}
