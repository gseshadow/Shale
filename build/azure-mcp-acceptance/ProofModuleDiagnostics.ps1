# Acceptance-only pre-THROW transform; public SQL and module hashes remain frozen.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'ProofModuleDiagnosticsV1.ps1')
function Add-ProofDiagnosticBlock([string]$Text) {
    return "/* ACCEPTANCE_PROOF_V2_BEGIN */`n"+$Text+"/* ACCEPTANCE_PROOF_V2_END */`n"
}
function Add-ProofAssertionDiagnostics([string]$Body,[string]$Phase) {
    if ($Phase -cnotin @('INSTALL','SEAL','STANDALONE')) { throw 'STOP: invalid proof diagnostic phase.' }
    $throw="THROW 59400,'CIMD proof module source/owner/context drift.',1;"
    $matches=@([regex]::Matches($Body,'(?m)^IF EXISTS\(SELECT 1 FROM @C4aModules e WHERE NOT EXISTS\([^\r\n]+'))
    if ($matches.Count -ne 1 -or -not $matches[0].Value.EndsWith($throw)) { throw 'STOP: proof assertion shape changed.' }
    $assertion=$matches[0].Value
    $predicate=$assertion.Substring(0,$assertion.Length-$throw.Length)
    $diagnostic=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ProofModuleFailure.sql')).Replace('__PHASE__',$Phase)
    # Original predicate and explicit THROW remain exact. Collect while branch is
    # failing, before THROW/XACT_ABORT or any catch can undo its catalog state.
    $replacement=$predicate+(Add-ProofDiagnosticBlock ("BEGIN`n"+$diagnostic+"`n"))+$throw+(Add-ProofDiagnosticBlock "`nEND;`n")
    return $Body.Replace($assertion,$replacement)
}
function Add-ProofModuleDiagnostics([string]$Body,[string]$Source) {
    if ($Source -ceq 'docs/sql/ai-integration/universal-pr4a-proof-verify.sql') { return (Add-ProofAssertionDiagnostics $Body 'STANDALONE') }
    if ($Source -cne 'docs/sql/ai-integration/universal-pr4a-proof.sql') { throw 'STOP: unsupported proof diagnostic source.' }
    $result=Add-ProofAssertionDiagnostics $Body 'INSTALL'
    $seal=@([regex]::Matches($result,'DECLARE @C4aSealVerify nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\); EXEC sys.sp_executesql @C4aSealVerify;'))
    if ($seal.Count -ne 1) { throw 'STOP: embedded proof seal shape changed.' }
    $hex=$seal[0].Groups[1].Value
    $bytes=[byte[]]::new($hex.Length/2)
    for ($i=0;$i -lt $bytes.Length;$i++) { $bytes[$i]=[Convert]::ToByte($hex.Substring($i*2,2),16) }
    $decoded=[Text.Encoding]::Unicode.GetString($bytes)
    $changed=Add-ProofAssertionDiagnostics $decoded 'SEAL'
    $newHex=[BitConverter]::ToString([Text.Encoding]::Unicode.GetBytes($changed)).Replace('-','').ToLowerInvariant()
    $result=$result.Replace($seal[0].Value,$seal[0].Value.Replace($hex,$newHex))
    $result=$result.Replace('BEGIN TRY BEGIN TRANSACTION;', (Add-ProofDiagnosticBlock "DECLARE @AcceptanceProofPhase varchar(16)='INSTALL';`n")+'BEGIN TRY BEGIN TRANSACTION;')
    $result=$result.Replace('DECLARE @C4aSealVerify nvarchar(max)=', (Add-ProofDiagnosticBlock "SET @AcceptanceProofPhase='SEAL';`n")+'DECLARE @C4aSealVerify nvarchar(max)=')
    $catch='END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;'
    if (-not $result.Contains($catch)) { throw 'STOP: proof installer catch shape changed.' }
    $state=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ProofCatchState.sql'))
    $rollback=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ProofRollbackState.sql'))
    return $result.Replace($catch, 'END TRY BEGIN CATCH '+(Add-ProofDiagnosticBlock $state)+'IF XACT_STATE()<>0 ROLLBACK TRANSACTION; '+(Add-ProofDiagnosticBlock $rollback)+'THROW; END CATCH;')
}
function Get-LegacyProofPacket([string]$Text) {
    # Only the frozen v1 packet can be unwound for the latest historical recovery.
    $clean=[regex]::Replace($Text,'(?s)/\* ACCEPTANCE_PROOF_DIAGNOSTIC_BEGIN \*/\r?\n.*?/\* ACCEPTANCE_PROOF_DIAGNOSTIC_END \*/\r?\n','')
    if ((Get-TextSHA256 $clean) -cne 'F3D4E8EB41054B0E5A5EF4A608497B6936DB0D889E77A1C99D9BE72A6FD5827E') { throw 'STOP: unsupported historical proof packet.' }
    $expected=$clean
    foreach ($source in @('docs/sql/ai-integration/universal-pr4a-proof.sql','docs/sql/ai-integration/universal-pr4a-proof-verify.sql')) {
        $body=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../'+$source)))
        $expected=$expected.Replace($body,(Add-ProofModuleDiagnosticsV1 $body $source))
    }
    if ($expected -cne $Text) { throw 'STOP: previous proof packet is not the exact frozen v1 diagnostic rendering.' }
    return $clean
}
