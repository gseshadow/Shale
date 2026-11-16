DECLARE @AcceptanceErrorNumber int=ERROR_NUMBER(),@AcceptanceErrorMessage nvarchar(2048)=ERROR_MESSAGE();
BEGIN TRY
 IF @AcceptanceErrorNumber=59400 AND @AcceptanceErrorMessage=N'CIMD proof module source/owner/context drift.'
  SELECT CAST(CONCAT('PROOF_CATCH:',@AcceptanceProofPhase,':',XACT_STATE(),':',@@TRANCOUNT,':',
    CASE WHEN (@@OPTIONS & 16384)=16384 THEN 1 ELSE 0 END) AS varchar(96)) AS ProofCatch;
END TRY BEGIN CATCH DECLARE @AcceptanceCatchDiagnosticUnavailable bit=1; END CATCH;
