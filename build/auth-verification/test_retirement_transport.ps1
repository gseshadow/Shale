# Real child-process regression, with synthetic output only. Never connects to SQL.
param([string]$RunnerPath)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if (-not $RunnerPath) { $RunnerPath=Join-Path $PSScriptRoot '../../docs/sql/auth/permission-cutover/retirement-operator.ps1' }
. $RunnerPath
Write-Host 'OFFLINE SYNTHETIC PROCESS TEST: no SQL connection; all phase/commit text below is fake-child output.'
$script:realTransport=${function:Invoke-PacketSql}
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('retirement-streams '+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $testRoot
$testPassed=$false
$savedIni=[Environment]::GetEnvironmentVariable('SQLCMDINI','Process')
try {
    $isWindowsHost=$env:OS -eq 'Windows_NT'
    if ($isWindowsHost) {
        if ($PSVersionTable.PSEdition -ne 'Desktop') { throw 'Run this Windows regression in native Windows PowerShell 5.1, not Core.' }
        $fake=Join-Path $testRoot 'synthetic-sqlcmd.exe'
        $source=@'
using System;
using System.IO;
using System.Text;
public static class SyntheticSqlcmd {
    public static int Main(string[] args) {
        if (Environment.GetEnvironmentVariable("SQLCMDPASSWORD") != "SYNTHETIC_ONLY_PASSWORD" ||
            Environment.GetEnvironmentVariable("SQLCMDINI") != null || Array.IndexOf(args,"-P") >= 0) return 87;
        string output=args[Array.IndexOf(args,"-o")+1];
        string input=args[Array.IndexOf(args,"-i")+1];
        string before="0x"+new string('A',64), after="0x"+new string('C',64);
        string text=input.Contains("controlled-retire-") ? "LegacyRetirementCommitted "+before+" "+after :
            input.Contains("controlled-rollback-") ? "LegacyRetirementRolledBack "+before :
            input.Contains("baseline.sql") ? "LegacyRetirementBaseline "+before : "OperatorCatalogVerified";
        File.WriteAllText(output,text+"\r\n",Encoding.Unicode);
        Console.Out.Write(new string('O',131072));
        Console.Error.Write(new string('E',131072));
        return 0;
    }
}
'@
        # Compile in a separate native host so a loaded generated assembly cannot
        # lock the fake executable against this test's exact-directory cleanup.
        $sourcePath=Join-Path $testRoot 'synthetic.cs'
        Write-NewText $sourcePath $source
        $compiler=Join-Path $testRoot 'compile.ps1'
        Write-NewText $compiler 'param($Source,$Output) $ErrorActionPreference="Stop"; Add-Type -TypeDefinition ([IO.File]::ReadAllText($Source)) -OutputAssembly $Output -OutputType ConsoleApplication'
        $savedPreference=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            $compilerOutput=@(& (Get-Process -Id $PID).Path -NoLogo -NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File $compiler -Source $sourcePath -Output $fake 2>&1)
            $compilerExit=$LASTEXITCODE
        } finally { $ErrorActionPreference=$savedPreference }
        $compilerLog=Join-Path $testRoot 'compiler.txt'
        [IO.File]::WriteAllLines($compilerLog,[string[]]@($compilerOutput | ForEach-Object { $_.ToString() }))
        if ($compilerExit -ne 0 -or -not (Test-Path -LiteralPath $fake -PathType Leaf)) {
            foreach ($line in $compilerOutput) { Write-Host $line.ToString() }
            throw "Synthetic Windows executable compilation failed: exit=$compilerExit; retained $compilerLog"
        }
    } else {
        $fake=Join-Path $testRoot 'synthetic-sqlcmd'
        $body=@'
#!/usr/bin/env python3
import os, pathlib, sys
args = sys.argv[1:]
assert os.environ.get('SQLCMDPASSWORD') == 'SYNTHETIC_ONLY_PASSWORD'
assert 'SQLCMDINI' not in os.environ and '-P' not in args
output = pathlib.Path(args[args.index('-o') + 1])
source = args[args.index('-i') + 1]
before, after = '0x' + 'A' * 64, '0x' + 'C' * 64
if 'controlled-retire-' in source:
    text = 'LegacyRetirementCommitted ' + before + ' ' + after
elif 'controlled-rollback-' in source:
    text = 'LegacyRetirementRolledBack ' + before
elif 'baseline.sql' in source:
    text = 'LegacyRetirementBaseline ' + before
else:
    text = 'OperatorCatalogVerified'
output.write_text(text + '\r\n', encoding='utf-16')
sys.stdout.write('O' * 131072)
sys.stderr.write('E' * 131072)
'@
        [IO.File]::WriteAllText($fake,$body.Replace("`r`n","`n"),[Text.UTF8Encoding]::new($false))
        & chmod 700 $fake
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic executable setup failed' }
    }
    # Keep the real process/stream/password/exit transport. Only metadata and human
    # prompts are synthetic; the shipping runner has no bypass or fake transport.
    function Invoke-PacketSql {
        param($Root,$Server,$Database,$User,$Password,$InputFile,$Variables,$Log,$Executable,$Deadline)
        $items=@(& $script:realTransport $Root $Server $Database $User $Password $InputFile $Variables $Log $Executable $Deadline)
        if ($items.Count -ne 1 -or $items[0] -isnot [string] -or $items[0] -cne ($Log+'.sqlcmd.txt')) {
            throw 'Transport must return exactly one string log path; async completion values must not enter the success pipeline.'
        }
        foreach ($suffix in @('.stdout.bin','.stderr.bin')) {
            if ((Get-Item -LiteralPath ($Log+$suffix)).Length -ne 131072) { throw 'Real child output was truncated' }
        }
        return $items[0]
    }
    function Assert-SupportedSqlcmd { param($Executable) return $Executable }
    function Read-Packet { return $script:syntheticPacket }
    function Read-Host {
        param($Prompt,[switch]$AsSecureString)
        if ($AsSecureString) { return (ConvertTo-SecureString 'SYNTHETIC_ONLY_PASSWORD' -AsPlainText -Force) }
        if ($Prompt -match 'Type exactly: (.+)$') { return $Matches[1] }
        if ($Prompt -like '*Observed safe*') { return 'PASSED' }
        return 'SYNTHETIC-TEST-RECORD'
    }
    $PacketPath=Join-Path $testRoot 'synthetic [packet]'
    $null=New-Item -ItemType Directory -Path (Join-Path $PacketPath 'source') -Force
    $before='0x'+('A'*64); $after='0x'+('C'*64)
    $script:syntheticPacket=[pscustomobject]@{BeforeHash=$before; Server='sql.invalid'; Database='Shale'; User='syntheticoperator'}
    Write-NewJson (Join-Path $PacketPath 'packet.json') $script:syntheticPacket
    Write-NewText (Join-Path $PacketPath 'packet-seal.txt') (Get-Digest (Join-Path $PacketPath 'packet.json'))
    Write-NewText (Join-Path $PacketPath 'BeforeHash.txt') $before
    $ready=[ordered]@{}
    foreach ($key in @('IdentityAuthority','KnownArtifacts','DesktopPassword','DesktopRemembered','ApiPassword','ApiRemembered','NeighboringWork','OperationalReadiness','ExactSqlcmdRehearsal','ManualRecovery')) {
        $ready[$key]=@{Status='PASSED'; Evidence='SYNTHETIC-TEST-RECORD'; ScopeAndLimits='Synthetic process test only'}
    }
    $ready.MeasuredRecoverySeconds=600
    $ready.KnownCallerDisposition='NO_IDENTIFIED_UNRESOLVED_CALLER'
    $ready.FollowUp=@{Owner='Brian Downing'; DailyReviewThroughUtc=[datetime]::UtcNow.AddDays(8).ToString('yyyy-MM-ddTHH:mm:ssZ'); KnownDueRuns='Synthetic test only'; ReopenedRiskReviewUtc=[datetime]::UtcNow.AddDays(9).ToString('yyyy-MM-ddTHH:mm:ssZ')}
    Write-NewJson (Join-Path $PacketPath 'readiness.json') $ready
    $SqlcmdPath=$fake
    $env:SQLCMDINI='MUST_NOT_REACH_CHILD'
    $WindowStartUtc=[datetime]::UtcNow.AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $WindowEndUtc=[datetime]::UtcNow.AddMinutes(28).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $RollbackStartByUtc=[datetime]::UtcNow.AddMinutes(15).ToString('yyyy-MM-ddTHH:mm:ssZ')
    Write-Host 'OFFLINE SYNTHETIC phase: Apply'
    $Phase='Apply'; Invoke-Retirement
    if ((Get-Content -LiteralPath (Join-Path $PacketPath 'AfterHash.txt') -Raw) -cne $after) { throw 'Original synthetic commit identity lost' }
    Write-Host 'OFFLINE SYNTHETIC phase: Verify'
    $Phase='Verify'; Invoke-Retirement
    $checks=@(Get-ChildItem -LiteralPath $PacketPath -Filter 'verify-*.checks.json')
    if ($checks.Count -ne 1 -or (Read-Json $checks[0].FullName).CatalogOutput -isnot [string]) { throw 'Verify did not persist one string catalog path' }
    Write-Host 'OFFLINE SYNTHETIC phase: Rollback'
    $Phase='Rollback'; Invoke-Retirement
    $restored=Read-Json (Join-Path $PacketPath 'rolled-back.json')
    if ($restored.BeforeHash -cne $before -or $restored.Output -isnot [string]) { throw 'Rollback restoration marker/path lost after SQL success' }
    Write-Host 'OFFLINE SYNTHETIC phase: restored Verify'
    $Phase='Verify'; Invoke-Retirement
    if (@(Get-ChildItem -LiteralPath $PacketPath -Filter 'verify-*.checks.json').Count -ne 2) { throw 'Restored-state verification did not complete' }
    if ($env:SQLCMDINI -ne 'MUST_NOT_REACH_CHILD' -or (Test-Path Env:SQLCMDPASSWORD)) { throw 'Process transport changed parent credential environment' }
    $testPassed=$true
    Write-Output ('PASS real child Apply/Verify/Rollback/Verify path-only results and complete stream capture on '+$PSVersionTable.PSEdition+' '+$PSVersionTable.PSVersion)
} finally {
    [Environment]::SetEnvironmentVariable('SQLCMDINI',$savedIni,'Process')
    # Exact newly-created synthetic test directory only; no SQL or operator state.
    if ($testPassed) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
    else { Write-Host ('FAILED offline test evidence retained: '+$testRoot) }
}
