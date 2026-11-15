# Fast-track protocol/journal helpers. No connection on import.
Set-StrictMode -Version Latest
function Write-FastTrackFile([string]$Path,[string]$Text) {
    [void](Assert-PrivatePath $Path)
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try { $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text); $stream.Write($bytes); $stream.Flush($true) } finally { $stream.Dispose() }
}
function Read-FastTrackBatch([object[]]$Lines,[object[]]$Stages) {
    $text=($Lines | ForEach-Object {[string]$_}) -join "`n"
    $rows=@($text -split '\r?\n' | ForEach-Object { $_.Trim().TrimStart([char]0xFEFF).Trim() })
    $completed=@(); $active=$null; $segment=@(); $next=0; $invalid=$false
    foreach ($line in $rows) {
        if ($line.StartsWith('FAST_STAGE_BEGIN:')) {
            if ($active -or $next -ge $Stages.Count -or $line -cne ('FAST_STAGE_BEGIN:'+$Stages[$next].name)) { $invalid=$true; break }
            $active=$Stages[$next]; $segment=@(); continue
        }
        if ($line.StartsWith('FAST_STAGE_DONE:')) {
            if (-not $active -or $line -cne ('FAST_STAGE_DONE:'+$active.name)) { $invalid=$true; break }
            try { $summary=Read-SafeResult $segment $active.name @($active.sources).Count }
            catch { $invalid=$true; break }
            $completed += [pscustomobject]@{Stage=$active.name; Result=$summary}
            $next++; $active=$null; $segment=@(); continue
        }
        if ($active) { $segment += $line }
    }
    [pscustomobject]@{ Completed=$completed; Complete=($next -eq $Stages.Count -and -not $invalid -and -not $active); Invalid=$invalid }
}
function Get-FastTrackHistory([string]$Path,[object[]]$Stages,[string]$BaselineHash,[string]$Directory) {
    $next=18; $pending=$false; $groupEnd=18; $chain='0'*64; $index=0
    if (Test-Path -LiteralPath $Path) {
        if (-not [IO.File]::ReadAllText($Path).EndsWith("`n")) { throw 'STOP: fast-track journal truncated; preserve it for reconciliation.' }
        foreach ($line in [IO.File]::ReadAllLines($Path)) {
            try {
                $outer=$line | ConvertFrom-Json
                $hash=Get-TextSHA256 ($chain+[string]$outer.Payload)
                if ($hash -cne $outer.Hash) { throw 'Hash mismatch' }
                $row=$outer.Payload | ConvertFrom-Json
                if ($row.Index -ne $index) { throw 'Index mismatch' }
                if ($index -eq 0) {
                    if ($row.Kind -cne 'baseline' -or $row.Hash -cne $BaselineHash) { throw 'Baseline changed' }
                } else {
                    switch ($row.Kind) {
                        'pending' { if ($pending -or $row.Start -ne $next -or $row.End -le $next -or $row.End -gt 51) { throw 'Bad pending group' }; $pending=$true; $groupEnd=$row.End }
                        'complete' { if (-not $pending -or $next -ge $groupEnd -or $row.Stage -cne $Stages[$next].name -or $row.Hash -cne (Get-StageHash $Directory $row.Stage)) { throw 'Bad completion' }; $next++ }
                        'verified' { if (-not $pending -or $next -ne $groupEnd) { throw 'Unfinished group' }; $pending=$false }
                        'failed' { if (-not $pending) { throw 'Failure without execution' } }
                        default { throw 'Unknown record' }
                    }
                }
                $chain=$hash; $index++
            } catch { throw 'STOP: invalid fast-track journal; preserve it for independent reconciliation.' }
        }
    }
    [pscustomobject]@{ Next=$next; Pending=$pending; Chain=$chain; Index=$index }
}
