#Requires -Version 7.0
<# Foreground operator supervision only. No Azure management or application operations. #>
[CmdletBinding()]
param([string]$RenderedDirectory, [string]$AuthoritativeFixture, [string]$OperatorUser, [ValidateSet('None','VerifiedCommitted','VerifiedRolledBack')][string]$Recovery = 'None')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Supervision.ps1')
function Approve([string]$Prompt, [string]$Exact) {
    if ((Read-Host "$Prompt; type $Exact (anything else stops)") -cne $Exact) { throw 'STOP: approval refused; no transition.' }
}
if (-not $RenderedDirectory) { $RenderedDirectory = Read-Host 'Exact reviewed rendered directory' }
if (-not $AuthoritativeFixture) { $AuthoritativeFixture = Read-Host 'Unchanged authoritative fixture path' }
if (-not $OperatorUser) { $OperatorUser = Read-Host 'Existing acceptance-only SQL owner username' }
$dir = Assert-PrivatePath $RenderedDirectory
$stages = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
$ledgerDirectory = Assert-PrivatePath ([IO.Path]::GetDirectoryName($dir))
$ledgerPath = Assert-PrivatePath (Join-Path $ledgerDirectory 'supervised.jsonl')
$lockPath = Assert-PrivatePath (Join-Path $ledgerDirectory 'supervised.lock')
# Exclusive session lock, released by process termination too. Never overwrite evidence.
$lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    if (Test-Path -LiteralPath (Join-Path $ledgerDirectory 'fast-track.jsonl')) { throw 'STOP: fast-track history exists; review its report before changing modes. No stage will replay.' }
    $script:previous = '0' * 64
    $events = @()
    if (Test-Path -LiteralPath $ledgerPath) {
        if (-not [IO.File]::ReadAllText($ledgerPath).EndsWith("`n")) { throw 'STOP: ledger empty/truncated or missing final newline. Preserve evidence; do not execute.' }
        foreach ($line in [IO.File]::ReadAllLines($ledgerPath)) {
            try { $event = $line | ConvertFrom-Json; $payload = [string]$event.Payload
                $digest = (Get-TextSHA256 ($script:previous + $payload))
                if ($event.Hash -cne $digest) { throw 'invalid chain' }
                $record = $payload | ConvertFrom-Json
                if ($record.Index -ne $events.Count -or $record.Kind -cnotin @('baseline','pending','verified','rolledback','failed')) { throw 'invalid sequence' }
                $events += $record; $script:previous = $digest
            } catch { throw 'STOP: ledger corrupt/truncated. Preserve evidence; do not execute.' }
        }
    }
    function Append-Event([string]$Kind, [string]$Stage, [string]$Hash, [string]$Snapshot, [string]$Result = '', $ExitCode = $null, [int[]]$SqlErrorNumbers = @(), [string]$FailureCode = '', $VerificationExitCode = $null) {
        $record = [ordered]@{Index=$script:eventCount; Kind=$Kind; Stage=$Stage; SHA256=$Hash; Snapshot=$Snapshot; Result=$Result; ExitCode=$ExitCode; SqlErrorNumbers=$SqlErrorNumbers; FailureCode=$FailureCode; VerificationExitCode=$VerificationExitCode; Utc=[DateTime]::UtcNow.ToString('o')}
        $payload = $record | ConvertTo-Json -Compress
        $digest = (Get-TextSHA256 ($script:previous + $payload))
        $line = ([ordered]@{Payload=$payload; Hash=$digest} | ConvertTo-Json -Compress) + "`n"
        $stream = [IO.File]::Open($ledgerPath, [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        try { $bytes=[Text.Encoding]::UTF8.GetBytes($line); $stream.Write($bytes); $stream.Flush($true) } finally { $stream.Dispose() }
        $script:previous=$digest; $script:eventCount++
    }
    function Get-BaselineHash {
        $parts = @($stages[0..17] | ForEach-Object { $_.name + ':' + (Get-StageHash $dir $_.name) })
        return (Get-TextSHA256 ($parts -join "`n"))
    }
    $script:eventCount=$events.Count
    $next=18
    $pending=$null
    if ($events.Count) {
        if ($events[0].Kind -cne 'baseline' -or $events[0].Stage -cne '15b-foundation-users' -or $events[0].SHA256 -cne (Get-BaselineHash)) { throw 'STOP: invalid baseline.' }
        foreach ($event in $events | Select-Object -Skip 1) {
            if ($next -ge 51 -or $event.Stage -cne $stages[$next].name -or $event.SHA256 -cnotmatch '^[A-F0-9]{64}$') { throw 'STOP: ledger stage order/hash invalid.' }
            switch ($event.Kind) {
                'pending' { if ($pending) { throw 'STOP: duplicate dispatch.' }; $pending=$event }
                'failed' { if (-not $pending -or $event.SHA256 -cne $pending.SHA256) { throw 'STOP: failure without matching dispatch.' } }
                { $_ -cin @('verified','rolledback') } {
                    if (-not $pending -or $event.SHA256 -cne $pending.SHA256) { throw 'STOP: outcome without matching dispatch.' }
                    if ($event.Kind -ceq 'verified') {
                        if ($event.SHA256 -cne (Get-StageHash $dir $event.Stage)) { throw 'STOP: completed stage bytes changed.' }
                        $next++
                    }
                    $pending=$null
                }
                default { throw 'STOP: unexpected ledger event.' }
            }
        }
    }
    $readyStage = if ($next -lt 51) { $stages[$next].name } else { '49-identity-dcr_metadata' }
    & (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $dir -AuthoritativeFixture $AuthoritativeFixture -Stage $readyStage
    # Explicit ODBC provenance approval; never invoke an arbitrary PATH replacement silently.
    $client = Get-Command sqlcmd -CommandType Application -ErrorAction Stop
    Write-Output "SQL client: $($client.Source) SHA256=$((Get-FileHash -LiteralPath $client.Source -Algorithm SHA256).Hash)"
    Approve 'Confirm independently reviewed Microsoft ODBC SQLCMD, private terminal, no transcript, acceptance app stopped and all MCP/AI controls OFF' 'ODBC_OFF_ONLY'
    $script:reconcileApproved = $null
    $clientHash = (Get-FileHash -LiteralPath $client.Source -Algorithm SHA256).Hash
    function Assert-Client {
        $now = Get-Command sqlcmd -CommandType Application -ErrorAction Stop
        if ($now.Source -cne $client.Source -or (Get-FileHash -LiteralPath $now.Source -Algorithm SHA256).Hash -cne $clientHash) { throw 'STOP: approved SQLCMD client changed.' }
    }
    function Reconcile([string]$AfterStage = '', [string]$AfterHash = '', [string]$AfterResult = '', $AfterExitCode = $null) {
        $hash = Get-StageHash $dir 'reconcile'
        Write-Host "READ-ONLY destination shale.database.windows.net / ShaleMcpAcceptance; reconcile SHA256=$hash"
        Assert-Client
        if (-not $script:reconcileApproved) {
            $script:reconcileApproved = Read-Host 'Independently approved reconciliation SHA256'
            if ($script:reconcileApproved -ine $hash) { throw 'STOP: reconciliation hash approval mismatch.' }
            Approve 'Authorize fresh read-only verification connections with this exact hash for this foreground session under the existing acceptance owner' 'VERIFY_ACCEPTANCE_SESSION'
        }
        if ($script:reconcileApproved -ine $hash) { throw 'STOP: approved reconciliation bytes changed.' }
        $approved = $script:reconcileApproved
        $result = & (Join-Path $PSScriptRoot 'Invoke-Stage.ps1') -RenderedDirectory $dir -Stage '15c-seed' -OperatorUser $OperatorUser -ReadOnlyCheck -Supervised -Execute -ApprovedSHA256 $approved -Acknowledgement EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE
        if ($result.ExitCode -ne 0 -or $result.Result -notmatch '^ACCEPTANCE_SNAPSHOT:') {
            if ($AfterStage) {
                # Execution exit and verification exit are different facts. A
                # failed postcheck never converts a successful SQLCMD exit into
                # proof of a committed stage or authorizes replay.
                Append-Event 'failed' $AfterStage $AfterHash '' $AfterResult $AfterExitCode $result.SqlErrorNumbers ('POSTCHECK_' + $result.FailureCode) $result.ExitCode
            }
            Write-SafeStageDiagnostic $result
            throw 'STOP: read-only reconciliation failed or output incomplete.'
        }
        return $result.Result
    }
    $snapshot = Reconcile
    if ($pending) {
        if ($pending.Kind -cne 'pending' -or $pending.Stage -cne $stages[$next].name -or $pending.SHA256 -cne (Get-StageHash $dir $pending.Stage)) { throw 'STOP: invalid pending stage.' }
        if ($Recovery -ceq 'None') { throw 'STOP: interrupted/failed stage. Live read-only state collected; independent reconciliation required. No automatic retry.' }
        # Human review is necessary for uncertain commit. The runner cannot infer
        # transactional outcome from schema hashes or SQLCMD exit alone.
        Approve 'Confirm separate independent read-only verification of ALL stage effects, receipts, controls, identity/permissions and transaction outcome; no recovery mutation was performed' "INDEPENDENT_$Recovery"
        if ($Recovery -ceq 'VerifiedRolledBack' -and $snapshot -cne $pending.Snapshot) { throw 'STOP: rollback state differs from pre-execution snapshot.' }
        $expected = Read-Host 'Exact independently verified ACCEPTANCE_SNAPSHOT record'
        if ($expected -cne $snapshot) { throw 'STOP: independent live verification mismatch.' }
        if ($pending.Stage -ceq '15c-seed') {
            $suffix = if ($Recovery -ceq 'VerifiedCommitted') { ':2:4:3$' } else { ':0:0:0$' }
            if ($snapshot -notmatch $suffix) { throw 'STOP: recovered seed counts inconsistent with disposition.' }
        }
        $kind = if ($Recovery -ceq 'VerifiedCommitted') { 'verified' } else { 'rolledback' }
        Append-Event $kind $pending.Stage $pending.SHA256 $snapshot
        Write-Output 'Recovery recorded; session stopped. Relaunch for fresh readiness, live reconciliation and separate stage approval. No stage executed.'
        return
    }
    if ($Recovery -cne 'None') { throw 'STOP: no interrupted stage to reconcile.' }
    if ($events.Count) {
        if ($snapshot -cne $events[-1].Snapshot) { throw 'STOP: live checkpoint differs from verified ledger; no replay/adoption.' }
    } else {
        if ($snapshot -notmatch ':0:0:0$') { throw 'STOP: checkpoint requires independently confirmed empty tenant/user/Case tables.' }
        Approve 'Confirm retained independent evidence for all 18 completed stages through 15b and the failed 15c rollback; live counts are zero' 'IMPORT_18_VERIFIED_STAGES'
        Append-Event 'baseline' '15b-foundation-users' (Get-BaselineHash) $snapshot 'IMPORTED_18_VERIFIED_STAGE_HASHES'
    }
    while ($next -lt 51) {
        $stage = $stages[$next]
        & (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $dir -AuthoritativeFixture $AuthoritativeFixture -Stage $stage.name
        if ($stage.name -ceq '16-binding-preflight') {
            Approve 'HARD GATE: independent actual test-worker JVM/zone evidence, approved SQL mapping and all 28 samples reviewed; this installer cannot obtain them or start/deploy the app' 'WORKER_JVM_EVIDENCE_REVIEWED'
        }
        $user = $OperatorUser
        if ($stage.PSObject.Properties.Name -contains 'user') { $user = $stage.user }
        & (Join-Path $PSScriptRoot 'Invoke-Stage.ps1') -RenderedDirectory $dir -Stage $stage.name -OperatorUser $user
        Write-Output "Progress $next/51; next=$($stage.name); login=$user. Review exact private SQL locally; no SQL is printed."
        $hash = Get-StageHash $dir $stage.name
        $approved = Read-Host 'Independently approved SHA256 for this exact stage'
        if ($approved -ine $hash) { throw 'STOP: independent hash approval mismatch.' }
        Approve "Authorize only $($stage.name), fixed acceptance target and relevant prerequisites; SQLCMD will prompt for this principal password" "EXECUTE_$($stage.name)"
        # Check live state immediately before every mutation/identity boundary.
        $before = Reconcile
        if ($before -cne $snapshot) { throw 'STOP: state changed since last verified checkpoint.' }
        Assert-Client
        Append-Event 'pending' $stage.name $hash $snapshot
        $result = & (Join-Path $PSScriptRoot 'Invoke-Stage.ps1') -RenderedDirectory $dir -Stage $stage.name -OperatorUser $user -Supervised -Execute -ApprovedSHA256 $approved -Acknowledgement EXECUTE_APPROVED_SHALE_MCP_ACCEPTANCE_STAGE
        if ($result.ExitCode -ne 0 -or $result.Result -notmatch '^PASS:') {
            Append-Event 'failed' $stage.name $hash $snapshot 'FAILED_RESULT_OR_PROCESS' $result.ExitCode $result.SqlErrorNumbers $result.FailureCode
            Write-SafeStageDiagnostic $result
            throw 'STOP: stage failed or results incomplete; raw SQLCMD output suppressed. Independent recovery required.'
        }
        $snapshot = Reconcile -AfterStage $stage.name -AfterHash $hash -AfterResult $result.Result -AfterExitCode $result.ExitCode
        if ($stage.name -ceq '15c-seed' -and $snapshot -notmatch ':2:4:3$') { throw 'STOP: seed outcome mismatch; reconcile independently.' }
        Append-Event 'verified' $stage.name $hash $snapshot $result.Result $result.ExitCode
        $next++
        Write-Output "Verified $next/51 ($($stage.name)); $($result.Result)."
        if ($next -lt 51) { Approve 'Continue supervision to preview the next stage; its separate hash and execution approvals are still required' 'CONTINUE' }
    }
    Write-Output '51/51 SQL preparation stages verified. Native/runtime acceptance remains OPEN; application stays OFF.'
} catch { Write-Error ('Supervision stopped. ' + $_.Exception.Message); exit 1 }
finally { $lock.Dispose() }
