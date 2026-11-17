# Shared implementation; dot-sourcing never connects or executes SQL.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Supervision.ps1')
. (Join-Path $PSScriptRoot 'ProofModuleDiagnostics.ps1')
if (-not ('CleanSql' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'CleanSql.cs') }
$script:CleanSubscription='cd334e90-1e97-4b68-b4e3-80bf7b438546'
$script:CleanResource="/subscriptions/$script:CleanSubscription/resourceGroups/Shale/providers"
$script:CleanLastLines=[Collections.Generic.List[string]]::new()
$script:CleanSqlNumbers=@(); $script:CleanAssertions=@()
function Invoke-CleanAzure([string[]]$Arguments,[switch]$PreserveSettingStrings) {
    # argv contains fixed resource identities only, never secrets. Never emit raw az errors.
    $value=& az @Arguments --only-show-errors --output json 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'STOP: acceptance Azure resource operation failed.' }
    if ($value -and $PreserveSettingStrings) {
        # App Service settings are strings, including ISO timestamps. Avoid ConvertFrom-Json's
        # automatic DateTime coercion; JsonDocument also works on the supported PowerShell 7.4.
        $document=$null
        try {
            $document=[System.Text.Json.JsonDocument]::Parse([string]($value -join "`n"))
            foreach ($row in $document.RootElement.EnumerateArray()) {
                $name=$row.GetProperty('name');$setting=$row.GetProperty('value')
                if ($name.ValueKind -ne [System.Text.Json.JsonValueKind]::String -or
                    $setting.ValueKind -ne [System.Text.Json.JsonValueKind]::String) { throw 'INVALID_APP_SETTING_RESPONSE' }
                [pscustomobject]@{name=$name.GetString();value=$setting.GetString()}
            }
        } catch { throw 'INVALID_APP_SETTING_RESPONSE' }
        finally { if ($null -ne $document) { $document.Dispose() } }
        return
    }
    if ($value) { return ($value -join "`n" | ConvertFrom-Json) }
}
function Assert-CleanResources([switch]$StopApp) {
    $dbId="$script:CleanResource/Microsoft.Sql/servers/shale/databases/ShaleMcpAcceptance"
    $appId="$script:CleanResource/Microsoft.Web/sites/shale-mcp-acceptance"
    $db=Invoke-CleanAzure @('sql','db','show','--ids',$dbId)
    if ($db.id -ine $dbId -or $db.name -cne 'ShaleMcpAcceptance' -or $db.collation -cne 'SQL_Latin1_General_CP1_CI_AS') { throw 'STOP: acceptance database resource identity differs.' }
    $app=Invoke-CleanAzure @('webapp','show','--ids',$appId)
    if ($app.id -ine $appId -or $app.name -cne 'shale-mcp-acceptance') { throw 'STOP: acceptance app resource identity differs.' }
    if ($StopApp -and $app.state -cne 'Stopped') {
        [void](Invoke-CleanAzure @('webapp','stop','--ids',$appId))
        $app=Invoke-CleanAzure @('webapp','show','--ids',$appId)
    }
    if ($app.state -cne 'Stopped') { throw 'STOP: acceptance app must stay stopped.' }
}
function New-CleanConnection([pscredential]$Credential) {
    # Fixed endpoint; no configurable server/database or fallback to master.
    $builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder['Data Source']='tcp:shale.database.windows.net,1433'
    $builder['Initial Catalog']='ShaleMcpAcceptance'
    $builder['Encrypt']=$true; $builder['TrustServerCertificate']=$false
    $builder['Pooling']=$false; $builder['MultipleActiveResultSets']=$false
    $builder['ConnectRetryCount']=0; $builder['Connect Timeout']=30
    $builder['Application Name']='Shale acceptance clean slate'
    $password=$Credential.Password.Copy(); $password.MakeReadOnly()
    $sqlCredential=[System.Data.SqlClient.SqlCredential]::new($Credential.UserName,$password)
    return [System.Data.SqlClient.SqlConnection]::new($builder.get_ConnectionString(),$sqlCredential)
}
function Invoke-CleanCommand($Connection,[string]$Sql,$Lines) {
    $command=$Connection.CreateCommand(); $command.CommandTimeout=600; $command.CommandText=$Sql
    $reader=$null
    try { $reader=$command.ExecuteReader(); [CleanSql]::Drain($reader,$Lines) }
    finally { if ($null -ne $reader) { $reader.Dispose() }; $command.Dispose() }
}
function Invoke-CleanSource([string]$Sql,[pscredential]$Credential) {
    $script:CleanLastLines=[Collections.Generic.List[string]]::new(); $script:CleanSqlNumbers=@(); $script:CleanAssertions=@()
    # Split/validate the ENTIRE source before opening any connection.
    $batches=[CleanSql]::Batches($Sql)
    $connection=New-CleanConnection $Credential
    try {
        $connection.Open()
        $target=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance/target.sql'))
        Invoke-CleanCommand $connection $target $script:CleanLastLines
        Invoke-CleanCommand $connection @'
SET NOCOUNT ON; SET IMPLICIT_TRANSACTIONS OFF; SET XACT_ABORT ON;
SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET QUOTED_IDENTIFIER ON; SET NUMERIC_ROUNDABORT OFF;
'@ $script:CleanLastLines
        foreach ($batch in $batches) { Invoke-CleanCommand $connection $batch $script:CleanLastLines }
        Invoke-CleanCommand $connection "IF @@TRANCOUNT<>0 THROW 59800,'SOURCE_TRANSACTION_OPEN',1;" $script:CleanLastLines
        return ,@($script:CleanLastLines.ToArray())
    } catch {
        $cause=$_.Exception
        while ($null -ne $cause -and $cause -isnot [System.Data.SqlClient.SqlException]) { $cause=$cause.InnerException }
        if ($null -ne $cause) {
            $script:CleanSqlNumbers=@($cause.Errors | ForEach-Object Number | Select-Object -Unique -First 32)
            if (Get-Command Get-SafeFastTrackAssertions -ErrorAction SilentlyContinue) { $script:CleanAssertions=@(Get-SafeFastTrackAssertions @($cause.Errors | ForEach-Object Message)) }
            $resetCodes=@('RESET_IDENTITY_REFUSED','RESET_UNSUPPORTED_FEATURE','RESET_MAINTENANCE_LOCK_REFUSED','RESET_DEPENDENCY_CYCLE','RESET_OBJECTS_REMAIN','SOURCE_TRANSACTION_OPEN')
            $safeCodes=@($cause.Errors | Where-Object { $_.Message -cin $resetCodes } | ForEach-Object Message | Select-Object -Unique)
            if ($safeCodes.Count) { $script:CleanAssertions=$safeCodes }

        }
        # Deliberately discard driver text, SQL, data and credentials.
        throw 'STOP: SQL source failed; only bounded safe diagnostics are recorded.'
    } finally {
        if ($connection.State -eq 'Open') {
            try { $discard=[Collections.Generic.List[string]]::new(); Invoke-CleanCommand $connection 'IF XACT_STATE()<>0 ROLLBACK;' $discard } catch { }
        }
        $connection.Dispose() # pooling disabled: session/context/impersonation never reused
    }
}
function Get-CleanSourceParts([string]$Sql,$Stage) {
    $result=@(); $position=0
    for ($i=1; $i -le $Stage.sources.Count; $i++) {
        $marker="SELECT 'ACCEPTANCE_SOURCE_OK:$($Stage.name):$i' AS AcceptanceResult;`nGO`n"
        $end=$Sql.IndexOf($marker,$position,[StringComparison]::Ordinal)
        if ($end -lt 0) { throw 'STOP: clean-slate source marker absent.' }
        $end+=$marker.Length; $result+=$Sql.Substring($position,$end-$position); $position=$end
    }
    if ($position -ne $Sql.Length) { throw 'STOP: unexpected trailing source bytes.' }
    return ,$result
}
function New-CleanEvent([string]$Path,[hashtable]$Event) {
    # New-run append-only evidence; no import/adoption/recovery of historical journals.
    $previous='0'*64
    if (Test-Path -LiteralPath $Path) {
        $bytes=[IO.File]::ReadAllText($Path)
        if (-not $bytes.EndsWith("`n")) { throw 'STOP: new-run journal truncated; preserve it.' }
        foreach ($line in [IO.File]::ReadAllLines($Path)) {
            $record=$line | ConvertFrom-Json
            if ($record.Previous -cne $previous -or $record.Hash -cne (Get-TextSHA256 ($previous+"`n"+$record.Payload))) { throw 'STOP: new-run journal integrity failure.' }
            $previous=$record.Hash
        }
    }
    $payload=ConvertTo-Json -InputObject $Event -Depth 8 -Compress
    $record=[ordered]@{Previous=$previous;Payload=$payload;Hash=(Get-TextSHA256 ($previous+"`n"+$payload))}
    $stream=[IO.File]::Open($Path,[IO.FileMode]::Append,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try {
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-Json -InputObject $record -Compress)+"`n")
        $stream.Write($bytes); $stream.Flush($true)
    } finally { $stream.Dispose() }

}
function Set-CleanPhase1R([string]$Report,[string]$Inputs) {
    $comparison=Get-Content -LiteralPath $Report -Raw | ConvertFrom-Json
    if ($comparison.outcome -cne 'PASS' -or @($comparison.discrepancies).Count -ne 0) { throw 'STOP: fresh baseline comparison failed; no normalization or hash adoption.' }
    $values=@{
        ReviewedStrictHash=$comparison.observedFunctionHashes.fn_FilterByTenant
        ReviewedOverlayHash=$comparison.observedFunctionHashes.fn_FilterByTenantOrGlobal
        ReviewedOtherPredicatesHash=$comparison.observedOtherPredicatesHash
    }
    foreach ($value in $values.Values) { if ($value -cnotmatch '^[A-F0-9]{64}$') { throw 'STOP: baseline fingerprints unavailable.' } }
    [IO.File]::WriteAllText($Inputs,($values | ConvertTo-Json -Compress))
}

