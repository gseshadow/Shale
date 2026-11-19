function Invoke-HermesAcceptanceWindow($Context) {
    $approved=$false; $sqlActivationAttempted=$false; $failure=$null; $failureCode=$null; $diagnostic=$null; $phase='approval'; $cleanup=$null; $steps=[Collections.Generic.List[object]]::new()
    try {
        # The ONLY approval. No Force/switch/environment/parameter can supply this acknowledgement.
        Write-Host "Target: $script:LiveAppId / ShaleMcpAcceptance; JAR SHA256 $($Context.Hash); build $($Context.Version)"
        Write-Host 'Deploy dormant, verify worker, activate only synthetic acceptance, open a private Hermes browser/client window, then OFF and stop.'
        Write-Host 'Dedicated acceptance verification budget: 600 seconds per admission, 180-second SQL/network reads, 30-second login. Every security check remains enabled; SQL tool transactions retain their existing short limits.'
        Write-Host 'Hermes connection/discovery budget: 600 seconds; interactive chat window: 30 minutes. No natural-expiry campaign replay.'
        if ((Read-Host 'Type CONNECT_SHALE_MCP_HERMES_ACCEPTANCE to approve this whole controlled run') -cne 'CONNECT_SHALE_MCP_HERMES_ACCEPTANCE') { throw 'OPERATOR_DECLINED' }
        $approved=$true
        New-CleanEvent (Join-Path $Context.Run 'journal.jsonl') @{Kind='approved';JarSHA256=$Context.Hash;Build=$Context.Version}
        # Repeat prerequisites after approval; do not use approval to override drift.
        $phase='repeat-preflight'
        foreach ($item in $Context.Inventory) { if ((Get-FileHash -LiteralPath $item.Path).Hash -cne $item.SHA256) { throw 'PRIOR_EVIDENCE_CHANGED' } }
        Assert-LiveAzureConfiguration; Assert-LiveSettings (Get-LiveSettings) $Context.Credentials; Assert-LiveSqlOff $Context.Owner
        Assert-LiveSynthetic $Context.Owner
        Assert-LiveJar $Context.Jar $Context.Hash $Context.Version
        $phase='guarded-deployment'
        [void](Invoke-CleanAzure @('webapp','deploy','--ids',$script:LiveAppId,'--src-path',$Context.Jar,'--type','jar','--restart','false','--clean','false','--track-status','false'))
        $app=Invoke-CleanAzure @('webapp','show','--ids',$script:LiveAppId)
        if ($app.state -cne 'Stopped') { throw 'DEPLOYMENT_STARTED_APP_UNEXPECTEDLY' }
        $phase='dormant-startup'
        Start-LiveWorker ('acceptance-'+$Context.Id+'-before-dormant')
        $markers=Assert-LiveWorker $Context.Hash $Context.Zones.Java
        if (@($markers | Where-Object {$_ -ceq 'AI_CASE_SQL_READINESS disabled'}).Count -ne 1) { throw 'DORMANT_READINESS_REQUIRED' }
        foreach ($route in @('/mcp','/oauth/mcp/register','/oauth/mcp/token')) {
            $r=Invoke-WebRequest -Uri ($script:LiveOrigin+$route) -Method Post -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck -MaximumRedirection 0 -TimeoutSec 10
            if ($r.StatusCode -ne 404) { throw 'DORMANT_ROUTE_OPEN' }
        }
        $steps.Add(@{Id='dormant-worker';Status='PASS';Markers=$markers})
        [void](Invoke-CleanAzure @('webapp','stop','--ids',$script:LiveAppId)); Assert-CleanResources
        $phase='activation-prerequisites'
        [void](Get-LiveBinding $Context.Evidence.Directory $Context.Owner)
        Invoke-LiveRls $Context.Credentials
        # All native principal/catalog checks precede activation. No installer stage is callable here.
        Invoke-LiveNativeChecks $Context.Specs $Context.Credentials $Context.Run
        $steps.Add(@{Id='native-identities-catalogs-binding-rls';Status='PASS'})
        $baseline=Get-LiveAuditBaseline $Context.Owner
        [IO.File]::WriteAllText((Join-Path $Context.Run 'audit-baseline.json'),($baseline|ConvertTo-Json))
        New-CleanEvent (Join-Path $Context.Run 'journal.jsonl') @{Kind='audit-baseline';Access=$baseline.Access;Phi=$baseline.Phi}
        $phase='synthetic-sql-activation'
        $sqlActivationAttempted=$true # Set before dispatch: partial activation must receive full shutdown.
        Set-LiveSqlControls $true $Context.Owner $Context.Credentials
        $phase='synthetic-app-activation'
        Set-LiveAppControls $true $Context.Run
        $phase='activation-resource-verification'
        Assert-CleanResources
        $phase='enabled-worker-start-and-health'
        Start-LiveWorker ('acceptance-'+$Context.Id+'-dormant')
        $phase='enabled-worker-evidence'
        $markers=Assert-LiveWorker $Context.Hash $Context.Zones.Java -Ready
        $steps.Add(@{Id='enabled-startup';Status='PASS';Markers=$markers})
        $phase='live-client'
        $response=Invoke-WebRequest -Uri ($script:LiveOrigin+'/login') -Headers @{Accept='text/html'} -MaximumRedirection 0 -TimeoutSec 30
        if ($response.StatusCode -ne 200 -or $response.Content -notmatch '<div id="root">') { throw 'BROWSER_UI_UNAVAILABLE' }
        Write-Host 'Sign in as admin@example.invalid in the browser; approve the application and separately consent.'
        Write-Host 'In Hermes, ask it to search synthetic cases and open Case 100. Case 200 must not disclose tenant B data.'
        Write-Host 'Exit Hermes when finished. The window has a 30-minute limit, followed by OFF and stop.'
        & hermes mcp add shale-acceptance --url ($script:LiveOrigin+'/mcp') --auth oauth --connect-timeout 600
        if ($LASTEXITCODE -ne 0) { throw 'HERMES_CONNECTION_FAILED' }
        $clientProcess=Start-Process -FilePath (Get-Command hermes -CommandType Application | Select-Object -First 1 -ExpandProperty Source) -ArgumentList @("chat","--cli","--toolsets","shale-acceptance") -NoNewWindow -PassThru
        try {
            if (-not $clientProcess.WaitForExit(1800000)) { $clientProcess.Kill($true); throw 'HERMES_WINDOW_TIMEOUT' }
            if ($clientProcess.ExitCode -ne 0) { throw 'HERMES_CLIENT_FAILED' }
        } finally { if (-not $clientProcess.HasExited) { $clientProcess.Kill($true) } }

        $steps.Add(@{Id='live-client';Status='PASS'})
        [void](Get-LiveBinding $Context.Evidence.Directory $Context.Owner); Assert-LiveSynthetic $Context.Owner
        $steps.Add(@{Id='operator-functional-check';Status='MANUAL_REVIEW_REQUIRED'})
    } catch { $failure=$phase; $failureCode=Get-LiveSafeCampaignFailure $_; $diagnostic=Get-LiveCampaignDiagnostic $_ } finally {
        if ($approved) { $cleanup=Stop-LiveAcceptance $Context.Owner $Context.Credentials $Context.Run -VerifySqlOnly:(-not $sqlActivationAttempted) }
        $pass=$approved -and -not $failure -and $cleanup.SqlOff -and $cleanup.AppOff -and $cleanup.ApplicationStopped
        if (-not $failure -and -not $pass) { $failureCode='CLEANUP_NOT_CONFIRMED' }
        $result=@{Status=$(if($pass){'WINDOW_COMPLETED'}else{'FAIL'});Approved=$approved;Failure=$failure;FailureCode=$failureCode;FailureDiagnostic=$diagnostic;Cleanup=$cleanup;Steps=$steps.ToArray();
          JarSHA256=$Context.Hash;BuildVersion=$Context.Version;SqlJournalHash=$Context.Evidence.JournalHash;
          Scope='Hermes acceptance-only manual trial; completed window does not certify functional outcomes or audits'}
        [IO.File]::WriteAllText((Join-Path $Context.Run 'report.json'),($result | ConvertTo-Json -Depth 12))
        New-CleanEvent (Join-Path $Context.Run 'journal.jsonl') @{Kind='finished';Status=$result.Status;Cleanup=$cleanup}
    }
    return $result
}
