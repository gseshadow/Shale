IF @AcceptanceErrorNumber=59400 AND @AcceptanceErrorMessage=N'CIMD proof module source/owner/context drift.' AND @@TRANCOUNT=0 SELECT CAST('PROOF_ROLLBACK_CONFIRMED' AS varchar(64)) AS ProofRollback;
