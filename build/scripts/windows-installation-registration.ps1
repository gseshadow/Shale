$ErrorActionPreference = 'Stop'
$Mode=@{I='Install';U='Uninstall';R='RollbackInstall';B='RollbackUninstall';C='Commit'}[$env:M]
$OwnerSid=$env:O; $InstallRoot=$env:I; $SupportRoot=$env:S
if ($Mode -notin @('Install','Uninstall','RollbackInstall','RollbackUninstall','Commit')) { throw 'Shale installation registration rejected: invalid-mode' }
$base = 'HKLM:\SOFTWARE\Shale'
$registrations = "$base\Installations"
$state = "$base\InstallerState"
$schema = 1

function Canonical([string]$Value) { return [IO.Path]::GetFullPath($Value).TrimEnd('\') }
function Fail([string]$Code) { throw "Shale installation registration rejected: $Code" }
function Assert-Inputs {
    if ($OwnerSid -notmatch '^S-1-(?:\d+-){1,14}\d+$') { Fail 'invalid-owner' }
    $profile = (Get-ItemProperty -LiteralPath "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\$OwnerSid" -Name ProfileImagePath).ProfileImagePath
    $expected = Canonical (Join-Path ([Environment]::ExpandEnvironmentVariables($profile)) 'AppData\Local\Shale')
    if ((Canonical $InstallRoot) -ine $expected -or (Canonical $SupportRoot) -ine $expected) { Fail 'owner-path-mismatch' }
    foreach ($candidate in @($InstallRoot,$SupportRoot)) { $cursor = Get-Item -LiteralPath (Canonical $candidate) -Force; while ($null -ne $cursor) { if (($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'reparse-unsafe' }; $cursor = $cursor.Parent } }
}
function State-Name { $bytes = [Text.Encoding]::UTF8.GetBytes("$OwnerSid`n$(Canonical $InstallRoot)"); $sha=[Security.Cryptography.SHA256]::Create(); try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() } }
function Protect-Key([string]$Path) {
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($rule in @($acl.Access)) { [void]$acl.RemoveAccessRuleAll($rule) }
    $inherit = [Security.AccessControl.InheritanceFlags]::ContainerInherit
    $propagate = [Security.AccessControl.PropagationFlags]::None
    $allow = [Security.AccessControl.AccessControlType]::Allow
    $acl.AddAccessRule([Security.AccessControl.RegistryAccessRule]::new('SYSTEM','FullControl',$inherit,$propagate,$allow))
    $acl.AddAccessRule([Security.AccessControl.RegistryAccessRule]::new('BUILTIN\Administrators','FullControl',$inherit,$propagate,$allow))
    $acl.AddAccessRule([Security.AccessControl.RegistryAccessRule]::new($OwnerSid,'ReadKey',$inherit,$propagate,$allow))
    Set-Acl -LiteralPath $Path -AclObject $acl
}
function Snapshot([string]$Name) {
    $snapshot = "$state\Rollback\$(State-Name)"
    $record = if (Test-Path "$registrations\$Name") { Get-ItemProperty -LiteralPath "$registrations\$Name" | Select-Object schemaVersion,installationId,ownerSid,installRoot,supportRoot } else { $null }
    New-Item -Force $snapshot | Out-Null; Protect-Key "$state\Rollback"; Protect-Key $snapshot
    New-ItemProperty -Force $snapshot installationId $Name | Out-Null; New-ItemProperty -Force $snapshot existed ([int]($null -ne $record)) | Out-Null
    if ($record) { foreach ($field in 'schemaVersion','installationId','ownerSid','installRoot','supportRoot') { New-ItemProperty -Force $snapshot $field $record.$field | Out-Null } }
    return $snapshot
}
function Install-Registration {
    Assert-Inputs
    New-Item -Force $registrations,$state | Out-Null
    Protect-Key $registrations; Protect-Key $state
    $stateName = State-Name
    $id = (Get-ItemProperty -LiteralPath "$state\$stateName" -Name installationId -ErrorAction SilentlyContinue).installationId
    if ($id -and $id -notmatch '^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$') { Fail 'invalid-protected-state' }
    if (-not $id) { $id = [guid]::NewGuid().ToString() }
    [void](Snapshot $id)
    New-Item -Force "$state\$stateName","$registrations\$id" | Out-Null
    New-ItemProperty -Force "$state\$stateName" installationId $id | Out-Null
    $values = @{schemaVersion=$schema;installationId=$id;ownerSid=$OwnerSid;installRoot=(Canonical $InstallRoot);supportRoot=(Canonical $SupportRoot)}
    foreach ($entry in $values.GetEnumerator()) { New-ItemProperty -Force "$registrations\$id" $entry.Key $entry.Value -PropertyType $(if ($entry.Key -eq 'schemaVersion') {'DWord'} else {'String'}) | Out-Null }
    Protect-Key "$state\$stateName"; Protect-Key "$registrations\$id"
}
function Uninstall-Registration {
    Assert-Inputs
    $stateName = State-Name
    $id = (Get-ItemProperty -LiteralPath "$state\$stateName" -Name installationId -ErrorAction SilentlyContinue).installationId
    if (-not $id) { return }
    [void](Snapshot $id)
    $record = Get-ItemProperty -LiteralPath "$registrations\$id" -ErrorAction SilentlyContinue
    if ($record -and ($record.ownerSid -ne $OwnerSid -or (Canonical $record.installRoot) -ine (Canonical $InstallRoot))) { Fail 'protected-state-mismatch' }
    Remove-Item -LiteralPath "$registrations\$id" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$state\$stateName" -Recurse -Force -ErrorAction SilentlyContinue
}
function Restore-Snapshot {
    Assert-Inputs
    $stateName = State-Name
    $snapshot="$state\Rollback\$stateName"; if (-not (Test-Path $snapshot)) { return }; $saved=Get-ItemProperty $snapshot
    if ($saved.existed -eq 0) { Remove-Item "$registrations\$($saved.installationId)" -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item "$state\$stateName" -Recurse -Force -ErrorAction SilentlyContinue }
    else { if ($saved.ownerSid -ne $OwnerSid -or (Canonical $saved.installRoot) -ine (Canonical $InstallRoot)) { Fail 'rollback-owner-mismatch' }; New-Item -Force "$state\$stateName","$registrations\$($saved.installationId)" | Out-Null; New-ItemProperty -Force "$state\$stateName" installationId $saved.installationId | Out-Null; foreach ($name in 'schemaVersion','installationId','ownerSid','installRoot','supportRoot') { New-ItemProperty -Force "$registrations\$($saved.installationId)" $name $saved.$name -PropertyType $(if ($name -eq 'schemaVersion') {'DWord'} else {'String'}) | Out-Null }; Protect-Key "$state\$stateName"; Protect-Key "$registrations\$($saved.installationId)" }
    Remove-Item -LiteralPath $snapshot -Recurse -Force
}

switch ($Mode) { 'Install' { Install-Registration } 'Uninstall' { Uninstall-Registration } 'Commit' { Assert-Inputs; Remove-Item -LiteralPath "$state\Rollback\$(State-Name)" -Recurse -Force -ErrorAction SilentlyContinue } default { Restore-Snapshot } }