function Invoke-CleanStage($Stage,[string]$Sql,[pscredential]$Credential) {
    $output=@()
    foreach ($part in (Get-CleanSourceParts $Sql $Stage)) { $output+=Invoke-CleanSource $part $Credential }
    return Read-SafeResult $output $Stage.name @($Stage.sources).Count
}

function Get-CleanIdentityCredentials([string]$Run) {
    $credentials=@{}
    $secrets=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    # Reuse valid reviewed identity inputs; never log their SQL or secret values.
    foreach ($file in @('identities-foundation.sql','identities.sql')) {
        $path=Join-Path $run $file; $text=[IO.File]::ReadAllText($path)
        $matches=[regex]::Matches($text,"\(N'(mcpacc_[a-z_]+)',(?:N'[A-Za-z0-9]+'|NULL),(?:N'((?:''|[^'])*)'|NULL)\)")
        $expected=if ($file -ceq 'identities.sql') {12} else {5}
        if ($matches.Count -ne $expected) { throw 'STOP: reviewed identity input shape differs.' }
        foreach ($match in $matches) {
            $user=$match.Groups[1].Value; $value=$match.Groups[2].Value.Replace("''", "'")
            if (-not $match.Groups[2].Success) {
                $newCredential=Get-Credential -UserName $user -Message 'Missing acceptance-only contained-user credential; stored only in the NEW protected private input'
                if ($null -eq $newCredential -or $newCredential.UserName -cne $user) { throw 'STOP: exact contained username required.' }
                $value=$newCredential.GetNetworkCredential().Password
                $replacement=$match.Value.Substring(0,$match.Value.Length-5)+"N'"+$value.Replace("'","''")+"')"
                $text=$text.Replace($match.Value,$replacement)
                $newCredential=$null
            }
            if ($value.TrimEnd().Length -lt 20 -or $value.Length -gt 128 -or $value -match '[\x00\r\n]' -or -not $secrets.Add($value)) { throw 'STOP: 17 independent reviewed passwords of 20-128 characters required.' }
            if ($credentials.ContainsKey($user)) { throw 'STOP: duplicate private identity.' }
            $credentials[$user]=[pscredential]::new($user,(ConvertTo-SecureString $value -AsPlainText -Force))
        }
        # Prepared authorization bits are effective only after the single whole-run approval.
        if ($text -notmatch '@ProvisionApproved\s+bit\s*=\s*[01]') { throw 'STOP: identity provisioning template differs.' }
        $text=[regex]::Replace($text,'(@ProvisionApproved\s+bit\s*=\s*)[01]','${1}1')
        [IO.File]::WriteAllText($path,$text)
    }
    $expectedUsers=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stages.json') -Raw | ConvertFrom-Json |
        Where-Object { $_.PSObject.Properties.Name -contains 'user' } | ForEach-Object user | Sort-Object -Unique)
    if ($credentials.Count -ne $expectedUsers.Count -or @($expectedUsers | Where-Object { -not $credentials.ContainsKey($_) }).Count) { throw 'STOP: contained credentials do not match the 17 required manifest identities.' }
    return $credentials
}
