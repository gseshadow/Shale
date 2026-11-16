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
function Get-FastTrackHistory([string]$Path,[object[]]$Stages,[string]$BaselineHash,[string]$Directory,[int]$Depth=0) {
    if ($Depth -gt 4) { throw 'STOP: recovery ancestry too deep.' }
    $next=18; $pending=$false; $groupEnd=18; $chain='0'*64; $index=0; $groupStart=18; $failedStage=$null; $prefix=''; $recovered=$false; $checkpointSnapshot=$null
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
                    if ($row.Hash -cne $BaselineHash) { throw 'Baseline changed' }
                    if ($row.Kind -ceq 'stage21-baseline') {
                        Assert-Stage21RetryReceipt $row.Evidence $row.EvidenceHash $Directory $Stages $BaselineHash $Depth
                        $receipt=Read-Stage21JsonFile $row.Evidence
                        $plan=Read-Stage21JsonFile $receipt.PlanPath
                        $checkpointSnapshot=$plan.CheckpointSnapshot
                        $next=24; $recovered=$true
                    } elseif ($row.Kind -cne 'baseline') { throw 'Unknown baseline' }
                } else {
                    switch ($row.Kind) {
                        'pending' { if ($pending -or $row.Start -ne $next -or $row.End -le $next -or $row.End -gt 51) { throw 'Bad pending group' }; $pending=$true; $groupStart=$row.Start; $groupEnd=$row.End; $failedStage=$null }
                        'complete' { if (-not $pending -or $next -ge $groupEnd -or $row.Stage -cne $Stages[$next].name -or $row.Hash -cne (Get-StageHash $Directory $row.Stage)) { throw 'Bad completion' }; $next++ }
                        'verified' { if (-not $pending -or $next -ne $groupEnd) { throw 'Unfinished group' }; $pending=$false }
                        'failed' { if (-not $pending) { throw 'Failure without execution' }; $failedStage=$row.Stage }
                        'recovered24' {
                            if (-not $pending -or $next -ne 24 -or $groupStart -ne 19 -or $groupEnd -ne 30 -or $failedStage -cne '21-proof') { throw 'Unsupported recovery' }
                            $source=Assert-PrivatePath $row.SourceJournal
                            if ($source -cne (Join-Path ([IO.Path]::GetDirectoryName($Path)) 'fast-track.jsonl')) { throw 'Wrong source journal' }
                            if ([IO.Path]::GetFullPath($source) -ceq [IO.Path]::GetFullPath($Path) -or
                                (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -cne $row.SourceHash -or
                                [IO.File]::ReadAllText($source) -cne $prefix) { throw 'Recovery source changed' }
                            $receiptFile=Assert-PrivatePath $row.Receipt
                            if ((Get-FileHash -LiteralPath $receiptFile -Algorithm SHA256).Hash -cne $row.ReceiptHash) { throw 'Recovery evidence changed' }
                            $receipt=Get-Content -LiteralPath $receiptFile -Raw | ConvertFrom-Json
                            if ($receipt.Checkpoint -ne 24 -or $receipt.JournalHash -cne $row.SourceHash -or -not $receipt.VerifiedNativeExitZero -or -not $receipt.NoOtherSessionsOrWriters -or [string]::IsNullOrWhiteSpace($receipt.ReviewReference)) { throw 'Incomplete review' }
                            $planFile=Assert-PrivatePath $receipt.PlanPath
                            if ((Get-FileHash -LiteralPath $planFile -Algorithm SHA256).Hash -cne $receipt.PlanHash) { throw 'Recovery plan changed' }
                            $plan=Get-Content -LiteralPath $planFile -Raw | ConvertFrom-Json
                            if ($plan.StageHashes.Count -ne 30) { throw 'Incomplete stage evidence' }
                            foreach ($i in 0..29) {
                                if ($plan.StageHashes[$i].Stage -cne $Stages[$i].name -or $plan.StageHashes[$i].Hash -cne (Get-StageHash $Directory $Stages[$i].name)) { throw 'Recovery stage bytes changed' }
                            }
                            foreach ($capture in $receipt.Captures) {
                                $capturePath=Assert-PrivatePath $capture.Path
                                if ((Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash -cne $capture.Hash) { throw 'Recovery capture changed' }
                            }
                            if (@($receipt.Captures).Count -ne 2) { throw 'Incomplete captures' }
                            $pending=$false; $recovered=$true; $checkpointSnapshot=$receipt.Snapshot
                        }
                        default { throw 'Unknown record' }
                    }
                }
                $chain=$hash; $index++; $prefix+=$line+"`n"
            } catch { throw 'STOP: invalid fast-track journal; preserve it for independent reconciliation.' }
        }
    }
    [pscustomobject]@{ Next=$next; Pending=$pending; Recovered=$recovered; Checkpoint24Snapshot=$checkpointSnapshot; GroupStart=$groupStart; GroupEnd=$groupEnd; FailedStage=$failedStage; Chain=$chain; Index=$index }
}

