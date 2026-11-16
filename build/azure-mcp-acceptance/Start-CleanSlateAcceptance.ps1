#Requires -Version 7.4
<# Operator-only. No SQLCMD, production connection, journal recovery, activation or app startup. #>
[CmdletBinding()]
param([string]$ExistingPacket,[string]$AuthoritativeFixture)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'CleanSlate.ps1')
. (Join-Path $PSScriptRoot 'FastTrack.ps1')
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$run=$null; $lock=$null; $oldLock=$null; $completed=0; $stageName='preflight'; $approved=$false; $operation='tools'
try {
    foreach ($tool in @('az','python','javac','java')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "STOP: install local prerequisite $tool." } }
    if (-not $ExistingPacket) { $ExistingPacket=Read-Host 'Existing private packet directory (inputs are copied; evidence is preserved)' }
    if (-not $AuthoritativeFixture) { $AuthoritativeFixture=Read-Host 'Unchanged authoritative fixture path' }
    $operation='private-inputs'
    $source=Assert-PrivatePath $ExistingPacket
    if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'STOP: existing private packet required.' }
    if ((Get-FileHash -LiteralPath $AuthoritativeFixture -Algorithm SHA256).Hash -cne '4B64B50DA531EBC6A2EB9DC26C6555EF555621DF371FE9D8A5CF994FB6E8417F') { throw 'STOP: authoritative fixture fingerprint differs.' }
    $lock=[IO.File]::Open((Join-Path $repo 'work/clean-slate-acceptance.lock'),'OpenOrCreate','ReadWrite','None')
    $oldLockPath=Join-Path $source 'supervised.lock'
    if (Test-Path -LiteralPath $oldLockPath) { $oldLock=[IO.File]::Open($oldLockPath,'Open','Read','None') }
    $inventory=@(Get-ChildItem -LiteralPath $source -Recurse -File | Where-Object FullName -CNE $oldLockPath | ForEach-Object {
        @{Path=$_.FullName;SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
    $run=Join-Path $repo ('work/clean-slate-'+[guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($run)
    if ($IsWindows) {
        $acl=[Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true,$false)
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl.SetOwner($sid)
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        Set-Acl -LiteralPath $run -AclObject $acl
    } else { [IO.File]::SetUnixFileMode($run,[IO.UnixFileMode]448) }
    $journal=Join-Path $run 'journal.jsonl'
    [IO.File]::WriteAllText((Join-Path $run 'historical-evidence.json'),($inventory | ConvertTo-Json -Depth 4))
    foreach ($file in @('fixture.sql','normalize.sql','identities-foundation.sql','identities.sql','seed.sql','binding-inputs.sql','phase1r.json')) {
        [void](Assert-PrivatePath (Join-Path $source $file))
        Copy-Item -LiteralPath (Join-Path $source $file) -Destination (Join-Path $run $file)
    }
    $credentials=Get-CleanIdentityCredentials $run
    $operation='owner-credential'
    $owner=Get-Credential -Message 'Existing database owner for ShaleMcpAcceptance ONLY; held in memory for this run'
    if ($null -eq $owner -or $owner.UserName -cin $credentials.Keys) { throw 'STOP: independent owner credential required.' }
    # No password switches, SQLCMD environment variables or inherited transcripts.
    $operation='offline-readiness'
    $expectedPath=Join-Path $run 'phase1r-expected.json'
    $offline=@(& python (Join-Path $PSScriptRoot 'phase1r_evidence.py') expected --fixture $AuthoritativeFixture --out $expectedPath 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'STOP: authoritative baseline compilation failed; no SQL execution.' }
    $normalizePath=Join-Path $run 'normalize.sql'
    $normalize=[IO.File]::ReadAllText($normalizePath)
    if ($normalize -notmatch '@NormalizeApproved\s+bit\s*=\s*[01]') { throw 'STOP: normalization template differs.' }
    # No execution yet; normalization still additionally requires a passing fresh baseline.
    $normalize=[regex]::Replace($normalize,'(@NormalizeApproved\s+bit\s*=\s*)[01]','${1}1')
    [IO.File]::WriteAllText($normalizePath,$normalize)
    $prepared=@(& (Join-Path $PSScriptRoot 'Prepare-Packet.ps1') -PacketDirectory $run -AuthoritativeFixture $AuthoritativeFixture -Continuation -CleanSlate)
    $rendered=@(Get-ChildItem -LiteralPath $run -Directory -Filter 'rendered-*')
    if ($rendered.Count -ne 1) { throw 'STOP: ambiguous new render.' }; $rendered=$rendered[0].FullName
    [void](& (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $rendered -AuthoritativeFixture $AuthoritativeFixture -SqlClient)
    # Validate early private approvals as well; legacy All checks only later stages.
    foreach ($name in @('09-normalize','15b-foundation-users')) {
        [void](& (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $rendered -AuthoritativeFixture $AuthoritativeFixture -SqlClient -Stage $name)
    }
    $stages=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json)
    # Freeze source bytes before approval. Fresh phase1r values are the sole later input change.
    $snapshots=@{}; $hashes=@{}
    foreach ($spec in $stages) {
        $hashes[$spec.name]=Get-StageHash $rendered $spec.name
        $snapshots[$spec.name]=[IO.File]::ReadAllText((Join-Path $rendered ($spec.name+'.sql')))
        [void](Get-CleanSourceParts $snapshots[$spec.name] $spec)
        [void][CleanSql]::Batches($snapshots[$spec.name])
    }
    $reset=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'CleanSlateReset.sql'))
    $operation='azure-identity-and-stopped-app'
    Assert-CleanResources
    $target=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/target.sql'))
    $ownerGuard=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/owner.sql'))
    # Read-only connection preflight; no destructive SQL before explicit approval.
    $operation='sql-owner-target-and-off-controls'
    [void](Invoke-CleanSource ($target+"`n"+$ownerGuard) $owner)
    Write-Host 'Target: shale.database.windows.net / ShaleMcpAcceptance ONLY. Acceptance app stays stopped; MCP/AI stay OFF.'
    Write-Host 'Approval authorizes in-place deletion of ALL acceptance schema/data/users and the full 51-stage rebuild, including baseline-verified normalization. Historical evidence is retained.'
    Write-Host "New private run: $run"
    if ((Read-Host 'Type RESET_AND_REBUILD_SHALE_MCP_ACCEPTANCE once to execute') -cne 'RESET_AND_REBUILD_SHALE_MCP_ACCEPTANCE') { throw 'STOP: destructive approval not given.' }
    $approved=$true; $operation='execution'
    New-CleanEvent $journal @{Kind='approved';Target='shale.database.windows.net/ShaleMcpAcceptance';Utc=[datetime]::UtcNow.ToString('o');StageManifest=(Get-FileHash (Join-Path $PSScriptRoot 'stages.json')).Hash}
    Assert-CleanResources -StopApp
    $stageName='reset'
    foreach ($item in $inventory) { if ((Get-FileHash -LiteralPath $item.Path).Hash -cne $item.SHA256) { throw 'STOP: historical evidence changed before reset.' } }
    New-CleanEvent $journal @{Kind='reset-pending';SHA256=(Get-TextSHA256 $reset)}
    $output=Invoke-CleanSource ($target+"`n"+$ownerGuard+"`nGO`n"+$reset) $owner
    if (@($output | Where-Object {$_ -ceq 'CLEAN_SLATE_RESET_COMMITTED'}).Count -ne 1) { throw 'STOP: reset completion unconfirmed; no stage dispatched.' }
    New-CleanEvent $journal @{Kind='reset-complete'}
    foreach ($stage in $stages) {
        $stageName=$stage.name
        Assert-CleanResources
        if ($completed -eq 9) {
            # Only catalog evidence, privately retained. Expected source bytes are authoritative.
            $captureSql=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Collect-Phase1R.sql')).Replace(':r docs/sql/ai-integration/azure-acceptance/target.sql',$target)
            $capture=Invoke-CleanSource $captureSql $owner
            $hex=@($capture | Where-Object {$_ -cmatch '^[A-F0-9]{2,2048}$'})
            if (-not $hex.Count) { throw 'STOP: fresh baseline evidence absent.' }
            $capturePath=Join-Path $run 'phase1r-capture.hex'
            [IO.File]::WriteAllText($capturePath,($hex -join "`n"))
            $comparison=Join-Path $run 'phase1r-comparison.json'
            $offline=@(& python (Join-Path $PSScriptRoot 'phase1r_evidence.py') compare --expected $expectedPath --capture $capturePath --report $comparison 2>&1)
            if ($LASTEXITCODE -ne 0) { throw 'STOP: fresh baseline comparison failed; no normalization.' }
            Set-CleanPhase1R $comparison (Join-Path $run 'phase1r.json')
            $prepared=@(& (Join-Path $PSScriptRoot 'Prepare-Packet.ps1') -PacketDirectory $run -AuthoritativeFixture $AuthoritativeFixture -Continuation -CleanSlate)
            $directories=@(Get-ChildItem -LiteralPath $run -Directory -Filter 'rendered-*' | Where-Object FullName -CNE $rendered)
            if ($directories.Count -ne 1) { throw 'STOP: ambiguous fresh baseline render.' }
            $fresh=$directories[0].FullName
            [void](& (Join-Path $PSScriptRoot 'Test-Readiness.ps1') -RenderedDirectory $fresh -AuthoritativeFixture $AuthoritativeFixture -SqlClient)
            foreach ($spec in $stages) {
                $nextHash=Get-StageHash $fresh $spec.name
                if ($spec.name -cnotin @('10-phase1r-preflight','11-phase1r-apply','12-phase1r-postcheck') -and $nextHash -cne $hashes[$spec.name]) { throw 'STOP: unexpected source change during fresh baseline preparation.' }
                $hashes[$spec.name]=$nextHash
                $snapshots[$spec.name]=[IO.File]::ReadAllText((Join-Path $fresh ($spec.name+'.sql')))
            }
            New-CleanEvent $journal @{Kind='baseline-verified';Expected=(Get-FileHash $expectedPath).Hash;Capture=(Get-FileHash $capturePath).Hash;Comparison=(Get-FileHash $comparison).Hash}
        }
        New-CleanEvent $journal @{Kind='stage-pending';Stage=$stageName;SHA256=$hashes[$stageName];Completed=$completed}
        $credential=$owner
        if ($stage.PSObject.Properties.Name -contains 'user') { $credential=$credentials[$stage.user] }
        # Independent Stage21 verifier must succeed after the installer connection closes.
        $result=Invoke-CleanStage $stage $snapshots[$stageName] $credential
        $completed++
        New-CleanEvent $journal @{Kind='stage-complete';Stage=$stageName;Completed=$completed;Result=$result;SHA256=$hashes[$stageName]}
        [IO.File]::WriteAllText((Join-Path $run 'report.json'),(@{Status='running';Completed=$completed;Total=51;LastCompleted=$stageName} | ConvertTo-Json))
        Write-Host "Completed $completed/51: $stageName"
    }
    Assert-CleanResources
    foreach ($item in $inventory) { if ((Get-FileHash -LiteralPath $item.Path).Hash -cne $item.SHA256) { throw 'STOP: historical evidence changed externally.' } }
    New-CleanEvent $journal @{Kind='complete';Completed=51;Application='Stopped';Activation='OFF'}
    [IO.File]::WriteAllText((Join-Path $run 'report.json'),(@{Status='complete';Completed=51;Total=51;Application='Stopped';Activation='OFF';Scope='SQL preparation; runtime acceptance remains separate'} | ConvertTo-Json))
    Write-Host "SQL preparation complete: 51/51. App stopped; MCP/AI OFF. Report: $run/report.json"
} catch {
    # Closed safe fields only. Do not serialize ErrorRecord, exception text or raw SQL output.
    if ($run) {
        $diagnostics=$null
        if ($stageName -ceq '21-proof') { $diagnostics=Read-ProofModuleFailures $script:CleanLastLines.ToArray() }
        $report=@{Status='stopped';Completed=$completed;Total=51;Stage=$stageName;Operation=$operation;Approved=$approved;SqlErrors=$script:CleanSqlNumbers;Assertions=$script:CleanAssertions;ProofDiagnostics=$diagnostics;NextAction='Preserve this new run. Do not replay pending stages. Fix the reported blocker before authorizing another clean rebuild.'}
        [IO.File]::WriteAllText((Join-Path $run 'report.json'),($report | ConvertTo-Json -Depth 10))
        if ($approved) { New-CleanEvent (Join-Path $run 'journal.jsonl') @{Kind='stopped';Stage=$stageName;Completed=$completed;SqlErrors=$script:CleanSqlNumbers} }
    }
    Write-Host "STOP at $stageName; confirmed $completed/51. Private report: $run/report.json. No automatic retry."
    # Preflight messages are deliberately generic too: private SQL/errors can reach PowerShell wrappers.
    throw 'Acceptance stopped. Check prerequisites and the safe report; preserve all evidence.'
} finally {
    if ($null -ne $oldLock) { $oldLock.Dispose() }; if ($null -ne $lock) { $lock.Dispose() }
    $owner=$null; $credentials=$null; $offline=$null; $output=$null; $snapshots=$null; $capture=$null; $script:CleanLastLines.Clear()
}
