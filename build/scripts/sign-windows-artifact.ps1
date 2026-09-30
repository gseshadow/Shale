param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference = 'Stop'
if ($env:SHALE_WINDOWS_SIGNING_REQUIRED -ne 'true') {
    Write-Host "Signing disabled; developer artifact remains unsigned: $Path"
    exit 0
}
if (-not (Test-Path $Path)) { throw "Signing input is missing: $Path" }
if (-not $env:SHALE_SIGNTOOL_PATH) { throw 'SHALE_SIGNTOOL_PATH is required when production signing is enabled.' }
if (-not (Test-Path $env:SHALE_SIGNTOOL_PATH)) { throw 'Configured signtool is unavailable.' }
if (-not $env:SHALE_SIGN_TIMESTAMP_URL) { throw 'SHALE_SIGN_TIMESTAMP_URL is required for RFC3161 timestamping.' }
if (-not $env:SHALE_SIGN_EXPECTED_SUBJECT) { throw 'SHALE_SIGN_EXPECTED_SUBJECT is required for publisher verification.' }

$identity = @()
if ($env:SHALE_SIGN_CERT_THUMBPRINT) {
    $identity = @('/sha1', $env:SHALE_SIGN_CERT_THUMBPRINT)
} elseif ($env:SHALE_SIGN_CERT_PATH) {
    if (-not (Test-Path $env:SHALE_SIGN_CERT_PATH)) { throw 'Configured signing certificate file is unavailable.' }
    $identity = @('/f', $env:SHALE_SIGN_CERT_PATH)
    if ($env:SHALE_SIGN_CERT_PASSWORD) { $identity += @('/p', $env:SHALE_SIGN_CERT_PASSWORD) }
} else { throw 'A certificate thumbprint or certificate path is required when production signing is enabled.' }

& $env:SHALE_SIGNTOOL_PATH sign /fd SHA256 /tr $env:SHALE_SIGN_TIMESTAMP_URL /td SHA256 @identity $Path
if ($LASTEXITCODE -ne 0) { throw 'Authenticode signing failed.' }
& $env:SHALE_SIGNTOOL_PATH verify /pa /all $Path
if ($LASTEXITCODE -ne 0) { throw 'Authenticode verification failed.' }
$signature = Get-AuthenticodeSignature -FilePath $Path
if ($signature.Status -ne 'Valid') { throw 'Authenticode status is not Valid.' }
if (-not $signature.TimeStamperCertificate) { throw 'A valid RFC3161 timestamp is required.' }
if ($signature.SignerCertificate.Subject -notlike "*$($env:SHALE_SIGN_EXPECTED_SUBJECT)*") { throw 'Authenticode publisher identity does not match release policy.' }
Write-Host "Authenticode signature verified for $Path"
