param(
    [string]$MsiPath,
    [string]$ExpectedPublisher
)

$ErrorActionPreference = 'Stop'
$registrationDisplayRoot = 'HKLM\SOFTWARE\Shale\Installations'
$registrationRoot = 'Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Shale\Installations'
$script:requiredFailure = $false
$script:requiredIncomplete = $false

function Write-Result([ValidateSet('PASS','FAIL','NOT RUN','INFO')][string]$Status, [string]$Name, [string]$Value) {
    if ($Status -eq 'FAIL') { $script:requiredFailure = $true }
    Write-Output ("[{0}] {1}: {2}" -f $Status, $Name, $Value)
}

function Write-RequiredNotRun([string]$Name, [string]$Value) {
    $script:requiredIncomplete = $true
    Write-Result 'NOT RUN' $Name $Value
}

function Canonical-Path([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'empty path' }
    return [IO.Path]::GetFullPath($Path).TrimEnd('\')
}

function Test-Sid([string]$Sid) {
    return $Sid -match '^S-1-(?:\d+-){1,14}\d+$'
}

function Test-ReparseAncestor([string]$Path) {
    $cursor = Get-Item -LiteralPath (Canonical-Path $Path) -Force
    while ($null -ne $cursor) {
        if (($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { return $true }
        $cursor = $cursor.Parent
    }
    return $false
}

function Read-Properties([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $parts = $trimmed -split '=', 2
        if ($parts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0]) -or $values.ContainsKey($parts[0].Trim())) {
            throw 'malformed or duplicate property'
        }
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

function Test-SemanticVersion([string]$Version) {
    if ($Version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') { return $false }
    foreach ($part in $Version.Split('.')) { $number = 0; if (-not [int]::TryParse($part, [ref]$number)) { return $false } }
    return $true
}

function Get-IdentityValue($Rule) {
    try { return $Rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value }
    catch { return $Rule.IdentityReference.Value }
}

function Test-RegistrationAcl($Key, [string]$OwnerSid) {
    try {
        $acl = Get-Acl -LiteralPath $Key.PSPath
        $full = [Security.AccessControl.RegistryRights]::FullControl
        $read = [Security.AccessControl.RegistryRights]::ReadKey
        $writeMask = [Security.AccessControl.RegistryRights]::SetValue -bor [Security.AccessControl.RegistryRights]::CreateSubKey -bor [Security.AccessControl.RegistryRights]::Delete -bor [Security.AccessControl.RegistryRights]::ChangePermissions -bor [Security.AccessControl.RegistryRights]::TakeOwnership
        $systemFull = $false; $adminFull = $false; $ownerRead = $false; $unsafeWriter = $false
        $observed = @()
        foreach ($rule in $acl.Access) {
            $identity = Get-IdentityValue $rule
            $observed += ("{0} {1} {2} inherited={3}" -f $identity,$rule.AccessControlType,$rule.RegistryRights,$rule.IsInherited)
            if ($rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) { continue }
            $rights = [Security.AccessControl.RegistryRights]$rule.RegistryRights
            if ($identity -in @('S-1-5-18','NT AUTHORITY\SYSTEM') -and (($rights -band $full) -eq $full)) { $systemFull = $true; continue }
            if ($identity -in @('S-1-5-32-544','BUILTIN\Administrators') -and (($rights -band $full) -eq $full)) { $adminFull = $true; continue }
            if ($identity -eq $OwnerSid) {
                if (($rights -band $read) -eq $read -and ($rights -band $writeMask) -eq 0) { $ownerRead = $true }
                else { $unsafeWriter = $true }
                continue
            }
            if (($rights -band $writeMask) -ne 0) { $unsafeWriter = $true }
        }
        Write-Result INFO 'Registration ACL observed ACEs' ($observed -join '; ')
        if (-not $acl.AreAccessRulesProtected -or -not $systemFull -or -not $adminFull -or -not $ownerRead -or $unsafeWriter) {
            Write-Result FAIL 'Registration ACL result' 'protected SYSTEM/Administrators Full Control plus owner read-only model was not proven'
        } else {
            Write-Result PASS 'Registration ACL result' 'protected; SYSTEM and Administrators Full Control; owner read-only; no unrelated allow-write ACE observed'
        }
    } catch {
        Write-RequiredNotRun 'Registration ACL result' 'ACL could not be evaluated'
    }
}

function Write-SignatureResult([string]$Name, [string]$Path, [bool]$RequiredPublisher) {
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-Result 'NOT RUN' "$Name Authenticode status" 'artifact was not available'
        if ($RequiredPublisher) { Write-Result FAIL "$Name expected publisher result" 'artifact was not available' }
        return
    }
    try {
        $signature = Get-AuthenticodeSignature -LiteralPath $Path
        $subject = if ($null -ne $signature.SignerCertificate) { $signature.SignerCertificate.Subject } else { '(none)' }
        if ($signature.Status -eq 'Valid') { Write-Result PASS "$Name Authenticode status" "Valid; publisher=$subject" }
        elseif ($RequiredPublisher) { Write-Result FAIL "$Name Authenticode status" "$($signature.Status); production signing was requested" }
        else { Write-Result INFO "$Name Authenticode status" "$($signature.Status); production-signing acceptance NOT RUN" }
        if ($RequiredPublisher) {
            if ($signature.Status -eq 'Valid' -and $subject.IndexOf($ExpectedPublisher,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
                Write-Result PASS "$Name expected publisher result" "signed subject contains '$ExpectedPublisher'"
            } else { Write-Result FAIL "$Name expected publisher result" "signed subject does not contain the expected fragment '$ExpectedPublisher'" }
        }
    } catch {
        Write-Result 'NOT RUN' "$Name Authenticode status" 'signature could not be inspected'
        if ($RequiredPublisher) { Write-Result FAIL "$Name expected publisher result" 'publisher could not be inspected' }
    }
}

Write-Output 'Phase 13F Installed-Windows Acceptance'
Write-Output '========================================'

try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $currentSid = $identity.User.Value
    Write-Result INFO 'Windows user' $identity.Name
    Write-Result INFO 'Current user SID' $currentSid
} catch {
    Write-RequiredNotRun 'Windows user' 'Windows identity is unavailable'
    Write-RequiredNotRun 'Current user SID' 'Windows identity is unavailable'
    $currentSid = $null
}
Write-Result INFO 'Authoritative registration root' $registrationDisplayRoot

$records = @()
if (Test-Path -LiteralPath $registrationRoot) {
    try { $records = @(Get-ChildItem -LiteralPath $registrationRoot | ForEach-Object { [pscustomobject]@{ Key=$_; Values=(Get-ItemProperty -LiteralPath $_.PSPath) } }) }
    catch { Write-RequiredNotRun 'Number of registration records found' 'registry enumeration failed' }
} else {
    Write-Result FAIL 'Number of registration records found' '0; authoritative registration root is missing'
}
if (Test-Path -LiteralPath $registrationRoot) { Write-Result INFO 'Number of registration records found' $records.Count }

$duplicateIds = @($records | Group-Object { [string]$_.Values.installationId } | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Name) -and $_.Count -gt 1 })
if ($duplicateIds.Count -gt 0) { Write-Result FAIL 'Duplicate installation UUID check' (($duplicateIds | ForEach-Object Name) -join ', ') }
else { Write-Result PASS 'Duplicate installation UUID check' 'no duplicate installation UUIDs found' }

$expectedRoot = $null
if ($currentSid) {
    try {
        $profileKey = "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\$currentSid"
        $profile = (Get-ItemProperty -LiteralPath $profileKey -Name ProfileImagePath).ProfileImagePath
        $expectedRoot = Canonical-Path (Join-Path ([Environment]::ExpandEnvironmentVariables($profile)) 'AppData\Local\Shale')
    } catch { Write-RequiredNotRun 'Expected owner install root' 'ProfileList authority could not be read' }
}

$matches = @()
if ($currentSid -and $expectedRoot) {
    $matches = @($records | Where-Object {
        try { $_.Values.ownerSid -eq $currentSid -and (Canonical-Path $_.Values.installRoot) -ieq $expectedRoot -and (Canonical-Path $_.Values.supportRoot) -ieq $expectedRoot }
        catch { $false }
    })
}

$selected = $null
if (-not $currentSid -or -not $expectedRoot) {
    Write-RequiredNotRun 'Shale install root discovered' 'owner authority was unavailable; no record was selected'
} elseif ($matches.Count -eq 0) {
    Write-Result FAIL 'Shale install root discovered' "no registration matched current owner and expected root $expectedRoot"
} elseif ($matches.Count -gt 1) {
    Write-Result FAIL 'Shale install root discovered' "$($matches.Count) registrations ambiguously matched current owner and expected root; no record was selected"
} else {
    $selected = $matches[0]
    Write-Result PASS 'Shale install root discovered' $expectedRoot
}

if ($null -eq $selected) {
    foreach ($field in 'Matching installation UUID','Registration schema version','Registered owner SID','Owner SID correctness','Registered install root','Install-root correctness','Registered support root','Support-root correctness','Registration ACL result','Installed-version metadata path','Installed-version metadata schema','Installed-version metadata version','Installed-version metadata channel','Updater executable path','Updater path existence','Phase 12 attempt-store path','Phase 13B execution-lock path','Update evidence/log path') {
        Write-RequiredNotRun $field 'no unique current-owner registration was selected'
    }
    Write-Result 'NOT RUN' 'Production registration-reader classification' 'no unique current-owner registration was selected'
    Write-Result 'NOT RUN' 'Runtime/application version' 'no unique current-owner registration was selected'
    Write-Result 'NOT RUN' 'Shale.exe Authenticode status' 'no unique current-owner registration was selected'
    Write-Result 'NOT RUN' 'ShaleUpdater.exe Authenticode status' 'no unique current-owner registration was selected'
} else {
    $r = $selected.Values
    $keyName = $selected.Key.PSChildName
    $uuidValid = $false
    try { $parsedId = [guid]::Parse([string]$r.installationId); $uuidValid = $parsedId.ToString() -ieq $keyName }
    catch { $uuidValid = $false }
    if ($uuidValid) { Write-Result PASS 'Matching installation UUID' ([string]$r.installationId) }
    else { Write-Result FAIL 'Matching installation UUID' 'value is not a canonical UUID matching its registration key' }
    if ($r.schemaVersion -eq 1) { Write-Result PASS 'Registration schema version' '1' } else { Write-Result FAIL 'Registration schema version' "$($r.schemaVersion); supported schema is 1" }
    Write-Result INFO 'Registered owner SID' ([string]$r.ownerSid)
    if ((Test-Sid $r.ownerSid) -and $r.ownerSid -eq $currentSid) { Write-Result PASS 'Owner SID correctness' 'valid SID matching the current Windows owner' }
    else { Write-Result FAIL 'Owner SID correctness' 'SID is invalid or does not match the current Windows owner' }
    Write-Result INFO 'Registered install root' ([string]$r.installRoot)
    Write-Result INFO 'Registered support root' ([string]$r.supportRoot)
    $installOk = $false; $supportOk = $false
    try { $installOk = (Canonical-Path $r.installRoot) -ieq $expectedRoot -and (Test-Path -LiteralPath $r.installRoot -PathType Container) -and -not (Test-ReparseAncestor $r.installRoot) -and (Test-Path -LiteralPath (Join-Path $r.installRoot 'Shale.exe') -PathType Leaf) -and (Test-Path -LiteralPath (Join-Path $r.installRoot 'app') -PathType Container) } catch {}
    try { $supportOk = (Canonical-Path $r.supportRoot) -ieq $expectedRoot -and (Canonical-Path $r.supportRoot) -ieq (Canonical-Path $r.installRoot) -and (Test-Path -LiteralPath $r.supportRoot -PathType Container) -and -not (Test-ReparseAncestor $r.supportRoot) } catch {}
    if ($installOk) { Write-Result PASS 'Install-root correctness' 'owner-derived canonical root exists with the expected Shale layout and no reparse ancestor' } else { Write-Result FAIL 'Install-root correctness' 'owner/path/layout/reparse invariant was not satisfied' }
    if ($supportOk) { Write-Result PASS 'Support-root correctness' 'equals the owner-derived canonical Shale root and has no reparse ancestor' } else { Write-Result FAIL 'Support-root correctness' 'owner/path/reparse invariant was not satisfied' }
    Test-RegistrationAcl $selected.Key $r.ownerSid
    Write-Result 'NOT RUN' 'Production registration-reader classification' 'the existing Java reader has no installed command-line entry point'

    $updater = Join-Path $r.installRoot 'app\updater\ShaleUpdater.exe'
    $metadata = Join-Path $r.installRoot 'app\shale-installed-version.properties'
    $attempts = Join-Path $r.supportRoot 'update-attempts'
    $lock = Join-Path $r.supportRoot 'updates\update-execution.lock'
    $logs = Join-Path $r.supportRoot 'logs\updates'
    Write-Result INFO 'Installed-version metadata path' $metadata
    $metadataVersion = $null
    if (-not (Test-Path -LiteralPath $metadata -PathType Leaf)) {
        Write-Result FAIL 'Installed-version metadata schema' 'metadata file is missing'
        Write-Result 'NOT RUN' 'Installed-version metadata version' 'metadata file is missing'
        Write-Result 'NOT RUN' 'Installed-version metadata channel' 'metadata file is missing'
    } else {
        try {
            $properties = Read-Properties $metadata
            if ($properties.schemaVersion -eq '1') { Write-Result PASS 'Installed-version metadata schema' '1' } else { Write-Result FAIL 'Installed-version metadata schema' "$($properties.schemaVersion); supported schema is 1" }
            if (Test-SemanticVersion $properties.version) { $metadataVersion=$properties.version; Write-Result PASS 'Installed-version metadata version' $metadataVersion } else { Write-Result FAIL 'Installed-version metadata version' 'not a strict canonical major.minor.build version' }
            if ($properties.channel -ceq 'PRODUCTION') { Write-Result PASS 'Installed-version metadata channel' 'PRODUCTION' } else { Write-Result FAIL 'Installed-version metadata channel' "$($properties.channel); canonical channel is PRODUCTION" }
        } catch {
            Write-Result FAIL 'Installed-version metadata schema' 'properties file is malformed'
            Write-Result 'NOT RUN' 'Installed-version metadata version' 'properties file could not be parsed'
            Write-Result 'NOT RUN' 'Installed-version metadata channel' 'properties file could not be parsed'
        }
    }
    if ($metadataVersion) { Write-Result INFO 'Runtime/application version' "$metadataVersion (installed payload metadata authority; UI was not launched)" }
    else { Write-Result 'NOT RUN' 'Runtime/application version' 'trusted installed payload metadata was unavailable' }
    Write-Result INFO 'Updater executable path' $updater
    if (Test-Path -LiteralPath $updater -PathType Leaf) { Write-Result PASS 'Updater path existence' 'file exists' } else { Write-Result FAIL 'Updater path existence' 'file is missing' }
    Write-Result INFO 'Phase 12 attempt-store path' $attempts
    Write-Result INFO 'Phase 13B execution-lock path' $lock
    Write-Result INFO 'Update evidence/log path' $logs
    Write-SignatureResult 'Shale.exe' (Join-Path $r.installRoot 'Shale.exe') ([bool]$ExpectedPublisher)
    Write-SignatureResult 'ShaleUpdater.exe' $updater ([bool]$ExpectedPublisher)
}

if ($MsiPath) { Write-SignatureResult 'MSI' (Canonical-Path $MsiPath) ([bool]$ExpectedPublisher) }
else { Write-Result 'NOT RUN' 'MSI Authenticode status' 'supply -MsiPath to inspect an MSI' }
if (-not $ExpectedPublisher) { Write-Result 'NOT RUN' 'Expected publisher result' 'supply -ExpectedPublisher for signed-release publisher acceptance' }

if ($script:requiredFailure) {
    Write-Result INFO 'Overall fresh-install acceptance result' 'FAIL'
    exit 1
}
if ($script:requiredIncomplete) {
    Write-Result INFO 'Overall fresh-install acceptance result' 'NOT RUN (one or more required observations could not be determined)'
    exit 0
}
Write-Result PASS 'Overall fresh-install acceptance result' 'PASS (all required non-destructive invariants passed; NOT RUN items require optional input or separate tooling)'
exit 0
