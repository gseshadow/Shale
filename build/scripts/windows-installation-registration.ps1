$ErrorActionPreference = 'Stop'
$Mode=@{I='Install';U='Uninstall';R='RollbackInstall';B='RollbackUninstall';C='Commit'}[$env:M]
$OwnerSid=$env:O; $InstallRoot=$env:I; $SupportRoot=$env:S
if ($Mode -notin @('Install','Uninstall','RollbackInstall','RollbackUninstall','Commit')) { throw 'Shale installation registration rejected: invalid-mode' }

$baseSub = 'SOFTWARE\Shale'
$registrationsSub = "$baseSub\Installations"
$stateSub = "$baseSub\InstallerState"
$schema = 1

function Canonical([string]$Value) { return [IO.Path]::GetFullPath($Value).TrimEnd('\') }
function Fail([string]$Code) { throw "Shale installation registration rejected: $Code" }
function Open-Hklm64([bool]$Writable) {
    $view = [Microsoft.Win32.RegistryView]::Registry64
    return [Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine,$view)
}
function Ensure-Key([string]$SubKey) {
    $root = Open-Hklm64 $true
    try {
        $key = $root.CreateSubKey($SubKey,[Microsoft.Win32.RegistryKeyPermissionCheck]::ReadWriteSubTree)
        if ($null -eq $key) { Fail 'registry-create-failed' }
        $key.Dispose()
    } finally { $root.Dispose() }
}
function Test-Key([string]$SubKey) {
    $root = Open-Hklm64 $false
    try {
        $key = $root.OpenSubKey($SubKey,$false)
        if ($null -eq $key) { return $false }
        $key.Dispose(); return $true
    } finally { $root.Dispose() }
}
function Get-Value([string]$SubKey,[string]$Name) {
    $root = Open-Hklm64 $false
    try {
        $key = $root.OpenSubKey($SubKey,$false)
        if ($null -eq $key) { return $null }
        try { return $key.GetValue($Name,$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames) }
        finally { $key.Dispose() }
    } finally { $root.Dispose() }
}
function Set-Value([string]$SubKey,[string]$Name,$Value,[Microsoft.Win32.RegistryValueKind]$Kind) {
    Ensure-Key $SubKey
    $root = Open-Hklm64 $true
    try {
        $key = $root.OpenSubKey($SubKey,$true)
        if ($null -eq $key) { Fail 'registry-open-failed' }
        try { $key.SetValue($Name,$Value,$Kind) }
        finally { $key.Dispose() }
    } finally { $root.Dispose() }
}
function Delete-Key([string]$SubKey) {
    $root = Open-Hklm64 $true
    try { $root.DeleteSubKeyTree($SubKey,$false) }
    catch [System.ArgumentException] { }
    finally { $root.Dispose() }
}
function Protect-Key([string]$SubKey) {
    Ensure-Key $SubKey
    $root = Open-Hklm64 $true
    try {
        $key = $root.OpenSubKey($SubKey,[Microsoft.Win32.RegistryKeyPermissionCheck]::ReadWriteSubTree,[System.Security.AccessControl.RegistryRights]::ChangePermissions)
        if ($null -eq $key) { Fail 'registry-acl-open-failed' }
        try {
            $security = [System.Security.AccessControl.RegistrySecurity]::new()
            $security.SetAccessRuleProtection($true,$false)
            $inherit = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit
            $propagate = [System.Security.AccessControl.PropagationFlags]::None
            $allow = [System.Security.AccessControl.AccessControlType]::Allow
            $systemSid = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')
            $administratorsSid = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
            $ownerIdentity = [System.Security.Principal.SecurityIdentifier]::new($OwnerSid)
            $security.AddAccessRule([System.Security.AccessControl.RegistryAccessRule]::new($systemSid,[System.Security.AccessControl.RegistryRights]::FullControl,$inherit,$propagate,$allow))
            $security.AddAccessRule([System.Security.AccessControl.RegistryAccessRule]::new($administratorsSid,[System.Security.AccessControl.RegistryRights]::FullControl,$inherit,$propagate,$allow))
            $security.AddAccessRule([System.Security.AccessControl.RegistryAccessRule]::new($ownerIdentity,[System.Security.AccessControl.RegistryRights]::ReadKey,$inherit,$propagate,$allow))
            $key.SetAccessControl($security)
        } finally { $key.Dispose() }
    } finally { $root.Dispose() }
}
function Assert-Inputs([bool]$RequireInstallRoot = $true) {
    if ($OwnerSid -notmatch '^S-1-(?:\d+-){1,14}\d+$') { Fail 'invalid-owner' }
    $profileSub = "SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\$OwnerSid"
    $profile = Get-Value $profileSub 'ProfileImagePath'
    if (-not $profile) { Fail 'owner-profile-missing' }
    $expected = Canonical (Join-Path ([Environment]::ExpandEnvironmentVariables([string]$profile)) 'AppData\Local\Shale')
    if ((Canonical $InstallRoot) -ine $expected -or (Canonical $SupportRoot) -ine $expected) { Fail 'owner-path-mismatch' }
    foreach ($candidate in @($InstallRoot,$SupportRoot)) {
        $canonicalCandidate = Canonical $candidate
        if ($RequireInstallRoot -and -not (Test-Path -LiteralPath $canonicalCandidate)) { Fail 'install-root-missing' }
        $cursorPath = $canonicalCandidate
        while (-not (Test-Path -LiteralPath $cursorPath)) {
            $parentPath = Split-Path -Parent $cursorPath
            if (-not $parentPath -or $parentPath -eq $cursorPath) { Fail 'path-ancestor-missing' }
            $cursorPath = $parentPath
        }
        $cursor = Get-Item -LiteralPath $cursorPath -Force
        while ($null -ne $cursor) {
            if (($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'reparse-unsafe' }
            $cursor = $cursor.Parent
        }
    }
}
function State-Name {
    $bytes = [Text.Encoding]::UTF8.GetBytes("$OwnerSid`n$(Canonical $InstallRoot)")
    $sha=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Snapshot([string]$Name) {
    $stateName = State-Name
    $snapshotSub = "$stateSub\Rollback\$stateName"
    $recordSub = "$registrationsSub\$Name"
    $exists = Test-Key $recordSub
    Ensure-Key "$stateSub\Rollback"
    Ensure-Key $snapshotSub
    Protect-Key "$stateSub\Rollback"
    Protect-Key $snapshotSub
    Set-Value $snapshotSub 'installationId' $Name ([Microsoft.Win32.RegistryValueKind]::String)
    Set-Value $snapshotSub 'existed' ([int]$exists) ([Microsoft.Win32.RegistryValueKind]::DWord)
    if ($exists) {
        foreach ($field in 'schemaVersion','installationId','ownerSid','installRoot','supportRoot') {
            $value = Get-Value $recordSub $field
            if ($null -ne $value) {
                $kind = if ($field -eq 'schemaVersion') { [Microsoft.Win32.RegistryValueKind]::DWord } else { [Microsoft.Win32.RegistryValueKind]::String }
                Set-Value $snapshotSub $field $value $kind
            }
        }
    }
    return $snapshotSub
}
function Install-Registration {
    Assert-Inputs
    Ensure-Key $baseSub
    Ensure-Key $registrationsSub
    Ensure-Key $stateSub
    Protect-Key $registrationsSub
    Protect-Key $stateSub
    $stateName = State-Name
    $stateRecordSub = "$stateSub\$stateName"
    $id = Get-Value $stateRecordSub 'installationId'
    if ($id -and ([string]$id) -notmatch '^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$') { Fail 'invalid-protected-state' }
    if (-not $id) { $id = [guid]::NewGuid().ToString() }
    [void](Snapshot ([string]$id))
    $registrationSub = "$registrationsSub\$id"
    Ensure-Key $stateRecordSub
    Ensure-Key $registrationSub
    Set-Value $stateRecordSub 'installationId' ([string]$id) ([Microsoft.Win32.RegistryValueKind]::String)
    Set-Value $registrationSub 'schemaVersion' $schema ([Microsoft.Win32.RegistryValueKind]::DWord)
    Set-Value $registrationSub 'installationId' ([string]$id) ([Microsoft.Win32.RegistryValueKind]::String)
    Set-Value $registrationSub 'ownerSid' $OwnerSid ([Microsoft.Win32.RegistryValueKind]::String)
    Set-Value $registrationSub 'installRoot' (Canonical $InstallRoot) ([Microsoft.Win32.RegistryValueKind]::String)
    Set-Value $registrationSub 'supportRoot' (Canonical $SupportRoot) ([Microsoft.Win32.RegistryValueKind]::String)
    Protect-Key $stateRecordSub
    Protect-Key $registrationSub
}
function Uninstall-Registration {
    Assert-Inputs $false
    $stateName = State-Name
    $stateRecordSub = "$stateSub\$stateName"
    $id = Get-Value $stateRecordSub 'installationId'
    if (-not $id) { return }
    [void](Snapshot ([string]$id))
    $registrationSub = "$registrationsSub\$id"
    if (Test-Key $registrationSub) {
        $recordOwner = Get-Value $registrationSub 'ownerSid'
        $recordInstall = Get-Value $registrationSub 'installRoot'
        if ($recordOwner -ne $OwnerSid -or (Canonical ([string]$recordInstall)) -ine (Canonical $InstallRoot)) { Fail 'protected-state-mismatch' }
    }
    Delete-Key $registrationSub
    Delete-Key $stateRecordSub
}
function Restore-Snapshot {
    Assert-Inputs $false
    $stateName = State-Name
    $snapshotSub = "$stateSub\Rollback\$stateName"
    if (-not (Test-Key $snapshotSub)) { return }
    $id = [string](Get-Value $snapshotSub 'installationId')
    $existed = [int](Get-Value $snapshotSub 'existed')
    $stateRecordSub = "$stateSub\$stateName"
    $registrationSub = "$registrationsSub\$id"
    if ($existed -eq 0) {
        Delete-Key $registrationSub
        Delete-Key $stateRecordSub
    } else {
        $savedOwner = [string](Get-Value $snapshotSub 'ownerSid')
        $savedInstall = [string](Get-Value $snapshotSub 'installRoot')
        if ($savedOwner -ne $OwnerSid -or (Canonical $savedInstall) -ine (Canonical $InstallRoot)) { Fail 'rollback-owner-mismatch' }
        Ensure-Key $stateRecordSub
        Ensure-Key $registrationSub
        Set-Value $stateRecordSub 'installationId' $id ([Microsoft.Win32.RegistryValueKind]::String)
        foreach ($name in 'schemaVersion','installationId','ownerSid','installRoot','supportRoot') {
            $value = Get-Value $snapshotSub $name
            $kind = if ($name -eq 'schemaVersion') { [Microsoft.Win32.RegistryValueKind]::DWord } else { [Microsoft.Win32.RegistryValueKind]::String }
            Set-Value $registrationSub $name $value $kind
        }
        Protect-Key $stateRecordSub
        Protect-Key $registrationSub
    }
    Delete-Key $snapshotSub
}

switch ($Mode) {
    'Install' { Install-Registration }
    'Uninstall' { Uninstall-Registration }
    'Commit' { Delete-Key "$stateSub\Rollback\$(State-Name)" }
    default { Restore-Snapshot }
}
