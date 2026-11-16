# Read-only checkpoint SQL construction; no connection on import.
function New-Checkpoint24Sql([string]$ticket,[string]$Directory) {
        $templates=Join-Path $PSScriptRoot '../../docs/sql/ai-integration/azure-acceptance'
        $sql=":on error exit`n"+[IO.File]::ReadAllText((Join-Path $templates 'target.sql'))+"`n"+[IO.File]::ReadAllText((Join-Path $templates 'owner.sql'))+"`nGO`n"
        $sql+="SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET QUOTED_IDENTIFIER ON; SET NUMERIC_ROUNDABORT OFF;`nGO`n"
        # Reuse unchanged read-only receipt/source/permission validators, never an installer.
        foreach ($source in @('universal-pr3-verify.sql','universal-pr3-confinement-verify.sql')) {
            $sql+=[IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../docs/sql/ai-integration/'+$source)))+"`nGO`n"
        }
        # Stage 18's historical V2 verifier predates PR3's new role permissions.
        # Its exact bytes are replaced ONLY in this derived read-only probe by the
        # current PR3-aware verifier above. Binding inputs/assertions remain exact;
        # the completed stage file/hash is never changed or re-executed as a stage.
        $binding=[IO.File]::ReadAllText((Join-Path $Directory '18-binding-postcheck.sql'))
        $legacy=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '../../docs/sql/ai-integration/phase3b8-confinement-verify.sql'))
        $at=$binding.IndexOf($legacy,[StringComparison]::Ordinal)
        if ($at -lt 0 -or $at -ne $binding.LastIndexOf($legacy,[StringComparison]::Ordinal)) { throw 'STOP: historical postcheck shape changed; independent reconciliation required.' }
        $sql+=$binding.Remove($at,$legacy.Length)+"`nGO`n"
        $sql+=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Stage21Checkpoint.sql'))+"`nGO`n"
        $sql+=([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Reconcile.sql')).Replace('PRINT @AcceptanceSnapshot;','SELECT @AcceptanceSnapshot AS AcceptanceResult;'))+"`nGO`n"
        $sql+="SELECT CAST('RECOVERY24_OK:$ticket' AS varchar(96)) AS RecoveryStatus;`nGO`n"
        return $sql
}
