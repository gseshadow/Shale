#requires -Version 5.1
[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][ValidateSet('Discover','Setup','Workload','Stop','Cleanup')][string]$Phase,
 [Parameter(Mandatory=$true)][string]$StatePath,
 [string]$FactsPath, [string]$EvidenceParent,
 [System.Management.Automation.PSCredential]$Credential,
 [string]$AccessToken,
 [string]$ReviewedMetadataSHA256,
 [string]$RetainedEvidenceSHA256
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$script:Root=$PSScriptRoot
$script:Connection=$null
function Save-State { $script:State | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $StatePath -Encoding UTF8 }
function Save-Evidence([string]$Name,$Value) {
 $path=Join-Path $script:State.Directory $Name
 $bytes=[Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Value -Depth 30))
 $stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
 try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
 $script:State.Files+=@([pscustomobject]@{Name=$Name;SHA256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash})
 Save-State
}
function Query([string]$Sql, $Connection=$script:Connection) {
 $cmd=$Connection.CreateCommand(); $cmd.CommandTimeout=30; $cmd.CommandText=$Sql
 $rows=New-Object 'Collections.Generic.List[object]'
 try {
  $reader=$cmd.ExecuteReader()
  try {
   do { while ($reader.Read()) {
    $row=[ordered]@{}
    for($i=0;$i -lt $reader.FieldCount;$i++) {
     $row[$reader.GetName($i)] = if($reader.IsDBNull($i)) {$null} else {$reader.GetValue($i)}
    }
    $rows.Add([pscustomobject]$row)
   } } while($reader.NextResult())
  } finally { $reader.Dispose() }
 } finally { $cmd.Dispose() }
 return $rows.ToArray()
}
function Open-Connection {
 $f=$script:State.Facts
 $builder=New-Object Data.SqlClient.SqlConnectionStringBuilder
 # PowerShell adapts this IDictionary through connection-string keywords, not CLR property names.
 $builder['Data Source']=$f.Endpoint; $builder['Initial Catalog']=$f.Database
 $builder['Encrypt']=$true; $builder['TrustServerCertificate']=$false
 $builder['Connect Timeout']=30; $builder['Pooling']=$false
 $builder['Asynchronous Processing']=$true
 $builder['Application Name']='SyntheticInvocationValidation'
 # Explicit CLR accessor avoids dictionary adaptation on the related read as well.
 $con=[Data.SqlClient.SqlConnection]::new($builder.get_ConnectionString())
 if ($AccessToken) { $con.AccessToken=$AccessToken }
 elseif ($Credential) {
  $securePassword=$Credential.Password.Copy();$securePassword.MakeReadOnly()
  $con.Credential=[Data.SqlClient.SqlCredential]::new($Credential.UserName,$securePassword)
 } else { throw 'Supply a separately approved synthetic-only credential or in-memory access token' }
 try {
  $con.Open()
  $identity=@(Query 'SELECT CONVERT(nvarchar(128),SERVERPROPERTY(''ServerName'')) AS ServerName, DB_NAME() AS DatabaseName, DB_ID() AS DatabaseId, CONVERT(int,SERVERPROPERTY(''EngineEdition'')) AS EngineEdition, USER_NAME() AS DatabaseUser, CONVERT(nvarchar(128),SERVERPROPERTY(''ProductVersion'')) AS ProductVersion, @@TRANCOUNT AS TransactionCount' $con)
  if($identity.Count -ne 1 -or $identity[0].ServerName -cne $f.SqlServerName -or
     $identity[0].DatabaseName -cne $f.Database -or $identity[0].DatabaseId -ne $f.DatabaseId -or
     $identity[0].EngineEdition -ne 5 -or $identity[0].DatabaseUser -cne 'dbo' -or $identity[0].DatabaseUser -cne $f.DatabaseUser -or
     $identity[0].TransactionCount -ne 0) { throw 'Wrong synthetic target or identity' }
  if($Phase -ne 'Stop' -and $script:State.Version -and $identity[0].ProductVersion -cne $script:State.Version) { throw 'Engine version changed; review new metadata in a fresh run' }
  return $con
 } catch { $con.Dispose(); throw 'Connection/identity guard failed; no fallback identity permitted' }
}
function Template([string]$Name,[hashtable]$Extra=@{}) {
 $sql=Get-Content -LiteralPath (Join-Path $script:Root $Name) -Raw
 $values=@{schema=$script:State.Schema;token=$script:State.Token;dbid=$script:State.Facts.DatabaseId}
 foreach($key in $Extra.Keys) { $values[$key]=$Extra[$key] }
 foreach($key in $values.Keys) { $sql=$sql.Replace('{{'+$key+'}}',[string]$values[$key]) }
 if($sql.Contains('{{')) { throw 'Unresolved SQL template' }
 return $sql
}
function Verify-LocalState {
 if($script:State.Token -cnotmatch '^[a-f0-9]{32}$' -or $script:State.Schema -cne ('iv_'+$script:State.Token)) { throw 'Task ownership mismatch' }
 $dir=Get-Item -LiteralPath $script:State.Directory
 if($dir.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Evidence directory is a reparse point' }
 foreach($file in $script:State.Files) {
  if($file.Name -notmatch '^[a-z0-9-]+\.json$') { throw 'Unsafe evidence name' }
  $p=Join-Path $dir.FullName $file.Name
  if((Get-Item -LiteralPath $p).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Evidence is a reparse point' }
  if((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash -cne $file.SHA256) { throw 'Evidence changed; preserve and review' }
 }
}
function Verify-Objects {
 $hash=@(Query (Template 'catalog.sql'))
 $marker=@(Query ("SELECT CONVERT(nvarchar(128),value) AS OwnerToken FROM sys.extended_properties WHERE class=3 AND major_id=SCHEMA_ID(N'"+$script:State.Schema+"') AND name=N'InvocationValidationOwner'"))
 if($marker.Count -ne 1 -or $marker[0].OwnerToken -cne $script:State.Token -or
    $hash.Count -ne 1 -or $hash[0].CatalogHash -cne $script:State.CatalogHash) { throw 'Exact synthetic object ownership/configuration changed' }
}
function Verify-Session($Session) {
 if($Session.Kind -cnotin @('normal','stress','wrongdb') -or [int]$Session.Id -le 0 -or $Session.Hash -cnotmatch '^[A-F0-9]{64}$') { throw 'Invalid saved session identity' }
 if($Session.Name -cne ('iv_'+$script:State.Token+'_'+$Session.Kind)) { throw 'Session name ownership mismatch' }
 $actual=@(Query (Template 'session-catalog.sql' @{session=$Session.Name}))
 if($actual.Count -ne 1 -or $actual[0].SessionId -ne $Session.Id -or
    $actual[0].ConfigHash -cne $Session.Hash) { throw 'Session identity/configuration drift; refuse adoption or deletion' }
}
function Snapshot($Session,[string]$Label) {
 Verify-Session $Session
 $rows=@(Query (Template 'collect.sql' @{session=$Session.Name}))
 Save-Evidence ($Session.Kind+'-'+$Label+'.json') $rows
 if($rows.Count -ne 1 -or $rows[0].TargetName -cne 'ring_buffer') { throw 'Capture missing or stopped: coverage UNKNOWN' }
 return ,$rows
}
function New-Call($Connection,[string]$Procedure,[int]$Mode=0) {
 $cmd=$Connection.CreateCommand(); $cmd.CommandTimeout=40
 $cmd.CommandType=[Data.CommandType]::StoredProcedure
 $cmd.CommandText=$script:State.Schema+'.'+$Procedure
 [void]$cmd.Parameters.Add('@input',[Data.SqlDbType]::NVarChar,4000)
 $cmd.Parameters['@input'].Value='IV_FORBIDDEN_SQL; IV_FORBIDDEN_PARAMETER; IV_FORBIDDEN_EMAIL@example.invalid; IV_FORBIDDEN_CREDENTIAL; IV_FORBIDDEN_HASH; IV_FORBIDDEN_TOKEN; IV_FORBIDDEN_DATA'
 if($Procedure -in @('probe_a','probe_b','probe_c')) {
  [void]$cmd.Parameters.Add('@mode',[Data.SqlDbType]::Int);$cmd.Parameters['@mode'].Value=$Mode
 }
 return $cmd
}
function Invoke-Call([string]$Procedure,[int]$Mode=0) {
 $cmd=New-Call $script:Connection $Procedure $Mode
 try { [void]$cmd.ExecuteNonQuery();if($Mode -eq 1){throw 'Synthetic failure did not fail'} }
 catch [Data.SqlClient.SqlException] { if($Mode -ne 1 -or $_.Exception.Number -ne 59011) { throw 'Unexpected workload SQL failure' } }
 finally { $cmd.Dispose() }
}
try {
 if($Phase -eq 'Discover') {
  if(Test-Path -LiteralPath $StatePath) { throw 'Refuse existing state' }
  $f=Get-Content -LiteralPath $FactsPath -Raw | ConvertFrom-Json
  foreach($key in @('Endpoint','SqlServerName','Database','DatabaseId','DatabaseUser','ResourceId','ApprovalReference','ProvenanceReference','IdentityApprovalReference','MetadataReviewReference','EvidenceRetentionReference','ExclusiveWindowReference','Region','ServiceObjectiveOrPool','ApprovedSourceRevision','CostApprovalReference','OperatorReference')) {
   if(-not $f.$key) { throw ('Missing real resource fact/approval: '+$key) }
  }
  if($f.DedicatedEmptySynthetic -cne 'APPROVED_NO_SHALE_DATA_OR_CREDENTIALS' -or
     $f.Database -match '(?i)shale|master|tempdb|model|msdb' -or
     $f.Endpoint -notmatch '^[A-Za-z0-9.-]+\.database\.windows\.net,1433$' -or
     [int]$f.DatabaseId -le 0) { throw 'Refuse unapproved or unsafe target' }
  $parent=(Resolve-Path -LiteralPath $EvidenceParent).Path
  $token=[guid]::NewGuid().ToString('N');$directory=Join-Path $parent ('iv-'+$token)
  if(Test-Path -LiteralPath $directory) { throw 'Refuse existing directory' }
  [void](New-Item -ItemType Directory -Path $directory)
  $script:State=[pscustomobject]@{Token=$token;Schema=('iv_'+$token);Directory=$directory;Facts=$f;Files=@();Version='';CatalogHash='';Modules=@();Sessions=@();Status='Discovering';CreatedUtc=[DateTime]::UtcNow.ToString('o')}
  Save-State
 } else {
  $script:State=Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
  Verify-LocalState
 }
 $script:Connection=Open-Connection
 if($Phase -notin @('Stop','Cleanup') -and [DateTime]::UtcNow -gt ([DateTime]::Parse($script:State.CreatedUtc).ToUniversalTime().AddHours(1))) { throw 'One-hour experiment bound reached; stop and review' }
 switch($Phase) {
  'Discover' {
   $empty=@(Query 'SELECT (SELECT COUNT_BIG(*) FROM sys.objects WHERE is_ms_shipped=0) AS ObjectCount,(SELECT COUNT_BIG(*) FROM sys.database_event_sessions) AS SessionCount')
   if($empty[0].ObjectCount -ne 0 -or $empty[0].SessionCount -ne 0) { throw 'Refuse nonempty database or existing telemetry' }
   $identity=@(Query "SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')) AS ProductVersion,CONVERT(nvarchar(33),SYSUTCDATETIME(),126) AS Utc")
   $script:State.Version=$identity[0].ProductVersion
   Save-Evidence 'identity.json' $identity
   Save-Evidence 'metadata.json' @(Query (Template 'discover.sql'))
   $script:State.Status='Discovered';Save-State
  }
  'Setup' {
   if($script:State.Status -cne 'Discovered') { throw 'Setup is single-use' }
   $meta=Join-Path $script:State.Directory 'metadata.json'
   if(-not $ReviewedMetadataSHA256 -or (Get-FileHash -LiteralPath $meta -Algorithm SHA256).Hash -cne $ReviewedMetadataSHA256) { throw 'Review complete supported metadata and supply its exact SHA256 first' }
   if((@(Query (Template 'discover.sql')) | ConvertTo-Json -Depth 30) -cne ((Get-Content -LiteralPath $meta -Raw).TrimEnd())) { throw 'Metadata drift; start fresh' }
   $metadata=Get-Content -LiteralPath $meta -Raw | ConvertFrom-Json
   foreach($event in @('module_start','module_end')) {
    foreach($field in @('collect_statement','source_database_id','object_id')) {
     $r=@($metadata | Where-Object {$_.PackageName -ceq 'sqlserver' -and $_.ObjectName -ceq $event -and $_.ColumnName -ceq $field})
     if($r.Count -ne 1 -or ($field -eq 'collect_statement' -and ($r[0].ColumnType -cne 'customizable' -or $r[0].TypeName -cne 'boolean'))) { throw 'Required event/filter/payload control unsupported' }
    }
   }
   # Human review must also reject unknown fields and unsupported semantics before this point.
   $script:State.Status='SetupInProgress';Save-State
   [void](Query (Template 'setup.sql'))
   $script:State.CatalogHash=@(Query (Template 'catalog.sql'))[0].CatalogHash
   $script:State.Modules=@(Query ("SELECT object_id AS Id,name AS Name FROM sys.procedures WHERE schema_id=SCHEMA_ID(N'"+$script:State.Schema+"') AND name IN (N'probe_a',N'probe_b',N'probe_c') ORDER BY name"))
   if($script:State.Modules.Count -ne 3) { throw 'Synthetic module identity incomplete' }
   Save-State
   foreach($kind in @('normal','stress','wrongdb')) {
    $name='iv_'+$script:State.Token+'_'+$kind
    $v=@{session=$name;a=$script:State.Modules[0].Id;b=$script:State.Modules[1].Id;c=$script:State.Modules[2].Id;limit=1000;buffer=4096}
    if($kind -eq 'stress') { $v.limit=8;$v.buffer=1024 }
    if($kind -eq 'wrongdb') { $v.dbid=[int]$script:State.Facts.DatabaseId+1 }
    if(@(Query (Template 'session-catalog.sql' @{session=$name})).Count -ne 0) { throw 'Refuse preexisting session' }
    [void](Query (Template 'session.sql' $v))
    $config=@(Query (Template 'session-catalog.sql' @{session=$name}))[0]
    $script:State.Sessions+=@([pscustomobject]@{Name=$name;Kind=$kind;Id=$config.SessionId;Hash=$config.ConfigHash})
    Save-Evidence ($kind+'-config.json') @($config)
   }
   $script:State.Status='Ready';Save-State
  }
  'Workload' {
   if($script:State.Status -cne 'Ready') { throw 'Workload is single-use; use a fresh run for repeats' }
   Verify-Objects
   $script:State.Status='Running';Save-State
   foreach($session in $script:State.Sessions) {
    Verify-Session $session
    if(@(Query (Template 'collect.sql' @{session=$session.Name})).Count -ne 0) { throw 'Refuse already-running session' }
    [void](Query ('ALTER EVENT SESSION ['+$session.Name+'] ON DATABASE STATE=START'))
    try {
     [void](Snapshot $session 'before')
     if($session.Kind -eq 'normal') {
      # Direct and same-owner nested calls, plus same-schema and near-name negatives.
      foreach($p in @('probe_a','probe_b','probe_c','unrelated','probe_a_decoy')) { Invoke-Call $p }
      Invoke-Call 'wrapper'
      [void](Query ('EXEC ['+$script:State.Schema+'_decoy].[probe_a] @input=N''IV_FORBIDDEN_OTHER_SCHEMA'';'))
      foreach($p in @('probe_a','probe_b','probe_c')) {
       $cmd=$script:Connection.CreateCommand();$cmd.CommandTimeout=30
       $cmd.CommandText="/* IV_FORBIDDEN_SQL */ EXEC sys.sp_executesql N'EXEC ["+$script:State.Schema+"].["+$p+"] @v',N'@v nvarchar(4000)',@v=N'IV_FORBIDDEN_DYNAMIC'"
       try { [void]$cmd.ExecuteNonQuery() } finally { $cmd.Dispose() }
       # Two genuinely overlapping requests on distinct guarded connections.
       $parallel=@()
       try {
        # Open/guard BOTH connections before firing either one-second request.
        foreach($i in 1..2) {
         $con=Open-Connection;$command=New-Call $con $p 3
         $parallel+=@([pscustomobject]@{Connection=$con;Command=$command;Pending=$null})
        }
        foreach($item in $parallel) { $item.Pending=$item.Command.BeginExecuteNonQuery() }
        foreach($item in $parallel) { [void]$item.Command.EndExecuteNonQuery($item.Pending) }
       } finally { foreach($item in $parallel) { $item.Command.Dispose();$item.Connection.Dispose() } }
       Invoke-Call $p 1
       # Binding/conversion failure before module entry. Any observed entry makes this test fail.
       $cmd=New-Call $script:Connection $p
       $cmd.Parameters.RemoveAt('@mode');[void]$cmd.Parameters.Add('@mode',[Data.SqlDbType]::NVarChar,32)
       $cmd.Parameters['@mode'].Value='IV_FORBIDDEN_BAD_MODE'
       try { [void]$cmd.ExecuteNonQuery();throw 'Pre-entry conversion unexpectedly succeeded' }
       catch [Data.SqlClient.SqlException] { if($_.Exception.Number -ne 8114) { throw 'Unexpected pre-entry failure category' } }
       finally { $cmd.Dispose() }
       # Observe entry before requesting cancellation; no guessed delay counts as entry proof.
       $con=Open-Connection;$cmd=New-Call $con $p 2
       try {
        $old=@(Query (Template 'collect.sql' @{session=$session.Name}));$xml=[xml]$old[0].RawTarget
        $id=($script:State.Modules | Where-Object Name -CEQ $p).Id
        $prior=@($xml.RingBufferTarget.event | Where-Object {$_.name -ceq 'module_start' -and ($_.data | Where-Object name -CEQ 'object_id').value -eq $id}).Count
        if($prior -ne 6) { throw 'Pre-cancel count unavailable or unexpected; coverage UNKNOWN' }
        $pending=$cmd.BeginExecuteNonQuery();$seen=$false
        for($i=0;$i -lt 20;$i++) {
         Start-Sleep -Milliseconds 250
         $r=@(Query (Template 'collect.sql' @{session=$session.Name}));$x=[xml]$r[0].RawTarget
         $count=@($x.RingBufferTarget.event | Where-Object {$_.name -ceq 'module_start' -and ($_.data | Where-Object name -CEQ 'object_id').value -eq $id}).Count
         if($count -gt $prior) { $seen=$true;break }
        }
        if(-not $seen) { throw 'Cancellation entry handshake unavailable: coverage UNKNOWN' }
        Save-Evidence ('cancel-entry-'+$p+'.json') $r
        $cmd.Cancel()
        try { [void]$cmd.EndExecuteNonQuery($pending);throw 'Cancel returned success: unsupported trial' }
        catch [Data.SqlClient.SqlException] { }
       } finally { $cmd.Dispose();$con.Dispose() }
      }
     } elseif($session.Kind -eq 'wrongdb') {
      foreach($p in @('probe_a','probe_b','probe_c')) { Invoke-Call $p }
     } else {
      # 2000 source events exceed the eight-event target; session buffers are bounded at 1024 KB.
      [void](Query ('DECLARE @i int=0; WHILE @i<1000 BEGIN EXEC ['+$script:State.Schema+'].[probe_a] @input=N''IV_FORBIDDEN_STRESS'';SET @i+=1;END;'))
     }
     Start-Sleep -Seconds 2
     [void](Snapshot $session 'after')
    } finally {
     # Preserve the complete target on failures too, before stopping destroys it.
     try { [void](Snapshot $session ('final-'+[guid]::NewGuid().ToString('N'))) }
     finally {
      Verify-Session $session
      [void](Query ('ALTER EVENT SESSION ['+$session.Name+'] ON DATABASE STATE=STOP'))
     }
    }
    Save-Evidence ($session.Kind+'-stopped.json') @(Query (Template 'collect.sql' @{session=$session.Name}))
   }
   Save-Evidence 'workload.json' @{NormalStartsPerModule=7;NormalEndsWithoutCancel=6;CancelEndRange=@(0,1);StressCalls=1000;PreEntryFailuresPerModule=1;CrossDatabaseTrial='NOT_RUN';RestartFailover='NOT_RUN'}
   Verify-Objects
   $script:State.Status='Collected';Save-State
  }
  'Stop' {
   Verify-Objects
   foreach($session in $script:State.Sessions) {
    Verify-Session $session
    if(@(Query (Template 'collect.sql' @{session=$session.Name})).Count -gt 0) {
     [void](Snapshot $session ('emergency-'+[guid]::NewGuid().ToString('N')))
     [void](Query ('ALTER EVENT SESSION ['+$session.Name+'] ON DATABASE STATE=STOP'))
    }
   }
   Save-Evidence ('stop-'+[guid]::NewGuid().ToString('N')+'.json') @(Query "SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')) AS ProductVersion,CONVERT(nvarchar(33),SYSUTCDATETIME(),126) AS Utc")
  }
  'Cleanup' {
   Verify-Objects
   if(-not $RetainedEvidenceSHA256 -or $RetainedEvidenceSHA256 -cnotmatch '^[A-Fa-f0-9]{64}$') { throw 'Independently verified retained evidence manifest SHA256 required' }
   # Require an independently copied, verified manifest; keep every local evidence file too.
   $manifest=Join-Path $script:State.Directory 'retained-manifest.json'
   if((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash -ine $RetainedEvidenceSHA256) { throw 'Retained evidence manifest mismatch' }
   $retained=Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
   if(-not $retained.RetentionApprovalReference -or -not $retained.VerifiedCopyDirectory -or (Resolve-Path -LiteralPath $retained.VerifiedCopyDirectory).Path -ceq (Resolve-Path -LiteralPath $script:State.Directory).Path) { throw 'Independent retained copy and actual retention decision required' }
   foreach($file in $script:State.Files) {
    $copy=Join-Path $retained.VerifiedCopyDirectory $file.Name
    if((Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash -cne $file.SHA256) { throw 'Retained evidence copy missing or changed' }
   }
   if($retained.Token -cne $script:State.Token -or ($retained.Files | ConvertTo-Json -Depth 10) -cne ($script:State.Files | ConvertTo-Json -Depth 10)) { throw 'Retained evidence inventory mismatch' }
   foreach($session in $script:State.Sessions) {
    Verify-Session $session
    if(@(Query (Template 'collect.sql' @{session=$session.Name})).Count -ne 0) { throw 'Stop and preserve raw target before cleanup' }
   }
   foreach($session in $script:State.Sessions) { [void](Query ('DROP EVENT SESSION ['+$session.Name+'] ON DATABASE')) }
   [void](Query (Template 'cleanup.sql'))
   $left=@(Query ("SELECT COUNT_BIG(*) AS RemainingCount FROM sys.schemas WHERE name IN (N'"+$script:State.Schema+"',N'"+$script:State.Schema+"_decoy')"))
   if($left[0].RemainingCount -ne 0) { throw 'Cleanup incomplete; review exact resources' }
   Save-Evidence 'cleanup.json' @{Utc=[DateTime]::UtcNow.ToString('o');EvidencePreserved=$true}
   $script:State.Status='Cleaned';Save-State
  }
 }
 Write-Output ('Synthetic harness phase recorded: '+$Phase+'. Production activation remains REFUSED.')
} catch {
 # Never print SqlException text, connection strings, credentials or workload inputs.
 Write-Error 'Harness refused or failed. Preserve state/evidence; follow README recovery for the failed phase. No production authorization.' -ErrorAction Continue
 exit 1
} finally { if($script:Connection) { $script:Connection.Dispose() } }

exit 0