# Exact public assertion text only. Never echo a SQL message, header suffix,
# procedure name, line content or unknown string from the child process.
function Get-SafeFastTrackAssertions([object[]]$Lines) {
    $allowed=@{
        'Fresh unscoped dbo lease required.'='PROOF_FRESH_UNSCOPED_DBO'
        'Exclusive schema lock and capability OFF required.'='PROOF_SCHEMA_LOCK_OR_CAPABILITY'
        'CIMD proof objects already present; never overwrite.'='PROOF_ALREADY_PRESENT'
        'CIMD proof module source/owner/context drift.'='PROOF_MODULE_DRIFT'
        'CIMD object ownership drift.'='PROOF_OWNERSHIP_DRIFT'
        'Unreviewed CIMD trigger/table-expression edge.'='PROOF_EXPRESSION_DRIFT'
        'CIMD strict RLS drift.'='PROOF_RLS_DRIFT'
        'CIMD exact role permission drift.'='PROOF_PERMISSION_DRIFT'
        'CIMD proof catalog/source or predecessor drift; never refresh receipts.'='PROOF_CATALOG_OR_PREDECESSOR_DRIFT'
        'CIMD proof role owner/inheritance drift.'='PROOF_ROLE_DRIFT'
        'CIMD proof member authority drift.'='PROOF_MEMBER_AUTHORITY_DRIFT'
        'CIMD proof signature drift.'='PROOF_SIGNATURE_DRIFT'
        'CIMD proof securable ownership drift.'='PROOF_SECURABLE_OWNER_DRIFT'
        'Unknown CIMD SQL platform.'='PROOF_SQL_PLATFORM_UNKNOWN'
        'CIMD SQL authentication type drift.'='PROOF_AUTHENTICATION_TYPE_DRIFT'
        'STOP: fresh neutral autocommit connection required.'='PACKET_SESSION_NOT_NEUTRAL'
        'Fresh unscoped dbo installation required.'='OAUTH_FRESH_UNSCOPED_DBO'
        'Fresh unscoped owner installation required.'='REVIEWER_FRESH_UNSCOPED_DBO'
        'Fresh unscoped owner required.'='INSTALL_FRESH_UNSCOPED_DBO'
        'Fresh neutral owner required.'='DCR_FRESH_UNSCOPED_DBO'
    }
    $found=@()
    foreach ($line in ((($Lines | ForEach-Object {[string]$_}) -join "`n") -split '\r?\n')) {
        $candidate=$line.Trim()
        if ($candidate -cin @($allowed.Keys)) { $found+=$allowed[$candidate] }
    }
    if (-not $found.Count) { return 'UNCLASSIFIED_SQL_ASSERTION' }
    return @($found | Sort-Object -Unique)
}

# Boolean states only: no tenant/principal values, identity names or SQL text.
function Get-SafeFastTrackSessionFailures([object[]]$Lines,[string]$Stage) {
    $pattern='^FAST_SESSION_STATE:'+ [regex]::Escape($Stage)+':([01]):([01]):([01]):([01]):([01])$'
    foreach ($line in ((($Lines | ForEach-Object {[string]$_}) -join "`n") -split '\r?\n')) {
        if ($line.Trim() -cmatch $pattern) {
            if ($Matches[1] -ceq '0') { 'USER_NOT_DBO' }
            if ($Matches[2] -ceq '1') { 'OPEN_TRANSACTION' }
            if ($Matches[3] -ceq '1') { 'TENANT_CONTEXT_SET' }
            if ($Matches[4] -ceq '1') { 'PRINCIPAL_CONTEXT_SET' }
            if ($Matches[5] -ceq '1') { 'IMPLICIT_TRANSACTIONS_ON' }
        }
    }
}

