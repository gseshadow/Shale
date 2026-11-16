# Pure renderer transform. No connection, module-body/hash or assertion changes.
Set-StrictMode -Version Latest
function Add-ProofModuleDiagnostics([string]$Body,[string]$Source) {
    $begin="/* ACCEPTANCE_PROOF_DIAGNOSTIC_BEGIN */`n"
    $end="/* ACCEPTANCE_PROOF_DIAGNOSTIC_END */`n"
    $diagnostic=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ProofModuleFailure.sql'))
    if ($Source -ceq 'docs/sql/ai-integration/universal-pr4a-proof.sql') {
        $catch='END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;'
        if ($Body.IndexOf($catch,[StringComparison]::Ordinal) -lt 0 -or $Body.IndexOf($catch,[StringComparison]::Ordinal) -ne $Body.LastIndexOf($catch,[StringComparison]::Ordinal)) { throw 'STOP: proof installer catch shape changed.' }
        # The original rollback and bare THROW remain in the same owning CATCH.
        $replacement='END TRY BEGIN CATCH '+$begin+"IF XACT_STATE()<>0 BEGIN`n"+$diagnostic+"`nEND;`n"+$end+'IF XACT_STATE()<>0 ROLLBACK TRANSACTION; '+$begin+"IF ERROR_NUMBER()=59400 AND ERROR_MESSAGE()=N'CIMD proof module source/owner/context drift.' AND @@TRANCOUNT=0 SELECT CAST('PROOF_ROLLBACK_CONFIRMED' AS varchar(64)) AS ProofRollback;`n"+$end+'THROW; END CATCH;'
        return $Body.Replace($catch,$replacement)
    }
    if ($Source -ceq 'docs/sql/ai-integration/universal-pr4a-proof-verify.sql') {
        return $begin+"BEGIN TRY`n"+$end+$Body+$begin+"END TRY BEGIN CATCH`n"+$diagnostic+"`nTHROW; END CATCH;`n"+$end
    }
    throw 'STOP: proof diagnostics only support the two Stage 21 sources.'
}
