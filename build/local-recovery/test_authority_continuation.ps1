#requires -Version 5.1
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repo
$text=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'authority-recovery.md'))
$blocks=[regex]::Matches($text,'(?s)```powershell\r?\n(.*?)\r?\n```')
foreach($block in $blocks){
    $tokens=$null;$errors=$null
    $null=[Management.Automation.Language.Parser]::ParseInput($block.Groups[1].Value,[ref]$tokens,[ref]$errors)
    if($errors.Count){throw 'Windows continuation block syntax failed'}
}
# Run only the payload-generation block with synthetic local files. No SQL, credentials,
# launcher, correction, application or exercise execution. Real production helpers/extraction.
$root=Join-Path ([IO.Path]::GetTempPath()) ('ShaleAuthorityPayload-'+[guid]::NewGuid().ToString('N'))
$state=Join-Path $root 'state';$revision='a'*40
$overlay=Join-Path $state ('authority-updates/'+$revision)
$null=New-Item -ItemType Directory -Path $overlay
$before='0xC584A399EA216FF10DD0E3C28AEDC8CC306DDB42606C93009F4C75123DC502AF'
$corrected='0x'+('AB'*32)
$result=@{BeforeHash=$before;CorrectedBaseline=$corrected;SqlResults=@(@{Outcome='NOT_GRANTED'},@{CheckName='LocalOwnershipCorrected';StateHash=$corrected})}
$resultPath=Join-Path $overlay 'correction-result.json'
[IO.File]::WriteAllText($resultPath,($result | ConvertTo-Json -Depth 8))
$originalDigest=(Get-FileHash -LiteralPath $resultPath -Algorithm SHA256).Hash
function notepad.exe {param($Path)}
$generation=@($blocks | Where-Object {$_.Groups[1].Value.Contains('$anchor=[regex]::Matches')})
if($generation.Count -ne 1){throw 'One payload generation block required'}
. ([scriptblock]::Create($generation[0].Groups[1].Value))
$generated=[IO.File]::ReadAllText($exercise)
if(-not $generated.Contains("if (`$before -ine '$corrected')")){throw 'Generated exercise did not pin recorded corrected baseline'}
if($generated.Contains("if (`$before -ine '$before')")){throw 'Old failed baseline was adopted for corrected exercise'}
$tokens=$null;$errors=$null
$null=[Management.Automation.Language.Parser]::ParseInput($generated,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Generated exercise syntax failed'}
if((Get-FileHash -LiteralPath $resultPath -Algorithm SHA256).Hash -cne $originalDigest){throw 'Payload generation changed original correction evidence'}
if((Get-Item -LiteralPath $exercise).Length -eq 0){throw 'Missing generated exercise'}
Write-Host ('PASS all continuation blocks parse; real payload generation binds recorded corrected baseline, parses and preserves original result. No exercise/SQL executed. Retained synthetic evidence: '+$root)