# Names and flags only. Unknown, duplicate, all-zero or malformed records fail
# closed as diagnostic evidence; raw lines never enter reports or exceptions.
function Read-ProofModuleFailures([object[]]$Lines) {
    $names=@([IO.File]::ReadAllLines((Join-Path $PSScriptRoot '../sql-confinement/cimd-v1-modules.sha256')) | ForEach-Object { $_.Split('=')[0] })
    $flags=@('OBJECT_MISSING','DEFINITION_UNAVAILABLE','OWNERSHIP','ANSI_NULLS','QUOTED_IDENTIFIER','EXECUTION_CONTEXT','DEFINITION_HASH')
    $found=@(); $checks=@(); $catches=@(); $absent=@(); $unavailable=@(); $seen=@(); $invalid=$false; $legacy=$false; $rollback=0
    foreach ($line in ((($Lines | ForEach-Object {[string]$_}) -join "`n") -split '\r?\n')) {
        $candidate=$line.Trim()
        if ($candidate -ceq 'PROOF_ROLLBACK_CONFIRMED') { $rollback++; continue }
        if ($candidate -cmatch '^PROOF_(CHECK|CATCH):(INSTALL|SEAL|STANDALONE):(-1|0|1):([0-9]{1,5}):([01])$') {
            $kind=$Matches[1]; $phase=$Matches[2]; $state=[int]$Matches[3]; $count=[int]$Matches[4]; $abort=($Matches[5] -ceq '1')
            if ($count -gt 32767 -or (($state -eq 0) -ne ($count -eq 0))) { $invalid=$true; continue }
            $record=[pscustomobject]@{Phase=$phase; XactState=$state; TranCount=$count; XactAbortOn=$abort}
            if ($kind -ceq 'CHECK') { $checks+=$record } else { $catches+=$record }
            continue
        }
        if ($candidate -cmatch '^PROOF_(METADATA_ABSENT|DIAGNOSTIC_UNAVAILABLE):(INSTALL|SEAL|STANDALONE)$') {
            if ($Matches[1] -ceq 'METADATA_ABSENT') { $absent+=$Matches[2] } else { $unavailable+=$Matches[2] }; continue
        }
        if ($candidate -cmatch '^PROOF_MODULE_FAIL:(INSTALL|SEAL|STANDALONE):([A-Za-z0-9_]{1,128}):([01]{7})$') {
            $phase=$Matches[1]; $name=$Matches[2]; $mask=$Matches[3]
            if ($name -cnotin $names -or ($phase+':'+$name) -cin $seen -or $mask -ceq '0000000') { $invalid=$true; continue }
            $seen+=($phase+':'+$name); $failed=@()
            foreach ($i in 0..6) { if ($mask[$i] -ceq [char]'1') { $failed+=$flags[$i] } }
            $found+=[pscustomobject]@{Phase=$phase;Module=$name;FailedProperties=$failed}; continue
        }
        if ($candidate.StartsWith('PROOF_MODULE_FAIL:')) { $legacy=$true; $invalid=$true }
        elseif ($candidate.StartsWith('PROOF_CHECK:') -or $candidate.StartsWith('PROOF_CATCH:') -or $candidate.StartsWith('PROOF_METADATA_ABSENT:') -or $candidate.StartsWith('PROOF_DIAGNOSTIC_UNAVAILABLE:')) { $invalid=$true }
    }
    if ($rollback -gt 1 -or $checks.Count -gt 1 -or $catches.Count -gt 1) { $invalid=$true }
    if ($catches.Count -and ($catches[0].Phase -ceq 'STANDALONE' -or ($checks.Count -and $checks[0].Phase -cne $catches[0].Phase))) { $invalid=$true }
    if ($rollback -and $catches.Count -ne 1) { $invalid=$true }
    foreach ($phase in @($absent)+@($unavailable)) { if ($checks.Count -ne 1 -or $phase -cne $checks[0].Phase) { $invalid=$true } }
    if ($absent.Count -and $found.Count) { $invalid=$true }
    foreach ($module in $found) { if (@($checks | Where-Object Phase -CEQ $module.Phase).Count -ne 1) { $invalid=$true } }
    if ($invalid -or $unavailable.Count -or $absent.Count) { $found=@() }
    $observation=if ($legacy) {'UNSCOPED_LEGACY_OUTPUT'} elseif ($invalid) {'INVALID_DIAGNOSTIC_PROTOCOL'} elseif ($absent.Count) {'NO_MODULE_METADATA_AT_ASSERTION'} elseif ($unavailable.Count -or -not $found.Count) {'DIAGNOSTICS_UNAVAILABLE'} else {'PRE_THROW_MODULE_MISMATCH'}
    [pscustomobject]@{Modules=@($found);Checks=@($checks);CatchStates=@($catches);Observation=$observation;Valid=(-not $invalid -and $found.Count -gt 0 -and $absent.Count -eq 0 -and $unavailable.Count -eq 0);RollbackConfirmed=($rollback -eq 1 -and -not $invalid)}
}
function Read-ProofStageSnapshot([string]$Path,[string]$ExpectedHash) {
    $algorithm=$null
    try {
        $bytes=[IO.File]::ReadAllBytes((Assert-PrivatePath $Path))
        $algorithm=[Security.Cryptography.SHA256]::Create()
        $actual=[BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-','')
        if ($actual -cne $ExpectedHash) { throw 'Changed bytes' }
        return [Text.UTF8Encoding]::new($false,$true).GetString($bytes)
    } catch { throw 'STOP: Stage 21 input changed or is not valid UTF-8; contents suppressed.' }
    finally { if ($algorithm) { $algorithm.Dispose() } }
}
function Split-ProofStageSources([string]$Text,[string]$Fence) {
    $boundary="SELECT 'ACCEPTANCE_SOURCE_OK:21-proof:1' AS AcceptanceResult;`nGO`n"
    $at=$Text.IndexOf($boundary,[StringComparison]::Ordinal)
    if ($at -lt 0 -or $at -ne $Text.LastIndexOf($boundary,[StringComparison]::Ordinal)) { throw 'STOP: Stage 21 source boundary changed.' }
    $cut=$at+$boundary.Length
    [pscustomobject]@{Install=$Text.Substring(0,$cut); Verify=(":on error exit`n"+$Fence+"SET XACT_ABORT ON;`nGO`n"+$Text.Substring($cut))}
}

. (Join-Path $PSScriptRoot 'Stage21RetryEvidence.ps1')
