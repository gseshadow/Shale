#requires -Version 5.1
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'session-update.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Launcher parse failed'}
# Load pure helpers only: never evaluate launch, stage, state or credential prompts.
foreach($name in @('Hash','ReadJson','Assert-Tree','Quote','Assert-SandboxProperties','Assert-AuthorityReceipt')){
    $node=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)
    if(-not $node){throw "Missing helper $name"}
    . ([scriptblock]::Create($node.Extent.Text))
}
$root=Join-Path ([IO.Path]::GetTempPath()) ('shale-session-update-test-'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path (Join-Path $root 'classes')
$null=New-Item -ItemType Directory -Path (Join-Path $root 'sources')
$file=Join-Path $root 'classes/example.class'
[IO.File]::WriteAllText($file,'synthetic class bytes')
$records=@(@{Path='classes/example.class';Sha256=(Hash $file)})
Assert-Tree $root $records @('classes','sources')
function Refuses([scriptblock]$action){$refused=$false;try{& $action}catch{$refused=$true};if(-not $refused){throw 'Unsafe artifact accepted'}}
$pinned='59d8374858764788c621f6f8d986773b58ab79c4'
$props="applicationRevision=$pinned`r`ndatabase=ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c`r`nsqlPort=14333`r`n"
Assert-SandboxProperties $props
Assert-SandboxProperties ($props.Replace("`r`n","`n"))
Refuses {Assert-SandboxProperties ($props.Replace('sqlPort=14333','sqlPort=1433'))}
Refuses {Assert-SandboxProperties ($props.Replace($pinned,('0'*40)))}
Refuses {Assert-Tree $root @($records[0],$records[0]) @()}
Refuses {Assert-Tree $root @(@{Path='../outside';Sha256='bad'}) @()}
Refuses {Assert-Tree $root @(@{Path=[IO.Path]::GetFullPath($file);Sha256='bad'}) @()}
[IO.File]::WriteAllText((Join-Path $root 'sources/unexpected.java'),'extra')
Refuses {Assert-Tree $root $records @('classes','sources')}
Remove-Item -LiteralPath (Join-Path $root 'sources/unexpected.java')
[IO.File]::WriteAllText($file,'changed synthetic bytes')
Refuses {Assert-Tree $root $records @()}
# New ownership overlay binds all retained artifacts; a receipt cannot bless changed parents.
[IO.File]::WriteAllText($file,'synthetic class bytes')
$ReviewedRevision='a'*40; $state=$root
$manifestHash='B'*64; $startupReceiptHash='C'*64; $sessionReceiptHash='D'*64
$receipt=@{Schema=1;Revision=$ReviewedRevision;State=$state;OriginalManifestSha256=$manifestHash;StartupReceiptSha256=$startupReceiptHash;SessionReceiptSha256=$sessionReceiptHash;Files=$records}
Assert-AuthorityReceipt $receipt $root
foreach($field in @('Schema','Revision','State','OriginalManifestSha256','StartupReceiptSha256','SessionReceiptSha256')) {
    $old=$receipt[$field];$receipt[$field]='changed'
    Refuses {Assert-AuthorityReceipt $receipt $root}
    $receipt[$field]=$old
}
Assert-AuthorityReceipt $receipt $root
# Test the argument transport through a real child, including spaces, quotes and trailing backslashes.
$echo=Join-Path $root 'echo.ps1'
[IO.File]::WriteAllText($echo,'ConvertTo-Json -InputObject @($args) -Compress')
$values=@('space in path','quote"inside','C:\Program Files\JDK\','one\"two','plain')
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=(Get-Process -Id $PID).Path;$info.UseShellExecute=$false;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$all=@('-NoLogo','-NoProfile','-File',$echo)+$values
$info.Arguments=(@($all|ForEach-Object {Quote $_}) -join ' ')
$child=[Diagnostics.Process]::Start($info);$output=$child.StandardOutput.ReadToEnd();$failure=$child.StandardError.ReadToEnd();$child.WaitForExit()
if($child.ExitCode){throw 'Argument transport child failed'}
$actual=ConvertFrom-Json -InputObject $output
if(@($actual).Count -ne $values.Count){throw 'Argument count changed'}
for($i=0;$i -lt $values.Count;$i++){if($actual[$i] -cne $values[$i]){throw 'Argument value changed'}}
Write-Host "PASS parse, manifest integrity/count/path refusal, authority receipt parent bindings/tamper rejection, argument transport. Retained synthetic evidence: $root"
Write-Host ('Native Windows PowerShell 5.1 coverage='+($env:OS -eq 'Windows_NT' -and $PSVersionTable.PSVersion.Major -eq 5))
